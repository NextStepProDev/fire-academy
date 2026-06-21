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
 * A shared point for sending HTML emails with retries (best-effort). Called from the {@code @Async}
 * methods of the mail services, so the retry loop runs on the {@code mailExecutor} pool thread and does not block
 * the user's request.
 * <p>
 * Transient SMTP errors (a brief Gmail unavailability, network) are the most common — a few spaced-out attempts
 * catch most of them. After the attempts are exhausted the email is dropped, but logged at ERROR level with a fixed
 * marker {@code MAIL_DELIVERY_FAILED}, so it can be caught in logs/monitoring. The method never
 * throws — sending an email must not break the business operation (the enrollment is already saved anyway).
 */
@Component
public class MailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MailDispatcher.class);
    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    // Delays (ms) BEFORE subsequent attempts: before the 2nd attempt, before the 3rd attempt.
    private static final long[] DEFAULT_BACKOFF_MS = {2_000L, 5_000L};

    private final JavaMailSender mailSender;
    private final AppConfig appConfig;
    private final int maxAttempts;
    private final long[] backoffMs;

    @Autowired
    public MailDispatcher(JavaMailSender mailSender, AppConfig appConfig) {
        this(mailSender, appConfig, DEFAULT_MAX_ATTEMPTS, DEFAULT_BACKOFF_MS);
    }

    /** Constructor for tests — allows shortening/zeroing the retry delays. */
    MailDispatcher(JavaMailSender mailSender, AppConfig appConfig, int maxAttempts, long[] backoffMs) {
        this.mailSender = mailSender;
        this.appConfig = appConfig;
        this.maxAttempts = maxAttempts;
        this.backoffMs = backoffMs;
    }

    /** Sends an HTML email with retries on SMTP errors. Does not throw (best-effort). */
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
                        return; // interrupted while waiting
                    }
                } else {
                    log.error("MAIL_DELIVERY_FAILED to={} after {} attempts", to, maxAttempts, e);
                }
            }
        }
    }

    /** @return false when the thread was interrupted while waiting. */
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
