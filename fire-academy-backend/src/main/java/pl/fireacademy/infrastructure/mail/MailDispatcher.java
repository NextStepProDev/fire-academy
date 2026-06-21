package pl.fireacademy.infrastructure.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import pl.fireacademy.config.AppConfig;

/**
 * Wspólny punkt wysyłki maili HTML z ponawianiem (best-effort). Wołany z metod {@code @Async}
 * serwisów mailowych, więc pętla ponawiania działa na wątku puli {@code mailExecutor} i nie blokuje
 * żądania użytkownika.
 * <p>
 * Przejściowe błędy SMTP (chwilowa niedostępność Gmaila, sieć) są najczęstsze — kilka prób z odstępem
 * łapie ich większość. Po wyczerpaniu prób mail jest porzucany, ale logowany na poziomie ERROR ze stałym
 * markerem {@code MAIL_DELIVERY_FAILED}, żeby dało się go wychwycić w logach/monitoringu. Metoda nigdy
 * nie rzuca wyjątku — wysyłka maila nie może wywrócić operacji biznesowej (zapis i tak jest już zapisany).
 */
@Component
public class MailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MailDispatcher.class);
    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    // Odstępy (ms) PRZED kolejnymi próbami: przed 2. próbą, przed 3. próbą.
    private static final long[] DEFAULT_BACKOFF_MS = {2_000L, 5_000L};

    private final JavaMailSender mailSender;
    private final AppConfig appConfig;
    private final int maxAttempts;
    private final long[] backoffMs;

    @Autowired
    public MailDispatcher(JavaMailSender mailSender, AppConfig appConfig) {
        this(mailSender, appConfig, DEFAULT_MAX_ATTEMPTS, DEFAULT_BACKOFF_MS);
    }

    /** Konstruktor dla testów — pozwala skrócić/wyzerować odstępy ponawiania. */
    MailDispatcher(JavaMailSender mailSender, AppConfig appConfig, int maxAttempts, long[] backoffMs) {
        this.mailSender = mailSender;
        this.appConfig = appConfig;
        this.maxAttempts = maxAttempts;
        this.backoffMs = backoffMs;
    }

    /** Wysyła maila HTML z ponawianiem przy błędach SMTP. Nie rzuca wyjątku (best-effort). */
    public void sendHtml(String to, String subject, String htmlBody) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                var message = mailSender.createMimeMessage();
                var helper = new MimeMessageHelper(message, true);
                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(htmlBody, true);
                helper.setFrom(appConfig.getMail().getFrom());
                mailSender.send(message);
                if (attempt > 1) {
                    log.info("Email sent to {} on attempt {}/{}", to, attempt, maxAttempts);
                } else {
                    log.info("Email sent to: {}", to);
                }
                return;
            } catch (MailException | jakarta.mail.MessagingException e) {
                if (attempt < maxAttempts) {
                    long delay = backoffMs[Math.min(attempt - 1, backoffMs.length - 1)];
                    log.warn("Email to {} failed (attempt {}/{}), retrying in {} ms: {}",
                            to, attempt, maxAttempts, delay, e.getMessage());
                    if (!sleep(delay)) {
                        return; // przerwano w trakcie odczekiwania
                    }
                } else {
                    log.error("MAIL_DELIVERY_FAILED to={} after {} attempts", to, maxAttempts, e);
                }
            }
        }
    }

    /** @return false, gdy wątek został przerwany podczas oczekiwania. */
    private boolean sleep(long millis) {
        if (millis <= 0) {
            return true;
        }
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Mail retry backoff interrupted; aborting send");
            return false;
        }
    }
}
