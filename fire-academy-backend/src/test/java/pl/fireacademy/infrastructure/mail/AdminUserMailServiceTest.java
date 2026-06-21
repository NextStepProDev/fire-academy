package pl.fireacademy.infrastructure.mail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.fireacademy.config.AppConfig;
import pl.fireacademy.infrastructure.i18n.MessageService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminUserMailServiceTest {

    @Mock private MailDispatcher mailDispatcher;
    @Mock private MessageService msg;
    @Captor private ArgumentCaptor<String> subjectCaptor;
    @Captor private ArgumentCaptor<String> bodyCaptor;

    private AdminUserMailService service;

    @BeforeEach
    void setUp() {
        AppConfig appConfig = new AppConfig();

        when(msg.get(anyString())).thenReturn("text");
        when(msg.get(anyString(), any())).thenReturn("text");
        when(msg.get("email.bulk.signature")).thenReturn("Pozdrawiam,");
        when(msg.get("email.footer")).thenReturn("Fire Academy");

        service = new AdminUserMailService(mailDispatcher, appConfig, msg);
    }

    @Test
    void shouldSendCustomMessageWithBrandingAndSignature() {
        service.sendCustomMessage("jan@test.com", "Jan", "Ważna informacja", "Treść wiadomości", null);

        verify(mailDispatcher).sendHtml(eq("jan@test.com"), anyString(), bodyCaptor.capture());
        String body = bodyCaptor.getValue();
        assertTrue(body.contains("logo-academy-fire-white.png"), "powinno zawierać logo Fire Academy");
        assertTrue(body.contains("Pozdrawiam,"));
        assertTrue(body.contains("Fire Academy"));
        assertFalse(body.contains("/wypisz-sie"), "mail serwisowy (token null) nie ma linku rezygnacji");
    }

    @Test
    void shouldAppendUnsubscribeLinkForMarketingMessage() {
        service.sendCustomMessage("jan@test.com", "Jan", "Nowy obóz", "Zapraszamy", "tok-123");

        verify(mailDispatcher).sendHtml(eq("jan@test.com"), anyString(), bodyCaptor.capture());
        assertTrue(bodyCaptor.getValue().contains("/wypisz-sie?token=tok-123"),
                "mail marketingowy zawiera link rezygnacji z tokenem usera");
    }

    @Test
    void shouldKeepSubjectRawWithoutHtmlEscaping() {
        // Temat z polskimi znakami i znakami specjalnymi musi pozostać surowy (bez encji HTML).
        String subject = "Zniżka 50% <wyjątkowo> ąćę";
        service.sendCustomMessage("jan@test.com", "Jan", subject, "Treść", null);

        verify(mailDispatcher).sendHtml(eq("jan@test.com"), subjectCaptor.capture(), anyString());
        assertEquals(subject, subjectCaptor.getValue());
    }

    @Test
    void shouldHtmlEscapeMessageBodyAndConvertNewlines() {
        service.sendCustomMessage("jan@test.com", "Jan", "Temat", "Linia 1\n<script>alert(1)</script>", null);

        verify(mailDispatcher).sendHtml(eq("jan@test.com"), anyString(), bodyCaptor.capture());
        String body = bodyCaptor.getValue();
        assertTrue(body.contains("&lt;script&gt;"), "treść powinna być escapowana");
        assertFalse(body.contains("<script>alert(1)</script>"), "surowy skrypt nie może trafić do treści");
        assertTrue(body.contains("Linia 1<br/>"), "nowe linie zamieniane na <br/>");
    }
}
