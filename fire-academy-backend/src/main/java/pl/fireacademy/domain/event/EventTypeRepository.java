package pl.fireacademy.domain.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventTypeRepository extends JpaRepository<EventType, UUID> {

    // JOIN FETCH zdjęć eliminuje N+1 przy publicznym listowaniu (każdy typ ładuje galerię).
    // DISTINCT na zapytaniu encyjnym Hibernate deduplikuje w pamięci — kolejność z @OrderBy zostaje zachowana.
    @Query("SELECT DISTINCT et FROM EventType et LEFT JOIN FETCH et.photos "
            + "WHERE et.category = :category AND et.active = true ORDER BY et.displayOrder ASC")
    List<EventType> findByCategoryAndActiveTrueOrderByDisplayOrderAsc(EventCategory category);

    List<EventType> findByCategoryOrderByDisplayOrderAsc(EventCategory category);

    Optional<EventType> findTopByCategoryOrderByDisplayOrderDesc(EventCategory category);

    Optional<EventType> findByNameAndCategory(String name, EventCategory category);
}
