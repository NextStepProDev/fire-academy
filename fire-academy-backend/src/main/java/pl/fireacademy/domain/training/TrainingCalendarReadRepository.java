package pl.fireacademy.domain.training;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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
}
