package pl.fireacademy.api.user;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.fireacademy.api.auth.AuthDtos.MessageResponse;
import pl.fireacademy.config.CurrentUserId;

import java.util.UUID;

@RestController
@RequestMapping("/api/user")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
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

    @PutMapping("/me/notifications")
    public ResponseEntity<Void> updateNotifications(@CurrentUserId UUID userId, @RequestBody UserDtos.UpdateNotificationsRequest request) {
        userService.updateNotifications(userId, request);
        return ResponseEntity.noContent().build();
    }
}
