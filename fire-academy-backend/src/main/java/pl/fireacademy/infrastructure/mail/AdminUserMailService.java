package pl.fireacademy.infrastructure.mail;

import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import pl.fireacademy.infrastructure.i18n.MessageService;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Emails written manually by the administrator to users (arbitrary subject + body).
 * Branding shared with the event style: Fire Academy logo, footer, signature "Pozdrawiam, Fire Academy".
 */
@Service
public class AdminUserMailService {

    private final BrandedMailSender mail;
    private final MessageService msg;

    public AdminUserMailService(BrandedMailSender mail, MessageService msg) {
        this.mail = mail;
        this.msg = msg;
    }

    /**
     * A campaign recipient detached from the {@code User} entity — plain values captured inside the caller's
     * transaction, safe to carry across the {@code @Async} boundary. {@code unsubscribeToken} follows the
     * single-send contract: non-null for marketing (adds the unsubscribe footer), null for service messages.
     */
    public record CampaignRecipient(String email, String firstName, @Nullable String unsubscribeToken) {
    }

    /**
     * Bulk send (admin broadcast/newsletter) as ONE background task: a sequential loop building one message at a
     * time, on the dedicated single-thread {@code mailCampaignExecutor}. Never dispatch a bulk send as one
     * {@code @Async} task per recipient on {@code mailExecutor} — its queue (100) overflows past ~104 recipients
     * (TaskRejectedException cuts the campaign short) and a flooded queue starves transactional mail.
     */
    @Async("mailCampaignExecutor")
    public void sendCampaign(List<CampaignRecipient> recipients, String subject, String message) {
        for (CampaignRecipient recipient : recipients) {
            doSendCustomMessage(recipient.email(), recipient.firstName(), subject, message,
                    recipient.unsubscribeToken());
        }
    }

    /**
     * Email written manually by the administrator. When {@code unsubscribeToken != null}, the message is
     * marketing: a paragraph with an unsubscribe link ({siteUrl}/wypisz-sie?token=...) is added to the footer,
     * separate from any service mechanism. For service messages the token is null.
     */
    @Async("mailExecutor")
    public void sendCustomMessage(String recipientEmail, String firstName, String subject, String message,
                                  @Nullable String unsubscribeToken) {
        doSendCustomMessage(recipientEmail, firstName, subject, message, unsubscribeToken);
    }

    private void doSendCustomMessage(String recipientEmail, String firstName, String subject, String message,
                                     @Nullable String unsubscribeToken) {
        // Email subject without HTML escaping — otherwise Polish characters would end up as entities (&oacute; etc.).
        String safeFirstName = HtmlUtils.htmlEscape(firstName);
        String safeMessage = HtmlUtils.htmlEscape(message).replace("\n", "<br/>");

        String content = """
                        <h1 style="color: #f97316; font-size: 20px;">%s</h1>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <div style="background-color: #3d3a37; border-left: 4px solid #f97316; border-radius: 8px; padding: 20px; margin: 24px 0;">
                            <p style="font-size: 16px; line-height: 1.8; margin: 0; color: #e0e0e0;">%s</p>
                        </div>
                        <p style="font-size: 16px; line-height: 1.6; margin: 24px 0 0;">%s<br/><strong>%s</strong></p>
                        %s
            """.formatted(
                msg.get("email.bulk.greeting", safeFirstName),
                msg.get("email.admin.intro"),
                safeMessage,
                msg.get("email.bulk.signature"),
                msg.get("email.footer"),
                unsubscribeToken != null ? unsubscribeBlock(unsubscribeToken) : ""
        );

        String html = mail.academyTemplate(content, false);
        // The token is the whole test of "is this marketing", here as in the footer above. Service mail must
        // never carry the unsubscribe headers: there is no opting out of a verification link, and saying
        // otherwise to a mailbox is a promise we cannot keep.
        if (unsubscribeToken != null) {
            mail.sendBulk(recipientEmail, subject, html, oneClickUnsubscribeUrl(unsubscribeToken));
        } else {
            mail.send(recipientEmail, subject, html);
        }
    }

    /**
     * The machine-facing unsubscribe address, for the button the mailbox draws itself.
     * <p>
     * Deliberately a different address from the footer link below, and the difference is the point: this one
     * goes to the API, because the mailbox POSTs to it directly and runs no JavaScript, so the page the
     * human gets would leave it holding an empty document and nobody unsubscribed. Same operation on the
     * server, two ways in.
     */
    private String oneClickUnsubscribeUrl(String unsubscribeToken) {
        return mail.siteUrl() + "/api/public/marketing/unsubscribe?token="
                + URLEncoder.encode(unsubscribeToken, StandardCharsets.UTF_8);
    }

    /** The human-facing link in the footer: our own page, with a button that explains what it is doing. */
    private String unsubscribeBlock(String unsubscribeToken) {
        String unsubscribeUrl = mail.siteUrl() + "/wypisz-sie?token=" + unsubscribeToken;
        return """
                        <hr style="border-color: #4a4a4a; margin: 24px 0 12px;" />
                        <p style="font-size: 12px; color: #9ca3af; line-height: 1.6; margin: 0;">%s
                            <a href="%s" style="color: #f97316;">%s</a>
                        </p>
            """.formatted(
                msg.get("email.marketing.unsubscribe.reason"),
                unsubscribeUrl,
                msg.get("email.marketing.unsubscribe.link")
        );
    }

    /**
     * Notification about account deletion by the organizer. If future reservations were lost,
     * it lists them (so the participant doesn't show up at an event they were unenrolled from).
     * {@code cancelledReservations} are ready-made "name — date" lines.
     */
    @Async("mailExecutor")
    public void sendAccountDeletedNotification(String recipientEmail, String firstName,
                                               List<String> cancelledReservations) {
        String subject = msg.get("email.account.deleted.subject");
        String safeFirstName = HtmlUtils.htmlEscape(firstName);

        String reservationsHtml = "";
        if (!cancelledReservations.isEmpty()) {
            String listHtml = cancelledReservations.stream()
                    .map(line -> "<p style=\"font-size: 15px; line-height: 1.6; margin: 4px 0;\">• %s</p>"
                            .formatted(HtmlUtils.htmlEscape(line)))
                    .reduce("", String::concat);
            reservationsHtml = """
                            <p style="font-size: 16px; line-height: 1.6;">%s</p>
                            <div style="background-color: #3d3a37; border-left: 4px solid #f97316; border-radius: 8px; padding: 14px 18px; margin: 16px 0;">
                                %s
                            </div>
                """.formatted(msg.get("email.account.deleted.reservations"), listHtml);
        }

        String content = """
                        <h1 style="color: #f97316; font-size: 20px;">%s</h1>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        %s
                        <p style="font-size: 14px; line-height: 1.6; color: #9ca3af; margin-top: 12px;">%s</p>
                        <p style="font-size: 16px; line-height: 1.6; margin: 24px 0 0;">%s<br/><strong>%s</strong></p>
            """.formatted(
                msg.get("email.bulk.greeting", safeFirstName),
                msg.get("email.account.deleted.body"),
                reservationsHtml,
                msg.get("email.account.deleted.contact"),
                msg.get("email.bulk.signature"),
                msg.get("email.footer")
        );

        mail.send(recipientEmail, subject, mail.academyTemplate(content, false));
    }

}
