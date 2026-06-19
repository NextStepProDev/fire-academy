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
            boolean marketingConsent,
            Instant createdAt
    ) {}

    // audience: MARKETING (tylko zgody marketingowe + link rezygnacji), ALL (komunikat serwisowy do wszystkich),
    // SELECTED (wybrane osoby z userIds). Walidacja wartości w serwisie.
    public record SendEmailRequest(
            @NotBlank @Size(max = 200, message = "{validation.email.subject.size}") String subject,
            @NotBlank @Size(max = 10000, message = "{validation.email.message.size}") String message,
            @NotBlank String audience,
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
            boolean marketingConsent,
            String preferredLanguage,
            boolean hasPassword,
            boolean oauthLinked,
            @Nullable String avatarUrl,
            Instant createdAt,
            List<UserEnrollmentResponse> currentEnrollments,
            List<UserEnrollmentResponse> pastEnrollments
    ) {}
}
