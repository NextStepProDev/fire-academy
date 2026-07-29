package pl.fireacademy.api.trainingcalendar;

import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.fireacademy.api.NotFoundException;
import pl.fireacademy.api.trainingcalendar.TrainingCalendarDtos.*;
import pl.fireacademy.domain.training.AthleteGoal;
import pl.fireacademy.domain.training.AthleteGoalRepository;
import pl.fireacademy.domain.training.GoalKind;
import pl.fireacademy.domain.user.User;
import pl.fireacademy.infrastructure.i18n.MessageService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Goals on three horizons, set by the coach and read-only for the client.
 * <p>
 * Two rules the database enforces and this service explains:
 * one active goal per horizon (partial unique index), and achieved goals are immutable — they are
 * the trophy case, and editing one would rewrite somebody's history.
 */
@Service
public class AthleteGoalService {

    private final AthleteGoalRepository repository;
    private final TrainingAccessService access;
    private final AthleteWeightService weights;
    private final MessageService msg;

    public AthleteGoalService(AthleteGoalRepository repository,
                              TrainingAccessService access,
                              AthleteWeightService weights,
                              MessageService msg) {
        this.repository = repository;
        this.access = access;
        this.weights = weights;
        this.msg = msg;
    }

    @Transactional(readOnly = true)
    public GoalsResponse getGoals(UUID athleteId) {
        access.requireAthlete(athleteId);
        return new GoalsResponse(
                repository.findActive(athleteId).stream()
                        // By the enum's own order (SHORT, MEDIUM, LONG) — see the repository for why
                        // this cannot be left to SQL.
                        .sorted(Comparator.comparing(AthleteGoal::getHorizon))
                        .map(AthleteGoalService::toResponse).toList(),
                repository.findAchieved(athleteId).stream().map(AthleteGoalService::toResponse).toList());
    }

    @Transactional
    public GoalResponse create(UUID athleteId, GoalRequest request) {
        User athlete = access.requireAthlete(athleteId);
        GoalKind kind = request.targetWeightKg() == null ? GoalKind.GENERAL : GoalKind.WEIGHT;

        // Pre-check for a readable message, plus a catch below: two coaches saving at once would
        // otherwise surface the raw constraint violation. Weight and general goals no longer compete
        // for a horizon, so the check is per kind.
        boolean slotTaken = repository.findActive(athleteId).stream()
                .anyMatch(g -> g.getHorizon() == request.horizon() && g.getKind() == kind);
        if (slotTaken) {
            throw new IllegalStateException(msg.get("athletegoal.duplicate.active"));
        }

        AthleteGoal goal = kind == GoalKind.WEIGHT
                ? buildWeightGoal(athlete, athleteId, request)
                : new AthleteGoal(athlete, request.horizon(), request.content().trim(), request.targetDate());
        try {
            return toResponse(repository.saveAndFlush(goal));
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException(msg.get("athletegoal.duplicate.active"));
        }
    }

    /**
     * A weight goal needs a starting point, and there is no honest way to invent one: without it the
     * goal cannot tell whether progress means going down or up, and the progress bar has nothing to
     * measure from. So the client has to have weighed in at least once first.
     */
    private AthleteGoal buildWeightGoal(User athlete, UUID athleteId, GoalRequest request) {
        BigDecimal start = weights.currentTrend(athleteId);
        if (start == null) {
            throw new IllegalStateException(msg.get("athletegoal.weight.no.start"));
        }
        BigDecimal target = request.targetWeightKg();
        if (target.compareTo(start) == 0) {
            throw new IllegalArgumentException(msg.get("athletegoal.weight.same"));
        }
        return AthleteGoal.weightGoal(athlete, request.horizon(), request.content().trim(),
                request.targetDate(), target, start);
    }

    /**
     * Closes any weight goal the current trend has reached. Called right after a weigh-in, so the
     * goal shuts the moment the data supports it rather than on a nightly sweep.
     * <p>
     * A goal past its target date still closes: hitting 73 kg a week late is still hitting 73 kg,
     * and refusing to record it would be petty.
     */
    @Transactional
    public void evaluateWeightGoals(UUID athleteId, @Nullable BigDecimal currentTrendKg) {
        if (currentTrendKg == null) {
            return;
        }
        List<AthleteGoal> reached = repository.findActive(athleteId).stream()
                .filter(g -> g.getKind() == GoalKind.WEIGHT)
                .filter(g -> g.isMetBy(currentTrendKg))
                .toList();
        for (AthleteGoal goal : reached) {
            goal.achieveAutomatically(LocalDate.now());
        }
        repository.saveAll(reached);
    }

    /**
     * Undoes an automatic close — and only an automatic one.
     * <p>
     * A typo inside the valid range (65 for 75) drags the trend down and would otherwise shut a goal
     * for good. A decision a person made stays final; a decision the machine made is correctable.
     */
    @Transactional
    public GoalResponse revertAutomaticAchievement(UUID goalId) {
        AthleteGoal goal = repository.findById(goalId)
                .orElseThrow(() -> new NotFoundException(msg.get("athletegoal.not.found")));
        if (!goal.isAchieved()) {
            throw new IllegalStateException(msg.get("athletegoal.not.achieved"));
        }
        if (!goal.isAchievedAutomatically()) {
            throw new IllegalStateException(msg.get("athletegoal.achieved.immutable"));
        }
        goal.revertAutomaticAchievement();
        return toResponse(repository.save(goal));
    }

    @Transactional
    public GoalResponse update(UUID goalId, GoalRequest request) {
        AthleteGoal goal = requireEditable(goalId);
        // The horizon is not editable: a short-term goal that became long-term is a different goal.
        goal.edit(request.content().trim(), request.targetDate());
        return toResponse(repository.save(goal));
    }

    @Transactional
    public void delete(UUID goalId) {
        repository.delete(requireEditable(goalId));
    }

    /**
     * Marks a goal reached, optionally on an earlier date — the coach usually notices afterwards.
     * A future date is refused: nothing has been achieved yet.
     */
    @Transactional
    public GoalResponse achieve(UUID goalId, AchieveGoalRequest request) {
        AthleteGoal goal = requireEditable(goalId);
        LocalDate date = request.achievedDate() == null ? LocalDate.now() : request.achievedDate();
        if (date.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException(msg.get("athletegoal.achieved.future"));
        }
        goal.achieve(date);
        return toResponse(repository.save(goal));
    }

    private AthleteGoal requireEditable(UUID goalId) {
        AthleteGoal goal = repository.findById(goalId)
                .orElseThrow(() -> new NotFoundException(msg.get("athletegoal.not.found")));
        if (goal.isAchieved()) {
            throw new IllegalStateException(msg.get("athletegoal.achieved.immutable"));
        }
        return goal;
    }

    static GoalResponse toResponse(AthleteGoal g) {
        return new GoalResponse(g.getId(), g.getKind(), g.getHorizon(), g.getContent(),
                g.getTargetDate(), g.getAchievedAt(), g.isAchievedAutomatically(),
                g.getTargetWeightKg(), g.getStartWeightKg());
    }
}
