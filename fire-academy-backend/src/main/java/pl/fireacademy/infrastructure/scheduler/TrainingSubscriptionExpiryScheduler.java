package pl.fireacademy.infrastructure.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.fireacademy.domain.training.TrainingEnrollmentRepository;
import pl.fireacademy.domain.training.TrainingSlot;
import pl.fireacademy.infrastructure.mail.TrainingMailService;

import java.time.YearMonth;

/**
 * Codziennie powiadamia użytkowników, których terminowa subskrypcja treningu właśnie wygasła
 * (mail K), żeby mogli zapisać się ponownie. Rezygnacje mają {@code expiryNotified=true}
 * (dostały już mail o rezygnacji), więc są pomijane.
 */
@Component
public class TrainingSubscriptionExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(TrainingSubscriptionExpiryScheduler.class);

    private final TrainingEnrollmentRepository enrollmentRepository;
    private final TrainingMailService trainingMail;

    public TrainingSubscriptionExpiryScheduler(TrainingEnrollmentRepository enrollmentRepository,
                                               TrainingMailService trainingMail) {
        this.enrollmentRepository = enrollmentRepository;
        this.trainingMail = trainingMail;
    }

    @Scheduled(cron = "0 15 4 * * *")
    @Transactional
    public void notifyExpiredSubscriptions() {
        String month = YearMonth.now().toString();
        var expired = enrollmentRepository.findExpiredNotNotified(month);
        for (var te : expired) {
            TrainingSlot slot = te.getSlot();
            var instr = slot.getInstructor();
            var info = new TrainingMailService.SlotInfo(
                    slot.getEventType().getName(),
                    instr != null ? instr.getFirstName() + " " + instr.getLastName() : null,
                    slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime(), slot.getPrice());
            var u = te.getUser();
            trainingMail.sendSubscriptionExpired(u.getEmail(), u.getFirstName(), info);
            te.setExpiryNotified(true);
        }
        if (!expired.isEmpty()) {
            log.info("Training subscriptions: sent {} expiry notifications for month < {}", expired.size(), month);
        }
    }
}
