package pl.fireacademy.api.admin;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.fireacademy.api.admin.TrainingSlotDtos.TrainingHolidayResponse;
import pl.fireacademy.domain.training.*;
import pl.fireacademy.infrastructure.i18n.MessageService;
import pl.fireacademy.infrastructure.mail.TrainingMailService;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
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
    private final TrainingRefundService refundService;
    private final TrainingMailService trainingMail;
    private final MessageService msg;

    public AdminTrainingHolidayService(TrainingHolidayRepository holidayRepository,
                                       TrainingSlotRepository slotRepository,
                                       TrainingEnrollmentRepository enrollmentRepository,
                                       TrainingPaymentRepository paymentRepository,
                                       TrainingRefundService refundService,
                                       TrainingMailService trainingMail,
                                       MessageService msg) {
        this.holidayRepository = holidayRepository;
        this.slotRepository = slotRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.paymentRepository = paymentRepository;
        this.refundService = refundService;
        this.trainingMail = trainingMail;
        this.msg = msg;
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

    /** Distinct paid participants affected by a day off (those who got the email / need a phone call on removal). */
    private int countNotified(LocalDate date) {
        String month = YearMonth.from(date).toString();
        var users = new HashSet<UUID>();
        for (var slot : slotRepository.findActiveByDayOfWeek(date.getDayOfWeek().getValue())) {
            for (var te : paidSubscribers(slot, month)) {
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

        // Group each affected PAID subscriber's sessions that day → ONE day-off email listing them all. Unpaid
        // subscribers are NOT emailed — their training simply gets cheaper and the estimate updates.
        var month = YearMonth.from(date).toString();
        var buckets = new java.util.LinkedHashMap<UUID, PersonCancellationBucket>();
        for (var slot : slotRepository.findActiveByDayOfWeek(date.getDayOfWeek().getValue())) {
            for (var te : paidSubscribers(slot, month)) {
                buckets.computeIfAbsent(te.getUser().getId(), k -> new PersonCancellationBucket(te.getUser()))
                        .add(slot.getEventType().getName(), slot.getStartTime(), slot.getEndTime(), slot.getPrice());
            }
        }
        for (var b : buckets.values()) {
            trainingMail.sendDayOffCancellation(b.user.getEmail(), b.user.getFirstName(), date, cleanLabel,
                    b.lines, b.refundOrNull());
        }
        return new TrainingHolidayResponse(holiday.getId(), holiday.getHolidayDate(), holiday.getLabel(),
                buckets.size(), true);   // just added → restorable; buckets.size() = participants notified
    }

    /** Subscribers of a slot who have paid the given month — the only ones notified about a day off. */
    private List<TrainingEnrollment> paidSubscribers(TrainingSlot slot, String month) {
        var subs = enrollmentRepository.findCoveringForSlot(slot.getId(), month);
        var ids = subs.stream().map(TrainingEnrollment::getId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        var paid = new HashSet<>(paymentRepository.findPaidEnrollmentIds(ids, month));
        return subs.stream().filter(te -> paid.contains(te.getId())).toList();
    }

    @Transactional
    public void remove(UUID id) {
        var holiday = holidayRepository.findById(id)
                .orElseThrow(() -> new pl.fireacademy.api.NotFoundException(msg.get("trainingholiday.not.found")));
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
