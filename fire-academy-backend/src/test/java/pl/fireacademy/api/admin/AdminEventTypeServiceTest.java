package pl.fireacademy.api.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;
import pl.fireacademy.api.admin.EventTypeDtos.*;
import pl.fireacademy.domain.event.*;
import pl.fireacademy.infrastructure.i18n.MessageService;
import pl.fireacademy.infrastructure.storage.FileStorageService;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminEventTypeServiceTest {

    @Mock private EventTypeRepository eventTypeRepository;
    @Mock private EventTypePhotoRepository photoRepository;
    @Mock private EventRepository eventRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private MessageService msg;

    @InjectMocks private AdminEventTypeService service;

    private EventType eventType;
    private UUID eventTypeId;

    @BeforeEach
    void setUp() throws Exception {
        eventTypeId = UUID.randomUUID();
        eventType = new EventType(EventCategory.TRAINING, "Trening personalny");
        setId(eventType, eventTypeId);
        eventType.setDescription("Opis treningu");
        eventType.setDisplayOrder(0);
    }

    @Test
    void shouldGetAllByCategory() {
        when(eventTypeRepository.findByCategoryOrderByDisplayOrderAsc(EventCategory.TRAINING))
            .thenReturn(List.of(eventType));

        List<EventTypeResponse> result = service.getAll(EventCategory.TRAINING);

        assertEquals(1, result.size());
        assertEquals("Trening personalny", result.getFirst().name());
    }

    @Test
    void shouldCreateEventType() {
        when(eventTypeRepository.findTopByCategoryOrderByDisplayOrderDesc(EventCategory.CAMP))
            .thenReturn(Optional.empty());
        when(eventTypeRepository.save(any(EventType.class))).thenAnswer(inv -> {
            EventType et = inv.getArgument(0);
            setId(et, UUID.randomUUID());
            return et;
        });

        CreateEventTypeRequest request = new CreateEventTypeRequest(EventCategory.CAMP, "Obóz letni", "Opis");
        EventTypeResponse result = service.create(request);

        assertEquals("Obóz letni", result.name());
        assertEquals("CAMP", result.category());
    }

    @Test
    void shouldSetNextDisplayOrderOnCreate() {
        EventType existing = new EventType(EventCategory.TRAINING, "Existing");
        existing.setDisplayOrder(5);
        when(eventTypeRepository.findTopByCategoryOrderByDisplayOrderDesc(EventCategory.TRAINING))
            .thenReturn(Optional.of(existing));
        when(eventTypeRepository.save(any(EventType.class))).thenAnswer(inv -> {
            EventType et = inv.getArgument(0);
            setId(et, UUID.randomUUID());
            return et;
        });

        service.create(new CreateEventTypeRequest(EventCategory.TRAINING, "New", null));

        verify(eventTypeRepository).save(argThat(et -> et.getDisplayOrder() == 6));
    }

    @Test
    void shouldUpdateEventType() {
        when(eventTypeRepository.findById(eventTypeId)).thenReturn(Optional.of(eventType));
        when(eventTypeRepository.save(eventType)).thenReturn(eventType);

        service.update(eventTypeId, new UpdateEventTypeRequest("Nowa nazwa", "Nowy opis"));

        assertEquals("Nowa nazwa", eventType.getName());
        assertEquals("Nowy opis", eventType.getDescription());
    }

    @Test
    void shouldDeleteEventTypeAndConvertLinkedEvents() throws Exception {
        Event linkedEvent = new Event(EventCategory.TRAINING, eventType, java.time.LocalDate.now().plusDays(7));
        setId(linkedEvent, UUID.randomUUID());
        eventType.setThumbnailFilename("thumb.jpg");

        EventTypePhoto photo = new EventTypePhoto(eventType, "photo.jpg", 0);
        setId(photo, UUID.randomUUID());
        eventType.getPhotos().add(photo);

        when(eventTypeRepository.findById(eventTypeId)).thenReturn(Optional.of(eventType));
        when(eventRepository.findByEventTypeIdOrderByStartDateDesc(eventTypeId)).thenReturn(List.of(linkedEvent));

        service.delete(eventTypeId);

        assertEquals("Trening personalny", linkedEvent.getCustomName());
        assertNull(linkedEvent.getEventType());
        verify(fileStorageService).delete("eventtypes", "thumb.jpg");
        verify(fileStorageService).delete("eventtypephotos", "photo.jpg");
        verify(eventTypeRepository).delete(eventType);
    }

    @Test
    void shouldUploadThumbnailAndDeleteOld() {
        eventType.setThumbnailFilename("old-thumb.jpg");
        when(eventTypeRepository.findById(eventTypeId)).thenReturn(Optional.of(eventType));
        MultipartFile file = mock(MultipartFile.class);
        when(fileStorageService.store("eventtypes", file)).thenReturn("new-thumb.jpg");
        when(eventTypeRepository.save(eventType)).thenReturn(eventType);

        service.uploadThumbnail(eventTypeId, file);

        verify(fileStorageService).delete("eventtypes", "old-thumb.jpg");
        assertEquals("new-thumb.jpg", eventType.getThumbnailFilename());
    }

    @Test
    void shouldAddPhoto() throws Exception {
        when(eventTypeRepository.findById(eventTypeId)).thenReturn(Optional.of(eventType));
        MultipartFile file = mock(MultipartFile.class);
        when(fileStorageService.store("eventtypephotos", file)).thenReturn("photo-uuid.jpg");
        when(eventTypeRepository.save(eventType)).thenReturn(eventType);

        service.addPhoto(eventTypeId, file);

        assertEquals(1, eventType.getPhotos().size());
        assertEquals("photo-uuid.jpg", eventType.getPhotos().getFirst().getFilename());
        assertEquals(0, eventType.getPhotos().getFirst().getDisplayOrder());
    }

    @Test
    void shouldDeletePhoto() throws Exception {
        EventTypePhoto photo = new EventTypePhoto(eventType, "photo.jpg", 0);
        UUID photoId = UUID.randomUUID();
        setId(photo, photoId);
        eventType.getPhotos().add(photo);

        when(eventTypeRepository.findById(eventTypeId)).thenReturn(Optional.of(eventType));

        service.deletePhoto(eventTypeId, photoId);

        verify(fileStorageService).delete("eventtypephotos", "photo.jpg");
        assertTrue(eventType.getPhotos().isEmpty());
    }

    @Test
    void shouldThrowWhenPhotoNotFound() {
        when(eventTypeRepository.findById(eventTypeId)).thenReturn(Optional.of(eventType));
        when(msg.get("eventtype.photo.not.found")).thenReturn("Zdjęcie nie znalezione");

        UUID randomPhotoId = UUID.randomUUID();
        var ex = assertThrows(IllegalArgumentException.class,
            () -> service.deletePhoto(eventTypeId, randomPhotoId));
        assertEquals("Zdjęcie nie znalezione", ex.getMessage());
    }

    @Test
    void shouldToggleActive() {
        when(eventTypeRepository.findById(eventTypeId)).thenReturn(Optional.of(eventType));
        when(eventTypeRepository.save(eventType)).thenReturn(eventType);

        service.toggleActive(eventTypeId);

        assertFalse(eventType.isActive());
    }

    @Test
    void shouldReorderEventTypes() throws Exception {
        EventType a = createEventType(UUID.randomUUID(), EventCategory.TRAINING, "A", 0);
        EventType b = createEventType(eventTypeId, EventCategory.TRAINING, "B", 1);

        when(eventTypeRepository.findById(eventTypeId)).thenReturn(Optional.of(b));
        when(eventTypeRepository.findByCategoryOrderByDisplayOrderAsc(EventCategory.TRAINING))
            .thenReturn(new ArrayList<>(List.of(a, b)));

        service.reorder(eventTypeId, "up");

        assertEquals(0, b.getDisplayOrder());
        assertEquals(1, a.getDisplayOrder());
    }

    @Test
    void shouldReorderPhotos() throws Exception {
        EventTypePhoto photoA = new EventTypePhoto(eventType, "a.jpg", 0);
        UUID photoAId = UUID.randomUUID();
        setId(photoA, photoAId);
        EventTypePhoto photoB = new EventTypePhoto(eventType, "b.jpg", 1);
        UUID photoBId = UUID.randomUUID();
        setId(photoB, photoBId);
        eventType.getPhotos().addAll(List.of(photoA, photoB));

        when(eventTypeRepository.findById(eventTypeId)).thenReturn(Optional.of(eventType));

        service.reorderPhoto(eventTypeId, photoBId, "up");

        assertEquals(0, photoB.getDisplayOrder());
        assertEquals(1, photoA.getDisplayOrder());
    }

    private EventType createEventType(UUID id, EventCategory category, String name, int order) throws Exception {
        EventType et = new EventType(category, name);
        setId(et, id);
        et.setDisplayOrder(order);
        return et;
    }

    private static void setId(Object entity, UUID id) throws Exception {
        Field idField = entity.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(entity, id);
    }
}
