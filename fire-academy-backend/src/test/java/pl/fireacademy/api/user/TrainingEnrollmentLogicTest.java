package pl.fireacademy.api.user;

import org.junit.jupiter.api.Test;
import pl.fireacademy.domain.training.TrainingBillingService;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pure logic for counting sessions in a month (the basis of the monthly amount). */
class TrainingEnrollmentLogicTest {

    @Test
    void shouldCountMondaysInKnownMonth() {
        // February 2027: Mondays 1, 8, 15, 22 = 4
        assertEquals(4, TrainingEnrollmentService.sessionsInMonth(1, YearMonth.of(2027, 2)));
    }

    @Test
    void shouldCountFiveOccurrencesWhenMonthHasFive() {
        // March 2027: Mondays 1, 8, 15, 22, 29 = 5
        assertEquals(5, TrainingEnrollmentService.sessionsInMonth(1, YearMonth.of(2027, 3)));
    }

    @Test
    void shouldCountSundaysAsIsoDaySeven() {
        // February 2027: Sundays 7, 14, 21, 28 = 4
        assertEquals(4, TrainingEnrollmentService.sessionsInMonth(7, YearMonth.of(2027, 2)));
    }

    @Test
    void shouldSubtractClosedDatesFromCount() {
        // February 2027: 4 Mondays; closing one (a day off or cancelled session) leaves 3.
        var closed = Set.of(LocalDate.of(2027, 2, 8));
        assertEquals(3, TrainingBillingService.sessionsInMonth(1, YearMonth.of(2027, 2), closed));
    }

    @Test
    void shouldIgnoreClosedDateOnDifferentWeekday() {
        // Closing a Tuesday does not affect the Monday count.
        var closed = Set.of(LocalDate.of(2027, 2, 9));
        assertEquals(4, TrainingBillingService.sessionsInMonth(1, YearMonth.of(2027, 2), closed));
    }
}
