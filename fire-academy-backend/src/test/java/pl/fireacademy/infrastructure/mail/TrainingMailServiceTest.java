package pl.fireacademy.infrastructure.mail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.support.ResourceBundleMessageSource;
import pl.fireacademy.api.admin.EventDtos.FieldChange;
import pl.fireacademy.config.AdminEmailConfig;
import pl.fireacademy.config.AppConfig;
import pl.fireacademy.infrastructure.i18n.MessageService;
import pl.fireacademy.infrastructure.mail.TrainingMailService.SlotInfo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests of training emails (A–K) with a REAL {@link MessageService} (loads messages.properties),
 * so they catch MessageFormat placeholder errors and verify the rendered content:
 * logo + the „Pozdrawiam" sign-off (skipped for „Do zobaczenia").
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TrainingMailServiceTest {

    @Mock private MailDispatcher mailDispatcher;
    @Mock private AdminEmailConfig adminEmailConfig;

    private TrainingMailService service;
    private BrandedMailSender mail;

    private static final String EMAIL = "jan@test.com";
    private static final SlotInfo SLOT = new SlotInfo(
            "Trening personalny", "Anna Nowak", 1, LocalTime.of(18, 0), LocalTime.of(19, 0), BigDecimal.valueOf(90));
    private static final YearMonth MONTH = YearMonth.of(2026, 7);

    @BeforeEach
    void setUp() {
        var source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        MessageService msg = new MessageService(source);

        AppConfig appConfig = new AppConfig();
        appConfig.getMail().setFrom("noreply@fireworkout.pl");

        // spy: real template rendering, but we capture the final HTML passed to send(...)
        mail = spy(new BrandedMailSender(mailDispatcher, appConfig, msg));
        service = new TrainingMailService(adminEmailConfig, msg, mail);
    }

    /** Captures the rendered HTML of the single sent email. */
    private String sentHtml() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(mail).send(anyString(), anyString(), captor.capture());
        return captor.getValue();
    }

    @Test
    void shouldSendEnrollmentConfirmationWithoutSignOff() throws Exception {
        service.sendEnrollmentConfirmation(EMAIL, "Jan", SLOT, MONTH, null, MONTH, 4, BigDecimal.valueOf(360));
        String html = sentHtml();
        assertTrue(html.contains("Trening personalny"));
        assertTrue(html.contains("Fire Academy"));               // logo + footer
        assertFalse(html.contains("Pozdrawiam"));                // A ends with „Do zobaczenia"
        assertTrue(html.contains("360"));                        // monthly amount
    }

    @Test
    void shouldSendAdminAddedConfirmationWithSignOff() throws Exception {
        service.sendAdminAddedConfirmation(EMAIL, "Jan", SLOT, MONTH, 3, MONTH, 4, BigDecimal.valueOf(360));
        String html = sentHtml();
        assertTrue(html.contains("organizator"));                // terminology, not „recepcja"
        assertTrue(html.contains("Pozdrawiam"));
    }

    @Test
    void shouldSendAdminEnrollmentNotificationToAdmins() throws Exception {
        when(adminEmailConfig.getAdminEmails()).thenReturn(Set.of("admin@test.com"));
        service.sendAdminEnrollmentNotification(true, "Jan Kowalski", EMAIL, SLOT, "lipiec 2026", 3, 6);
        String html = sentHtml();
        assertTrue(html.contains("Jan Kowalski"));
        assertTrue(html.contains("3/6"));
    }

    @Test
    void shouldNotSendAdminNotificationWhenNoAdmins() {
        when(adminEmailConfig.getAdminEmails()).thenReturn(Set.of());
        service.sendAdminEnrollmentNotification(false, "Jan Kowalski", EMAIL, SLOT, "lipiec 2026", 2, 6);
        verify(mail, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void shouldSendCancellationConfirmationWithoutSignOff() throws Exception {
        service.sendCancellationConfirmation(EMAIL, "Jan", SLOT, MONTH);
        String html = sentHtml();
        assertTrue(html.contains("Trening personalny"));
        assertFalse(html.contains("Pozdrawiam"));                // C ends with „Do zobaczenia"
    }

    @Test
    void shouldSendCancellationConfirmationImmediateVariant() throws Exception {
        service.sendCancellationConfirmation(EMAIL, "Jan", SLOT, null);
        assertTrue(sentHtml().contains("Trening personalny"));
    }

    @Test
    void shouldSendSlotModificationWithChanges() throws Exception {
        var changes = List.of(new FieldChange("Godziny", "18:00", "20:00"));
        service.sendSlotModification(EMAIL, "Jan", SLOT, changes);
        String html = sentHtml();
        assertTrue(html.contains("Godziny"));
        assertTrue(html.contains("20:00"));                      // new value in the changes block
        assertTrue(html.contains("Pozdrawiam"));
    }

    @Test
    void shouldSendSlotDeletionWithSignOff() throws Exception {
        service.sendSlotDeletion(EMAIL, "Jan", SLOT);
        String html = sentHtml();
        assertTrue(html.contains("nie będzie już prowadzony"));
        assertTrue(html.contains("Pozdrawiam"));
    }

    @Test
    void shouldSendSessionCancelledWithoutSignOff() throws Exception {
        service.sendSessionCancelled(EMAIL, "Jan", SLOT, LocalDate.of(2026, 7, 13));
        String html = sentHtml();
        assertTrue(html.contains("13.07.2026"));
        assertFalse(html.contains("Pozdrawiam"));                // F ends with „do zobaczenia na kolejnym treningu"
    }

    @Test
    void shouldSendAdminRemovedWithSignOff() throws Exception {
        service.sendAdminRemoved(EMAIL, "Jan", SLOT);
        String html = sentHtml();
        assertTrue(html.contains("organizatora"));
        assertTrue(html.contains("Pozdrawiam"));
    }

    @Test
    void shouldSendSlotDeactivationWithDateAndSignOff() throws Exception {
        service.sendSlotDeactivation(EMAIL, "Jan", SLOT, LocalDate.of(2026, 9, 1));
        String html = sentHtml();
        assertTrue(html.contains("01.09.2026"));
        assertTrue(html.contains("Pozdrawiam"));
    }

    @Test
    void shouldSendSubscriptionExpiredWithoutSignOff() throws Exception {
        service.sendSubscriptionExpired(EMAIL, "Jan", SLOT);
        String html = sentHtml();
        assertTrue(html.contains("dobiegła końca"));
        assertFalse(html.contains("Pozdrawiam"));                // K ends with „Do zobaczenia na sali"
    }

    @Test
    void shouldRenderCampLogoForCampNotApplicable_trainingsAlwaysAcademy() throws Exception {
        // Trainings are always the ACADEMY section → Fire Academy logo, never Fire Camp.
        service.sendSlotDeletion(EMAIL, "Jan", SLOT);
        String html = sentHtml();
        assertTrue(html.contains("logo-academy-fire-white.png"));
        assertFalse(html.contains("logo-white.png"));
    }
}
