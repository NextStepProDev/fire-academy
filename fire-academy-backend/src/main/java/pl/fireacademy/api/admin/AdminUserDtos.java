package pl.fireacademy.api.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import pl.fireacademy.domain.event.EventCategory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class AdminUserDtos {

    private AdminUserDtos() {}

    public record AdminUserResponse(
            UUID id,
            String email,
            String firstName,
            String lastName,
            @Nullable String phone,
            String role,
            boolean isAdmin,
            boolean superAdmin,
            boolean emailVerified,
            boolean emailNotificationsEnabled,
            Instant createdAt
    ) {}

    public record SendEmailRequest(
            @NotBlank @Size(max = 200, message = "{validation.email.subject.size}") String subject,
            @NotBlank @Size(max = 10000, message = "{validation.email.message.size}") String message,
            boolean allUsers,
            @Nullable List<UUID> userIds
    ) {}

    public record SendEmailResponse(int recipientCount) {}

    public record DeleteUserResponse(int freedEnrollments, int anonymizedEnrollments) {}

    public record PagedUsersResponse(
            List<AdminUserResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}

    public record UserEnrollmentResponse(
            UUID id,
            UUID eventId,
            String eventName,
            EventCategory category,
            LocalDate startDate,
            @Nullable LocalDate endDate,
            @Nullable LocalTime startTime,
            @Nullable LocalTime endTime,
            @Nullable String location,
            @Nullable String note,
            boolean addedByAdmin,
            boolean past,
            Instant createdAt
    ) {}

    public record AdminUserDetailResponse(
            UUID id,
            String email,
            String firstName,
            String lastName,
            @Nullable String phone,
            String role,
            boolean isAdmin,
            boolean superAdmin,
            boolean emailVerified,
            boolean emailNotificationsEnabled,
            String preferredLanguage,
            boolean hasPassword,
            boolean oauthLinked,
            @Nullable String avatarUrl,
            Instant createdAt,
            List<UserEnrollmentResponse> currentEnrollments,
            List<UserEnrollmentResponse> pastEnrollments
    ) {}
}
