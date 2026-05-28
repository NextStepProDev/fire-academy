package pl.fireacademy.infrastructure.mail;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import pl.fireacademy.config.AdminEmailConfig;
import pl.fireacademy.config.AppConfig;
import pl.fireacademy.infrastructure.i18n.MessageService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class EnrollmentMailService {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentMailService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final JavaMailSender mailSender;
    private final AppConfig appConfig;
    private final AdminEmailConfig adminEmailConfig;
    private final MessageService msg;

    public EnrollmentMailService(JavaMailSender mailSender, AppConfig appConfig,
                                 AdminEmailConfig adminEmailConfig, MessageService msg) {
        this.mailSender = mailSender;
        this.appConfig = appConfig;
        this.adminEmailConfig = adminEmailConfig;
        this.msg = msg;
    }

    @Async("mailExecutor")
    public void sendEnrollmentConfirmation(String recipientEmail, String firstName,
                                            String eventTypeName, LocalDate date,
                                            @Nullable String location) {
        String subject = msg.get("email.enrollment.confirmation.subject");
        String dateStr = date.format(DATE_FMT);

        String locationHtml = location != null
                ? "<p style=\"font-size: 16px; line-height: 1.6;\">%s</p>".formatted(
                        msg.get("email.enrollment.confirmation.location", location))
                : "";

        String body = """
            <html>
            <body style="font-family: Arial, sans-serif; background-color: #1a1816; color: #e0e0e0; padding: 20px;">
                <div style="max-width: 600px; margin: 0 auto; background-color: #312e2b; border-radius: 12px; overflow: hidden;">
                    <div style="padding: 30px;">
                        <h1 style="color: #f97316;">%s</h1>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        %s
                        <p style="font-size: 16px; line-height: 1.6; margin-top: 20px;">%s</p>
                        <hr style="border-color: #4a4a4a; margin: 20px 0;" />
                        <p style="font-size: 12px; color: #9ca3af; text-align: center;">%s</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
                msg.get("email.enrollment.confirmation.greeting", firstName),
                msg.get("email.enrollment.confirmation.body", eventTypeName),
                msg.get("email.enrollment.confirmation.date", dateStr),
                locationHtml,
                msg.get("email.enrollment.confirmation.footer"),
                msg.get("email.footer")
        );

        sendEmail(recipientEmail, subject, body);
    }

    @Async("mailExecutor")
    public void sendEnrollmentNotification(String eventTypeName, String participantName,
                                            String participantEmail, LocalDate date) {
        String subject = msg.get("email.enrollment.notification.subject", eventTypeName);
        String dateStr = date.format(DATE_FMT);

        String body = """
            <html>
            <body style="font-family: Arial, sans-serif; background-color: #1a1816; color: #e0e0e0; padding: 20px;">
                <div style="max-width: 600px; margin: 0 auto; background-color: #312e2b; border-radius: 12px; overflow: hidden;">
                    <div style="padding: 30px;">
                        <h1 style="color: #f97316;">%s</h1>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
                subject,
                msg.get("email.enrollment.notification.body", eventTypeName),
                msg.get("email.enrollment.notification.participant", participantName, participantEmail),
                msg.get("email.enrollment.confirmation.date", dateStr)
        );

        for (String adminEmail : adminEmailConfig.getAdminEmails()) {
            sendEmail(adminEmail, subject, body);
        }
    }

    private void sendEmail(String to, String subject, String body) {
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true);
            helper.setFrom(appConfig.getMail().getFrom());
            mailSender.send(message);
            log.info("Enrollment email sent to: {}", to);
        } catch (MailException | jakarta.mail.MessagingException e) {
            log.error("Failed to send enrollment email to: {}", to, e);
        }
    }
}
