package pl.fireacademy.api.user;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.fireacademy.api.NotFoundException;
import pl.fireacademy.infrastructure.mail.TrainingMailService;
import pl.fireacademy.api.user.TrainingEnrollmentDtos.*;
import pl.fireacademy.domain.training.TrainingBillingService;
import pl.fireacademy.domain.training.TrainingCreditService;
import pl.fireacademy.domain.training.TrainingCancelledSessionRepository;
import pl.fireacademy.domain.training.TrainingEnrollment;
import pl.fireacademy.domain.training.TrainingEnrollmentRepository;
import pl.fireacademy.domain.training.TrainingHoliday;
import pl.fireacademy.domain.training.TrainingHolidayRepository;
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

    /** How many months ahead (beyond the current one) can be booked. */
    private static final int BOOKABLE_MONTHS_AHEAD = 2;

    /**
     * How many days before the next month starts the user gets to see its estimated billing.
     * The current-month figure counts only the sessions still remaining, so near month-end it
     * drops toward zero — this preview surfaces what they'll actually owe for the coming month.
     */
    private static final int NEXT_MONTH_PREVIEW_DAYS = 7;

    private final TrainingEnrollmentRepository enrollmentRepository;
    private final TrainingSlotRepository slotRepository;
    private final TrainingCancelledSessionRepository cancelledSessionRepository;
    private final TrainingHolidayRepository holidayRepository;
    private final TrainingBillingService billing;
    private final TrainingCreditService creditService;
    private final pl.fireacademy.domain.training.TrainingPaymentRepository paymentRepository;
    private final pl.fireacademy.domain.training.TrainingRefundRepository refundRepository;
    private final UserRepository userRepository;
    private final MessageService msg;
    private final TrainingMailService trainingMail;

    public TrainingEnrollmentService(TrainingEnrollmentRepository enrollmentRepository,
                                     TrainingSlotRepository slotRepository,
                                     TrainingCancelledSessionRepository cancelledSessionRepository,
                                     TrainingHolidayRepository holidayRepository,
                                     TrainingBillingService billing,
                                     TrainingCreditService creditService,
                                     pl.fireacademy.domain.training.TrainingPaymentRepository paymentRepository,
                                     pl.fireacademy.domain.training.TrainingRefundRepository refundRepository,
                                     UserRepository userRepository,
                                     MessageService msg,
                                     TrainingMailService trainingMail) {
        this.enrollmentRepository = enrollmentRepository;
        this.slotRepository = slotRepository;
        this.cancelledSessionRepository = cancelledSessionRepository;
        this.holidayRepository = holidayRepository;
        this.billing = billing;
        this.creditService = creditService;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
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

        // Check spot availability for each covered month within the booking window.
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
        int sessions = billing.sessions(slot, billingMonth);
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
        // Upcoming cancelled sessions and days off (from today to the end of the booking window).
        var slotIds = enrollments.stream().map(te -> te.getSlot().getId()).distinct().toList();
        var to = current.plusMonths(BOOKABLE_MONTHS_AHEAD).atEndOfMonth();
        var cancelledMap = cancelledSessionRepository
                .findForSlotsInRange(slotIds, LocalDate.now(), to).stream()
                .collect(java.util.stream.Collectors.groupingBy(cs -> cs.getSlot().getId(),
                        java.util.stream.Collectors.mapping(cs -> cs.getSessionDate(), java.util.stream.Collectors.toList())));
        var holidays = holidayRepository.findByHolidayDateBetweenOrderByHolidayDateAsc(LocalDate.now(), to);
        return enrollments.stream()
                .map(te -> {
                    var slotHolidays = holidays.stream()
                            .map(TrainingHoliday::getHolidayDate)
                            .filter(d -> d.getDayOfWeek().getValue() == te.getSlot().getDayOfWeek())
                            .toList();
                    return toResponse(te, current, cancelledMap.getOrDefault(te.getSlot().getId(), List.of()), slotHolidays);
                })
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
            // Subscription has not started yet — remove it entirely.
            enrollmentRepository.delete(te);
            activeUntil = null;
        } else {
            // Cancellation from the next month — stays for the current one.
            // expiryNotified=true: the cancellation email (C) replaces the expiry email (K) from the scheduler.
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
                                                    List<LocalDate> cancelledDates, List<LocalDate> holidayDates) {
        TrainingSlot slot = te.getSlot();
        var et = slot.getEventType();
        var instr = slot.getInstructor();
        var start = te.getStartMonth();
        var billingMonth = start.isAfter(current) ? start : current;
        int sessions = billing.sessions(slot, billingMonth);
        // Surplus credit (from CREDITED refunds) discounts the bill; monthlyAmount is the NET the user pays.
        BigDecimal monthlyCredit = creditService.appliedFor(te, billingMonth);
        BigDecimal monthlyAmount = slot.getPrice() != null
                ? slot.getPrice().multiply(BigDecimal.valueOf(sessions)).subtract(monthlyCredit).max(BigDecimal.ZERO)
                : null;
        boolean billingMonthPaid = paymentRepository
                .existsByEnrollmentIdAndYearMonth(te.getId(), billingMonth.toString());
        // Money owed for cancelled paid sessions not yet resolved by the organizer.
        BigDecimal pendingRefundAmount = refundRepository.sumPendingForEnrollment(te.getId());
        // What the client actually paid for the billing month = current bill + this month's still-unresolved refunds
        // (so a paid month later cut by a cancellation/deactivation shows the real amount, not the recomputed 0).
        BigDecimal billingMonthPaidAmount = (billingMonthPaid && monthlyAmount != null)
                ? monthlyAmount.add(refundRepository.sumPendingForEnrollmentAndMonth(te.getId(), billingMonth.toString()))
                : null;
        // Surplus still waiting for upcoming months = total available minus the part already shown on the
        // (unpaid) current bill; when the current month is paid, nothing was applied to it, so it's the full balance.
        BigDecimal upcomingCreditBalance = creditService.availableBalance(te.getId())
                .subtract(billingMonthPaid ? BigDecimal.ZERO : monthlyCredit).max(BigDecimal.ZERO);

        // Next-month estimate: only once we are within the preview window before it starts and the
        // subscription is still active that month (open-ended or ending no earlier than then).
        YearMonth nextMonth = billingMonth.plusMonths(1);
        YearMonth nextBillingMonth = null;
        Integer nextMonthSessions = null;
        BigDecimal nextMonthAmount = null;
        BigDecimal nextMonthCredit = null;
        boolean inPreviewWindow = !LocalDate.now().isBefore(nextMonth.atDay(1).minusDays(NEXT_MONTH_PREVIEW_DAYS));
        boolean activeNextMonth = te.getEndMonth() == null || !te.getEndMonth().isBefore(nextMonth);
        if (inPreviewWindow && activeNextMonth) {
            nextBillingMonth = nextMonth;
            nextMonthSessions = billing.sessions(slot, nextMonth);
            nextMonthCredit = creditService.appliedFor(te, nextMonth);
            nextMonthAmount = slot.getPrice() != null
                    ? slot.getPrice().multiply(BigDecimal.valueOf(nextMonthSessions)).subtract(nextMonthCredit).max(BigDecimal.ZERO)
                    : null;
        }

        return new MyTrainingEnrollmentResponse(
                te.getId(), slot.getId(), et.getId(), et.getName(),
                instr != null ? instr.getFirstName() + " " + instr.getLastName() : null,
                slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime(), slot.getPrice(),
                start, te.getEndMonth(), billingMonth, sessions, monthlyAmount, monthlyCredit, billingMonthPaid,
                billingMonthPaidAmount, pendingRefundAmount, upcomingCreditBalance, nextBillingMonth, nextMonthSessions,
                nextMonthAmount, nextMonthCredit, cancelledDates, holidayDates, slot.getDeactivatedFrom()
        );
    }

    /**
     * Number of sessions on a given weekday (ISO 1–7) to be paid for in the month, ignoring days off and
     * cancellations. Kept for the pure-counting unit tests; production billing uses {@link TrainingBillingService}.
     */
    public static int sessionsInMonth(int isoDayOfWeek, YearMonth month) {
        return TrainingBillingService.sessionsInMonth(isoDayOfWeek, month, java.util.Set.of());
    }
}
