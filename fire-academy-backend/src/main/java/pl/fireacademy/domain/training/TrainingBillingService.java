package pl.fireacademy.domain.training;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
        return closedDatesInRange(slotId, dayOfWeek, month.atDay(1), month.atEndOfMonth());
    }

    /**
     * Same rule over an arbitrary date range rather than a calendar month.
     * <p>
     * The whole billing API is monthly because a bill is monthly. A calendar page is not: it spans a
     * week or a 42-day grid that straddles two or three months. Asking the monthly methods for each
     * month in turn would cost two queries per month per slot; this costs two for the entire span,
     * which is what keeps the read-only overlay affordable.
     */
    @Transactional(readOnly = true)
    public Set<LocalDate> closedDatesInRange(UUID slotId, int dayOfWeek, LocalDate from, LocalDate to) {
        return closedDatesInRange(List.of(slotId), Map.of(slotId, dayOfWeek), from, to)
                .getOrDefault(slotId, Set.of());
    }

    /**
     * Batched: one pair of queries covers every slot and the whole range at once.
     *
     * @param dayOfWeekBySlot each slot's ISO weekday — a club day off only closes the slots that
     *                        actually fall on it
     */
    @Transactional(readOnly = true)
    public Map<UUID, Set<LocalDate>> closedDatesInRange(Collection<UUID> slotIds,
                                                        Map<UUID, Integer> dayOfWeekBySlot,
                                                        LocalDate from, LocalDate to) {
        Map<UUID, Set<LocalDate>> bySlot = new HashMap<>();
        if (slotIds.isEmpty()) {
            return bySlot;
        }
        for (UUID slotId : slotIds) {
            bySlot.put(slotId, new HashSet<>());
        }

        var holidays = holidayRepository.findByHolidayDateBetweenOrderByHolidayDateAsc(from, to);
        for (var holiday : holidays) {
            int holidayDow = holiday.getHolidayDate().getDayOfWeek().getValue();
            for (UUID slotId : slotIds) {
                if (dayOfWeekBySlot.getOrDefault(slotId, -1) == holidayDow) {
                    bySlot.get(slotId).add(holiday.getHolidayDate());
                }
            }
        }
        for (var cs : cancelledSessionRepository.findForSlotsInRange(slotIds, from, to)) {
            Set<LocalDate> dates = bySlot.get(cs.getSlot().getId());
            if (dates != null) {
                dates.add(cs.getSessionDate());
            }
        }
        return bySlot;
    }

    /**
     * Dates on/after a scheduled deactivation, added to {@code into}. Extracted from the monthly
     * variant so the range API applies exactly the same rule rather than a second copy of it.
     */
    public static void addDeactivationDates(TrainingSlot slot, LocalDate from, LocalDate to, Set<LocalDate> into) {
        LocalDate deactivatedFrom = slot.getDeactivatedFrom();
        if (deactivatedFrom == null) {
            return;
        }
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (date.getDayOfWeek().getValue() == slot.getDayOfWeek() && !date.isBefore(deactivatedFrom)) {
                into.add(date);
            }
        }
    }

    /**
     * The subscription's session dates within a range — PURE, no database access.
     * <p>
     * The caller supplies the closed dates (fetched once for the whole range), so this can be run for
     * every subscription on a calendar page without another query. Applies the same three filters as
     * the bill: the month must be covered by the subscription, the date must not be closed, and it
     * must not precede the subscription's billable-from anchor.
     */
    public static List<LocalDate> sessionDatesInRange(TrainingEnrollment te, LocalDate from, LocalDate to,
                                                      Set<LocalDate> closed) {
        var slot = te.getSlot();
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (date.getDayOfWeek().getValue() != slot.getDayOfWeek()) continue;
            if (closed.contains(date)) continue;
            if (!te.covers(YearMonth.from(date))) continue;
            if (date.getDayOfMonth() < billableFromDay(te, YearMonth.from(date))) continue;
            dates.add(date);
        }
        return dates;
    }

    /**
     * Closed dates of a whole month, batched across slots — the monthly counterpart of
     * {@link #closedDatesInRange(Collection, Map, LocalDate, LocalDate)}, with each slot's scheduled
     * deactivation already folded in.
     * <p>
     * Every method below that bills a single subscription derives this set on its own, at the cost of two
     * queries per call. That is the right shape for one subscriber, and quadratic for a page of them: the
     * slot roster and the monthly payment overview ask for sessions, first session, overdue and amount per
     * subscriber, all off the same handful of slots, and re-run the same two queries for each. Callers that
     * bill many subscriptions at once fetch this once and pass the result to the {@code closed}-taking
     * overloads — the same batching {@code RecurringSessionOverlayService} already does for the calendar.
     */
    @Transactional(readOnly = true)
    public Map<UUID, Set<LocalDate>> closedBySlotForMonth(Collection<TrainingSlot> slots, YearMonth month) {
        Map<UUID, TrainingSlot> distinct = new HashMap<>();
        Map<UUID, Integer> dayOfWeekBySlot = new HashMap<>();
        for (TrainingSlot slot : slots) {
            distinct.put(slot.getId(), slot);
            dayOfWeekBySlot.put(slot.getId(), slot.getDayOfWeek());
        }
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        Map<UUID, Set<LocalDate>> closed = closedDatesInRange(distinct.keySet(), dayOfWeekBySlot, from, to);
        for (TrainingSlot slot : distinct.values()) {
            addDeactivationDates(slot, from, to, closed.computeIfAbsent(slot.getId(), k -> new HashSet<>()));
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
        return sessions(te, month, closedIncludingDeactivation(te.getSlot(), month));
    }

    /** As above, on a closed-date set the caller already holds. */
    public int sessions(TrainingEnrollment te, YearMonth month, Set<LocalDate> closed) {
        return sessionsInMonth(te.getSlot().getDayOfWeek(), month, closed, billableFromDay(te, month));
    }

    @Nullable
    @Transactional(readOnly = true)
    public BigDecimal amount(TrainingEnrollment te, YearMonth month) {
        return amount(te, month, closedIncludingDeactivation(te.getSlot(), month));
    }

    /** As above, on a closed-date set the caller already holds. */
    @Nullable
    public BigDecimal amount(TrainingEnrollment te, YearMonth month, Set<LocalDate> closed) {
        var price = te.getSlot().getPrice();
        return price != null ? price.multiply(BigDecimal.valueOf(sessions(te, month, closed))) : null;
    }

    /**
     * Whether a specific session date is part of what the subscription is billed for: it must not fall before the
     * subscription's billable-from anchor in its own month (a mid-month joiner, or an organizer's {@code billableFrom}
     * override, is not charged for — and so is owed no refund for — sessions before their start day). Callers already
     * ensure the date's month is covered by the subscription; this only guards the intra-month proration.
     */
    public boolean isBillableSession(TrainingEnrollment te, LocalDate date) {
        return date.getDayOfMonth() >= billableFromDay(te, YearMonth.from(date));
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

    /** As above, on a closed-date set the caller already holds. */
    @Nullable
    public LocalDate partialStartDate(TrainingEnrollment te, YearMonth month, Set<LocalDate> closed) {
        if (!te.getStartMonth().equals(month)) return null;
        if (billableFromDay(te, month) <= 1) return null;
        return firstSessionDate(te, month, closed);
    }

    /** How many days after the month's first session a payment stays "on time" before it counts as overdue. */
    private static final int OVERDUE_GRACE_DAYS = 1;

    /** The first billable session date of the subscription in the month, or null if there is none (all closed). */
    @Nullable
    @Transactional(readOnly = true)
    public LocalDate firstSessionDate(TrainingEnrollment te, YearMonth month) {
        return firstSessionDate(te, month, closedIncludingDeactivation(te.getSlot(), month));
    }

    /** As above, on a closed-date set the caller already holds. */
    @Nullable
    public LocalDate firstSessionDate(TrainingEnrollment te, YearMonth month, Set<LocalDate> closed) {
        var slot = te.getSlot();
        for (int day = billableFromDay(te, month); day <= month.lengthOfMonth(); day++) {
            LocalDate date = LocalDate.of(month.getYear(), month.getMonthValue(), day);
            if (date.getDayOfWeek().getValue() == slot.getDayOfWeek() && !closed.contains(date)) {
                return date;
            }
        }
        return null;
    }

    /**
     * The subscription's billable session dates in the month on or after {@code from} (inclusive), ascending. Same
     * rule as the monthly bill: the slot's weekday occurrences minus the closed dates (days off, single cancellations,
     * scheduled deactivation), and never before the subscription's billable-from anchor day. Used to work out which
     * paid sessions a removed subscriber will not attend and is therefore owed back.
     */
    @Transactional(readOnly = true)
    public List<LocalDate> billableSessionDates(TrainingEnrollment te, YearMonth month, LocalDate from) {
        var slot = te.getSlot();
        Set<LocalDate> closed = closedIncludingDeactivation(slot, month);
        List<LocalDate> dates = new ArrayList<>();
        for (int day = billableFromDay(te, month); day <= month.lengthOfMonth(); day++) {
            LocalDate date = LocalDate.of(month.getYear(), month.getMonthValue(), day);
            if (date.getDayOfWeek().getValue() == slot.getDayOfWeek() && !closed.contains(date)
                    && !date.isBefore(from)) {
                dates.add(date);
            }
        }
        return dates;
    }

    /**
     * Whether the month's payment is past due: its first session (plus a day of grace) has already passed. A caller
     * combines this with the paid flag — an overdue AND unpaid month is a reserved spot that was never paid for.
     */
    @Transactional(readOnly = true)
    public boolean isPaymentOverdue(TrainingEnrollment te, YearMonth month) {
        return isOverdue(firstSessionDate(te, month));
    }

    /** As above, on a closed-date set the caller already holds. */
    public boolean isPaymentOverdue(TrainingEnrollment te, YearMonth month, Set<LocalDate> closed) {
        return isOverdue(firstSessionDate(te, month, closed));
    }

    private static boolean isOverdue(@Nullable LocalDate firstSession) {
        return firstSession != null && LocalDate.now().isAfter(firstSession.plusDays(OVERDUE_GRACE_DAYS));
    }

    /** Closed dates of the month plus the slot's weekday dates on/after a scheduled deactivation. */
    private Set<LocalDate> closedIncludingDeactivation(TrainingSlot slot, YearMonth month) {
        Set<LocalDate> closed = new HashSet<>(closedDates(slot.getId(), slot.getDayOfWeek(), month));
        // A scheduled deactivation stops the slot from a date on — those sessions no longer take place,
        // so they must drop out of the bill too (not just days off / single cancellations).
        addDeactivationDates(slot, month.atDay(1), month.atEndOfMonth(), closed);
        return closed;
    }
}
