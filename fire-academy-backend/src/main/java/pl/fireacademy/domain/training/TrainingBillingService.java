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
     * — all of them.
     */
    public static int sessionsInMonth(int isoDayOfWeek, YearMonth month, Set<LocalDate> closedDates) {
        int fromDay = month.equals(YearMonth.now()) ? LocalDate.now().getDayOfMonth() : 1;
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

    @Transactional(readOnly = true)
    public int sessions(TrainingSlot slot, YearMonth month) {
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
        return sessionsInMonth(slot.getDayOfWeek(), month, closed);
    }

    @Nullable
    @Transactional(readOnly = true)
    public BigDecimal amount(TrainingSlot slot, YearMonth month) {
        if (slot.getPrice() == null) {
            return null;
        }
        return slot.getPrice().multiply(BigDecimal.valueOf(sessions(slot, month)));
    }
}
