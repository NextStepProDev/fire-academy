package pl.fireacademy.domain.training;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TrainingEnrollmentRepository extends JpaRepository<TrainingEnrollment, UUID> {

    /** Number of subscriptions covering a given month for a single slot. */
    @Query("""
        SELECT COUNT(te) FROM TrainingEnrollment te
        WHERE te.slot.id = :slotId
          AND te.startMonth <= :month AND (te.endMonth IS NULL OR te.endMonth >= :month)
        """)
    long countCovering(@Param("slotId") UUID slotId, @Param("month") String month);

    /** Number of subscriptions covering a month, grouped by slot (batch). */
    @Query("""
        SELECT te.slot.id, COUNT(te) FROM TrainingEnrollment te
        WHERE te.slot.id IN :slotIds
          AND te.startMonth <= :month AND (te.endMonth IS NULL OR te.endMonth >= :month)
        GROUP BY te.slot.id
        """)
    List<Object[]> countCoveringBySlotIds(@Param("slotIds") Collection<UUID> slotIds, @Param("month") String month);

    /** Whether the user already has a slot subscription active in/after the given month (collision). */
    @Query("""
        SELECT COUNT(te) > 0 FROM TrainingEnrollment te
        WHERE te.user.id = :userId AND te.slot.id = :slotId
          AND (te.endMonth IS NULL OR te.endMonth >= :month)
        """)
    boolean existsActiveFor(@Param("userId") UUID userId, @Param("slotId") UUID slotId, @Param("month") String month);

    /** Active subscriptions of the user (not ended and on non-deleted slots). */
    @Query("""
        SELECT te FROM TrainingEnrollment te
        WHERE te.user.id = :userId
          AND te.slot.deletedAt IS NULL
          AND (te.endMonth IS NULL OR te.endMonth >= :month)
        ORDER BY te.slot.dayOfWeek ASC, te.slot.startTime ASC
        """)
    List<TrainingEnrollment> findActiveByUser(@Param("userId") UUID userId, @Param("month") String month);

    long countBySlotId(UUID slotId);

    /** Subscriptions covering a given month for a slot (admin roster). */
    @Query("""
        SELECT te FROM TrainingEnrollment te
        WHERE te.slot.id = :slotId
          AND te.startMonth <= :month AND (te.endMonth IS NULL OR te.endMonth >= :month)
        ORDER BY te.createdAt ASC
        """)
    List<TrainingEnrollment> findCoveringForSlot(@Param("slotId") UUID slotId, @Param("month") String month);

    /**
     * Current recipients of slot notifications: subscriptions not yet ended
     * (indefinite or ending in/after the current month, including future ones). For emails D/E.
     */
    @Query("""
        SELECT te FROM TrainingEnrollment te
        JOIN FETCH te.user
        WHERE te.slot.id = :slotId AND (te.endMonth IS NULL OR te.endMonth >= :month)
        ORDER BY te.createdAt ASC
        """)
    List<TrainingEnrollment> findActiveSubscribersForSlot(@Param("slotId") UUID slotId, @Param("month") String month);

    /** Everyone ever enrolled in the slot (archive of a deleted slot — contact data). */
    @Query("""
        SELECT te FROM TrainingEnrollment te
        JOIN FETCH te.user
        WHERE te.slot.id = :slotId
        ORDER BY te.createdAt ASC
        """)
    List<TrainingEnrollment> findAllForSlotWithUser(@Param("slotId") UUID slotId);

    /**
     * Fixed-term subscriptions that have already expired (endMonth before the current month) and have not
     * received the end-of-subscription email yet. For the scheduler (email K). Cancellations have the flag = true → skipped.
     */
    @Query("""
        SELECT te FROM TrainingEnrollment te
        JOIN FETCH te.user
        JOIN FETCH te.slot
        WHERE te.endMonth IS NOT NULL AND te.endMonth < :month AND te.expiryNotified = false
        """)
    List<TrainingEnrollment> findExpiredNotNotified(@Param("month") String month);
}
