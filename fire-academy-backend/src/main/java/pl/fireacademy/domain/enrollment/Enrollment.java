package pl.fireacademy.domain.enrollment;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import pl.fireacademy.domain.event.Event;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "enrollments")
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false)
    private String email;

    @Nullable
    @Column(length = 20)
    private String phone;

    @Nullable
    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "added_by_admin", nullable = false)
    private boolean addedByAdmin;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Enrollment() {}

    public Enrollment(Event event, String firstName, String lastName, String email, @Nullable String phone,
                      @Nullable String note, boolean addedByAdmin) {
        this.event = event;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.note = note;
        this.addedByAdmin = addedByAdmin;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public void anonymize() {
        this.firstName = "Dane";
        this.lastName = "usunięte";
        this.email = "anonimowy-" + id + "@usuniety.rodo";
        this.phone = "000000000";
        this.note = null;
    }

    public boolean isAnonymized() {
        return email != null && email.endsWith("@usuniety.rodo");
    }

    public UUID getId() { return id; }
    public Event getEvent() { return event; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    @Nullable public String getPhone() { return phone; }
    @Nullable public String getNote() { return note; }
    public boolean isAddedByAdmin() { return addedByAdmin; }
    public Instant getCreatedAt() { return createdAt; }
}
