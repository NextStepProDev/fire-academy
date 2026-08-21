package pl.fireacademy.domain.training;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainingCalendarReadRepository
        extends JpaRepository<TrainingCalendarRead, TrainingCalendarRead.Key> {

    Optional<TrainingCalendarRead> findByUserIdAndAthleteId(UUID userId, UUID athleteId);

    /**
     * Upsert of the seen marker.
     * <p>
     * Two deliberate details:
     * <ul>
     *   <li>{@code seenAt} comes from the JVM clock, not SQL {@code now()}. Postgres {@code now()} is
     *       the transaction start time, which can predate activity written moments earlier and would
     *       freeze the counter at a stale value.</li>
     *   <li>{@code clearAutomatically} — without it Hibernate keeps serving the old {@code seenAt}
     *       from its identity map for the rest of the transaction.</li>
     * </ul>
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        INSERT INTO training_calendar_reads (user_id, athlete_id, seen_at)
        VALUES (:userId, :athleteId, :seenAt)
        ON CONFLICT (user_id, athlete_id) DO UPDATE SET seen_at = :seenAt
        """, nativeQuery = true)
    void upsertSeen(@Param("userId") UUID userId,
                    @Param("athleteId") UUID athleteId,
                    @Param("seenAt") Instant seenAt);

    /**
     * Unread counts for a whole roster in ONE query per source.
     * <p>
     * The seen marker differs per athlete, which is why this cannot be a simple {@code IN} count:
     * each row has to be compared against that viewer's own marker for that athlete. The LEFT JOIN
     * does exactly that, and a missing marker (never opened) correctly counts everything.
     * <p>
     * Asking per athlete instead would cost 1 + 4N queries — 61 on a roster of fifteen.
     */
    @Query(value = """
        SELECT pt.athlete_id AS athlete_id, count(*) AS total
        FROM personal_trainings pt
        LEFT JOIN training_calendar_reads r
               ON r.user_id = :viewerId AND r.athlete_id = pt.athlete_id
        WHERE pt.athlete_id IN (:athleteIds)
          AND pt.last_modified_by_admin = :fromAdmin
          AND (r.seen_at IS NULL OR pt.updated_at > r.seen_at)
        GROUP BY pt.athlete_id
        """, nativeQuery = true)
    List<UnreadCount> countUnreadTrainings(@Param("viewerId") UUID viewerId,
                                           @Param("athleteIds") Collection<UUID> athleteIds,
                                           @Param("fromAdmin") boolean fromAdmin);

    @Query(value = """
        SELECT pt.athlete_id AS athlete_id, count(*) AS total
        FROM training_comments c
        JOIN personal_trainings pt ON pt.id = c.training_id
        LEFT JOIN training_calendar_reads r
               ON r.user_id = :viewerId AND r.athlete_id = pt.athlete_id
        WHERE pt.athlete_id IN (:athleteIds)
          AND c.author_is_admin = :fromAdmin
          AND (r.seen_at IS NULL OR c.created_at > r.seen_at)
        GROUP BY pt.athlete_id
        """, nativeQuery = true)
    List<UnreadCount> countUnreadComments(@Param("viewerId") UUID viewerId,
                                          @Param("athleteIds") Collection<UUID> athleteIds,
                                          @Param("fromAdmin") boolean fromAdmin);

    @Query(value = """
        SELECT d.athlete_id AS athlete_id, count(*) AS total
        FROM training_deletions d
        LEFT JOIN training_calendar_reads r
               ON r.user_id = :viewerId AND r.athlete_id = d.athlete_id
        WHERE d.athlete_id IN (:athleteIds)
          AND d.deleted_by_admin = :fromAdmin
          AND d.dismissed_at IS NULL
          AND (r.seen_at IS NULL OR d.deleted_at > r.seen_at)
        GROUP BY d.athlete_id
        """, nativeQuery = true)
    List<UnreadCount> countUnreadDeletions(@Param("viewerId") UUID viewerId,
                                           @Param("athleteIds") Collection<UUID> athleteIds,
                                           @Param("fromAdmin") boolean fromAdmin);

    /** Projection for the batched counts above. */
    interface UnreadCount {
        UUID getAthleteId();
        long getTotal();
    }

    /** Erasing one athlete's plan: both sides' read markers about that athlete go with it. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM TrainingCalendarRead r WHERE r.athleteId = :athleteId")
    int deleteAllForAthlete(@Param("athleteId") UUID athleteId);
}
