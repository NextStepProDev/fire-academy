package pl.fireacademy.api.pub;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.fireacademy.api.NotFoundException;
import pl.fireacademy.api.pub.PublicDtos.*;
import pl.fireacademy.domain.enrollment.Enrollment;
import pl.fireacademy.domain.enrollment.EnrollmentRepository;
import pl.fireacademy.domain.event.*;
import pl.fireacademy.domain.instructor.Instructor;
import pl.fireacademy.domain.instructor.InstructorRepository;
import pl.fireacademy.infrastructure.i18n.MessageService;
import pl.fireacademy.infrastructure.mail.EnrollmentMailService;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublicServiceTest {

    @Mock private InstructorRepository instructorRepository;
    @Mock private EventTypeRepository eventTypeRepository;
    @Mock private EventRepository eventRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private EnrollmentMailService enrollmentMailService;
    @Mock private MessageService msg;

    @InjectMocks private PublicService publicService;

    private Event activeEvent;
    private UUID eventId;

    @BeforeEach
    void setUp() throws Exception {
        eventId = UUID.randomUUID();
        activeEvent = new Event(EventCategory.TRAINING, "Trening personalny", LocalDate.now().plusDays(7));
        setId(activeEvent, eventId);
        activeEvent.setStartTime(LocalTime.of(10, 0));
        activeEvent.setMaxParticipants(6);
        activeEvent.setActive(true);
    }

    // --- Get Active Instructors ---

    @Test
    void shouldReturnActiveInstructors() throws Exception {
        Instructor instructor = new Instructor("Anna", "Nowak");
        setId(instructor, UUID.randomUUID());
        instructor.setPhotoFilename("photo.jpg");

        when(instructorRepository.findActiveByCategoryOrdered(EventCategory.TRAINING))
            .thenReturn(List.of(instructor));

        List<InstructorCard> result = publicService.getActiveInstructors(EventCategory.TRAINING);

        assertEquals(1, result.size());
        assertEquals("Anna", result.getFirst().firstName());
        assertTrue(result.getFirst().photoUrl().contains("/api/files/instructors/photo.jpg"));
    }

    @Test
    void shouldReturnNullPhotoUrlWhenNoPhoto() throws Exception {
        Instructor instructor = new Instructor("Anna", "Nowak");
        setId(instructor, UUID.randomUUID());

        when(instructorRepository.findActiveByCategoryOrdered(EventCategory.TRAINING))
            .thenReturn(List.of(instructor));

        List<InstructorCard> result = publicService.getActiveInstructors(EventCategory.TRAINING);

        assertNull(result.getFirst().photoUrl());
    }

    // --- Get Active Event Types ---

    @Test
    void shouldReturnActiveEventTypes() throws Exception {
        EventType et = new EventType(EventCategory.TRAINING, "Trening personalny");
        setId(et, UUID.randomUUID());
        et.setThumbnailFilename("thumb.jpg");

        when(eventTypeRepository.findByCategoryAndActiveTrueOrderByDisplayOrderAsc(EventCategory.TRAINING))
            .thenReturn(List.of(et));

        List<EventTypeCard> result = publicService.getActiveEventTypes(EventCategory.TRAINING);

        assertEquals(1, result.size());
        assertEquals("Trening personalny", result.getFirst().name());
        assertTrue(result.getFirst().thumbnailUrl().contains("/api/files/eventtypes/thumb.jpg"));
    }

    // --- Get Upcoming Events ---

    @Test
    void shouldReturnUpcomingEventsWithAvailableSpots() {
        when(eventRepository.findByCategoryAndActiveTrueAndStartDateGreaterThanEqualOrderByStartDateAsc(
            eq(EventCategory.TRAINING), any(LocalDate.class)))
            .thenReturn(List.of(activeEvent));
        when(enrollmentRepository.countByEventIds(List.of(eventId)))
            .thenReturn(List.<Object[]>of(new Object[]{eventId, 2L}));

        List<EventCard> result = publicService.getUpcomingEvents(EventCategory.TRAINING);

        assertEquals(1, result.size());
        assertEquals(4, result.getFirst().availableSpots());
    }

    @Test
    void shouldReturnMinusOneAvailableSpotsWhenNoMaxParticipants() throws Exception {
        activeEvent.setMaxParticipants(null);
        when(eventRepository.findByCategoryAndActiveTrueAndStartDateGreaterThanEqualOrderByStartDateAsc(
            eq(EventCategory.TRAINING), any(LocalDate.class)))
            .thenReturn(List.of(activeEvent));
        when(enrollmentRepository.countByEventIds(List.of(eventId)))
            .thenReturn(List.<Object[]>of(new Object[]{eventId, 10L}));

        List<EventCard> result = publicService.getUpcomingEvents(EventCategory.TRAINING);

        assertEquals(-1, result.getFirst().availableSpots());
    }

    @Test
    void shouldReturnZeroAvailableSpotsWhenFull() {
        when(eventRepository.findByCategoryAndActiveTrueAndStartDateGreaterThanEqualOrderByStartDateAsc(
            eq(EventCategory.TRAINING), any(LocalDate.class)))
            .thenReturn(List.of(activeEvent));
        when(enrollmentRepository.countByEventIds(List.of(eventId)))
            .thenReturn(List.<Object[]>of(new Object[]{eventId, 6L}));

        List<EventCard> result = publicService.getUpcomingEvents(EventCategory.TRAINING);

        assertEquals(0, result.getFirst().availableSpots());
    }

    // --- Get Instructor By Id ---

    @Test
    void shouldReturnInstructorById() throws Exception {
        UUID id = UUID.randomUUID();
        Instructor instructor = new Instructor("Jan", "Kowalski");
        setId(instructor, id);
        instructor.setActive(true);

        when(instructorRepository.findById(id)).thenReturn(Optional.of(instructor));

        InstructorCard result = publicService.getInstructorById(id);

        assertEquals("Jan", result.firstName());
    }

    @Test
    void shouldThrowWhenInstructorNotActive() throws Exception {
        UUID id = UUID.randomUUID();
        Instructor instructor = new Instructor("Jan", "Kowalski");
        setId(instructor, id);
        instructor.setActive(false);

        when(instructorRepository.findById(id)).thenReturn(Optional.of(instructor));
        when(msg.get("instructor.not.found")).thenReturn("Nie znaleziono");

        assertThrows(NotFoundException.class, () -> publicService.getInstructorById(id));
    }

    // --- Enroll ---

    @Test
    void shouldEnrollSuccessfully() {
        EnrollRequest request = new EnrollRequest("Anna", "Nowak", "anna@example.com", "123456789", null);

        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(activeEvent));
        when(enrollmentRepository.existsByEventIdAndEmail(eventId, "anna@example.com")).thenReturn(false);
        when(enrollmentRepository.countByEventId(eventId)).thenReturn(3L);

        publicService.enroll(eventId, request);

        verify(enrollmentRepository).save(any(Enrollment.class));
        verify(enrollmentMailService).sendEnrollmentConfirmation(
            eq("anna@example.com"), eq("Anna"), eq("Trening personalny"), any(), any(), any(), any());
        verify(enrollmentMailService).sendEnrollmentNotification(
            eq("Trening personalny"), eq("Anna Nowak"), eq("anna@example.com"),
            any(), any(), any(), any(), any());
    }

    @Test
    void shouldThrowWhenEventNotFound() {
        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.empty());
        when(msg.get("event.not.found")).thenReturn("Nie znaleziono");

        EnrollRequest request = new EnrollRequest("Anna", "Nowak", "anna@example.com", "123456789", null);
        assertThrows(NotFoundException.class, () -> publicService.enroll(eventId, request));
    }

    @Test
    void shouldThrowWhenEventInactive() {
        activeEvent.setActive(false);
        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(activeEvent));
        when(msg.get("enrollment.event.inactive")).thenReturn("Wydarzenie nieaktywne");

        EnrollRequest request = new EnrollRequest("Anna", "Nowak", "anna@example.com", "123456789", null);
        var ex = assertThrows(IllegalStateException.class, () -> publicService.enroll(eventId, request));
        assertEquals("Wydarzenie nieaktywne", ex.getMessage());
    }

    @Test
    void shouldThrowWhenEnrollingLessThan24HoursBeforeEvent() throws Exception {
        Event soonEvent = new Event(EventCategory.TRAINING, "Trening", LocalDate.now());
        setId(soonEvent, eventId);
        soonEvent.setStartTime(LocalTime.now().plusHours(1));
        soonEvent.setActive(true);

        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(soonEvent));
        when(msg.get("enrollment.too.late")).thenReturn("Za późno");

        EnrollRequest request = new EnrollRequest("Anna", "Nowak", "anna@example.com", "123456789", null);
        assertThrows(IllegalStateException.class, () -> publicService.enroll(eventId, request));
    }

    @Test
    void shouldThrowWhenDuplicateEnrollment() {
        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(activeEvent));
        when(enrollmentRepository.existsByEventIdAndEmail(eventId, "anna@example.com")).thenReturn(true);
        when(msg.get("enrollment.duplicate")).thenReturn("Już zapisany");

        EnrollRequest request = new EnrollRequest("Anna", "Nowak", "anna@example.com", "123456789", null);
        var ex = assertThrows(IllegalStateException.class, () -> publicService.enroll(eventId, request));
        assertEquals("Już zapisany", ex.getMessage());
    }

    @Test
    void shouldThrowWhenEventFull() {
        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(activeEvent));
        when(enrollmentRepository.existsByEventIdAndEmail(eventId, "anna@example.com")).thenReturn(false);
        when(enrollmentRepository.countByEventId(eventId)).thenReturn(6L);
        when(msg.get("enrollment.event.full")).thenReturn("Brak miejsc");

        EnrollRequest request = new EnrollRequest("Anna", "Nowak", "anna@example.com", "123456789", null);
        var ex = assertThrows(IllegalStateException.class, () -> publicService.enroll(eventId, request));
        assertEquals("Brak miejsc", ex.getMessage());
    }

    @Test
    void shouldAllowEnrollmentWhenNoMaxParticipants() {
        activeEvent.setMaxParticipants(null);
        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(activeEvent));
        when(enrollmentRepository.existsByEventIdAndEmail(eventId, "anna@example.com")).thenReturn(false);

        EnrollRequest request = new EnrollRequest("Anna", "Nowak", "anna@example.com", "123456789", null);

        assertDoesNotThrow(() -> publicService.enroll(eventId, request));
        verify(enrollmentRepository).save(any(Enrollment.class));
    }

    private static void setId(Object entity, UUID id) throws Exception {
        Field idField = entity.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(entity, id);
    }
}
