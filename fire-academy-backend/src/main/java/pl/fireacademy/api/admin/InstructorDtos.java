package pl.fireacademy.api.admin;

import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public final class InstructorDtos {

    private InstructorDtos() {}

    public record InstructorResponse(
            UUID id,
            String firstName,
            String lastName,
            @Nullable String bio,
            @Nullable String photoUrl,
            int displayOrder,
            boolean active,
            Instant createdAt
    ) {}

    public record CreateInstructorRequest(
            @NotBlank String firstName,
            @NotBlank String lastName,
            @Nullable String bio
    ) {}

    public record UpdateInstructorRequest(
            @NotBlank String firstName,
            @NotBlank String lastName,
            @Nullable String bio
    ) {}
}
