package pl.fireacademy.api.user;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.fireacademy.api.auth.AuthDtos.MessageResponse;
import pl.fireacademy.api.user.TrainingEnrollmentDtos.*;
import pl.fireacademy.config.CurrentUserId;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/user")
public class TrainingEnrollmentController {

    private final TrainingEnrollmentService service;

    public TrainingEnrollmentController(TrainingEnrollmentService service) {
        this.service = service;
    }

    @PostMapping("/training-slots/{slotId}/enroll")
    public ResponseEntity<MessageResponse> enroll(@CurrentUserId UUID userId,
                                                  @PathVariable UUID slotId,
                                                  @Valid @RequestBody EnrollTrainingRequest request) {
        service.enroll(userId, slotId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new MessageResponse("Zapis potwierdzony."));
    }

    @GetMapping("/training-enrollments")
    public List<MyTrainingEnrollmentResponse> getMyEnrollments(@CurrentUserId UUID userId) {
        return service.getMyEnrollments(userId);
    }

    @DeleteMapping("/training-enrollments/{id}")
    public ResponseEntity<Void> cancel(@CurrentUserId UUID userId, @PathVariable UUID id) {
        service.cancel(userId, id);
        return ResponseEntity.noContent().build();
    }
}
