package pl.fireacademy.api.trainingcalendar;

import org.jspecify.annotations.Nullable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.fireacademy.api.trainingcalendar.TrainingCalendarDtos.*;
import pl.fireacademy.domain.training.PersonalTraining;
import pl.fireacademy.domain.training.PersonalTrainingRepository;
import pl.fireacademy.domain.user.User;
import pl.fireacademy.infrastructure.i18n.MessageService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Core of the 1-on-1 calendar, shared by the coach and the client.
 * <p>
 * Both roles get the same operations; the only differences are which athlete they may reach (settled
 * by {@link TrainingAccessService}) and the {@code byAdmin} flag stamped on every write, which is what
 * later drives the unread dots. Keeping one implementation means a rule cannot be enforced for one
 * role and forgotten for the other.
 */
@Service
public class PersonalTrainingService {

    /**
     * Widest calendar page we will assemble. A month grid is 42 days and a week is 7, so this leaves
     * headroom while keeping the cost of the (later) recurring overlay bounded and predictable.
     */
    static final int MAX_RANGE_DAYS = 62;

    private static final int DEFAULT_DUPLICATE_OFFSET_DAYS = 7;

    private final PersonalTrainingRepository repository;
    private final TrainingAccessService access;
    private final MessageService msg;

    public PersonalTrainingService(PersonalTrainingRepository repository,
                                   TrainingAccessService access,
                                   MessageService msg) {
        this.repository = repository;
        this.access = access;
        this.msg = msg;
    }

    @Transactional(readOnly = true)
    public CalendarRangeResponse getRange(UUID athleteId, LocalDate from, LocalDate to) {
        access.requireAthlete(athleteId);
        validateRange(from, to);
        LocalDateTime now = LocalDateTime.now();
        List<PersonalTrainingResponse> trainings = repository.findRange(athleteId, from, to).stream()
                .map(t -> toResponse(t, now))
                .toList();
        return new CalendarRangeResponse(from, to, trainings);
    }

    @Transactional
    public PersonalTrainingResponse create(UUID athleteId, CreateTrainingRequest request, boolean byAdmin) {
        User athlete = access.requireAthlete(athleteId);
        validateTimes(request.startTime(), request.endTime());

        PersonalTraining training = new PersonalTraining(athlete, request.date(), request.title().trim(), byAdmin);
        training.edit(request.date(), request.startTime(), request.endTime(),
                request.title().trim(), trimToNull(request.description()), byAdmin);
        return toResponse(repository.save(training), LocalDateTime.now());
    }

    @Transactional
    public PersonalTrainingResponse update(UUID trainingId, UpdateTrainingRequest request,
                                           UUID viewerId, boolean viewerIsAdmin) {
        PersonalTraining training = access.requireTraining(trainingId, viewerId, viewerIsAdmin);
        requireCurrentVersion(training, request.version());
        validateTimes(request.startTime(), request.endTime());

        training.edit(request.date(), request.startTime(), request.endTime(),
                request.title().trim(), trimToNull(request.description()), viewerIsAdmin);
        requireCompletedStaysInPast(training);
        return toResponse(save(training), LocalDateTime.now());
    }

    @Transactional
    public void delete(UUID trainingId, UUID viewerId, boolean viewerIsAdmin) {
        PersonalTraining training = access.requireTraining(trainingId, viewerId, viewerIsAdmin);
        repository.delete(training);
    }

    /** Same content, shifted forward — "the same thing again next week" is the common case. */
    @Transactional
    public PersonalTrainingResponse duplicate(UUID trainingId, DuplicateTrainingRequest request,
                                              UUID viewerId, boolean viewerIsAdmin) {
        PersonalTraining source = access.requireTraining(trainingId, viewerId, viewerIsAdmin);
        int offset = request.offsetDays() == null ? DEFAULT_DUPLICATE_OFFSET_DAYS : request.offsetDays();

        PersonalTraining copy = copyOf(source, source.getDate().plusDays(offset), viewerIsAdmin);
        return toResponse(repository.save(copy), LocalDateTime.now());
    }

