package pl.fireacademy.api.trainingcalendar;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import pl.fireacademy.domain.training.TrainingStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Shared response shapes for the 1-on-1 training calendar.
 * <p>
 * Both the coach view ({@code /api/admin/...}) and the client view ({@code /api/user/my-training/...})
 * return the SAME records — that is the whole point of keeping the two controllers in one package.
 * The frontend renders one component for both roles, so any divergence here would immediately show up
 * as a second code path on the client.
 */
public final class TrainingCalendarDtos {

    private TrainingCalendarDtos() {}

    /** One row of the coach's roster. Unread counters and the overtraining flag land here later. */
    public record AthleteSummary(
            UUID id,
            String firstName,
            String lastName,
            String email,
            @Nullable String avatarUrl
    ) {}

    /**
     * A single training.
     *
     * @param status  computed server-side and never stored; the frontend only colours it, so the
     *                two can never disagree about what "missed" means at a day boundary
     * @param version echoed back on update — the client holds it to detect a concurrent edit
     */
    public record PersonalTrainingResponse(
            UUID id,
            LocalDate date,
            @Nullable LocalTime startTime,
            @Nullable LocalTime endTime,
            String title,
            @Nullable String description,
            TrainingStatus status,
            @Nullable Instant completedAt,
            @Nullable String feedback,
            @Nullable Integer rpe,
            boolean createdByAdmin,
            boolean lastModifiedByAdmin,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {}

    /** One calendar page. The read-only recurring-session overlay joins this later. */
    public record CalendarRangeResponse(
            LocalDate from,
            LocalDate to,
            List<PersonalTrainingResponse> trainings
    ) {}

    /**
     * Times are optional and omitting them is the normal case. Passing an end without a start is
     * rejected — without an hour grid there is nothing for a bare end time to mean.
     */
    public record CreateTrainingRequest(
            @NotNull LocalDate date,
            @Nullable LocalTime startTime,
            @Nullable LocalTime endTime,
            @NotBlank @Size(max = 150) String title,
            @Nullable @Size(max = 2000) String description
    ) {}

    public record UpdateTrainingRequest(
            @NotNull LocalDate date,
            @Nullable LocalTime startTime,
            @Nullable LocalTime endTime,
            @NotBlank @Size(max = 150) String title,
            @Nullable @Size(max = 2000) String description,
            @NotNull Long version
    ) {}

    /** RPE is mandatory: a ticked-off training with no perceived effort tells the coach nothing. */
    public record CompleteTrainingRequest(
            @NotNull @Min(1) @Max(10) Integer rpe,
            @Nullable @Size(max = 2000) String feedback
    ) {}

    /** Shifts a copy forward; the default lands it on the same weekday next week. */
    public record DuplicateTrainingRequest(
            @Nullable Integer offsetDays
    ) {}

    public enum PasteMode {
        /** Leaves the source alone. */
        COPY,
        /** Moves the original, so its id, completion and (later) comments survive the move. */
        MOVE
    }

    public record PasteTrainingRequest(
            @NotNull UUID sourceId,
            @NotNull LocalDate targetDate,
            @NotNull PasteMode mode
    ) {}
}
