package pl.fireacademy.api.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

    public record AdminEnrollRequest(
            @NotNull UUID eventId,
            @NotBlank @Size(min = 3, max = 36, message = "{validation.firstname.size}") String firstName,
            @NotBlank @Size(min = 3, max = 36, message = "{validation.lastname.size}") String lastName,
            @Email @NotBlank String email,
            @Nullable @Pattern(regexp = "^(\\d{9}|\\+\\d{1,4}\\d{9})$", message = "{validation.phone.format}") String phone,
            @Nullable @Size(max = 2000, message = "{validation.note.size}") String note
    ) {}

    public record AnonymizeResponse(int anonymizedCount) {}

    public record BulkEmailRequest(
            @NotNull UUID eventId,
            @NotBlank @Size(max = 5000, message = "{validation.note.size}") String message
    ) {}

    public record BulkEmailResponse(int recipientCount) {}
}
