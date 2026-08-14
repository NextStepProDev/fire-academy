package pl.fireacademy.infrastructure.mail;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import pl.fireacademy.api.admin.EventDtos.FieldChange;
import pl.fireacademy.config.AppConfig;
import pl.fireacademy.domain.event.EventCategory;
import pl.fireacademy.infrastructure.i18n.MessageService;

import java.util.List;

/**
 * Shared branding and email sending (anthracite + orange), and the only copy of the outer HTML.
 * <p>
 * Every mail service renders through here: events ({@link EnrollmentMailService}), cyclical trainings
 * ({@link TrainingMailService}), account mail ({@link AuthMailService}) and the organizer's own messages
 * ({@link AdminUserMailService}). It used to be shared by two of the four while the other two carried
 * their own copy of the same skeleton — same colours, same 600px shell, same footer — so restyling meant
 * finding all four and a missed one meant a class of emails that quietly looked different.
 * <p>
 * Sending delegates to {@link MailDispatcher}, so every email gets the same retrying on transient SMTP
 * failures.
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

    /**
     * Emails that belong to no section — account mail (verification, reset) and the organizer's own
     * messages. They always carry the Fire Academy logo, so they say that outright instead of passing a
     * category they do not have just to mean "not a camp".
     */
    public String academyTemplate(String content, boolean signOff) {
        return brandedTemplate(content, null, signOff);
    }

    public String brandedTemplate(String content, @Nullable EventCategory category) {
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
    public String brandedTemplate(String content, @Nullable EventCategory category, boolean signOff) {
        String siteUrl = appConfig.getSiteUrl();
        // FIRE CAMP logo only for camps; the other sections (trainings/courses) → ACADEMY FIRE.
        boolean camp = category == EventCategory.CAMP;
        String logoUrl = siteUrl + (camp ? "/images/logo/logo-white.png" : "/images/logo/logo-academy-fire-white.png");
        String logoAlt = camp ? "Fire Camp" : "Fire Academy";
        // Joined into the content rather than given its own slot: an empty slot would still emit its
        // line of indentation, and a mail without a sign-off should render byte-for-byte as it always did.
        String body = signOff
                ? content + "\n            "
                        + "<p style=\"font-size: 15px; line-height: 1.6; margin: 24px 0 0;\">%s<br/><strong>%s</strong></p>"
                                .formatted(msg.get("email.regards"), logoAlt)
                : content;
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
            """.formatted(siteUrl, logoUrl, logoAlt, body,
                    siteUrl, msg.get("email.footer.visit"), msg.get("email.footer"));
    }

    public void send(String to, String subject, String htmlBody) {
        mailDispatcher.sendHtml(to, subject, htmlBody);
    }

    /**
     * A bulk send: the same message, plus the headers that let the mailbox offer its own unsubscribe button
     * (see {@link MailDispatcher#sendBulkHtml}). Reserved for mail the recipient can actually opt out of.
     */
    public void sendBulk(String to, String subject, String htmlBody, String unsubscribeUrl) {
        mailDispatcher.sendBulkHtml(to, subject, htmlBody, unsubscribeUrl);
    }
}
