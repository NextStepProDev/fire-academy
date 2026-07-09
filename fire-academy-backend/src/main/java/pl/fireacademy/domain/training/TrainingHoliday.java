package pl.fireacademy.domain.training;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A whole-club closure day (day off) for the TRAINING category — no slot takes place on this date.
 * Reduces the billable session count for every slot whose weekday matches the date.
 */
@Entity
@Table(name = "training_holidays")
public class TrainingHoliday {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Nullable
    @Column(length = 120)
    private String label;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TrainingHoliday() {}

    public TrainingHoliday(LocalDate holidayDate, @Nullable String label) {
        this.holidayDate = holidayDate;
        this.label = label;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public LocalDate getHolidayDate() { return holidayDate; }
    @Nullable public String getLabel() { return label; }
    public Instant getCreatedAt() { return createdAt; }
}
