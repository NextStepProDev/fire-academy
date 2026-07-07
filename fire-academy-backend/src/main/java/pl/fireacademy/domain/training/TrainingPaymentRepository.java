package pl.fireacademy.domain.training;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainingPaymentRepository extends JpaRepository<TrainingPayment, UUID> {

    boolean existsByEnrollmentIdAndYearMonth(UUID enrollmentId, String yearMonth);

    Optional<TrainingPayment> findByEnrollmentIdAndYearMonth(UUID enrollmentId, String yearMonth);

    @Transactional
    void deleteByEnrollmentIdAndYearMonth(UUID enrollmentId, String yearMonth);

    /** Ids of subscriptions paid for a given month among the provided ones. */
    @Query("SELECT p.enrollment.id FROM TrainingPayment p WHERE p.enrollment.id IN :ids AND p.yearMonth = :month")
    List<UUID> findPaidEnrollmentIds(@Param("ids") Collection<UUID> ids, @Param("month") String month);

    /** Payment records for a given month among the provided subscriptions — carries when each was marked paid. */
    @Query("SELECT p FROM TrainingPayment p WHERE p.enrollment.id IN :ids AND p.yearMonth = :month")
    List<TrainingPayment> findPaidForMonth(@Param("ids") Collection<UUID> ids, @Param("month") String month);

    /** Months (YYYY-MM) already paid for a subscription — to know where surplus credit has landed. */
    @Query("SELECT p.yearMonth FROM TrainingPayment p WHERE p.enrollment.id = :enrollmentId")
    List<String> findPaidMonths(@Param("enrollmentId") UUID enrollmentId);

    /** Total surplus already consumed by this subscription's paid months. */
    @Query("SELECT COALESCE(SUM(p.creditApplied), 0) FROM TrainingPayment p WHERE p.enrollment.id = :enrollmentId")
    BigDecimal sumCreditAppliedForEnrollment(@Param("enrollmentId") UUID enrollmentId);

    /** All payments of a user's subscriptions, newest first — the admin training history of one person. */
    @Query("""
        SELECT p FROM TrainingPayment p
        JOIN FETCH p.enrollment e
        JOIN FETCH e.slot s
        JOIN FETCH s.eventType
        WHERE e.user.id = :userId
        ORDER BY p.createdAt DESC
        """)
    List<TrainingPayment> findByUserWithSlot(@Param("userId") UUID userId);
}
