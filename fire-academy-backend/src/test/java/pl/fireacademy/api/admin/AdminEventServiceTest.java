package pl.fireacademy.api.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.fireacademy.api.admin.EventDtos.*;
import pl.fireacademy.domain.enrollment.Enrollment;
import pl.fireacademy.domain.enrollment.EnrollmentRepository;
import pl.fireacademy.domain.event.*;
import pl.fireacademy.infrastructure.i18n.MessageService;
import pl.fireacademy.infrastructure.mail.EnrollmentMailService;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminEventServiceTest {

    @Mock private EventRepository eventRepository;
    @Mock private EventTypeRepository eventTypeRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private EnrollmentMailService enrollmentMailService;
    @Mock private MessageService msg;

    @InjectMocks private AdminEventService service;

    private Event event;
    private EventType eventType;
    private UUID eventId;
    private UUID eventTypeId;

    @BeforeEach
    void setUp() throws Exception {
        eventId = UUID.randomUUID();
        eventTypeId = UUID.randomUUID();

        eventType = new EventType(EventCategory.TRAINING, "Trening personalny");
        setId(eventType, eventTypeId);

        event = new Event(EventCategory.TRAINING, eventType, LocalDate.now().plusDays(7));
        setId(event, eventId);
        event.setActive(true);
    }

    @Test
    void shouldGetAllEventsByCategory() {
        when(eventRepository.findByCategoryOrderByStartDateAsc(EventCategory.TRAINING)).thenReturn(List.of(event));
        when(enrollmentRepository.countByEventId(eventId)).thenReturn(3L);

        List<EventResponse> result = service.getAll(EventCategory.TRAINING);

        assertEquals(1, result.size());
        assertEquals("Trening personalny", result.getFirst().eventTypeName());
        assertEquals(3L, result.getFirst().enrollmentCount());
    }

    @Test
    void shouldCreateEventWithEventType() {
        CreateEventRequest request = new CreateEventRequest(
            eventTypeId, null, "Opis", EventCategory.TRAINING,
            LocalDate.now().plusDays(7), null, LocalTime.of(10, 0), LocalTime.of(11, 0),
            "Kraków", BigDecimal.valueOf(150), 6
        );

        when(eventTypeRepository.findById(eventTypeId)).thenReturn(Optional.of(eventType));
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> {
            Event e = inv.getArgument(0);
            setId(e, UUID.randomUUID());
            return e;
        });
        when(enrollmentRepository.countByEventId(any())).thenReturn(0L);

        EventResponse result = service.create(request);

        assertNotNull(result);
        verify(eventRepository).save(any(Event.class));
    }

    @Test
    void shouldCreateEventWithCustomName() {
        CreateEventRequest request = new CreateEventRequest(
            null, "Specjalny trening", "Opis", EventCategory.CAMP,
            LocalDate.now().plusDays(14), null, null, null,
            null, null, null
        );

        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> {
            Event e = inv.getArgument(0);
            setId(e, UUID.randomUUID());
            return e;
        });
        when(enrollmentRepository.countByEventId(any())).thenReturn(0L);

        EventResponse result = service.create(request);

        assertNotNull(result);
    }

    @Test
    void shouldThrowWhenCreatingEventInThePast() {
        CreateEventRequest request = new CreateEventRequest(
            eventTypeId, null, null, EventCategory.TRAINING,
            LocalDate.now().minusDays(1), null, null, null,
            null, null, null
        );
        when(msg.get("event.date.past")).thenReturn("Data w przeszłości");

        var ex = assertThrows(IllegalArgumentException.class, () -> service.create(request));
        assertEquals("Data w przeszłości", ex.getMessage());
    }

    @Test
    void shouldThrowWhenNoEventTypeIdAndNoCustomName() {
        CreateEventRequest request = new CreateEventRequest(
            null, null, null, EventCategory.TRAINING,
            LocalDate.now().plusDays(7), null, null, null,
            null, null, null
        );
        when(msg.get("event.name.required")).thenReturn("Nazwa wymagana");

        var ex = assertThrows(IllegalArgumentException.class, () -> service.create(request));
        assertEquals("Nazwa wymagana", ex.getMessage());
    }

    @Test
    void shouldThrowWhenCustomNameIsBlank() {
        CreateEventRequest request = new CreateEventRequest(
            null, "   ", null, EventCategory.TRAINING,
            LocalDate.now().plusDays(7), null, null, null,
            null, null, null
        );
        when(msg.get("event.name.required")).thenReturn("Nazwa wymagana");

        assertThrows(IllegalArgumentException.class, () -> service.create(request));
    }

    @Test
    void shouldUpdateEventAndSendNotifications() {
        event.setLocation("Kraków");
        event.setPrice(BigDecimal.valueOf(100));
        Enrollment enrollment = mock(Enrollment.class);
        // Powiadomienie używa aktualnych danych konta (display*), nie migawki z chwili zapisu.
        when(enrollment.displayEmail()).thenReturn("user@test.com");
        when(enrollment.displayFirstName()).thenReturn("Jan");

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(eventRepository.save(event)).thenReturn(event);
        when(enrollmentRepository.countByEventId(eventId)).thenReturn(1L);
        when(enrollmentRepository.findByEventIdOrderByCreatedAtDesc(eventId)).thenReturn(List.of(enrollment));

        UpdateEventRequest request = new UpdateEventRequest(
            eventTypeId, null, "Nowy opis",
            LocalDate.now().plusDays(14), null, null, null,
            "Warszawa", BigDecimal.valueOf(200), null
        );

        when(eventTypeRepository.findById(eventTypeId)).thenReturn(Optional.of(eventType));
        when(msg.get("email.change.date")).thenReturn("Data");
        when(msg.get("email.change.endDate")).thenReturn("Data końcowa");
        when(msg.get("email.change.startTime")).thenReturn("Godzina rozpoczęcia");
        when(msg.get("email.change.endTime")).thenReturn("Godzina zakończenia");
        when(msg.get("email.change.location")).thenReturn("Lokalizacja");
        when(msg.get("email.change.price")).thenReturn("Cena");
        when(msg.get("email.change.maxParticipants")).thenReturn("Max uczestników");
        when(msg.get("email.change.name")).thenReturn("Nazwa");

        service.update(eventId, request);

        verify(enrollmentMailService).sendEventModificationNotification(
            eq("user@test.com"), eq("Jan"), anyString(), anyString(), anyList(), any(), any());
        verify(enrollmentMailService).sendEventModificationAdminNotification(
            anyString(), anyString(), anyList(), any(), any());
    }

    @Test
    void shouldNotSendNotificationsWhenNoChanges() {
        LocalDate futureDate = LocalDate.now().plusDays(7);
        Event unchangedEvent = new Event(EventCategory.TRAINING, eventType, futureDate);
        setIdUnchecked(unchangedEvent, eventId);

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(unchangedEvent));
        when(eventTypeRepository.findById(eventTypeId)).thenReturn(Optional.of(eventType));
        when(eventRepository.save(unchangedEvent)).thenReturn(unchangedEvent);
        when(enrollmentRepository.countByEventId(eventId)).thenReturn(0L);
        when(msg.get("email.change.name")).thenReturn("Nazwa");
        when(msg.get("email.change.date")).thenReturn("Data");
        when(msg.get("email.change.endDate")).thenReturn("Data końcowa");
        when(msg.get("email.change.startTime")).thenReturn("Godzina rozpoczęcia");
        when(msg.get("email.change.endTime")).thenReturn("Godzina zakończenia");
        when(msg.get("email.change.location")).thenReturn("Lokalizacja");
        when(msg.get("email.change.price")).thenReturn("Cena");
        when(msg.get("email.change.maxParticipants")).thenReturn("Max uczestników");

        UpdateEventRequest request = new UpdateEventRequest(
            eventTypeId, null, null,
            futureDate, null, null, null,
            null, null, null
        );

        service.update(eventId, request);

        verify(enrollmentMailService, never()).sendEventModificationNotification(any(), any(), any(), any(), anyList(), any(), any());
    }

    @Test
    void shouldToggleEventActive() {
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(eventRepository.save(event)).thenReturn(event);
        when(enrollmentRepository.countByEventId(eventId)).thenReturn(0L);

        service.toggleActive(eventId);

        assertFalse(event.isActive());
    }

    @Test
    void shouldDeleteEventWithoutEnrollments() {
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(enrollmentRepository.findByEventIdOrderByCreatedAtDesc(eventId)).thenReturn(List.of());

        service.delete(eventId, false);

        verify(eventRepository).delete(event);
        verify(enrollmentMailService, never()).sendEnrollmentDeletionNotification(any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldThrowWhenDeletingEventWithEnrollments() {
        Enrollment enrollment = mock(Enrollment.class);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(enrollmentRepository.findByEventIdOrderByCreatedAtDesc(eventId)).thenReturn(List.of(enrollment));
        when(msg.get("event.has.enrollments")).thenReturn("Ma zapisy");

        var ex = assertThrows(IllegalStateException.class, () -> service.delete(eventId, false));
        assertEquals("Ma zapisy", ex.getMessage());
        verify(eventRepository, never()).delete(any());
    }

    @Test
    void shouldForceDeleteFutureEventAndNotifyParticipants() {
        Enrollment enrollment = mock(Enrollment.class);
        when(enrollment.displayEmail()).thenReturn("user@test.com");
        when(enrollment.displayFirstName()).thenReturn("Jan");
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(enrollmentRepository.findByEventIdOrderByCreatedAtDesc(eventId)).thenReturn(List.of(enrollment));

        service.delete(eventId, true);

        verify(enrollmentMailService).sendEnrollmentDeletionNotification(
                eq("user@test.com"), eq("Jan"), any(), any(),
                eq(EventCategory.TRAINING), eq(eventId.toString()));
        verify(enrollmentRepository).deleteByEventId(eventId);
        verify(eventRepository).delete(event);
    }

    @Test
    void shouldForceDeletePastEventWithoutNotifying() {
        event.setStartDate(LocalDate.now().minusDays(10));
        Enrollment enrollment = mock(Enrollment.class);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(enrollmentRepository.findByEventIdOrderByCreatedAtDesc(eventId)).thenReturn(List.of(enrollment));

        service.delete(eventId, true);

        verify(enrollmentMailService, never()).sendEnrollmentDeletionNotification(any(), any(), any(), any(), any(), any());
        verify(enrollmentRepository).deleteByEventId(eventId);
        verify(eventRepository).delete(event);
    }

    private static void setId(Object entity, UUID id) throws Exception {
        Field idField = entity.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(entity, id);
    }

    private static void setIdUnchecked(Object entity, UUID id) {
        try {
            setId(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
