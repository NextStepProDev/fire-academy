package pl.fireacademy.infrastructure.mail;

import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import pl.fireacademy.config.AdminEmailConfig;
import pl.fireacademy.config.AppConfig;
import pl.fireacademy.infrastructure.i18n.MessageService;

import pl.fireacademy.api.admin.EventDtos.FieldChange;

import pl.fireacademy.domain.event.Event;
import pl.fireacademy.domain.event.EventCategory;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class EnrollmentMailService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final Map<EventCategory, String> CATEGORY_SLUGS = Map.of(
            EventCategory.TRAINING, "treningi",
            EventCategory.CAMP, "obozy",
            EventCategory.COURSE, "szkolenia"
    );

    private final MailDispatcher mailDispatcher;
    private final AppConfig appConfig;
    private final AdminEmailConfig adminEmailConfig;
    private final MessageService msg;

    public EnrollmentMailService(MailDispatcher mailDispatcher, AppConfig appConfig,
                                 AdminEmailConfig adminEmailConfig, MessageService msg) {
        this.mailDispatcher = mailDispatcher;
        this.appConfig = appConfig;
        this.adminEmailConfig = adminEmailConfig;
        this.msg = msg;
    }

    @Async("mailExecutor")
    public void sendEnrollmentConfirmation(String recipientEmail, String firstName,
                                            String eventTypeName, String schedule,
                                            @Nullable String location,
                                            EventCategory category, String eventId) {
        String subject = msg.get("email.enrollment.confirmation.subject");
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
                msg.get("email.enrollment.confirmation.date", schedule),
                locationHtml,
                eventButton(category, eventId),
                msg.get("email.enrollment.confirmation.footer"),
                msg.get("email.enrollment.cancel.info")
        );

        sendEmail(recipientEmail, subject, brandedTemplate(content, category));
    }

    @Async("mailExecutor")
    public void sendEnrollmentNotification(String eventTypeName, String participantName,
                                            String participantEmail, String participantPhone,
                                            @Nullable String note, String schedule,
                                            EventCategory category, String eventId) {
        String safeEventTypeName = HtmlUtils.htmlEscape(eventTypeName);
        String subject = msg.get("email.enrollment.notification.subject", eventTypeName);
        String heading = msg.get("email.enrollment.notification.subject", safeEventTypeName);

        String content = """
                        <h1 style="color: #f97316; font-size: 20px;">%s</h1>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        %s
                        %s
            """.formatted(
                heading,
                msg.get("email.enrollment.notification.body", safeEventTypeName),
                msg.get("email.enrollment.notification.participant",
                        HtmlUtils.htmlEscape(participantName), HtmlUtils.htmlEscape(participantEmail)),
                msg.get("email.enrollment.notification.phone", HtmlUtils.htmlEscape(participantPhone)),
                msg.get("email.enrollment.confirmation.date", schedule),
                noteBlock(note),
                eventButton(category, eventId)
        );

        for (String adminEmail : adminEmailConfig.getAdminEmails()) {
            sendEmail(adminEmail, subject, brandedTemplate(content, category));
        }
    }

    @Async("mailExecutor")
    public void sendAdminEnrollmentConfirmation(String recipientEmail, String firstName,
                                                 String eventTypeName, String schedule,
                                                 @Nullable String location,
                                                 EventCategory category, String eventId) {
        String subject = msg.get("email.enrollment.admin.confirmation.subject");
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
                msg.get("email.enrollment.confirmation.date", schedule),
                locationHtml,
                eventButton(category, eventId),
                msg.get("email.enrollment.confirmation.footer"),
                msg.get("email.enrollment.cancel.info")
        );

        sendEmail(recipientEmail, subject, brandedTemplate(content, category));
    }

    @Async("mailExecutor")
    public void sendAdminEnrollmentNotification(String eventTypeName, String participantName,
                                                 String participantEmail, String participantPhone,
                                                 @Nullable String note, String schedule,
                                                 EventCategory category, String eventId) {
        String safeEventTypeName = HtmlUtils.htmlEscape(eventTypeName);
        String subject = msg.get("email.enrollment.admin.notification.subject", eventTypeName);
        String heading = msg.get("email.enrollment.admin.notification.subject", safeEventTypeName);

        String content = """
                        <h1 style="color: #f97316; font-size: 20px;">%s</h1>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        %s
                        %s
            """.formatted(
                heading,
                msg.get("email.enrollment.admin.notification.body", safeEventTypeName),
                msg.get("email.enrollment.notification.participant",
                        HtmlUtils.htmlEscape(participantName), HtmlUtils.htmlEscape(participantEmail)),
                msg.get("email.enrollment.notification.phone", HtmlUtils.htmlEscape(participantPhone)),
                msg.get("email.enrollment.confirmation.date", schedule),
                noteBlock(note),
                eventButton(category, eventId)
        );

        for (String adminEmail : adminEmailConfig.getAdminEmails()) {
            sendEmail(adminEmail, subject, brandedTemplate(content, category));
        }
    }

    @Async("mailExecutor")
    public void sendEventModificationNotification(String recipientEmail, String firstName,
                                                    String eventName, String schedule,
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
                msg.get("email.event.modified.body", safeEventName, schedule),
                msg.get("email.event.modified.changes"),
                renderChanges(changes),
                eventButton(category, eventId),
                msg.get("email.event.modified.footer"),
                msg.get("email.enrollment.cancel.info")
        );

        sendEmail(recipientEmail, subject, brandedTemplate(content, category));
    }

    @Async("mailExecutor")
    public void sendEventModificationAdminNotification(String eventName, String schedule,
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
                msg.get("email.event.modified.admin.body", safeEventName, schedule),
                msg.get("email.event.modified.changes"),
                renderChanges(changes),
                eventButton(category, eventId)
        );

        for (String adminEmail : adminEmailConfig.getAdminEmails()) {
            sendEmail(adminEmail, subject, brandedTemplate(content, category));
        }
    }

    @Async("mailExecutor")
    public void sendEnrollmentDeletionNotification(String recipientEmail, String firstName,
                                                    String eventName, String schedule,
                                                    EventCategory category, String eventId) {
        String subject = msg.get("email.enrollment.deletion.subject");
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
                msg.get("email.enrollment.deletion.body", safeEventName, schedule),
                msg.get("email.enrollment.confirmation.date", schedule),
                eventButton(category, eventId),
                msg.get("email.enrollment.deletion.footer")
        );

        sendEmail(recipientEmail, subject, brandedTemplate(content, category));
    }

    @Async("mailExecutor")
    public void sendEnrollmentDeletionAdminNotification(String eventName, String participantName,
                                                         String participantEmail, String schedule,
                                                         EventCategory category, String eventId) {
        String safeEventName = HtmlUtils.htmlEscape(eventName);
        String safeParticipantName = HtmlUtils.htmlEscape(participantName);
        String safeParticipantEmail = HtmlUtils.htmlEscape(participantEmail);
        String subject = msg.get("email.enrollment.deletion.admin.subject", eventName);
        String heading = msg.get("email.enrollment.deletion.admin.subject", safeEventName);

        String content = """
                        <h1 style="color: #f97316; font-size: 20px;">%s</h1>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        %s
            """.formatted(
                heading,
                msg.get("email.enrollment.deletion.admin.body", safeEventName, schedule),
                msg.get("email.enrollment.notification.participant", safeParticipantName, safeParticipantEmail),
                msg.get("email.enrollment.confirmation.date", schedule),
                eventButton(category, eventId)
        );

        for (String adminEmail : adminEmailConfig.getAdminEmails()) {
            sendEmail(adminEmail, subject, brandedTemplate(content, category));
        }
    }

    /**
     * Jeden zbiorczy mail do organizatorów: uczestnik samodzielnie usunął konto. Wysyłany ZAWSZE przy
     * samodzielnym usunięciu (przy usunięciu z panelu admin sam zna wynik). Gdy usunięcie zwolniło miejsca
     * na przyszłych wydarzeniach, mail zawiera dodatkowo ich listę.
     * {@code eventLines} to gotowe wiersze „nazwa — termin" (budowane w warstwie aplikacji, w transakcji).
     */
    @Async("mailExecutor")
    public void sendAccountSelfDeletedNotification(String participantName, String participantEmail,
                                                   List<String> eventLines) {
        String subject = msg.get("email.account.deleted.admin.subject");

        String seatsHtml = "";
        if (!eventLines.isEmpty()) {
            String listHtml = eventLines.stream()
                    .map(line -> "<p style=\"font-size: 15px; line-height: 1.6; margin: 4px 0;\">• %s</p>"
                            .formatted(HtmlUtils.htmlEscape(line)))
                    .reduce("", String::concat);
            seatsHtml = """
                            <p style="font-size: 16px; line-height: 1.6;">%s</p>
                            <div style="background-color: #3d3a37; border-left: 4px solid #f97316; border-radius: 8px; padding: 14px 18px; margin: 16px 0;">
                                %s
                            </div>
                """.formatted(msg.get("email.account.deleted.admin.seats"), listHtml);
        }

        String content = """
                        <h1 style="color: #f97316; font-size: 20px;">%s</h1>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        %s
            """.formatted(
                subject,
                msg.get("email.account.deleted.admin.body",
                        HtmlUtils.htmlEscape(participantName), HtmlUtils.htmlEscape(participantEmail)),
                seatsHtml
        );

        // Branding neutralny (Fire Academy) — lista może obejmować różne kategorie.
        String body = brandedTemplate(content, EventCategory.TRAINING);
        for (String adminEmail : adminEmailConfig.getAdminEmails()) {
            sendEmail(adminEmail, subject, body);
        }
    }

    @Async("mailExecutor")
    public void sendBulkEventMessage(String recipientEmail, String firstName,
                                      String eventName, String schedule,
                                      @Nullable String location, String message,
                                      @Nullable String senderName,
                                      EventCategory category, String eventId) {
        String subject = eventName + " — " + msg.get("email.bulk.subject");
        String safeFirstName = HtmlUtils.htmlEscape(firstName);
        String safeEventName = HtmlUtils.htmlEscape(eventName);
        String safeMessage = HtmlUtils.htmlEscape(message).replace("\n", "<br/>");
        String safeLocation = location != null ? HtmlUtils.htmlEscape(location) : null;

        String locationHtml = safeLocation != null
                ? "<p style=\"font-size: 14px; margin: 4px 0;\"><strong>%s</strong> %s</p>".formatted(
                        msg.get("email.bulk.location"), safeLocation)
                : "";

        String signatureHtml = (senderName != null && !senderName.isBlank())
                ? """
                  <p style="font-size: 16px; line-height: 1.6; margin: 20px 0 0;">%s<br/><strong>%s</strong><br/>%s</p>
                  """.formatted(msg.get("email.bulk.signature"),
                                HtmlUtils.htmlEscape(senderName), msg.get("email.footer"))
                : "";

        String content = """
                        <h1 style="color: #f97316; font-size: 20px;">%s</h1>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <div style="background-color: #3d3a37; border-left: 4px solid #f97316; border-radius: 8px; padding: 20px; margin: 24px 0;">
                            <p style="font-size: 16px; line-height: 1.8; margin: 0; color: #e0e0e0;">%s</p>
                        </div>
                        %s
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
                signatureHtml,
                msg.get("email.bulk.event"), safeEventName,
                msg.get("email.bulk.date"), schedule,
                locationHtml,
                eventButton(category, eventId),
                msg.get("email.enrollment.cancel.info")
        );

        sendEmail(recipientEmail, subject, brandedTemplate(content, category));
    }

    /**
     * Formatuje termin wydarzenia jako jeden ciągły blok „od początku do końca".
     * <p>
     * Wielodniowe: godzina jest przyklejona do swojej daty (początek pierwszego dnia → koniec
     * ostatniego), żeby nie sugerować „codziennie w tych godzinach" — np.
     * „15.07.2026, 09:00 – 18.07.2026, 16:00". Jednodniowe: „30.05.2026, 10:00–11:30".
     * <p>
     * Wywoływane w obrębie transakcji (przed granicą @Async), żeby uniknąć lazy-loadingu odłączonej encji.
     */
    public static String formatSchedule(Event event) {
        LocalDate start = event.getStartDate();
        LocalDate end = event.getEndDate();
        LocalTime startTime = event.getStartTime();
        LocalTime endTime = event.getEndTime();

        boolean multiDay = end != null && !end.equals(start);
        if (multiDay) {
            String from = start.format(DATE_FMT) + (startTime != null ? ", " + startTime.format(TIME_FMT) : "");
            String to = end.format(DATE_FMT) + (endTime != null ? ", " + endTime.format(TIME_FMT) : "");
            return from + " – " + to;
        }

        String date = start.format(DATE_FMT);
        if (startTime != null && endTime != null) {
            return date + ", " + startTime.format(TIME_FMT) + "–" + endTime.format(TIME_FMT);
        }
        if (startTime != null) {
            return date + ", " + startTime.format(TIME_FMT);
        }
        return date;
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

    private String noteBlock(@Nullable String note) {
        if (note == null || note.isBlank()) {
            return "";
        }
        return """
            <div style="background-color: #3d3a37; border-left: 4px solid #f97316; border-radius: 8px; padding: 14px 18px; margin: 16px 0;">
                <p style="font-size: 14px; font-weight: bold; margin: 0 0 6px;">%s</p>
                <p style="font-size: 15px; line-height: 1.6; margin: 0; color: #e0e0e0;">%s</p>
            </div>
            """.formatted(
                msg.get("email.enrollment.notification.note"),
                HtmlUtils.htmlEscape(note).replace("\n", "<br/>"));
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

    private String brandedTemplate(String content, EventCategory category) {
        String siteUrl = appConfig.getSiteUrl();
        // Logo FIRE CAMP tylko dla obozów; pozostałe sekcje (treningi/szkolenia) → ACADEMY FIRE.
        boolean camp = category == EventCategory.CAMP;
        String logoUrl = siteUrl + (camp ? "/images/logo/logo-white.png" : "/images/logo/logo-academy-fire-white.png");
        String logoAlt = camp ? "Fire Camp" : "Fire Academy";
        return """
            <html>
            <body style="font-family: Arial, sans-serif; background-color: #1a1816; color: #e0e0e0; padding: 20px; margin: 0;">
                <div style="max-width: 600px; margin: 0 auto; background-color: #312e2b; border-radius: 12px; overflow: hidden;">
                    <div style="background-color: #292524; padding: 24px 30px; text-align: center; border-bottom: 2px solid #f97316;">
                        <a href="%s" style="text-decoration: none;">
                            <img src="%s" alt="%s" width="170" style="display: inline-block; width: 170px; max-width: 70%%; height: auto;" />
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
            """.formatted(siteUrl, logoUrl, logoAlt, content, siteUrl, msg.get("email.footer.visit"), msg.get("email.footer"));
    }

    private void sendEmail(String to, String subject, String body) {
        mailDispatcher.sendHtml(to, subject, body);
    }
}
