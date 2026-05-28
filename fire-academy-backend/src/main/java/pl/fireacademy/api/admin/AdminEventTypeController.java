package pl.fireacademy.api.admin;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pl.fireacademy.api.admin.EventTypeDtos.*;
import pl.fireacademy.domain.event.EventCategory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/event-types")
public class AdminEventTypeController {

    private final AdminEventTypeService service;

    public AdminEventTypeController(AdminEventTypeService service) {
        this.service = service;
    }

    @GetMapping
    public List<EventTypeResponse> getAll(@RequestParam EventCategory category) {
        return service.getAll(category);
    }

    @PostMapping
    public ResponseEntity<EventTypeResponse> create(@Valid @RequestBody CreateEventTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public EventTypeResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateEventTypeRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/thumbnail")
    public EventTypeResponse uploadThumbnail(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return service.uploadThumbnail(id, file);
    }

    @PostMapping("/{id}/photos")
    public EventTypeResponse addPhoto(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return service.addPhoto(id, file);
    }

    @DeleteMapping("/{id}/photos/{photoId}")
    public ResponseEntity<Void> deletePhoto(@PathVariable UUID id, @PathVariable UUID photoId) {
        service.deletePhoto(id, photoId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/photos/{photoId}/reorder")
    public ResponseEntity<Void> reorderPhoto(@PathVariable UUID id, @PathVariable UUID photoId,
                                             @RequestBody Map<String, String> body) {
        service.reorderPhoto(id, photoId, body.get("direction"));
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/toggle-active")
    public EventTypeResponse toggleActive(@PathVariable UUID id) {
        return service.toggleActive(id);
    }

    @PostMapping("/{id}/reorder")
    public ResponseEntity<Void> reorder(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        service.reorder(id, body.get("direction"));
        return ResponseEntity.ok().build();
    }
}
