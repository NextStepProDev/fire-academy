package pl.fireacademy.domain.enrollment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.fireacademy.domain.enrollment.EnrollmentErasureService.ErasureResult;
import pl.fireacademy.domain.event.Event;
import pl.fireacademy.domain.event.EventCategory;
import pl.fireacademy.domain.user.User;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnrollmentErasureServiceTest {

    @Mock private EnrollmentRepository enrollmentRepository;
    @InjectMocks private EnrollmentErasureService service;

    @Test
    void shouldDeleteFutureAndAnonymizePast() {
        UUID userId = UUID.randomUUID();
        User user = new User("anna@test.com", "Anna", "Nowak", "123456789");

        Event futureEvent = new Event(EventCategory.CAMP, "Obóz", LocalDate.now().plusDays(5));
        Event pastEvent = new Event(EventCategory.COURSE, "Szkolenie", LocalDate.now().minusDays(20));
        Enrollment future = Enrollment.forUser(futureEvent, user, null, false);
        Enrollment past = Enrollment.forUser(pastEvent, user, null, false);

        when(enrollmentRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(future, past));

        ErasureResult result = service.eraseForUser(userId);

        assertEquals(1, result.freed());
        assertEquals(1, result.anonymized());
        // The freed event is exposed for the organizer notification.
        assertEquals(1, result.freedEvents().size());
        assertSame(futureEvent, result.freedEvents().getFirst());
        verify(enrollmentRepository).deleteAll(List.of(future));

        ArgumentCaptor<List<Enrollment>> captor = ArgumentCaptor.captor();
        verify(enrollmentRepository).saveAll(captor.capture());
        Enrollment anonymized = captor.getValue().getFirst();
        assertTrue(anonymized.isAnonymized());
        assertNull(anonymized.getUser());
    }

    @Test
    void shouldDoNothingWhenNoEnrollments() {
        UUID userId = UUID.randomUUID();
        when(enrollmentRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());

        ErasureResult result = service.eraseForUser(userId);

        assertEquals(0, result.freed());
        assertEquals(0, result.anonymized());
        assertTrue(result.freedEvents().isEmpty());
        verify(enrollmentRepository, never()).deleteAll(any());
        verify(enrollmentRepository, never()).saveAll(any());
    }
}
