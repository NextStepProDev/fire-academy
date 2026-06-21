package pl.fireacademy.domain.enrollment;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Splits enrollments into current and archived by the event's end date — single source of truth for
 * the "My reservations" view, the user profile in the admin panel, and data erasure (GDPR).
 * <p>
 * Current = the event is ongoing or in the future (sorted ascending — soonest on top),
 * archived = the event ended before today (sorted descending — most recent on top).
 */
public final class EnrollmentTimeline {

    private EnrollmentTimeline() {}

    public record Split(List<Enrollment> current, List<Enrollment> past) {}

    public static Split split(List<Enrollment> enrollments) {
        LocalDate today = LocalDate.now();
        List<Enrollment> current = new ArrayList<>();
        List<Enrollment> past = new ArrayList<>();
        for (Enrollment e : enrollments) {
            (e.getEvent().isPastOn(today) ? past : current).add(e);
        }
        current.sort(Comparator.comparing(e -> e.getEvent().getStartDate()));
        past.sort(Comparator.comparing((Enrollment e) -> e.getEvent().getStartDate()).reversed());
        return new Split(current, past);
    }
}
