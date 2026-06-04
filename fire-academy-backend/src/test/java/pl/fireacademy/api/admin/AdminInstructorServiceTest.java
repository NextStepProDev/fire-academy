package pl.fireacademy.api.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;
import pl.fireacademy.api.NotFoundException;
import pl.fireacademy.api.admin.InstructorDtos.*;
import pl.fireacademy.domain.event.EventCategory;
import pl.fireacademy.domain.instructor.Instructor;
import pl.fireacademy.domain.instructor.InstructorRepository;
import pl.fireacademy.infrastructure.i18n.MessageService;
import pl.fireacademy.infrastructure.storage.FileStorageService;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminInstructorServiceTest {

    @Mock private InstructorRepository instructorRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private MessageService msg;

    @InjectMocks private AdminInstructorService service;

    private Instructor instructor;
    private UUID instructorId;

    @BeforeEach
    void setUp() throws Exception {
        instructorId = UUID.randomUUID();
        instructor = new Instructor("Jan", "Kowalski");
        setId(instructor, instructorId);
        instructor.setBio("Trener personalny");
        instructor.setCategories(Set.of(EventCategory.TRAINING));
        instructor.setDisplayOrder(0);
        instructor.setActive(true);
    }

    @Test
    void shouldReturnAllInstructors() {
        when(instructorRepository.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of(instructor));

        List<InstructorResponse> result = service.getAll();

        assertEquals(1, result.size());
        assertEquals("Jan", result.getFirst().firstName());
    }

    @Test
    void shouldCreateInstructorWithNextDisplayOrder() {
        Instructor existing = new Instructor("Existing", "One");
        existing.setDisplayOrder(2);
        when(instructorRepository.findTopByOrderByDisplayOrderDesc()).thenReturn(Optional.of(existing));
        when(instructorRepository.save(any(Instructor.class))).thenAnswer(inv -> {
            Instructor i = inv.getArgument(0);
            setId(i, UUID.randomUUID());
            return i;
        });

        CreateInstructorRequest request = new CreateInstructorRequest("Anna", "Nowak", "Bio", Set.of(EventCategory.CAMP));
        InstructorResponse result = service.create(request);

        assertEquals("Anna", result.firstName());
        ArgumentCaptor<Instructor> captor = ArgumentCaptor.forClass(Instructor.class);
        verify(instructorRepository).save(captor.capture());
        assertEquals(3, captor.getValue().getDisplayOrder());
    }

    @Test
    void shouldCreateFirstInstructorWithOrderZero() {
        when(instructorRepository.findTopByOrderByDisplayOrderDesc()).thenReturn(Optional.empty());
        when(instructorRepository.save(any(Instructor.class))).thenAnswer(inv -> {
            Instructor i = inv.getArgument(0);
            setId(i, UUID.randomUUID());
            return i;
        });

        CreateInstructorRequest request = new CreateInstructorRequest("Anna", "Nowak", null, Set.of(EventCategory.TRAINING));
        service.create(request);

        ArgumentCaptor<Instructor> captor = ArgumentCaptor.forClass(Instructor.class);
        verify(instructorRepository).save(captor.capture());
        assertEquals(0, captor.getValue().getDisplayOrder());
    }

    @Test
    void shouldUpdateInstructor() {
        when(instructorRepository.findById(instructorId)).thenReturn(Optional.of(instructor));
        when(instructorRepository.save(instructor)).thenReturn(instructor);

        UpdateInstructorRequest request = new UpdateInstructorRequest("Updated", "Name", "New bio", Set.of(EventCategory.CAMP, EventCategory.COURSE));
        service.update(instructorId, request);

        assertEquals("Updated", instructor.getFirstName());
        assertEquals("Name", instructor.getLastName());
        assertEquals("New bio", instructor.getBio());
        assertEquals(Set.of(EventCategory.CAMP, EventCategory.COURSE), instructor.getCategories());
    }

    @Test
    void shouldThrowWhenUpdatingNonExistentInstructor() {
        when(instructorRepository.findById(instructorId)).thenReturn(Optional.empty());
        when(msg.get("instructor.not.found")).thenReturn("Nie znaleziono");

        assertThrows(NotFoundException.class,
            () -> service.update(instructorId, new UpdateInstructorRequest("A", "B", null, Set.of(EventCategory.TRAINING))));
    }

    @Test
    void shouldDeleteInstructorAndPhoto() {
        instructor.setPhotoFilename("old-photo.jpg");
        when(instructorRepository.findById(instructorId)).thenReturn(Optional.of(instructor));

        service.delete(instructorId);

        verify(fileStorageService).delete("instructors", "old-photo.jpg");
        verify(instructorRepository).delete(instructor);
    }

    @Test
    void shouldDeleteInstructorWithoutPhoto() {
        when(instructorRepository.findById(instructorId)).thenReturn(Optional.of(instructor));

        service.delete(instructorId);

        verify(fileStorageService, never()).delete(anyString(), anyString());
        verify(instructorRepository).delete(instructor);
    }

    @Test
    void shouldUploadPhotoAndDeleteOld() {
        instructor.setPhotoFilename("old.jpg");
        when(instructorRepository.findById(instructorId)).thenReturn(Optional.of(instructor));
        MultipartFile file = mock(MultipartFile.class);
        when(fileStorageService.store("instructors", file)).thenReturn("new-uuid.jpg");
        when(instructorRepository.save(instructor)).thenReturn(instructor);

        service.uploadPhoto(instructorId, file);

        verify(fileStorageService).delete("instructors", "old.jpg");
        assertEquals("new-uuid.jpg", instructor.getPhotoFilename());
    }

    @Test
    void shouldUploadFirstPhoto() {
        when(instructorRepository.findById(instructorId)).thenReturn(Optional.of(instructor));
        MultipartFile file = mock(MultipartFile.class);
        when(fileStorageService.store("instructors", file)).thenReturn("first.jpg");
        when(instructorRepository.save(instructor)).thenReturn(instructor);

        service.uploadPhoto(instructorId, file);

        verify(fileStorageService, never()).delete(anyString(), anyString());
        assertEquals("first.jpg", instructor.getPhotoFilename());
    }

    @Test
    void shouldToggleActiveStatus() {
        when(instructorRepository.findById(instructorId)).thenReturn(Optional.of(instructor));
        when(instructorRepository.save(instructor)).thenReturn(instructor);

        service.toggleActive(instructorId);

        assertFalse(instructor.isActive());
    }

    @Test
    void shouldReorderUp() throws Exception {
        Instructor a = createInstructor(UUID.randomUUID(), 0);
        Instructor b = createInstructor(instructorId, 1);
        when(instructorRepository.findAllByOrderByDisplayOrderAsc()).thenReturn(new ArrayList<>(List.of(a, b)));

        service.reorder(instructorId, "up");

        assertEquals(0, b.getDisplayOrder());
        assertEquals(1, a.getDisplayOrder());
    }

    @Test
    void shouldReorderDown() throws Exception {
        Instructor a = createInstructor(instructorId, 0);
        Instructor b = createInstructor(UUID.randomUUID(), 1);
        when(instructorRepository.findAllByOrderByDisplayOrderAsc()).thenReturn(new ArrayList<>(List.of(a, b)));

        service.reorder(instructorId, "down");

        assertEquals(1, a.getDisplayOrder());
        assertEquals(0, b.getDisplayOrder());
    }

    @Test
    void shouldNotReorderWhenAlreadyFirst() throws Exception {
        Instructor a = createInstructor(instructorId, 0);
        Instructor b = createInstructor(UUID.randomUUID(), 1);
        when(instructorRepository.findAllByOrderByDisplayOrderAsc()).thenReturn(new ArrayList<>(List.of(a, b)));

        service.reorder(instructorId, "up");

        assertEquals(0, a.getDisplayOrder());
        verify(instructorRepository, never()).save(any());
    }

    @Test
    void shouldNotReorderWhenAlreadyLast() throws Exception {
        Instructor a = createInstructor(UUID.randomUUID(), 0);
        Instructor b = createInstructor(instructorId, 1);
        when(instructorRepository.findAllByOrderByDisplayOrderAsc()).thenReturn(new ArrayList<>(List.of(a, b)));

        service.reorder(instructorId, "down");

        assertEquals(1, b.getDisplayOrder());
        verify(instructorRepository, never()).save(any());
    }

    private Instructor createInstructor(UUID id, int order) throws Exception {
        Instructor i = new Instructor("Test", "Instructor");
        setId(i, id);
        i.setDisplayOrder(order);
        return i;
    }

    private static void setId(Object entity, UUID id) throws Exception {
        Field idField = entity.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(entity, id);
    }
}
