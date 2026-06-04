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

    @BeforeEach
    void setUp() throws Exception {
        eventId = UUID.randomUUID();
        event = new Event(EventCategory.TRAINING, "Trening personalny", LocalDate.now().plusDays(7));
        setId(event, eventId);
        event.setLocation("Kraków");

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
    void shouldGetEnrollmentsByCategory() {
        when(enrollmentRepository.findByEventCategory(EventCategory.TRAINING)).thenReturn(List.of(enrollment));

        List<EnrollmentResponse> result = service.getByCategory(EventCategory.TRAINING);

        assertEquals(1, result.size());
    }

    @Test
    void shouldAdminEnroll() {
        AdminEnrollRequest request = new AdminEnrollRequest(
            eventId, "Anna", "Nowak", "anna@test.com", "987654321", "Admin note"
        );

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
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
            any(), any(), any(), any(), any());
    }

    @Test
    void shouldThrowWhenAdminEnrollEventNotFound() {
        AdminEnrollRequest request = new AdminEnrollRequest(
            eventId, "Anna", "Nowak", "anna@test.com", "987654321", null
        );
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());
        when(msg.get("event.not.found")).thenReturn("Nie znaleziono");

        assertThrows(NotFoundException.class, () -> service.adminEnroll(request));
    }

    @Test
    void shouldDeleteEnrollmentAndSendNotifications() {
        when(enrollmentRepository.findById(enrollmentId)).thenReturn(Optional.of(enrollment));

        service.delete(enrollmentId);

        verify(enrollmentRepository).delete(enrollment);
        verify(enrollmentMailService).sendEnrollmentDeletionNotification(
            eq("jan@test.com"), eq("Jan"), eq("Trening personalny"), any(), any(), any());
        verify(enrollmentMailService).sendEnrollmentDeletionAdminNotification(
            eq("Trening personalny"), eq("Jan Kowalski"), eq("jan@test.com"), any(), any(), any());
    }

    @Test
    void shouldThrowWhenDeletingNonExistentEnrollment() {
        when(enrollmentRepository.findById(enrollmentId)).thenReturn(Optional.empty());
        when(msg.get("enrollment.not.found")).thenReturn("Nie znaleziono");

        assertThrows(NotFoundException.class, () -> service.delete(enrollmentId));
    }

    @Test
    void shouldSearchByEmail() {
        when(enrollmentRepository.findByEmailIgnoreCase("jan@test.com")).thenReturn(List.of(enrollment));

        List<EnrollmentResponse> result = service.searchByEmail("jan@test.com");

        assertEquals(1, result.size());
        assertEquals("Jan", result.getFirst().firstName());
    }

    @Test
    void shouldTrimEmailOnSearch() {
        when(enrollmentRepository.findByEmailIgnoreCase("jan@test.com")).thenReturn(List.of(enrollment));

        service.searchByEmail("  jan@test.com  ");

        verify(enrollmentRepository).findByEmailIgnoreCase("jan@test.com");
    }

    @Test
    void shouldAnonymizeByEmail() {
        when(enrollmentRepository.findByEmailIgnoreCase("jan@test.com")).thenReturn(List.of(enrollment));

        AnonymizeResponse result = service.anonymizeByEmail("jan@test.com");

        assertEquals(1, result.anonymizedCount());
        assertTrue(enrollment.isAnonymized());
        verify(enrollmentRepository).saveAll(List.of(enrollment));
    }

    @Test
    void shouldReturnZeroWhenNoEnrollmentsToAnonymize() {
        when(enrollmentRepository.findByEmailIgnoreCase("none@test.com")).thenReturn(List.of());

        AnonymizeResponse result = service.anonymizeByEmail("none@test.com");

        assertEquals(0, result.anonymizedCount());
    }

    private static void setId(Object entity, UUID id) throws Exception {
        Field idField = entity.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(entity, id);
    }
}
