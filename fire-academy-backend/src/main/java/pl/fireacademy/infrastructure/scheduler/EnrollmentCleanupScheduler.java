package pl.fireacademy.infrastructure.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.fireacademy.domain.enrollment.EnrollmentRepository;

import java.time.LocalDate;

@Component
public class EnrollmentCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(EnrollmentCleanupScheduler.class);
    private static final int RETENTION_YEARS = 3;
    private final EnrollmentRepository enrollmentRepository;

    public EnrollmentCleanupScheduler(EnrollmentRepository enrollmentRepository) {
        this.enrollmentRepository = enrollmentRepository;
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void cleanupExpiredEnrollments() {
        LocalDate cutoffDate = LocalDate.now().minusYears(RETENTION_YEARS);
        int deleted = enrollmentRepository.deleteByEventEndedBefore(cutoffDate);
        if (deleted > 0) {
            log.info("RODO cleanup: deleted {} enrollments for events ended before {}", deleted, cutoffDate);
        }
    }
}
