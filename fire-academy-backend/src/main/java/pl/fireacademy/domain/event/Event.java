package pl.fireacademy.domain.event;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_type_id", nullable = false)
    private EventType eventType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Nullable
    @Column(name = "end_date")
    private LocalDate endDate;

    @Nullable
    @Column(name = "start_time")
    private LocalTime startTime;

    @Nullable
    private String location;

    @Nullable
    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    @Nullable
    @Column(name = "max_participants")
    private Integer maxParticipants;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Event() {}

    public Event(EventType eventType, LocalDate startDate) {
        this.eventType = eventType;
        this.startDate = startDate;
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

    public BigDecimal getEffectivePrice() {
        return price != null ? price : eventType.getPrice();
    }

    public Integer getEffectiveMaxParticipants() {
        return maxParticipants != null ? maxParticipants : eventType.getMaxParticipants();
    }

    public UUID getId() { return id; }
    public EventType getEventType() { return eventType; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    @Nullable public LocalDate getEndDate() { return endDate; }
    public void setEndDate(@Nullable LocalDate endDate) { this.endDate = endDate; }
    @Nullable public LocalTime getStartTime() { return startTime; }
    public void setStartTime(@Nullable LocalTime startTime) { this.startTime = startTime; }
    @Nullable public String getLocation() { return location; }
    public void setLocation(@Nullable String location) { this.location = location; }
    @Nullable public BigDecimal getPrice() { return price; }
    public void setPrice(@Nullable BigDecimal price) { this.price = price; }
    @Nullable public Integer getMaxParticipants() { return maxParticipants; }
    public void setMaxParticipants(@Nullable Integer maxParticipants) { this.maxParticipants = maxParticipants; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
