package pl.fireacademy.api.trainingcalendar;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.fireacademy.api.trainingcalendar.TrainingCalendarDtos.*;
import pl.fireacademy.domain.training.AthleteWeight;
import pl.fireacademy.domain.training.AthleteWeightRepository;
import pl.fireacademy.domain.training.WeightTrendCalculator;
import pl.fireacademy.domain.user.User;
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

    /** Enough history for the chart to show a real trend without turning into a wall of dots. */
    private static final int DEFAULT_HISTORY_DAYS = 120;

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
    public WeightSeriesResponse series(UUID athleteId, boolean includeRapidLossWarning) {
        access.requireAthlete(athleteId);
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(DEFAULT_HISTORY_DAYS - 1L);

        List<AthleteWeight> weights = repository.findRange(athleteId, from, today);
        Map<LocalDate, BigDecimal> byDate = WeightTrendCalculator.index(weights);

        // The trend is emitted per reading, so the frontend never has to reimplement the window.
        List<WeightPoint> points = new ArrayList<>();
        for (AthleteWeight weight : weights) {
            points.add(new WeightPoint(weight.getMeasuredOn(), weight.getWeightKg(),
                    WeightTrendCalculator.trendOn(byDate, weight.getMeasuredOn())));
        }

        BigDecimal weeklyChange = WeightTrendCalculator.weeklyChangePercent(byDate, today);
        return new WeightSeriesResponse(
                points,
                WeightTrendCalculator.trendOn(byDate, today),
                weeklyChange,
                includeRapidLossWarning ? WeightTrendCalculator.isRapidLoss(weeklyChange) : null);
    }

    /**
     * Records or corrects one day's weight. Upsert rather than insert: stepping on the scale twice
     * is a correction, not a second data point, and the unique index would reject the second row
     * anyway.
     */
    @Transactional
    public WeightPoint record(UUID athleteId, RecordWeightRequest request) {
        User athlete = access.requireAthlete(athleteId);
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

        AthleteWeight weight = repository.findByAthleteIdAndMeasuredOn(athleteId, date)
                .map(existing -> {
                    existing.correctTo(request.weightKg());
                    return existing;
                })
                .orElseGet(() -> new AthleteWeight(athlete, date, request.weightKg()));

        AthleteWeight saved = repository.save(weight);
        return new WeightPoint(saved.getMeasuredOn(), saved.getWeightKg(), null);
    }

    @Transactional
    public void delete(UUID athleteId, LocalDate date) {
        access.requireAthlete(athleteId);
        repository.deleteByAthleteIdAndMeasuredOn(athleteId, date);
    }
}
