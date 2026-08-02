package pl.fireacademy.domain.training;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
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
}
