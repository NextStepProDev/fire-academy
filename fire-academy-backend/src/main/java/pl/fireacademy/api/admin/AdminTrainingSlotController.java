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

    @PostMapping("/batch")
    public ResponseEntity<List<TrainingSlotResponse>> createBatch(@Valid @RequestBody BatchCreateTrainingSlotRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createBatch(request));
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

    /** Zaplanowana dezaktywacja od daty (powiadamia zapisanych mailem). */
    @PostMapping("/{id}/deactivate")
    public TrainingSlotResponse deactivate(@PathVariable UUID id, @Valid @RequestBody DeactivateSlotRequest request) {
        return service.deactivate(id, request.from());
    }

    @PostMapping("/{id}/reactivate")
    public TrainingSlotResponse reactivate(@PathVariable UUID id) {
        return service.reactivate(id);
    }

    /** Archiwum usuniętych slotów wraz z danymi kontaktowymi byłych uczestników. */
    @GetMapping("/deleted")
    public List<DeletedSlotResponse> getDeleted() {
        return service.getDeletedSlots();
    }

    // --- Odwoływanie pojedynczych zajęć ---

    @GetMapping("/{id}/cancelled-sessions")
    public List<CancelledSessionResponse> getCancelledSessions(@PathVariable UUID id) {
        return service.getCancelledSessions(id);
    }

    @PostMapping("/{id}/cancel-session")
    public ResponseEntity<Void> cancelSession(@PathVariable UUID id,
                                              @Valid @RequestBody CancelSessionRequest request) {
        service.cancelSession(id, request.sessionDate());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{id}/cancel-session")
    public ResponseEntity<Void> restoreSession(@PathVariable UUID id,
                                               @RequestParam java.time.LocalDate date) {
        service.restoreSession(id, date);
        return ResponseEntity.noContent().build();
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
