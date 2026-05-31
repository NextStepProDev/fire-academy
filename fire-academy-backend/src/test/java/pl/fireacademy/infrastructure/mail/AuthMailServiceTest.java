package pl.fireacademy.infrastructure.mail;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import pl.fireacademy.config.AppConfig;
import pl.fireacademy.domain.user.User;
import pl.fireacademy.infrastructure.i18n.MessageService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthMailServiceTest {

    @Mock private JavaMailSender mailSender;
    @Mock private MessageService msg;
    @Mock private MimeMessage mimeMessage;

    private AuthMailService service;
    private User user;

    @BeforeEach
    void setUp() {
        AppConfig appConfig = new AppConfig();
        appConfig.setBaseUrl("http://localhost:8081");
        appConfig.setSiteUrl("http://localhost:5174");
        appConfig.getMail().setFrom("noreply@fireworkout.pl");

        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        service = new AuthMailService(mailSender, appConfig, msg);

        user = new User("jan@test.com", "Jan", "Kowalski", null);
        user.setPreferredLanguage("pl");
    }

    @Test
    void shouldSendVerificationEmail() {
        when(msg.getForLang(eq("email.verification.subject"), eq("pl"))).thenReturn("Weryfikacja");
        when(msg.getForLang(eq("email.verification.greeting"), eq("pl"), eq("Jan"))).thenReturn("Cześć Jan!");
        when(msg.getForLang(eq("email.verification.body"), eq("pl"))).thenReturn("Zweryfikuj email");
        when(msg.getForLang(eq("email.verification.action"), eq("pl"))).thenReturn("Kliknij");
        when(msg.getForLang(eq("email.verification.button"), eq("pl"))).thenReturn("Zweryfikuj");
        when(msg.getForLang(eq("email.verification.expiry"), eq("pl"))).thenReturn("15 minut");
        when(msg.getForLang(eq("email.verification.ignore"), eq("pl"))).thenReturn("Ignoruj");

        service.sendVerificationEmail(user, "test-token-123");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void shouldSendWelcomeEmail() {
        when(msg.getForLang(eq("email.welcome.subject"), eq("pl"))).thenReturn("Witamy!");
        when(msg.getForLang(eq("email.welcome.greeting"), eq("pl"), eq("Jan"))).thenReturn("Cześć Jan!");
        when(msg.getForLang(eq("email.welcome.body"), eq("pl"))).thenReturn("Witamy w Fire Academy");
        when(msg.getForLang(eq("email.welcome.see.you"), eq("pl"))).thenReturn("Do zobaczenia!");

        service.sendWelcomeEmail(user);

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void shouldSendPasswordResetEmail() {
        when(msg.getForLang(eq("email.reset.subject"), eq("pl"))).thenReturn("Reset hasła");
        when(msg.getForLang(eq("email.reset.greeting"), eq("pl"), eq("Jan"))).thenReturn("Cześć Jan!");
        when(msg.getForLang(eq("email.reset.body"), eq("pl"))).thenReturn("Resetuj hasło");
        when(msg.getForLang(eq("email.reset.action"), eq("pl"))).thenReturn("Kliknij");
        when(msg.getForLang(eq("email.reset.button"), eq("pl"))).thenReturn("Resetuj");
        when(msg.getForLang(eq("email.reset.expiry"), eq("pl"))).thenReturn("1 godzina");
        when(msg.getForLang(eq("email.reset.ignore"), eq("pl"))).thenReturn("Ignoruj");

        service.sendPasswordResetEmail(user, "reset-token-456");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void shouldSendPasswordChangedNotification() {
        when(msg.getForLang(eq("email.password.changed.subject"), eq("pl"))).thenReturn("Hasło zmienione");
        when(msg.getForLang(eq("email.password.changed.greeting"), eq("pl"), eq("Jan"))).thenReturn("Cześć Jan!");
        when(msg.getForLang(eq("email.password.changed.body"), eq("pl"))).thenReturn("Hasło zostało zmienione");
        when(msg.getForLang(eq("email.password.changed.warning"), eq("pl"))).thenReturn("Jeśli to nie Ty...");

        service.sendPasswordChangedNotification(user);

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void shouldHandleMailException() {
        when(msg.getForLang(eq("email.welcome.subject"), eq("pl"))).thenReturn("Witamy!");
        when(msg.getForLang(eq("email.welcome.greeting"), eq("pl"), eq("Jan"))).thenReturn("Cześć!");
        when(msg.getForLang(eq("email.welcome.body"), eq("pl"))).thenReturn("Witamy");
        when(msg.getForLang(eq("email.welcome.see.you"), eq("pl"))).thenReturn("Do zobaczenia!");
        doThrow(new org.springframework.mail.MailSendException("SMTP error"))
            .when(mailSender).send(any(MimeMessage.class));

        assertDoesNotThrow(() -> service.sendWelcomeEmail(user));
    }
}
