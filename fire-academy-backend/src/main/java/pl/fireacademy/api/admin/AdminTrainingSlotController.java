package pl.fireacademy.api.admin;

import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.fireacademy.api.admin.TrainingSlotDtos.*;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/training-slots")
public class AdminTrainingSlotController {

    private final AdminTrainingSlotService service;
    private final AdminTrainingEnrollmentService enrollmentService;

    public AdminTrainingSlotController(AdminTrainingSlotService service,
                                       AdminTrainingEnrollmentService enrollmentService) {
        this.service = service;
        this.enrollmentService = enrollmentService;
    }

    @GetMapping
    public List<TrainingSlotResponse> getAll(@RequestParam(required = false) @Nullable String month) {
        var ym = (month != null && !month.isBlank()) ? YearMonth.parse(month) : YearMonth.now();
        return service.getAll(ym);
    }

    @PostMapping
    public ResponseEntity<TrainingSlotResponse> create(@Valid @RequestBody CreateTrainingSlotRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public TrainingSlotResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateTrainingSlotRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle-active")
    public TrainingSlotResponse toggleActive(@PathVariable UUID id) {
        return service.toggleActive(id);
    }

    // --- Zarządzanie zapisami (roster) ---

    @GetMapping("/{slotId}/enrollments")
    public List<RosterEntry> getRoster(@PathVariable UUID slotId,
                                       @RequestParam(required = false) @Nullable String month) {
        var ym = (month != null && !month.isBlank()) ? YearMonth.parse(month) : YearMonth.now();
        return enrollmentService.getRoster(slotId, ym);
    }

    @PostMapping("/{slotId}/enrollments")
    public ResponseEntity<Void> addEnrollment(@PathVariable UUID slotId,
                                              @Valid @RequestBody AdminAddEnrollmentRequest request) {
        enrollmentService.addEnrollment(slotId, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
