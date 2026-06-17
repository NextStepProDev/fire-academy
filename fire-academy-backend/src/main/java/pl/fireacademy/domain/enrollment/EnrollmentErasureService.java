package pl.fireacademy.domain.enrollment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.fireacademy.domain.event.Event;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Usuwanie danych osobowych użytkownika z zapisów (RODO, art. 17). Jedno źródło prawdy dla obu ścieżek
 * usunięcia konta: panel admina ({@code AdminUserService.delete}) i samodzielne ({@code UserService.deleteMe}).
 * <p>
 * Przyszłe zapisy kasujemy (miejsce wraca do puli), przeszłe anonimizujemy (archiwum zostaje bez PII,
 * {@code user_id} → null). Wywoływać <b>przed</b> usunięciem konta — po skasowaniu usera FK
 * {@code ON DELETE SET NULL} zeruje powiązanie i zapisów nie da się już odnaleźć po {@code user_id}.
 */
@Service
public class EnrollmentErasureService {

    private final EnrollmentRepository enrollmentRepository;

    public EnrollmentErasureService(EnrollmentRepository enrollmentRepository) {
        this.enrollmentRepository = enrollmentRepository;
    }

    @Transactional
    public ErasureResult eraseForUser(UUID userId) {
        LocalDate today = LocalDate.now();
        List<Enrollment> future = new ArrayList<>();
        List<Enrollment> past = new ArrayList<>();
        for (Enrollment enrollment : enrollmentRepository.findByUserIdOrderByCreatedAtDesc(userId)) {
            Event event = enrollment.getEvent();
            LocalDate eventEnd = event.getEndDate() != null ? event.getEndDate() : event.getStartDate();
            if (!eventEnd.isBefore(today)) {
                future.add(enrollment);
            } else {
                past.add(enrollment);
            }
        }
        // Wydarzenia, z których zwolniliśmy miejsce — do powiadomienia organizatora (zbieramy przed delete).
        List<Event> freedEvents = future.stream().map(Enrollment::getEvent).toList();

        if (!future.isEmpty()) {
            enrollmentRepository.deleteAll(future);
        }
        if (!past.isEmpty()) {
            past.forEach(Enrollment::anonymize);
            enrollmentRepository.saveAll(past);
        }
        return new ErasureResult(future.size(), past.size(), freedEvents);
    }

    public record ErasureResult(int freed, int anonymized, List<Event> freedEvents) {}
}
