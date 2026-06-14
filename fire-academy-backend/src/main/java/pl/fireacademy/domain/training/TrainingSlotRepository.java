package pl.fireacademy.domain.training;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainingSlotRepository extends JpaRepository<TrainingSlot, UUID> {

    List<TrainingSlot> findByActiveTrueOrderByDayOfWeekAscStartTimeAsc();

    List<TrainingSlot> findAllByOrderByDayOfWeekAscStartTimeAscDisplayOrderAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM TrainingSlot s WHERE s.id = :id")
    Optional<TrainingSlot> findByIdForUpdate(@Param("id") UUID id);
}
