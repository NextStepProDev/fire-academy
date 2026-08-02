package pl.fireacademy.domain.training;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * "Last time this viewer looked at this calendar."
 * <p>
 * The client is the row where {@code userId == athleteId}; a coach gets one row per client, so two
 * admins keep independent counters. A missing row means EPOCH — count everything, which is the right
 * answer for a calendar nobody has opened yet.
 */
@Entity
@Table(name = "training_calendar_reads")
@IdClass(TrainingCalendarRead.Key.class)
public class TrainingCalendarRead {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "athlete_id", nullable = false)
    private UUID athleteId;

    @Column(name = "seen_at", nullable = false)
    private Instant seenAt;

    protected TrainingCalendarRead() {}

    public UUID getUserId() {
        return userId;
    }

    public UUID getAthleteId() {
        return athleteId;
    }

    public Instant getSeenAt() {
        return seenAt;
    }

    public static class Key implements Serializable {
        private UUID userId;
        private UUID athleteId;

        public Key() {}

        public Key(UUID userId, UUID athleteId) {
            this.userId = userId;
            this.athleteId = athleteId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return Objects.equals(userId, key.userId) && Objects.equals(athleteId, key.athleteId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, athleteId);
        }
    }
}
