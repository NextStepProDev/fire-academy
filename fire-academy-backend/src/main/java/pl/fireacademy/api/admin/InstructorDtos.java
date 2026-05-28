package pl.fireacademy.api.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.jspecify.annotations.Nullable;
import pl.fireacademy.domain.event.EventCategory;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public final class InstructorDtos {

    private InstructorDtos() {}

    public record InstructorResponse(
            UUID id,
            String firstName,
            String lastName,
            @Nullable String bio,
            @Nullable String photoUrl,
            Set<EventCategory> categories,
            int displayOrder,
            boolean active,
            Instant createdAt
    ) {}

    public record CreateInstructorRequest(
            @NotBlank String firstName,
            @NotBlank String lastName,
            @Nullable String bio,
            @NotEmpty Set<EventCategory> categories
    ) {}

    public record UpdateInstructorRequest(
            @NotBlank String firstName,
            @NotBlank String lastName,
            @Nullable String bio,
            @NotEmpty Set<EventCategory> categories
    ) {}
}
