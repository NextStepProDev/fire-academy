package pl.fireacademy.domain.training;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TrainingRefundRepository extends JpaRepository<TrainingRefund, UUID> {

    boolean existsByEnrollmentIdAndSessionDate(UUID enrollmentId, LocalDate sessionDate);

    /** Total surplus a subscription has available as credit: refunds settled as CREDITED. */
    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM TrainingRefund r "
            + "WHERE r.enrollment.id = :enrollmentId AND r.settlementType = 'CREDITED'")
    BigDecimal sumCreditedForEnrollment(@Param("enrollmentId") UUID enrollmentId);

    /** Money owed for a subscription's cancelled paid sessions that the organizer has not yet resolved (shown to the client). */
    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM TrainingRefund r "
            + "WHERE r.enrollment.id = :enrollmentId AND r.settledAt IS NULL")
    BigDecimal sumPendingForEnrollment(@Param("enrollmentId") UUID enrollmentId);

    /** Unresolved refunds for one month — added back to the current bill to show what the client actually paid. */
    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM TrainingRefund r "
            + "WHERE r.enrollment.id = :enrollmentId AND r.yearMonth = :month AND r.settledAt IS NULL")
    BigDecimal sumPendingForEnrollmentAndMonth(@Param("enrollmentId") UUID enrollmentId, @Param("month") String month);

    /** All refunds (pending or settled) already registered for one month — the running total a new one must not push past what was paid. */
    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM TrainingRefund r "
            + "WHERE r.enrollment.id = :enrollmentId AND r.yearMonth = :month")
    BigDecimal sumForEnrollmentAndMonth(@Param("enrollmentId") UUID enrollmentId, @Param("month") String month);

    /** Months (YYYY-MM) a subscription's surplus came from, earliest first — the surplus never discounts a month before this. */
    @Query("SELECT r.yearMonth FROM TrainingRefund r "
            + "WHERE r.enrollment.id = :enrollmentId AND r.settlementType = 'CREDITED' ORDER BY r.yearMonth ASC")
    List<String> creditedMonthsForEnrollment(@Param("enrollmentId") UUID enrollmentId);

    /** All refunds for a slot's session date (any settlement) — to decide/undo a restore. */
    @Query("SELECT r FROM TrainingRefund r WHERE r.sessionDate = :date AND r.enrollment.slot.id = :slotId")
    List<TrainingRefund> findBySlotAndDate(@Param("slotId") UUID slotId, @Param("date") LocalDate date);

    /** Enrollment ids that still have an unresolved refund for a slot's session date — drives the "do zwrotu" badge. */
    @Query("SELECT r.enrollment.id FROM TrainingRefund r "
            + "WHERE r.enrollment.slot.id = :slotId AND r.sessionDate = :date AND r.settledAt IS NULL")
    List<UUID> findPendingEnrollmentIdsForSlotAndDate(@Param("slotId") UUID slotId, @Param("date") LocalDate date);

    /** All refunds for a date across all slots (any settlement) — to decide/undo a day-off removal. */
    @Query("SELECT r FROM TrainingRefund r WHERE r.sessionDate = :date")
    List<TrainingRefund> findByDate(@Param("date") LocalDate date);

    /** All of one subscriber's pending refunds — for the "settle everything for this person" bulk action. */
    @Query("SELECT r FROM TrainingRefund r WHERE r.enrollment.user.id = :userId AND r.settledAt IS NULL")
    List<TrainingRefund> findPendingByUser(@Param("userId") UUID userId);

    /** Pending refunds of a subscription in a month — to revoke when its payment is reverted. */
    @Query("""
        SELECT r FROM TrainingRefund r
        WHERE r.enrollment.id = :enrollmentId AND r.yearMonth = :month AND r.settledAt IS NULL
        """)
    List<TrainingRefund> findPendingByEnrollmentAndMonth(@Param("enrollmentId") UUID enrollmentId,
                                                         @Param("month") String month);

    /** Whether the month has an already-settled refund (cash paid out / surplus credited) — blocks un-paying it. */
    @Query("""
        SELECT COUNT(r) > 0 FROM TrainingRefund r
        WHERE r.enrollment.id = :enrollmentId AND r.yearMonth = :month AND r.settledAt IS NOT NULL
        """)
    boolean existsSettledByEnrollmentAndMonth(@Param("enrollmentId") UUID enrollmentId,
                                              @Param("month") String month);

    /** Refunds list for the admin "Zwroty" view (pending or settled), with subscriber + slot data. */
    @Query("""
        SELECT r FROM TrainingRefund r
        JOIN FETCH r.enrollment e
        JOIN FETCH e.user
        JOIN FETCH e.slot s
        JOIN FETCH s.eventType
        WHERE (:settled = true AND r.settledAt IS NOT NULL)
           OR (:settled = false AND r.settledAt IS NULL)
        ORDER BY r.sessionDate ASC, r.createdAt ASC
        """)
    List<TrainingRefund> findForAdmin(@Param("settled") boolean settled);
}
