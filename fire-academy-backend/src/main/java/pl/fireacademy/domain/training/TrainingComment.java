package pl.fireacademy.domain.training;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import pl.fireacademy.domain.user.User;

import java.time.Instant;
import java.util.UUID;

/** One message in the conversation attached to a training. */
@Entity
@Table(name = "training_comments")
public class TrainingComment {

    public static final int MAX_BODY_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "training_id", nullable = false)
    private PersonalTraining training;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    @Nullable
    private User author;

    /**
     * The author's role frozen at the time of writing. Reading it off the user today would relabel a
     * client's old comments the day they are promoted, and flip everyone's unread dots with them.
     */
    @Column(name = "author_is_admin", nullable = false)
    private boolean authorIsAdmin;

    @Column(nullable = false, length = MAX_BODY_LENGTH)
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TrainingComment() {}

    public TrainingComment(PersonalTraining training, User author, boolean authorIsAdmin, String body) {
        this.training = training;
        this.author = author;
        this.authorIsAdmin = authorIsAdmin;
        this.body = body;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public PersonalTraining getTraining() {
        return training;
    }

    @Nullable
    public User getAuthor() {
        return author;
    }

    public boolean isAuthorIsAdmin() {
        return authorIsAdmin;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
