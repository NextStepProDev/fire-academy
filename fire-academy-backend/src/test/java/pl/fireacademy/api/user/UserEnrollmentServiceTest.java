package pl.fireacademy.api.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.fireacademy.api.NotFoundException;
import pl.fireacademy.api.user.UserEnrollmentDtos.EnrollRequest;
import pl.fireacademy.api.user.UserEnrollmentDtos.MyEnrollmentsResponse;
import pl.fireacademy.domain.enrollment.Enrollment;
import pl.fireacademy.domain.enrollment.EnrollmentRepository;
import pl.fireacademy.domain.event.Event;
import pl.fireacademy.domain.event.EventCategory;
import pl.fireacademy.domain.event.EventRepository;
import pl.fireacademy.domain.user.User;
import pl.fireacademy.domain.user.UserRepository;
import pl.fireacademy.infrastructure.i18n.MessageService;
import pl.fireacademy.infrastructure.mail.EnrollmentMailService;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserEnrollmentServiceTest {

    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private EventRepository eventRepository;
    @Mock private UserRepository userRepository;
    @Mock private EnrollmentMailService enrollmentMailService;
    @Mock private MessageService msg;

    @InjectMocks private UserEnrollmentService service;

    private UUID userId;
    private UUID eventId;
    private User user;
    private Event activeEvent;

    @BeforeEach
    void setUp() throws Exception {
        userId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        user = new User("anna@example.com", "Anna", "Nowak", "123456789");
        user.setPrivacyAcceptedAt(Instant.now());
        setId(user, userId);

        activeEvent = new Event(EventCategory.CAMP, "Obóz letni", LocalDate.now().plusDays(7));
        setId(activeEvent, eventId);
        activeEvent.setStartTime(LocalTime.of(10, 0));
        activeEvent.setMaxParticipants(6);
        activeEvent.setActive(true);
    }

    // --- enroll ---

    @Test
    void shouldEnrollSuccessfully() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(activeEvent));
        when(enrollmentRepository.existsByEventIdAndUserId(eventId, userId)).thenReturn(false);
        when(enrollmentRepository.countByEventId(eventId)).thenReturn(3L);

        service.enroll(userId, new EnrollRequest(eventId, null));

        verify(enrollmentRepository).save(any(Enrollment.class));
        verify(enrollmentMailService).sendEnrollmentConfirmation(
                eq("anna@example.com"), eq("Anna"), eq("Obóz letni"), any(), any(), any(), any());
        verify(enrollmentMailService).sendEnrollmentNotification(
                eq("Obóz letni"), eq("Anna Nowak"), eq("anna@example.com"),
                eq("123456789"), any(), any(), any(), any());
    }

    @Test
    void shouldThrowWhenPrivacyNotAccepted() {
        user.setPrivacyAcceptedAt(null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(msg.get("enrollment.privacy.required")).thenReturn("Zaakceptuj politykę");

        var ex = assertThrows(IllegalStateException.class,
                () -> service.enroll(userId, new EnrollRequest(eventId, null)));
        assertEquals("Zaakceptuj politykę", ex.getMessage());
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenUserHasNoPhone() {
        user.setPhone(null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(msg.get("enrollment.phone.required")).thenReturn("Uzupełnij telefon");

        var ex = assertThrows(IllegalStateException.class,
                () -> service.enroll(userId, new EnrollRequest(eventId, null)));
        assertEquals("Uzupełnij telefon", ex.getMessage());
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenEventInactive() {
        activeEvent.setActive(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(activeEvent));
        when(msg.get("enrollment.event.inactive")).thenReturn("Zamknięte");

        assertThrows(IllegalStateException.class,
                () -> service.enroll(userId, new EnrollRequest(eventId, null)));
    }

    @Test
    void shouldThrowWhenTooLate() throws Exception {
        Event soon = new Event(EventCategory.CAMP, "Obóz", LocalDate.now());
        setId(soon, eventId);
        soon.setStartTime(LocalTime.now().plusHours(1));
        soon.setActive(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(soon));
        when(msg.get("enrollment.too.late")).thenReturn("Za późno");

        assertThrows(IllegalStateException.class,
                () -> service.enroll(userId, new EnrollRequest(eventId, null)));
    }

    @Test
    void shouldThrowWhenDuplicate() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(activeEvent));
        when(enrollmentRepository.existsByEventIdAndUserId(eventId, userId)).thenReturn(true);
        when(msg.get("enrollment.duplicate")).thenReturn("Już zapisany");

        var ex = assertThrows(IllegalStateException.class,
                () -> service.enroll(userId, new EnrollRequest(eventId, null)));
        assertEquals("Już zapisany", ex.getMessage());
    }

    @Test
    void shouldThrowWhenEventFull() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(activeEvent));
        when(enrollmentRepository.existsByEventIdAndUserId(eventId, userId)).thenReturn(false);
        when(enrollmentRepository.countByEventId(eventId)).thenReturn(6L);
        when(msg.get("enrollment.event.full")).thenReturn("Brak miejsc");

        var ex = assertThrows(IllegalStateException.class,
                () -> service.enroll(userId, new EnrollRequest(eventId, null)));
        assertEquals("Brak miejsc", ex.getMessage());
    }

    // --- getMyEnrollments ---

    @Test
    void shouldSplitCurrentAndPastEnrollments() throws Exception {
        Event future = activeEvent;
        Event past = new Event(EventCategory.COURSE, "Szkolenie", LocalDate.now().minusDays(10));
        setId(past, UUID.randomUUID());
        past.setActive(true);

        when(enrollmentRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(
                Enrollment.forUser(future, user, null, false),
                Enrollment.forUser(past, user, null, false)
        ));

        MyEnrollmentsResponse result = service.getMyEnrollments(userId);

        assertEquals(1, result.current().size());
        assertEquals(1, result.past().size());
        assertTrue(result.current().getFirst().canCancel());
        assertFalse(result.past().getFirst().canCancel());
    }

    // --- cancelMyEnrollment ---

    @Test
    void shouldCancelOwnEnrollmentAndNotifyAdmin() {
        Enrollment enrollment = Enrollment.forUser(activeEvent, user, null, false);
        UUID enrollmentId = UUID.randomUUID();
        when(enrollmentRepository.findByIdAndUserId(enrollmentId, userId)).thenReturn(Optional.of(enrollment));

        service.cancelMyEnrollment(userId, enrollmentId);

        verify(enrollmentRepository).delete(enrollment);
        verify(enrollmentMailService).sendEnrollmentDeletionAdminNotification(
                eq("Obóz letni"), eq("Anna Nowak"), eq("anna@example.com"), any(), any(), any());
    }

    @Test
    void shouldUseCurrentAccountDataInCancellationNotification() {
        // The enrollment holds a snapshot from registration time (Anna Nowak). After a surname change
        // in the profile, the organizer notification must show the CURRENT account data, consistent with the roster.
        Enrollment enrollment = Enrollment.forUser(activeEvent, user, null, false);
        user.setLastName("Kowalska");
        UUID enrollmentId = UUID.randomUUID();
        when(enrollmentRepository.findByIdAndUserId(enrollmentId, userId)).thenReturn(Optional.of(enrollment));

        service.cancelMyEnrollment(userId, enrollmentId);

        verify(enrollmentMailService).sendEnrollmentDeletionAdminNotification(
                eq("Obóz letni"), eq("Anna Kowalska"), eq("anna@example.com"), any(), any(), any());
    }

    @Test
    void shouldThrowWhenCancellingEnrollmentNotOwned() {
        UUID enrollmentId = UUID.randomUUID();
        when(enrollmentRepository.findByIdAndUserId(enrollmentId, userId)).thenReturn(Optional.empty());
        when(msg.get("enrollment.not.found")).thenReturn("Nie znaleziono");

        assertThrows(NotFoundException.class, () -> service.cancelMyEnrollment(userId, enrollmentId));
        verify(enrollmentRepository, never()).delete(any());
    }

    @Test
    void shouldThrowWhenCancellingTooLate() throws Exception {
        Event soon = new Event(EventCategory.CAMP, "Obóz", LocalDate.now());
        setId(soon, UUID.randomUUID());
        soon.setStartTime(LocalTime.now().plusHours(1));
        soon.setActive(true);
        Enrollment enrollment = Enrollment.forUser(soon, user, null, false);
        UUID enrollmentId = UUID.randomUUID();
        when(enrollmentRepository.findByIdAndUserId(enrollmentId, userId)).thenReturn(Optional.of(enrollment));
        when(msg.get("enrollment.cancel.too.late")).thenReturn("Za późno");

        assertThrows(IllegalStateException.class, () -> service.cancelMyEnrollment(userId, enrollmentId));
        verify(enrollmentRepository, never()).delete(any());
    }

    private static void setId(Object entity, UUID id) throws Exception {
        Field idField = entity.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(entity, id);
    }
}
