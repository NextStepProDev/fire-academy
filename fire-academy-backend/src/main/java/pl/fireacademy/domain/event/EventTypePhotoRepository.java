package pl.fireacademy.domain.event;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EventTypePhotoRepository extends JpaRepository<EventTypePhoto, UUID> {
}
