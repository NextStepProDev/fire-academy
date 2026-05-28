package pl.fireacademy.infrastructure.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import pl.fireacademy.config.AppConfig;
import pl.fireacademy.domain.user.User;
import pl.fireacademy.infrastructure.i18n.MessageService;

@Service
public class AuthMailService {

    private static final Logger log = LoggerFactory.getLogger(AuthMailService.class);

    private final JavaMailSender mailSender;
    private final AppConfig appConfig;
    private final MessageService msg;
    private final String siteUrl;

    public AuthMailService(JavaMailSender mailSender, AppConfig appConfig, MessageService msg) {
        this.mailSender = mailSender;
        this.appConfig = appConfig;
        this.msg = msg;
        this.siteUrl = appConfig.getSiteUrl();
    }

    @Async("mailExecutor")
    public void sendVerificationEmail(User user, String token) {
        String lang = user.getPreferredLanguage();
        String verificationUrl = appConfig.getBaseUrl() + "/verify-email?token=" + token;
        String subject = msg.getForLang("email.verification.subject", lang);
        String body = buildVerificationEmailBody(lang, user.getFirstName(), verificationUrl);
        sendEmail(user.getEmail(), subject, body);
    }

    @Async("mailExecutor")
    public void sendWelcomeEmail(User user) {
        String lang = user.getPreferredLanguage();
        String subject = msg.getForLang("email.welcome.subject", lang);
        String body = buildWelcomeEmailBody(lang, user.getFirstName());
        sendEmail(user.getEmail(), subject, body);
    }

    @Async("mailExecutor")
    public void sendPasswordResetEmail(User user, String token) {
        String lang = user.getPreferredLanguage();
        String resetUrl = appConfig.getBaseUrl() + "/reset-password?token=" + token;
        String subject = msg.getForLang("email.reset.subject", lang);
        String body = buildPasswordResetEmailBody(lang, user.getFirstName(), resetUrl);
        sendEmail(user.getEmail(), subject, body);
    }

    @Async("mailExecutor")
    public void sendPasswordChangedNotification(User user) {
        String lang = user.getPreferredLanguage();
        String subject = msg.getForLang("email.password.changed.subject", lang);
        String body = buildPasswordChangedEmailBody(lang, user.getFirstName());
        sendEmail(user.getEmail(), subject, body);
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

    private String buildVerificationEmailBody(String lang, String firstName, String verificationUrl) {
        return """
            <html>
            <body style="font-family: Arial, sans-serif; background-color: #1a1816; color: #e0e0e0; padding: 20px;">
                <div style="max-width: 600px; margin: 0 auto; background-color: #312e2b; border-radius: 12px; overflow: hidden;">
                    <div style="padding: 30px;">
                        <h1 style="color: #f97316;">%s</h1>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <div style="text-align: center; margin: 30px 0;">
                            <a href="%s" style="background: linear-gradient(135deg, #f97316, #ea580c); color: white; padding: 14px 32px; text-decoration: none; border-radius: 8px; font-weight: bold; font-size: 16px; display: inline-block;">%s</a>
                        </div>
                        <p style="font-size: 14px; color: #9ca3af;">%s</p>
                        <p style="font-size: 14px; color: #9ca3af;">%s</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
            msg.getForLang("email.verification.greeting", lang, firstName),
            msg.getForLang("email.verification.body", lang),
            msg.getForLang("email.verification.action", lang),
            verificationUrl,
            msg.getForLang("email.verification.button", lang),
            msg.getForLang("email.verification.expiry", lang),
            msg.getForLang("email.verification.ignore", lang)
        );
    }

    private String buildWelcomeEmailBody(String lang, String firstName) {
        return """
            <html>
            <body style="font-family: Arial, sans-serif; background-color: #1a1816; color: #e0e0e0; padding: 20px;">
                <div style="max-width: 600px; margin: 0 auto; background-color: #312e2b; border-radius: 12px; overflow: hidden;">
                    <div style="padding: 30px;">
                        <h1 style="color: #f97316;">%s</h1>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
            msg.getForLang("email.welcome.greeting", lang, firstName),
            msg.getForLang("email.welcome.body", lang),
            msg.getForLang("email.welcome.see.you", lang)
        );
    }

    private String buildPasswordResetEmailBody(String lang, String firstName, String resetUrl) {
        return """
            <html>
            <body style="font-family: Arial, sans-serif; background-color: #1a1816; color: #e0e0e0; padding: 20px;">
                <div style="max-width: 600px; margin: 0 auto; background-color: #312e2b; border-radius: 12px; overflow: hidden;">
                    <div style="padding: 30px;">
                        <h1 style="color: #f97316;">%s</h1>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <div style="text-align: center; margin: 30px 0;">
                            <a href="%s" style="background: linear-gradient(135deg, #f97316, #ea580c); color: white; padding: 14px 32px; text-decoration: none; border-radius: 8px; font-weight: bold; font-size: 16px; display: inline-block;">%s</a>
                        </div>
                        <p style="font-size: 14px; color: #9ca3af;">%s</p>
                        <p style="font-size: 14px; color: #9ca3af;">%s</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
            msg.getForLang("email.reset.greeting", lang, firstName),
            msg.getForLang("email.reset.body", lang),
            msg.getForLang("email.reset.action", lang),
            resetUrl,
            msg.getForLang("email.reset.button", lang),
            msg.getForLang("email.reset.expiry", lang),
            msg.getForLang("email.reset.ignore", lang)
        );
    }

    private String buildPasswordChangedEmailBody(String lang, String firstName) {
        return """
            <html>
            <body style="font-family: Arial, sans-serif; background-color: #1a1816; color: #e0e0e0; padding: 20px;">
                <div style="max-width: 600px; margin: 0 auto; background-color: #312e2b; border-radius: 12px; overflow: hidden;">
                    <div style="padding: 30px;">
                        <h1 style="color: #f97316;">%s</h1>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                        <p style="font-size: 16px; line-height: 1.6;">%s</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
            msg.getForLang("email.password.changed.greeting", lang, firstName),
            msg.getForLang("email.password.changed.body", lang),
            msg.getForLang("email.password.changed.warning", lang)
        );
    }
}
