package pl.fireacademy.domain.training;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TrainingCancelledSessionRepository extends JpaRepository<TrainingCancelledSession, UUID> {

    boolean existsBySlotIdAndSessionDate(UUID slotId, LocalDate sessionDate);

    List<TrainingCancelledSession> findBySlotIdOrderBySessionDateAsc(UUID slotId);

    /** All cancelled sessions across the club, newest first (admin overview). Slot graph fetched for the listing. */
    @Query("""
        SELECT cs FROM TrainingCancelledSession cs
        JOIN FETCH cs.slot s
        JOIN FETCH s.eventType
        LEFT JOIN FETCH s.instructor
        ORDER BY cs.sessionDate DESC
        """)
    List<TrainingCancelledSession> findAllForOverview();

    void deleteBySlotIdAndSessionDate(UUID slotId, LocalDate sessionDate);

    /** Cancelled dates for a set of slots within a range (public schedule / user account). */
    @Query("""
        SELECT cs FROM TrainingCancelledSession cs
        WHERE cs.slot.id IN :slotIds AND cs.sessionDate >= :from AND cs.sessionDate <= :to
        ORDER BY cs.sessionDate ASC
        """)
    List<TrainingCancelledSession> findForSlotsInRange(@Param("slotIds") Collection<UUID> slotIds,
                                                       @Param("from") LocalDate from,
                                                       @Param("to") LocalDate to);
}
