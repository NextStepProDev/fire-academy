package pl.fireacademy.api.trainingcalendar;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.fireacademy.api.trainingcalendar.TrainingCalendarDtos.*;
import pl.fireacademy.domain.training.AthleteWeight;
import pl.fireacademy.domain.training.AthleteWeightRepository;
import pl.fireacademy.domain.training.WeightTrendCalculator;
import pl.fireacademy.infrastructure.i18n.MessageService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Morning weigh-ins and what can honestly be read from them.
 * <p>
 * Only the client records their own weight — the coach reads it. That is not a permission subtlety
 * but the shape of the thing: nobody else is standing on the scale.
 */
@Service
public class AthleteWeightService {

    /**
     * How far back a series may go.
     * <p>
     * The window is capped server-side rather than left to the caller: whatever the page asks for,
     * the response stays one row per day of a bounded range. QUARTER is the default and the shape
     * everything else was built around; the longer ranges exist because a year-on-year comparison is
     * half the reason anyone logs their weight at all, and the readings were unreachable without it.
     */
    public enum Range {
        QUARTER(120),
        YEAR(365),
        /** Everything ever recorded. Bounded in practice by one row per day since the account began. */
        ALL(0);

        private final int days;

        Range(int days) {
            this.days = days;
        }

        LocalDate from(LocalDate today) {
            return this == ALL ? LocalDate.of(1900, 1, 1) : today.minusDays(days - 1L);
        }
    }

    private final AthleteWeightRepository repository;
    private final TrainingAccessService access;
    private final MessageService msg;

    public AthleteWeightService(AthleteWeightRepository repository,
                                TrainingAccessService access,
                                MessageService msg) {
        this.repository = repository;
        this.access = access;
        this.msg = msg;
    }

    /**
     * @param includeRapidLossWarning coach-only. The client sees their own trend and can read it;
     *                                a page telling somebody "you are cutting too fast" is a verdict,
     *                                while a coach noticing it is a conversation.
     */
    @Transactional(readOnly = true)
    public WeightSeriesResponse series(UUID athleteId, boolean includeRapidLossWarning, Range range) {
        access.requireAthlete(athleteId);
        LocalDate today = LocalDate.now();
        LocalDate from = range.from(today);

        // Reads reach back one trend window BEFORE the range so the first points on the chart carry
        // a full seven days behind them. Without it the left edge of every series would dip or jump
        // purely because the earlier readings were not fetched.
        List<AthleteWeight> weights = repository.findRange(
                athleteId, from.minusDays(WeightTrendCalculator.TREND_WINDOW_DAYS - 1L), today);
        Map<LocalDate, BigDecimal> byDate = WeightTrendCalculator.index(weights);

        // The trend is emitted per reading, so the frontend never has to reimplement the window.
        List<WeightPoint> points = new ArrayList<>();
        for (AthleteWeight weight : weights) {
            if (weight.getMeasuredOn().isBefore(from)) {
                continue;
            }
            points.add(new WeightPoint(weight.getMeasuredOn(), weight.getWeightKg(),
                    WeightTrendCalculator.trendOn(byDate, weight.getMeasuredOn())));
        }

        BigDecimal weeklyChange = WeightTrendCalculator.weeklyChangePercent(byDate, today);
        return new WeightSeriesResponse(
                points,
                WeightTrendCalculator.trendOn(byDate, today),
                weeklyChange,
                includeRapidLossWarning ? WeightTrendCalculator.isRapidLoss(weeklyChange) : null,
                WeightTrendCalculator.readingsInWindow(byDate, today),
                WeightTrendCalculator.MIN_READINGS_TO_CLOSE_GOAL);
    }

    /**
     * Today's trend together with the number of mornings behind it.
     *
     * @param trendKg  null when the window holds no readings at all
     * @param readings how many days of the window were actually weighed — a trend off one reading and
     *                 a trend off seven are the same type and very different things
     */
    public record TrendSnapshot(@Nullable BigDecimal trendKg, int readings) {}

    @Transactional(readOnly = true)
    public TrendSnapshot currentTrendSnapshot(UUID athleteId) {
        LocalDate today = LocalDate.now();
        var weights = repository.findRange(athleteId, today.minusDays(WeightTrendCalculator.TREND_WINDOW_DAYS), today);
        Map<LocalDate, BigDecimal> byDate = WeightTrendCalculator.index(weights);
        return new TrendSnapshot(
                WeightTrendCalculator.trendOn(byDate, today),
                WeightTrendCalculator.readingsInWindow(byDate, today));
    }

    /** Today's 7-day trend, or null when there are no readings yet. Used to anchor a weight goal. */
    @Transactional(readOnly = true)
    @Nullable
    public BigDecimal currentTrend(UUID athleteId) {
        return currentTrendSnapshot(athleteId).trendKg();
    }

    /**
     * Records or corrects one day's weight. Upsert rather than insert: stepping on the scale twice
     * is a correction, not a second data point, and the unique index would reject the second row
     * anyway.
     */
    @Transactional
    public WeightPoint record(UUID athleteId, RecordWeightRequest request) {
        access.requireAthlete(athleteId);
        LocalDate date = request.date() == null ? LocalDate.now() : request.date();
        if (date.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException(msg.get("athleteweight.future"));
        }
        // Belt and braces with the DB CHECK: a slipped decimal point (7.42 instead of 74.2) is the
        // realistic typo here, and it would otherwise poison the trend for a fortnight.
        if (request.weightKg().compareTo(AthleteWeight.MIN_KG) < 0
                || request.weightKg().compareTo(AthleteWeight.MAX_KG) > 0) {
            throw new IllegalArgumentException(msg.get("athleteweight.out.of.range"));
        }

        // Left to the database rather than read-then-written here: a double-tapped save sends two
        // identical requests, both find no row for today, both insert, and the unique index rejects
        // the second — a server error for someone who pressed the button twice. See the repository.
        repository.upsertReading(athleteId, date, request.weightKg());
        return new WeightPoint(date, request.weightKg(), null);
    }

    @Transactional
    public void delete(UUID athleteId, LocalDate date) {
        access.requireAthlete(athleteId);
        repository.deleteByAthleteIdAndMeasuredOn(athleteId, date);
    }
}
