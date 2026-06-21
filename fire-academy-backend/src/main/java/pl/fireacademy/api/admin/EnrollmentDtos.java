package pl.fireacademy.api.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class EnrollmentDtos {

    private EnrollmentDtos() {}

    public record EnrollmentResponse(
            UUID id,
            UUID eventId,
            String eventTypeName,
            LocalDate eventStartDate,
            String firstName,
            String lastName,
            String email,
            @Nullable String phone,
            @Nullable String note,
            boolean addedByAdmin,
            Instant createdAt
    ) {}

    // Admin can only add an existing account (user selection) — personal data comes from the account.
    public record AdminEnrollRequest(
            @NotNull(message = "{validation.event.required}") UUID eventId,
            @NotNull(message = "{validation.user.required}") UUID userId,
            @Nullable @Size(max = 2000, message = "{validation.note.size}") String note
    ) {}

    public record BulkEmailRequest(
            @NotNull UUID eventId,
            @NotBlank @Size(max = 5000, message = "{validation.note.size}") String message
    ) {}

    public record BulkEmailResponse(int recipientCount) {}
}
