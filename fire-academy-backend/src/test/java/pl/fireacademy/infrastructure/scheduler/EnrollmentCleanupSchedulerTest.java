package pl.fireacademy.infrastructure.scheduler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.fireacademy.domain.enrollment.EnrollmentRepository;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnrollmentCleanupSchedulerTest {

    @Mock private EnrollmentRepository enrollmentRepository;

    @InjectMocks private EnrollmentCleanupScheduler scheduler;

    @Test
    void shouldDeleteEnrollmentsOlderThanThreeYears() {
        when(enrollmentRepository.deleteByEventEndedBefore(any(LocalDate.class))).thenReturn(10);

        scheduler.cleanupExpiredEnrollments();

        ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(enrollmentRepository).deleteByEventEndedBefore(dateCaptor.capture());

        LocalDate cutoff = dateCaptor.getValue();
        LocalDate expected = LocalDate.now().minusYears(3);
        assertEquals(expected, cutoff);
    }

    @Test
    void shouldHandleNoEnrollmentsToDelete() {
        when(enrollmentRepository.deleteByEventEndedBefore(any(LocalDate.class))).thenReturn(0);

        scheduler.cleanupExpiredEnrollments();

        verify(enrollmentRepository).deleteByEventEndedBefore(any(LocalDate.class));
    }
}
