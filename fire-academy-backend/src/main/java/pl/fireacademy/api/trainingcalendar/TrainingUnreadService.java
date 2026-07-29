package pl.fireacademy.api.trainingcalendar;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.fireacademy.domain.training.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Everything that can be "new" on a training calendar, in one place.
 * <p>
 * The complete list was settled before the first counter was written, because the failure mode is
 * silent: a source nobody remembered simply never lights a dot, and the gap only surfaces when
 * someone complains that a change went unnoticed.
 *
 * <p><b>News for the client (from the coach):</b>
 * <ol>
 *   <li>a training the coach created or edited</li>
 *   <li>a comment written by the coach</li>
 *   <li>a future training the coach deleted</li>
 * </ol>
 *
 * <p><b>News for the coach (from the client):</b>
 * <ol start="4">
 *   <li>a training the client created or edited — this covers ticking off AND undoing it</li>
 *   <li>a comment written by the client</li>
 *   <li>a future training the client deleted</li>
 * </ol>
 *
 * <p>Goals become the seventh source when they land; the counter below already routes through one
 * method per side, so adding it is a single line rather than a new mechanism.
 *
 * <p>Symmetry is the point: both directions are computed by the same code with the role flag
 * flipped, so a rule cannot be enforced one way and forgotten the other.
 */
@Service
public class TrainingUnreadService {

    private final PersonalTrainingRepository trainingRepository;
    private final TrainingCommentRepository commentRepository;
    private final TrainingDeletionRepository deletionRepository;
    private final TrainingCalendarReadRepository readRepository;

    public TrainingUnreadService(PersonalTrainingRepository trainingRepository,
                                 TrainingCommentRepository commentRepository,
                                 TrainingDeletionRepository deletionRepository,
                                 TrainingCalendarReadRepository readRepository) {
        this.trainingRepository = trainingRepository;
        this.commentRepository = commentRepository;
        this.deletionRepository = deletionRepository;
        this.readRepository = readRepository;
    }

    /** No marker means the calendar was never opened — everything counts as new. */
    @Transactional(readOnly = true)
    public Instant seenAt(UUID viewerId, UUID athleteId) {
        return readRepository.findByUserIdAndAthleteId(viewerId, athleteId)
                .map(TrainingCalendarRead::getSeenAt)
                .orElse(Instant.EPOCH);
    }

    /**
     * How many things the other side has done since this viewer last looked.
     *
     * @param viewerIsAdmin the coach counts the client's activity, and vice versa
     */
    @Transactional(readOnly = true)
    public long countUnread(UUID viewerId, UUID athleteId, boolean viewerIsAdmin) {
        Instant since = seenAt(viewerId, athleteId);
        boolean fromAdmin = !viewerIsAdmin;
        return trainingRepository.countTouchedSince(athleteId, fromAdmin, since)
                + commentRepository.countSince(athleteId, fromAdmin, since)
                + deletionRepository.countSince(athleteId, fromAdmin, since);
    }

    /** Per-training dots for one calendar page, batched — one query for the whole range. */
    @Transactional(readOnly = true)
    public Set<UUID> unreadTrainingIds(List<UUID> trainingIds, boolean viewerIsAdmin, Instant since) {
        if (trainingIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(commentRepository.findTrainingIdsWithNewComments(trainingIds, !viewerIsAdmin, since));
    }

    /**
     * Stamps "seen" for this viewer.
     * <p>
     * The caller decides WHEN: the frontend must wait until the calendar has actually rendered, or
     * the dots are cleared before anyone sees them.
     */
    @Transactional
    public void markSeen(UUID viewerId, UUID athleteId) {
        readRepository.upsertSeen(viewerId, athleteId, Instant.now());
    }
}
