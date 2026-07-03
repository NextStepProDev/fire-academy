package pl.fireacademy.domain.training;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A scheduled slot deactivation must drop the no-longer-happening sessions from the bill, and an existing
 * subscription is prorated from its enrollment date only in the month it was created in — never "from today"
 * (a regular who pays late still owes the whole month).
 */
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

    private TrainingEnrollment enrollmentCreatedOn(LocalDate created) {
        var slot = mondaySlot(null);   // build first — stubbing inside thenReturn() breaks Mockito
        var te = mock(TrainingEnrollment.class);
        when(te.getSlot()).thenReturn(slot);
        when(te.getCreatedAt()).thenReturn(created.atStartOfDay(ZoneId.systemDefault()).toInstant());
        return te;
    }

    @Test
    void proratesTheMonthTheEnrollmentWasCreatedInFromItsJoinDay() {
        // Joined 2027-02-10 → only the Mondays from that day on (15, 22) are billed for February.
        assertEquals(2, billing.sessions(enrollmentCreatedOn(LocalDate.of(2027, 2, 10)), YearMonth.of(2027, 2)));
    }

    @Test
    void billsTheFullMonthWhenTheEnrollmentPredatesIt() {
        // Joined in January → February is a full month (4 Mondays), regardless of when it is viewed/paid.
        assertEquals(4, billing.sessions(enrollmentCreatedOn(LocalDate.of(2027, 1, 20)), YearMonth.of(2027, 2)));
    }
}
