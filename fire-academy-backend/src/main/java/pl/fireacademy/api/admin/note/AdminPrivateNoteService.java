package pl.fireacademy.api.admin.note;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.fireacademy.api.NotFoundException;
import pl.fireacademy.api.admin.note.NoteDtos.NoteMarkersResponse;
import pl.fireacademy.api.admin.note.NoteDtos.NoteResponse;
import pl.fireacademy.api.admin.note.NoteDtos.SessionMarkerResponse;
import pl.fireacademy.api.trainingcalendar.TrainingAccessService;
import pl.fireacademy.domain.adminnote.AdminPrivateNote;
import pl.fireacademy.domain.adminnote.AdminPrivateNoteRepository;
import pl.fireacademy.domain.event.EventRepository;
import pl.fireacademy.domain.training.TrainingSlot;
import pl.fireacademy.domain.training.TrainingSlotRepository;
import pl.fireacademy.infrastructure.i18n.MessageService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The owner's private notebook.
 *
 * <p>One code path for four targets. The alternative -- four families of endpoints -- fails in these
 * projects the same way every time: a fix lands in one copy of the twins and not the others.
 */
@Service
public class AdminPrivateNoteService {

    /** Same ceiling as the calendar page, and the same message when it is exceeded. */
    private static final int MAX_MARKER_RANGE_DAYS = 62;

    private final AdminPrivateNoteRepository notes;
    private final TrainingAccessService access;
    private final EventRepository events;
    private final TrainingSlotRepository slots;
    private final MessageService msg;

    public AdminPrivateNoteService(AdminPrivateNoteRepository notes,
                                   TrainingAccessService access,
                                   EventRepository events,
                                   TrainingSlotRepository slots,
                                   MessageService msg) {
        this.notes = notes;
        this.access = access;
        this.events = events;
        this.slots = slots;
        this.msg = msg;
    }

    /**
     * Where a note may be pinned, once the caller has been allowed to go there.
     *
     * <p>Everything the URL carries is validated here and nowhere else, so a new operation cannot
     * accidentally skip a gate: the target must exist, and a target naming a person goes through the
     * same 404-never-403 gate as the rest of the panel.
     */
    private record Anchor(NoteTarget target, UUID id, @Nullable UUID athleteId, @Nullable LocalDate date) {}

    private Anchor resolveTarget(NoteTarget target, UUID id,
                                 @Nullable UUID athleteId, @Nullable LocalDate date, UUID adminId) {
        switch (target) {
            case TRAINING -> access.requireTraining(id, adminId, true);
            case EVENT -> {
                if (!events.existsById(id)) {
                    throw new NotFoundException(msg.get("event.not.found"));
                }
            }
            case SLOT -> requireLiveSlot(id);
            case SESSION -> {
                if (athleteId == null || date == null) {
                    throw new IllegalArgumentException(msg.get("adminnote.session.incomplete"));
                }
                access.requireAthlete(athleteId);
                requireLiveSlot(id);
                // Deliberately NOT checking that a session actually falls on that date for that
                // person. Answering it means re-running the billing engine — subscription window,
                // club holidays, cancellations, mid-month deactivation — and a note would then start
                // depending on rules that legitimately change afterwards: declare a day off and
                // yesterday's note becomes unwritable. The worst case here is a row that never
                // renders, which is cheap; coupling the notebook to the billing rules is not.
            }
        }
        return new Anchor(target, id, athleteId, date);
    }

    private void requireLiveSlot(UUID slotId) {
        TrainingSlot slot = slots.findById(slotId)
            .orElseThrow(() -> new NotFoundException(msg.get("trainingslot.not.found")));
        if (slot.isDeleted()) {
            throw new NotFoundException(msg.get("trainingslot.not.found"));
        }
    }

    @Transactional(readOnly = true)
    public NoteResponse get(NoteTarget target, UUID id,
                            @Nullable UUID athleteId, @Nullable LocalDate date, UUID adminId) {
        Anchor anchor = resolveTarget(target, id, athleteId, date, adminId);
        return find(anchor, adminId)
            .map(note -> new NoteResponse(note.getBody(), note.getUpdatedAt()))
            .orElse(NoteResponse.EMPTY);
    }

    @Transactional
    public void save(NoteTarget target, UUID id,
                     @Nullable UUID athleteId, @Nullable LocalDate date, UUID adminId, String rawBody) {
        Anchor anchor = resolveTarget(target, id, athleteId, date, adminId);
        String body = sanitize(rawBody);
        Instant now = Instant.now();

        switch (anchor.target()) {
            case TRAINING -> notes.upsertForTraining(adminId, anchor.id(), body, now);
            case EVENT -> notes.upsertForEvent(adminId, anchor.id(), body, now);
            case SLOT -> notes.upsertForSlot(adminId, anchor.id(), body, now);
            case SESSION -> notes.upsertForSession(
                adminId, requireAthleteId(anchor), anchor.id(), requireDate(anchor), body, now);
        }
    }

