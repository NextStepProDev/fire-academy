package pl.fireacademy.domain.instructor;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import pl.fireacademy.domain.event.EventCategory;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "instructors")
public class Instructor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Nullable
    @Column(columnDefinition = "TEXT")
    private String bio;

    @Nullable
    @Column(name = "photo_filename")
    private String photoFilename;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "instructor_categories", joinColumns = @JoinColumn(name = "instructor_id"))
    @Column(name = "category")
    @Enumerated(EnumType.STRING)
    private Set<EventCategory> categories = new HashSet<>();

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Instructor() {}

    public Instructor(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
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
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    @Nullable public String getBio() { return bio; }
    public void setBio(@Nullable String bio) { this.bio = bio; }
    @Nullable public String getPhotoFilename() { return photoFilename; }
    public void setPhotoFilename(@Nullable String photoFilename) { this.photoFilename = photoFilename; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public Set<EventCategory> getCategories() { return categories; }
    public void setCategories(Set<EventCategory> categories) { this.categories = categories; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
