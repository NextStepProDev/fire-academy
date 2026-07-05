package pl.fireacademy.api.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.fireacademy.api.NotFoundException;
import pl.fireacademy.api.admin.TrainingSlotDtos.*;
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
    private final TrainingBillingService billing;
    private final TrainingCreditService creditService;
    private final TrainingRefundService refundService;
    private final UserRepository userRepository;
    private final MessageService msg;
    private final TrainingMailService trainingMail;

    public AdminTrainingEnrollmentService(TrainingSlotRepository slotRepository,
                                          TrainingEnrollmentRepository enrollmentRepository,
                                          TrainingPaymentRepository paymentRepository,
                                          TrainingBillingService billing,
                                          TrainingCreditService creditService,
                                          TrainingRefundService refundService,
                                          UserRepository userRepository,
                                          MessageService msg,
                                          TrainingMailService trainingMail) {
        this.slotRepository = slotRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.paymentRepository = paymentRepository;
        this.billing = billing;
        this.creditService = creditService;
        this.refundService = refundService;
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
        var paymentByEnrollment = new java.util.HashMap<UUID, TrainingPayment>();
        for (var p : paymentRepository.findPaidForMonth(ids, month.toString())) {
            paymentByEnrollment.put(p.getEnrollment().getId(), p);
        }
        return enrollments.stream().map(te -> {
            var u = te.getUser();
            var payment = paymentByEnrollment.get(te.getId());
            boolean paid = payment != null;
            boolean overdue = !paid && billing.isPaymentOverdue(te, month);
            return new RosterEntry(
                    te.getId(), u.getId(), u.getFirstName(), u.getLastName(), u.getEmail(), u.getPhone(),
                    te.getStartMonth(), te.getEndMonth(), te.getEndMonth() == null, paid,
                    creditService.availableBalance(te.getId()), te.getBillableFrom(),
                    netFor(te, month, payment), overdue
            );
        }).toList();
    }

    /**
     * NET amount owed for the month: the amount frozen at payment time when paid (never drifts), otherwise the live
     * bill (price × sessions) minus the surplus credit that would apply. Legacy paid rows without a stored amount
     * fall back to the live figure.
     */
    private java.math.BigDecimal netFor(TrainingEnrollment te, YearMonth month, @org.jspecify.annotations.Nullable TrainingPayment payment) {
        if (payment != null && payment.getAmount() != null) {
            return payment.getAmount();
        }
        var gross = billing.amount(te, month);
        var credit = creditService.appliedFor(te, month);
        return gross != null ? gross.subtract(credit).max(java.math.BigDecimal.ZERO) : java.math.BigDecimal.ZERO;
    }

    /**
     * Sets (or clears, when null) the first-month billing start date of a subscription — the organizer's discretionary
     * "count from day X" at first payment. The date must fall in the subscription's start month, and the start month
     * must not already be paid (its NET was frozen at payment time and would drift — revert the payment first).
     */
    @Transactional
    public void setStartDate(UUID enrollmentId, SetStartRequest request) {
        var te = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new NotFoundException(msg.get("trainingenrollment.not.found")));
        var startDate = request.startDate();
        if (startDate != null && !YearMonth.from(startDate).equals(te.getStartMonth())) {
            throw new IllegalArgumentException(msg.get("trainingenrollment.start.invalid"));
        }
        if (paymentRepository.findByEnrollmentIdAndYearMonth(enrollmentId, te.getStartMonth().toString()).isPresent()) {
            throw new IllegalStateException(msg.get("trainingenrollment.start.paid"));
        }
        te.setBillableFrom(startDate);
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
        if (slot.isDeleted()) {
            throw new NotFoundException(msg.get("trainingslot.not.found"));
        }
        // No adding people to a slot that stops before their subscription would even start.
        if (slot.getDeactivatedFrom() != null && !slot.getDeactivatedFrom().isAfter(start.atDay(1))) {
            throw new IllegalStateException(msg.get("trainingslot.inactive"));
        }

        var user = userRepository.findById(request.userId())
                .orElseThrow(() -> new NotFoundException(msg.get("trainingenrollment.user.not.found")));

        var end = request.months() != null ? start.plusMonths(request.months() - 1L) : null;

        if (enrollmentRepository.existsOverlapping(user.getId(), slotId, start.toString(),
                end != null ? end.toString() : null)) {
            throw new IllegalStateException(msg.get("trainingenrollment.duplicate"));
        }

        // Admin may add participants beyond the capacity limit (deliberate overbooking) — no capacity check.
        var saved = enrollmentRepository.save(new TrainingEnrollment(slot, user, start, end));

        // G: notify the user that the organizer added them.
        var info = slotInfo(slot);
        var billingMonth = start.isAfter(current) ? start : current;
        int sessions = billing.sessions(saved, billingMonth);
        var amount = slot.getPrice() != null
                ? slot.getPrice().multiply(java.math.BigDecimal.valueOf(sessions)) : null;
        trainingMail.sendAdminAddedConfirmation(user.getEmail(), user.getFirstName(), info,
                start, request.months(), billingMonth, sessions, amount);
    }

    /** Removal at any time — hard delete (frees the spot in all months). */
    @Transactional
    public void remove(UUID enrollmentId) {
        var te = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new NotFoundException(msg.get("trainingenrollment.not.found")));
        // Hard delete cascades the payment rows away. A paid current/future month would vanish without a
        // trace (money collected, no service, no refund) — the admin has to revert those payments first.
        var current = YearMonth.now();
        for (var pm : paymentRepository.findPaidMonths(enrollmentId)) {
            if (!YearMonth.parse(pm).isBefore(current)) {
                throw new IllegalStateException(msg.get("trainingenrollment.remove.paid"));
            }
        }
        // A CREDITED refund's surplus would cascade-delete with the enrollment, taking the money owed with it.
        if (creditService.availableBalance(enrollmentId).signum() > 0) {
            throw new IllegalStateException(msg.get("trainingenrollment.remove.credit"));
        }
        var user = te.getUser();
        var info = slotInfo(te.getSlot());
        enrollmentRepository.delete(te);
        // H: notify the user that the organizer removed them.
        trainingMail.sendAdminRemoved(user.getEmail(), user.getFirstName(), info);
    }

    private TrainingMailService.SlotInfo slotInfo(TrainingSlot slot) {
        var instr = slot.getInstructor();
        return new TrainingMailService.SlotInfo(
                slot.getEventType().getName(),
                instr != null ? instr.getFirstName() + " " + instr.getLastName() : null,
                slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime(), slot.getPrice());
    }

    /**
     * Monthly payment roster grouped by person: each subscriber with the total they owe for the whole month
     * (net of surplus credit) across all their trainings, whether it's fully paid, and a per-training breakdown.
     */
    @Transactional(readOnly = true)
    public List<UserMonthlyPayment> listMonthlyByUser(YearMonth month) {
        var enrollments = enrollmentRepository.findAllCoveringMonth(month.toString());
        if (enrollments.isEmpty()) {
            return List.of();
        }
        var ids = enrollments.stream().map(TrainingEnrollment::getId).toList();
        var paymentByEnrollment = new java.util.HashMap<java.util.UUID, TrainingPayment>();
        for (var p : paymentRepository.findPaidForMonth(ids, month.toString())) {
            paymentByEnrollment.put(p.getEnrollment().getId(), p);
        }
        var paidIds = paymentByEnrollment.keySet();

        var byUser = new java.util.LinkedHashMap<java.util.UUID, List<TrainingEnrollment>>();
        for (var te : enrollments) {
            byUser.computeIfAbsent(te.getUser().getId(), k -> new java.util.ArrayList<>()).add(te);
        }
        var result = new java.util.ArrayList<UserMonthlyPayment>();
        for (var group : byUser.values()) {
            var user = group.getFirst().getUser();
            var lines = new java.util.ArrayList<MonthlyTrainingLine>();
            var total = java.math.BigDecimal.ZERO;
            boolean allPaid = true;
            java.time.Instant paidAt = null;
            var creditBalance = java.math.BigDecimal.ZERO;
            for (var te : group) {
                var slot = te.getSlot();
                boolean paid = paidIds.contains(te.getId());
                if (!paid) allPaid = false;
                var payment = paymentByEnrollment.get(te.getId());
                if (payment != null && (paidAt == null || payment.getCreatedAt().isAfter(paidAt))) {
                    paidAt = payment.getCreatedAt();
                }
                // A paid line shows the amount frozen at payment time (never drifts); an unpaid one the live NET bill.
                var net = netFor(te, month, payment);
                boolean overdue = !paid && billing.isPaymentOverdue(te, month);
                total = total.add(net);
                creditBalance = creditBalance.add(creditService.availableBalance(te.getId()));
                var instr = slot.getInstructor();
                lines.add(new MonthlyTrainingLine(slot.getEventType().getName(), slot.getDayOfWeek(),
                        slot.getStartTime(), slot.getEndTime(), net, paid, payment != null && payment.isPinned(), overdue,
                        te.getId(), te.getStartMonth(), te.getBillableFrom(),
                        slot.getId(), slot.getEventType().getId(),
                        instr != null ? instr.getId() : null,
                        instr != null ? instr.getFirstName() + " " + instr.getLastName() : null));
            }
            result.add(new UserMonthlyPayment(user.getId(), user.getFirstName(), user.getLastName(),
                    user.getEmail(), user.getPhone(), lines, total, allPaid, paidAt, creditBalance));
        }
        return result;
    }

    @Transactional
    public void setPayment(UUID enrollmentId, SetPaymentRequest request) {
        var te = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new NotFoundException(msg.get("trainingenrollment.not.found")));
        // A per-slot roster toggle targets one specific training deliberately → pin it so a later whole-month
        // revert leaves it alone; only this same toggle can clear it.
        applyPayment(te, request.month(), request.paid(), true);
    }

    /**
     * Marks/reverts a whole month's payment for one subscriber across ALL their trainings at once — a person pays
     * for the month, not per training. Atomic: either every covered training flips or none (an error rolls back).
     * A revert leaves individually-pinned trainings paid — undoing it only clears what this action added.
     */
    @Transactional
    public void payUserMonth(UUID userId, YearMonth month, boolean paid) {
        var enrollments = enrollmentRepository.findCoveringByUserAndMonth(userId, month.toString());
        for (var te : enrollments) {
            applyPayment(te, month, paid, false);
        }
    }

    /**
     * @param individual whether this is a deliberate single-training action (per-slot roster toggle) as opposed
     *                   to a whole-month batch. On payment it decides whether the row is pinned; on revert an
     *                   individual action clears any row, while a batch revert leaves pinned rows untouched.
     */
    private void applyPayment(TrainingEnrollment te, YearMonth requestMonth, boolean requestPaid, boolean individual) {
        var enrollmentId = te.getId();
        var month = requestMonth.toString();
        var paidMonths = new HashSet<>(paymentRepository.findPaidMonths(enrollmentId));
        if (requestPaid) {
            if (!paidMonths.contains(month)) {
                // A month opens for payment only in the last week before it starts (same window as the
                // next-month estimate) — you don't pay August in early July; payment happens right before
                // its first sessions. Current and past months are always open (corrections).
                requireMonthOpenForPayment(requestMonth);
                // Payments run in calendar order: a month can be marked paid only once every earlier payable
                // month (from the current month, within coverage) is already paid — you cannot pay August with
                // July still open. This keeps paid months a contiguous prefix, so surplus never lands backwards.
                requireEarlierMonthsPaid(te, requestMonth, paidMonths);
                // Freeze how much surplus this month absorbs, so the same credit is never applied twice,
                // and the NET amount collected, so displays never drift with passing days or price edits.
                var applied = creditService.liveAppliedFor(te, requestMonth);
                var gross = billing.amount(te, requestMonth);
                var net = gross != null ? gross.subtract(applied).max(java.math.BigDecimal.ZERO) : null;
                paymentRepository.save(new TrainingPayment(te, requestMonth, applied, net, individual));
            }
        } else {
            // A whole-month (batch) revert leaves individually-pinned payments alone — once a single training is
            // marked paid on the roster it stays until the admin un-marks that very training. An individual
            // revert (the same roster toggle) always clears the row.
            var existing = paymentRepository.findByEnrollmentIdAndYearMonth(enrollmentId, month).orElse(null);
            if (existing == null) {
                return;
            }
            if (!individual && existing.isPinned()) {
                return;
            }
            // Symmetrically, a month can be un-paid only if no later month is still paid (no gaps).
            for (var pm : paidMonths) {
                if (YearMonth.parse(pm).isAfter(requestMonth)) {
                    throw new IllegalStateException(msg.get("trainingpayment.unpay.out.of.order"));
                }
            }
            // A month with an already-settled refund cannot be un-paid: the cash was handed back / the surplus
            // credited against this very payment. The admin must unsettle the refund first.
            if (refundService.hasSettledForMonth(enrollmentId, requestMonth)) {
                throw new IllegalStateException(msg.get("trainingpayment.unpay.settled.refund"));
            }
            paymentRepository.deleteByEnrollmentIdAndYearMonth(enrollmentId, month);
            // Reverting the payment cancels any refund that was owed for this month — nothing was actually paid.
            refundService.revokeForPayment(enrollmentId, requestMonth);
        }
    }

    /** How many days before a month starts it opens for payment (matches the next-month estimate window). */
    private static final int PAYMENT_OPENS_DAYS_BEFORE = 7;

    /** Rejects paying a month too early — before its payment window (last week of the previous month) opens. */
    private void requireMonthOpenForPayment(YearMonth month) {
        var opensOn = month.atDay(1).minusDays(PAYMENT_OPENS_DAYS_BEFORE);
        if (java.time.LocalDate.now().isBefore(opensOn)) {
            throw new IllegalStateException(msg.get("trainingpayment.too.early"));
        }
    }

    /** Rejects paying {@code month} while an earlier covered payable month (from the current month) is unpaid. */
    private void requireEarlierMonthsPaid(TrainingEnrollment te, YearMonth month, java.util.Set<String> paidMonths) {
        var current = YearMonth.now();
        var start = te.getStartMonth();
        var firstPayable = start.isAfter(current) ? start : current;   // max(current, start)
        var end = te.getEndMonth();
        for (var e = firstPayable; e.isBefore(month); e = e.plusMonths(1)) {
            if (end != null && e.isAfter(end)) {
                break;
            }
            if (!paidMonths.contains(e.toString())) {
                throw new IllegalStateException(msg.get("trainingpayment.pay.out.of.order"));
            }
        }
    }
}
