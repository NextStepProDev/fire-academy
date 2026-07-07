package pl.fireacademy.api.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.fireacademy.api.NotFoundException;
import pl.fireacademy.api.admin.TrainingSlotDtos.*;
import pl.fireacademy.domain.training.*;
import pl.fireacademy.domain.user.UserRepository;
import pl.fireacademy.infrastructure.i18n.MessageService;
import pl.fireacademy.infrastructure.mail.TrainingMailService;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
public class AdminTrainingEnrollmentService {

    private final TrainingSlotRepository slotRepository;
    private final TrainingEnrollmentRepository enrollmentRepository;
    private final TrainingPaymentRepository paymentRepository;
    private final TrainingRefundRepository refundRepository;
    private final TrainingBillingService billing;
    private final TrainingCreditService creditService;
    private final TrainingRefundService refundService;
    private final UserRepository userRepository;
    private final MessageService msg;
    private final TrainingMailService trainingMail;

    public AdminTrainingEnrollmentService(TrainingSlotRepository slotRepository,
                                          TrainingEnrollmentRepository enrollmentRepository,
                                          TrainingPaymentRepository paymentRepository,
                                          TrainingRefundRepository refundRepository,
                                          TrainingBillingService billing,
                                          TrainingCreditService creditService,
                                          TrainingRefundService refundService,
                                          UserRepository userRepository,
                                          MessageService msg,
                                          TrainingMailService trainingMail) {
        this.slotRepository = slotRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
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

        // An explicit start day must fall inside the start month (the first month is then billed from that day).
        var billableFrom = request.billableFrom();
        if (billableFrom != null && !YearMonth.from(billableFrom).equals(start)) {
            throw new IllegalArgumentException(msg.get("trainingenrollment.start.invalid"));
        }

        if (enrollmentRepository.existsOverlapping(user.getId(), slotId, start.toString(),
                end != null ? end.toString() : null)) {
            throw new IllegalStateException(msg.get("trainingenrollment.duplicate"));
        }

        // Admin may add participants beyond the capacity limit (deliberate overbooking) — no capacity check.
        var enrollment = new TrainingEnrollment(slot, user, start, end);
        enrollment.setBillableFrom(billableFrom);   // set before saving so the first-month bill prorates from it
        var saved = enrollmentRepository.save(enrollment);

        // G: notify the user that the organizer added them.
        var info = slotInfo(slot);
        var billingMonth = start.isAfter(current) ? start : current;
        int sessions = billing.sessions(saved, billingMonth);
        var amount = slot.getPrice() != null
                ? slot.getPrice().multiply(java.math.BigDecimal.valueOf(sessions)) : null;
        trainingMail.sendAdminAddedConfirmation(user.getEmail(), user.getFirstName(), info,
                start, request.months(), billingMonth, sessions, amount);
    }

