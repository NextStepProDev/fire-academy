package pl.fireacademy.domain.training;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Marker of a paid month for a training subscription.
 * Presence of the record = the month is paid (payment to the organizer, set manually by the admin/instructor).
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

    /** Part of this paid month's bill covered by carried-over surplus (CREDITED refund), not fresh cash. */
    @Column(name = "credit_applied", nullable = false, precision = 10, scale = 2)
    private BigDecimal creditApplied = BigDecimal.ZERO;

    /**
     * NET amount (bill minus surplus credit) frozen when the month was marked paid, so displays never drift
     * with the passing days or later price edits. NULL only on legacy rows from before the column existed.
     */
    @org.jspecify.annotations.Nullable
    @Column(precision = 10, scale = 2)
    private BigDecimal amount;

    /**
     * How this payment was set. {@code true} = marked individually via the per-slot roster toggle (one
     * specific training) — it survives a whole-month revert and is cleared only by un-marking that training.
     * {@code false} = added by the whole-month "pay for the person" action (or a legacy row).
     */
    @Column(nullable = false)
    private boolean pinned = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TrainingPayment() {}

    public TrainingPayment(TrainingEnrollment enrollment, YearMonth yearMonth) {
        this.enrollment = enrollment;
        this.yearMonth = yearMonth.toString();
    }

    public TrainingPayment(TrainingEnrollment enrollment, YearMonth yearMonth, BigDecimal creditApplied,
                           @org.jspecify.annotations.Nullable BigDecimal amount, boolean pinned) {
        this.enrollment = enrollment;
        this.yearMonth = yearMonth.toString();
        this.creditApplied = creditApplied;
        this.amount = amount;
        this.pinned = pinned;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public TrainingEnrollment getEnrollment() { return enrollment; }
    public YearMonth getYearMonth() { return YearMonth.parse(yearMonth); }
    public BigDecimal getCreditApplied() { return creditApplied; }
    @org.jspecify.annotations.Nullable public BigDecimal getAmount() { return amount; }
    public boolean isPinned() { return pinned; }
    public Instant getCreatedAt() { return createdAt; }
}
