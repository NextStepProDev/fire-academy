package pl.fireacademy.api.admin;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * The training-focused profile of one client, shown when the organizer opens a person from the trainings section:
 * their subscriptions, when they paid each month, and the refunds/surplus settlements — everything about this client's
 * trainings in one place (event sign-ups live in the separate "Users" tab).
 */
public final class TrainingUserHistoryDtos {

    private TrainingUserHistoryDtos() {}

    public record TrainingUserHistory(
            UUID userId,
            String firstName,
            String lastName,
            String email,
            @Nullable String phone,
            Instant joinedAt,
            // Unused surplus (settled CREDITED refunds not yet consumed) still owed to this client across all trainings.
            BigDecimal creditBalance,
            List<Subscription> subscriptions,
            List<Payment> payments,
            List<Refund> refunds
    ) {}

    /** One of the client's training subscriptions (active or ended). */
    public record Subscription(
            UUID enrollmentId,
            String trainingName,
            @Nullable String instructorName,
            int dayOfWeek,
            LocalTime startTime,
            @Nullable LocalTime endTime,
            @Nullable BigDecimal price,
            String startMonth,
            @Nullable String endMonth,
            @Nullable LocalDate billableFrom,
            Instant enrolledAt,
            boolean active
    ) {}

    /** A month the client paid for — carries when it was marked paid and how much surplus it absorbed. */
    public record Payment(
            String trainingName,
            String yearMonth,
            @Nullable BigDecimal amount,
            BigDecimal creditApplied,
            boolean pinned,
            Instant paidAt
    ) {}

    /** Money owed back for a paid session that did not take place — with how (and when) it was resolved. */
    public record Refund(
            String trainingName,
            LocalDate sessionDate,
            BigDecimal amount,
            String type,
            @Nullable String label,
            Instant owedSince,
            @Nullable Instant settledAt,
            // REFUNDED (cash) / CREDITED (toward a month) / MADE_UP (made up elsewhere); null = still pending.
            @Nullable String settlementType,
            // For a CREDITED surplus: the month whose paid bill it actually discounted (YYYY-MM), or null when the
            // surplus is not yet consumed. The source month is the session's own month.
            @Nullable String consumedInMonth
    ) {}
}
