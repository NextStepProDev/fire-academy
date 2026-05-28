package pl.fireacademy.domain.instructor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.fireacademy.domain.event.EventCategory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstructorRepository extends JpaRepository<Instructor, UUID> {

    List<Instructor> findByActiveTrueOrderByDisplayOrderAsc();

    List<Instructor> findAllByOrderByDisplayOrderAsc();

    Optional<Instructor> findTopByOrderByDisplayOrderDesc();

    @Query("SELECT DISTINCT i FROM Instructor i JOIN i.categories c WHERE c = :category AND i.active = true ORDER BY i.displayOrder")
    List<Instructor> findActiveByCategoryOrdered(@Param("category") EventCategory category);
}
