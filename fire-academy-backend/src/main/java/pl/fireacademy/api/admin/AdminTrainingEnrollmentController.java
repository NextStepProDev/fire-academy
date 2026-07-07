package pl.fireacademy.api.admin;

import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.fireacademy.api.admin.TrainingSlotDtos.SetPaymentRequest;
import pl.fireacademy.api.admin.TrainingSlotDtos.SetStartRequest;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/training-enrollments")
public class AdminTrainingEnrollmentController {

    private final AdminTrainingEnrollmentService service;

    public AdminTrainingEnrollmentController(AdminTrainingEnrollmentService service) {
        this.service = service;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate date) {
        service.remove(id, date);
        return ResponseEntity.noContent().build();
    }

    /** Remove a person from ALL their live trainings at once, effective {@code date} (defaults to today). */
    @DeleteMapping("/user/{userId}")
    public ResponseEntity<Void> removeAllForUser(
            @PathVariable UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate date) {
        service.removeAllForUser(userId, date);
        return ResponseEntity.noContent().build();
    }

    /** One client's training-focused profile: subscriptions, payment history, refunds and surplus. */
    @GetMapping("/user/{userId}/history")
    public TrainingUserHistoryDtos.TrainingUserHistory history(@PathVariable UUID userId) {
        return service.getUserHistory(userId);
    }

    @PutMapping("/{id}/payment")
    public ResponseEntity<Void> setPayment(@PathVariable UUID id, @Valid @RequestBody SetPaymentRequest request) {
        service.setPayment(id, request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/start")
    public ResponseEntity<Void> setStart(@PathVariable UUID id, @Valid @RequestBody SetStartRequest request) {
        service.setStartDate(id, request);
        return ResponseEntity.noContent().build();
    }
}
