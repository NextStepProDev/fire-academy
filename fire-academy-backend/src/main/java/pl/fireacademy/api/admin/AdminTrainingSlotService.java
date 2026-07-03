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
import pl.fireacademy.domain.training.TrainingEnrollment;
import pl.fireacademy.domain.training.TrainingEnrollmentRepository;
import pl.fireacademy.domain.training.TrainingPaymentRepository;
import pl.fireacademy.domain.training.TrainingRefund;
import pl.fireacademy.domain.training.TrainingRefundService;
import pl.fireacademy.domain.training.TrainingSlot;
import pl.fireacademy.domain.training.TrainingSlotRepository;
import pl.fireacademy.infrastructure.i18n.MessageService;
import pl.fireacademy.infrastructure.mail.TrainingMailService;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
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
    private final TrainingPaymentRepository paymentRepository;
    private final pl.fireacademy.domain.training.TrainingHolidayRepository holidayRepository;
    private final TrainingRefundService refundService;
    private final EventTypeRepository eventTypeRepository;
    private final InstructorRepository instructorRepository;
    private final MessageService msg;
    private final TrainingMailService trainingMail;

    public AdminTrainingSlotService(TrainingSlotRepository slotRepository,
                                    TrainingEnrollmentRepository enrollmentRepository,
                                    TrainingCancelledSessionRepository cancelledSessionRepository,
                                    TrainingPaymentRepository paymentRepository,
                                    pl.fireacademy.domain.training.TrainingHolidayRepository holidayRepository,
                                    TrainingRefundService refundService,
                                    EventTypeRepository eventTypeRepository,
                                    InstructorRepository instructorRepository,
                                    MessageService msg,
                                    TrainingMailService trainingMail) {
        this.slotRepository = slotRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.cancelledSessionRepository = cancelledSessionRepository;
        this.paymentRepository = paymentRepository;
        this.holidayRepository = holidayRepository;
        this.refundService = refundService;
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

        // Every session from `from` on no longer happens — register refunds for already-paid subscribers,
        // exactly as if each of those sessions were cancelled. Bounded to the booking horizon; unpaid
        // months are no-ops (a refund arises only for a session in a month the subscriber has paid).
        forEachSessionInDeactivationWindow(from, saved.getDayOfWeek(),
                d -> refundService.registerForSlotSession(saved, d, TrainingRefund.TYPE_SESSION, null,
                        TrainingRefundService.ClosureCause.DEACTIVATION));

        var info = mailInfo(saved);
        String month = YearMonth.from(from).toString();
        for (var te : enrollmentRepository.findActiveSubscribersForSlot(id, month)) {
            var u = te.getUser();
            trainingMail.sendSlotDeactivation(u.getEmail(), u.getFirstName(), info, from);
        }
        return toResponse(saved, YearMonth.now().toString());
    }

    /** Undo deactivation — the slot becomes active again; drops the pending refunds the deactivation created (no email). */
    @Transactional
    public TrainingSlotResponse reactivate(UUID id) {
        var slot = findOrThrow(id);
        var former = slot.getDeactivatedFrom();
        slot.setDeactivatedFrom(null);
        slot.setActive(true);
        var saved = slotRepository.save(slot);
        if (former != null) {
            // Blocked if any deactivated session already had a cash refund / spent credit; otherwise revoke them.
            forEachSessionInDeactivationWindow(former, saved.getDayOfWeek(),
                    d -> requireRefundsReversibleForSlotSession(id, d, TrainingRefundService.ClosureCause.DEACTIVATION));
            forEachSessionInDeactivationWindow(former, saved.getDayOfWeek(),
                    d -> refundService.revokeForSlotSession(id, d, TrainingRefundService.ClosureCause.DEACTIVATION));
        }
        return toResponse(saved, YearMonth.now().toString());
    }

    /** Blocks a restore/reactivation when the refunds it would revive can no longer be cleanly reversed. */
    private void requireRefundsReversibleForSlotSession(UUID slotId, java.time.LocalDate date,
                                                        TrainingRefundService.ClosureCause undone) {
        if (refundService.hasCashRefundForSlotSession(slotId, date, undone)) {
            throw new IllegalStateException(msg.get("trainingrefund.restore.cash"));
        }
        if (refundService.hasConsumedCreditForSlotSession(slotId, date, undone)) {
            throw new IllegalStateException(msg.get("trainingrefund.restore.credit.consumed"));
        }
    }

    /** Runs an action for every slot-weekday date from {@code from} up to the end of the booking horizon. */
    private void forEachSessionInDeactivationWindow(java.time.LocalDate from, int dayOfWeek,
                                                    java.util.function.Consumer<java.time.LocalDate> action) {
        var horizon = YearMonth.now().plusMonths(2).atEndOfMonth();
        for (var d = from; !d.isAfter(horizon); d = d.plusDays(1)) {
            if (d.getDayOfWeek().getValue() == dayOfWeek) {
                action.accept(d);
            }
        }
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

    /**
     * Club-wide overview of cancelled sessions with the people each one affected (current subscribers of the
     * slot for that session's month). Used by the admin "Cancelled sessions" view — newest first, the frontend
     * splits it into upcoming (restorable) and archive by the {@code future} flag.
     */
    @Transactional(readOnly = true)
    public List<CancelledSessionOverviewItem> getCancelledOverview() {
        var today = java.time.LocalDate.now();
        return cancelledSessionRepository.findAllForOverview().stream().map(cs -> {
            var slot = cs.getSlot();
            var instr = slot.getInstructor();
            String month = YearMonth.from(cs.getSessionDate()).toString();
            var subscribers = enrollmentRepository.findCoveringForSlot(slot.getId(), month);
            var ids = subscribers.stream().map(TrainingEnrollment::getId).toList();
            var paid = ids.isEmpty() ? new HashSet<UUID>()
                    : new HashSet<>(paymentRepository.findPaidEnrollmentIds(ids, month));
            var participants = subscribers.stream().map(te -> {
                var u = te.getUser();
                boolean isPaid = paid.contains(te.getId());
                // A refund is owed only when a paid session of a priced slot is cancelled.
                boolean owed = isPaid && slot.getPrice() != null;
                return new AffectedParticipant(u.getFirstName(), u.getLastName(), u.getEmail(),
                        u.getPhone(), isPaid, owed);
            }).toList();
            var cause = TrainingRefundService.ClosureCause.SINGLE_SESSION;
            boolean restorable = !refundService.hasCashRefundForSlotSession(slot.getId(), cs.getSessionDate(), cause)
                    && !refundService.hasConsumedCreditForSlotSession(slot.getId(), cs.getSessionDate(), cause);
            return new CancelledSessionOverviewItem(
                    cs.getId(), slot.getId(), cs.getSessionDate(),
                    slot.getEventType().getName(),
                    instr != null ? instr.getFirstName() + " " + instr.getLastName() : null,
                    slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime(), slot.getPrice(),
                    !cs.getSessionDate().isBefore(today), restorable, participants);
        }).toList();
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
        // Past dates are allowed on purpose: a session that already happened but did NOT take place must still
        // generate a refund for subscribers who paid that month (they paid while the price covered this session).
        if (cancelledSessionRepository.existsBySlotIdAndSessionDate(slotId, date)) {
            throw new IllegalStateException(msg.get("trainingsession.already.cancelled"));
        }
        // A whole-club day off or an effective deactivation already closed this date — the session does not
        // take place anyway, and cancelling it again would double-count it (emails, possible refunds).
        if (holidayRepository.existsByHolidayDate(date)
                || (slot.getDeactivatedFrom() != null && !slot.getDeactivatedFrom().isAfter(date))) {
            throw new IllegalStateException(msg.get("trainingsession.already.cancelled"));
        }

        cancelledSessionRepository.save(new TrainingCancelledSession(slot, date));
        notifyAndRefundForCancelledSession(slot, date);
    }

    /**
     * Cancels every session of one instructor on a given date (e.g. the instructor is unavailable), leaving
     * the rest of the schedule intact. Skips slots already cancelled for that date. Returns how many were cancelled.
     */
    @Transactional
    public int cancelInstructorDay(UUID instructorId, java.time.LocalDate date) {
        // Past dates allowed on purpose — a historical session that did not take place must still refund paid subscribers.
        var instructor = instructorRepository.findById(instructorId)
                .orElseThrow(() -> new NotFoundException(msg.get("instructor.not.found")));
        // A whole-club day off already cancels every session that date — nothing left to cancel per instructor.
        if (holidayRepository.existsByHolidayDate(date)) {
            throw new IllegalStateException(msg.get("traininginstructorday.holiday"));
        }
        String instructorName = instructor.getFirstName() + " " + instructor.getLastName();
        String month = YearMonth.from(date).toString();
        // A past day already happened — the "don't come" email is pointless retroactively; only refund.
        boolean notify = !date.isBefore(java.time.LocalDate.now());
        // Accumulate each affected PAID subscriber's cancelled sessions so they get ONE grouped email.
        var buckets = new java.util.LinkedHashMap<UUID, PersonCancellationBucket>();
        int cancelled = 0;
        for (var slot : slotRepository.findActiveByInstructorAndDayOfWeek(instructorId, date.getDayOfWeek().getValue())) {
            // Skip slots already deactivated by that date or already cancelled for it.
            if (slot.getDeactivatedFrom() != null && !slot.getDeactivatedFrom().isAfter(date)) {
                continue;
            }
            if (cancelledSessionRepository.existsBySlotIdAndSessionDate(slot.getId(), date)) {
                continue;
            }
            cancelledSessionRepository.save(new TrainingCancelledSession(slot, date));
            if (notify) {
                collectPaidCancellations(buckets, slot, month);
            }
            refundService.registerForSlotSession(slot, date, TrainingRefund.TYPE_SESSION, null,
                    TrainingRefundService.ClosureCause.SINGLE_SESSION);
            cancelled++;
        }
        if (notify) {
            for (var b : buckets.values()) {
                trainingMail.sendInstructorDayCancellation(b.user.getEmail(), b.user.getFirstName(),
                        instructorName, date, b.lines, b.refundOrNull());
            }
        }
        return cancelled;
    }

    /** Adds a slot's paid subscribers (and their would-be refund) to the per-person grouping for a grouped email. */
    private void collectPaidCancellations(java.util.Map<UUID, PersonCancellationBucket> buckets, TrainingSlot slot, String month) {
        for (var te : paidSubscribers(slot, month)) {
            buckets.computeIfAbsent(te.getUser().getId(), k -> new PersonCancellationBucket(te.getUser()))
                    .add(slot.getEventType().getName(), slot.getStartTime(), slot.getEndTime(), slot.getPrice());
        }
    }

    /**
     * Registers refunds and emails the affected subscribers about a single cancelled session. Only subscribers who
     * have PAID that month are emailed — for the unpaid ones the training just gets cheaper (their estimate updates),
     * no notification. Refunds are registered for paid subscribers regardless.
     */
    private void notifyAndRefundForCancelledSession(TrainingSlot slot, java.time.LocalDate date) {
        // A past session already happened — the cancellation email exists to tell people not to come, which is
        // pointless retroactively. Skip the email; still register the refund for paid subscribers.
        if (!date.isBefore(java.time.LocalDate.now())) {
            var info = mailInfo(slot);
            String month = YearMonth.from(date).toString();
            for (var te : paidSubscribers(slot, month)) {
                var u = te.getUser();
                trainingMail.sendSessionCancelled(u.getEmail(), u.getFirstName(), info, date, slot.getPrice());
            }
        }
        refundService.registerForSlotSession(slot, date, TrainingRefund.TYPE_SESSION, null,
                TrainingRefundService.ClosureCause.SINGLE_SESSION);
    }

    /** Subscribers of a slot who have paid the given month — the only ones notified about a cancellation. */
    private List<TrainingEnrollment> paidSubscribers(TrainingSlot slot, String month) {
        var subs = enrollmentRepository.findCoveringForSlot(slot.getId(), month);
        var ids = subs.stream().map(TrainingEnrollment::getId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        var paid = new HashSet<>(paymentRepository.findPaidEnrollmentIds(ids, month));
        return subs.stream().filter(te -> paid.contains(te.getId())).toList();
    }

    /**
     * Undo cancellation — the session will take place again; drops any pending refunds, which restores the
     * next-month cost for affected subscribers. Restricted to today-or-future sessions: a past session already
     * did not happen, and re-counting it would reopen a possibly settled month. No email is sent — the admin is
     * reminded (in the UI) to phone participants, since the cancellation email already went out.
     */
    @Transactional
    public void restoreSession(UUID slotId, java.time.LocalDate date) {
        if (date.isBefore(java.time.LocalDate.now())) {
            throw new IllegalArgumentException(msg.get("trainingsession.restore.past"));
        }
        // Cannot restore if the refund was already paid out in cash (or the credited surplus already spent).
        requireRefundsReversibleForSlotSession(slotId, date, TrainingRefundService.ClosureCause.SINGLE_SESSION);
        cancelledSessionRepository.deleteBySlotIdAndSessionDate(slotId, date);
        refundService.revokeForSlotSession(slotId, date, TrainingRefundService.ClosureCause.SINGLE_SESSION);
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
                s.isActive(), s.getDeactivatedFrom(), isReactivatable(s), s.getCreatedAt()
        );
    }

    /** A deactivated slot can be reactivated only while every skipped session's refund is still reversible. */
    private boolean isReactivatable(TrainingSlot s) {
        var from = s.getDeactivatedFrom();
        if (from == null) {
            return true;
        }
        var horizon = YearMonth.now().plusMonths(2).atEndOfMonth();
        var cause = TrainingRefundService.ClosureCause.DEACTIVATION;
        for (var d = from; !d.isAfter(horizon); d = d.plusDays(1)) {
            if (d.getDayOfWeek().getValue() == s.getDayOfWeek()
                    && (refundService.hasCashRefundForSlotSession(s.getId(), d, cause)
                        || refundService.hasConsumedCreditForSlotSession(s.getId(), d, cause))) {
                return false;
            }
        }
        return true;
    }
}
