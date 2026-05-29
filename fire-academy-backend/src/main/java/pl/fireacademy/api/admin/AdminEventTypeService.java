package pl.fireacademy.api.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pl.fireacademy.api.admin.EventTypeDtos.*;
import pl.fireacademy.domain.event.*;
import pl.fireacademy.infrastructure.i18n.MessageService;
import pl.fireacademy.infrastructure.storage.FileStorageService;

import java.util.List;
import java.util.UUID;

@Service
public class AdminEventTypeService {

    private static final String THUMBNAIL_FOLDER = "eventtypes";
    private static final String PHOTO_FOLDER = "eventtypephotos";

    private final EventTypeRepository eventTypeRepository;
    private final EventTypePhotoRepository photoRepository;
    private final EventRepository eventRepository;
    private final FileStorageService fileStorageService;
    private final MessageService msg;

    public AdminEventTypeService(EventTypeRepository eventTypeRepository,
                                 EventTypePhotoRepository photoRepository,
                                 EventRepository eventRepository,
                                 FileStorageService fileStorageService,
                                 MessageService msg) {
        this.eventTypeRepository = eventTypeRepository;
        this.photoRepository = photoRepository;
        this.eventRepository = eventRepository;
        this.fileStorageService = fileStorageService;
        this.msg = msg;
    }

    @Transactional(readOnly = true)
    public List<EventTypeResponse> getAll(EventCategory category) {
        return eventTypeRepository.findByCategoryOrderByDisplayOrderAsc(category).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public EventTypeResponse create(CreateEventTypeRequest request) {
        int maxOrder = eventTypeRepository.findTopByCategoryOrderByDisplayOrderDesc(request.category())
                .map(EventType::getDisplayOrder)
                .orElse(-1);

        var et = new EventType(request.category(), request.name());
        et.setDescription(request.description());
        et.setDisplayOrder(maxOrder + 1);
        return toResponse(eventTypeRepository.save(et));
    }

    @Transactional
    public EventTypeResponse update(UUID id, UpdateEventTypeRequest request) {
        var et = findOrThrow(id);
        et.setName(request.name());
        et.setDescription(request.description());
        return toResponse(eventTypeRepository.save(et));
    }

    @Transactional
    public void delete(UUID id) {
        var et = findOrThrow(id);
        for (var event : eventRepository.findByEventTypeIdOrderByStartDateDesc(id)) {
            event.convertToCustomName(et.getName());
            eventRepository.save(event);
        }
        if (et.getThumbnailFilename() != null) {
            fileStorageService.delete(THUMBNAIL_FOLDER, et.getThumbnailFilename());
        }
        for (var photo : et.getPhotos()) {
            fileStorageService.delete(PHOTO_FOLDER, photo.getFilename());
        }
        eventTypeRepository.delete(et);
    }

    @Transactional
    public EventTypeResponse uploadThumbnail(UUID id, MultipartFile file) {
        var et = findOrThrow(id);
        if (et.getThumbnailFilename() != null) {
            fileStorageService.delete(THUMBNAIL_FOLDER, et.getThumbnailFilename());
        }
        String filename = fileStorageService.store(THUMBNAIL_FOLDER, file);
        et.setThumbnailFilename(filename);
        return toResponse(eventTypeRepository.save(et));
    }

    @Transactional
    public EventTypeResponse addPhoto(UUID id, MultipartFile file) {
        var et = findOrThrow(id);
        String filename = fileStorageService.store(PHOTO_FOLDER, file);
        int maxOrder = et.getPhotos().stream()
                .mapToInt(EventTypePhoto::getDisplayOrder)
                .max().orElse(-1);
        var photo = new EventTypePhoto(et, filename, maxOrder + 1);
        et.getPhotos().add(photo);
        return toResponse(eventTypeRepository.save(et));
    }

    @Transactional
    public void deletePhoto(UUID eventTypeId, UUID photoId) {
        var et = findOrThrow(eventTypeId);
        var photo = et.getPhotos().stream()
                .filter(p -> p.getId().equals(photoId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(msg.get("eventtype.photo.not.found")));
        fileStorageService.delete(PHOTO_FOLDER, photo.getFilename());
        et.getPhotos().remove(photo);
        eventTypeRepository.save(et);
    }

    @Transactional
    public EventTypeResponse toggleActive(UUID id) {
        var et = findOrThrow(id);
        et.setActive(!et.isActive());
        return toResponse(eventTypeRepository.save(et));
    }

    @Transactional
    public void reorder(UUID id, String direction) {
        var et = findOrThrow(id);
        var all = eventTypeRepository.findByCategoryOrderByDisplayOrderAsc(et.getCategory());
        int idx = -1;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getId().equals(id)) { idx = i; break; }
        }
        if (idx < 0) return;

        int swapIdx = "up".equals(direction) ? idx - 1 : idx + 1;
        if (swapIdx < 0 || swapIdx >= all.size()) return;

        var a = all.get(idx);
        var b = all.get(swapIdx);
        int tmp = a.getDisplayOrder();
        a.setDisplayOrder(b.getDisplayOrder());
        b.setDisplayOrder(tmp);
        eventTypeRepository.save(a);
        eventTypeRepository.save(b);
    }

    @Transactional
    public void reorderPhoto(UUID eventTypeId, UUID photoId, String direction) {
        var et = findOrThrow(eventTypeId);
        var photos = et.getPhotos();
        int idx = -1;
        for (int i = 0; i < photos.size(); i++) {
            if (photos.get(i).getId().equals(photoId)) { idx = i; break; }
        }
        if (idx < 0) return;

        int swapIdx = "up".equals(direction) ? idx - 1 : idx + 1;
        if (swapIdx < 0 || swapIdx >= photos.size()) return;

        var a = photos.get(idx);
        var b = photos.get(swapIdx);
        int tmp = a.getDisplayOrder();
        a.setDisplayOrder(b.getDisplayOrder());
        b.setDisplayOrder(tmp);
        photoRepository.save(a);
        photoRepository.save(b);
    }

    private EventType findOrThrow(UUID id) {
        return eventTypeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(msg.get("eventtype.not.found")));
    }

    private EventTypeResponse toResponse(EventType et) {
        String thumbnailUrl = et.getThumbnailFilename() != null
                ? "/api/files/" + THUMBNAIL_FOLDER + "/" + et.getThumbnailFilename()
                : null;
        var photos = et.getPhotos().stream()
                .map(p -> new PhotoResponse(p.getId(), "/api/files/" + PHOTO_FOLDER + "/" + p.getFilename(), p.getDisplayOrder()))
                .toList();
        return new EventTypeResponse(
                et.getId(), et.getCategory().name(), et.getName(), et.getDescription(),
                thumbnailUrl, photos, et.getDisplayOrder(), et.isActive(), et.getCreatedAt()
        );
    }
}
