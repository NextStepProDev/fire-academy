package pl.fireacademy.domain.event;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventTypeRepository extends JpaRepository<EventType, UUID> {

    List<EventType> findByCategoryAndActiveTrueOrderByDisplayOrderAsc(EventCategory category);

    List<EventType> findByCategoryOrderByDisplayOrderAsc(EventCategory category);

    Optional<EventType> findTopByCategoryOrderByDisplayOrderDesc(EventCategory category);

    Optional<EventType> findByNameAndCategory(String name, EventCategory category);
}
