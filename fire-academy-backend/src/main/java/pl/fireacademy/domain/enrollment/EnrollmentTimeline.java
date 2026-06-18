package pl.fireacademy.domain.enrollment;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Podział zapisów na bieżące i archiwalne wg daty zakończenia terminu — jedno źródło prawdy dla
 * widoku „Moje rezerwacje", profilu użytkownika w panelu admina i usuwania danych (RODO).
 * <p>
 * Bieżące = termin trwa lub jest w przyszłości (sortowane rosnąco — najbliższe na górze),
 * archiwalne = termin zakończył się przed dziś (sortowane malejąco — najnowsze na górze).
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
