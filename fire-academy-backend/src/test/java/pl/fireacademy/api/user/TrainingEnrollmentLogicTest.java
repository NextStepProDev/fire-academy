package pl.fireacademy.api.user;

import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Czysta logika liczenia zajęć w miesiącu (podstawa kwoty miesięcznej). */
class TrainingEnrollmentLogicTest {

    @Test
    void shouldCountMondaysInKnownMonth() {
        // Luty 2027: poniedziałki 1, 8, 15, 22 = 4
        assertEquals(4, TrainingEnrollmentService.sessionsInMonth(1, YearMonth.of(2027, 2)));
    }

    @Test
    void shouldCountFiveOccurrencesWhenMonthHasFive() {
        // Marzec 2027: poniedziałki 1, 8, 15, 22, 29 = 5
        assertEquals(5, TrainingEnrollmentService.sessionsInMonth(1, YearMonth.of(2027, 3)));
    }

    @Test
    void shouldCountSundaysAsIsoDaySeven() {
        // Luty 2027: niedziele 7, 14, 21, 28 = 4
        assertEquals(4, TrainingEnrollmentService.sessionsInMonth(7, YearMonth.of(2027, 2)));
    }
}
