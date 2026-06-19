package pl.fireacademy.api.user;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pl.fireacademy.api.auth.AuthDtos.MessageResponse;
import pl.fireacademy.api.user.UserEnrollmentDtos.MyEnrollmentsResponse;
import pl.fireacademy.config.CurrentUserId;

import java.util.UUID;

@RestController
@RequestMapping("/api/user")
public class UserController {
    private final UserService userService;
    private final UserEnrollmentService userEnrollmentService;

    public UserController(UserService userService, UserEnrollmentService userEnrollmentService) {
        this.userService = userService;
        this.userEnrollmentService = userEnrollmentService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserDtos.UserResponse> getMe(@CurrentUserId UUID userId) {
        return ResponseEntity.ok(userService.getMe(userId));
    }

    @PutMapping("/me")
    public ResponseEntity<UserDtos.UserResponse> updateMe(@CurrentUserId UUID userId, @Valid @RequestBody UserDtos.UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateMe(userId, request));
    }

    @PutMapping("/me/password")
    public ResponseEntity<MessageResponse> changePassword(@CurrentUserId UUID userId, @Valid @RequestBody UserDtos.ChangePasswordRequest request) {
        userService.changePassword(userId, request);
        return ResponseEntity.ok(new MessageResponse("Password changed successfully"));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMe(@CurrentUserId UUID userId, @RequestBody UserDtos.DeleteAccountRequest request) {
        userService.deleteMe(userId, request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/me/marketing")
    public ResponseEntity<UserDtos.UserResponse> updateMarketing(@CurrentUserId UUID userId, @RequestBody UserDtos.UpdateMarketingRequest request) {
        return ResponseEntity.ok(userService.updateMarketing(userId, request));
    }

    @PostMapping("/me/consents")
    public ResponseEntity<UserDtos.UserResponse> submitConsents(@CurrentUserId UUID userId, @RequestBody UserDtos.ConsentsRequest request) {
        return ResponseEntity.ok(userService.submitConsents(userId, request));
    }

    @PostMapping("/me/avatar")
    public ResponseEntity<UserDtos.UserResponse> uploadAvatar(@CurrentUserId UUID userId, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(userService.uploadAvatar(userId, file));
    }

    @DeleteMapping("/me/avatar")
    public ResponseEntity<UserDtos.UserResponse> deleteAvatar(@CurrentUserId UUID userId) {
        return ResponseEntity.ok(userService.deleteAvatar(userId));
    }

    @PostMapping("/enrollments")
    public ResponseEntity<MessageResponse> enroll(@CurrentUserId UUID userId,
                                                  @Valid @RequestBody UserEnrollmentDtos.EnrollRequest request) {
        userEnrollmentService.enroll(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new MessageResponse("Zapis potwierdzony. Sprawdź swoją skrzynkę email."));
    }

    @GetMapping("/enrollments")
    public ResponseEntity<MyEnrollmentsResponse> getMyEnrollments(@CurrentUserId UUID userId) {
        return ResponseEntity.ok(userEnrollmentService.getMyEnrollments(userId));
    }

    @DeleteMapping("/enrollments/{id}")
    public ResponseEntity<Void> cancelEnrollment(@CurrentUserId UUID userId, @PathVariable UUID id) {
        userEnrollmentService.cancelMyEnrollment(userId, id);
        return ResponseEntity.noContent().build();
    }
}
