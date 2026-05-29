package pl.fireacademy.api.admin;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.fireacademy.api.admin.EnrollmentDtos.*;
import pl.fireacademy.domain.event.EventCategory;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/enrollments")
public class AdminEnrollmentController {

    private final AdminEnrollmentService service;

    public AdminEnrollmentController(AdminEnrollmentService service) {
        this.service = service;
    }

    @GetMapping
    public List<EnrollmentResponse> getByEvent(@RequestParam UUID eventId) {
        return service.getByEvent(eventId);
    }

    @GetMapping("/by-category")
    public List<EnrollmentResponse> getByCategory(@RequestParam EventCategory category) {
        return service.getByCategory(category);
    }

    @PostMapping
    public ResponseEntity<EnrollmentResponse> adminEnroll(@Valid @RequestBody AdminEnrollRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.adminEnroll(request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public List<EnrollmentResponse> searchByEmail(@RequestParam String email) {
        return service.searchByEmail(email);
    }

    @PostMapping("/anonymize")
    public AnonymizeResponse anonymizeByEmail(@RequestParam String email) {
        return service.anonymizeByEmail(email);
    }
}
