package pl.fireacademy.api.user;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.fireacademy.api.NotFoundException;
import pl.fireacademy.infrastructure.mail.TrainingMailService;
import pl.fireacademy.api.user.TrainingEnrollmentDtos.*;
import pl.fireacademy.domain.training.TrainingCancelledSessionRepository;
import pl.fireacademy.domain.training.TrainingEnrollment;
import pl.fireacademy.domain.training.TrainingEnrollmentRepository;
import pl.fireacademy.domain.training.TrainingSlot;
import pl.fireacademy.domain.training.TrainingSlotRepository;
import pl.fireacademy.domain.user.UserRepository;
import pl.fireacademy.infrastructure.i18n.MessageService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Service
public class TrainingEnrollmentService {

    /** Ile miesięcy do przodu (poza bieżącym) można rezerwować. */
    private static final int BOOKABLE_MONTHS_AHEAD = 2;

    private final TrainingEnrollmentRepository enrollmentRepository;
    private final TrainingSlotRepository slotRepository;
    private final TrainingCancelledSessionRepository cancelledSessionRepository;
    private final UserRepository userRepository;
    private final MessageService msg;
    private final TrainingMailService trainingMail;

    public TrainingEnrollmentService(TrainingEnrollmentRepository enrollmentRepository,
                                     TrainingSlotRepository slotRepository,
                                     TrainingCancelledSessionRepository cancelledSessionRepository,
                                     UserRepository userRepository,
                                     MessageService msg,
                                     TrainingMailService trainingMail) {
        this.enrollmentRepository = enrollmentRepository;
        this.slotRepository = slotRepository;
        this.cancelledSessionRepository = cancelledSessionRepository;
        this.userRepository = userRepository;
        this.msg = msg;
        this.trainingMail = trainingMail;
    }

    @Transactional
    public void enroll(UUID userId, UUID slotId, EnrollTrainingRequest request) {
        var current = YearMonth.now();
        var windowEnd = current.plusMonths(BOOKABLE_MONTHS_AHEAD);
        var start = request.startMonth();

        if (start.isBefore(current)) {
            throw new IllegalArgumentException(msg.get("trainingenrollment.month.past"));
        }
        if (start.isAfter(windowEnd)) {
            throw new IllegalArgumentException(msg.get("trainingenrollment.month.out.of.range"));
        }

        var slot = slotRepository.findByIdForUpdate(slotId)
                .orElseThrow(() -> new NotFoundException(msg.get("trainingslot.not.found")));
        if (!slot.isActive() || slot.isDeleted()) {
            throw new IllegalStateException(msg.get("trainingslot.inactive"));
        }
        if (slot.getDeactivatedFrom() != null && !slot.getDeactivatedFrom().isAfter(LocalDate.now())) {
            throw new IllegalStateException(msg.get("trainingslot.inactive"));
        }

        var end = request.months() != null ? start.plusMonths(request.months() - 1L) : null;

        if (enrollmentRepository.existsActiveFor(userId, slotId, start.toString())) {
            throw new IllegalStateException(msg.get("trainingenrollment.duplicate"));
        }

        // Sprawdź dostępność miejsc dla każdego pokrywanego miesiąca w oknie rezerwacji.
        var lastToCheck = (end != null && end.isBefore(windowEnd)) ? end : windowEnd;
        for (var m = start; !m.isAfter(lastToCheck); m = m.plusMonths(1)) {
            if (enrollmentRepository.countCovering(slotId, m.toString()) >= slot.getMaxParticipants()) {
                throw new IllegalStateException(msg.get("trainingenrollment.full"));
            }
        }

        var user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(msg.get("error.user.not.found")));

        enrollmentRepository.save(new TrainingEnrollment(slot, user, start, end));

