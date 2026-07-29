package pl.fireacademy.domain.training;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Body-weight trend arithmetic. Pure and clock-free, so every window and threshold is testable.
 *
 * <h2>Why a trailing average and not the raw numbers</h2>
 * Day-to-day body weight is mostly water, glycogen, salt and what time somebody stepped on the
 * scale. A single reading says almost nothing; a 7-day average says almost everything. The UI shows
 * the raw points faintly and the trend line prominently for exactly this reason — otherwise people
 * react to noise, which in a weight-class sport means chasing a number that was never real.
 */
public final class WeightTrendCalculator {

    /** A full week smooths out both the weekly eating rhythm and the day-of-week weighing habit. */
    public static final int TREND_WINDOW_DAYS = 7;

    /**
     * Weekly loss beyond this is worth a word from the coach. Around 0.5–1% of body weight per week
     * is the usual guidance for athletes; past 1% the loss increasingly comes from somewhere other
     * than fat, and in a club with weight classes that is exactly the failure mode to catch early.
     * <p>
     * Deliberately one-directional: gaining weight quickly is not flagged.
     */
    public static final BigDecimal RAPID_LOSS_PERCENT_PER_WEEK = new BigDecimal("1.0");

    private WeightTrendCalculator() {}

    /**
     * Trailing {@link #TREND_WINDOW_DAYS}-day average ending on {@code day}, or null when the window
     * holds no readings. Gaps are fine — this averages what is there rather than demanding a reading
     * every day, because nobody weighs themselves every single morning.
     */
    @Nullable
    public static BigDecimal trendOn(Map<LocalDate, BigDecimal> byDate, LocalDate day) {
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (int i = 0; i < TREND_WINDOW_DAYS; i++) {
            BigDecimal value = byDate.get(day.minusDays(i));
            if (value != null) {
                sum = sum.add(value);
                count++;
            }
        }
        return count == 0 ? null : sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    /**
     * Change of the trend over the last week, in percent of body weight.
     * <p>
     * Compares two trend values a week apart rather than two readings — comparing single days would
     * just be comparing two pieces of noise. Null until both windows contain something.
     *
     * @return negative when losing weight
     */
    @Nullable
    public static BigDecimal weeklyChangePercent(Map<LocalDate, BigDecimal> byDate, LocalDate today) {
        BigDecimal now = trendOn(byDate, today);
        BigDecimal weekAgo = trendOn(byDate, today.minusDays(TREND_WINDOW_DAYS));
        if (now == null || weekAgo == null || weekAgo.signum() == 0) {
            return null;
        }
        return now.subtract(weekAgo)
                .multiply(BigDecimal.valueOf(100))
                .divide(weekAgo, 1, RoundingMode.HALF_UP);
    }

    /**
     * Whether the client is losing weight faster than is usually sensible.
     * <p>
     * For the coach only. The client sees their own trend and can draw their own conclusions; a page
     * telling somebody "you are cutting too fast" is a verdict, while a coach noticing it is a
     * conversation — the same reasoning as the overtraining signal.
     */
    public static boolean isRapidLoss(@Nullable BigDecimal weeklyChangePercent) {
        return weeklyChangePercent != null
                && weeklyChangePercent.negate().compareTo(RAPID_LOSS_PERCENT_PER_WEEK) > 0;
    }

    /** Readings keyed by day, newest last — the shape the functions above expect. */
    public static Map<LocalDate, BigDecimal> index(List<AthleteWeight> weights) {
        Map<LocalDate, BigDecimal> byDate = new TreeMap<>();
        for (AthleteWeight weight : weights) {
            byDate.put(weight.getMeasuredOn(), weight.getWeightKg());
        }
        return byDate;
    }
}
