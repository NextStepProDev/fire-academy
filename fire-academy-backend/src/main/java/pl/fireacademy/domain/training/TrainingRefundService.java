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
 */
@Service
public class TrainingRefundService {

    private final TrainingEnrollmentRepository enrollmentRepository;
    private final TrainingPaymentRepository paymentRepository;
    private final TrainingRefundRepository refundRepository;
    private final TrainingSlotRepository slotRepository;
    private final TrainingCreditService creditService;

    public TrainingRefundService(TrainingEnrollmentRepository enrollmentRepository,
                                 TrainingPaymentRepository paymentRepository,
                                 TrainingRefundRepository refundRepository,
                                 TrainingSlotRepository slotRepository,
                                 TrainingCreditService creditService) {
        this.enrollmentRepository = enrollmentRepository;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.slotRepository = slotRepository;
        this.creditService = creditService;
    }

    /** A single session of one slot was cancelled — register refunds for its paid subscribers. */
    @Transactional
    public void registerForSlotSession(TrainingSlot slot, LocalDate date, String type, @Nullable String label) {
        if (slot.getPrice() == null) {
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
            if (refundRepository.existsByEnrollmentIdAndSessionDate(te.getId(), date)) {
                continue;
            }
            refundRepository.save(new TrainingRefund(te, date, slot.getPrice(), type, label));
        }
    }

    /** A day off was added — register refunds for paid subscribers of every active slot on that weekday. */
    @Transactional
    public void registerForHoliday(LocalDate date, @Nullable String label) {
        for (var slot : slotRepository.findActiveByDayOfWeek(date.getDayOfWeek().getValue())) {
            registerForSlotSession(slot, date, TrainingRefund.TYPE_HOLIDAY, label);
        }
    }

    /** A single-session cancellation was undone — drop its reversible refunds (pending + credited, never cash). */
    @Transactional
    public void revokeForSlotSession(UUID slotId, LocalDate date) {
        deleteReversible(refundRepository.findBySlotAndDate(slotId, date));
    }

    /** A day off was removed — drop reversible refunds for that date across all slots. */
    @Transactional
    public void revokeForHoliday(LocalDate date) {
        deleteReversible(refundRepository.findByDate(date));
    }

    // ── Restore guards: a refund is reversible unless the cash was already handed back, or the credited
    //    surplus was already spent on a paid month. The caller blocks the restore when these return true. ──

    /** True if a cash refund (REFUNDED) was already paid out for this cancelled session — restore must be blocked. */
    @Transactional(readOnly = true)
    public boolean hasCashRefundForSlotSession(UUID slotId, LocalDate date) {
        return anyCash(refundRepository.findBySlotAndDate(slotId, date));
    }

    /** True if a cash refund was already paid out for any slot on this day off — restore must be blocked. */
    @Transactional(readOnly = true)
    public boolean hasCashRefundForDate(LocalDate date) {
        return anyCash(refundRepository.findByDate(date));
    }

    /** True if a credited surplus from this session was already consumed by a paid month — cannot cleanly reverse. */
    @Transactional(readOnly = true)
    public boolean hasConsumedCreditForSlotSession(UUID slotId, LocalDate date) {
        return anyConsumedCredit(refundRepository.findBySlotAndDate(slotId, date));
    }

    /** True if a credited surplus from this day off was already consumed by a paid month — cannot cleanly reverse. */
    @Transactional(readOnly = true)
    public boolean hasConsumedCreditForDate(LocalDate date) {
        return anyConsumedCredit(refundRepository.findByDate(date));
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
}