        var info = slotInfo(slot);
        var billingMonth = start.isAfter(current) ? start : current;
        int sessions = sessionsInMonth(slot.getDayOfWeek(), billingMonth);
        BigDecimal amount = slot.getPrice() != null
                ? slot.getPrice().multiply(BigDecimal.valueOf(sessions)) : null;
        long taken = enrollmentRepository.countCovering(slotId, current.toString());
        trainingMail.sendEnrollmentConfirmation(user.getEmail(), user.getFirstName(), info,
                start, request.months(), billingMonth, sessions, amount);
        trainingMail.sendAdminEnrollmentNotification(true,
                user.getFirstName() + " " + user.getLastName(), user.getEmail(), info,
                periodLabel(start, end), taken, slot.getMaxParticipants());
    }

    @Transactional(readOnly = true)
    public List<MyTrainingEnrollmentResponse> getMyEnrollments(UUID userId) {
        var current = YearMonth.now();
        var enrollments = enrollmentRepository.findActiveByUser(userId, current.toString());
        if (enrollments.isEmpty()) {
            return List.of();
        }
        // Nadchodzące odwołane zajęcia (od dziś do końca okna rezerwacji) per slot.
        var slotIds = enrollments.stream().map(te -> te.getSlot().getId()).distinct().toList();
        var to = current.plusMonths(BOOKABLE_MONTHS_AHEAD).atEndOfMonth();
        var cancelledMap = cancelledSessionRepository
                .findForSlotsInRange(slotIds, LocalDate.now(), to).stream()
                .collect(java.util.stream.Collectors.groupingBy(cs -> cs.getSlot().getId(),
                        java.util.stream.Collectors.mapping(cs -> cs.getSessionDate(), java.util.stream.Collectors.toList())));
        return enrollments.stream()
                .map(te -> toResponse(te, current, cancelledMap.getOrDefault(te.getSlot().getId(), List.of())))
                .toList();
    }

    @Transactional
    public void cancel(UUID userId, UUID enrollmentId) {
        var te = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new NotFoundException(msg.get("trainingenrollment.not.found")));
        if (!te.getUser().getId().equals(userId)) {
            throw new NotFoundException(msg.get("trainingenrollment.not.found"));
        }
        var current = YearMonth.now();
        var slot = te.getSlot();
        var user = te.getUser();
        var info = slotInfo(slot);
        YearMonth activeUntil;
        if (te.getStartMonth().isAfter(current)) {
            // Subskrypcja jeszcze się nie zaczęła — usuwamy w całości.
            enrollmentRepository.delete(te);
            activeUntil = null;
        } else {
            // Rezygnacja od kolejnego miesiąca — zostaje na bieżący.
            // expiryNotified=true: mail o rezygnacji (C) zastępuje mail o wygaśnięciu (K) ze schedulera.
            te.setEndMonth(current);
            te.setExpiryNotified(true);
            enrollmentRepository.save(te);
            activeUntil = current;
        }

        trainingMail.sendCancellationConfirmation(user.getEmail(), user.getFirstName(), info, activeUntil);
        trainingMail.sendAdminEnrollmentNotification(false,
                user.getFirstName() + " " + user.getLastName(), user.getEmail(), info,
                periodLabel(te.getStartMonth(), activeUntil),
                enrollmentRepository.countCovering(slot.getId(), current.toString()),
                slot.getMaxParticipants());
    }

    private TrainingMailService.SlotInfo slotInfo(TrainingSlot slot) {
        var instr = slot.getInstructor();
        return new TrainingMailService.SlotInfo(
                slot.getEventType().getName(),
                instr != null ? instr.getFirstName() + " " + instr.getLastName() : null,
                slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime(), slot.getPrice());
    }

    private String periodLabel(YearMonth start, @Nullable YearMonth end) {
        String startLbl = TrainingMailService.monthLabel(start);
        return end != null ? startLbl + " – " + TrainingMailService.monthLabel(end)
                : msg.get("email.training.details.duration.indefinite");
    }

    private MyTrainingEnrollmentResponse toResponse(TrainingEnrollment te, YearMonth current,
                                                    List<LocalDate> cancelledDates) {
        TrainingSlot slot = te.getSlot();
        var et = slot.getEventType();
        var instr = slot.getInstructor();
        var start = te.getStartMonth();
        var billingMonth = start.isAfter(current) ? start : current;
        int sessions = sessionsInMonth(slot.getDayOfWeek(), billingMonth);
        BigDecimal monthlyAmount = slot.getPrice() != null
                ? slot.getPrice().multiply(BigDecimal.valueOf(sessions))
                : null;
        return new MyTrainingEnrollmentResponse(
                te.getId(), slot.getId(), et.getId(), et.getName(),
                instr != null ? instr.getFirstName() + " " + instr.getLastName() : null,
                slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime(), slot.getPrice(),
                start, te.getEndMonth(), billingMonth, sessions, monthlyAmount, cancelledDates
        );
    }

    /**
     * Liczba zajęć danego dnia tygodnia (ISO 1–7) do opłacenia w miesiącu:
     * dla bieżącego miesiąca liczona od DZIŚ do końca (pozostałe), dla przyszłych — wszystkie.
     */
    public static int sessionsInMonth(int isoDayOfWeek, YearMonth month) {
        int fromDay = month.equals(YearMonth.now()) ? LocalDate.now().getDayOfMonth() : 1;
        int count = 0;
        for (int day = fromDay; day <= month.lengthOfMonth(); day++) {
            if (LocalDate.of(month.getYear(), month.getMonthValue(), day).getDayOfWeek().getValue() == isoDayOfWeek) {
                count++;
            }
        }
        return count;
    }
}
