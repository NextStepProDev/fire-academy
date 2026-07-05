package pl.fireacademy.domain.training;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Single source of truth for the monthly billing of a training slot. The number of paid sessions is the
 * count of the slot's weekday occurrences in the month minus the dates on which the slot does not take place:
 * whole-club days off ({@link TrainingHoliday}) and single cancelled sessions ({@link TrainingCancelledSession}).
 */
@Service
public class TrainingBillingService {

    private final TrainingHolidayRepository holidayRepository;
    private final TrainingCancelledSessionRepository cancelledSessionRepository;

    public TrainingBillingService(TrainingHolidayRepository holidayRepository,
                                  TrainingCancelledSessionRepository cancelledSessionRepository) {
        this.holidayRepository = holidayRepository;
        this.cancelledSessionRepository = cancelledSessionRepository;
    }

    /**
     * Number of sessions on a given weekday (ISO 1–7) to be paid for in the month, after subtracting the
     * closed dates: for the current month counted from TODAY to the end (remaining ones), for future months
     * — all of them. This "from today" flavor is for previews of a would-be NEW enrollment (public catalog,
     * enroll modal); an existing subscription bills via {@link #sessions(TrainingEnrollment, YearMonth)},
     * which prorates from the enrollment date instead, so paying late never shrinks the bill.
     */
    public static int sessionsInMonth(int isoDayOfWeek, YearMonth month, Set<LocalDate> closedDates) {
        int fromDay = month.equals(YearMonth.now()) ? LocalDate.now().getDayOfMonth() : 1;
        return sessionsInMonth(isoDayOfWeek, month, closedDates, fromDay);
    }

    /** Same count, but from an explicit day of the month (1 = the whole month). */
    public static int sessionsInMonth(int isoDayOfWeek, YearMonth month, Set<LocalDate> closedDates, int fromDay) {
        int count = 0;
        for (int day = fromDay; day <= month.lengthOfMonth(); day++) {
            LocalDate date = LocalDate.of(month.getYear(), month.getMonthValue(), day);
            if (date.getDayOfWeek().getValue() == isoDayOfWeek && !closedDates.contains(date)) {
                count++;
            }
        }
        return count;
    }

    /** Dates in the month on which the given slot does NOT take place (days off on that weekday + cancellations). */
    @Transactional(readOnly = true)
    public Set<LocalDate> closedDates(UUID slotId, int dayOfWeek, YearMonth month) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        Set<LocalDate> closed = new HashSet<>();
        for (var holiday : holidayRepository.findByHolidayDateBetweenOrderByHolidayDateAsc(from, to)) {
            if (holiday.getHolidayDate().getDayOfWeek().getValue() == dayOfWeek) {
                closed.add(holiday.getHolidayDate());
            }
        }
        for (var cs : cancelledSessionRepository.findForSlotsInRange(List.of(slotId), from, to)) {
            closed.add(cs.getSessionDate());
        }
        return closed;
    }

    /** Preview flavor (would-be new enrollment): current month counted from today. */
    @Transactional(readOnly = true)
    public int sessions(TrainingSlot slot, YearMonth month) {
        return sessionsInMonth(slot.getDayOfWeek(), month, closedIncludingDeactivation(slot, month));
    }

    /**
     * Billable sessions of an existing subscription: the full month, prorated from the enrollment date only
     * in the month the subscription was created in (a mid-month joiner pays from their join day; a regular
     * who pays late still owes the whole month — the bill must not shrink as the days pass).
     */
    @Transactional(readOnly = true)
    public int sessions(TrainingEnrollment te, YearMonth month) {
        var slot = te.getSlot();
        return sessionsInMonth(slot.getDayOfWeek(), month, closedIncludingDeactivation(slot, month),
                billableFromDay(te, month));
    }

    @Nullable
    @Transactional(readOnly = true)
    public BigDecimal amount(TrainingEnrollment te, YearMonth month) {
        var price = te.getSlot().getPrice();
        return price != null ? price.multiply(BigDecimal.valueOf(sessions(te, month))) : null;
    }

    /**
     * First billable day of the month: the anchor day when the anchor falls in that month, else 1. The anchor is
     * the organizer's explicit override ({@code billableFrom}) if set, otherwise the signup date ({@code createdAt}).
     */
    private static int billableFromDay(TrainingEnrollment te, YearMonth month) {
        LocalDate anchor = te.getBillableFrom() != null
                ? te.getBillableFrom()
                : LocalDate.ofInstant(te.getCreatedAt(), java.time.ZoneId.systemDefault());
        return YearMonth.from(anchor).equals(month) ? anchor.getDayOfMonth() : 1;
    }

    /**
     * The date a partial first month effectively starts — its first attendable session — when it is billed from a
     * later day (organizer's {@code billableFrom} override, or a mid-month signup). Returns null for a whole month
     * billed from day 1 (an ongoing month, or a first month that starts at the beginning), so callers only ever
     * surface a "valid from" hint when the start is genuinely partial. Stays meaningful after payment: it lets the
     * organizer see from which day a paid month is actually valid, not just that it is paid.
     */
    @Nullable
    @Transactional(readOnly = true)
    public LocalDate partialStartDate(TrainingEnrollment te, YearMonth month) {
        if (!te.getStartMonth().equals(month)) return null;
        if (billableFromDay(te, month) <= 1) return null;
        return firstSessionDate(te, month);
    }

    /** How many days after the month's first session a payment stays "on time" before it counts as overdue. */
    private static final int OVERDUE_GRACE_DAYS = 1;

    /** The first billable session date of the subscription in the month, or null if there is none (all closed). */
    @Nullable
    @Transactional(readOnly = true)
    public LocalDate firstSessionDate(TrainingEnrollment te, YearMonth month) {
        var slot = te.getSlot();
        Set<LocalDate> closed = closedIncludingDeactivation(slot, month);
        for (int day = billableFromDay(te, month); day <= month.lengthOfMonth(); day++) {
            LocalDate date = LocalDate.of(month.getYear(), month.getMonthValue(), day);
            if (date.getDayOfWeek().getValue() == slot.getDayOfWeek() && !closed.contains(date)) {
                return date;
            }
        }
        return null;
    }

    /**
     * Whether the month's payment is past due: its first session (plus a day of grace) has already passed. A caller
     * combines this with the paid flag — an overdue AND unpaid month is a reserved spot that was never paid for.
     */
    @Transactional(readOnly = true)
    public boolean isPaymentOverdue(TrainingEnrollment te, YearMonth month) {
        LocalDate first = firstSessionDate(te, month);
        return first != null && LocalDate.now().isAfter(first.plusDays(OVERDUE_GRACE_DAYS));
    }

    /** Closed dates of the month plus the slot's weekday dates on/after a scheduled deactivation. */
    private Set<LocalDate> closedIncludingDeactivation(TrainingSlot slot, YearMonth month) {
        Set<LocalDate> closed = new HashSet<>(closedDates(slot.getId(), slot.getDayOfWeek(), month));
        // A scheduled deactivation stops the slot from a date on — those sessions no longer take place,
        // so they must drop out of the bill too (not just days off / single cancellations).
        LocalDate deactivatedFrom = slot.getDeactivatedFrom();
        if (deactivatedFrom != null) {
            for (int day = 1; day <= month.lengthOfMonth(); day++) {
                LocalDate date = LocalDate.of(month.getYear(), month.getMonthValue(), day);
                if (date.getDayOfWeek().getValue() == slot.getDayOfWeek() && !date.isBefore(deactivatedFrom)) {
                    closed.add(date);
                }
            }
        }
        return closed;
    }
}
