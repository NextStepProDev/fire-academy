package pl.fireacademy.api.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;
import pl.fireacademy.domain.event.EventCategory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class EventTypeDtos {

    private EventTypeDtos() {}

    public record EventTypeResponse(
            UUID id,
            String category,
            String name,
            @Nullable String description,
            @Nullable String thumbnailUrl,
            List<PhotoResponse> photos,
            int displayOrder,
            boolean active,
            Instant createdAt
    ) {}

    public record PhotoResponse(
            UUID id,
            String url,
            int displayOrder
    ) {}

    public record CreateEventTypeRequest(
            @NotNull EventCategory category,
            @NotBlank String name,
            @Nullable String description
    ) {}

    public record UpdateEventTypeRequest(
            @NotBlank String name,
            @Nullable String description
    ) {}
}
