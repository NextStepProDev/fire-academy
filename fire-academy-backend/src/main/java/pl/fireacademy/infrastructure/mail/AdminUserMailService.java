package pl.fireacademy.infrastructure.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import pl.fireacademy.config.AppConfig;
import pl.fireacademy.infrastructure.i18n.MessageService;

/**
 * Maile pisane ręcznie przez administratora do użytkowników (dowolny temat + treść).
 * Branding wspólny ze stylem eventów: logo Fire Academy, stopka, podpis „Pozdrawiam, Fire Academy".
 */
@Service
public class AdminUserMailService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserMailService.class);

    private final JavaMailSender mailSender;
    private final AppConfig appConfig;
    private final MessageService msg;

    public AdminUserMailService(JavaMailSender mailSender, AppConfig appConfig, MessageService msg) {
        this.mailSender = mailSender;
        this.appConfig = appConfig;
        this.msg = msg;
    }

    @Async("mailExecutor")
    public void sendCustomMessage(String recipientEmail, String firstName, String subject, String message) {
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
            """.formatted(
                msg.get("email.bulk.greeting", safeFirstName),
                msg.get("email.admin.intro"),
                safeMessage,
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
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true);
            helper.setFrom(appConfig.getMail().getFrom());
            mailSender.send(message);
            log.info("Admin user email sent to: {}", to);
        } catch (MailException | jakarta.mail.MessagingException e) {
            log.error("Failed to send admin user email to: {}", to, e);
        }
    }
}
