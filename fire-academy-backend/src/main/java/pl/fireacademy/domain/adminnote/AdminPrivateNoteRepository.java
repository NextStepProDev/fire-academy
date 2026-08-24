package pl.fireacademy.domain.adminnote;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Every read and every delete is narrowed to (author, target).
 * <p>
 * A note is never addressed by its own id, so there is no branch in which somebody could forget to
 * compare the author. That is the whole access-control story for this table, and it is structural
 * rather than remembered.
 */
public interface AdminPrivateNoteRepository extends JpaRepository<AdminPrivateNote, UUID> {

    // --- reads -----------------------------------------------------------------------------------

    @Query("SELECT n FROM AdminPrivateNote n WHERE n.author.id = :authorId AND n.training.id = :trainingId")
    Optional<AdminPrivateNote> findForTraining(@Param("authorId") UUID authorId,
                                               @Param("trainingId") UUID trainingId);

    @Query("SELECT n FROM AdminPrivateNote n WHERE n.author.id = :authorId AND n.event.id = :eventId")
    Optional<AdminPrivateNote> findForEvent(@Param("authorId") UUID authorId,
                                            @Param("eventId") UUID eventId);

    @Query("SELECT n FROM AdminPrivateNote n WHERE n.author.id = :authorId AND n.slot.id = :slotId"
        + " AND n.sessionDate IS NULL")
    Optional<AdminPrivateNote> findForSlot(@Param("authorId") UUID authorId,
                                           @Param("slotId") UUID slotId);

    @Query("SELECT n FROM AdminPrivateNote n WHERE n.author.id = :authorId AND n.athlete.id = :athleteId"
        + " AND n.slot.id = :slotId AND n.sessionDate = :date")
    Optional<AdminPrivateNote> findForSession(@Param("authorId") UUID authorId,
                                              @Param("athleteId") UUID athleteId,
                                              @Param("slotId") UUID slotId,
                                              @Param("date") LocalDate date);

