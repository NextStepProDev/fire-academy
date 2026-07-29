package pl.fireacademy.domain.training;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import pl.fireacademy.domain.user.User;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A goal the coach set on one of three horizons.
 * <p>
 * Achieved goals are immutable and are never deleted: they are the trophy case, and rewriting one
 * would edit somebody's history.
 */
@Entity
@Table(name = "athlete_goals")
public class AthleteGoal {

    public static final int MAX_CONTENT_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private User athlete;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GoalHorizon horizon;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GoalKind kind = GoalKind.GENERAL;

    @Column(name = "target_weight_kg", precision = 5, scale = 2)
    @Nullable
    private BigDecimal targetWeightKg;

    /**
     * The trend when the goal was set. Without it the goal cannot know whether progress means going
     * down or up, and the progress bar has nothing to measure from.
     */
    @Column(name = "start_weight_kg", precision = 5, scale = 2)
    @Nullable
    private BigDecimal startWeightKg;

    @Column(name = "achieved_automatically", nullable = false)
    private boolean achievedAutomatically = false;

    @Column(nullable = false, length = MAX_CONTENT_LENGTH)
    private String content;

    @Column(name = "target_date")
    @Nullable
    private LocalDate targetDate;

    @Column(name = "achieved_at")
    @Nullable
    private LocalDate achievedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AthleteGoal() {}

    public AthleteGoal(User athlete, GoalHorizon horizon, String content, @Nullable LocalDate targetDate) {
        this.athlete = athlete;
        this.horizon = horizon;
        this.content = content;
        this.targetDate = targetDate;
    }

    public static AthleteGoal weightGoal(User athlete, GoalHorizon horizon, String content,
                                         @Nullable LocalDate targetDate,
                                         BigDecimal targetWeightKg, BigDecimal startWeightKg) {
        AthleteGoal goal = new AthleteGoal(athlete, horizon, content, targetDate);
        goal.kind = GoalKind.WEIGHT;
        goal.targetWeightKg = targetWeightKg;
        goal.startWeightKg = startWeightKg;
        return goal;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void edit(String content, @Nullable LocalDate targetDate) {
        this.content = content;
        this.targetDate = targetDate;
    }

    /** The horizon deliberately cannot change: a short-term goal that became long-term is a new goal. */
    public void achieve(LocalDate date) {
        this.achievedAt = date;
        this.achievedAutomatically = false;
    }

    /** Closed by the weight log rather than by a person — and therefore reversible. */
    public void achieveAutomatically(LocalDate date) {
        this.achievedAt = date;
        this.achievedAutomatically = true;
    }

    /** Undoes an automatic close. Never valid for one a person made — see the service. */
    public void revertAutomaticAchievement() {
        this.achievedAt = null;
        this.achievedAutomatically = false;
    }

    /** Losing weight when the target sits below where the client started, gaining otherwise. */
    public boolean isLossGoal() {
        return targetWeightKg != null && startWeightKg != null
                && targetWeightKg.compareTo(startWeightKg) < 0;
    }

    /**
     * Whether the given trend reaches the target. Deliberately fed the 7-DAY TREND and never a
     * single morning reading: a raw number can touch the target through dehydration and bounce back
     * the next day, and celebrating that would contradict everything else the weight module says.
     */
    public boolean isMetBy(BigDecimal trendKg) {
        if (targetWeightKg == null) {
            return false;
        }
        return isLossGoal()
                ? trendKg.compareTo(targetWeightKg) <= 0
                : trendKg.compareTo(targetWeightKg) >= 0;
    }

    public boolean isAchieved() {
        return achievedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public GoalHorizon getHorizon() {
        return horizon;
    }

    public GoalKind getKind() {
        return kind;
    }

    @Nullable
    public BigDecimal getTargetWeightKg() {
        return targetWeightKg;
    }

    @Nullable
    public BigDecimal getStartWeightKg() {
        return startWeightKg;
    }

    public boolean isAchievedAutomatically() {
        return achievedAutomatically;
    }

    public String getContent() {
        return content;
    }

    @Nullable
    public LocalDate getTargetDate() {
        return targetDate;
    }

    @Nullable
    public LocalDate getAchievedAt() {
        return achievedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
