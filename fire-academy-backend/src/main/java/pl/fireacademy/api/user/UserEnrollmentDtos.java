package pl.fireacademy.api.user;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import pl.fireacademy.domain.event.EventCategory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class UserEnrollmentDtos {

    private UserEnrollmentDtos() {}

    public record EnrollRequest(
            @NotNull(message = "{validation.event.required}") UUID eventId,
            @Nullable @Size(max = 2000, message = "{validation.note.size}") String note
    ) {}

    public record MyEnrollmentResponse(
            UUID id,
            UUID eventId,
            String eventName,
            EventCategory category,
            LocalDate startDate,
            @Nullable LocalDate endDate,
            @Nullable LocalTime startTime,
            @Nullable LocalTime endTime,
            @Nullable String location,
            @Nullable String note,
            boolean past,
            boolean canCancel,
            Instant createdAt
    ) {}

    public record MyEnrollmentsResponse(
            List<MyEnrollmentResponse> current,
            List<MyEnrollmentResponse> past
    ) {}
}
