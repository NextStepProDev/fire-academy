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

    List<Event> findByEventType_CategoryAndActiveTrueAndStartDateGreaterThanEqualOrderByStartDateAsc(
            EventCategory category, LocalDate date);

    List<Event> findByEventType_CategoryOrderByStartDateDesc(EventCategory category);

    List<Event> findByEventTypeIdOrderByStartDateDesc(UUID eventTypeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Event e WHERE e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") UUID id);
}
