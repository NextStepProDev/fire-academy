package pl.fireacademy.domain.training;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TrainingPaymentRepository extends JpaRepository<TrainingPayment, UUID> {

    boolean existsByEnrollmentIdAndYearMonth(UUID enrollmentId, String yearMonth);

    @Transactional
    void deleteByEnrollmentIdAndYearMonth(UUID enrollmentId, String yearMonth);

    /** Ids of subscriptions paid for a given month among the provided ones. */
    @Query("SELECT p.enrollment.id FROM TrainingPayment p WHERE p.enrollment.id IN :ids AND p.yearMonth = :month")
    List<UUID> findPaidEnrollmentIds(@Param("ids") Collection<UUID> ids, @Param("month") String month);
}
