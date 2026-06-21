package pl.fireacademy.domain.event;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    /**
     * "Current" events for public views (list, sitemap): active and not yet finished.
     * Computed by the end date (or, when absent, by the start date) so that a multi-day event
     * in progress does not disappear from the page before its actual end.
     */
    @Query("""
            SELECT e FROM Event e
            WHERE e.category = :category AND e.active = true
              AND COALESCE(e.endDate, e.startDate) >= :today
            ORDER BY e.startDate ASC
            """)
    List<Event> findActiveCurrentByCategory(@Param("category") EventCategory category,
                                            @Param("today") LocalDate today);

    List<Event> findByCategoryOrderByStartDateAsc(EventCategory category);

    List<Event> findByEventTypeIdOrderByStartDateDesc(UUID eventTypeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Event e WHERE e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") UUID id);
}
