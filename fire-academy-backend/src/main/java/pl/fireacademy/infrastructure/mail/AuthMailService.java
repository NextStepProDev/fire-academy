package pl.fireacademy.infrastructure.mail;

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
import pl.fireacademy.domain.user.User;
import pl.fireacademy.infrastructure.i18n.MessageService;

@Service
public class AuthMailService {

    private static final Logger log = LoggerFactory.getLogger(AuthMailService.class);

    private final JavaMailSender mailSender;
    private final AppConfig appConfig;
    private final AdminEmailConfig adminEmailConfig;
    private final MessageService msg;
    private final String siteUrl;

    public AuthMailService(JavaMailSender mailSender, AppConfig appConfig,
                           AdminEmailConfig adminEmailConfig, MessageService msg) {
        this.mailSender = mailSender;
        this.appConfig = appConfig;
        this.adminEmailConfig = adminEmailConfig;
        this.msg = msg;
        this.siteUrl = appConfig.getSiteUrl();
    }

    @Async("mailExecutor")
    public void sendVerificationEmail(User user, String token) {
        String lang = user.getPreferredLanguage();
        String verificationUrl = siteUrl + "/verify-email?token=" + token;
        String subject = msg.getForLang("email.verification.subject", lang);
        String safeFirstName = HtmlUtils.htmlEscape(user.getFirstName());

        String content = """
                        <h1 style="color: #f97316; font-size: 20px;">%s</h1>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <div style="text-align: center; margin: 28px 0;">
                            <a href="%s" style="display: inline-block; background-color: #f97316; color: #ffffff; text-decoration: none; padding: 14px 32px; border-radius: 8px; font-weight: bold; font-size: 16px;">%s</a>
                        </div>
                        <p style="font-size: 14px; color: #9ca3af;">%s</p>
                        <p style="font-size: 14px; color: #9ca3af;">%s</p>
            """.formatted(
                msg.getForLang("email.verification.greeting", lang, safeFirstName),
                msg.getForLang("email.verification.body", lang),
                verificationUrl,
                msg.getForLang("email.verification.button", lang),
                msg.getForLang("email.verification.expiry", lang),
                msg.getForLang("email.verification.ignore", lang)
        );

        sendEmail(user.getEmail(), subject, brandedTemplate(content, lang));
    }

    @Async("mailExecutor")
    public void sendWelcomeEmail(User user) {
        String lang = user.getPreferredLanguage();
        String subject = msg.getForLang("email.welcome.subject", lang);
        String safeFirstName = HtmlUtils.htmlEscape(user.getFirstName());

        String content = """
                        <h1 style="color: #f97316; font-size: 20px;">%s</h1>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
            """.formatted(
                msg.getForLang("email.welcome.greeting", lang, safeFirstName),
                msg.getForLang("email.welcome.body", lang),
                msg.getForLang("email.welcome.see.you", lang)
        );

        sendEmail(user.getEmail(), subject, brandedTemplate(content, lang));
    }

    @Async("mailExecutor")
    public void sendPasswordResetEmail(User user, String token) {
        String lang = user.getPreferredLanguage();
        String resetUrl = siteUrl + "/reset-password?token=" + token;
        String subject = msg.getForLang("email.reset.subject", lang);
        String safeFirstName = HtmlUtils.htmlEscape(user.getFirstName());

        String content = """
                        <h1 style="color: #f97316; font-size: 20px;">%s</h1>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <div style="text-align: center; margin: 28px 0;">
                            <a href="%s" style="display: inline-block; background-color: #f97316; color: #ffffff; text-decoration: none; padding: 14px 32px; border-radius: 8px; font-weight: bold; font-size: 16px;">%s</a>
                        </div>
                        <p style="font-size: 14px; color: #9ca3af;">%s</p>
                        <p style="font-size: 14px; color: #9ca3af;">%s</p>
            """.formatted(
                msg.getForLang("email.reset.greeting", lang, safeFirstName),
                msg.getForLang("email.reset.body", lang),
                msg.getForLang("email.reset.action", lang),
                resetUrl,
                msg.getForLang("email.reset.button", lang),
                msg.getForLang("email.reset.expiry", lang),
                msg.getForLang("email.reset.ignore", lang)
        );

        sendEmail(user.getEmail(), subject, brandedTemplate(content, lang));
    }