    /**
     * Removes a subscriber, effective {@code effectiveFrom} (defaults to today; may be backdated within the
     * subscription). Every paid session from that date on that the subscriber will no longer attend is registered as
     * a pending refund the organizer resolves later in the "Zwroty" tab. If nothing was paid ahead — no refund arises
     * and no surplus credit is owed — the subscription is hard-deleted and leaves no trace on the user's history
     * (the old behaviour). Otherwise it is kept as an ended record (it has to anchor the refunds / the credit) and
     * simply stops covering the months after the removal, freeing the spot going forward.
     */
    @Transactional
    public void remove(UUID enrollmentId, @Nullable LocalDate effectiveFrom) {
        var te = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new NotFoundException(msg.get("trainingenrollment.not.found")));
        var today = LocalDate.now();
        var effective = effectiveFrom != null ? effectiveFrom : today;
        // Immediate effect or backdated within the subscription — never a future date, never before it started.
        if (effective.isAfter(today) || effective.isBefore(te.getStartMonth().atDay(1))) {
            throw new IllegalArgumentException(msg.get("trainingenrollment.remove.date.invalid"));
        }
        var user = te.getUser();
        var info = slotInfo(te.getSlot());
        removeEnrollment(te, effective);
        // H: notify the user that the organizer removed them from this one training.
        trainingMail.sendAdminRemoved(user.getEmail(), user.getFirstName(), info);
    }

    /**
     * Removes a subscriber from ALL their live trainings at once, effective {@code effectiveFrom} (defaults to today;
     * may be backdated, never a future date). Each subscription is ended exactly as in {@link #remove}: paid, unused
     * sessions from that date on become pending refunds and the record is kept as an archive, or — if nothing was
     * paid ahead — hard-deleted without a trace. The user gets ONE grouped e-mail listing everything they were taken
     * off. For a training that starts after the removal date the effective date is its own start (you cannot remove
     * someone from before a training even began).
     */
    @Transactional
    public void removeAllForUser(UUID userId, @Nullable LocalDate effectiveFrom) {
        var today = LocalDate.now();
        var effective = effectiveFrom != null ? effectiveFrom : today;
        if (effective.isAfter(today)) {
            throw new IllegalArgumentException(msg.get("trainingenrollment.remove.date.invalid"));
        }
        var enrollments = enrollmentRepository.findActiveByUser(userId, YearMonth.now().toString());
        if (enrollments.isEmpty()) {
            throw new NotFoundException(msg.get("trainingenrollment.not.found"));
        }
        var user = enrollments.getFirst().getUser();
        var lines = new java.util.ArrayList<TrainingMailService.SessionLine>();
        var totalRefund = BigDecimal.ZERO;
        for (var te : enrollments) {
            var eff = effective.isBefore(te.getStartMonth().atDay(1)) ? te.getStartMonth().atDay(1) : effective;
            var slot = te.getSlot();
            lines.add(new TrainingMailService.SessionLine(slot.getEventType().getName(),
                    slot.getStartTime(), slot.getEndTime()));
            totalRefund = totalRefund.add(removeEnrollment(te, eff));
        }
        // ONE grouped e-mail per person (like a day-off / instructor-day cancellation), not one per training.
        trainingMail.sendAdminRemovedAll(user.getEmail(), user.getFirstName(), lines,
                totalRefund.signum() > 0 ? totalRefund : null);
    }

    /**
     * Ends one subscription effective {@code effective}: registers refunds for its paid, unused sessions and keeps it
     * as an archived record, or hard-deletes it when nothing is owed. Does not send any e-mail — the caller decides
     * whether to notify per-training or with one grouped message. Returns the money refunded for this training (0 when
     * nothing was owed), so a bulk caller can sum a grouped total.
     */
    private BigDecimal removeEnrollment(TrainingEnrollment te, LocalDate effective) {
        int refunds = refundService.registerForEnrollmentRemoval(te, effective, msg.get("trainingrefund.label.removal"));
        var price = te.getSlot().getPrice();
        var refundAmount = price != null ? price.multiply(BigDecimal.valueOf(refunds)) : BigDecimal.ZERO;

        // Nothing paid ahead and no surplus owed → hard-delete: the row (and its past payment rows) cascades away
        // entirely, leaving no trace.
        if (refunds == 0 && creditService.availableBalance(te.getId()).signum() == 0) {
            enrollmentRepository.delete(te);
            return refundAmount;
        }

        // Money is owed back (pending refunds) or credit is still held → keep the subscription as an ended record so
        // the refunds stay anchored to it. End coverage at the removal: the effective month is retained only if the
        // subscriber still attended part of it (a session before the removal date), otherwise it ends the month before.
        var fromMonth = YearMonth.from(effective);
        boolean attendedPartOfMonth = billing.billableSessionDates(te, fromMonth, fromMonth.atDay(1)).stream()
                .anyMatch(d -> d.isBefore(effective));
        var endMonth = attendedPartOfMonth ? fromMonth : fromMonth.minusMonths(1);
        // Never end before the subscription started (a removal on/before its very first session leaves the start
        // month as the minimal valid span) — the period check constraint requires end_month >= start_month.
        te.setEndMonth(endMonth.isBefore(te.getStartMonth()) ? te.getStartMonth() : endMonth);
        te.setExpiryNotified(true);   // the daily scheduler must not also send a generic "subscription expired" mail
        return refundAmount;
    }

    /**
     * The training-focused profile of one client: their subscriptions (active + ended), every month they paid for
     * (with when and how much surplus it absorbed), and every refund with how it was resolved — plus the unused
     * surplus still owed. Everything about this client's trainings in one place.
     */
    @Transactional(readOnly = true)
    public TrainingUserHistoryDtos.TrainingUserHistory getUserHistory(UUID userId) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(msg.get("trainingenrollment.user.not.found")));
        var current = YearMonth.now();

        var subscriptions = new java.util.ArrayList<TrainingUserHistoryDtos.Subscription>();
        var creditBalance = BigDecimal.ZERO;
        for (var te : enrollmentRepository.findAllByUserWithSlot(userId)) {
            var slot = te.getSlot();
            var instr = slot.getInstructor();
            boolean active = te.getEndMonth() == null || !te.getEndMonth().isBefore(current);
            subscriptions.add(new TrainingUserHistoryDtos.Subscription(
                    te.getId(), slot.getEventType().getName(),
                    instr != null ? instr.getFirstName() + " " + instr.getLastName() : null,
                    slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime(), slot.getPrice(),
                    te.getStartMonth().toString(), te.getEndMonth() != null ? te.getEndMonth().toString() : null,
                    te.getBillableFrom(), te.getCreatedAt(), active));
            creditBalance = creditBalance.add(creditService.availableBalance(te.getId()));
        }

        var paymentRows = paymentRepository.findByUserWithSlot(userId);
        var payments = paymentRows.stream()
                .map(p -> new TrainingUserHistoryDtos.Payment(
                        p.getEnrollment().getSlot().getEventType().getName(),
                        p.getYearMonth().toString(), p.getAmount(), p.getCreditApplied(), p.isPinned(), p.getCreatedAt()))
                .toList();

        var refundRows = refundRepository.findByUserWithSlot(userId);
        // Reconstruct which paid month each CREDITED surplus actually discounted (the ledger only stores it as a
        // balance). Per subscription: credited refunds funnel — earliest source first — into the paid months that
        // absorbed surplus (credit_applied), in calendar order.
        var consumedMonthByRefund = mapCreditConsumption(refundRows, paymentRows);
        var refunds = refundRows.stream()
                .map(r -> new TrainingUserHistoryDtos.Refund(
                        r.getEnrollment().getSlot().getEventType().getName(),
                        r.getSessionDate(), r.getAmount(), r.getType(), r.getLabel(),
                        r.getCreatedAt(), r.getSettledAt(), r.getSettlementType(),
                        consumedMonthByRefund.get(r.getId())))
                .toList();

        return new TrainingUserHistoryDtos.TrainingUserHistory(
                userId, user.getFirstName(), user.getLastName(), user.getEmail(), user.getPhone(),
                user.getCreatedAt(), creditBalance, subscriptions, payments, refunds);
    }

    /**
     * Which paid month each CREDITED refund's surplus discounted. The ledger stores surplus only as a balance, so we
     * reconstruct it: per subscription the credited refunds are matched — earliest source month first — to the paid
     * months that absorbed surplus (their frozen credit_applied), in calendar order.
     */
    private static java.util.Map<UUID, String> mapCreditConsumption(List<TrainingRefund> refunds,
                                                                     List<TrainingPayment> payments) {
        var creditedByEnrollment = new java.util.HashMap<UUID, List<CreditedRef>>();
        for (var r : refunds) {
            if (!TrainingRefund.SETTLEMENT_CREDITED.equals(r.getSettlementType())) {
                continue;
            }
            creditedByEnrollment.computeIfAbsent(r.getEnrollment().getId(), k -> new java.util.ArrayList<>())
                    .add(new CreditedRef(r.getId(), r.getYearMonth().toString(), r.getCreatedAt(), r.getAmount()));
        }
        var consumersByEnrollment = new java.util.HashMap<UUID, List<CreditConsumer>>();
        for (var p : payments) {
            if (p.getCreditApplied().signum() <= 0) {
                continue;
            }
            consumersByEnrollment.computeIfAbsent(p.getEnrollment().getId(), k -> new java.util.ArrayList<>())
                    .add(new CreditConsumer(p.getYearMonth().toString(), p.getCreditApplied()));
        }
        var result = new java.util.HashMap<UUID, String>();
        for (var e : creditedByEnrollment.entrySet()) {
            allocateCreditConsumption(e.getValue(),
                    consumersByEnrollment.getOrDefault(e.getKey(), List.of()), result);
        }
        return result;
    }

    /** One CREDITED refund's surplus, with the month it came from. */
    record CreditedRef(UUID id, String sourceMonth, java.time.Instant createdAt, BigDecimal amount) {}

    /** A paid month that absorbed surplus, with how much of it is still to be attributed to a source. */
    static final class CreditConsumer {
        final String month;
        BigDecimal remaining;
        CreditConsumer(String month, BigDecimal remaining) { this.month = month; this.remaining = remaining; }
    }

    /**
     * Attributes each credited surplus to the paid month(s) that absorbed it — earliest source into the earliest
     * paying month — recording each surplus's first funded month (the common case is one month). A surplus no paid
     * month absorbed is left unmapped (still available). Package-private for direct unit testing; does not mutate
     * its inputs.
     */
    static void allocateCreditConsumption(List<CreditedRef> credited, List<CreditConsumer> consumers,
                                          java.util.Map<UUID, String> out) {
        var sortedCredited = credited.stream()
                .sorted(java.util.Comparator.comparing(CreditedRef::sourceMonth).thenComparing(CreditedRef::createdAt))
                .toList();
        var queue = consumers.stream()
                .sorted(java.util.Comparator.comparing(c -> c.month))
                .map(c -> new CreditConsumer(c.month, c.remaining))
                .toList();
        for (var cr : sortedCredited) {
            var amt = cr.amount();
            String target = null;
            for (var c : queue) {
                if (amt.signum() <= 0) {
                    break;
                }
                if (c.remaining.signum() <= 0) {
                    continue;
                }
                var take = amt.min(c.remaining);
                if (target == null) {
                    target = c.month;
                }
                c.remaining = c.remaining.subtract(take);
                amt = amt.subtract(take);
            }
            if (target != null) {
                out.put(cr.id(), target);
            }
        }
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
                        te.getId(), te.getStartMonth(), te.getBillableFrom(), billing.partialStartDate(te, month),
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
