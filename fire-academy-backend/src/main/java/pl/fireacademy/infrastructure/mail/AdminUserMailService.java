package pl.fireacademy.infrastructure.mail;

import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import pl.fireacademy.config.AppConfig;
import pl.fireacademy.infrastructure.i18n.MessageService;

import java.util.List;

/**
 * Maile pisane ręcznie przez administratora do użytkowników (dowolny temat + treść).
 * Branding wspólny ze stylem eventów: logo Fire Academy, stopka, podpis „Pozdrawiam, Fire Academy".
 */
@Service
public class AdminUserMailService {

    private final MailDispatcher mailDispatcher;
    private final AppConfig appConfig;
    private final MessageService msg;

    public AdminUserMailService(MailDispatcher mailDispatcher, AppConfig appConfig, MessageService msg) {
        this.mailDispatcher = mailDispatcher;
        this.appConfig = appConfig;
        this.msg = msg;
    }

    /**
     * Mail pisany ręcznie przez administratora. Gdy {@code unsubscribeToken != null}, wiadomość jest
     * marketingowa: w stopce dochodzi akapit z linkiem rezygnacji ({siteUrl}/wypisz-sie?token=...),
     * odrębnym od jakiegokolwiek mechanizmu serwisowego. Dla komunikatów serwisowych token jest null.
     */
    @Async("mailExecutor")
    public void sendCustomMessage(String recipientEmail, String firstName, String subject, String message,
                                  @Nullable String unsubscribeToken) {
        // Temat e-maila bez HTML-escape — inaczej polskie znaki trafiałyby jako encje (&oacute; itd.).
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

        sendEmail(recipientEmail, subject, brandedTemplate(content));
    }

    private String unsubscribeBlock(String unsubscribeToken) {
        String unsubscribeUrl = appConfig.getSiteUrl() + "/wypisz-sie?token=" + unsubscribeToken;
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
     * Powiadomienie o usunięciu konta przez organizatora. Jeśli przepadły przyszłe rezerwacje,
     * wymienia je (żeby uczestnik nie stawił się na wydarzenie, z którego został wypisany).
     * {@code cancelledReservations} to gotowe wiersze „nazwa — termin".
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

        sendEmail(recipientEmail, subject, brandedTemplate(content));
    }

    private String brandedTemplate(String content) {
        String siteUrl = appConfig.getSiteUrl();
        String logoUrl = siteUrl + "/images/logo/logo-academy-fire-white.png";
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
        mailDispatcher.sendHtml(to, subject, body);
    }
}
