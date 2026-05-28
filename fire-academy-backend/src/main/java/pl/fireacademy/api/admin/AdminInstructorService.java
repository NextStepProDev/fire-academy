package pl.fireacademy.api.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pl.fireacademy.api.admin.InstructorDtos.*;
import pl.fireacademy.domain.instructor.Instructor;
import pl.fireacademy.domain.instructor.InstructorRepository;
import pl.fireacademy.infrastructure.i18n.MessageService;
import pl.fireacademy.infrastructure.storage.FileStorageService;

import java.util.List;
import java.util.UUID;

@Service
public class AdminInstructorService {

    private static final String FOLDER = "instructors";

    private final InstructorRepository instructorRepository;
    private final FileStorageService fileStorageService;
    private final MessageService msg;

    public AdminInstructorService(InstructorRepository instructorRepository,
                                  FileStorageService fileStorageService,
                                  MessageService msg) {
        this.instructorRepository = instructorRepository;
        this.fileStorageService = fileStorageService;
        this.msg = msg;
    }

    public List<InstructorResponse> getAll() {
        return instructorRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public InstructorResponse create(CreateInstructorRequest request) {
        int maxOrder = instructorRepository.findTopByOrderByDisplayOrderDesc()
                .map(Instructor::getDisplayOrder)
                .orElse(-1);

        var instructor = new Instructor(request.firstName(), request.lastName());
        instructor.setBio(request.bio());
        instructor.setCategories(request.categories());
        instructor.setDisplayOrder(maxOrder + 1);
        return toResponse(instructorRepository.save(instructor));
    }

    @Transactional
    public InstructorResponse update(UUID id, UpdateInstructorRequest request) {
        var instructor = findOrThrow(id);
        instructor.setFirstName(request.firstName());
        instructor.setLastName(request.lastName());
        instructor.setBio(request.bio());
        instructor.setCategories(request.categories());
        return toResponse(instructorRepository.save(instructor));
    }

    @Transactional
    public void delete(UUID id) {
        var instructor = findOrThrow(id);
        if (instructor.getPhotoFilename() != null) {
            fileStorageService.delete(FOLDER, instructor.getPhotoFilename());
        }
        instructorRepository.delete(instructor);
    }

    @Transactional
    public InstructorResponse uploadPhoto(UUID id, MultipartFile file) {
        var instructor = findOrThrow(id);
        if (instructor.getPhotoFilename() != null) {
            fileStorageService.delete(FOLDER, instructor.getPhotoFilename());
        }
        String filename = fileStorageService.store(FOLDER, file);
        instructor.setPhotoFilename(filename);
        return toResponse(instructorRepository.save(instructor));
    }

    @Transactional
    public InstructorResponse toggleActive(UUID id) {
        var instructor = findOrThrow(id);
        instructor.setActive(!instructor.isActive());
        return toResponse(instructorRepository.save(instructor));
    }

    @Transactional
    public void reorder(UUID id, String direction) {
        var all = instructorRepository.findAllByOrderByDisplayOrderAsc();
        int idx = -1;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getId().equals(id)) { idx = i; break; }
        }
        if (idx < 0) throw new IllegalArgumentException(msg.get("instructor.not.found"));

        int swapIdx = "up".equals(direction) ? idx - 1 : idx + 1;
        if (swapIdx < 0 || swapIdx >= all.size()) return;

        var a = all.get(idx);
        var b = all.get(swapIdx);
        int tmp = a.getDisplayOrder();
        a.setDisplayOrder(b.getDisplayOrder());
        b.setDisplayOrder(tmp);
        instructorRepository.save(a);
        instructorRepository.save(b);
    }

    private Instructor findOrThrow(UUID id) {
        return instructorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(msg.get("instructor.not.found")));
    }

    private InstructorResponse toResponse(Instructor i) {
        String photoUrl = i.getPhotoFilename() != null
                ? "/api/files/" + FOLDER + "/" + i.getPhotoFilename()
                : null;
        return new InstructorResponse(
                i.getId(), i.getFirstName(), i.getLastName(), i.getBio(),
                photoUrl, i.getCategories(), i.getDisplayOrder(), i.isActive(), i.getCreatedAt()
        );
    }
}
