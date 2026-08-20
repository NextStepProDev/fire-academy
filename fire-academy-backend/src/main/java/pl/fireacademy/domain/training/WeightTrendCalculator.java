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
     * How many readings the window needs before the trend may close a weight goal.
     * <p>
     * The average is happy to run on one reading, and for showing a number that is fine — it is the
     * best guess available and it is labelled as a trend, not as a measurement. Closing a goal is a
     * different act: a single morning after a hard session or a sauna can sit two kilos below the
     * week's truth, and a goal shut on that reads as an achievement nobody earned. Three mornings is
     * the smallest number that cannot be one bad one.
     */
    public static final int MIN_READINGS_TO_CLOSE_GOAL = 3;

    /**
     * How far back the lowest-trend statistic looks. Deliberately a constant rather than the range
     * the chart happens to be showing.
     * <p>
     * The figure is labelled "last 3 months" on screen, and that label has to stay true when
     * somebody switches the chart to a year — a number that silently means something different
     * depending on a toggle above it is worse than no number. Same reasoning as the fixed backfill
     * limit: a window somebody signs their name under is policy, not whatever is currently in view.
     */
    public static final int LOWEST_TREND_WINDOW_DAYS = 90;

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
     * How many readings the trailing window ending on {@code day} actually holds — the trend's own
     * measure of how much it is worth. One reading and seven produce a number the same way; only
     * this tells them apart.
     */
    public static int readingsInWindow(Map<LocalDate, BigDecimal> byDate, LocalDate day) {
        int count = 0;
        for (int i = 0; i < TREND_WINDOW_DAYS; i++) {
            if (byDate.get(day.minusDays(i)) != null) {
                count++;
            }
        }
        return count;
    }

    /**
     * The trend on {@code day}, but only when the window behind it holds enough mornings to be
     * believed — otherwise null.
     * <p>
     * This is the same bar a weight goal has to clear before it closes itself (see
     * {@link #MIN_READINGS_TO_CLOSE_GOAL}), stated once instead of being spelled out at each call
     * site. The goal and the lowest-trend figure sit on the same screen, so a record standing below
     * a goal that never closed would be two numbers contradicting each other in front of the same
     * person.
     */
    @Nullable
    public static BigDecimal confirmedTrendOn(Map<LocalDate, BigDecimal> byDate, LocalDate day) {
        return readingsInWindow(byDate, day) < MIN_READINGS_TO_CLOSE_GOAL ? null : trendOn(byDate, day);
    }

    /** A trend that cleared {@link #MIN_READINGS_TO_CLOSE_GOAL}, together with the day it fell on. */
    public record ConfirmedTrend(BigDecimal trendKg, LocalDate day) {}

    /**
     * The lowest confirmed trend within the last {@code windowDays} days, or null when no day in
     * that window ever had enough mornings behind it.
     * <p>
     * Not the lowest raw reading, for two reasons. It has to be the number that could close a weight
     * goal, or the screen contradicts itself. And a minimum over N samples falls with N: somebody
     * weighing in every morning would "beat" somebody weighing in twice a week at identical real
     * weight, which would make this a measure of diary-keeping rather than of progress.
     * <p>
     * Walks only the days that hold a reading, never every date in the range: an average computed on
     * a day nobody stepped on the scale would plant a record on a day when nothing happened. The
     * chart draws its trend points the same way, one per reading.
     *
     * @return on a tie, the LATEST day — coming back down to your own minimum is news; having once
     *         been there is not
     */
    @Nullable
    public static ConfirmedTrend lowestConfirmedTrend(Map<LocalDate, BigDecimal> byDate,
                                                      LocalDate today,
                                                      int windowDays) {
        LocalDate from = today.minusDays(windowDays - 1L);
        BigDecimal bestKg = null;
        LocalDate bestDay = null;
        for (LocalDate day : byDate.keySet()) {
            if (day.isBefore(from) || day.isAfter(today)) {
                continue;
            }
            BigDecimal trend = confirmedTrendOn(byDate, day);
            if (trend == null) {
                continue;
            }
            // Compared explicitly rather than relying on iteration order: index() hands over a
            // TreeMap, but this takes any Map and the tie rule must not depend on that.
            int cmp = bestKg == null ? -1 : trend.compareTo(bestKg);
            if (cmp < 0 || (cmp == 0 && day.isAfter(bestDay))) {
                bestKg = trend;
                bestDay = day;
            }
        }
        return bestKg == null ? null : new ConfirmedTrend(bestKg, bestDay);
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
