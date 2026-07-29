package pl.fireacademy.domain.training;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TrainingDeletionRepository extends JpaRepository<TrainingDeletion, UUID> {

    /**
     * Pending alerts for one side. Deliberately NOT limited to the visible date range: a training
     * deleted from next month still matters while you are looking at this week.
     */
    @Query("""
        SELECT d FROM TrainingDeletion d
        WHERE d.athlete.id = :athleteId
          AND d.deletedByAdmin = :byAdmin
          AND d.dismissedAt IS NULL
        ORDER BY d.deletedAt DESC
        """)
    List<TrainingDeletion> findPending(@Param("athleteId") UUID athleteId,
                                       @Param("byAdmin") boolean byAdmin,
                                       Pageable pageable);

    @Query("""
        SELECT COUNT(d) FROM TrainingDeletion d
        WHERE d.athlete.id = :athleteId
          AND d.deletedByAdmin = :byAdmin
          AND d.dismissedAt IS NULL
          AND d.deletedAt > :since
        """)
    long countSince(@Param("athleteId") UUID athleteId,
                    @Param("byAdmin") boolean byAdmin,
                    @Param("since") Instant since);

    @Modifying
    @Query("""
        UPDATE TrainingDeletion d SET d.dismissedAt = :now
        WHERE d.athlete.id = :athleteId AND d.deletedByAdmin = :byAdmin AND d.dismissedAt IS NULL
        """)
    int dismissAll(@Param("athleteId") UUID athleteId,
                   @Param("byAdmin") boolean byAdmin,
                   @Param("now") Instant now);

    /** Housekeeping: the log is an alert feed, not an archive. */
    @Modifying
    @Query("DELETE FROM TrainingDeletion d WHERE d.deletedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
