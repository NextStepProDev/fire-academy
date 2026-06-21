package pl.fireacademy.domain.training;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Oznaczenie opłaconego miesiąca dla subskrypcji treningowej.
 * Obecność rekordu = miesiąc opłacony (płatność u organizatora, ustawiana ręcznie przez admina/trenera).
 */
@Entity
@Table(name = "training_payments")
public class TrainingPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enrollment_id", nullable = false)
    private TrainingEnrollment enrollment;

    @Column(name = "year_month", nullable = false, length = 7)
    private String yearMonth;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TrainingPayment() {}

    public TrainingPayment(TrainingEnrollment enrollment, YearMonth yearMonth) {
        this.enrollment = enrollment;
        this.yearMonth = yearMonth.toString();
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public TrainingEnrollment getEnrollment() { return enrollment; }
    public YearMonth getYearMonth() { return YearMonth.parse(yearMonth); }
    public Instant getCreatedAt() { return createdAt; }
}