    @Async("mailExecutor")
    public void sendPasswordChangedNotification(User user) {
        String lang = user.getPreferredLanguage();
        String subject = msg.getForLang("email.password.changed.subject", lang);
        String safeFirstName = HtmlUtils.htmlEscape(user.getFirstName());

        String content = """
                        <h1 style="color: #f97316; font-size: 20px;">%s</h1>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
            """.formatted(
                msg.getForLang("email.password.changed.greeting", lang, safeFirstName),
                msg.getForLang("email.password.changed.body", lang),
                msg.getForLang("email.password.changed.warning", lang)
        );

        sendEmail(user.getEmail(), subject, brandedTemplate(content, lang));
    }

    /**
     * Powiadomienie organizatorów o nowym aktywnym koncie. Wysyłane po weryfikacji emailem
     * (flow email/hasło) lub bezpośrednio po utworzeniu konta przez Google OAuth — w obu
     * przypadkach konto jest już „realne" (zweryfikowany właściciel skrzynki).
     */
    @Async("mailExecutor")
    public void sendNewUserAdminNotification(User user) {
        if (adminEmailConfig.getAdminEmails().isEmpty()) {
            return;
        }
        String fullName = (user.getFirstName() + " " + user.getLastName()).trim();
        String subject = msg.get("email.admin.new.user.subject", fullName);

        String safeFullName = HtmlUtils.htmlEscape(fullName);
        String safeEmail = HtmlUtils.htmlEscape(user.getEmail());
        String phone = user.getPhone();
        String safePhone = (phone == null || phone.isBlank())
                ? msg.get("email.admin.new.user.phone.none")
                : HtmlUtils.htmlEscape(phone);
        String source = user.getOauthProvider() != null
                ? msg.get("email.admin.new.user.source.google")
                : msg.get("email.admin.new.user.source.email");
        String marketing = user.getMarketingConsentAt() != null
                ? msg.get("email.admin.new.user.marketing.yes")
                : msg.get("email.admin.new.user.marketing.no");

        String adminUrl = siteUrl + "/admin/uzytkownicy";

        String content = """
                        <h1 style="color: #f97316; font-size: 20px;">%s</h1>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <div style="background-color: #3d3a37; border-radius: 8px; padding: 16px; margin: 16px 0;">
                            <p style="font-size: 15px; margin: 4px 0;">%s</p>
                            <p style="font-size: 15px; margin: 4px 0;">%s</p>
                            <p style="font-size: 15px; margin: 4px 0;">%s</p>
                            <p style="font-size: 15px; margin: 4px 0;">%s</p>
                            <p style="font-size: 15px; margin: 4px 0;">%s</p>
                        </div>
                        <div style="text-align: center; margin: 28px 0;">
                            <a href="%s" style="display: inline-block; background-color: #f97316; color: #ffffff; text-decoration: none; padding: 14px 32px; border-radius: 8px; font-weight: bold; font-size: 16px;">%s</a>
                        </div>
            """.formatted(
                msg.get("email.admin.new.user.heading"),
                msg.get("email.admin.new.user.intro"),
                msg.get("email.admin.new.user.name", safeFullName),
                msg.get("email.admin.new.user.email", safeEmail),
                msg.get("email.admin.new.user.phone", safePhone),
                msg.get("email.admin.new.user.source", source),
                msg.get("email.admin.new.user.marketing", marketing),
                adminUrl,
                msg.get("email.admin.new.user.button")
        );

        String body = brandedTemplate(content, "pl");
        for (String adminEmail : adminEmailConfig.getAdminEmails()) {
            sendEmail(adminEmail, subject, body);
        }
    }

    private String brandedTemplate(String content, String lang) {
        // Maile kontowe nie są przypisane do sekcji → ogólne logo ACADEMY FIRE (nie obozowe FIRE CAMP).
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
            """.formatted(siteUrl, logoUrl, content, siteUrl,
                msg.getForLang("email.footer.visit", lang), msg.getForLang("email.footer", lang));
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
            log.info("Auth email sent to: {}", to);
        } catch (MailException | jakarta.mail.MessagingException e) {
            log.error("Failed to send auth email to: {}", to, e);
        }
    }
}
