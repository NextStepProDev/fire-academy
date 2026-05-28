package pl.fireacademy.domain.instructor;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstructorRepository extends JpaRepository<Instructor, UUID> {

    List<Instructor> findByActiveTrueOrderByDisplayOrderAsc();

    List<Instructor> findAllByOrderByDisplayOrderAsc();

    Optional<Instructor> findTopByOrderByDisplayOrderDesc();
}
