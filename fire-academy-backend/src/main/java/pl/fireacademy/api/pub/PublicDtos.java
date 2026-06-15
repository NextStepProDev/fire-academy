package pl.fireacademy.api.pub;

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

    public record TrainingSlotCard(
            UUID id,
            UUID eventTypeId,
            String eventTypeName,
            @Nullable UUID instructorId,
            @Nullable String instructorName,
            int dayOfWeek,
            LocalTime startTime,
            @Nullable LocalTime endTime,
            @Nullable BigDecimal price,
            int maxParticipants,
            int availableSpots,
            java.util.List<java.time.LocalDate> cancelledDates
    ) {}
}
