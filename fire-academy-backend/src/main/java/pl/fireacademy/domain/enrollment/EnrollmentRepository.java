package pl.fireacademy.domain.enrollment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pl.fireacademy.domain.event.EventCategory;

import java.util.List;
import java.util.UUID;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    long countByEventId(UUID eventId);

    List<Enrollment> findByEventIdOrderByCreatedAtDesc(UUID eventId);

    @Query("SELECT e FROM Enrollment e JOIN e.event ev WHERE ev.category = :category ORDER BY e.createdAt DESC")
    List<Enrollment> findByEventCategory(EventCategory category);

    boolean existsByEventIdAndEmail(UUID eventId, String email);
}
