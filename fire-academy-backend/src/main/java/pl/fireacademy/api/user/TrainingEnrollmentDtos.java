package pl.fireacademy.api.user;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.UUID;

public final class TrainingEnrollmentDtos {

    private TrainingEnrollmentDtos() {}

    /** {@code months} empty = indefinite; provided = subscription for N months. */
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
            // monthlyAmount is NET (after subtracting surplus credit); monthlyCreditApplied = surplus used this month.
            @Nullable BigDecimal monthlyAmount,
            BigDecimal monthlyCreditApplied,
            // Whether the organizer has marked the billing month as paid — the account shows "paid" instead of "to pay".
            boolean billingMonthPaid,
            // What the client actually paid for the billing month (current bill + its still-unresolved refunds), so a
            // month that was paid then cut short by a cancellation/deactivation still shows the real amount, not 0.
            @Nullable BigDecimal billingMonthPaidAmount,
            // Money owed for cancelled, already-paid sessions not yet resolved — the client can claim it as a
            // cash refund or put it toward a future month (to settle with the organizer). 0 when nothing pending.
            BigDecimal pendingRefundAmount,
            // Surplus (from refunds credited toward future months) still waiting to reduce upcoming bills — shown
            // to the client year-round, not only inside the next-month estimate window. 0 when none.
            BigDecimal upcomingCreditBalance,
            // Estimated billing for next month, exposed only within NEXT_MONTH_PREVIEW_DAYS before it starts
            // and while the subscription stays active then; null otherwise. nextMonthAmount is likewise NET.
            @Nullable YearMonth nextBillingMonth,
            @Nullable Integer nextMonthSessions,
            @Nullable BigDecimal nextMonthAmount,
            @Nullable BigDecimal nextMonthCreditApplied,
            java.util.List<java.time.LocalDate> cancelledDates,
            java.util.List<java.time.LocalDate> holidayDates,
            // Set when the whole slot has been scheduled to stop from this date — no sessions (and no bill) after it.
            @Nullable LocalDate slotDeactivatedFrom
    ) {}
}
