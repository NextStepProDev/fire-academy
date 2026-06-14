package pl.fireacademy.api.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public final class TrainingSlotDtos {

    private TrainingSlotDtos() {}

    public record TrainingSlotResponse(
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
            int displayOrder,
            long enrolledThisMonth,
            boolean active,
            Instant createdAt
    ) {}

    public record CreateTrainingSlotRequest(
            @NotNull UUID eventTypeId,
            @Nullable UUID instructorId,
            @NotNull @Min(1) @Max(7) Integer dayOfWeek,
            @NotNull LocalTime startTime,
            @Nullable LocalTime endTime,
            @Nullable @PositiveOrZero BigDecimal price,
            @NotNull @Min(1) Integer maxParticipants
    ) {}

    public record UpdateTrainingSlotRequest(
            @NotNull UUID eventTypeId,
            @Nullable UUID instructorId,
            @NotNull @Min(1) @Max(7) Integer dayOfWeek,
            @NotNull LocalTime startTime,
            @Nullable LocalTime endTime,
            @Nullable @PositiveOrZero BigDecimal price,
            @NotNull @Min(1) Integer maxParticipants
    ) {}

    /** Zbiorcze tworzenie wielu slotów jednego rodzaju z tym samym trenerem (różne dni/godziny). */
    public record BatchCreateTrainingSlotRequest(
            @NotNull UUID eventTypeId,
            @Nullable UUID instructorId,
            @NotEmpty @Valid List<SlotRow> slots
    ) {}

    public record SlotRow(
            @NotNull @Min(1) @Max(7) Integer dayOfWeek,
            @NotNull LocalTime startTime,
            @Nullable LocalTime endTime,
            @Nullable @PositiveOrZero BigDecimal price,
            @NotNull @Min(1) Integer maxParticipants
    ) {}

    /** Pozycja rostera: jeden zapisany uczestnik na dany miesiąc. */
    public record RosterEntry(
            UUID enrollmentId,
            UUID userId,
            String firstName,
            String lastName,
            String email,
            String phone,
            YearMonth startMonth,
            @Nullable YearMonth endMonth,
            boolean indefinite,
            boolean paid
    ) {}

    public record AdminAddEnrollmentRequest(
            @NotNull UUID userId,
            @NotNull YearMonth startMonth,
            @Nullable @Min(1) @Max(24) Integer months
    ) {}

    public record SetPaymentRequest(
            @NotNull YearMonth month,
            boolean paid
    ) {}
}
