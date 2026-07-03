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
import java.time.LocalDate;
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
            @Nullable LocalDate deactivatedFrom,
            // For a deactivated slot: whether it can be reactivated (false once a cash refund was paid out /
            // credited surplus spent for any of its skipped sessions). Always true when not deactivated.
            boolean reactivatable,
            Instant createdAt
    ) {}

    public record DeactivateSlotRequest(
            @NotNull LocalDate from
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

    /** Bulk creation of multiple slots of one event type with the same instructor (different days/times). */
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

    /** Roster entry: a single enrolled participant for a given month. */
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
            boolean paid,
            // Surplus (CREDITED refunds) still available to discount this subscriber's upcoming bills.
            BigDecimal creditBalance
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

    // ── Monthly payments grouped by person ───────────────────────────────────

    /** One subscriber's whole-month bill: total across all their trainings + a per-training breakdown. */
    public record UserMonthlyPayment(
            UUID userId,
            String firstName,
            String lastName,
            String email,
            String phone,
            List<MonthlyTrainingLine> trainings,
            BigDecimal totalAmount,
            boolean allPaid,
            // When the whole month was marked paid (latest of the paid trainings); null if nothing paid yet.
            @Nullable Instant paidAt,
            // Surplus (credited refunds) the person still has waiting for upcoming months.
            BigDecimal creditBalance
    ) {}

    public record MonthlyTrainingLine(
            String trainingName,
            int dayOfWeek,
            LocalTime startTime,
            @Nullable LocalTime endTime,
            BigDecimal amount,
            boolean paid
    ) {}

    public record PayUserMonthRequest(
            @NotNull YearMonth month,
            boolean paid
    ) {}

    /** Deleted (archived) slot with contact data of former participants. */
    public record DeletedSlotResponse(
            UUID id,
            String eventTypeName,
            @Nullable String instructorName,
            int dayOfWeek,
            LocalTime startTime,
            @Nullable LocalTime endTime,
            Instant deletedAt,
            List<ArchivedParticipant> participants
    ) {}

    public record ArchivedParticipant(
            String firstName,
            String lastName,
            String email,
            String phone,
            YearMonth startMonth,
            @Nullable YearMonth endMonth
    ) {}

    public record CancelledSessionResponse(
            UUID id,
            java.time.LocalDate sessionDate
    ) {}

    /**
     * One cancelled session across the whole club, with the people it affected. Drives the
     * admin "Cancelled sessions" overview (upcoming + archive). {@code future} = the session date is
     * today or later, i.e. it can still be restored.
     */
    public record CancelledSessionOverviewItem(
            UUID id,
            UUID slotId,
            LocalDate sessionDate,
            String eventTypeName,
            @Nullable String instructorName,
            int dayOfWeek,
            LocalTime startTime,
            @Nullable LocalTime endTime,
            @Nullable BigDecimal price,
            boolean future,
            // false when a cash refund was already paid out (or a credited surplus already spent) → restore is blocked.
            boolean restorable,
            List<AffectedParticipant> participants
    ) {}

    /** A participant affected by a cancelled session. {@code owedRefund} = had paid that month, so is owed money back. */
    public record AffectedParticipant(
            String firstName,
            String lastName,
            String email,
            @Nullable String phone,
            boolean paid,
            boolean owedRefund
    ) {}

    public record CancelSessionRequest(
            @NotNull java.time.LocalDate sessionDate
    ) {}

    /** Cancel all of one instructor's sessions on a given date. */
    public record CancelInstructorDayRequest(
            @NotNull UUID instructorId,
            @NotNull java.time.LocalDate date
    ) {}

    public record CancelInstructorDayResponse(
            int cancelled
    ) {}

    // ── Days off (whole-club closures) ───────────────────────────────────────

    public record TrainingHolidayResponse(
            UUID id,
            LocalDate date,
            @Nullable String label,
            // How many paid participants are affected (got the day-off email) — drives the "phone them" warning on removal.
            int notifiedCount,
            // false when a cash refund was already paid out (or credited surplus spent) → removal is blocked.
            boolean restorable
    ) {}

    public record CreateHolidayRequest(
            @NotNull LocalDate date,
            @Nullable @jakarta.validation.constraints.Size(max = 120) String label
    ) {}

    // ── Refunds ──────────────────────────────────────────────────────────────

    /** One refund owed (or already settled) for a paid session that was cancelled. */
    public record RefundEntry(
            UUID id,
            UUID userId,
            String firstName,
            String lastName,
            String email,
            String phone,
            String trainingName,
            LocalDate sessionDate,
            YearMonth yearMonth,
            BigDecimal amount,
            String type,
            @Nullable String label,
            @Nullable Instant settledAt,
            @Nullable String settlementType
    ) {}

    /** An ended subscription still sitting on unconsumed CREDITED surplus — nobody will ever apply it automatically. */
    public record UnconsumedCreditEntry(
            UUID enrollmentId,
            UUID userId,
            String firstName,
            String lastName,
            String email,
            String phone,
            String trainingName,
            YearMonth endMonth,
            BigDecimal balance
    ) {}

    public record SettleRefundRequest(
            @NotNull String settlementType
    ) {}
}
