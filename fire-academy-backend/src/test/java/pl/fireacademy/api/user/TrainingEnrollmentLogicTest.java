package pl.fireacademy.api.user;

import org.junit.jupiter.api.Test;

import java.time.YearMonth;

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
}
