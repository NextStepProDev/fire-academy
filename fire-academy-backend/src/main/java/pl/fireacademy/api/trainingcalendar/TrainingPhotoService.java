package pl.fireacademy.api.trainingcalendar;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pl.fireacademy.api.NotFoundException;
import pl.fireacademy.api.Strings;
import pl.fireacademy.api.trainingcalendar.TrainingCalendarDtos.TrainingCommentResponse;
import pl.fireacademy.domain.training.PersonalTraining;
import pl.fireacademy.domain.training.TrainingComment;
import pl.fireacademy.domain.training.TrainingCommentRepository;
import pl.fireacademy.domain.user.User;
import pl.fireacademy.domain.user.UserRepository;
import pl.fireacademy.infrastructure.i18n.MessageService;
import pl.fireacademy.infrastructure.storage.FileStorageService;
import pl.fireacademy.infrastructure.storage.StorePolicy;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Photos attached to comments in the 1-on-1 plan — in practice a screenshot from a sports watch.
 * <p>
 * Shared by both roles for the same reason the controllers are: the frontend renders one component
 * for the coach and the client, so both must get an identical payload. Which athlete a caller may
 * reach is settled by {@link TrainingAccessService}, exactly as everywhere else in this package —
 * meaning a stranger gets 404 rather than 403, and the client's own path is additionally gated by
 * {@code TrainingConsentInterceptor} because it lives under {@code /api/user/my-training}.
 * <p>
 * These files are health data (GDPR art. 9). They never enter the public {@code /api/files}
 * namespace and are never cached to disk anywhere — see the headers in the controllers.
 */
@Service
public class TrainingPhotoService {

    private static final Logger log = LoggerFactory.getLogger(TrainingPhotoService.class);

    /** Folder under the storage root. Deliberately absent from FileController's public allowlist. */
    public static final String FOLDER = "trainingphotos";

    /**
     * How long a photo lives. A screenshot answers "how did that session go" and stops being worth
     * keeping once the answer has been read — so the honest retention is short, and a short one is
     * also what keeps a folder of health data from growing without limit for years.
     */
    public static final Duration RETENTION = Duration.ofDays(30);

    /** Per training, across the whole thread. A watch session is a summary, zones and splits. */
    static final int MAX_PHOTOS_PER_TRAINING = 3;

    private final TrainingCommentRepository commentRepository;
    private final TrainingAccessService access;
    private final UserRepository userRepository;
    private final FileStorageService storage;
    private final MessageService msg;

    public TrainingPhotoService(TrainingCommentRepository commentRepository,
                                TrainingAccessService access,
                                UserRepository userRepository,
                                FileStorageService storage,
                                MessageService msg) {
        this.commentRepository = commentRepository;
        this.access = access;
        this.userRepository = userRepository;
        this.storage = storage;
        this.msg = msg;
    }

    /**
     * Adds a comment carrying a photo, with optional text alongside it.
     * <p>
     * Kept separate from {@code PersonalTrainingService.addComment} rather than folded into it: that
     * endpoint takes JSON and is what every plain reply uses, and putting a plain text comment
     * through multipart to serve the rarer case would be the wrong way round.
     */
    @Transactional
    public TrainingCommentResponse addPhotoComment(UUID trainingId, @Nullable String body, MultipartFile file,
                                                   UUID viewerId, boolean viewerIsAdmin) {
        PersonalTraining training = access.requireTraining(trainingId, viewerId, viewerIsAdmin);

        if (file.isEmpty()) {
            throw new IllegalArgumentException(msg.get("personaltraining.comment.empty"));
        }
        if (commentRepository.countPhotosForTraining(trainingId) >= MAX_PHOTOS_PER_TRAINING) {
            throw new IllegalStateException(msg.get("personaltraining.photo.limit"));
        }

        User author = userRepository.findById(viewerId)
                .orElseThrow(() -> new IllegalStateException(msg.get("error.user.not.found")));

        // Stored under the training-photo policy: JPEG only, re-encoded by us. See StorePolicy.
        FileStorageService.StoredImage stored = storage.storeImage(FOLDER, file, StorePolicy.TRAINING_PHOTO);

        TrainingComment comment = new TrainingComment(training, author, viewerIsAdmin, Strings.trimToNull(body));
        comment.attachPhoto(stored.filename(), stored.width(), stored.height(), Instant.now().plus(RETENTION));
        return TrainingCommentMapper.toResponse(commentRepository.save(comment), viewerId, viewerIsAdmin);
    }

