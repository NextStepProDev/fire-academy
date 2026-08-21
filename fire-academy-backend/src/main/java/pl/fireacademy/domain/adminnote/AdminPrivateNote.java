package pl.fireacademy.domain.adminnote;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import pl.fireacademy.domain.event.Event;
import pl.fireacademy.domain.training.PersonalTraining;
import pl.fireacademy.domain.training.TrainingSlot;
import pl.fireacademy.domain.user.User;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One private note, written by the owner about one session, readable by nobody else.
 *
 * <h2>Why this type is fenced off</h2>
 * The risk this feature carries is not a missing role check on the controller -- it is an obliging
 * field. The shapes describing a session are shared: {@code CalendarRangeResponse} is one record
 * served to the coach and to the client, and the public calendar DTOs are cached. A note field
 * added to any of them would compile, would look like a convenience, and would publish the
 * notebook to the very people it is written about. So the type is unreachable outside
 * {@code domain.adminnote} and {@code api.admin.note}, enforced by
 * {@code architecture/AdminNoteIsolationArchTest}: a service that cannot read a note cannot leak
 * one, and privacy stops depending on anybody remembering.
 *
 * <h2>Four anchors, three columns</h2>
 * {@code slot_id} carries two of them, told apart by {@code session_date}: set, it is one dated
 * occurrence inside one person's calendar; null, it is the weekly slot as a whole. A group session
 * has no row anywhere -- {@code RecurringSessionOverlayService} computes it on every read and that
 * is a standing decision -- so it is addressed by (athlete, slot, date) rather than by an id.
 *
 * <h2>No optimistic locking, deliberately</h2>
 * The rest of the module version-checks a shared row ({@link PersonalTraining}) because two people
 * genuinely edit it at once. A note has ONE author, so the only collision possible is that author's
 * own second tab, and the rule this table already runs on — a second write is a correction, not a
 * second note — gives that the same answer. Adding {@code @Version} would buy a conflict dialog for
 * a conflict with yourself.
 *
 * <h2>A session note can go quiet, and that is accepted</h2>
 * Declaring a day off, cancelling the session or ending the subscription makes the tile disappear
 * while the note stays. It is not deleted: a cancellation can be undone
 * ({@code isSessionRestorable}), so deleting here would destroy the coach's text over a state that
 * reverts. The note comes back with the session and dies with the slot or the account.
 */
@Entity
@Table(name = "admin_private_notes")
public class AdminPrivateNote {

    public static final int MAX_BODY_LENGTH = 4000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Nullable
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "training_id")
    private PersonalTraining training;

    @Nullable
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private Event event;

    @Nullable
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id")
    private TrainingSlot slot;

    @Nullable
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "athlete_id")
    private User athlete;

    @Nullable
    @Column(name = "session_date")
    private LocalDate sessionDate;

    @Column(nullable = false, length = MAX_BODY_LENGTH)
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AdminPrivateNote() {
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }

    public User getAuthor() { return author; }

    public @Nullable PersonalTraining getTraining() { return training; }

    public @Nullable Event getEvent() { return event; }

    public @Nullable TrainingSlot getSlot() { return slot; }

    public @Nullable User getAthlete() { return athlete; }

    public @Nullable LocalDate getSessionDate() { return sessionDate; }

    public String getBody() { return body; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
}
