package pl.fireacademy.api.pub;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class PublicDtos {

    private PublicDtos() {}

    public record InstructorCard(
            UUID id,
            String firstName,
            String lastName,
            @Nullable String bio,
            @Nullable String photoUrl
    ) {}

    public record PhotoItem(UUID id, String url, int displayOrder) {}

    public record EventTypeCard(
            UUID id,
            String name,
            @Nullable String description,
            @Nullable String thumbnailUrl,
            List<PhotoItem> photos
    ) {}

    public record EventCard(
            UUID id,
            UUID eventTypeId,
            String eventTypeName,
            LocalDate startDate,
            @Nullable LocalDate endDate,
            @Nullable LocalTime startTime,
            @Nullable String location,
            @Nullable BigDecimal price,
            @Nullable Integer maxParticipants,
            @Nullable String duration,
            int availableSpots
    ) {}

    public record EnrollRequest(
            @NotBlank String firstName,
            @NotBlank String lastName,
            @Email @NotBlank String email,
            @NotBlank String phone
    ) {}
}
