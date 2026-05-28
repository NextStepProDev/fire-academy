package pl.fireacademy.domain.event;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "event_types")
public class EventType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventCategory category;

    @Column(nullable = false)
    private String name;

    @Nullable
    @Column(columnDefinition = "TEXT")
    private String description;

    @Nullable
    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    @Nullable
    @Column(name = "max_participants")
    private Integer maxParticipants;

    @Nullable
    @Column(length = 100)
    private String duration;

    @Nullable
    @Column(name = "thumbnail_filename")
    private String thumbnailFilename;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "eventType", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<EventTypePhoto> photos = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EventType() {}

    public EventType(EventCategory category, String name) {
        this.category = category;
        this.name = name;
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
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    @Nullable public String getDescription() { return description; }
    public void setDescription(@Nullable String description) { this.description = description; }
    @Nullable public BigDecimal getPrice() { return price; }
    public void setPrice(@Nullable BigDecimal price) { this.price = price; }
    @Nullable public Integer getMaxParticipants() { return maxParticipants; }
    public void setMaxParticipants(@Nullable Integer maxParticipants) { this.maxParticipants = maxParticipants; }
    @Nullable public String getDuration() { return duration; }
    public void setDuration(@Nullable String duration) { this.duration = duration; }
    @Nullable public String getThumbnailFilename() { return thumbnailFilename; }
    public void setThumbnailFilename(@Nullable String thumbnailFilename) { this.thumbnailFilename = thumbnailFilename; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public List<EventTypePhoto> getPhotos() { return photos; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