    /**
     * Clipboard paste. COPY leaves the source alone; MOVE re-dates the original so its id — and with
     * it the completion state and the comment thread — survives, which a delete-and-recreate would lose.
     */
    @Transactional
    public PersonalTrainingResponse paste(PasteTrainingRequest request, UUID viewerId, boolean viewerIsAdmin) {
        PersonalTraining source = access.requireTraining(request.sourceId(), viewerId, viewerIsAdmin);

        if (request.mode() == PasteMode.COPY) {
            PersonalTraining copy = copyOf(source, request.targetDate(), viewerIsAdmin);
            return toResponse(repository.save(copy), LocalDateTime.now());
        }

        source.edit(request.targetDate(), source.getStartTime(), source.getEndTime(),
                source.getTitle(), source.getDescription(), viewerIsAdmin);
        requireCompletedStaysInPast(source);
        return toResponse(save(source), LocalDateTime.now());
    }

    /**
     * Ticking off is the client's act alone — the coach cannot mark someone else's session done, so
     * this has no counterpart on the admin controller.
     */
    @Transactional
    public PersonalTrainingResponse complete(UUID trainingId, CompleteTrainingRequest request, UUID viewerId) {
        PersonalTraining training = access.requireTraining(trainingId, viewerId, false);
        LocalDateTime now = LocalDateTime.now();
        if (!training.hasStarted(now)) {
            throw new IllegalStateException(msg.get("personaltraining.not.started"));
        }
        training.complete(request.rpe(), trimToNull(request.feedback()));
        return toResponse(save(training), now);
    }

    @Transactional
    public PersonalTrainingResponse uncomplete(UUID trainingId, UUID viewerId) {
        PersonalTraining training = access.requireTraining(trainingId, viewerId, false);
        training.uncomplete();
        return toResponse(save(training), LocalDateTime.now());
    }

    private PersonalTraining copyOf(PersonalTraining source, LocalDate date, boolean byAdmin) {
        PersonalTraining copy = new PersonalTraining(source.getAthlete(), date, source.getTitle(), byAdmin);
        // A copy is a fresh plan, never a fresh achievement: completion, RPE and feedback stay behind.
        copy.edit(date, source.getStartTime(), source.getEndTime(),
                source.getTitle(), source.getDescription(), byAdmin);
        return copy;
    }

    private PersonalTraining save(PersonalTraining training) {
        try {
            return repository.saveAndFlush(training);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new IllegalStateException(msg.get("personaltraining.version.conflict"));
        }
    }

    /**
     * The client holds the version they loaded. Comparing it here — rather than relying on the
     * {@code @Version} column alone — is what makes the check work across separate HTTP requests:
     * within one transaction Hibernate would happily write over a row read moments earlier.
     */
    private void requireCurrentVersion(PersonalTraining training, Long expected) {
        if (expected == null || training.getVersion() != expected) {
            throw new IllegalStateException(msg.get("personaltraining.version.conflict"));
        }
    }

    /**
     * A completed training describes something that happened. Letting it slide into the future would
     * produce a session that is both done and yet to come; undo the completion first.
     */
    private void requireCompletedStaysInPast(PersonalTraining training) {
        if (training.isCompleted() && training.start().isAfter(LocalDateTime.now())) {
            throw new IllegalStateException(msg.get("personaltraining.completed.future"));
        }
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException(msg.get("personaltraining.range.invalid"));
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException(msg.get("personaltraining.range.too.long"));
        }
    }

    private void validateTimes(@Nullable LocalTime start, @Nullable LocalTime end) {
        if (start == null && end != null) {
            throw new IllegalArgumentException(msg.get("personaltraining.time.end.without.start"));
        }
        if (start != null && end != null && !end.isAfter(start)) {
            throw new IllegalArgumentException(msg.get("personaltraining.time.end.before.start"));
        }
    }

    @Nullable
    private static String trimToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static PersonalTrainingResponse toResponse(PersonalTraining t, LocalDateTime now) {
        return new PersonalTrainingResponse(
                t.getId(), t.getDate(), t.getStartTime(), t.getEndTime(),
                t.getTitle(), t.getDescription(), t.status(now),
                t.getCompletedAt(), t.getFeedback(), t.getRpe(),
                t.isCreatedByAdmin(), t.isLastModifiedByAdmin(),
                t.getVersion(), t.getCreatedAt(), t.getUpdatedAt());
    }
}
