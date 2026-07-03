package pl.fireacademy.domain.training;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * A refund owed to a subscriber for a single paid session that was later cancelled (a day off or a
 * single-session cancellation). Created when the cancellation lands in a month the subscriber has already
 * paid for; removed if the cancellation is undone before settlement. {@code refundedAt} = money handed back.
 */
@Entity
@Table(name = "training_refunds")
public class TrainingRefund {

    /** Reason a session was cancelled. */
    public static final String TYPE_HOLIDAY = "HOLIDAY";
    public static final String TYPE_SESSION = "SESSION";

    /** How a refund was resolved. */
    public static final String SETTLEMENT_REFUNDED = "REFUNDED";   // money handed back
    public static final String SETTLEMENT_CREDITED = "CREDITED";   // counted toward this/next month

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enrollment_id", nullable = false)
    private TrainingEnrollment enrollment;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "year_month", nullable = false, length = 7)
    private String yearMonth;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 20)
    private String type;

    @Nullable
    @Column(length = 120)
    private String label;

    @Nullable
    @Column(name = "settled_at")
    private Instant settledAt;

    @Nullable
    @Column(name = "settlement_type", length = 20)
    private String settlementType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TrainingRefund() {}

    public TrainingRefund(TrainingEnrollment enrollment, LocalDate sessionDate, BigDecimal amount,
                          String type, @Nullable String label) {
        this.enrollment = enrollment;
        this.sessionDate = sessionDate;
        this.yearMonth = YearMonth.from(sessionDate).toString();
        this.amount = amount;
        this.type = type;
        this.label = label;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public TrainingEnrollment getEnrollment() { return enrollment; }
    public LocalDate getSessionDate() { return sessionDate; }
    public YearMonth getYearMonth() { return YearMonth.parse(yearMonth); }
    public BigDecimal getAmount() { return amount; }
    public String getType() { return type; }
    @Nullable public String getLabel() { return label; }
    @Nullable public Instant getSettledAt() { return settledAt; }
    public void setSettledAt(@Nullable Instant settledAt) { this.settledAt = settledAt; }
    @Nullable public String getSettlementType() { return settlementType; }
    public void setSettlementType(@Nullable String settlementType) { this.settlementType = settlementType; }
    public Instant getCreatedAt() { return createdAt; }
}
