package pl.fireacademy.domain.enrollment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.fireacademy.domain.event.EventCategory;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    long countByEventId(UUID eventId);

    @Query("SELECT e.event.id, COUNT(e) FROM Enrollment e WHERE e.event.id IN :eventIds GROUP BY e.event.id")
    List<Object[]> countByEventIds(@Param("eventIds") Collection<UUID> eventIds);

    List<Enrollment> findByEventIdOrderByCreatedAtDesc(UUID eventId);

    @Query("SELECT e FROM Enrollment e JOIN e.event ev WHERE ev.category = :category ORDER BY e.createdAt DESC")
    List<Enrollment> findByEventCategory(EventCategory category);

    boolean existsByEventIdAndUserId(UUID eventId, UUID userId);

    List<Enrollment> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Enrollment> findByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query("""
        DELETE FROM Enrollment e WHERE e.event.id IN (
            SELECT ev.id FROM Event ev
            WHERE COALESCE(ev.endDate, ev.startDate) < :cutoffDate
        )
        """)
    int deleteByEventEndedBefore(LocalDate cutoffDate);
}
