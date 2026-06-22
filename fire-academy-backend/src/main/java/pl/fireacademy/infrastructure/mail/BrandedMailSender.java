package pl.fireacademy.infrastructure.mail;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import pl.fireacademy.api.admin.EventDtos.FieldChange;
import pl.fireacademy.config.AppConfig;
import pl.fireacademy.domain.event.EventCategory;
import pl.fireacademy.infrastructure.i18n.MessageService;

import java.util.List;

/**
 * Shared branding and email sending (anthracite + orange). A single source of truth for
 * {@link EnrollmentMailService} (events) and {@link TrainingMailService} (cyclical trainings).
 * Sending delegates to {@link MailDispatcher}, so training emails use the same
 * retrying on transient SMTP failures as the other service emails.
 */
@Component
public class BrandedMailSender {

    private final MailDispatcher mailDispatcher;
    private final AppConfig appConfig;
    private final MessageService msg;

    public BrandedMailSender(MailDispatcher mailDispatcher, AppConfig appConfig, MessageService msg) {
        this.mailDispatcher = mailDispatcher;
        this.appConfig = appConfig;
        this.msg = msg;
    }

    public String siteUrl() {
        return appConfig.getSiteUrl();
    }

    /** Orange CTA button leading to the given URL. */
    public String button(String url, String label) {
        return """
            <div style="text-align: center; margin: 28px 0;">
                <a href="%s" style="display: inline-block; background-color: #f97316; color: #ffffff; text-decoration: none; padding: 14px 32px; border-radius: 8px; font-weight: bold; font-size: 16px;">%s</a>
            </div>
            """.formatted(url, label);
    }

    /** List of field changes: old value struck through → new one in orange. */
    public String renderChanges(List<FieldChange> changes) {
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

    public String brandedTemplate(String content, EventCategory category) {
        // By default skips the sign-off if the content already has a warm closing („Do zobaczenia")
        // or its own sign-off („Pozdrawiam"), to avoid duplicating courtesy phrases.
        String lower = content.toLowerCase();
        boolean signOff = !lower.contains("do zobaczenia") && !lower.contains("pozdrawiam");
        return brandedTemplate(content, category, signOff);
    }

    /**
     * @param signOff whether to add the „Pozdrawiam, Fire Academy/Fire Camp" sign-off. Skipped for emails
     *                that already end with a warm closing („Do zobaczenia!") or have their own sign-off.
     */
    public String brandedTemplate(String content, EventCategory category, boolean signOff) {
        String siteUrl = appConfig.getSiteUrl();
        // FIRE CAMP logo only for camps; the other sections (trainings/courses) → ACADEMY FIRE.
        boolean camp = category == EventCategory.CAMP;
        String logoUrl = siteUrl + (camp ? "/images/logo/logo-white.png" : "/images/logo/logo-academy-fire-white.png");
        String logoAlt = camp ? "Fire Camp" : "Fire Academy";
        String signOffHtml = signOff
                ? "<p style=\"font-size: 15px; line-height: 1.6; margin: 24px 0 0;\">%s<br/><strong>%s</strong></p>"
                        .formatted(msg.get("email.regards"), logoAlt)
                : "";
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
            """.formatted(siteUrl, logoUrl, logoAlt, content, signOffHtml,
                    siteUrl, msg.get("email.footer.visit"), msg.get("email.footer"));
    }

    public void send(String to, String subject, String htmlBody) {
        mailDispatcher.sendHtml(to, subject, htmlBody);
    }
}
