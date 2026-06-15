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

    /** Publiczny katalog: aktywne, nieusunięte, jeszcze niezdezaktywowane sloty. */
    @Query("""
        SELECT s FROM TrainingSlot s
        WHERE s.active = true AND s.deletedAt IS NULL
          AND (s.deactivatedFrom IS NULL OR s.deactivatedFrom > CURRENT_DATE)
        ORDER BY s.dayOfWeek ASC, s.startTime ASC
        """)
    List<TrainingSlot> findPublicSlots();

    /** Panel admina: wszystkie nieusunięte sloty. */
    @Query("""
        SELECT s FROM TrainingSlot s
        WHERE s.deletedAt IS NULL
        ORDER BY s.dayOfWeek ASC, s.startTime ASC, s.displayOrder ASC
        """)
    List<TrainingSlot> findAllActive();

    /** Archiwum: usunięte sloty (dostęp do danych kontaktowych byłych uczestników). */
    @Query("SELECT s FROM TrainingSlot s WHERE s.deletedAt IS NOT NULL ORDER BY s.deletedAt DESC")
    List<TrainingSlot> findDeleted();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM TrainingSlot s WHERE s.id = :id")
    Optional<TrainingSlot> findByIdForUpdate(@Param("id") UUID id);
}
