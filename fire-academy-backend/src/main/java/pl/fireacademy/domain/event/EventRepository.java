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
     * Terminy „bieżące" do widoków publicznych (lista, sitemap): aktywne i jeszcze niezakończone.
     * Liczone po dacie zakończenia (a gdy jej brak — po dacie rozpoczęcia), żeby wydarzenie wielodniowe
     * w trakcie trwania nie znikało ze strony przed swoim faktycznym końcem.
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
