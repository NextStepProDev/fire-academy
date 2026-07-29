package pl.fireacademy.domain.training;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WeightTrendCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2027, 6, 16);

    /** Readings ending today, newest last. */
    private static Map<LocalDate, BigDecimal> series(String... kg) {
        Map<LocalDate, BigDecimal> byDate = new LinkedHashMap<>();
        for (int i = 0; i < kg.length; i++) {
            byDate.put(TODAY.minusDays(kg.length - 1L - i), new BigDecimal(kg[i]));
        }
        return byDate;
    }

    @Test
    void shouldAverageTheLastSevenDays() {
        var byDate = series("80", "80", "80", "80", "80", "80", "74");

        // (80*6 + 74) / 7 = 79.14…
        assertEquals(new BigDecimal("79.14"), WeightTrendCalculator.trendOn(byDate, TODAY));
    }

    @Test
    void shouldSmoothOutASingleWildReading() {
        // The point of the trend: one salty dinner must not look like a 2 kg gain.
        var steady = series("74.0", "74.0", "74.0", "74.0", "74.0", "74.0", "74.0");
        var withSpike = series("74.0", "74.0", "74.0", "74.0", "74.0", "74.0", "76.0");

        BigDecimal calm = WeightTrendCalculator.trendOn(steady, TODAY);
        BigDecimal spiked = WeightTrendCalculator.trendOn(withSpike, TODAY);

        // A 2 kg jump in one reading moves the trend by well under half a kilo
        assertTrue(spiked.subtract(calm).compareTo(new BigDecimal("0.3")) < 0);
    }

    @Test
    void shouldAverageOnlyTheDaysThatExist() {
        // Nobody weighs themselves every single morning; gaps must not zero the average out.
        Map<LocalDate, BigDecimal> byDate = new LinkedHashMap<>();
        byDate.put(TODAY, new BigDecimal("74.0"));
        byDate.put(TODAY.minusDays(3), new BigDecimal("76.0"));

        assertEquals(new BigDecimal("75.00"), WeightTrendCalculator.trendOn(byDate, TODAY));
    }

    @Test
    void shouldReportNoTrendWithoutReadings() {
        assertNull(WeightTrendCalculator.trendOn(Map.of(), TODAY));
        // A reading older than the window does not count towards today
        Map<LocalDate, BigDecimal> old = Map.of(TODAY.minusDays(7), new BigDecimal("74.0"));
        assertNull(WeightTrendCalculator.trendOn(old, TODAY));
    }

    @Test
    void shouldCompareTrendsAWeekApartRatherThanSingleDays() {
        // Two weeks at a steady 80 then a steady 78: −2.5%
        var byDate = series(
                "80", "80", "80", "80", "80", "80", "80",
                "78", "78", "78", "78", "78", "78", "78");

        assertEquals(new BigDecimal("-2.5"), WeightTrendCalculator.weeklyChangePercent(byDate, TODAY));
    }

    @Test
    void shouldReportNoWeeklyChangeUntilBothWindowsHaveData() {
        // One week of readings is not yet a comparison.
        var byDate = series("80", "80", "80", "80", "80", "80", "80");

        assertNull(WeightTrendCalculator.weeklyChangePercent(byDate, TODAY));
    }

    @Test
    void shouldFlagLossFasterThanOnePercentAWeek() {
        // −2.5% a week: worth a word from the coach
        var fast = series(
                "80", "80", "80", "80", "80", "80", "80",
                "78", "78", "78", "78", "78", "78", "78");
        assertTrue(WeightTrendCalculator.isRapidLoss(
                WeightTrendCalculator.weeklyChangePercent(fast, TODAY)));

        // −0.6% a week: a sensible cut, no warning
        var steady = series(
                "80.0", "80.0", "80.0", "80.0", "80.0", "80.0", "80.0",
                "79.5", "79.5", "79.5", "79.5", "79.5", "79.5", "79.5");
        assertFalse(WeightTrendCalculator.isRapidLoss(
                WeightTrendCalculator.weeklyChangePercent(steady, TODAY)));
    }

    @Test
    void shouldNeverFlagGainingWeight() {
        // Deliberately one-directional — putting weight on quickly is not the failure mode here.
        var gaining = series(
                "78", "78", "78", "78", "78", "78", "78",
                "82", "82", "82", "82", "82", "82", "82");

        var change = WeightTrendCalculator.weeklyChangePercent(gaining, TODAY);
        assertTrue(change.signum() > 0);
        assertFalse(WeightTrendCalculator.isRapidLoss(change));
    }

    @Test
    void shouldNotFlagAnythingWithoutEnoughHistory() {
        assertFalse(WeightTrendCalculator.isRapidLoss(null));
    }

    @Test
    void shouldSitExactlyOnTheThresholdWithoutFiring() {
        // The rule is "faster than 1%", so exactly 1.0% is not a warning.
        assertFalse(WeightTrendCalculator.isRapidLoss(new BigDecimal("-1.0")));
        assertTrue(WeightTrendCalculator.isRapidLoss(new BigDecimal("-1.1")));
    }
}
