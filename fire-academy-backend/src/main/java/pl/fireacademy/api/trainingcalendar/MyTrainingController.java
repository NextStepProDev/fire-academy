package pl.fireacademy.api.trainingcalendar;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.fireacademy.api.trainingcalendar.TrainingCalendarDtos.*;
import pl.fireacademy.config.CurrentUserId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Client side of the 1-on-1 calendar. Authenticated by the {@code /api/user/**} prefix; whether the
 * caller is actually a coaching client is settled by {@link TrainingAccessService}, which answers 404
 * (not 403) so an ordinary account cannot tell the feature exists.
 * <p>
 * The path segment {@code my-training} also selects a dedicated rate-limit bucket — see
 * {@code RateLimitFilter}.
 */
@RestController
@RequestMapping("/api/user/my-training")
public class MyTrainingController {

    private final PersonalTrainingService service;
    private final AthleteGoalService goalService;
    private final TrainingStatsService statsService;
    private final AthleteWeightService weightService;

    public MyTrainingController(PersonalTrainingService service,
                                AthleteGoalService goalService,
                                TrainingStatsService statsService,
                                AthleteWeightService weightService) {
        this.service = service;
        this.goalService = goalService;
        this.statsService = statsService;
        this.weightService = weightService;
    }

    @GetMapping("/calendar")
    public CalendarRangeResponse getRange(
            @CurrentUserId UUID userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.getRange(userId, from, to, userId, false);
    }

    /** Cheap payload behind the account tile badge — no calendar page, just the counters. */
    @GetMapping("/summary")
    public MyTrainingSummary summary(@CurrentUserId UUID userId) {
        return service.summary(userId, userId, false);
    }

    @PostMapping("/trainings")
    @ResponseStatus(HttpStatus.CREATED)
    public PersonalTrainingResponse create(@CurrentUserId UUID userId,
                                           @Valid @RequestBody CreateTrainingRequest request) {
        return service.create(userId, request, false);
    }

    @PutMapping("/trainings/{id}")
    public PersonalTrainingResponse update(@CurrentUserId UUID userId, @PathVariable UUID id,
                                           @Valid @RequestBody UpdateTrainingRequest request) {
        return service.update(id, request, userId, false);
    }

    @DeleteMapping("/trainings/{id}")
    public ResponseEntity<Void> delete(@CurrentUserId UUID userId, @PathVariable UUID id) {
        service.delete(id, userId, false);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/trainings/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public PersonalTrainingResponse duplicate(@CurrentUserId UUID userId, @PathVariable UUID id,
                                              @Valid @RequestBody DuplicateTrainingRequest request) {
        return service.duplicate(id, request, userId, false);
    }

    @PostMapping("/trainings/paste")
    public PersonalTrainingResponse paste(@CurrentUserId UUID userId,
                                          @Valid @RequestBody PasteTrainingRequest request) {
        return service.paste(request, userId, false);
    }

    /** Ticking off is the client's act alone — there is deliberately no coach equivalent. */
    @PostMapping("/trainings/{id}/complete")
    public PersonalTrainingResponse complete(@CurrentUserId UUID userId, @PathVariable UUID id,
                                             @Valid @RequestBody CompleteTrainingRequest request) {
        return service.complete(id, request, userId);
    }

    @DeleteMapping("/trainings/{id}/complete")
    public PersonalTrainingResponse uncomplete(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return service.uncomplete(id, userId);
    }

    @GetMapping("/trainings/{id}/comments")
    public List<TrainingCommentResponse> comments(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return service.getComments(id, userId, false);
    }

    @PostMapping("/trainings/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public TrainingCommentResponse addComment(@CurrentUserId UUID userId, @PathVariable UUID id,
                                              @Valid @RequestBody AddCommentRequest request) {
        return service.addComment(id, request, userId, false);
    }

    @PostMapping("/mark-seen")
    public ResponseEntity<Void> markSeen(@CurrentUserId UUID userId) {
        service.markSeen(userId, userId, false);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/deletions/dismiss")
    public ResponseEntity<Void> dismissDeletions(@CurrentUserId UUID userId) {
        service.dismissDeletions(userId, userId, false);
        return ResponseEntity.noContent().build();
    }

    /** Same numbers as the coach sees, minus the overtraining signal. */
    @GetMapping("/stats")
    public TrainingStatsResponse stats(@CurrentUserId UUID userId) {
        return statsService.stats(userId, false);
    }

    // --- Body weight. Only the client records it; nobody else stands on the scale. ---

    /** Without the rapid-loss warning: the client sees their trend and reads it themselves. */
    @GetMapping("/weights")
    public WeightSeriesResponse weights(@CurrentUserId UUID userId) {
        return weightService.series(userId, false);
    }

    /**
     * Upsert: weighing twice in a day is a correction, not a second reading.
     * <p>
     * Recording a weight is also the moment any weight goal can be reached, so the check happens
     * here rather than on a nightly sweep — the goal closes while the client is still looking at it.
     * The two services are wired together at this seam so neither has to depend on the other.
     */
    @PutMapping("/weights")
    public WeightPoint recordWeight(@CurrentUserId UUID userId,
                                    @Valid @RequestBody RecordWeightRequest request) {
        WeightPoint saved = weightService.record(userId, request);
        goalService.evaluateWeightGoals(userId, weightService.currentTrendSnapshot(userId));
        return saved;
    }

    @DeleteMapping("/weights/{date}")
    public ResponseEntity<Void> deleteWeight(
            @CurrentUserId UUID userId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        weightService.delete(userId, date);
        return ResponseEntity.noContent().build();
    }

    /** Read-only: goals are the coach's call, the trophy case is the client's to look at. */
    @GetMapping("/goals")
    public GoalsResponse goals(@CurrentUserId UUID userId) {
        return goalService.getGoals(userId);
    }
}
