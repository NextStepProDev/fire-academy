package pl.fireacademy.api.trainingcalendar;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import pl.fireacademy.domain.training.AttachmentKind;
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

    /** One row of the coach's roster. The overtraining flag lands here later. */
    public record AthleteSummary(
            UUID id,
            String firstName,
            String lastName,
            String email,
            @Nullable String avatarUrl,
            long unreadCount
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
            /** The other side touched this since the viewer last looked — drives the dot on the card. */
            boolean unread,
            int commentCount,
            List<AttachmentResponse> attachments,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {}

    /** One calendar page. The read-only recurring-session overlay joins this later. */
    public record CalendarRangeResponse(
            LocalDate from,
            LocalDate to,
            List<PersonalTrainingResponse> trainings,
            /** Deleted future trainings the viewer has not dismissed yet. */
            List<DeletedTrainingNotice> deletions
    ) {}

    public record DeletedTrainingNotice(
            UUID id,
            LocalDate date,
            @Nullable LocalTime startTime,
            String title,
            Instant deletedAt
    ) {}

    public record TrainingCommentResponse(
            UUID id,
            String body,
            boolean fromCoach,
            @Nullable String authorName,
            Instant createdAt
    ) {}

    public record AddCommentRequest(
            @NotBlank @Size(max = 1000) String body
    ) {}

    /** Lightweight payload behind the account tile badge — deliberately cheaper than a full page. */
    public record MyTrainingSummary(
            long unreadCount,
            int deletedCount,
            @Nullable LocalDate nextTrainingDate
    ) {}

    /**
     * Times are optional and omitting them is the normal case. Passing an end without a start is
     * rejected — without an hour grid there is nothing for a bare end time to mean.
     *
     * @param attachments {@code null} = leave materials alone · {@code []} = clear them ·
     *                    a list = replace with exactly that. The three-way contract matters because
     *                    a drag-and-drop re-date sends the whole training and must not silently drop
     *                    the materials it never touched.
     */
    public record CreateTrainingRequest(
            @NotNull LocalDate date,
            @Nullable LocalTime startTime,
            @Nullable LocalTime endTime,
            @NotBlank @Size(max = 150) String title,
            @Nullable @Size(max = 2000) String description,
            @Nullable @Size(max = 3) List<@Valid AttachmentRequest> attachments
    ) {}

    public record UpdateTrainingRequest(
            @NotNull LocalDate date,
            @Nullable LocalTime startTime,
            @Nullable LocalTime endTime,
            @NotBlank @Size(max = 150) String title,
            @Nullable @Size(max = 2000) String description,
            @Nullable @Size(max = 3) List<@Valid AttachmentRequest> attachments,
            @NotNull Long version
    ) {}

    /** Exactly one of {@code url} / {@code videoId} must be set, matching {@code kind}. */
    public record AttachmentRequest(
            @NotNull AttachmentKind kind,
            @Nullable @Size(max = 150) String label,
            @Nullable @Size(max = 500) String url,
            @Nullable UUID videoId
    ) {}

    public record AttachmentResponse(
            UUID id,
            AttachmentKind kind,
            @Nullable String label,
            /** For a LINK the address itself; for a VIDEO the library entry's own URL. */
            @Nullable String url,
            @Nullable UUID videoId,
            @Nullable String videoName,
            /** Canonical player URL, built from the id — a pasted link never reaches an iframe. */
            @Nullable String embedUrl,
            @Nullable String thumbnailUrl
    ) {}

    public record ExerciseVideoResponse(
            UUID id,
            String name,
            String url,
            @Nullable String description,
            @Nullable String category,
            String embedUrl,
            String thumbnailUrl,
            boolean archived
    ) {}

    public record PagedExerciseVideos(
            List<ExerciseVideoResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}

    public record ExerciseVideoRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 500) String url,
            @Nullable @Size(max = 1000) String description,
            @Nullable @Size(max = 80) String category
    ) {}

    public record TrainingTemplateResponse(
            UUID id,
            String title,
            @Nullable String description,
            @Nullable Integer defaultDurationMinutes,
            List<AttachmentResponse> attachments
    ) {}

    public record TrainingTemplateRequest(
            @NotBlank @Size(max = 150) String title,
            @Nullable @Size(max = 2000) String description,
            @Nullable @Min(15) @Max(720) Integer defaultDurationMinutes,
            @Nullable @Size(max = 3) List<@Valid AttachmentRequest> attachments
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
