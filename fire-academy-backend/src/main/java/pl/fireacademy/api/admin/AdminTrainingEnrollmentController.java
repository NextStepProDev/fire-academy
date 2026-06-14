package pl.fireacademy.api.admin;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.fireacademy.api.admin.TrainingSlotDtos.SetPaymentRequest;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/training-enrollments")
public class AdminTrainingEnrollmentController {

    private final AdminTrainingEnrollmentService service;

    public AdminTrainingEnrollmentController(AdminTrainingEnrollmentService service) {
        this.service = service;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@PathVariable UUID id) {
        service.remove(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/payment")
    public ResponseEntity<Void> setPayment(@PathVariable UUID id, @Valid @RequestBody SetPaymentRequest request) {
        service.setPayment(id, request);
        return ResponseEntity.noContent().build();
    }
}
