package pl.fireacademy.infrastructure.mail;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import pl.fireacademy.config.AppConfig;
import pl.fireacademy.infrastructure.i18n.MessageService;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminUserMailServiceTest {

    @Mock private JavaMailSender mailSender;
    @Mock private MessageService msg;

    private MimeMessage mimeMessage;
    private AdminUserMailService service;

    @BeforeEach
    void setUp() {
        AppConfig appConfig = new AppConfig();
        appConfig.getMail().setFrom("noreply@fireworkout.pl");

        mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        when(msg.get(anyString())).thenReturn("text");
        when(msg.get(anyString(), any())).thenReturn("text");
        when(msg.get("email.bulk.signature")).thenReturn("Pozdrawiam,");
        when(msg.get("email.footer")).thenReturn("Fire Academy");

        service = new AdminUserMailService(mailSender, appConfig, msg);
    }

    @Test
    void shouldSendCustomMessageWithBrandingAndSignature() throws Exception {
        service.sendCustomMessage("jan@test.com", "Jan", "Ważna informacja", "Treść wiadomości");

        verify(mailSender).send(mimeMessage);
        String body = extractHtml(mimeMessage);
        assertTrue(body.contains("logo-academy-fire-white.png"), "powinno zawierać logo Fire Academy");
        assertTrue(body.contains("Pozdrawiam,"));
        assertTrue(body.contains("Fire Academy"));
    }

    @Test
    void shouldKeepSubjectRawWithoutHtmlEscaping() throws Exception {
        // Temat z polskimi znakami i znakami specjalnymi musi pozostać surowy (bez encji HTML).
        String subject = "Zniżka 50% <wyjątkowo> ąćę";
        service.sendCustomMessage("jan@test.com", "Jan", subject, "Treść");

        assertEquals(subject, mimeMessage.getSubject());
    }

    @Test
    void shouldHtmlEscapeMessageBodyAndConvertNewlines() throws Exception {
        service.sendCustomMessage("jan@test.com", "Jan", "Temat", "Linia 1\n<script>alert(1)</script>");

        String body = extractHtml(mimeMessage);
        assertTrue(body.contains("&lt;script&gt;"), "treść powinna być escapowana");
        assertFalse(body.contains("<script>alert(1)</script>"), "surowy skrypt nie może trafić do treści");
        assertTrue(body.contains("Linia 1<br/>"), "nowe linie zamieniane na <br/>");
    }

    @Test
    void shouldHandleMailExceptionGracefully() {
        doThrow(new MailSendException("SMTP error")).when(mailSender).send(any(MimeMessage.class));

        assertDoesNotThrow(() -> service.sendCustomMessage(
                "jan@test.com", "Jan", "Temat", "Treść"));
    }

    // Helper z multipart=true → treść owinięta w MimeMultipart; wyciągamy rekursywnie cały HTML.
    private static String extractHtml(Object content) throws Exception {
        if (content instanceof MimeMessage message) {
            return extractHtml(message.getContent());
        }
        if (content instanceof MimeMultipart multipart) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                sb.append(extractHtml(multipart.getBodyPart(i).getContent()));
            }
            return sb.toString();
        }
        return String.valueOf(content);
    }
}
