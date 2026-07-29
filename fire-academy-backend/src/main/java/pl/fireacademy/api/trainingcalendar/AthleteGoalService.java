package pl.fireacademy.api.trainingcalendar;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.fireacademy.api.NotFoundException;
import pl.fireacademy.api.trainingcalendar.TrainingCalendarDtos.*;
import pl.fireacademy.domain.training.AthleteGoal;
import pl.fireacademy.domain.training.AthleteGoalRepository;
import pl.fireacademy.domain.user.User;
import pl.fireacademy.infrastructure.i18n.MessageService;

import java.time.LocalDate;
import java.util.Comparator;
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
    private final MessageService msg;

    public AthleteGoalService(AthleteGoalRepository repository,
                              TrainingAccessService access,
                              MessageService msg) {
        this.repository = repository;
        this.access = access;
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
        // Pre-check for a readable message, plus a catch below: two coaches saving at once would
        // otherwise surface the raw constraint violation.
        if (repository.findActive(athleteId).stream().anyMatch(g -> g.getHorizon() == request.horizon())) {
            throw new IllegalStateException(msg.get("athletegoal.duplicate.active"));
        }
        AthleteGoal goal = new AthleteGoal(athlete, request.horizon(), request.content().trim(),
                request.targetDate());
        try {
            return toResponse(repository.saveAndFlush(goal));
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException(msg.get("athletegoal.duplicate.active"));
        }
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
        return new GoalResponse(g.getId(), g.getHorizon(), g.getContent(),
                g.getTargetDate(), g.getAchievedAt());
    }
}
