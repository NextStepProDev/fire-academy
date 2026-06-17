package pl.fireacademy.api.pub;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.fireacademy.api.NotFoundException;
import pl.fireacademy.api.pub.PublicDtos.*;
import pl.fireacademy.domain.enrollment.EnrollmentRepository;
import pl.fireacademy.domain.event.*;
import pl.fireacademy.domain.instructor.Instructor;
import pl.fireacademy.domain.instructor.InstructorRepository;
import pl.fireacademy.infrastructure.i18n.MessageService;

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

    private static void setId(Object entity, UUID id) throws Exception {
        Field idField = entity.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(entity, id);
    }
}
