package pl.fireacademy.api.user;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.UUID;

public final class TrainingEnrollmentDtos {

    private TrainingEnrollmentDtos() {}

    /** {@code months} puste = na czas nieokreślony; podane = subskrypcja na N miesięcy. */
    public record EnrollTrainingRequest(
            @NotNull YearMonth startMonth,
            @Nullable @Min(1) @Max(24) Integer months
    ) {}

    public record MyTrainingEnrollmentResponse(
            UUID id,
            UUID slotId,
            UUID eventTypeId,
            String eventTypeName,
            @Nullable String instructorName,
            int dayOfWeek,
            LocalTime startTime,
            @Nullable LocalTime endTime,
            @Nullable BigDecimal price,
            YearMonth startMonth,
            @Nullable YearMonth endMonth,
            YearMonth billingMonth,
            int sessionsInBillingMonth,
            @Nullable BigDecimal monthlyAmount,
            java.util.List<java.time.LocalDate> cancelledDates
    ) {}
}
