package pl.fireacademy.api.admin;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.fireacademy.api.NotFoundException;
import pl.fireacademy.api.admin.TrainingSlotDtos.*;
import pl.fireacademy.domain.event.EventCategory;
import pl.fireacademy.domain.event.EventType;
import pl.fireacademy.domain.event.EventTypeRepository;
import pl.fireacademy.domain.instructor.Instructor;
import pl.fireacademy.domain.instructor.InstructorRepository;
import pl.fireacademy.domain.training.TrainingCancelledSession;
import pl.fireacademy.domain.training.TrainingCancelledSessionRepository;
import pl.fireacademy.domain.training.TrainingEnrollmentRepository;
import pl.fireacademy.domain.training.TrainingSlot;
import pl.fireacademy.domain.training.TrainingSlotRepository;
import pl.fireacademy.infrastructure.i18n.MessageService;
import pl.fireacademy.infrastructure.mail.TrainingMailService;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AdminTrainingSlotService {

    private static final java.time.format.DateTimeFormatter TIME_FMT =
            java.time.format.DateTimeFormatter.ofPattern("HH:mm");
    private static final String[] DAYS_PL = {
            "", "poniedziałek", "wtorek", "środa", "czwartek", "piątek", "sobota", "niedziela"
    };

    private final TrainingSlotRepository slotRepository;
    private final TrainingEnrollmentRepository enrollmentRepository;
    private final TrainingCancelledSessionRepository cancelledSessionRepository;
    private final EventTypeRepository eventTypeRepository;
    private final InstructorRepository instructorRepository;
    private final MessageService msg;
    private final TrainingMailService trainingMail;

    public AdminTrainingSlotService(TrainingSlotRepository slotRepository,
                                    TrainingEnrollmentRepository enrollmentRepository,
                                    TrainingCancelledSessionRepository cancelledSessionRepository,
                                    EventTypeRepository eventTypeRepository,
                                    InstructorRepository instructorRepository,
                                    MessageService msg,
                                    TrainingMailService trainingMail) {
        this.slotRepository = slotRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.cancelledSessionRepository = cancelledSessionRepository;
        this.eventTypeRepository = eventTypeRepository;
        this.instructorRepository = instructorRepository;
        this.msg = msg;
        this.trainingMail = trainingMail;
    }

    @Transactional(readOnly = true)
    public List<TrainingSlotResponse> getAll(YearMonth month) {
        String m = month.toString();
        return slotRepository.findAllActive().stream()
                .map(s -> toResponse(s, m))
                .toList();
    }

    @Transactional
    public TrainingSlotResponse create(CreateTrainingSlotRequest request) {
        var eventType = resolveTrainingType(request.eventTypeId());
        var slot = new TrainingSlot(eventType, request.dayOfWeek(), request.startTime(), request.maxParticipants());
        applyCommon(slot, request.instructorId(), request.endTime(), request.price());
        return toResponse(slotRepository.save(slot), YearMonth.now().toString());
    }

    @Transactional
    public List<TrainingSlotResponse> createBatch(BatchCreateTrainingSlotRequest request) {
        var eventType = resolveTrainingType(request.eventTypeId());
        var instructor = resolveInstructor(request.instructorId());
        String month = YearMonth.now().toString();
        List<TrainingSlotResponse> created = new ArrayList<>();
        for (var row : request.slots()) {
            var slot = new TrainingSlot(eventType, row.dayOfWeek(), row.startTime(), row.maxParticipants());
            slot.setInstructor(instructor);
            slot.setEndTime(row.endTime());
            slot.setPrice(row.price());
            created.add(toResponse(slotRepository.save(slot), month));
        }
        return created;
    }

    @Transactional
    public TrainingSlotResponse update(UUID id, UpdateTrainingSlotRequest request) {
        var slot = findOrThrow(id);

        // Snapshot of the values before the change (to detect differences for email D).
        var before = snapshot(slot);

        slot.setEventType(resolveTrainingType(request.eventTypeId()));
        slot.setDayOfWeek(request.dayOfWeek());
        slot.setStartTime(request.startTime());
        slot.setMaxParticipants(request.maxParticipants());
        applyCommon(slot, request.instructorId(), request.endTime(), request.price());
        var saved = slotRepository.save(slot);

        var changes = diff(before, snapshot(saved));
        if (!changes.isEmpty()) {
            var info = mailInfo(saved);
            notifySubscribers(saved.getId(), (email, firstName) ->
                    trainingMail.sendSlotModification(email, firstName, info, changes));
        }
        return toResponse(saved, YearMonth.now().toString());
    }

    @Transactional
    public TrainingSlotResponse toggleActive(UUID id) {
        var slot = findOrThrow(id);
        slot.setActive(!slot.isActive());
        return toResponse(slotRepository.save(slot), YearMonth.now().toString());
    }

    /**
     * Scheduled deactivation: the slot stops taking place from the given date. Sessions before it
     * happen normally. Notifies current subscribers by email (J) (indefinite +
     * those covering the month of the date). The date cannot be in the past.
     */
    @Transactional
    public TrainingSlotResponse deactivate(UUID id, java.time.LocalDate from) {
        var slot = findOrThrow(id);
        if (slot.isDeleted()) {
            throw new IllegalStateException(msg.get("trainingslot.not.found"));
        }
        if (from.isBefore(java.time.LocalDate.now())) {
            throw new IllegalArgumentException(msg.get("trainingslot.deactivate.past"));
        }
        slot.setDeactivatedFrom(from);
        var saved = slotRepository.save(slot);

        var info = mailInfo(saved);
        String month = YearMonth.from(from).toString();
        for (var te : enrollmentRepository.findActiveSubscribersForSlot(id, month)) {
            var u = te.getUser();
            trainingMail.sendSlotDeactivation(u.getEmail(), u.getFirstName(), info, from);
        }
        return toResponse(saved, YearMonth.now().toString());
    }

    /** Undo deactivation — the slot becomes active again (no email). */
    @Transactional
    public TrainingSlotResponse reactivate(UUID id) {
        var slot = findOrThrow(id);
        slot.setDeactivatedFrom(null);
        slot.setActive(true);
        return toResponse(slotRepository.save(slot), YearMonth.now().toString());
    }

    /**
     * Soft delete: the slot disappears from the catalog, but enrollments and contact data stay in the archive
     * (the admin can call former participants). Sends email E to current subscribers.
     */
    @Transactional
    public void delete(UUID id) {
        var slot = findOrThrow(id);
        if (slot.isDeleted()) {
            return;
        }
        var info = mailInfo(slot);
        notifySubscribers(id, (email, firstName) ->
                trainingMail.sendSlotDeletion(email, firstName, info));
        slot.setDeletedAt(java.time.Instant.now());
        slotRepository.save(slot);
    }

    @Transactional(readOnly = true)
    public List<DeletedSlotResponse> getDeletedSlots() {
        return slotRepository.findDeleted().stream().map(s -> {
            var instr = s.getInstructor();
            var participants = enrollmentRepository.findAllForSlotWithUser(s.getId()).stream()
                    .map(te -> {
                        var u = te.getUser();
                        return new ArchivedParticipant(u.getFirstName(), u.getLastName(),
                                u.getEmail(), u.getPhone(), te.getStartMonth(), te.getEndMonth());
                    }).toList();
            return new DeletedSlotResponse(
                    s.getId(), s.getEventType().getName(),
                    instr != null ? instr.getFirstName() + " " + instr.getLastName() : null,
                    s.getDayOfWeek(), s.getStartTime(), s.getEndTime(),
                    java.util.Objects.requireNonNull(s.getDeletedAt()), participants);
        }).toList();
    }

    // ── Cancelling individual sessions (email F) ─────────────────────────────

    @Transactional(readOnly = true)
    public List<CancelledSessionResponse> getCancelledSessions(UUID slotId) {
        return cancelledSessionRepository.findBySlotIdOrderBySessionDateAsc(slotId).stream()
                .map(cs -> new CancelledSessionResponse(cs.getId(), cs.getSessionDate()))
                .toList();
    }

    /** Cancels a specific session of a given slot (e.g. the instructor is ill) and notifies those enrolled for that month. */
    @Transactional
    public void cancelSession(UUID slotId, java.time.LocalDate date) {
        var slot = findOrThrow(slotId);
        if (slot.isDeleted()) {
            throw new IllegalStateException(msg.get("trainingslot.not.found"));
        }
        if (date.getDayOfWeek().getValue() != slot.getDayOfWeek()) {
            throw new IllegalArgumentException(msg.get("trainingsession.wrong.day"));
        }
        if (date.isBefore(java.time.LocalDate.now())) {
            throw new IllegalArgumentException(msg.get("trainingsession.past"));
        }
        if (cancelledSessionRepository.existsBySlotIdAndSessionDate(slotId, date)) {
            throw new IllegalStateException(msg.get("trainingsession.already.cancelled"));
        }

        cancelledSessionRepository.save(new TrainingCancelledSession(slot, date));

        var info = mailInfo(slot);
        String month = YearMonth.from(date).toString();
        for (var te : enrollmentRepository.findCoveringForSlot(slotId, month)) {
            var u = te.getUser();
            trainingMail.sendSessionCancelled(u.getEmail(), u.getFirstName(), info, date);
        }
    }

    /** Undo cancellation — the session will take place again (no email). */
    @Transactional
    public void restoreSession(UUID slotId, java.time.LocalDate date) {
        cancelledSessionRepository.deleteBySlotIdAndSessionDate(slotId, date);
    }

    private record SlotSnapshot(String type, String instructor, String day, String time, String price) {}

    private SlotSnapshot snapshot(TrainingSlot s) {
        var instr = s.getInstructor();
        String time = s.getStartTime().format(TIME_FMT)
                + (s.getEndTime() != null ? "–" + s.getEndTime().format(TIME_FMT) : "");
        return new SlotSnapshot(
                s.getEventType().getName(),
                instr != null ? instr.getFirstName() + " " + instr.getLastName() : "—",
                DAYS_PL[s.getDayOfWeek()],
                time,
                s.getPrice() != null ? s.getPrice().toPlainString() + " zł" : "—");
    }

    private List<EventDtos.FieldChange> diff(SlotSnapshot a, SlotSnapshot b) {
        var changes = new ArrayList<EventDtos.FieldChange>();
        if (!a.type().equals(b.type())) changes.add(new EventDtos.FieldChange(msg.get("training.change.type"), a.type(), b.type()));
        if (!a.day().equals(b.day())) changes.add(new EventDtos.FieldChange(msg.get("training.change.day"), a.day(), b.day()));
        if (!a.time().equals(b.time())) changes.add(new EventDtos.FieldChange(msg.get("training.change.time"), a.time(), b.time()));
        if (!a.instructor().equals(b.instructor())) changes.add(new EventDtos.FieldChange(msg.get("training.change.instructor"), a.instructor(), b.instructor()));
        if (!a.price().equals(b.price())) changes.add(new EventDtos.FieldChange(msg.get("training.change.price"), a.price(), b.price()));
        return changes;
    }

    private TrainingMailService.SlotInfo mailInfo(TrainingSlot s) {
        var instr = s.getInstructor();
        return new TrainingMailService.SlotInfo(
                s.getEventType().getName(),
                instr != null ? instr.getFirstName() + " " + instr.getLastName() : null,
                s.getDayOfWeek(), s.getStartTime(), s.getEndTime(), s.getPrice());
    }

    /** Sends a notification to every current subscriber of the slot (current month and beyond). */
    private void notifySubscribers(UUID slotId, java.util.function.BiConsumer<String, String> send) {
        String month = YearMonth.now().toString();
        for (var te : enrollmentRepository.findActiveSubscribersForSlot(slotId, month)) {
            var u = te.getUser();
            send.accept(u.getEmail(), u.getFirstName());
        }
    }

    private void applyCommon(TrainingSlot slot, @Nullable UUID instructorId,
                             @Nullable LocalTime endTime, @Nullable BigDecimal price) {
        slot.setInstructor(resolveInstructor(instructorId));
        slot.setEndTime(endTime);
        slot.setPrice(price);
    }

    private EventType resolveTrainingType(UUID eventTypeId) {
        var eventType = eventTypeRepository.findById(eventTypeId)
                .orElseThrow(() -> new NotFoundException(msg.get("eventtype.not.found")));
        if (eventType.getCategory() != EventCategory.TRAINING) {
            throw new IllegalArgumentException(msg.get("trainingslot.type.not.training"));
        }
        return eventType;
    }

    @Nullable
    private Instructor resolveInstructor(@Nullable UUID instructorId) {
        if (instructorId == null) {
            return null;
        }
        return instructorRepository.findById(instructorId)
                .orElseThrow(() -> new NotFoundException(msg.get("instructor.not.found")));
    }

    private TrainingSlot findOrThrow(UUID id) {
        return slotRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(msg.get("trainingslot.not.found")));
    }

    private TrainingSlotResponse toResponse(TrainingSlot s, String month) {
        var et = s.getEventType();
        var instr = s.getInstructor();
        return new TrainingSlotResponse(
                s.getId(),
                et.getId(), et.getName(),
                instr != null ? instr.getId() : null,
                instr != null ? instr.getFirstName() + " " + instr.getLastName() : null,
                s.getDayOfWeek(), s.getStartTime(), s.getEndTime(), s.getPrice(),
                s.getMaxParticipants(),
                s.getDisplayOrder(),
                enrollmentRepository.countCovering(s.getId(), month),
                s.isActive(), s.getDeactivatedFrom(), s.getCreatedAt()
        );
    }
}
