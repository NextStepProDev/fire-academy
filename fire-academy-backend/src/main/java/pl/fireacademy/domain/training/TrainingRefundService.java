package pl.fireacademy.domain.training;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Keeps the refund ledger in sync with session cancellations. A refund arises only when a session is cancelled
 * in a month a subscriber has <em>already paid</em> for — each such cancelled session is worth one session's
 * price back. Undoing the cancellation before settlement removes the pending refund again.
 *
 * <p>A cancellable session is always in the future, and the payment that covered it was made earlier, so the
 * cancelled session was necessarily part of what the subscriber paid — the single-session price is the refund.
 *
 * <p>Three mechanisms can close the same date (a day off, a single-session cancellation, a scheduled
 * deactivation), and they may stack. The ledger is cause-aware in both directions: registering skips a date
 * already closed by ANOTHER mechanism (that session was never part of what a later payer paid — the bill
 * already excluded it, or the first closure already produced the refund), and revoking touches only refunds
 * whose date actually comes back to life (still closed by another mechanism → the refund stays owed).
 */
@Service
public class TrainingRefundService {

    /** Which mechanism closed (or is being reopened for) a slot's session date. */
    public enum ClosureCause { SINGLE_SESSION, HOLIDAY, DEACTIVATION }

    private final TrainingEnrollmentRepository enrollmentRepository;
    private final TrainingPaymentRepository paymentRepository;
    private final TrainingRefundRepository refundRepository;
    private final TrainingSlotRepository slotRepository;
    private final TrainingHolidayRepository holidayRepository;
    private final TrainingCancelledSessionRepository cancelledSessionRepository;
    private final TrainingCreditService creditService;
    private final TrainingBillingService billingService;

    public TrainingRefundService(TrainingEnrollmentRepository enrollmentRepository,
                                 TrainingPaymentRepository paymentRepository,
                                 TrainingRefundRepository refundRepository,
                                 TrainingSlotRepository slotRepository,
                                 TrainingHolidayRepository holidayRepository,
                                 TrainingCancelledSessionRepository cancelledSessionRepository,
                                 TrainingCreditService creditService,
                                 TrainingBillingService billingService) {
        this.enrollmentRepository = enrollmentRepository;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.slotRepository = slotRepository;
        this.holidayRepository = holidayRepository;
        this.cancelledSessionRepository = cancelledSessionRepository;
        this.creditService = creditService;
        this.billingService = billingService;
    }

    /** True if the date is closed for the slot by a mechanism other than {@code cause}. */
    private boolean closedByOtherCause(TrainingSlot slot, LocalDate date, ClosureCause cause) {
        if (cause != ClosureCause.HOLIDAY && holidayRepository.existsByHolidayDate(date)) {
            return true;
        }
        if (cause != ClosureCause.SINGLE_SESSION
                && cancelledSessionRepository.existsBySlotIdAndSessionDate(slot.getId(), date)) {
            return true;
        }
        return cause != ClosureCause.DEACTIVATION && slot.getDeactivatedFrom() != null
                && !slot.getDeactivatedFrom().isAfter(date);
    }

    /** Refunds that would actually come back to life when the {@code undone} closure is reversed. */
    private List<TrainingRefund> refundsToRevert(List<TrainingRefund> refunds, ClosureCause undone) {
        return refunds.stream()
                .filter(r -> !closedByOtherCause(r.getEnrollment().getSlot(), r.getSessionDate(), undone))
                .toList();
    }

    /**
     * Enrollment ids that still have an unresolved refund for a slot's session date. Drives the "do zwrotu" badge in
     * the cancelled-sessions overview: it reflects the refund ledger (still owed?), not merely "was the month paid".
     */
    @Transactional(readOnly = true)
    public java.util.Set<UUID> pendingRefundEnrollmentIds(UUID slotId, LocalDate date) {
        return new HashSet<>(refundRepository.findPendingEnrollmentIdsForSlotAndDate(slotId, date));
    }

    /** A single session of one slot was closed by {@code cause} — register refunds for its paid subscribers. */
    @Transactional
    public void registerForSlotSession(TrainingSlot slot, LocalDate date, String type, @Nullable String label,
                                       ClosureCause cause) {
        if (slot.getPrice() == null) {
            return;
        }
        // Already closed by another mechanism → the session was not part of anyone's live bill anymore
        // (a subscriber who paid before the first closure got their refund then; one who paid after it
        // paid a bill that already excluded this date). Registering again would refund unpaid money.
        if (closedByOtherCause(slot, date, cause)) {
            return;
        }
        var month = YearMonth.from(date).toString();
        var enrollments = enrollmentRepository.findCoveringForSlot(slot.getId(), month);
        if (enrollments.isEmpty()) {
            return;
        }
        var ids = enrollments.stream().map(TrainingEnrollment::getId).toList();
        var paid = new HashSet<>(paymentRepository.findPaidEnrollmentIds(ids, month));
        for (var te : enrollments) {
            if (!paid.contains(te.getId())) {
                continue;
            }
            // The date must actually be part of what this subscriber paid: a mid-month joiner (or an organizer's
            // billableFrom override) is not billed for sessions before their start day, so cancelling one owes no
            // refund. Without this, cancelling a session before the start day invents a refund for money never paid.
            if (!billingService.isBillableSession(te, date)) {
                continue;
            }
            if (refundRepository.existsByEnrollmentIdAndSessionDate(te.getId(), date)) {
                continue;
            }
            // Safety net: whatever combination of closures led here, refunds for one paid month must never
            // exceed what that month's payment actually collected (a frozen payment.amount is the ceiling;
            // legacy rows without one, pre-V26, are left uncapped since there is nothing to cap against).
            var payment = paymentRepository.findByEnrollmentIdAndYearMonth(te.getId(), month).orElse(null);
            if (payment != null && payment.getAmount() != null) {
                var alreadyRefunded = refundRepository.sumForEnrollmentAndMonth(te.getId(), month);
                if (alreadyRefunded.add(slot.getPrice()).compareTo(payment.getAmount()) > 0) {
                    continue;
                }
            }
            refundRepository.save(new TrainingRefund(te, date, slot.getPrice(), type, label));
        }
    }

