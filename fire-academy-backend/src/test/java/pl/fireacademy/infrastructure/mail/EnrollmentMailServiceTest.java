package pl.fireacademy.infrastructure.mail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
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

    @Mock private MailDispatcher mailDispatcher;
    @Mock private AdminEmailConfig adminEmailConfig;
    @Mock private MessageService msg;

    private EnrollmentMailService service;

    @BeforeEach
    void setUp() {
        AppConfig appConfig = new AppConfig();

        when(msg.get(anyString())).thenReturn("text");
        when(msg.get(anyString(), any())).thenReturn("text");

        service = new EnrollmentMailService(new BrandedMailSender(mailDispatcher, appConfig, msg),
                adminEmailConfig, msg);
    }

    @Test
    void shouldSendEnrollmentConfirmation() {
        service.sendEnrollmentConfirmation("jan@test.com", "Jan", "Trening",
            "30.05.2026", "Kraków", EventCategory.TRAINING, EVENT_ID);

        verify(mailDispatcher).sendHtml(eq("jan@test.com"), anyString(), anyString());
    }

    @Test
    void shouldSendEnrollmentConfirmationWithoutLocation() {
        service.sendEnrollmentConfirmation("anna@test.com", "Anna", "Obóz",
            "15.07.2026", null, EventCategory.CAMP, EVENT_ID);

        verify(mailDispatcher).sendHtml(eq("anna@test.com"), anyString(), anyString());
    }

    @Test
    void shouldSendEnrollmentNotification() {
        when(adminEmailConfig.getAdminEmails()).thenReturn(Set.of("admin@test.com"));

        service.sendEnrollmentNotification("Trening", "Jan Kowalski",
            "jan@test.com", "534823667", "Wegetarianin", "30.05.2026",
            EventCategory.TRAINING, EVENT_ID);

        verify(mailDispatcher).sendHtml(eq("admin@test.com"), anyString(), anyString());
    }

    @Test
    void shouldSendEnrollmentNotificationWithoutNote() {
        when(adminEmailConfig.getAdminEmails()).thenReturn(Set.of("admin@test.com"));

        service.sendEnrollmentNotification("Trening", "Jan Kowalski",
            "jan@test.com", "534823667", null, "30.05.2026",
            EventCategory.TRAINING, EVENT_ID);

        verify(mailDispatcher).sendHtml(eq("admin@test.com"), anyString(), anyString());
    }

    @Test
    void shouldSendEventModificationNotification() {
        List<FieldChange> changes = List.of(
            new FieldChange("Data", "01.06.2026", "15.06.2026"),
            new FieldChange("Lokalizacja", "Kraków", "Warszawa")
        );

        service.sendEventModificationNotification("jan@test.com", "Jan",
            "Trening", "30.05.2026", changes, EventCategory.TRAINING, EVENT_ID);

        verify(mailDispatcher).sendHtml(eq("jan@test.com"), anyString(), anyString());
    }

    @Test
    void shouldSendEventModificationAdminNotification() {
        when(adminEmailConfig.getAdminEmails()).thenReturn(Set.of("admin1@test.com", "admin2@test.com"));

        List<FieldChange> changes = List.of(new FieldChange("Cena", "100 PLN", "150 PLN"));

        service.sendEventModificationAdminNotification("Trening",
            "30.05.2026", changes, EventCategory.TRAINING, EVENT_ID);

        verify(mailDispatcher, times(2)).sendHtml(anyString(), anyString(), anyString());
    }

    @Test
    void shouldSendEnrollmentDeletionNotification() {
        service.sendEnrollmentDeletionNotification("jan@test.com", "Jan",
            "Trening", "30.05.2026", EventCategory.TRAINING, EVENT_ID);

        verify(mailDispatcher).sendHtml(eq("jan@test.com"), anyString(), anyString());
    }

    @Test
    void shouldSendEnrollmentDeletionAdminNotification() {
        when(adminEmailConfig.getAdminEmails()).thenReturn(Set.of("admin@test.com"));

        service.sendEnrollmentDeletionAdminNotification("Trening", "Jan Kowalski",
            "jan@test.com", "30.05.2026", EventCategory.TRAINING, EVENT_ID);

        verify(mailDispatcher).sendHtml(eq("admin@test.com"), anyString(), anyString());
    }

    @Test
    void shouldSendAdminEnrollmentConfirmation() {
        service.sendAdminEnrollmentConfirmation("anna@test.com", "Anna",
            "Obóz", "15.07.2026", "Zakopane", EventCategory.CAMP, EVENT_ID);

        verify(mailDispatcher).sendHtml(eq("anna@test.com"), anyString(), anyString());
    }

    @Test
    void shouldSendAdminEnrollmentNotification() {
        when(adminEmailConfig.getAdminEmails()).thenReturn(Set.of("admin@test.com"));

        service.sendAdminEnrollmentNotification("Obóz", "Anna Nowak",
            "anna@test.com", "534823667", "Pierwszy raz", "15.07.2026",
            EventCategory.CAMP, EVENT_ID);

        verify(mailDispatcher).sendHtml(eq("admin@test.com"), anyString(), anyString());
    }

    @Test
    void shouldNotSendNotificationWhenNoAdminEmails() {
        when(adminEmailConfig.getAdminEmails()).thenReturn(Set.of());

        service.sendEnrollmentNotification("Trening", "Jan", "jan@test.com",
            "534823667", null, "30.05.2026", EventCategory.TRAINING, EVENT_ID);

        verify(mailDispatcher, never()).sendHtml(any(), any(), any());
    }

    @Test
    void shouldSendBulkEventMessageWithSenderSignature() {
        service.sendBulkEventCampaign(
            List.of(new EnrollmentMailService.BulkEventRecipient("jan@test.com", "Jan")),
            "Trening", "30.05.2026, 10:00–11:30",
            "Kraków", "Do zobaczenia na zajęciach!", "Przemysław Fajer",
            EventCategory.TRAINING, EVENT_ID);

        verify(mailDispatcher).sendHtml(eq("jan@test.com"), anyString(), anyString());
    }

    @Test
    void shouldSendBulkEventMessageWithoutSenderSignature() {
        service.sendBulkEventCampaign(
            List.of(new EnrollmentMailService.BulkEventRecipient("jan@test.com", "Jan")),
            "Trening", "30.05.2026, 10:00–11:30",
            null, "Do zobaczenia na zajęciach!", null,
            EventCategory.TRAINING, EVENT_ID);

        verify(mailDispatcher).sendHtml(eq("jan@test.com"), anyString(), anyString());
    }

    /**
     * The campaign is one background task for the whole list, and it must sit on the executor built
     * for that. Asserted on the annotation because behaviour cannot tell the two pools apart: with
     * @Async unproxied in a unit test, a per-recipient dispatch on mailExecutor would send exactly
     * the same messages and pass every other test in this file.
     */
    @Test
    void shouldRunTheBulkCampaignOnTheCampaignExecutor() throws Exception {
        var method = EnrollmentMailService.class.getMethod("sendBulkEventCampaign",
            List.class, String.class, String.class, String.class, String.class, String.class,
            EventCategory.class, String.class);
        var async = method.getAnnotation(org.springframework.scheduling.annotation.Async.class);

        assertNotNull(async, "the bulk campaign must be dispatched to a background executor");
        assertEquals("mailCampaignExecutor", async.value(),
            "A bulk send belongs on the single-thread campaign executor. mailExecutor carries "
                + "verification and password-reset mail; a camp-sized send queues in front of it and, "
                + "past ~104 recipients, its AbortPolicy throws into the caller mid-send.");
    }

    /** One task for the whole list, not one per recipient — the shape the fix exists to keep. */
    @Test
    void shouldSendOneMessagePerRecipientFromASingleCampaignTask() {
        service.sendBulkEventCampaign(
            List.of(new EnrollmentMailService.BulkEventRecipient("jan@test.com", "Jan"),
                    new EnrollmentMailService.BulkEventRecipient("anna@test.com", "Anna"),
                    new EnrollmentMailService.BulkEventRecipient("ola@test.com", "Ola")),
            "Obóz", "30.05.2026, 10:00–11:30",
            "Kraków", "Zmiana terminu.", "Przemysław Fajer",
            EventCategory.CAMP, EVENT_ID);

        verify(mailDispatcher).sendHtml(eq("jan@test.com"), anyString(), anyString());
        verify(mailDispatcher).sendHtml(eq("anna@test.com"), anyString(), anyString());
        verify(mailDispatcher).sendHtml(eq("ola@test.com"), anyString(), anyString());
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

        // Each time is attached to its own date — start of the first day → end of the last.
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
