package pl.fireacademy.infrastructure.mail;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import pl.fireacademy.config.AppConfig;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MailDispatcherTest {

    @Mock private JavaMailSender mailSender;

    private MailDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        AppConfig appConfig = new AppConfig();
        appConfig.getMail().setFrom("noreply@test.com");
        // Zerowy backoff — test bez realnego oczekiwania.
        dispatcher = new MailDispatcher(mailSender, appConfig, 3, new long[]{0, 0});
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));
    }

    @Test
    void shouldSendOnFirstAttempt() {
        dispatcher.sendHtml("to@test.com", "Temat", "<p>Treść</p>");

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void shouldRetryThenSucceed() {
        doThrow(new MailSendException("transient")).doNothing()
                .when(mailSender).send(any(MimeMessage.class));

        dispatcher.sendHtml("to@test.com", "Temat", "<p>Treść</p>");

        verify(mailSender, times(2)).send(any(MimeMessage.class));
    }

    @Test
    void shouldNotThrowAfterExhaustingRetries() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));

        assertDoesNotThrow(() -> dispatcher.sendHtml("to@test.com", "Temat", "<p>Treść</p>"));

        verify(mailSender, times(3)).send(any(MimeMessage.class));
    }
}
