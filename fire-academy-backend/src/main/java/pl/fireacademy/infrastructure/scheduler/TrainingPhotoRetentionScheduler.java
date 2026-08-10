package pl.fireacademy.infrastructure.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.fireacademy.api.trainingcalendar.TrainingPhotoService;
import pl.fireacademy.domain.training.TrainingComment;
import pl.fireacademy.domain.training.TrainingCommentRepository;
import pl.fireacademy.infrastructure.storage.FileStorageService;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Keeps the training photo folder honest, in two passes.
 * <p>
 * Photos are health data, and the retention promised in the privacy policy is 30 days — a promise
 * nothing else in the service would keep. The second pass is the one that matters for an audit:
 * every explicit unlink elsewhere happens inside a transaction that can still roll back, and
 * {@code LocalFileStorageService.delete} logs its failures rather than raising them. Reconciling the
 * folder against the rows turns every one of those races from a permanent leak of somebody's health
 * data into a file that survives at most one more night.
 */
@Component
public class TrainingPhotoRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(TrainingPhotoRetentionScheduler.class);

    /**
     * A file younger than this is left alone by the orphan sweep. The set of known filenames is read
     * before the folder is listed, so an upload landing between the two would look orphaned — and
     * deleting a photo somebody just sent is far worse than sweeping it a day later.
     */
    private static final Duration ORPHAN_GRACE = Duration.ofHours(1);

    private final TrainingCommentRepository commentRepository;
    private final FileStorageService storage;

    public TrainingPhotoRetentionScheduler(TrainingCommentRepository commentRepository,
                                           FileStorageService storage) {
        this.commentRepository = commentRepository;
        this.storage = storage;
    }

    @Scheduled(cron = "0 45 3 * * *")
    public void sweep() {
        int expired = deleteExpired();
        int orphans = deleteOrphans();
        if (expired > 0 || orphans > 0) {
            log.info("Training photo sweep: {} expired, {} orphaned file(s) removed", expired, orphans);
        }
    }

    /**
     * Retention pass. The words stay — only the picture goes, because a comment reading "legs were
     * dead today" is still worth having a year on. A comment that was nothing but a photo has
     * nothing left to say and is removed whole; the CHECK constraint would refuse it anyway.
     */
    @Transactional
    public int deleteExpired() {
        List<TrainingComment> expired = commentRepository.findExpiredPhotos(Instant.now());
        for (TrainingComment comment : expired) {
            String filename = comment.getPhotoFilename();
            if (comment.getBody() == null) {
                commentRepository.delete(comment);
            } else {
                comment.clearPhoto();
                commentRepository.save(comment);
            }
            if (filename != null) {
                storage.delete(TrainingPhotoService.FOLDER, filename);
            }
        }
        return expired.size();
    }

    /** Files on disk that no row claims any more — the safety net under every other delete path. */
    @Transactional(readOnly = true)
    public int deleteOrphans() {
        Set<String> known = commentRepository.findAllPhotoFilenames();
        Instant cutoff = Instant.now().minus(ORPHAN_GRACE);

        int removed = 0;
        for (String filename : storage.listFilenames(TrainingPhotoService.FOLDER)) {
            if (known.contains(filename)) {
                continue;
            }
            if (storage.getLastModified(TrainingPhotoService.FOLDER, filename).isAfter(cutoff)) {
                continue;
            }
            storage.delete(TrainingPhotoService.FOLDER, filename);
            removed++;
        }
        return removed;
    }
}
