package pl.fireacademy.api.pub;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
            @Nullable UUID eventTypeId,
            String eventTypeName,
            @Nullable String description,
            LocalDate startDate,
            @Nullable LocalDate endDate,
            @Nullable LocalTime startTime,
            @Nullable LocalTime endTime,
            @Nullable String location,
            @Nullable BigDecimal price,
            @Nullable Integer maxParticipants,
            int availableSpots
    ) {}

    public record EnrollRequest(
            @NotBlank @Size(min = 3, max = 36, message = "{validation.firstname.size}") String firstName,
            @NotBlank @Size(min = 3, max = 36, message = "{validation.lastname.size}") String lastName,
            @Email @NotBlank String email,
            @NotBlank @Pattern(regexp = "^(\\d{9}|\\+\\d{1,4}\\d{9})$", message = "{validation.phone.format}") String phone,
            @Nullable @Size(max = 2000, message = "{validation.note.size}") String note
    ) {}
}
