package pl.fireacademy.api.trainingcalendar;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.fireacademy.api.trainingcalendar.TrainingCalendarDtos.*;
import pl.fireacademy.domain.training.*;
import pl.fireacademy.domain.user.User;
import pl.fireacademy.domain.user.UserRepository;
import pl.fireacademy.infrastructure.i18n.MessageService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Core of the 1-on-1 calendar, shared by the coach and the client.
 * <p>
 * Both roles get the same operations; the only differences are which athlete they may reach (settled
 * by {@link TrainingAccessService}) and the {@code byAdmin} flag stamped on every write, which is what
 * drives the unread dots. Keeping one implementation means a rule cannot be enforced for one role and
 * forgotten for the other.
 */
@Service
public class PersonalTrainingService {

    /**
     * Widest calendar page we will assemble. A month grid is 42 days and a week is 7, so this leaves
     * headroom while keeping the cost of the (later) recurring overlay bounded and predictable.
     */
    static final int MAX_RANGE_DAYS = 62;

    private static final int DEFAULT_DUPLICATE_OFFSET_DAYS = 7;

    /** The banner is an alert feed, not an archive — showing every deletion ever would bury the news. */
    private static final int MAX_DELETION_NOTICES = 10;

    private final PersonalTrainingRepository repository;
    private final TrainingCommentRepository commentRepository;
    private final TrainingDeletionRepository deletionRepository;
    private final TrainingAccessService access;
    private final TrainingUnreadService unread;
    private final AttachmentService attachments;
    private final UserRepository userRepository;
    private final MessageService msg;

    public PersonalTrainingService(PersonalTrainingRepository repository,
                                   TrainingCommentRepository commentRepository,
                                   TrainingDeletionRepository deletionRepository,
                                   TrainingAccessService access,
                                   TrainingUnreadService unread,
                                   AttachmentService attachments,
                                   UserRepository userRepository,
                                   MessageService msg) {
        this.repository = repository;
        this.commentRepository = commentRepository;
        this.deletionRepository = deletionRepository;
        this.access = access;
        this.unread = unread;
        this.attachments = attachments;
        this.userRepository = userRepository;
        this.msg = msg;
    }

    @Transactional(readOnly = true)
    public CalendarRangeResponse getRange(UUID athleteId, LocalDate from, LocalDate to,
                                          UUID viewerId, boolean viewerIsAdmin) {
        access.requireAthlete(athleteId);
        validateRange(from, to);

        List<PersonalTraining> trainings = repository.findRange(athleteId, from, to);
        List<UUID> ids = trainings.stream().map(PersonalTraining::getId).toList();

        Instant since = unread.seenAt(viewerId, athleteId);
        Set<UUID> withNewComments = unread.unreadTrainingIds(ids, viewerIsAdmin, since);
        Map<UUID, Integer> commentCounts = commentCounts(ids);
        Map<UUID, List<AttachmentResponse>> materials = attachments.forTrainings(ids);

        LocalDateTime now = LocalDateTime.now();
        List<PersonalTrainingResponse> items = trainings.stream()
                .map(t -> toResponse(t, now,
                        isUnread(t, viewerIsAdmin, since) || withNewComments.contains(t.getId()),
                        commentCounts.getOrDefault(t.getId(), 0),
                        materials.getOrDefault(t.getId(), List.of())))
                .toList();

        // The coach hears about the client's deletions and vice versa.
        List<DeletedTrainingNotice> notices = deletionRepository
                .findPending(athleteId, !viewerIsAdmin, PageRequest.of(0, MAX_DELETION_NOTICES)).stream()
                .map(d -> new DeletedTrainingNotice(d.getId(), d.getDate(), d.getStartTime(),
                        d.getTitle(), d.getDeletedAt()))
                .toList();

        return new CalendarRangeResponse(from, to, items, notices);
    }

    @Transactional
    public PersonalTrainingResponse create(UUID athleteId, CreateTrainingRequest request, boolean byAdmin) {
        User athlete = access.requireAthlete(athleteId);
        validateTimes(request.startTime(), request.endTime());

        PersonalTraining training = new PersonalTraining(athlete, request.date(), request.title().trim(), byAdmin);
        training.edit(request.date(), request.startTime(), request.endTime(),
                request.title().trim(), trimToNull(request.description()), byAdmin);
        PersonalTraining saved = repository.saveAndFlush(training);
        attachments.applyToTraining(saved, request.attachments());
        return single(saved);
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
        PersonalTraining saved = save(training);
        // null here means "leave materials alone" — see AttachmentService for why that matters.
        attachments.applyToTraining(saved, request.attachments());
        return single(saved);
    }

    @Transactional
    public void delete(UUID trainingId, UUID viewerId, boolean viewerIsAdmin) {
        PersonalTraining training = access.requireTraining(trainingId, viewerId, viewerIsAdmin);
        // Only FUTURE deletions are announced. Clearing out a past entry is housekeeping, and an
        // alert for it would train the other side to ignore the banner.
        if (training.getDate().isAfter(LocalDate.now())) {
            deletionRepository.save(new TrainingDeletion(training, viewerIsAdmin));
        }
        repository.delete(training);
    }

    /** Same content, shifted forward — "the same thing again next week" is the common case. */
    @Transactional
    public PersonalTrainingResponse duplicate(UUID trainingId, DuplicateTrainingRequest request,
                                              UUID viewerId, boolean viewerIsAdmin) {
        PersonalTraining source = access.requireTraining(trainingId, viewerId, viewerIsAdmin);
        int offset = request.offsetDays() == null ? DEFAULT_DUPLICATE_OFFSET_DAYS : request.offsetDays();
        return single(repository.save(copyOf(source, source.getDate().plusDays(offset), viewerIsAdmin)));
    }

