package pl.fireacademy.api.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
            String phone,
            boolean addedByAdmin,
            Instant createdAt
    ) {}

    public record AdminEnrollRequest(
            @NotNull UUID eventId,
            @NotBlank String firstName,
            @NotBlank String lastName,
            @Email @NotBlank String email,
            @NotBlank @Pattern(regexp = "^(\\d{9}|\\+\\d{2}\\d{9})$", message = "{validation.phone.format}") String phone
    ) {}
}
