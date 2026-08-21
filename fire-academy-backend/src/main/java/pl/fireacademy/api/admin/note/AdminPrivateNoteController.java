package pl.fireacademy.api.admin.note;

import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pl.fireacademy.api.admin.note.NoteDtos.NoteMarkersResponse;
import pl.fireacademy.api.admin.note.NoteDtos.NoteResponse;
import pl.fireacademy.api.admin.note.NoteDtos.SaveNoteRequest;
import pl.fireacademy.config.CurrentUserId;
import pl.fireacademy.infrastructure.i18n.MessageService;

import java.time.LocalDate;
import java.util.UUID;

/**
 * The owner's private notes.
 *
 * <p>The kind of target travels as a PATH SEGMENT, not as four families of endpoints: three mappings
 * and one code path instead of twelve mappings and four copies to keep in step.
 *
 * <p>Role protection already comes from the {@code /api/admin/**} prefix, as everywhere else in this
 * codebase; {@code @PreAuthorize} here is a second lock, and the only one in {@code main/}. It is
 * deliberate rather than a stray import: this is the one controller whose entire purpose is that its
 * data reaches exactly one person, so it does not rely on a single line of URL matching in a config
 * class somebody may one day refactor.
 */
@RestController
@RequestMapping("/api/admin/notes")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPrivateNoteController {

    private final AdminPrivateNoteService service;
    private final MessageService msg;

    public AdminPrivateNoteController(AdminPrivateNoteService service, MessageService msg) {
        this.service = service;
        this.msg = msg;
    }

    /**
     * Markers for a calendar page: which targets already carry a note.
     *
     * <p>Mapped above the {@code /{target}/{id}} routes for readability only -- "markers" is not a
     * valid target, so it could never match them anyway.
     */
    @GetMapping("/markers")
    public NoteMarkersResponse markers(@CurrentUserId UUID adminId,
                                       @RequestParam(required = false)
                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate from,
                                       @RequestParam(required = false)
                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate to,
                                       @RequestParam(required = false) @Nullable UUID athleteId) {
        return service.markers(adminId, from, to, athleteId);
    }

    @GetMapping("/{target}/{id}")
    public NoteResponse get(@CurrentUserId UUID adminId,
                            @PathVariable String target,
                            @PathVariable UUID id,
                            @RequestParam(required = false) @Nullable UUID athleteId,
                            @RequestParam(required = false)
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate date) {
        return service.get(parse(target), id, athleteId, date, adminId);
    }

    @PutMapping("/{target}/{id}")
    public ResponseEntity<Void> save(@CurrentUserId UUID adminId,
                                     @PathVariable String target,
                                     @PathVariable UUID id,
                                     @RequestParam(required = false) @Nullable UUID athleteId,
                                     @RequestParam(required = false)
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate date,
                                     @Valid @RequestBody SaveNoteRequest request) {
        service.save(parse(target), id, athleteId, date, adminId, request.body());
        return ResponseEntity.noContent().build();
    }

    /** Idempotent: deleting a note that is not there is a success, not a 404. */
    @DeleteMapping("/{target}/{id}")
    public ResponseEntity<Void> delete(@CurrentUserId UUID adminId,
                                       @PathVariable String target,
                                       @PathVariable UUID id,
                                       @RequestParam(required = false) @Nullable UUID athleteId,
                                       @RequestParam(required = false)
                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate date) {
        service.delete(parse(target), id, athleteId, date, adminId);
        return ResponseEntity.noContent().build();
    }

    /** The bad segment came from the caller, so it does not belong in the answer. */
    private NoteTarget parse(String target) {
        return NoteTarget.tryFrom(target)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("adminnote.target.invalid")));
    }
}
