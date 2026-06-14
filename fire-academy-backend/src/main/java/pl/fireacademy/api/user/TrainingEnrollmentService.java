package pl.fireacademy.api.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.fireacademy.api.NotFoundException;
import pl.fireacademy.api.user.TrainingEnrollmentDtos.*;
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
    private final UserRepository userRepository;
    private final MessageService msg;

    public TrainingEnrollmentService(TrainingEnrollmentRepository enrollmentRepository,
                                     TrainingSlotRepository slotRepository,
                                     UserRepository userRepository,
                                     MessageService msg) {
        this.enrollmentRepository = enrollmentRepository;
        this.slotRepository = slotRepository;
        this.userRepository = userRepository;
        this.msg = msg;
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
        if (!slot.isActive()) {
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
    }

    @Transactional(readOnly = true)
    public List<MyTrainingEnrollmentResponse> getMyEnrollments(UUID userId) {
        var current = YearMonth.now();
        return enrollmentRepository.findActiveByUser(userId, current.toString()).stream()
                .map(te -> toResponse(te, current))
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
        if (te.getStartMonth().isAfter(current)) {
            // Subskrypcja jeszcze się nie zaczęła — usuwamy w całości.
            enrollmentRepository.delete(te);
        } else {
            // Rezygnacja od kolejnego miesiąca — zostaje na bieżący.
            te.setEndMonth(current);
            enrollmentRepository.save(te);
        }
    }

    private MyTrainingEnrollmentResponse toResponse(TrainingEnrollment te, YearMonth current) {
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
                start, te.getEndMonth(), billingMonth, sessions, monthlyAmount
        );
    }

    /** Liczba wystąpień danego dnia tygodnia (ISO 1–7) w miesiącu. */
    static int sessionsInMonth(int isoDayOfWeek, YearMonth month) {
        int count = 0;
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            if (LocalDate.of(month.getYear(), month.getMonthValue(), day).getDayOfWeek().getValue() == isoDayOfWeek) {
                count++;
            }
        }
        return count;
    }
}
