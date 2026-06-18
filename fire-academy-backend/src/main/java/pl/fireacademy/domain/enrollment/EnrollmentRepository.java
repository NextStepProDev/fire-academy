package pl.fireacademy.domain.enrollment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    long countByEventId(UUID eventId);

    long deleteByEventId(UUID eventId);

    @Query("SELECT e.event.id, COUNT(e) FROM Enrollment e WHERE e.event.id IN :eventIds GROUP BY e.event.id")
    List<Object[]> countByEventIds(@Param("eventIds") Collection<UUID> eventIds);

    List<Enrollment> findByEventIdOrderByCreatedAtDesc(UUID eventId);

    boolean existsByEventIdAndUserId(UUID eventId, UUID userId);

    List<Enrollment> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Enrollment> findByIdAndUserId(UUID id, UUID userId);
}
