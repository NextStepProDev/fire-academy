package pl.fireacademy.api.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.fireacademy.api.NotFoundException;
import pl.fireacademy.api.admin.TrainingSlotDtos.*;
import pl.fireacademy.api.user.TrainingEnrollmentService;
import pl.fireacademy.domain.training.*;
import pl.fireacademy.domain.user.UserRepository;
import pl.fireacademy.infrastructure.i18n.MessageService;
import pl.fireacademy.infrastructure.mail.TrainingMailService;

import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
public class AdminTrainingEnrollmentService {

    private final TrainingSlotRepository slotRepository;
    private final TrainingEnrollmentRepository enrollmentRepository;
    private final TrainingPaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final MessageService msg;
    private final TrainingMailService trainingMail;

    public AdminTrainingEnrollmentService(TrainingSlotRepository slotRepository,
                                          TrainingEnrollmentRepository enrollmentRepository,
                                          TrainingPaymentRepository paymentRepository,
                                          UserRepository userRepository,
                                          MessageService msg,
                                          TrainingMailService trainingMail) {
        this.slotRepository = slotRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.msg = msg;
        this.trainingMail = trainingMail;
    }

    @Transactional(readOnly = true)
    public List<RosterEntry> getRoster(UUID slotId, YearMonth month) {
        var enrollments = enrollmentRepository.findCoveringForSlot(slotId, month.toString());
        if (enrollments.isEmpty()) {
            return List.of();
        }
        var ids = enrollments.stream().map(TrainingEnrollment::getId).toList();
        var paidIds = new HashSet<>(paymentRepository.findPaidEnrollmentIds(ids, month.toString()));
        return enrollments.stream().map(te -> {
            var u = te.getUser();
            return new RosterEntry(
                    te.getId(), u.getId(), u.getFirstName(), u.getLastName(), u.getEmail(), u.getPhone(),
                    te.getStartMonth(), te.getEndMonth(), te.getEndMonth() == null, paidIds.contains(te.getId())
            );
        }).toList();
    }

    @Transactional
    public void addEnrollment(UUID slotId, AdminAddEnrollmentRequest request) {
        var current = YearMonth.now();
        var start = request.startMonth();
        if (start.isBefore(current)) {
            throw new IllegalArgumentException(msg.get("trainingenrollment.month.past"));
        }

        var slot = slotRepository.findByIdForUpdate(slotId)
                .orElseThrow(() -> new NotFoundException(msg.get("trainingslot.not.found")));

        var user = userRepository.findById(request.userId())
                .orElseThrow(() -> new NotFoundException(msg.get("trainingenrollment.user.not.found")));

        var end = request.months() != null ? start.plusMonths(request.months() - 1L) : null;

        if (enrollmentRepository.existsActiveFor(user.getId(), slotId, start.toString())) {
            throw new IllegalStateException(msg.get("trainingenrollment.duplicate"));
        }

        // Admin może dopisywać ponad limit miejsc (świadome przekroczenie) — brak kontroli pojemności.
        enrollmentRepository.save(new TrainingEnrollment(slot, user, start, end));

        // G: powiadom usera, że recepcja go dopisała.
        var info = slotInfo(slot);
        var billingMonth = start.isAfter(current) ? start : current;
        int sessions = TrainingEnrollmentService.sessionsInMonth(slot.getDayOfWeek(), billingMonth);
        var amount = slot.getPrice() != null
                ? slot.getPrice().multiply(java.math.BigDecimal.valueOf(sessions)) : null;
        trainingMail.sendAdminAddedConfirmation(user.getEmail(), user.getFirstName(), info,
                start, request.months(), billingMonth, sessions, amount);
    }

    /** Wypisanie w dowolnym momencie — twarde usunięcie (zwalnia miejsce we wszystkich miesiącach). */
    @Transactional
    public void remove(UUID enrollmentId) {
        var te = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new NotFoundException(msg.get("trainingenrollment.not.found")));
        var user = te.getUser();
        var info = slotInfo(te.getSlot());
        enrollmentRepository.delete(te);
        // H: powiadom usera, że recepcja go wypisała.
        trainingMail.sendAdminRemoved(user.getEmail(), user.getFirstName(), info);
    }

    private TrainingMailService.SlotInfo slotInfo(TrainingSlot slot) {
        var instr = slot.getInstructor();
        return new TrainingMailService.SlotInfo(
                slot.getEventType().getName(),
                instr != null ? instr.getFirstName() + " " + instr.getLastName() : null,
                slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime(), slot.getPrice());
    }

    @Transactional
    public void setPayment(UUID enrollmentId, SetPaymentRequest request) {
        var te = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new NotFoundException(msg.get("trainingenrollment.not.found")));
        var month = request.month().toString();
        if (request.paid()) {
            if (!paymentRepository.existsByEnrollmentIdAndYearMonth(enrollmentId, month)) {
                paymentRepository.save(new TrainingPayment(te, request.month()));
            }
        } else {
            paymentRepository.deleteByEnrollmentIdAndYearMonth(enrollmentId, month);
        }
    }
}
