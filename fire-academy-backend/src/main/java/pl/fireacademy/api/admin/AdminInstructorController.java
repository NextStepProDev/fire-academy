package pl.fireacademy.api.admin;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pl.fireacademy.api.admin.InstructorDtos.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/instructors")
public class AdminInstructorController {

    private final AdminInstructorService service;

    public AdminInstructorController(AdminInstructorService service) {
        this.service = service;
    }

    @GetMapping
    public List<InstructorResponse> getAll() {
        return service.getAll();
    }

    @PostMapping
    public ResponseEntity<InstructorResponse> create(@Valid @RequestBody CreateInstructorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public InstructorResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateInstructorRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/photo")
    public InstructorResponse uploadPhoto(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return service.uploadPhoto(id, file);
    }

    @PatchMapping("/{id}/toggle-active")
    public InstructorResponse toggleActive(@PathVariable UUID id) {
        return service.toggleActive(id);
    }

    @PostMapping("/{id}/reorder")
    public ResponseEntity<Void> reorder(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        service.reorder(id, body.get("direction"));
        return ResponseEntity.ok().build();
    }
}
