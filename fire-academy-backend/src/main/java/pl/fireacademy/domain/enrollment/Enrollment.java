package pl.fireacademy.domain.enrollment;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import pl.fireacademy.domain.event.Event;
import pl.fireacademy.domain.user.User;

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

    // Konto, z którego pochodzi zapis. Nullable wyłącznie po to, by usunięcie/anonimizacja konta
    // mogło je wyzerować (FK ON DELETE SET NULL) — w normalnym obrocie zawsze ustawione.
    // Dane osobowe poniżej to snapshot z chwili zapisu — służy wyłącznie jako fallback dla
    // czytelności rostera po usunięciu konta. Bieżące widoki czytają dane przez {@code display*()},
    // które preferują żywe konto (źródło prawdy PII = users), więc zmiana danych w profilu jest widoczna.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @Nullable
    private User user;

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

    /**
     * Tworzy zapis powiązany z kontem. Dane osobowe są kopiowane z konta jako snapshot
     * (źródło prawdy PII pozostaje w {@code users}; snapshot służy czytelności rostera po usunięciu konta).
     */
    public static Enrollment forUser(Event event, User user, @Nullable String note, boolean addedByAdmin) {
        Enrollment e = new Enrollment(event, user.getFirstName(), user.getLastName(),
                user.getEmail(), user.getPhone(), note, addedByAdmin);
        e.user = user;
        return e;
    }

    /** Pusta/biała notatka → {@code null} (jedna reguła dla zapisu usera i admina). */
    public static @Nullable String normalizeNote(@Nullable String note) {
        return (note == null || note.isBlank()) ? null : note;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public void anonymize() {
        this.firstName = "Dane";
        this.lastName = "usunięte";
        this.email = "anonimowy-" + id + "@usuniety.rodo";
        this.phone = null;
        this.note = null;
        this.user = null;
    }

    public boolean isAnonymized() {
        return email != null && email.endsWith("@usuniety.rodo");
    }

    // Dane do wyświetlenia/wysyłki: żywe konto, gdy istnieje; po usunięciu konta — snapshot (zanonimizowany).
    public String displayFirstName() { return user != null ? user.getFirstName() : firstName; }
    public String displayLastName() { return user != null ? user.getLastName() : lastName; }
    public String displayEmail() { return user != null ? user.getEmail() : email; }
    @Nullable public String displayPhone() { return user != null ? user.getPhone() : phone; }

    public UUID getId() { return id; }
    public Event getEvent() { return event; }
    @Nullable public User getUser() { return user; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    @Nullable public String getPhone() { return phone; }
    @Nullable public String getNote() { return note; }
    public boolean isAddedByAdmin() { return addedByAdmin; }
    public Instant getCreatedAt() { return createdAt; }
}
