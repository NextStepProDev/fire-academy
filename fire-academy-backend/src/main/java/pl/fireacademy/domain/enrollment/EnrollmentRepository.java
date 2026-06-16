package pl.fireacademy.domain.enrollment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.fireacademy.domain.event.EventCategory;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    long countByEventId(UUID eventId);

    @Query("SELECT e.event.id, COUNT(e) FROM Enrollment e WHERE e.event.id IN :eventIds GROUP BY e.event.id")
    List<Object[]> countByEventIds(@Param("eventIds") Collection<UUID> eventIds);

    List<Enrollment> findByEventIdOrderByCreatedAtDesc(UUID eventId);

    @Query("SELECT e FROM Enrollment e JOIN e.event ev WHERE ev.category = :category ORDER BY e.createdAt DESC")
    List<Enrollment> findByEventCategory(EventCategory category);

    boolean existsByEventIdAndEmail(UUID eventId, String email);

    List<Enrollment> findByEmailIgnoreCase(String email);

    /**
     * Wyszukiwanie RODO po dowolnej frazie — dopasowanie częściowe (LIKE), bez rozróżniania wielkości liter,
     * po imieniu, nazwisku, pełnym imieniu i nazwisku ("imię nazwisko") oraz adresie e-mail.
     */
    @Query("""
        SELECT e FROM Enrollment e
        WHERE LOWER(e.firstName) LIKE LOWER(CONCAT('%', :query, '%'))
           OR LOWER(e.lastName) LIKE LOWER(CONCAT('%', :query, '%'))
           OR LOWER(CONCAT(e.firstName, ' ', e.lastName)) LIKE LOWER(CONCAT('%', :query, '%'))
           OR LOWER(e.email) LIKE LOWER(CONCAT('%', :query, '%'))
        ORDER BY e.createdAt DESC
        """)
    List<Enrollment> searchByQuery(@Param("query") String query);

    @Modifying
    @Query("""
        DELETE FROM Enrollment e WHERE e.event.id IN (
            SELECT ev.id FROM Event ev
            WHERE COALESCE(ev.endDate, ev.startDate) < :cutoffDate
        )
        """)
    int deleteByEventEndedBefore(LocalDate cutoffDate);
}
