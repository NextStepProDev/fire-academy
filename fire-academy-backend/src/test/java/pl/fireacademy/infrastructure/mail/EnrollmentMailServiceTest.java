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
import pl.fireacademy.domain.event.Event;
import pl.fireacademy.domain.event.EventCategory;
import pl.fireacademy.infrastructure.i18n.MessageService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EnrollmentMailServiceTest {

    private static final String EVENT_ID = "11111111-1111-1111-1111-111111111111";

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
            "30.05.2026", "Kraków", EventCategory.TRAINING, EVENT_ID);

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void shouldSendEnrollmentConfirmationWithoutLocation() {
        service.sendEnrollmentConfirmation("anna@test.com", "Anna", "Obóz",
            "15.07.2026", null, EventCategory.CAMP, EVENT_ID);

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void shouldSendEnrollmentNotification() {
        when(adminEmailConfig.getAdminEmails()).thenReturn(Set.of("admin@test.com"));

        service.sendEnrollmentNotification("Trening", "Jan Kowalski",
            "jan@test.com", "534823667", "Wegetarianin", "30.05.2026",
            EventCategory.TRAINING, EVENT_ID);

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void shouldSendEnrollmentNotificationWithoutNote() {
        when(adminEmailConfig.getAdminEmails()).thenReturn(Set.of("admin@test.com"));

        service.sendEnrollmentNotification("Trening", "Jan Kowalski",
            "jan@test.com", "534823667", null, "30.05.2026",
            EventCategory.TRAINING, EVENT_ID);

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void shouldSendEventModificationNotification() {
        List<FieldChange> changes = List.of(
            new FieldChange("Data", "01.06.2026", "15.06.2026"),
            new FieldChange("Lokalizacja", "Kraków", "Warszawa")
        );

        service.sendEventModificationNotification("jan@test.com", "Jan",
            "Trening", "30.05.2026", changes, EventCategory.TRAINING, EVENT_ID);

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void shouldSendEventModificationAdminNotification() {
        when(adminEmailConfig.getAdminEmails()).thenReturn(Set.of("admin1@test.com", "admin2@test.com"));

        List<FieldChange> changes = List.of(new FieldChange("Cena", "100 PLN", "150 PLN"));

        service.sendEventModificationAdminNotification("Trening",
            "30.05.2026", changes, EventCategory.TRAINING, EVENT_ID);

        verify(mailSender, times(2)).send(mimeMessage);
    }

    @Test
    void shouldSendEnrollmentDeletionNotification() {
        service.sendEnrollmentDeletionNotification("jan@test.com", "Jan",
            "Trening", "30.05.2026", EventCategory.TRAINING, EVENT_ID);

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void shouldSendEnrollmentDeletionAdminNotification() {
        when(adminEmailConfig.getAdminEmails()).thenReturn(Set.of("admin@test.com"));

        service.sendEnrollmentDeletionAdminNotification("Trening", "Jan Kowalski",
            "jan@test.com", "30.05.2026", EventCategory.TRAINING, EVENT_ID);

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void shouldSendAdminEnrollmentConfirmation() {
        service.sendAdminEnrollmentConfirmation("anna@test.com", "Anna",
            "Obóz", "15.07.2026", "Zakopane", EventCategory.CAMP, EVENT_ID);

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void shouldSendAdminEnrollmentNotification() {
        when(adminEmailConfig.getAdminEmails()).thenReturn(Set.of("admin@test.com"));

        service.sendAdminEnrollmentNotification("Obóz", "Anna Nowak",
            "anna@test.com", "534823667", "Pierwszy raz", "15.07.2026",
            EventCategory.CAMP, EVENT_ID);

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void shouldHandleMailExceptionGracefully() {
        doThrow(new org.springframework.mail.MailSendException("SMTP error"))
            .when(mailSender).send(any(MimeMessage.class));

        assertDoesNotThrow(() -> service.sendEnrollmentConfirmation(
            "test@test.com", "Test", "Event", "30.05.2026", null, EventCategory.TRAINING, EVENT_ID));
    }

    @Test
    void shouldNotSendNotificationWhenNoAdminEmails() {
        when(adminEmailConfig.getAdminEmails()).thenReturn(Set.of());

        service.sendEnrollmentNotification("Trening", "Jan", "jan@test.com",
            "534823667", null, "30.05.2026", EventCategory.TRAINING, EVENT_ID);

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void shouldSendBulkEventMessageWithSenderSignature() {
        service.sendBulkEventMessage("jan@test.com", "Jan", "Trening", "30.05.2026, 10:00–11:30",
            "Kraków", "Do zobaczenia na zajęciach!", "Przemysław Fajer",
            EventCategory.TRAINING, EVENT_ID);

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void shouldSendBulkEventMessageWithoutSenderSignature() {
        service.sendBulkEventMessage("jan@test.com", "Jan", "Trening", "30.05.2026, 10:00–11:30",
            null, "Do zobaczenia na zajęciach!", null,
            EventCategory.TRAINING, EVENT_ID);

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void shouldFormatScheduleAsSingleDateWhenNoEndDateOrTime() {
        Event event = new Event(EventCategory.TRAINING, "Trening", LocalDate.of(2026, 5, 30));

        assertEquals("30.05.2026", EnrollmentMailService.formatSchedule(event));
    }

    @Test
    void shouldFormatScheduleWithTimeRange() {
        Event event = new Event(EventCategory.TRAINING, "Trening", LocalDate.of(2026, 5, 30));
        event.setStartTime(LocalTime.of(10, 0));
        event.setEndTime(LocalTime.of(11, 30));

        assertEquals("30.05.2026, 10:00–11:30", EnrollmentMailService.formatSchedule(event));
    }

    @Test
    void shouldFormatScheduleWithStartTimeOnly() {
        Event event = new Event(EventCategory.TRAINING, "Trening", LocalDate.of(2026, 5, 30));
        event.setStartTime(LocalTime.of(10, 0));

        assertEquals("30.05.2026, 10:00", EnrollmentMailService.formatSchedule(event));
    }

    @Test
    void shouldFormatScheduleAsContinuousBlockForMultiDayEvent() {
        Event event = new Event(EventCategory.CAMP, "Obóz", LocalDate.of(2026, 7, 15));
        event.setEndDate(LocalDate.of(2026, 7, 18));
        event.setStartTime(LocalTime.of(9, 0));
        event.setEndTime(LocalTime.of(16, 0));

        // Godzina przyklejona do swojej daty — start pierwszego dnia → koniec ostatniego.
        assertEquals("15.07.2026, 09:00 – 18.07.2026, 16:00", EnrollmentMailService.formatSchedule(event));
    }

    @Test
    void shouldFormatScheduleAsDateRangeForMultiDayEventWithoutTimes() {
        Event event = new Event(EventCategory.CAMP, "Obóz", LocalDate.of(2026, 7, 15));
        event.setEndDate(LocalDate.of(2026, 7, 18));

        assertEquals("15.07.2026 – 18.07.2026", EnrollmentMailService.formatSchedule(event));
    }

    @Test
    void shouldNotShowEndDateWhenSameAsStartDate() {
        Event event = new Event(EventCategory.TRAINING, "Trening", LocalDate.of(2026, 5, 30));
        event.setEndDate(LocalDate.of(2026, 5, 30));

        assertEquals("30.05.2026", EnrollmentMailService.formatSchedule(event));
    }
}
