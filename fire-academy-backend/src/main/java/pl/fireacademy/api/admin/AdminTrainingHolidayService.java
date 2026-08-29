package pl.fireacademy.api.admin;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.fireacademy.api.NotFoundException;
import pl.fireacademy.api.admin.TrainingSlotDtos.TrainingHolidayResponse;
import pl.fireacademy.domain.training.*;
import pl.fireacademy.infrastructure.i18n.MessageService;
import pl.fireacademy.infrastructure.mail.TrainingMailService;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Days off (whole-club closures) for the TRAINING category. Adding one reduces the billable session count
 * of every active slot on that weekday, registers refunds for already-paid subscribers, and notifies them.
 */
@Service
public class AdminTrainingHolidayService {

    private final TrainingHolidayRepository holidayRepository;
    private final TrainingSlotRepository slotRepository;
    private final TrainingEnrollmentRepository enrollmentRepository;
    private final TrainingPaymentRepository paymentRepository;
    private final TrainingCancelledSessionRepository cancelledSessionRepository;
    private final TrainingRefundService refundService;
    private final TrainingMailService trainingMail;
    private final MessageService msg;

    public AdminTrainingHolidayService(TrainingHolidayRepository holidayRepository,
                                       TrainingSlotRepository slotRepository,
                                       TrainingEnrollmentRepository enrollmentRepository,
                                       TrainingPaymentRepository paymentRepository,
                                       TrainingCancelledSessionRepository cancelledSessionRepository,
                                       TrainingRefundService refundService,
                                       TrainingMailService trainingMail,
                                       MessageService msg) {
        this.holidayRepository = holidayRepository;
        this.slotRepository = slotRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.paymentRepository = paymentRepository;
        this.cancelledSessionRepository = cancelledSessionRepository;
        this.refundService = refundService;
        this.trainingMail = trainingMail;
        this.msg = msg;
    }

    /**
     * Whether the day off actually cancels this slot's session that date — false when the slot is already
     * stopped by then (scheduled deactivation) or the session was already cancelled individually. Those
     * subscribers were informed/refunded by the earlier closure; the day off changes nothing for them.
     */
    private boolean affectedByHoliday(TrainingSlot slot, LocalDate date) {
        if (slot.getDeactivatedFrom() != null && !slot.getDeactivatedFrom().isAfter(date)) {
            return false;
        }
        return !cancelledSessionRepository.existsBySlotIdAndSessionDate(slot.getId(), date);
    }

    @Transactional(readOnly = true)
    public List<TrainingHolidayResponse> getForMonth(YearMonth month) {
        return holidayRepository.findByHolidayDateBetweenOrderByHolidayDateAsc(month.atDay(1), month.atEndOfMonth())
                .stream()
                .map(h -> new TrainingHolidayResponse(h.getId(), h.getHolidayDate(), h.getLabel(),
                        countNotified(h.getHolidayDate()), isRestorable(h.getHolidayDate())))
                .toList();
    }

    /** Whether the day off can be removed (its sessions restored) — false once a cash refund is paid out / credit spent. */
    private boolean isRestorable(LocalDate date) {
        return !refundService.hasCashRefundForDate(date) && !refundService.hasConsumedCreditForDate(date);
    }

    /**
     * Distinct participants affected by a day off — everyone who got the email, and therefore everyone
     * who may need a phone call if the day off is removed again. Counts the unpaid too: they are told
     * about the cancellation as well, so leaving them out here would send the admin calling back only
     * half of the people who were informed.
     */
    private int countNotified(LocalDate date) {
        String month = YearMonth.from(date).toString();
        var users = new HashSet<UUID>();
        for (var slot : slotRepository.findActiveByDayOfWeek(date.getDayOfWeek().getValue())) {
            if (!affectedByHoliday(slot, date)) {
                continue;
            }
            for (var te : coveringSubscribers(slot, month)) {
                users.add(te.getUser().getId());
            }
        }
        return users.size();
    }

    @Transactional
    public TrainingHolidayResponse add(LocalDate date, @Nullable String label) {
        if (date.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException(msg.get("trainingholiday.past"));
        }
        if (holidayRepository.existsByHolidayDate(date)) {
            throw new IllegalStateException(msg.get("trainingholiday.duplicate"));
        }
        String cleanLabel = (label != null && !label.isBlank()) ? label.trim() : null;
        var holiday = holidayRepository.saveAndFlush(new TrainingHoliday(date, cleanLabel));

        // Refund ledger: register refunds for already-paid subscribers of affected slots.
        refundService.registerForHoliday(date, cleanLabel);

        // Group each affected subscriber's sessions that day → ONE day-off email listing them all.
        //
        // EVERYONE enrolled is emailed, not only those who have paid. Payment is taken up front, before
        // the month's first session, so "enrolled but not yet paid" is exactly the window in which a day
        // off is normally announced — and those people still intend to show up. Mailing only the payers
        // handled their money and let the rest drive to a closed gym.
        //
        // What still depends on payment is the REFUND: only a paid session is owed anything back, so the
        // price goes into the bucket only for payers and everyone else gets the same email without an
        // amount. `registerForHoliday` above is unchanged — it books refunds for the paid ones only.
        var month = YearMonth.from(date).toString();
        var buckets = new java.util.LinkedHashMap<UUID, PersonCancellationBucket>();
        for (var slot : slotRepository.findActiveByDayOfWeek(date.getDayOfWeek().getValue())) {
            if (!affectedByHoliday(slot, date)) {
                continue;   // stopped/cancelled earlier — informed and refunded by that closure, not this one
            }
            var subscribers = coveringSubscribers(slot, month);
            var paid = paidIdsOf(subscribers, month);
            for (var te : subscribers) {
                buckets.computeIfAbsent(te.getUser().getId(), k -> new PersonCancellationBucket(te.getUser()))
                        .add(slot.getEventType().getName(), slot.getStartTime(), slot.getEndTime(),
                                paid.contains(te.getId()) ? slot.getPrice() : null);
            }
        }
        for (var b : buckets.values()) {
            trainingMail.sendDayOffCancellation(b.user.getEmail(), b.user.getFirstName(), date, cleanLabel,
                    b.lines, b.refundOrNull());
        }
        return new TrainingHolidayResponse(holiday.getId(), holiday.getHolidayDate(), holiday.getLabel(),
                buckets.size(), true);   // just added → restorable; buckets.size() = participants notified
    }

    /** Everyone whose subscription covers the given month on this slot — paid or not. */
    private List<TrainingEnrollment> coveringSubscribers(TrainingSlot slot, String month) {
        return enrollmentRepository.findCoveringForSlot(slot.getId(), month);
    }

    /** Which of those subscriptions have the month paid — one query for the whole group, not one per person. */
    private Set<UUID> paidIdsOf(List<TrainingEnrollment> subscribers, String month) {
        var ids = subscribers.stream().map(TrainingEnrollment::getId).toList();
        if (ids.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(paymentRepository.findPaidEnrollmentIds(ids, month));
    }

    @Transactional
    public void remove(UUID id) {
        var holiday = holidayRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(msg.get("trainingholiday.not.found")));
        var date = holiday.getHolidayDate();
        // Sessions come back — blocked if a cash refund was already paid out (or credited surplus already spent).
        if (refundService.hasCashRefundForDate(date)) {
            throw new IllegalStateException(msg.get("trainingrefund.restore.cash"));
        }
        if (refundService.hasConsumedCreditForDate(date)) {
            throw new IllegalStateException(msg.get("trainingrefund.restore.credit.consumed"));
        }
        holidayRepository.delete(holiday);
        refundService.revokeForHoliday(date);
    }
}
