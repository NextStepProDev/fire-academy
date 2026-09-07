package pl.fireacademy.api.admin;

import org.junit.jupiter.api.Test;
import pl.fireacademy.domain.training.TrainingCreditService;
import pl.fireacademy.domain.training.TrainingEnrollment;
import pl.fireacademy.domain.training.TrainingEnrollmentRepository;
import pl.fireacademy.domain.training.TrainingRefund;
import pl.fireacademy.domain.training.TrainingRefundRepository;
import pl.fireacademy.infrastructure.i18n.MessageService;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The safety net: a credited surplus already spent on a paid month cannot be un-credited. */
class AdminTrainingRefundServiceTest {

    private final TrainingRefundRepository refunds = mock(TrainingRefundRepository.class);
    private final TrainingEnrollmentRepository enrollments = mock(TrainingEnrollmentRepository.class);
    private final TrainingCreditService credit = mock(TrainingCreditService.class);
    private final MessageService msg = mock(MessageService.class);
    private final AdminTrainingRefundService service = new AdminTrainingRefundService(refunds, enrollments, credit, msg);

    private TrainingRefund creditedRefund(UUID enrollmentId) {
        var te = mock(TrainingEnrollment.class);
        when(te.getId()).thenReturn(enrollmentId);
        var refund = mock(TrainingRefund.class);
        when(refund.getSettlementType()).thenReturn(TrainingRefund.SETTLEMENT_CREDITED);
        when(refund.getAmount()).thenReturn(BigDecimal.valueOf(60));
        when(refund.getEnrollment()).thenReturn(te);
        return refund;
    }

    @Test
    void blocksUncreditingWhenSurplusAlreadyConsumed() {
        var id = UUID.randomUUID();
        var enrollmentId = UUID.randomUUID();
        var refund = creditedRefund(enrollmentId);
        when(refunds.findById(id)).thenReturn(Optional.of(refund));
        when(credit.rawBalance(enrollmentId)).thenReturn(BigDecimal.ZERO);   // 0 left → 60 already spent

        assertThrows(IllegalStateException.class, () -> service.unsettle(id));
        verify(refund, never()).setSettlementType(any());
    }

    @Test
    void allowsUncreditingWhenSurplusStillAvailable() {
        var id = UUID.randomUUID();
        var enrollmentId = UUID.randomUUID();
        var refund = creditedRefund(enrollmentId);
        when(refunds.findById(id)).thenReturn(Optional.of(refund));
        when(credit.rawBalance(enrollmentId)).thenReturn(BigDecimal.valueOf(60));   // full surplus intact

        service.unsettle(id);
        verify(refund).setSettledAt(null);
        verify(refund).setSettlementType(null);
    }

    @Test
    void settlesRefundAsMadeUp() {
        var id = UUID.randomUUID();
        var refund = mock(TrainingRefund.class);
        when(refunds.findById(id)).thenReturn(Optional.of(refund));

        service.settle(id, TrainingRefund.SETTLEMENT_MADE_UP);

        verify(refund).setSettledAt(any());
        verify(refund).setSettlementType(TrainingRefund.SETTLEMENT_MADE_UP);
    }

    @Test
    void rejectsUnknownSettlementType() {
        assertThrows(IllegalArgumentException.class, () -> service.settle(UUID.randomUUID(), "BOGUS"));
    }

    @Test
    void unsettlingMadeUpNeverConsultsCreditAndIsAlwaysAllowed() {
        var id = UUID.randomUUID();
        var refund = mock(TrainingRefund.class);
        when(refund.getSettlementType()).thenReturn(TrainingRefund.SETTLEMENT_MADE_UP);
        when(refunds.findById(id)).thenReturn(Optional.of(refund));

        service.unsettle(id);

        verify(refund).setSettledAt(null);
        verify(refund).setSettlementType(null);
        verifyNoInteractions(credit);   // made-up has no cash and no surplus — the credit guard never runs
    }

    /**
     * Resolving an already-resolved refund is a second answer, not a correction — and the two answers
     * spend different money. REFUNDED means cash left the till; re-settling it as CREDITED puts the
     * same amount back on the ledger as a discount against the next unpaid month, so the club pays it
     * twice. unsettle already refuses to pull back a consumed credit; this is that same invariant
     * from the other side, which was missing.
     */
    @Test
    void blocksSettlingARefundThatIsAlreadySettled() {
        var id = UUID.randomUUID();
        var refund = mock(TrainingRefund.class);
        when(refund.getSettledAt()).thenReturn(java.time.Instant.now());
        when(refunds.findById(id)).thenReturn(Optional.of(refund));
        when(msg.get("trainingrefund.already.settled")).thenReturn("Już rozliczony");

        assertThrows(IllegalStateException.class,
            () -> service.settle(id, TrainingRefund.SETTLEMENT_CREDITED));
        verify(refund, never()).setSettlementType(any());
        verify(refund, never()).setSettledAt(any());
    }

    /** ...and a pending one still settles, so the guard is a guard and not a wall. */
    @Test
    void allowsSettlingAPendingRefund() {
        var id = UUID.randomUUID();
        var refund = mock(TrainingRefund.class);
        when(refund.getSettledAt()).thenReturn(null);
        when(refunds.findById(id)).thenReturn(Optional.of(refund));

        service.settle(id, TrainingRefund.SETTLEMENT_REFUNDED);

        verify(refund).setSettlementType(TrainingRefund.SETTLEMENT_REFUNDED);
    }
}
