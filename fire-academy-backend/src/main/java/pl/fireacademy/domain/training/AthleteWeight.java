package pl.fireacademy.domain.training;

import jakarta.persistence.*;
import pl.fireacademy.domain.user.User;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One morning weigh-in. At most one per day — weighing again is a correction, not a second point. */
@Entity
@Table(name = "athlete_weights")
public class AthleteWeight {

    public static final BigDecimal MIN_KG = new BigDecimal("20");
    public static final BigDecimal MAX_KG = new BigDecimal("300");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private User athlete;

    @Column(name = "measured_on", nullable = false)
    private LocalDate measuredOn;

    @Column(name = "weight_kg", nullable = false, precision = 5, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AthleteWeight() {}

    public AthleteWeight(User athlete, LocalDate measuredOn, BigDecimal weightKg) {
        this.athlete = athlete;
        this.measuredOn = measuredOn;
        this.weightKg = weightKg;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void correctTo(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public UUID getId() {
        return id;
    }

    public LocalDate getMeasuredOn() {
        return measuredOn;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }
}
