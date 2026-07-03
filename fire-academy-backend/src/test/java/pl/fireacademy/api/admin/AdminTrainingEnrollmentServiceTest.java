package pl.fireacademy.api.admin;

import org.junit.jupiter.api.Test;
import pl.fireacademy.api.admin.TrainingSlotDtos.SetPaymentRequest;
import pl.fireacademy.domain.training.TrainingBillingService;
import pl.fireacademy.domain.training.TrainingCreditService;
import pl.fireacademy.domain.training.TrainingEnrollment;
import pl.fireacademy.domain.training.TrainingEnrollmentRepository;
import pl.fireacademy.domain.training.TrainingPaymentRepository;
import pl.fireacademy.domain.training.TrainingRefundService;
import pl.fireacademy.domain.training.TrainingSlotRepository;
import pl.fireacademy.domain.user.UserRepository;
import pl.fireacademy.infrastructure.i18n.MessageService;
import pl.fireacademy.infrastructure.mail.TrainingMailService;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
            mock(TrainingSlotRepository.class), enrollments, payments, mock(TrainingBillingService.class),
            mock(TrainingCreditService.class), refundService, mock(UserRepository.class), msg,
            mock(TrainingMailService.class));

    @Test
    void rejectsUnpayingAMonthWhileALaterMonthIsPaid() {
        var id = UUID.randomUUID();
        var current = YearMonth.now();
        var te = mock(TrainingEnrollment.class);
        when(te.getId()).thenReturn(id);
        when(enrollments.findById(id)).thenReturn(Optional.of(te));
        when(payments.findPaidMonths(id)).thenReturn(List.of(current.plusMonths(1).toString()));

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
        when(refundService.hasSettledForMonth(id, current)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.setPayment(id, new SetPaymentRequest(current, false)));
        verify(payments, never()).deleteByEnrollmentIdAndYearMonth(any(), any());
    }
}
