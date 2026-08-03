package pl.fireacademy.api.trainingcalendar;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.fireacademy.api.trainingcalendar.TrainingCalendarDtos.RecurringSession;
import pl.fireacademy.domain.training.TrainingBillingService;
import pl.fireacademy.domain.training.TrainingEnrollment;
import pl.fireacademy.domain.training.TrainingEnrollmentRepository;
import pl.fireacademy.domain.training.TrainingSlot;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The client's group training sessions, shown read-only on their personal calendar.
 *
 * <h2>Nothing here is ever written down</h2>
 * No row in {@code personal_trainings} is ever created from a recurring slot. Materialising these
 * sessions would look simpler for one afternoon and then cost forever: a day off is declared, a
 * session is cancelled, someone resigns mid-month, a slot is deactivated from the 15th — each of
 * those would have to hunt down and delete rows, and every miss is a calendar that disagrees with
 * the invoice.
 * <p>
 * Instead the same code that computes the bill computes the calendar. Cancelling a session removes
 * it from the plan in the same instant it reduces what is owed, because there is only one answer to
 * "which dates does this person actually train".
 *
 * <h2>Cost</h2>
 * Exactly three queries, whatever the range and however many subscriptions: the subscriptions with
 * their slot graph, the club days off, and the cancelled sessions. Everything after that is
 * in-memory arithmetic, one pass per day of the range.
 * <p>
 * The range is NOT bounded by {@code PersonalTrainingService.MAX_RANGE_DAYS}. That cap belongs to
 * the calendar page; {@link TrainingStatsService} legitimately asks for a full year here, to count
 * the group sessions that already happened. The query count does not move — only the day loop does,
 * and 365 passes of date arithmetic is not worth a second code path. Anything materially wider than
 * a year would be, so keep new callers within one.
 */
@Service
public class RecurringSessionOverlayService {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private final TrainingEnrollmentRepository enrollmentRepository;
    private final TrainingBillingService billing;

    public RecurringSessionOverlayService(TrainingEnrollmentRepository enrollmentRepository,
                                          TrainingBillingService billing) {
        this.enrollmentRepository = enrollmentRepository;
        this.billing = billing;
    }

    @Nullable
    private static String instructorName(TrainingSlot slot) {
        var instructor = slot.getInstructor();
        return instructor == null ? null : instructor.getFirstName() + " " + instructor.getLastName();
    }

    @Transactional(readOnly = true)
    public List<RecurringSession> sessionsInRange(UUID athleteId, LocalDate from, LocalDate to) {
        // (1) subscriptions overlapping the range, slot graph included
        List<TrainingEnrollment> enrollments = enrollmentRepository.findByUserCoveringMonthRange(
                athleteId, YearMonth.from(from).format(MONTH), YearMonth.from(to).format(MONTH));
        if (enrollments.isEmpty()) {
            return List.of();
        }

        Map<UUID, Integer> dayOfWeekBySlot = new HashMap<>();
        for (TrainingEnrollment te : enrollments) {
            dayOfWeekBySlot.put(te.getSlot().getId(), te.getSlot().getDayOfWeek());
        }

        // (2) + (3) days off and cancellations, batched across every slot and the whole range
        Map<UUID, Set<LocalDate>> closedBySlot =
                billing.closedDatesInRange(dayOfWeekBySlot.keySet(), dayOfWeekBySlot, from, to);

        List<RecurringSession> sessions = new ArrayList<>();
        // Two subscriptions to the same slot (a renewal spanning the range) must not produce the
        // session twice — the overlay describes what happens, not what was bought.
        Set<String> seen = new HashSet<>();

        for (TrainingEnrollment te : enrollments) {
            TrainingSlot slot = te.getSlot();
            Set<LocalDate> closed = new HashSet<>(closedBySlot.getOrDefault(slot.getId(), Set.of()));
            TrainingBillingService.addDeactivationDates(slot, from, to, closed);

            for (LocalDate date : TrainingBillingService.sessionDatesInRange(te, from, to, closed)) {
                if (!seen.add(slot.getId() + "@" + date)) {
                    continue;
                }
                sessions.add(new RecurringSession(
                        date, slot.getId(),
                        slot.getEventType().getName(),
                        instructorName(slot),
                        slot.getStartTime(), slot.getEndTime()));
            }
        }
        sessions.sort((a, b) -> {
            int byDate = a.date().compareTo(b.date());
            return byDate != 0 ? byDate : a.startTime().compareTo(b.startTime());
        });
        return sessions;
    }
}
