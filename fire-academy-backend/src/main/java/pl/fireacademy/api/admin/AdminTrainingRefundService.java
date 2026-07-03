package pl.fireacademy.api.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.fireacademy.api.NotFoundException;
import pl.fireacademy.api.admin.TrainingSlotDtos.RefundEntry;
import pl.fireacademy.api.admin.TrainingSlotDtos.UnconsumedCreditEntry;
import pl.fireacademy.domain.training.TrainingCreditService;
import pl.fireacademy.domain.training.TrainingEnrollmentRepository;
import pl.fireacademy.domain.training.TrainingRefund;
import pl.fireacademy.domain.training.TrainingRefundRepository;
import pl.fireacademy.infrastructure.i18n.MessageService;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/** Admin view of money owed back to subscribers ("Zwroty"): pending refunds and the settled history. */
@Service
public class AdminTrainingRefundService {

    private final TrainingRefundRepository refundRepository;
    private final TrainingEnrollmentRepository enrollmentRepository;
    private final TrainingCreditService creditService;
    private final MessageService msg;

    public AdminTrainingRefundService(TrainingRefundRepository refundRepository,
                                      TrainingEnrollmentRepository enrollmentRepository,
                                      TrainingCreditService creditService, MessageService msg) {
        this.refundRepository = refundRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.creditService = creditService;
        this.msg = msg;
    }

    /**
     * Ended subscriptions still sitting on unconsumed CREDITED surplus — nothing applies it automatically once
     * there is no future month left to bill, so without this list the money would just sit forgotten in the ledger.
     */
    @Transactional(readOnly = true)
    public List<UnconsumedCreditEntry> listUnconsumedCredit() {
        return enrollmentRepository.findEndedWithCreditedRefund(YearMonth.now().toString()).stream()
                .map(te -> new java.util.AbstractMap.SimpleEntry<>(te, creditService.availableBalance(te.getId())))
                .filter(e -> e.getValue().signum() > 0)
                .map(e -> {
                    var te = e.getKey();
                    var u = te.getUser();
                    return new UnconsumedCreditEntry(
                            te.getId(), u.getId(), u.getFirstName(), u.getLastName(), u.getEmail(), u.getPhone(),
                            te.getSlot().getEventType().getName(), te.getEndMonth(), e.getValue()
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RefundEntry> list(boolean settled) {
        return refundRepository.findForAdmin(settled).stream().map(this::toEntry).toList();
    }

    /** Resolve a refund: {@code REFUNDED} (money handed back) or {@code CREDITED} (counted toward a month). */
    @Transactional
    public void settle(UUID id, String settlementType) {
        requireValidType(settlementType);
        var refund = find(id);
        refund.setSettledAt(Instant.now());
        refund.setSettlementType(settlementType);
    }

    /** Resolve ALL of one subscriber's pending refunds the same way (bulk "refund/credit everything for this person"). */
    @Transactional
    public void settleAllForUser(UUID userId, String settlementType) {
        requireValidType(settlementType);
        var now = Instant.now();
        for (var refund : refundRepository.findPendingByUser(userId)) {
            refund.setSettledAt(now);
            refund.setSettlementType(settlementType);
        }
    }

    private void requireValidType(String settlementType) {
        if (!TrainingRefund.SETTLEMENT_REFUNDED.equals(settlementType)
                && !TrainingRefund.SETTLEMENT_CREDITED.equals(settlementType)) {
            throw new IllegalArgumentException(msg.get("trainingrefund.settlement.invalid"));
        }
    }

    @Transactional
    public void unsettle(UUID id) {
        var refund = find(id);
        // A CREDITED surplus that already discounted a paid month cannot be pulled back — the discount is gone.
        // Available credit < this refund means part of it is already consumed; ask the admin to unpay first.
        if (TrainingRefund.SETTLEMENT_CREDITED.equals(refund.getSettlementType())
                && creditService.rawBalance(refund.getEnrollment().getId()).compareTo(refund.getAmount()) < 0) {
            throw new IllegalStateException(msg.get("trainingrefund.credit.consumed"));
        }
        refund.setSettledAt(null);
        refund.setSettlementType(null);
    }

    private TrainingRefund find(UUID id) {
        return refundRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(msg.get("trainingrefund.not.found")));
    }

    private RefundEntry toEntry(TrainingRefund r) {
        var te = r.getEnrollment();
        var u = te.getUser();
        return new RefundEntry(
                r.getId(), u.getId(), u.getFirstName(), u.getLastName(), u.getEmail(), u.getPhone(),
                te.getSlot().getEventType().getName(), r.getSessionDate(), r.getYearMonth(),
                r.getAmount(), r.getType(), r.getLabel(), r.getSettledAt(), r.getSettlementType()
        );
    }
}
