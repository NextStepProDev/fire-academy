package pl.fireacademy.api.admin;

import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;
import pl.fireacademy.domain.event.EventCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public final class EventDtos {

    private EventDtos() {}

    public record EventResponse(
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
            long enrollmentCount,
            boolean active,
            Instant createdAt
    ) {}

    public record CreateEventRequest(
            @Nullable UUID eventTypeId,
            @Nullable String customName,
            @NotNull EventCategory category,
            @NotNull LocalDate startDate,
            @Nullable LocalDate endDate,
            @Nullable LocalTime startTime,
            @Nullable String location,
            @Nullable BigDecimal price,
            @Nullable Integer maxParticipants,
            @Nullable String duration
    ) {}

    public record UpdateEventRequest(
            @NotNull LocalDate startDate,
            @Nullable LocalDate endDate,
            @Nullable LocalTime startTime,
            @Nullable String location,
            @Nullable BigDecimal price,
            @Nullable Integer maxParticipants,
            @Nullable String duration
    ) {}
}