    /**
     * Deliberately does NOT call {@link #resolveTarget}.
     *
     * <p>Reads and writes are gated; deleting is not, and that is a correction rather than an
     * oversight. Removing your own text cannot expose anything, and a query narrowed to
     * (author, target) can only reach the row the caller wrote. Giving all three operations the same
     * guard looks safe and is not: in the sibling app, clearing somebody's athlete flag made the
     * notes about their trainings invisible AND undeletable at once, because the coach's calendar is
     * unreachable without the flag and the gate refused the one operation that could have tidied
     * them away. Another person's data, stranded with no route to erasure -- the precise opposite of
     * what the gate stands there for.
     */
    @Transactional
    public void delete(NoteTarget target, UUID id,
                       @Nullable UUID athleteId, @Nullable LocalDate date, UUID adminId) {
        switch (target) {
            case TRAINING -> notes.deleteForTraining(adminId, id);
            case EVENT -> notes.deleteForEvent(adminId, id);
            case SLOT -> notes.deleteForSlot(adminId, id);
            case SESSION -> {
                if (athleteId == null || date == null) {
                    throw new IllegalArgumentException(msg.get("adminnote.session.incomplete"));
                }
                notes.deleteForSession(adminId, athleteId, id, date);
            }
        }
    }

    /**
     * Removes every note about one athlete, as part of erasing their 1-on-1 plan.
     * <p>
     * This is the seam other packages use: the entity and its repository stay unreachable outside
     * this package (see the isolation gate), so erasure asks the notebook to empty itself rather than
     * reaching in. Notes on a SLOT or a TERM survive — they are observations about the business, not
     * about the person.
     *
     * <p>
     * Must run BEFORE the athlete's trainings are deleted. Notes anchored to a training need no
     * statement here — they leave through the cascade when the trainings go — but they still have to
     * be COUNTED, and once the trainings are gone there is nothing left to count them from.
     *
     * @return how many of the caller's OWN notes went, both kinds. Everybody's are deleted — erasure
     *         of a person's data cannot be selective — but the count must not describe somebody
     *         else's notebook, or the reply becomes a way to discover that one exists.
     */
    @Transactional
    public int purgeForAthlete(UUID athleteId, UUID actingAdminId) {
        int minePerTraining = notes.countAboutAthleteTrainingsByAuthor(athleteId, actingAdminId);
        int minePerSession = notes.deleteAboutAthleteByAuthor(athleteId, actingAdminId);
        notes.deleteAllAboutAthlete(athleteId);
        return minePerTraining + minePerSession;
    }

    @Transactional(readOnly = true)
    public NoteMarkersResponse markers(UUID adminId, @Nullable LocalDate from, @Nullable LocalDate to,
                                       @Nullable UUID athleteId) {
        boolean ranged = from != null && to != null;
        if (ranged) {
            validateRange(from, to);
        } else if (athleteId != null) {
            // A calendar always knows its own window; asking per athlete without one would mean
            // scanning that person's whole history to draw a fortnight of icons.
            throw new IllegalArgumentException(msg.get("personaltraining.range.invalid"));
        }

        List<UUID> trainingIds = List.of();
        List<SessionMarkerResponse> sessions = List.of();
        if (athleteId != null) {
            access.requireAthlete(athleteId);
            trainingIds = notes.markedTrainingIds(adminId, athleteId, from, to);
            sessions = notes.markedSessions(adminId, athleteId, from, to).stream()
                .map(marker -> new SessionMarkerResponse(marker.slotId(), marker.date()))
                .toList();
        }
        // Slots carry no date, so they are never range-filtered; the set is small by construction.
        List<UUID> eventIds = ranged
            ? notes.markedEventIds(adminId, from, to)
            : notes.markedEventIds(adminId);
        return new NoteMarkersResponse(notes.markedSlotIds(adminId), eventIds, trainingIds, sessions);
    }

    private Optional<AdminPrivateNote> find(Anchor anchor, UUID adminId) {
        return switch (anchor.target()) {
            case TRAINING -> notes.findForTraining(adminId, anchor.id());
            case EVENT -> notes.findForEvent(adminId, anchor.id());
            case SLOT -> notes.findForSlot(adminId, anchor.id());
            case SESSION -> notes.findForSession(
                adminId, requireAthleteId(anchor), anchor.id(), requireDate(anchor));
        };
    }

    /**
     * Trim, and deliberately no HTML escaping.
     *
     * <p>Escaping on the way in turns the author's own quotes and apostrophes into entities, and then
     * every reader needs to undo that at render time. This text never reaches an email or an
     * {@code innerHTML}; its only author and its only reader are the same person.
     *
     * <p>Length is NOT clipped here. {@code @Size} on the request already answers an over-long note
     * with a 400, and silently truncating would be a second, different answer to the same question —
     * the one that loses the end of somebody's paragraph without saying so.
     */
    private String sanitize(@Nullable String rawBody) {
        String body = rawBody == null ? "" : rawBody.trim();
        if (body.isEmpty()) {
            throw new IllegalArgumentException(msg.get("adminnote.body.empty"));
        }
        return body;
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException(msg.get("personaltraining.range.invalid"));
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_MARKER_RANGE_DAYS) {
            throw new IllegalArgumentException(msg.get("personaltraining.range.too.long"));
        }
    }

    private UUID requireAthleteId(Anchor anchor) {
        UUID athleteId = anchor.athleteId();
        if (athleteId == null) {
            throw new IllegalArgumentException(msg.get("adminnote.session.incomplete"));
        }
        return athleteId;
    }

    private LocalDate requireDate(Anchor anchor) {
        LocalDate date = anchor.date();
        if (date == null) {
            throw new IllegalArgumentException(msg.get("adminnote.session.incomplete"));
        }
        return date;
    }
}
