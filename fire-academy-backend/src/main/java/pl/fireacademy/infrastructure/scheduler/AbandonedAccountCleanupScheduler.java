package pl.fireacademy.infrastructure.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.fireacademy.api.user.UserService;

/**
 * Codzienne czyszczenie porzuconych kont OAuth (logowanie Google), które nigdy nie zaakceptowały
 * polityki prywatności. RODO: bez zgody nie mamy podstawy trzymać danych w nieskończoność, a sama
 * aplikacja i tak nie wpuszcza takiego konta dalej niż ekran zgody. Próg dni konfigurowalny
 * (domyślnie 7) — daje userowi czas na powrót i domknięcie konta przed skasowaniem.
 */
@Component
public class AbandonedAccountCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(AbandonedAccountCleanupScheduler.class);

    private final UserService userService;
    private final int retentionDays;

    public AbandonedAccountCleanupScheduler(UserService userService,
                                            @Value("${app.unconsented-account-retention-days:7}") int retentionDays) {
        this.userService = userService;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void purgeAbandonedAccounts() {
        int deleted = userService.purgeAbandonedUnconsentedAccounts(retentionDays);
        if (deleted > 0) {
            log.info("Purged {} abandoned OAuth accounts without privacy consent (older than {} days)",
                    deleted, retentionDays);
        }
    }
}
