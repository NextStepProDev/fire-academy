package pl.fireacademy.domain.training;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import pl.fireacademy.domain.event.EventType;
import pl.fireacademy.domain.instructor.Instructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Cykliczny, bezterminowy slot tygodniowy treningu (np. poniedziałek 8:00–9:00).
 * Należy do rodzaju ({@link EventType}, kategoria TRAINING) i ma przypisanego jednego trenera.
 */
@Entity
@Table(name = "training_slots")
public class TrainingSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_type_id", nullable = false)
    private EventType eventType;

    @Nullable
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instructor_id")
    private Instructor instructor;

    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Nullable
    @Column(name = "end_time")
    private LocalTime endTime;

    @Nullable
    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "max_participants", nullable = false)
    private int maxParticipants;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Nullable
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Nullable
    @Column(name = "deactivated_from")
    private LocalDate deactivatedFrom;

    protected TrainingSlot() {}

    public TrainingSlot(EventType eventType, int dayOfWeek, LocalTime startTime, int maxParticipants) {
        this.eventType = eventType;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.maxParticipants = maxParticipants;
    }

    @PrePersist
    void onCreate() {
        var now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }
    @Nullable public Instructor getInstructor() { return instructor; }
    public void setInstructor(@Nullable Instructor instructor) { this.instructor = instructor; }
    public int getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(int dayOfWeek) { this.dayOfWeek = dayOfWeek; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    @Nullable public LocalTime getEndTime() { return endTime; }
    public void setEndTime(@Nullable LocalTime endTime) { this.endTime = endTime; }
    @Nullable public BigDecimal getPrice() { return price; }
    public void setPrice(@Nullable BigDecimal price) { this.price = price; }
    public int getMaxParticipants() { return maxParticipants; }
    public void setMaxParticipants(int maxParticipants) { this.maxParticipants = maxParticipants; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    @Nullable public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(@Nullable Instant deletedAt) { this.deletedAt = deletedAt; }
    public boolean isDeleted() { return deletedAt != null; }
    @Nullable public LocalDate getDeactivatedFrom() { return deactivatedFrom; }
    public void setDeactivatedFrom(@Nullable LocalDate deactivatedFrom) { this.deactivatedFrom = deactivatedFrom; }
}
