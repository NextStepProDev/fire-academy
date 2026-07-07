package pl.fireacademy.api.admin;

import org.junit.jupiter.api.Test;
import pl.fireacademy.api.admin.TrainingSlotDtos.SetPaymentRequest;
import pl.fireacademy.domain.training.TrainingBillingService;
import pl.fireacademy.domain.training.TrainingCreditService;
import pl.fireacademy.domain.training.TrainingEnrollment;
import pl.fireacademy.domain.training.TrainingEnrollmentRepository;
import pl.fireacademy.domain.training.TrainingPayment;
import pl.fireacademy.domain.training.TrainingPaymentRepository;
import pl.fireacademy.domain.training.TrainingRefundRepository;
import pl.fireacademy.domain.training.TrainingRefundService;
import pl.fireacademy.domain.training.TrainingSlotRepository;
import pl.fireacademy.domain.user.UserRepository;
import pl.fireacademy.infrastructure.i18n.MessageService;
import pl.fireacademy.infrastructure.mail.TrainingMailService;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Un-paying a month must not leave a gap: a month can be reverted only if no later month is still paid. */
class AdminTrainingEnrollmentServiceTest {

    private final TrainingEnrollmentRepository enrollments = mock(TrainingEnrollmentRepository.class);
    private final TrainingPaymentRepository payments = mock(TrainingPaymentRepository.class);
    private final TrainingRefundService refundService = mock(TrainingRefundService.class);
    private final MessageService msg = mock(MessageService.class);
    private final AdminTrainingEnrollmentService service = new AdminTrainingEnrollmentService(
            mock(TrainingSlotRepository.class), enrollments, payments, mock(TrainingRefundRepository.class),
            mock(TrainingBillingService.class), mock(TrainingCreditService.class), refundService,
            mock(UserRepository.class), msg, mock(TrainingMailService.class));

    /** A stand-in paid payment row (pinned state irrelevant here — the individual toggle clears rows regardless). */
    private static TrainingPayment paidRow() {
        return mock(TrainingPayment.class);
    }

    @Test
    void rejectsUnpayingAMonthWhileALaterMonthIsPaid() {
        var id = UUID.randomUUID();
        var current = YearMonth.now();
        var te = mock(TrainingEnrollment.class);
        when(te.getId()).thenReturn(id);
        when(enrollments.findById(id)).thenReturn(Optional.of(te));
        when(payments.findPaidMonths(id)).thenReturn(List.of(current.toString(), current.plusMonths(1).toString()));
        when(payments.findByEnrollmentIdAndYearMonth(id, current.toString())).thenReturn(Optional.of(paidRow()));

        assertThrows(IllegalStateException.class, () -> service.setPayment(id, new SetPaymentRequest(current, false)));
        verify(payments, never()).deleteByEnrollmentIdAndYearMonth(any(), any());
    }

    @Test
    void allowsUnpayingTheLatestPaidMonth() {
        var id = UUID.randomUUID();
        var current = YearMonth.now();
        var te = mock(TrainingEnrollment.class);
        when(te.getId()).thenReturn(id);
        when(enrollments.findById(id)).thenReturn(Optional.of(te));
        when(payments.findPaidMonths(id)).thenReturn(List.of(current.toString()));
        when(payments.findByEnrollmentIdAndYearMonth(id, current.toString())).thenReturn(Optional.of(paidRow()));

        service.setPayment(id, new SetPaymentRequest(current, false));
        verify(payments).deleteByEnrollmentIdAndYearMonth(id, current.toString());
        verify(refundService).revokeForPayment(eq(id), eq(current));
    }

    @Test
    void rejectsUnpayingAMonthWithAnAlreadySettledRefund() {
        // The refund was resolved against this payment (cash handed back / surplus credited) — reverting the
        // payment would leave that settlement hanging in the air. The refund must be unsettled first.
        var id = UUID.randomUUID();
        var current = YearMonth.now();
        var te = mock(TrainingEnrollment.class);
        when(te.getId()).thenReturn(id);
        when(enrollments.findById(id)).thenReturn(Optional.of(te));
        when(payments.findPaidMonths(id)).thenReturn(List.of(current.toString()));
        when(payments.findByEnrollmentIdAndYearMonth(id, current.toString())).thenReturn(Optional.of(paidRow()));
        when(refundService.hasSettledForMonth(id, current)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.setPayment(id, new SetPaymentRequest(current, false)));
        verify(payments, never()).deleteByEnrollmentIdAndYearMonth(any(), any());
    }

    // ── Credit-consumption reconstruction: which paid month a CREDITED surplus discounted ─────────────

    private static AdminTrainingEnrollmentService.CreditedRef credited(String sourceMonth, String amount) {
        return new AdminTrainingEnrollmentService.CreditedRef(
                UUID.randomUUID(), sourceMonth, java.time.Instant.now(), new java.math.BigDecimal(amount));
    }

    private static AdminTrainingEnrollmentService.CreditConsumer consumer(String month, String amount) {
        return new AdminTrainingEnrollmentService.CreditConsumer(month, new java.math.BigDecimal(amount));
    }

    @Test
    void mapsCreditedSurplusToTheMonthThatAbsorbedIt() {
        var r = credited("2026-06", "90");
        var out = new java.util.HashMap<UUID, String>();
        AdminTrainingEnrollmentService.allocateCreditConsumption(List.of(r), List.of(consumer("2026-07", "90")), out);
        assertEquals("2026-07", out.get(r.id()));
    }

    @Test
    void leavesUnconsumedSurplusUnmapped() {
        var r = credited("2026-06", "90");
        var out = new java.util.HashMap<UUID, String>();
        AdminTrainingEnrollmentService.allocateCreditConsumption(List.of(r), List.of(), out);
        assertNull(out.get(r.id()));
    }

    @Test
    void attributesEarliestSourceFirstWhenSurplusIsPartlyConsumed() {
        var june = credited("2026-06", "90");
        var july = credited("2026-07", "90");
        // Only one paid month absorbed 90 → the earlier source (June) is attributed to it; July stays available.
        var out = new java.util.HashMap<UUID, String>();
        AdminTrainingEnrollmentService.allocateCreditConsumption(
                List.of(july, june), List.of(consumer("2026-08", "90")), out);
        assertEquals("2026-08", out.get(june.id()));
        assertNull(out.get(july.id()));
    }
}
