package pl.fireacademy.domain.training;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A scheduled slot deactivation must drop the no-longer-happening sessions from the bill. */
class TrainingBillingServiceTest {

    private final TrainingHolidayRepository holidays = mock(TrainingHolidayRepository.class);
    private final TrainingCancelledSessionRepository cancelled = mock(TrainingCancelledSessionRepository.class);
    private final TrainingBillingService billing = new TrainingBillingService(holidays, cancelled);

    private TrainingSlot mondaySlot(LocalDate deactivatedFrom) {
        when(holidays.findByHolidayDateBetweenOrderByHolidayDateAsc(any(), any())).thenReturn(List.of());
        when(cancelled.findForSlotsInRange(any(), any(), any())).thenReturn(List.of());
        var slot = mock(TrainingSlot.class);
        when(slot.getId()).thenReturn(UUID.randomUUID());
        when(slot.getDayOfWeek()).thenReturn(1);                       // Monday
        when(slot.getDeactivatedFrom()).thenReturn(deactivatedFrom);
        return slot;
    }

    @Test
    void countsAllMondaysWhenNotDeactivated() {
        // February 2027: Mondays 1, 8, 15, 22 = 4
        assertEquals(4, billing.sessions(mondaySlot(null), YearMonth.of(2027, 2)));
    }

    @Test
    void excludesSessionsFromTheDeactivationDateOn() {
        // Deactivated from the 15th → only Mondays 1 and 8 remain billable.
        assertEquals(2, billing.sessions(mondaySlot(LocalDate.of(2027, 2, 15)), YearMonth.of(2027, 2)));
    }

    @Test
    void zeroWhenDeactivatedBeforeTheWholeMonth() {
        // Deactivated from before the month starts → nothing is billed.
        assertEquals(0, billing.sessions(mondaySlot(LocalDate.of(2027, 1, 1)), YearMonth.of(2027, 2)));
    }
}
