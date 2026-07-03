package pl.fireacademy.domain.training;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the surplus-credit engine (money-critical). Repos are mocked so the credit maths is
 * verified deterministically, independent of the wall clock and the time-gated payment endpoint.
 */
class TrainingCreditServiceTest {

    private static final UUID ID = UUID.randomUUID();
    private final YearMonth current = YearMonth.now();

    private final TrainingRefundRepository refunds = mock(TrainingRefundRepository.class);
    private final TrainingPaymentRepository payments = mock(TrainingPaymentRepository.class);
    private final TrainingBillingService billing = mock(TrainingBillingService.class);
    private final TrainingCreditService credit = new TrainingCreditService(refunds, payments, billing);

    private TrainingEnrollment enrollment(YearMonth start) {
        var slot = mock(TrainingSlot.class);
        when(slot.getPrice()).thenReturn(BigDecimal.valueOf(60));
        var te = mock(TrainingEnrollment.class);
        when(te.getId()).thenReturn(ID);
        when(te.getSlot()).thenReturn(slot);
        when(te.getStartMonth()).thenReturn(start);
        when(te.getEndMonth()).thenReturn(null);
        when(billing.sessions(eq(te), any(YearMonth.class))).thenReturn(4);   // every month costs 4 × 60 = 240
        return te;
    }

    /** amount credited, amount already consumed, source months of the surplus, months already paid. */
    private void ledger(String credited, String consumed, List<String> sourceMonths, List<String> paidMonths) {
        when(refunds.sumCreditedForEnrollment(ID)).thenReturn(new BigDecimal(credited));
        when(payments.sumCreditAppliedForEnrollment(ID)).thenReturn(new BigDecimal(consumed));
        when(refunds.creditedMonthsForEnrollment(ID)).thenReturn(sourceMonths);
        when(payments.findPaidMonths(ID)).thenReturn(paidMonths);
        for (String m : paidMonths) {
            when(payments.findByEnrollmentIdAndYearMonth(ID, m))
                    .thenReturn(java.util.Optional.of(mock(TrainingPayment.class)));
        }
    }

    @Test
    void surplusNeverDiscountsAMonthEarlierThanItsSource() {
        var aug = current.plusMonths(1);
        var te = enrollment(current);                                     // covers current…
        ledger("60", "0", List.of(aug.toString()), List.of(aug.toString())); // surplus from August (paid)

        // Earlier month (current) is NOT discounted…
        assertEquals(0, credit.liveAppliedFor(te, current).compareTo(BigDecimal.ZERO));
        // …the surplus rolls forward to the next unpaid month after August.
        assertEquals(0, credit.liveAppliedFor(te, current.plusMonths(2)).compareTo(BigDecimal.valueOf(60)));
    }

    @Test
    void surplusFillsTheNextUnpaidMonthAndOverflowsToTheFollowing() {
        var te = enrollment(current);
        // 450 zł surplus from July (paid). Each month costs 240.
        ledger("450", "0", List.of(current.toString()), List.of(current.toString()));

        // August absorbs a full month (240), September takes the remaining 210.
        assertEquals(0, credit.liveAppliedFor(te, current.plusMonths(1)).compareTo(BigDecimal.valueOf(240)));
        assertEquals(0, credit.liveAppliedFor(te, current.plusMonths(2)).compareTo(BigDecimal.valueOf(210)));
    }

    @Test
    void consumedSurplusIsNotCountedTwice() {
        var aug = current.plusMonths(1);
        var te = enrollment(current);
        // 450 credited, 240 already consumed by a paid August → 210 left for September.
        ledger("450", "240", List.of(current.toString()), List.of(current.toString(), aug.toString()));

        assertEquals(0, credit.availableBalance(ID).compareTo(BigDecimal.valueOf(210)));
        assertEquals(0, credit.liveAppliedFor(te, current.plusMonths(2)).compareTo(BigDecimal.valueOf(210)));
    }

    @Test
    void noSurplusMeansNoDiscount() {
        var te = enrollment(current);
        ledger("0", "0", List.of(), List.of(current.toString()));
        assertEquals(0, credit.liveAppliedFor(te, current.plusMonths(1)).compareTo(BigDecimal.ZERO));
    }
}
