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

    /** Public catalog: active, non-deleted, not-yet-deactivated slots. */
    @Query("""
        SELECT s FROM TrainingSlot s
        WHERE s.active = true AND s.deletedAt IS NULL
          AND (s.deactivatedFrom IS NULL OR s.deactivatedFrom > CURRENT_DATE)
        ORDER BY s.dayOfWeek ASC, s.startTime ASC
        """)
    List<TrainingSlot> findPublicSlots();

    /** Admin panel: all non-deleted slots. */
    @Query("""
        SELECT s FROM TrainingSlot s
        WHERE s.deletedAt IS NULL
        ORDER BY s.dayOfWeek ASC, s.startTime ASC, s.displayOrder ASC
        """)
    List<TrainingSlot> findAllActive();

    /** Archive: deleted slots (access to contact data of former participants). */
    @Query("SELECT s FROM TrainingSlot s WHERE s.deletedAt IS NOT NULL ORDER BY s.deletedAt DESC")
    List<TrainingSlot> findDeleted();

    /** Active, non-deleted slots on a given weekday (ISO 1–7) — for applying a day off. */
    @Query("""
        SELECT s FROM TrainingSlot s
        WHERE s.dayOfWeek = :dayOfWeek AND s.active = true AND s.deletedAt IS NULL
        """)
    List<TrainingSlot> findActiveByDayOfWeek(@Param("dayOfWeek") int dayOfWeek);

    /** Active, non-deleted slots of one instructor on a given weekday — for cancelling an instructor's day. */
    @Query("""
        SELECT s FROM TrainingSlot s
        WHERE s.instructor.id = :instructorId AND s.dayOfWeek = :dayOfWeek
          AND s.active = true AND s.deletedAt IS NULL
        ORDER BY s.startTime ASC
        """)
    List<TrainingSlot> findActiveByInstructorAndDayOfWeek(@Param("instructorId") UUID instructorId,
                                                          @Param("dayOfWeek") int dayOfWeek);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM TrainingSlot s WHERE s.id = :id")
    Optional<TrainingSlot> findByIdForUpdate(@Param("id") UUID id);
}
