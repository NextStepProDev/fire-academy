package pl.fireacademy.infrastructure.mail;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import pl.fireacademy.config.AdminEmailConfig;
import pl.fireacademy.config.AppConfig;
import pl.fireacademy.infrastructure.i18n.MessageService;

import pl.fireacademy.api.admin.EventDtos.FieldChange;

import pl.fireacademy.domain.event.EventCategory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class EnrollmentMailService {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentMailService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Map<EventCategory, String> CATEGORY_SLUGS = Map.of(
            EventCategory.TRAINING, "treningi",
            EventCategory.CAMP, "obozy",
            EventCategory.COURSE, "szkolenia"
    );

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
                                            @Nullable String location,
                                            EventCategory category, String eventId) {
        String subject = msg.get("email.enrollment.confirmation.subject");
        String dateStr = date.format(DATE_FMT);
        String safeFirstName = HtmlUtils.htmlEscape(firstName);
        String safeEventTypeName = HtmlUtils.htmlEscape(eventTypeName);
        String safeLocation = location != null ? HtmlUtils.htmlEscape(location) : null;

        String locationHtml = safeLocation != null
                ? "<p style=\"font-size: 16px; line-height: 1.6;\">%s</p>".formatted(
                        msg.get("email.enrollment.confirmation.location", safeLocation))
                : "";

        String content = """
                        <h1 style="color: #f97316; font-size: 20px;">%s</h1>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        %s
                        %s
                        <p style="font-size: 16px; line-height: 1.6; margin-top: 20px;">%s</p>
                        <p style="font-size: 14px; line-height: 1.6; color: #9ca3af; margin-top: 12px;">%s</p>
            """.formatted(
                msg.get("email.enrollment.confirmation.greeting", safeFirstName),
                msg.get("email.enrollment.confirmation.body", safeEventTypeName),
                msg.get("email.enrollment.confirmation.date", dateStr),
                locationHtml,
                eventButton(category, eventId),
                msg.get("email.enrollment.confirmation.footer"),
                msg.get("email.enrollment.cancel.info")
        );

        sendEmail(recipientEmail, subject, brandedTemplate(content));
    }

    @Async("mailExecutor")
    public void sendEnrollmentNotification(String eventTypeName, String participantName,
                                            String participantEmail, LocalDate date,
                                            EventCategory category, String eventId) {
        String safeEventTypeName = HtmlUtils.htmlEscape(eventTypeName);
        String safeParticipantName = HtmlUtils.htmlEscape(participantName);
        String safeParticipantEmail = HtmlUtils.htmlEscape(participantEmail);
        String subject = msg.get("email.enrollment.notification.subject", safeEventTypeName);
        String dateStr = date.format(DATE_FMT);

        String content = """
                        <h1 style="color: #f97316; font-size: 20px;">%s</h1>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        %s
            """.formatted(
                subject,
                msg.get("email.enrollment.notification.body", safeEventTypeName),
                msg.get("email.enrollment.notification.participant", safeParticipantName, safeParticipantEmail),
                msg.get("email.enrollment.confirmation.date", dateStr),
                eventButton(category, eventId)
        );

        for (String adminEmail : adminEmailConfig.getAdminEmails()) {
            sendEmail(adminEmail, subject, brandedTemplate(content));
        }
    }

    @Async("mailExecutor")
    public void sendAdminEnrollmentConfirmation(String recipientEmail, String firstName,
                                                 String eventTypeName, LocalDate date,
                                                 @Nullable String location,
                                                 EventCategory category, String eventId) {
        String subject = msg.get("email.enrollment.admin.confirmation.subject");
        String dateStr = date.format(DATE_FMT);
        String safeFirstName = HtmlUtils.htmlEscape(firstName);
        String safeEventTypeName = HtmlUtils.htmlEscape(eventTypeName);
        String safeLocation = location != null ? HtmlUtils.htmlEscape(location) : null;

        String locationHtml = safeLocation != null
                ? "<p style=\"font-size: 16px; line-height: 1.6;\">%s</p>".formatted(
                        msg.get("email.enrollment.confirmation.location", safeLocation))
                : "";

        String content = """
                        <h1 style="color: #f97316; font-size: 20px;">%s</h1>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        %s
                        %s
                        <p style="font-size: 16px; line-height: 1.6; margin-top: 20px;">%s</p>
                        <p style="font-size: 14px; line-height: 1.6; color: #9ca3af; margin-top: 12px;">%s</p>
            """.formatted(
                msg.get("email.enrollment.admin.confirmation.greeting", safeFirstName),
                msg.get("email.enrollment.admin.confirmation.body", safeEventTypeName),
                msg.get("email.enrollment.confirmation.date", dateStr),
                locationHtml,
                eventButton(category, eventId),
                msg.get("email.enrollment.confirmation.footer"),
                msg.get("email.enrollment.cancel.info")
        );

        sendEmail(recipientEmail, subject, brandedTemplate(content));
    }

    @Async("mailExecutor")
    public void sendAdminEnrollmentNotification(String eventTypeName, String participantName,
                                                 String participantEmail, LocalDate date,
                                                 EventCategory category, String eventId) {
        String safeEventTypeName = HtmlUtils.htmlEscape(eventTypeName);
        String safeParticipantName = HtmlUtils.htmlEscape(participantName);
        String safeParticipantEmail = HtmlUtils.htmlEscape(participantEmail);
        String subject = msg.get("email.enrollment.admin.notification.subject", safeEventTypeName);
        String dateStr = date.format(DATE_FMT);

        String content = """
                        <h1 style="color: #f97316; font-size: 20px;">%s</h1>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        %s
            """.formatted(
                subject,
                msg.get("email.enrollment.admin.notification.body", safeEventTypeName),
                msg.get("email.enrollment.notification.participant", safeParticipantName, safeParticipantEmail),
                msg.get("email.enrollment.confirmation.date", dateStr),
                eventButton(category, eventId)
        );

        for (String adminEmail : adminEmailConfig.getAdminEmails()) {
            sendEmail(adminEmail, subject, brandedTemplate(content));
        }
    }

    @Async("mailExecutor")
    public void sendEventModificationNotification(String recipientEmail, String firstName,
                                                    String eventName, LocalDate date,
                                                    List<FieldChange> changes,
                                                    EventCategory category, String eventId) {
        String subject = msg.get("email.event.modified.subject");
        String safeFirstName = HtmlUtils.htmlEscape(firstName);
        String safeEventName = HtmlUtils.htmlEscape(eventName);

        String content = """
                        <h1 style="color: #f97316; font-size: 20px;">%s</h1>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <div style="background-color: #3d3a37; border-radius: 8px; padding: 16px; margin: 16px 0;">
                            <p style="font-size: 14px; font-weight: bold; margin-bottom: 8px;">%s</p>
                            %s
                        </div>
                        %s
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <p style="font-size: 14px; line-height: 1.6; color: #9ca3af; margin-top: 12px;">%s</p>
            """.formatted(
                msg.get("email.event.modified.greeting", safeFirstName),
                msg.get("email.event.modified.body", safeEventName, date.format(DATE_FMT)),
                msg.get("email.event.modified.changes"),
                renderChanges(changes),
                eventButton(category, eventId),
                msg.get("email.event.modified.footer"),
                msg.get("email.enrollment.cancel.info")
        );

        sendEmail(recipientEmail, subject, brandedTemplate(content));
    }

    @Async("mailExecutor")
    public void sendEventModificationAdminNotification(String eventName, LocalDate date,
                                                        List<FieldChange> changes,
                                                        EventCategory category, String eventId) {
        String safeEventName = HtmlUtils.htmlEscape(eventName);
        String subject = msg.get("email.event.modified.subject");

        String content = """
                        <h1 style="color: #f97316; font-size: 20px;">%s</h1>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <div style="background-color: #3d3a37; border-radius: 8px; padding: 16px; margin: 16px 0;">
                            <p style="font-size: 14px; font-weight: bold; margin-bottom: 8px;">%s</p>
                            %s
                        </div>
                        %s
            """.formatted(
                subject,
                msg.get("email.event.modified.admin.body", safeEventName, date.format(DATE_FMT)),
                msg.get("email.event.modified.changes"),
                renderChanges(changes),
                eventButton(category, eventId)
        );

        for (String adminEmail : adminEmailConfig.getAdminEmails()) {
            sendEmail(adminEmail, subject, brandedTemplate(content));
        }
    }

    @Async("mailExecutor")
    public void sendEnrollmentDeletionNotification(String recipientEmail, String firstName,
                                                    String eventName, LocalDate date,
                                                    EventCategory category, String eventId) {
        String subject = msg.get("email.enrollment.deletion.subject");
        String dateStr = date.format(DATE_FMT);
        String safeFirstName = HtmlUtils.htmlEscape(firstName);
        String safeEventName = HtmlUtils.htmlEscape(eventName);

        String content = """
                        <h1 style="color: #f97316; font-size: 20px;">%s</h1>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        %s
                        <p style="font-size: 16px; line-height: 1.6; margin-top: 20px;">%s</p>
            """.formatted(
                msg.get("email.enrollment.deletion.greeting", safeFirstName),
                msg.get("email.enrollment.deletion.body", safeEventName, dateStr),
                msg.get("email.enrollment.confirmation.date", dateStr),
                eventButton(category, eventId),
                msg.get("email.enrollment.deletion.footer")
        );

        sendEmail(recipientEmail, subject, brandedTemplate(content));
    }

    @Async("mailExecutor")
    public void sendEnrollmentDeletionAdminNotification(String eventName, String participantName,
                                                         String participantEmail, LocalDate date,
                                                         EventCategory category, String eventId) {
        String safeEventName = HtmlUtils.htmlEscape(eventName);
        String safeParticipantName = HtmlUtils.htmlEscape(participantName);
        String safeParticipantEmail = HtmlUtils.htmlEscape(participantEmail);
        String subject = msg.get("email.enrollment.deletion.admin.subject", safeEventName);
        String dateStr = date.format(DATE_FMT);

        String content = """
                        <h1 style="color: #f97316; font-size: 20px;">%s</h1>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        %s
            """.formatted(
                subject,
                msg.get("email.enrollment.deletion.admin.body", safeEventName, dateStr),
                msg.get("email.enrollment.notification.participant", safeParticipantName, safeParticipantEmail),
                msg.get("email.enrollment.confirmation.date", dateStr),
                eventButton(category, eventId)
        );

        for (String adminEmail : adminEmailConfig.getAdminEmails()) {
            sendEmail(adminEmail, subject, brandedTemplate(content));
        }
    }

    @Async("mailExecutor")
    public void sendBulkEventMessage(String recipientEmail, String firstName,
                                      String eventName, LocalDate date,
                                      @Nullable String location, String message,
                                      EventCategory category, String eventId) {
        String subject = eventName + " — " + msg.get("email.bulk.subject");
        String safeFirstName = HtmlUtils.htmlEscape(firstName);
        String safeEventName = HtmlUtils.htmlEscape(eventName);
        String safeMessage = HtmlUtils.htmlEscape(message).replace("\n", "<br/>");
        String dateStr = date.format(DATE_FMT);
        String safeLocation = location != null ? HtmlUtils.htmlEscape(location) : null;

        String locationHtml = safeLocation != null
                ? "<p style=\"font-size: 14px; margin: 4px 0;\"><strong>%s</strong> %s</p>".formatted(
                        msg.get("email.bulk.location"), safeLocation)
                : "";

        String content = """
                        <h1 style="color: #f97316; font-size: 20px;">%s</h1>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <div style="background-color: #3d3a37; border-left: 4px solid #f97316; border-radius: 8px; padding: 20px; margin: 24px 0;">
                            <p style="font-size: 16px; line-height: 1.8; margin: 0; color: #e0e0e0;">%s</p>
                        </div>
                        <div style="background-color: #292524; border-radius: 8px; padding: 16px; margin: 24px 0;">
                            <p style="font-size: 14px; margin: 4px 0;"><strong>%s</strong> %s</p>
                            <p style="font-size: 14px; margin: 4px 0;"><strong>%s</strong> %s</p>
                            %s
                        </div>
                        %s
                        <p style="font-size: 14px; color: #9ca3af; margin-top: 12px;">%s</p>
            """.formatted(
                msg.get("email.bulk.greeting", safeFirstName),
                msg.get("email.bulk.intro", safeEventName),
                safeMessage,
                msg.get("email.bulk.event"), safeEventName,
                msg.get("email.bulk.date"), dateStr,
                locationHtml,
                eventButton(category, eventId),
                msg.get("email.enrollment.cancel.info")
        );

        sendEmail(recipientEmail, subject, brandedTemplate(content));
    }

    private String renderChanges(List<FieldChange> changes) {
        var changesHtml = new StringBuilder();
        for (var change : changes) {
            changesHtml.append("""
                <p style="font-size: 14px; margin: 4px 0;">
                    <strong>%s:</strong>
                    <span style="text-decoration: line-through; color: #9ca3af;">%s</span>
                    → <span style="color: #f97316; font-weight: bold;">%s</span>
                </p>
                """.formatted(HtmlUtils.htmlEscape(change.field()),
                              HtmlUtils.htmlEscape(change.oldValue()),
                              HtmlUtils.htmlEscape(change.newValue())));
        }
        return changesHtml.toString();
    }

    private String eventButton(EventCategory category, String eventId) {
        String slug = CATEGORY_SLUGS.get(category);
        String eventUrl = appConfig.getSiteUrl() + "/" + slug + "/termin/" + eventId;
        return """
            <div style="text-align: center; margin: 28px 0;">
                <a href="%s" style="display: inline-block; background-color: #f97316; color: #ffffff; text-decoration: none; padding: 14px 32px; border-radius: 8px; font-weight: bold; font-size: 16px;">%s</a>
            </div>
            """.formatted(eventUrl, msg.get("email.event.view"));
    }

    private String brandedTemplate(String content) {
        String siteUrl = appConfig.getSiteUrl();
        String logoUrl = siteUrl + "/images/logo/logo-white.png";
        return """
            <html>
            <body style="font-family: Arial, sans-serif; background-color: #1a1816; color: #e0e0e0; padding: 20px; margin: 0;">
                <div style="max-width: 600px; margin: 0 auto; background-color: #312e2b; border-radius: 12px; overflow: hidden;">
                    <div style="background-color: #292524; padding: 24px 30px; text-align: center; border-bottom: 2px solid #f97316;">
                        <a href="%s" style="text-decoration: none;">
                            <img src="%s" alt="Fire Academy" width="170" style="display: inline-block; width: 170px; max-width: 70%%; height: auto;" />
                        </a>
                    </div>
                    <div style="padding: 30px;">
                        %s
                        <hr style="border-color: #4a4a4a; margin: 20px 0;" />
                        <p style="font-size: 12px; color: #9ca3af; text-align: center; margin: 4px 0;">
                            <a href="%s" style="color: #f97316; text-decoration: none;">%s</a>
                        </p>
                        <p style="font-size: 12px; color: #9ca3af; text-align: center; margin: 4px 0;">%s</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(siteUrl, logoUrl, content, siteUrl, msg.get("email.footer.visit"), msg.get("email.footer"));
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
