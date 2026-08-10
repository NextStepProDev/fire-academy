package pl.fireacademy.domain.training;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface TrainingCommentRepository extends JpaRepository<TrainingComment, UUID> {

    @Query("""
        SELECT c FROM TrainingComment c
        LEFT JOIN FETCH c.author
        WHERE c.training.id = :trainingId
        ORDER BY c.createdAt ASC
        """)
    List<TrainingComment> findThread(@Param("trainingId") UUID trainingId);

    /**
     * Comments the given viewer has not seen: written by the other side after their last visit.
     * {@code authorIsAdmin} is the frozen role, so this stays correct even if someone changes role.
     */
    @Query("""
        SELECT COUNT(c) FROM TrainingComment c
        WHERE c.training.athlete.id = :athleteId
          AND c.authorIsAdmin = :fromAdmin
          AND c.createdAt > :since
        """)
    long countSince(@Param("athleteId") UUID athleteId,
                    @Param("fromAdmin") boolean fromAdmin,
                    @Param("since") Instant since);

    long countByTrainingId(UUID trainingId);

    /** Comment counts for a whole calendar page in one query. */
    @Query("""
        SELECT c.training.id, COUNT(c) FROM TrainingComment c
        WHERE c.training.id IN :trainingIds
        GROUP BY c.training.id
        """)
    List<Object[]> countByTrainingIds(@Param("trainingIds") List<UUID> trainingIds);

    /** Per-training flag for the unread dot, batched for one calendar page. */
    @Query("""
        SELECT DISTINCT c.training.id FROM TrainingComment c
        WHERE c.training.id IN :trainingIds
          AND c.authorIsAdmin = :fromAdmin
          AND c.createdAt > :since
        """)
    List<UUID> findTrainingIdsWithNewComments(@Param("trainingIds") List<UUID> trainingIds,
                                              @Param("fromAdmin") boolean fromAdmin,
                                              @Param("since") Instant since);

    /** The per-training photo cap counts across the whole thread, not per comment. */
    @Query("""
        SELECT COUNT(c) FROM TrainingComment c
        WHERE c.training.id = :trainingId
          AND c.photoFilename IS NOT NULL
        """)
    long countPhotosForTraining(@Param("trainingId") UUID trainingId);

    /**
     * Filenames to unlink before a training is deleted. The rows go through the DB cascade without
     * Hibernate ever loading them, so no entity callback can do this — it has to be explicit.
     */
    @Query("""
        SELECT c.photoFilename FROM TrainingComment c
        WHERE c.training.id = :trainingId
          AND c.photoFilename IS NOT NULL
        """)
    List<String> findPhotoFilenamesForTraining(@Param("trainingId") UUID trainingId);

    /**
     * Filenames to unlink before an account is deleted. Covers both sides: photos on this person's
     * own trainings, and photos they wrote in someone else's thread — {@code author_id} is only set
     * to NULL by the cascade, so those files would otherwise outlive their author.
     */
    @Query("""
        SELECT c.photoFilename FROM TrainingComment c
        WHERE c.photoFilename IS NOT NULL
          AND (c.training.athlete.id = :userId OR c.author.id = :userId)
        """)
    List<String> findPhotoFilenamesForUser(@Param("userId") UUID userId);

    @Query("""
        SELECT c FROM TrainingComment c
        WHERE c.photoFilename IS NOT NULL
          AND c.photoExpiresAt < :now
        """)
    List<TrainingComment> findExpiredPhotos(@Param("now") Instant now);

    /** Every filename the database still knows about — the other half of the orphan sweep. */
    @Query("SELECT c.photoFilename FROM TrainingComment c WHERE c.photoFilename IS NOT NULL")
    Set<String> findAllPhotoFilenames();
}
