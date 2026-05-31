package pl.fireacademy.infrastructure.mail;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mail.javamail.JavaMailSender;
import pl.fireacademy.api.admin.EventDtos.FieldChange;
import pl.fireacademy.config.AdminEmailConfig;
import pl.fireacademy.config.AppConfig;
import pl.fireacademy.infrastructure.i18n.MessageService;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EnrollmentMailServiceTest {

    @Mock private JavaMailSender mailSender;
    @Mock private AdminEmailConfig adminEmailConfig;
    @Mock private MessageService msg;
    @Mock private MimeMessage mimeMessage;

    private EnrollmentMailService service;

    @BeforeEach
    void setUp() {
        AppConfig appConfig = new AppConfig();
        appConfig.getMail().setFrom("noreply@fireworkout.pl");

        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(msg.get(anyString())).thenReturn("text");
        when(msg.get(anyString(), any())).thenReturn("text");

        service = new EnrollmentMailService(mailSender, appConfig, adminEmailConfig, msg);
    }

    @Test
    void shouldSendEnrollmentConfirmation() {
        service.sendEnrollmentConfirmation("jan@test.com", "Jan", "Trening",
            LocalDate.of(2026, 5, 30), "Kraków");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void shouldSendEnrollmentConfirmationWithoutLocation() {
        service.sendEnrollmentConfirmation("anna@test.com", "Anna", "Obóz",
            LocalDate.of(2026, 7, 15), null);

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void shouldSendEnrollmentNotification() {
        when(adminEmailConfig.getAdminEmails()).thenReturn(Set.of("admin@test.com"));

        service.sendEnrollmentNotification("Trening", "Jan Kowalski",
            "jan@test.com", LocalDate.of(2026, 5, 30));

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void shouldSendEventModificationNotification() {
        List<FieldChange> changes = List.of(
            new FieldChange("Data", "01.06.2026", "15.06.2026"),
            new FieldChange("Lokalizacja", "Kraków", "Warszawa")
        );

        service.sendEventModificationNotification("jan@test.com", "Jan",
            "Trening", LocalDate.of(2026, 5, 30), changes);

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void shouldSendEventModificationAdminNotification() {
        when(adminEmailConfig.getAdminEmails()).thenReturn(Set.of("admin1@test.com", "admin2@test.com"));

        List<FieldChange> changes = List.of(new FieldChange("Cena", "100 PLN", "150 PLN"));

        service.sendEventModificationAdminNotification("Trening",
            LocalDate.of(2026, 5, 30), changes);

        verify(mailSender, times(2)).send(mimeMessage);
    }

    @Test
    void shouldSendEnrollmentDeletionNotification() {
        service.sendEnrollmentDeletionNotification("jan@test.com", "Jan",
            "Trening", LocalDate.of(2026, 5, 30));

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void shouldSendEnrollmentDeletionAdminNotification() {
        when(adminEmailConfig.getAdminEmails()).thenReturn(Set.of("admin@test.com"));

        service.sendEnrollmentDeletionAdminNotification("Trening", "Jan Kowalski",
            "jan@test.com", LocalDate.of(2026, 5, 30));

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void shouldSendAdminEnrollmentConfirmation() {
        service.sendAdminEnrollmentConfirmation("anna@test.com", "Anna",
            "Obóz", LocalDate.of(2026, 7, 15), "Zakopane");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void shouldSendAdminEnrollmentNotification() {
        when(adminEmailConfig.getAdminEmails()).thenReturn(Set.of("admin@test.com"));

        service.sendAdminEnrollmentNotification("Obóz", "Anna Nowak",
            "anna@test.com", LocalDate.of(2026, 7, 15));

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void shouldHandleMailExceptionGracefully() {
        doThrow(new org.springframework.mail.MailSendException("SMTP error"))
            .when(mailSender).send(any(MimeMessage.class));

        assertDoesNotThrow(() -> service.sendEnrollmentConfirmation(
            "test@test.com", "Test", "Event", LocalDate.now(), null));
    }

    @Test
    void shouldNotSendNotificationWhenNoAdminEmails() {
        when(adminEmailConfig.getAdminEmails()).thenReturn(Set.of());

        service.sendEnrollmentNotification("Trening", "Jan", "jan@test.com", LocalDate.now());

        verify(mailSender, never()).send(any(MimeMessage.class));
    }
}
