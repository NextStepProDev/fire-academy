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

    /** Aktywne subskrypcje użytkownika (niezakończone i na nieusuniętych slotach). */
    @Query("""
        SELECT te FROM TrainingEnrollment te
        WHERE te.user.id = :userId
          AND te.slot.deletedAt IS NULL
          AND (te.endMonth IS NULL OR te.endMonth >= :month)
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

    /**
     * Aktualni odbiorcy powiadomień o slocie: subskrypcje jeszcze niezakończone
     * (bezterminowe lub kończące się w/po bieżącym miesiącu, w tym przyszłe). Dla maili D/E.
     */
    @Query("""
        SELECT te FROM TrainingEnrollment te
        JOIN FETCH te.user
        WHERE te.slot.id = :slotId AND (te.endMonth IS NULL OR te.endMonth >= :month)
        ORDER BY te.createdAt ASC
        """)
    List<TrainingEnrollment> findActiveSubscribersForSlot(@Param("slotId") UUID slotId, @Param("month") String month);

    /** Wszyscy kiedykolwiek zapisani na slot (archiwum usuniętego slotu — dane kontaktowe). */
    @Query("""
        SELECT te FROM TrainingEnrollment te
        JOIN FETCH te.user
        WHERE te.slot.id = :slotId
        ORDER BY te.createdAt ASC
        """)
    List<TrainingEnrollment> findAllForSlotWithUser(@Param("slotId") UUID slotId);

    /**
     * Subskrypcje terminowe, które już wygasły (endMonth przed bieżącym miesiącem) i nie dostały
     * jeszcze maila o zakończeniu. Dla schedulera (mail K). Rezygnacje mają flagę = true → pomijane.
     */
    @Query("""
        SELECT te FROM TrainingEnrollment te
        JOIN FETCH te.user
        JOIN FETCH te.slot
        WHERE te.endMonth IS NOT NULL AND te.endMonth < :month AND te.expiryNotified = false
        """)
    List<TrainingEnrollment> findExpiredNotNotified(@Param("month") String month);
}