    // --- writes ----------------------------------------------------------------------------------
    //
    // Four twin upserts rather than one, which is the price of four real foreign keys instead of a
    // (target_type, target_id) pair -- and the reason no row survives the thing it describes.
    //
    // Not read-then-save: a second tab or a double tap would both find nothing, both insert, and the
    // partial unique would refuse the second -- a 500 for somebody who pressed the button twice.
    // Letting the database settle the tie makes the second write a correction, which is what a
    // second note has always meant here.
    //
    // `updatedAt` arrives as an argument rather than SQL now(): now() is the transaction start time,
    // which can predate an edit made moments earlier. `clearAutomatically` matters just as much --
    // without it Hibernate keeps serving the pre-save row from its identity map for the rest of the
    // transaction, and the caller reads back what it just overwrote.
    //
    // Each ON CONFLICT repeats its index predicate verbatim; that is what lets Postgres infer the
    // partial index instead of refusing the statement.

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        INSERT INTO admin_private_notes (author_id, training_id, body, updated_at)
        VALUES (:authorId, :trainingId, :body, :updatedAt)
        ON CONFLICT (author_id, training_id) WHERE training_id IS NOT NULL
        DO UPDATE SET body = :body, updated_at = :updatedAt
        """, nativeQuery = true)
    void upsertForTraining(@Param("authorId") UUID authorId,
                           @Param("trainingId") UUID trainingId,
                           @Param("body") String body,
                           @Param("updatedAt") Instant updatedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        INSERT INTO admin_private_notes (author_id, event_id, body, updated_at)
        VALUES (:authorId, :eventId, :body, :updatedAt)
        ON CONFLICT (author_id, event_id) WHERE event_id IS NOT NULL
        DO UPDATE SET body = :body, updated_at = :updatedAt
        """, nativeQuery = true)
    void upsertForEvent(@Param("authorId") UUID authorId,
                        @Param("eventId") UUID eventId,
                        @Param("body") String body,
                        @Param("updatedAt") Instant updatedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        INSERT INTO admin_private_notes (author_id, slot_id, body, updated_at)
        VALUES (:authorId, :slotId, :body, :updatedAt)
        ON CONFLICT (author_id, slot_id) WHERE slot_id IS NOT NULL AND session_date IS NULL
        DO UPDATE SET body = :body, updated_at = :updatedAt
        """, nativeQuery = true)
    void upsertForSlot(@Param("authorId") UUID authorId,
                       @Param("slotId") UUID slotId,
                       @Param("body") String body,
                       @Param("updatedAt") Instant updatedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        INSERT INTO admin_private_notes (author_id, athlete_id, slot_id, session_date, body, updated_at)
        VALUES (:authorId, :athleteId, :slotId, :date, :body, :updatedAt)
        ON CONFLICT (author_id, athlete_id, slot_id, session_date) WHERE session_date IS NOT NULL
        DO UPDATE SET body = :body, updated_at = :updatedAt
        """, nativeQuery = true)
    void upsertForSession(@Param("authorId") UUID authorId,
                          @Param("athleteId") UUID athleteId,
                          @Param("slotId") UUID slotId,
                          @Param("date") LocalDate date,
                          @Param("body") String body,
                          @Param("updatedAt") Instant updatedAt);

    // --- deletes ---------------------------------------------------------------------------------
    //
    // Deliberately NOT behind the athlete gate the reads and writes sit behind. Removing your own
    // text cannot leak anything, and a delete narrowed to (author, target) can only ever reach the
    // row the caller wrote. In the sibling app the symmetric version trapped data: clearing
    // somebody's athlete flag made notes about their trainings invisible AND undeletable at once,
    // leaving another person's data in the database with no way to remove it -- the exact opposite
    // of what the gate is for.

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM AdminPrivateNote n WHERE n.author.id = :authorId AND n.training.id = :trainingId")
    int deleteForTraining(@Param("authorId") UUID authorId, @Param("trainingId") UUID trainingId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM AdminPrivateNote n WHERE n.author.id = :authorId AND n.event.id = :eventId")
    int deleteForEvent(@Param("authorId") UUID authorId, @Param("eventId") UUID eventId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM AdminPrivateNote n WHERE n.author.id = :authorId AND n.slot.id = :slotId"
        + " AND n.sessionDate IS NULL")
    int deleteForSlot(@Param("authorId") UUID authorId, @Param("slotId") UUID slotId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM AdminPrivateNote n WHERE n.author.id = :authorId AND n.athlete.id = :athleteId"
        + " AND n.slot.id = :slotId AND n.sessionDate = :date")
    int deleteForSession(@Param("authorId") UUID authorId, @Param("athleteId") UUID athleteId,
                         @Param("slotId") UUID slotId, @Param("date") LocalDate date);

    /**
     * Erasing one athlete's plan: every note ANY author wrote about that person's group sessions.
     * <p>
     * Not scoped to one author, because this is erasure of the subject's data rather than tidying of
     * one owner's notebook. Notes anchored to a training need no statement here — they leave through
     * the cascade when the trainings go. Notes about a SLOT or a TERM are untouched: those are about
     * the business, not about a person.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM AdminPrivateNote n WHERE n.athlete.id = :athleteId")
    int deleteAllAboutAthlete(@Param("athleteId") UUID athleteId);

    /**
     * The caller's OWN notes about that athlete, deleted first so the count can be reported back.
     * <p>
     * Erasure removes everybody's, but the number that reaches the response must describe only what
     * the caller wrote — otherwise the reply quietly announces that another admin keeps notes about
     * this person, and how many.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM AdminPrivateNote n WHERE n.athlete.id = :athleteId AND n.author.id = :authorId")
    int deleteAboutAthleteByAuthor(@Param("athleteId") UUID athleteId, @Param("authorId") UUID authorId);

    /**
     * How many of ONE author's notes hang off that athlete's trainings.
     * <p>
     * Counted rather than deleted: those rows leave on their own when the trainings do, through the
     * foreign key's cascade. But a count is not optional — erasure reports back how many of the
     * caller's notes went, and reading only the session-anchored rows made that number describe a
     * fraction of what actually disappeared. Must be asked BEFORE the trainings are deleted.
     */
    @Query("SELECT COUNT(n) FROM AdminPrivateNote n JOIN n.training t"
        + " WHERE n.author.id = :authorId AND t.athlete.id = :athleteId")
    int countAboutAthleteTrainingsByAuthor(@Param("athleteId") UUID athleteId,
                                           @Param("authorId") UUID authorId);

    // --- markers ---------------------------------------------------------------------------------
    //
    // Identifiers only, never the text. A month at a time answers "is there a note here" and nothing
    // else; handing back the notes themselves would drop the notebook into the response that exists
    // to draw icons, undoing the reason a note has its own endpoint per target.

    @Query("SELECT n.slot.id FROM AdminPrivateNote n WHERE n.author.id = :authorId"
        + " AND n.slot.id IS NOT NULL AND n.sessionDate IS NULL")
    List<UUID> markedSlotIds(@Param("authorId") UUID authorId);

    /** An event is a span, so "in range" means OVERLAP, not containment. */
    @Query("SELECT n.event.id FROM AdminPrivateNote n JOIN n.event e WHERE n.author.id = :authorId"
        + " AND e.startDate <= :to AND COALESCE(e.endDate, e.startDate) >= :from")
    List<UUID> markedEventIds(@Param("authorId") UUID authorId,
                              @Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Every noted event, unfiltered.
     * <p>
     * Unbounded by row count, and that is safe for a reason worth writing down rather than assuming:
     * it returns identifiers for ONE author, and an author cannot have more notes than there are
     * events — a number the club itself sets. If terms ever run to thousands, page this.
     * <p>
     * The admin tabs need this rather than the ranged version: the events list runs to whatever is
     * scheduled and the archive runs backwards forever, so no window of a sane width covers either.
     * It is safe where a general "fetch the notebook" query would not be, because it returns
     * identifiers for ONE author and no text at all.
     */
    @Query("SELECT n.event.id FROM AdminPrivateNote n WHERE n.author.id = :authorId AND n.event.id IS NOT NULL")
    List<UUID> markedEventIds(@Param("authorId") UUID authorId);

    @Query("SELECT n.training.id FROM AdminPrivateNote n JOIN n.training t WHERE n.author.id = :authorId"
        + " AND t.athlete.id = :athleteId AND t.date BETWEEN :from AND :to")
    List<UUID> markedTrainingIds(@Param("authorId") UUID authorId, @Param("athleteId") UUID athleteId,
                                 @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
        SELECT new pl.fireacademy.domain.adminnote.SessionMarker(n.slot.id, n.sessionDate)
        FROM AdminPrivateNote n
        WHERE n.author.id = :authorId AND n.athlete.id = :athleteId
          AND n.sessionDate BETWEEN :from AND :to
        """)
    List<SessionMarker> markedSessions(@Param("authorId") UUID authorId, @Param("athleteId") UUID athleteId,
                                       @Param("from") LocalDate from, @Param("to") LocalDate to);
}
