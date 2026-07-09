package pl.fireacademy.domain.training;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A cancelled individual session of a cyclical slot (e.g. the instructor is ill) — a specific occurrence date.
 * The slot stays active; only that specific session does not take place.
 */
@Entity
@Table(name = "training_cancelled_sessions")
public class TrainingCancelledSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id", nullable = false)
    private TrainingSlot slot;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TrainingCancelledSession() {}

    public TrainingCancelledSession(TrainingSlot slot, LocalDate sessionDate) {
        this.slot = slot;
        this.sessionDate = sessionDate;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public TrainingSlot getSlot() { return slot; }
    public LocalDate getSessionDate() { return sessionDate; }
    public Instant getCreatedAt() { return createdAt; }
}
