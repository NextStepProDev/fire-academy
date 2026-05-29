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
            long enrollmentCount,
            boolean active,
            Instant createdAt
    ) {}

    public record CreateEventRequest(
            @Nullable UUID eventTypeId,
            @Nullable String customName,
            @Nullable String description,
            @NotNull EventCategory category,
            @NotNull LocalDate startDate,
            @Nullable LocalDate endDate,
            @Nullable LocalTime startTime,
            @Nullable LocalTime endTime,
            @Nullable String location,
            @Nullable BigDecimal price,
            @Nullable Integer maxParticipants
    ) {}

    public record UpdateEventRequest(
            @NotNull LocalDate startDate,
            @Nullable String description,
            @Nullable LocalDate endDate,
            @Nullable LocalTime startTime,
            @Nullable LocalTime endTime,
            @Nullable String location,
            @Nullable BigDecimal price,
            @Nullable Integer maxParticipants
    ) {}
}
