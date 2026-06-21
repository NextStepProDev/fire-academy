package pl.fireacademy.domain.enrollment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.fireacademy.domain.event.Event;

import java.util.List;
import java.util.UUID;

/**
 * Erasure of a user's personal data from enrollments (GDPR, art. 17). Single source of truth for both
 * account-deletion paths: the admin panel ({@code AdminUserService.delete}) and self-service ({@code UserService.deleteMe}).
 * <p>
 * Future enrollments are deleted (the spot returns to the pool), past ones are anonymized (the archive stays without PII,
 * {@code user_id} → null). Must be called <b>before</b> deleting the account — once the user is deleted the FK
 * {@code ON DELETE SET NULL} nulls the link and the enrollments can no longer be found by {@code user_id}.
 */
@Service
public class EnrollmentErasureService {

    private final EnrollmentRepository enrollmentRepository;

    public EnrollmentErasureService(EnrollmentRepository enrollmentRepository) {
        this.enrollmentRepository = enrollmentRepository;
    }

    @Transactional
    public ErasureResult eraseForUser(UUID userId) {
        // Current = future/ongoing → deleted (the spot returns to the pool);
        // archived (past) → anonymized. The same split as the reservation views.
        var split = EnrollmentTimeline.split(enrollmentRepository.findByUserIdOrderByCreatedAtDesc(userId));
        List<Enrollment> future = split.current();
        List<Enrollment> past = split.past();
        // Events from which we freed a spot — for notifying the organizer (collected before delete).
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
