package pl.fireacademy.infrastructure.mail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.fireacademy.config.AdminEmailConfig;
import pl.fireacademy.config.AppConfig;
import pl.fireacademy.domain.event.EventCategory;
import pl.fireacademy.domain.user.User;
import pl.fireacademy.infrastructure.i18n.MessageService;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Every mail service renders through {@link BrandedMailSender} — there is one copy of the outer HTML.
 * <p>
 * Three of the four services used to carry their own copy of the same skeleton. Nothing broke, which is
 * the problem: restyling meant finding all four, and the one that got missed would only surface as a
 * customer noticing that password-reset mail looks different from enrolment mail. This test fails the
 * moment a service starts emitting a shell the shared sender did not produce.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SharedMailTemplateTest {

    /** The 600px shell — the part every branded email must have in common. */
    private static final String SHELL =
            "<div style=\"max-width: 600px; margin: 0 auto; background-color: #312e2b;"
                    + " border-radius: 12px; overflow: hidden;\">";

    private static final String ACADEMY_LOGO = "/images/logo/logo-academy-fire-white.png";
    private static final String CAMP_LOGO = "/images/logo/logo-white.png";

    @Mock private MailDispatcher mailDispatcher;
    @Mock private MessageService msg;
    @Mock private AdminEmailConfig adminEmailConfig;

    private AppConfig appConfig() {
        AppConfig c = new AppConfig();
        c.setBaseUrl("http://localhost:8081");
        c.setSiteUrl("http://localhost:5174");
        return c;
    }

    private BrandedMailSender sender() {
        when(msg.get(anyString())).thenAnswer(i -> i.getArgument(0));
        when(msg.get(anyString(), any(Object[].class))).thenAnswer(i -> i.getArgument(0));
        when(msg.getForLang(anyString(), anyString())).thenAnswer(i -> i.getArgument(0));
        when(msg.getForLang(anyString(), anyString(), any(Object[].class)))
                .thenAnswer(i -> i.getArgument(0));
        return new BrandedMailSender(mailDispatcher, appConfig(), msg);
    }

    private String sent() {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailDispatcher).sendHtml(anyString(), anyString(), body.capture());
        return body.getValue();
    }

    private static void assertBranded(String html, String expectedLogo) {
        assertTrue(html.contains(SHELL), "email does not use the shared 600px shell");
        assertTrue(html.contains(expectedLogo), "email does not carry the expected logo " + expectedLogo);
    }

    @Test
    void accountMailUsesTheSharedShell() {
        var service = new AuthMailService(sender(), appConfig(), adminEmailConfig, msg);
        User user = new User("jan@test.com", "Jan", "Kowalski", "+48123456789");
        user.setPreferredLanguage("pl");

        service.sendVerificationEmail(user, "tok");

        assertBranded(sent(), ACADEMY_LOGO);
    }

    @Test
    void organizerMessageUsesTheSharedShell() {
        var service = new AdminUserMailService(sender(), msg);

        service.sendCustomMessage("jan@test.com", "Jan", "Temat", "Treść", null);

        assertBranded(sent(), ACADEMY_LOGO);
    }

    @Test
    void enrolmentMailUsesTheSharedShell() {
        var service = new EnrollmentMailService(sender(), adminEmailConfig, msg);

        service.sendEnrollmentConfirmation("jan@test.com", "Jan", "Trening", "1 maja", "Sala",
                EventCategory.TRAINING, "id-1");

        assertBranded(sent(), ACADEMY_LOGO);
    }

    /** The one branding decision the category actually drives. */
    @Test
    void campMailSwapsTheLogoAndNothingElse() {
        var service = new EnrollmentMailService(sender(), adminEmailConfig, msg);

        service.sendEnrollmentConfirmation("jan@test.com", "Jan", "Obóz", "1 maja", "Góry",
                EventCategory.CAMP, "id-2");

        String html = sent();
        assertBranded(html, CAMP_LOGO);
        assertFalse(html.contains(ACADEMY_LOGO), "a camp email must not also carry the academy logo");
    }

    /**
     * Emails that sign off inside their own content must not get a second one from the shell — the
     * distinction the {@code signOff} flag exists for.
     */
    @Test
    void mailThatSignsItselfOffGetsNoSecondSignOff() {
        var service = new AdminUserMailService(sender(), msg);

        service.sendCustomMessage("jan@test.com", "Jan", "Temat", "Treść", null);

        String html = sent();
        assertFalse(html.contains("email.regards"),
                "the shell added a sign-off to a message that already carries one");
    }
}
