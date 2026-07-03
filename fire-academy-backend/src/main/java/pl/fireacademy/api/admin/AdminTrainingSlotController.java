package pl.fireacademy.api.admin;

import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.fireacademy.api.RequestParams;
import pl.fireacademy.api.admin.TrainingSlotDtos.*;
import pl.fireacademy.infrastructure.i18n.MessageService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/training-slots")
public class AdminTrainingSlotController {

    private final AdminTrainingSlotService service;
    private final AdminTrainingEnrollmentService enrollmentService;
    private final MessageService msg;

    public AdminTrainingSlotController(AdminTrainingSlotService service,
                                       AdminTrainingEnrollmentService enrollmentService,
                                       MessageService msg) {
        this.service = service;
        this.enrollmentService = enrollmentService;
        this.msg = msg;
    }

    @GetMapping
    public List<TrainingSlotResponse> getAll(@RequestParam(required = false) @Nullable String month) {
        return service.getAll(RequestParams.parseMonth(month, msg));
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

    /** Scheduled deactivation from a date (notifies the enrolled participants by email). */
    @PostMapping("/{id}/deactivate")
    public TrainingSlotResponse deactivate(@PathVariable UUID id, @Valid @RequestBody DeactivateSlotRequest request) {
        return service.deactivate(id, request.from());
    }

    @PostMapping("/{id}/reactivate")
    public TrainingSlotResponse reactivate(@PathVariable UUID id) {
        return service.reactivate(id);
    }

    /** Archive of deleted slots together with the contact data of former participants. */
    @GetMapping("/deleted")
    public List<DeletedSlotResponse> getDeleted() {
        return service.getDeletedSlots();
    }

    // --- Cancelling individual sessions ---

    @GetMapping("/{id}/cancelled-sessions")
    public List<CancelledSessionResponse> getCancelledSessions(@PathVariable UUID id) {
        return service.getCancelledSessions(id);
    }

    /** Club-wide overview of cancelled sessions (who + when, upcoming and archive). */
    @GetMapping("/cancelled-sessions/overview")
    public List<CancelledSessionOverviewItem> getCancelledOverview() {
        return service.getCancelledOverview();
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

    /** Cancel all of one instructor's sessions on a date (e.g. the instructor is unavailable). */
    @PostMapping("/cancel-instructor-day")
    public CancelInstructorDayResponse cancelInstructorDay(@Valid @RequestBody CancelInstructorDayRequest request) {
        return new CancelInstructorDayResponse(service.cancelInstructorDay(request.instructorId(), request.date()));
    }

    // --- Enrollment management (roster) ---

    @GetMapping("/{slotId}/enrollments")
    public List<RosterEntry> getRoster(@PathVariable UUID slotId,
                                       @RequestParam(required = false) @Nullable String month) {
        return enrollmentService.getRoster(slotId, RequestParams.parseMonth(month, msg));
    }

    @PostMapping("/{slotId}/enrollments")
    public ResponseEntity<Void> addEnrollment(@PathVariable UUID slotId,
                                              @Valid @RequestBody AdminAddEnrollmentRequest request) {
        enrollmentService.addEnrollment(slotId, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
