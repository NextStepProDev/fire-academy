package pl.fireacademy.domain.training;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TrainingEnrollmentRepository extends JpaRepository<TrainingEnrollment, UUID> {

    /** Liczba subskrypcji pokrywających dany miesiąc dla pojedynczego slotu. */
    @Query("""
        SELECT COUNT(te) FROM TrainingEnrollment te
        WHERE te.slot.id = :slotId
          AND te.startMonth <= :month AND (te.endMonth IS NULL OR te.endMonth >= :month)
        """)
    long countCovering(@Param("slotId") UUID slotId, @Param("month") String month);

    /** Liczba subskrypcji pokrywających miesiąc, zgrupowana po slotach (batch). */
    @Query("""
        SELECT te.slot.id, COUNT(te) FROM TrainingEnrollment te
        WHERE te.slot.id IN :slotIds
          AND te.startMonth <= :month AND (te.endMonth IS NULL OR te.endMonth >= :month)
        GROUP BY te.slot.id
        """)
    List<Object[]> countCoveringBySlotIds(@Param("slotIds") Collection<UUID> slotIds, @Param("month") String month);

    /** Czy użytkownik ma już subskrypcję slotu aktywną w/po wskazanym miesiącu (kolizja). */
    @Query("""
        SELECT COUNT(te) > 0 FROM TrainingEnrollment te
        WHERE te.user.id = :userId AND te.slot.id = :slotId
          AND (te.endMonth IS NULL OR te.endMonth >= :month)
        """)
    boolean existsActiveFor(@Param("userId") UUID userId, @Param("slotId") UUID slotId, @Param("month") String month);

    /** Aktywne (niezakończone przed bieżącym miesiącem) subskrypcje użytkownika. */
    @Query("""
        SELECT te FROM TrainingEnrollment te
        WHERE te.user.id = :userId AND (te.endMonth IS NULL OR te.endMonth >= :month)
        ORDER BY te.slot.dayOfWeek ASC, te.slot.startTime ASC
        """)
    List<TrainingEnrollment> findActiveByUser(@Param("userId") UUID userId, @Param("month") String month);

    long countBySlotId(UUID slotId);

    /** Subskrypcje pokrywające dany miesiąc dla slotu (roster admina). */
    @Query("""
        SELECT te FROM TrainingEnrollment te
        WHERE te.slot.id = :slotId
          AND te.startMonth <= :month AND (te.endMonth IS NULL OR te.endMonth >= :month)
        ORDER BY te.createdAt ASC
        """)
    List<TrainingEnrollment> findCoveringForSlot(@Param("slotId") UUID slotId, @Param("month") String month);
}
