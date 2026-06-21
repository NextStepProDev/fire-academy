package pl.fireacademy.api.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.fireacademy.api.NotFoundException;
import pl.fireacademy.api.admin.EnrollmentDtos.*;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminEnrollmentServiceTest {

    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private EventRepository eventRepository;
    @Mock private UserRepository userRepository;
    @Mock private EnrollmentMailService enrollmentMailService;
    @Mock private MessageService msg;

    @InjectMocks private AdminEnrollmentService service;

    private Event event;
    private UUID eventId;
    private Enrollment enrollment;
    private UUID enrollmentId;
    private User user;
    private UUID userId;

    @BeforeEach
    void setUp() throws Exception {
        eventId = UUID.randomUUID();
        event = new Event(EventCategory.TRAINING, "Trening personalny", LocalDate.now().plusDays(7));
        setId(event, eventId);
        event.setLocation("Kraków");

        userId = UUID.randomUUID();
        user = new User("anna@test.com", "Anna", "Nowak", "987654321");
        setId(user, userId);

        enrollmentId = UUID.randomUUID();
        enrollment = new Enrollment(event, "Jan", "Kowalski", "jan@test.com", "123456789", "Notatka", false);
        setId(enrollment, enrollmentId);
    }

    @Test
    void shouldGetEnrollmentsByEvent() {
        when(enrollmentRepository.findByEventIdOrderByCreatedAtDesc(eventId)).thenReturn(List.of(enrollment));

        List<EnrollmentResponse> result = service.getByEvent(eventId);

        assertEquals(1, result.size());
        assertEquals("Jan", result.getFirst().firstName());
        assertEquals("jan@test.com", result.getFirst().email());
    }

    @Test
    void shouldAdminEnroll() {
        AdminEnrollRequest request = new AdminEnrollRequest(eventId, userId, "Admin note");

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(inv -> {
            Enrollment e = inv.getArgument(0);
            setId(e, UUID.randomUUID());
            return e;
        });

        EnrollmentResponse result = service.adminEnroll(request);

        assertNotNull(result);
        verify(enrollmentMailService).sendAdminEnrollmentConfirmation(
            eq("anna@test.com"), eq("Anna"), eq("Trening personalny"), any(), eq("Kraków"), any(), any());
        verify(enrollmentMailService).sendAdminEnrollmentNotification(
            eq("Trening personalny"), eq("Anna Nowak"), eq("anna@test.com"),
            eq("987654321"), any(), any(), any(), any());
    }

    @Test
    void shouldAdminEnrollUserWithoutPhone() throws Exception {
        User noPhone = new User("anna@test.com", "Anna", "Nowak", null);
        setId(noPhone, userId);
        AdminEnrollRequest request = new AdminEnrollRequest(eventId, userId, null);

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(userRepository.findById(userId)).thenReturn(Optional.of(noPhone));
        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(inv -> {
            Enrollment e = inv.getArgument(0);
            setId(e, UUID.randomUUID());
            return e;
        });

        EnrollmentResponse result = service.adminEnroll(request);

        assertNull(result.phone());
        // The organizer mail gets the placeholder „—" instead of null (no NPE in the template).
        verify(enrollmentMailService).sendAdminEnrollmentNotification(
            eq("Trening personalny"), eq("Anna Nowak"), eq("anna@test.com"),
            eq("—"), any(), any(), any(), any());
    }

    @Test
    void shouldThrowWhenAdminEnrollEventNotFound() {
        AdminEnrollRequest request = new AdminEnrollRequest(eventId, userId, null);
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());
        when(msg.get("event.not.found")).thenReturn("Nie znaleziono");

        assertThrows(NotFoundException.class, () -> service.adminEnroll(request));
    }

    @Test
    void shouldDeleteEnrollmentAndSendNotifications() {
        when(enrollmentRepository.findById(enrollmentId)).thenReturn(Optional.of(enrollment));

        service.delete(enrollmentId, true);

        verify(enrollmentRepository).delete(enrollment);
        verify(enrollmentMailService).sendEnrollmentDeletionNotification(
            eq("jan@test.com"), eq("Jan"), eq("Trening personalny"), any(), any(), any());
        verify(enrollmentMailService).sendEnrollmentDeletionAdminNotification(
            eq("Trening personalny"), eq("Jan Kowalski"), eq("jan@test.com"), any(), any(), any());
    }

    @Test
    void shouldDeleteWithoutNotificationWhenNotifyFalse() {
        when(enrollmentRepository.findById(enrollmentId)).thenReturn(Optional.of(enrollment));

        service.delete(enrollmentId, false);

        verify(enrollmentRepository).delete(enrollment);
        // Archive correction — no mails.
        verifyNoInteractions(enrollmentMailService);
    }

    @Test
    void shouldThrowWhenDeletingNonExistentEnrollment() {
        when(enrollmentRepository.findById(enrollmentId)).thenReturn(Optional.empty());
        when(msg.get("enrollment.not.found")).thenReturn("Nie znaleziono");

        assertThrows(NotFoundException.class, () -> service.delete(enrollmentId, true));
    }

    @Test
    void shouldThrowWhenAdminEnrollDuplicate() {
        AdminEnrollRequest request = new AdminEnrollRequest(eventId, userId, null);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(enrollmentRepository.existsByEventIdAndUserId(eventId, userId)).thenReturn(true);
        when(msg.get("enrollment.already.exists")).thenReturn("Już zapisana");

        assertThrows(IllegalStateException.class, () -> service.adminEnroll(request));
        verify(enrollmentRepository, never()).save(any());
    }

    private static void setId(Object entity, UUID id) throws Exception {
        Field idField = entity.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(entity, id);
    }
}