    /** A day off was added — register refunds for paid subscribers of every affected slot on that weekday. */
    @Transactional
    public void registerForHoliday(LocalDate date, @Nullable String label) {
        for (var slot : slotRepository.findActiveByDayOfWeek(date.getDayOfWeek().getValue())) {
            registerForSlotSession(slot, date, TrainingRefund.TYPE_HOLIDAY, label, ClosureCause.HOLIDAY);
        }
    }

    /** A closure of one slot's date was undone — drop the refunds that come back to life (never cash ones). */
    @Transactional
    public void revokeForSlotSession(UUID slotId, LocalDate date, ClosureCause undone) {
        deleteReversible(refundsToRevert(refundRepository.findBySlotAndDate(slotId, date), undone));
    }

    /** A day off was removed — drop the refunds that come back to life across all slots of that date. */
    @Transactional
    public void revokeForHoliday(LocalDate date) {
        deleteReversible(refundsToRevert(refundRepository.findByDate(date), ClosureCause.HOLIDAY));
    }

    // ── Restore guards: a refund is reversible unless the cash was already handed back, or the credited
    //    surplus was already spent on a paid month. Only refunds the undo would actually revive count —
    //    one kept alive by another closure is left untouched, so it never blocks. The caller blocks the
    //    restore when these return true. ──

    /** True if a cash refund (REFUNDED) the undo would revive was already paid out — restore must be blocked. */
    @Transactional(readOnly = true)
    public boolean hasCashRefundForSlotSession(UUID slotId, LocalDate date, ClosureCause undone) {
        return anyCash(refundsToRevert(refundRepository.findBySlotAndDate(slotId, date), undone));
    }

    /** True if a cash refund the day-off removal would revive was already paid out — removal must be blocked. */
    @Transactional(readOnly = true)
    public boolean hasCashRefundForDate(LocalDate date) {
        return anyCash(refundsToRevert(refundRepository.findByDate(date), ClosureCause.HOLIDAY));
    }

    /** True if a credited surplus the undo would revive was already consumed by a paid month. */
    @Transactional(readOnly = true)
    public boolean hasConsumedCreditForSlotSession(UUID slotId, LocalDate date, ClosureCause undone) {
        return anyConsumedCredit(refundsToRevert(refundRepository.findBySlotAndDate(slotId, date), undone));
    }

    /** True if a credited surplus the day-off removal would revive was already consumed by a paid month. */
    @Transactional(readOnly = true)
    public boolean hasConsumedCreditForDate(LocalDate date) {
        return anyConsumedCredit(refundsToRevert(refundRepository.findByDate(date), ClosureCause.HOLIDAY));
    }

    private boolean anyCash(List<TrainingRefund> refunds) {
        return refunds.stream().anyMatch(r -> TrainingRefund.SETTLEMENT_REFUNDED.equals(r.getSettlementType()));
    }

    private boolean anyConsumedCredit(List<TrainingRefund> refunds) {
        var creditedByEnrollment = new java.util.HashMap<UUID, java.math.BigDecimal>();
        for (var r : refunds) {
            if (TrainingRefund.SETTLEMENT_CREDITED.equals(r.getSettlementType())) {
                creditedByEnrollment.merge(r.getEnrollment().getId(), r.getAmount(), java.math.BigDecimal::add);
            }
        }
        return creditedByEnrollment.entrySet().stream()
                .anyMatch(e -> creditService.rawBalance(e.getKey()).compareTo(e.getValue()) < 0);
    }

    /** Deletes pending and credited refunds (both reversible); leaves cash refunds (blocked upstream) untouched. */
    private void deleteReversible(List<TrainingRefund> refunds) {
        refundRepository.deleteAll(refunds.stream()
                .filter(r -> !TrainingRefund.SETTLEMENT_REFUNDED.equals(r.getSettlementType()))
                .toList());
    }

    /** A month's payment was reverted — drop pending refunds tied to it (nothing was actually paid). */
    @Transactional
    public void revokeForPayment(UUID enrollmentId, YearMonth month) {
        refundRepository.deleteAll(refundRepository.findPendingByEnrollmentAndMonth(enrollmentId, month.toString()));
    }

    /**
     * True if the month has an already-settled refund. Un-paying such a month must be blocked: the cash was
     * handed back (REFUNDED) or the surplus credited (CREDITED) against a payment that would no longer exist —
     * the admin has to unsettle the refund first (where the credit-consumed safety net applies).
     */
    @Transactional(readOnly = true)
    public boolean hasSettledForMonth(UUID enrollmentId, YearMonth month) {
        return refundRepository.existsSettledByEnrollmentAndMonth(enrollmentId, month.toString());
    }
}
