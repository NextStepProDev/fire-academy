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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventCategory category;

    @Nullable
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_type_id")
    private EventType eventType;

    @Nullable
    @Column(name = "custom_name")
    private String customName;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Nullable
    @Column(name = "end_date")
    private LocalDate endDate;

    @Nullable
    @Column(name = "start_time")
    private LocalTime startTime;

    @Nullable
    @Column(name = "end_time")
    private LocalTime endTime;

    @Nullable
    @Column(columnDefinition = "TEXT")
    private String description;

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

    public Event(EventCategory category, EventType eventType, LocalDate startDate) {
        this.category = category;
        this.eventType = eventType;
        this.startDate = startDate;
    }

    public Event(EventCategory category, String customName, LocalDate startDate) {
        this.category = category;
        this.customName = customName;
        this.startDate = startDate;
    }

    public void convertToCustomName(String name) {
        this.customName = name;
        this.eventType = null;
    }

    public String getDisplayName() {
        return eventType != null ? eventType.getName() : customName;
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
    public EventCategory getCategory() { return category; }
    @Nullable public EventType getEventType() { return eventType; }
    @Nullable public String getCustomName() { return customName; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    @Nullable public LocalDate getEndDate() { return endDate; }
    public void setEndDate(@Nullable LocalDate endDate) { this.endDate = endDate; }
    @Nullable public LocalTime getStartTime() { return startTime; }
    public void setStartTime(@Nullable LocalTime startTime) { this.startTime = startTime; }
    @Nullable public LocalTime getEndTime() { return endTime; }
    public void setEndTime(@Nullable LocalTime endTime) { this.endTime = endTime; }
    @Nullable public String getDescription() { return description; }
    public void setDescription(@Nullable String description) { this.description = description; }
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