    /**
     * Opens the stored photo for streaming, after checking that this viewer may see the training it
     * hangs off. A missing row, an unreachable athlete and a file already swept away all end as the
     * same 404 — the caller learns nothing from which of the three it was.
     */
    @Transactional(readOnly = true)
    public PhotoStream open(UUID commentId, UUID viewerId, boolean viewerIsAdmin) {
        TrainingComment comment = requireReadableComment(commentId, viewerId, viewerIsAdmin);
        String filename = comment.getPhotoFilename();
        if (filename == null || !storage.exists(FOLDER, filename)) {
            throw new NotFoundException(msg.get("personaltraining.photo.not.found"));
        }
        return new PhotoStream(storage.getInputStream(FOLDER, filename), storage.getFileSize(FOLDER, filename));
    }

    /**
     * Removes the photo and keeps the words. A comment that was nothing but a photo goes entirely —
     * the CHECK constraint refuses an empty one, and an empty bubble would be nothing to read anyway.
     */
    @Transactional
    public void deletePhoto(UUID commentId, UUID viewerId, boolean viewerIsAdmin) {
        TrainingComment comment = requireReadableComment(commentId, viewerId, viewerIsAdmin);
        String filename = comment.getPhotoFilename();
        if (filename == null) {
            throw new NotFoundException(msg.get("personaltraining.photo.not.found"));
        }
        if (!TrainingCommentMapper.canDelete(comment, viewerId, viewerIsAdmin)) {
            // A client may withdraw what they sent; they may not remove what the coach sent them.
            throw new IllegalStateException(msg.get("personaltraining.photo.not.yours"));
        }

        if (comment.getBody() == null) {
            commentRepository.delete(comment);
        } else {
            comment.clearPhoto();
            commentRepository.save(comment);
        }
        storage.delete(FOLDER, filename);
    }

    /**
     * Unlinks every photo on a training. Must run BEFORE the training row is deleted: the comment
     * rows disappear through the database cascade without Hibernate ever loading them, so no entity
     * callback can reach the files afterwards.
     */
    @Transactional(readOnly = true)
    public void purgeForTraining(UUID trainingId) {
        deleteAll(commentRepository.findPhotoFilenamesForTraining(trainingId));
    }

    /**
     * Removes every photo tied to an account about to be deleted — both the ones on that person's
     * own trainings and the ones they wrote in someone else's thread.
     * <p>
     * The two halves end differently, which is why the rows are cleared and not just the files.
     * Comments on the person's own trainings vanish with them through the cascade. Comments they
     * wrote in someone ELSE's plan survive: {@code author_id} is only set to NULL, so the text stays
     * and reads as "Konto usunięte". Unlinking the file without clearing {@code photo_filename}
     * would leave those rows pointing at nothing, and the other side would be looking at a broken
     * frame for as long as the comment exists.
     */
    @Transactional
    public void purgeForUser(UUID userId) {
        detachAll(commentRepository.findPhotosForUser(userId));
    }

    /** Clears the photo off each comment — row and all, when the photo was the whole message. */
    private void detachAll(List<TrainingComment> comments) {
        List<String> filenames = new ArrayList<>(comments.size());
        for (TrainingComment comment : comments) {
            String filename = comment.getPhotoFilename();
            if (filename != null) {
                filenames.add(filename);
            }
            if (comment.getBody() == null) {
                commentRepository.delete(comment);
            } else {
                comment.clearPhoto();
                commentRepository.save(comment);
            }
        }
        deleteAll(filenames);
    }

    private void deleteAll(List<String> filenames) {
        for (String filename : filenames) {
            storage.delete(FOLDER, filename);
        }
        if (!filenames.isEmpty()) {
            log.info("Unlinked {} training photo(s)", filenames.size());
        }
    }

    /** Resolves the comment through the same access rules as the training it belongs to. */
    private TrainingComment requireReadableComment(UUID commentId, UUID viewerId, boolean viewerIsAdmin) {
        TrainingComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException(msg.get("personaltraining.photo.not.found")));
        access.requireTraining(comment.getTraining().getId(), viewerId, viewerIsAdmin);
        return comment;
    }

    public record PhotoStream(InputStream inputStream, long size) {}
}
