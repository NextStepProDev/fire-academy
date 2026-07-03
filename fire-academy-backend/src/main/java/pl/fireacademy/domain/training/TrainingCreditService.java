package pl.fireacademy.domain.training;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.Set;

/**
 * Turns a subscriber's surplus (refunds settled as CREDITED) into a real discount on future bills.
 *
 * <p>Model: the surplus is a single balance per subscription ({@code sum(CREDITED refunds) − sum(credit already
 * consumed by paid months)}). It is applied to the <b>nearest unpaid covered month at or after the month the
 * surplus came from</b> — never an earlier month (an August overpayment must not cheapen July). Whatever does
 * not fit that month's bill carries over to the next unpaid month, and so on — nothing is ever lost. A month's
 * bill is dynamic ({@code price × sessions}), so the credit is clamped to the bill live; when a month is finally
 * marked paid, the amount it actually absorbed is frozen on the payment row, so the same surplus can never be
 * counted twice across months.
 *
 * <p>REFUNDED refunds are cash handed back and never touch a bill — they are not part of this balance.
 */
@Service
public class TrainingCreditService {

    private final TrainingRefundRepository refundRepository;
    private final TrainingPaymentRepository paymentRepository;
    private final TrainingBillingService billing;

    public TrainingCreditService(TrainingRefundRepository refundRepository,
                                 TrainingPaymentRepository paymentRepository,
                                 TrainingBillingService billing) {
        this.refundRepository = refundRepository;
        this.paymentRepository = paymentRepository;
        this.billing = billing;
    }

    /** Surplus credited minus what paid months have already absorbed (may be negative if credits were retracted). */
    @Transactional(readOnly = true)
    public BigDecimal rawBalance(java.util.UUID enrollmentId) {
        return refundRepository.sumCreditedForEnrollment(enrollmentId)
                .subtract(paymentRepository.sumCreditAppliedForEnrollment(enrollmentId));
    }

    /** Surplus still available to discount future months (never negative). */
    @Transactional(readOnly = true)
    public BigDecimal availableBalance(java.util.UUID enrollmentId) {
        return rawBalance(enrollmentId).max(BigDecimal.ZERO);
    }

    /**
     * How much surplus discounts the given month's bill. For a paid month it is the amount frozen on the payment
     * row; for an unpaid month it is computed live by absorbing the balance across the earlier unpaid months first.
     */
    @Transactional(readOnly = true)
    public BigDecimal appliedFor(TrainingEnrollment te, YearMonth month) {
        var paid = paymentRepository.findByEnrollmentIdAndYearMonth(te.getId(), month.toString());
        if (paid.isPresent()) {
            return paid.get().getCreditApplied();
        }
        return liveAppliedFor(te, month);
    }

    /**
     * Surplus that would discount {@code month} if it were the next to be paid — used both for display of an
     * unpaid month and to freeze the amount when the month is marked paid (call before saving the payment).
     */
    @Transactional(readOnly = true)
    public BigDecimal liveAppliedFor(TrainingEnrollment te, YearMonth month) {
        var slot = te.getSlot();
        if (slot.getPrice() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal balance = availableBalance(te.getId());
        if (balance.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        var current = YearMonth.now();
        var start = te.getStartMonth();
        var first = start.isAfter(current) ? start : current;   // nearest applicable month = max(current, start)
        // The surplus may not discount a month earlier than the one it came from — an overpayment for August
        // rolls forward (to Sept, …), it never cheapens July. Floor the window at the earliest source month.
        var creditedMonths = refundRepository.creditedMonthsForEnrollment(te.getId());
        if (!creditedMonths.isEmpty()) {
            var source = YearMonth.parse(creditedMonths.get(0));
            if (source.isAfter(first)) {
                first = source;
            }
        }
        var end = te.getEndMonth();
        if (month.isBefore(first) || (end != null && month.isAfter(end))) {
            return BigDecimal.ZERO;                              // outside the covered, still-payable window
        }
        Set<String> paidMonths = new HashSet<>(paymentRepository.findPaidMonths(te.getId()));
        for (var m = first; m.isBefore(month); m = m.plusMonths(1)) {
            if (paidMonths.contains(m.toString())) {
                continue;                                        // already consumed — its share is out of `balance`
            }
            balance = balance.subtract(balance.min(cost(te, m)));
            if (balance.signum() <= 0) {
                return BigDecimal.ZERO;
            }
        }
        return balance.min(cost(te, month));
    }

    private BigDecimal cost(TrainingEnrollment te, YearMonth month) {
        return te.getSlot().getPrice().multiply(BigDecimal.valueOf(billing.sessions(te, month)));
    }
}
