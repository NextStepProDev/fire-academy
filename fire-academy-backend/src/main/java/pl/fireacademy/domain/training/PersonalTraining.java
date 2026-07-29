package pl.fireacademy.domain.training;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import pl.fireacademy.domain.user.User;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/**
 * One entry in a client's 1-on-1 plan.
 * <p>
 * The time of day is optional and its absence is the normal case — an untimed training means
 * "do this that day". Callers must not assume {@link #getStartTime()} is present.
 */
@Entity
@Table(name = "personal_trainings")
public class PersonalTraining {

    public static final int MAX_TITLE_LENGTH = 150;
    public static final int MAX_DESCRIPTION_LENGTH = 2000;
    public static final int MAX_FEEDBACK_LENGTH = 2000;
    public static final int MIN_RPE = 1;
    public static final int MAX_RPE = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private User athlete;

    @Column(name = "training_date", nullable = false)
    private LocalDate date;

    @Column(name = "start_time")
    @Nullable
    private LocalTime startTime;

    @Column(name = "end_time")
    @Nullable
    private LocalTime endTime;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(length = MAX_DESCRIPTION_LENGTH)
    @Nullable
    private String description;

    @Column(name = "created_by_admin", nullable = false)
    private boolean createdByAdmin;

    @Column(name = "last_modified_by_admin", nullable = false)
    private boolean lastModifiedByAdmin;

    @Column(name = "completed_at")
    @Nullable
    private Instant completedAt;

    @Column(length = MAX_FEEDBACK_LENGTH)
    @Nullable
    private String feedback;

    @Column
    @Nullable
    private Integer rpe;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PersonalTraining() {}

    public PersonalTraining(User athlete, LocalDate date, String title, boolean byAdmin) {
        this.athlete = athlete;
        this.date = date;
        this.title = title;
        this.createdByAdmin = byAdmin;
        this.lastModifiedByAdmin = byAdmin;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Applies an edit to the schedule and content. Times are set as a pair: passing a null start
     * clears both, which is how a timed training becomes untimed.
     */
    public void edit(LocalDate date, @Nullable LocalTime startTime, @Nullable LocalTime endTime,
                     String title, @Nullable String description, boolean byAdmin) {
        this.date = date;
        this.startTime = startTime;
        this.endTime = startTime == null ? null : endTime;
        this.title = title;
        this.description = description;
        this.lastModifiedByAdmin = byAdmin;
    }

    /** Untimed trainings start at midnight, so they can be ticked off from the beginning of the day. */
    public LocalDateTime start() {
        return startTime == null ? date.atStartOfDay() : date.atTime(startTime);
    }

    /** Untimed trainings run to the end of the day — that is when they count as missed. */
    public LocalDateTime end() {
        if (startTime == null) {
            return date.atTime(LocalTime.MAX);
        }
        return date.atTime(endTime == null ? LocalTime.MAX : endTime);
    }

    public boolean hasStarted(LocalDateTime now) {
        return !now.isBefore(start());
    }

    public boolean isCompleted() {
        return completedAt != null;
    }

    public TrainingStatus status(LocalDateTime now) {
        if (completedAt != null) {
            return TrainingStatus.COMPLETED;
        }
        return now.isAfter(end()) ? TrainingStatus.MISSED : TrainingStatus.PLANNED;
    }

    /**
     * Ticks the training off. Only the client does this, and only once it has started — logging a
     * session days later is fine, claiming a future one is not.
     * <p>
     * Clearing {@code lastModifiedByAdmin} is load-bearing: {@code @PreUpdate} bumps {@code updatedAt}
     * on every write, so the role flag is the only thing separating "the coach changed something" from
     * "the client did". Without this the client would light their own "new from coach" badge.
     */
    public void complete(int rpe, @Nullable String feedback) {
        if (rpe < MIN_RPE || rpe > MAX_RPE) {
            throw new IllegalArgumentException("RPE out of range: " + rpe);
        }
        this.completedAt = Instant.now();
        this.rpe = rpe;
        this.feedback = feedback;
        this.lastModifiedByAdmin = false;
    }

    /** Undoes completion. Clears RPE and feedback — the DB CHECK will not accept an RPE without one. */
    public void uncomplete() {
        this.completedAt = null;
        this.rpe = null;
        this.feedback = null;
        this.lastModifiedByAdmin = false;
    }

    public UUID getId() {
        return id;
    }

    public User getAthlete() {
        return athlete;
    }

    public LocalDate getDate() {
        return date;
    }

    @Nullable
    public LocalTime getStartTime() {
        return startTime;
    }

    @Nullable
    public LocalTime getEndTime() {
        return endTime;
    }

    public String getTitle() {
        return title;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    public boolean isCreatedByAdmin() {
        return createdByAdmin;
    }

    public boolean isLastModifiedByAdmin() {
        return lastModifiedByAdmin;
    }

    @Nullable
    public Instant getCompletedAt() {
        return completedAt;
    }

    @Nullable
    public String getFeedback() {
        return feedback;
    }

    @Nullable
    public Integer getRpe() {
        return rpe;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