    /**
     * Clipboard paste. COPY leaves the source alone; MOVE re-dates the original so its id — and with
     * it the completion state and the comment thread — survives, which a delete-and-recreate would lose.
     */
    @Transactional
    public PersonalTrainingResponse paste(PasteTrainingRequest request, UUID viewerId, boolean viewerIsAdmin) {
        PersonalTraining source = access.requireTraining(request.sourceId(), viewerId, viewerIsAdmin);

        if (request.mode() == PasteMode.COPY) {
            return single(repository.save(copyOf(source, request.targetDate(), viewerIsAdmin)));
        }

        source.edit(request.targetDate(), source.getStartTime(), source.getEndTime(),
                source.getTitle(), source.getDescription(), viewerIsAdmin);
        requireCompletedStaysInPast(source);
        return single(save(source));
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
        return single(save(training));
    }

    @Transactional
    public PersonalTrainingResponse uncomplete(UUID trainingId, UUID viewerId) {
        PersonalTraining training = access.requireTraining(trainingId, viewerId, false);
        training.uncomplete();
        return single(save(training));
    }

    @Transactional(readOnly = true)
    public List<TrainingCommentResponse> getComments(UUID trainingId, UUID viewerId, boolean viewerIsAdmin) {
        access.requireTraining(trainingId, viewerId, viewerIsAdmin);
        return commentRepository.findThread(trainingId).stream()
                .map(PersonalTrainingService::toCommentResponse)
                .toList();
    }

    @Transactional
    public TrainingCommentResponse addComment(UUID trainingId, AddCommentRequest request,
                                              UUID viewerId, boolean viewerIsAdmin) {
        PersonalTraining training = access.requireTraining(trainingId, viewerId, viewerIsAdmin);
        User author = userRepository.findById(viewerId)
                .orElseThrow(() -> new IllegalStateException(msg.get("error.user.not.found")));
        TrainingComment comment = new TrainingComment(training, author, viewerIsAdmin, request.body().trim());
        return toCommentResponse(commentRepository.save(comment));
    }

    @Transactional
    public void markSeen(UUID athleteId, UUID viewerId, boolean viewerIsAdmin) {
        access.requireAthlete(athleteId);
        unread.markSeen(viewerId, athleteId);
    }

    /** Hides the deletion banner. Separate from "seen" — acknowledging a loss is its own act. */
    @Transactional
    public void dismissDeletions(UUID athleteId, UUID viewerId, boolean viewerIsAdmin) {
        access.requireAthlete(athleteId);
        deletionRepository.dismissAll(athleteId, !viewerIsAdmin, Instant.now());
    }

    @Transactional(readOnly = true)
    public MyTrainingSummary summary(UUID athleteId, UUID viewerId, boolean viewerIsAdmin) {
        access.requireAthlete(athleteId);
        long unreadCount = unread.countUnread(viewerId, athleteId, viewerIsAdmin);
        int deleted = deletionRepository
                .findPending(athleteId, !viewerIsAdmin, PageRequest.of(0, MAX_DELETION_NOTICES)).size();
        return new MyTrainingSummary(unreadCount, deleted,
                repository.findNextTrainingDate(athleteId, LocalDate.now()));
    }

    private Map<UUID, Integer> commentCounts(List<UUID> trainingIds) {
        Map<UUID, Integer> counts = new HashMap<>();
        if (trainingIds.isEmpty()) {
            return counts;
        }
        for (Object[] row : commentRepository.countByTrainingIds(trainingIds)) {
            counts.put((UUID) row[0], ((Number) row[1]).intValue());
        }
        return counts;
    }

    /**
     * Keyed on {@code updatedAt}, never {@code completedAt}: undoing a completion clears that column
     * and the coach still needs to hear about it.
     */
    private static boolean isUnread(PersonalTraining t, boolean viewerIsAdmin, Instant since) {
        return t.isLastModifiedByAdmin() != viewerIsAdmin && t.getUpdatedAt().isAfter(since);
    }

    private PersonalTrainingResponse single(PersonalTraining training) {
        // The viewer just performed this action, so nothing about it is news to them.
        return toResponse(training, LocalDateTime.now(), false,
                (int) commentRepository.countByTrainingId(training.getId()),
                attachments.forTrainings(List.of(training.getId()))
                        .getOrDefault(training.getId(), List.of()));
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

    private static TrainingCommentResponse toCommentResponse(TrainingComment c) {
        User author = c.getAuthor();
        return new TrainingCommentResponse(c.getId(), c.getBody(), c.isAuthorIsAdmin(),
                author == null ? null : author.getFirstName(), c.getCreatedAt());
    }

    static PersonalTrainingResponse toResponse(PersonalTraining t, LocalDateTime now,
                                               boolean unread, int commentCount,
                                               List<AttachmentResponse> materials) {
        return new PersonalTrainingResponse(
                t.getId(), t.getDate(), t.getStartTime(), t.getEndTime(),
                t.getTitle(), t.getDescription(), t.status(now),
                t.getCompletedAt(), t.getFeedback(), t.getRpe(),
                t.isCreatedByAdmin(), t.isLastModifiedByAdmin(),
                unread, commentCount, materials,
                t.getVersion(), t.getCreatedAt(), t.getUpdatedAt());
    }
}
