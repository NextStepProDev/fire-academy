package pl.fireacademy.api.user;

import jakarta.validation.constraints.*;
import org.jspecify.annotations.Nullable;
import java.time.Instant;
import java.util.UUID;

public final class UserDtos {
    private UserDtos() {}

    public record UserResponse(UUID id, String email, String firstName, String lastName,
                                @Nullable String phone, String role, boolean isAdmin,
                                boolean emailVerified, boolean emailNotificationsEnabled,
                                String preferredLanguage, boolean hasPassword,
                                boolean oauthLinked, @Nullable String avatarUrl, Instant createdAt) {}

    public record UpdateUserRequest(
        @NotBlank @Size(min = 3, max = 100) String firstName,
        @NotBlank @Size(min = 3, max = 100) String lastName,
        @Nullable String phone
    ) {}

    public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 8, max = 100) String newPassword
    ) {}

    public record DeleteAccountRequest(@Nullable String password) {}

    public record UpdateNotificationsRequest(boolean enabled) {}
}
