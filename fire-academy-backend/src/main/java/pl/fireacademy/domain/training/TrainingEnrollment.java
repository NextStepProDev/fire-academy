package pl.fireacademy.domain.training;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import pl.fireacademy.domain.user.User;

import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Subskrypcja użytkownika na cykliczny slot treningowy.
 * Interwał miesięcy: od {@code startMonth} do {@code endMonth} (NULL = na czas nieokreślony).
 * Każdy zapisany jest domyślnie „stałym bywalcem" — bezterminowo trzyma miejsce, dopóki sam
 * nie zrezygnuje (od kolejnego miesiąca) lub trener go nie usunie.
 */
@Entity
@Table(name = "training_enrollments")
public class TrainingEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id", nullable = false)
    private TrainingSlot slot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "start_month", nullable = false, length = 7)
    private String startMonth;

    @Nullable
    @Column(name = "end_month", length = 7)
    private String endMonth;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expiry_notified", nullable = false)
    private boolean expiryNotified = false;

    protected TrainingEnrollment() {}

    public TrainingEnrollment(TrainingSlot slot, User user, YearMonth startMonth, @Nullable YearMonth endMonth) {
        this.slot = slot;
        this.user = user;
        this.startMonth = startMonth.toString();
        this.endMonth = endMonth != null ? endMonth.toString() : null;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    /** Czy subskrypcja obejmuje wskazany miesiąc. */
    public boolean covers(YearMonth month) {
        var m = month.toString();
        return startMonth.compareTo(m) <= 0 && (endMonth == null || endMonth.compareTo(m) >= 0);
    }

    public UUID getId() { return id; }
    public TrainingSlot getSlot() { return slot; }
    public User getUser() { return user; }
    public YearMonth getStartMonth() { return YearMonth.parse(startMonth); }
    @Nullable public YearMonth getEndMonth() { return endMonth != null ? YearMonth.parse(endMonth) : null; }
    public void setEndMonth(@Nullable YearMonth endMonth) { this.endMonth = endMonth != null ? endMonth.toString() : null; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isExpiryNotified() { return expiryNotified; }
    public void setExpiryNotified(boolean expiryNotified) { this.expiryNotified = expiryNotified; }
}
