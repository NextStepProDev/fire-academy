package pl.fireacademy.domain.enrollment;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.fireacademy.domain.event.EventCategory;

import java.util.List;
import java.util.UUID;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    long countByEventId(UUID eventId);

    List<Enrollment> findByEventIdOrderByCreatedAtDesc(UUID eventId);

    List<Enrollment> findByEvent_EventType_CategoryOrderByCreatedAtDesc(EventCategory category);

    boolean existsByEventIdAndEmail(UUID eventId, String email);
}
