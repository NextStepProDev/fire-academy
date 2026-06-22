package pl.fireacademy.infrastructure.mail;

import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import pl.fireacademy.api.admin.EventDtos.FieldChange;
import pl.fireacademy.config.AdminEmailConfig;
import pl.fireacademy.domain.event.EventCategory;
import pl.fireacademy.infrastructure.i18n.MessageService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Emails for cyclical trainings (TRAINING category): enrollment, cancellation, slot changes,
 * slot deletion, cancellation of individual sessions, and organizer actions. Branding shared with
 * {@link EnrollmentMailService} via {@link BrandedMailSender}.
 */
@Service
public class TrainingMailService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final String[] DAYS_PL = {
            "", "Poniedziałek", "Wtorek", "Środa", "Czwartek", "Piątek", "Sobota", "Niedziela"
    };
    private static final String[] MONTHS_PL = {
            "", "styczeń", "luty", "marzec", "kwiecień", "maj", "czerwiec",
            "lipiec", "sierpień", "wrzesień", "październik", "listopad", "grudzień"
    };

    private final AdminEmailConfig adminEmailConfig;
    private final MessageService msg;
    private final BrandedMailSender mail;

    public TrainingMailService(AdminEmailConfig adminEmailConfig, MessageService msg, BrandedMailSender mail) {
        this.adminEmailConfig = adminEmailConfig;
        this.msg = msg;
        this.mail = mail;
    }

    /** Immutable snapshot of slot data, built within the transaction (before the @Async boundary). */
    public record SlotInfo(String trainingName, @Nullable String instructorName,
                           int dayOfWeek, LocalTime startTime, @Nullable LocalTime endTime,
                           @Nullable BigDecimal price) {

        public String schedule() {
            String day = DAYS_PL[dayOfWeek];
            String time = startTime.format(TIME_FMT)
                    + (endTime != null ? "–" + endTime.format(TIME_FMT) : "");
            return day + ", " + time;
        }
    }

    public static String monthLabel(YearMonth month) {
        return MONTHS_PL[month.getMonthValue()] + " " + month.getYear();
    }

    // ── A: enrollment confirmation (user) ──────────────────────────────────────
    @Async("mailExecutor")
    public void sendEnrollmentConfirmation(String email, String firstName, SlotInfo slot,
                                           YearMonth startMonth, @Nullable Integer months,
                                           YearMonth billingMonth, int sessions,
                                           @Nullable BigDecimal monthlyAmount) {
        String content = """
                <h1 style="color: #f97316; font-size: 20px;">%s</h1>
                <p style="font-size: 16px; line-height: 1.6;">%s</p>
                %s
                <p style="font-size: 16px; line-height: 1.6;">%s</p>
                %s
            """.formatted(
                msg.get("email.training.enroll.greeting", esc(firstName)),
                msg.get("email.training.enroll.body"),
                detailsBlock(slot, startMonth, months, billingMonth, sessions, monthlyAmount),
                msg.get("email.training.enroll.payment"),
                trainingsButton()
        );
        mail.send(email, msg.get("email.training.enroll.subject", slot.trainingName()), branded(content, false));
    }

    // ── G: organizer added the user (user) ─────────────────────────────────
    @Async("mailExecutor")
    public void sendAdminAddedConfirmation(String email, String firstName, SlotInfo slot,
                                           YearMonth startMonth, @Nullable Integer months,
                                           YearMonth billingMonth, int sessions,
                                           @Nullable BigDecimal monthlyAmount) {
        String content = """
                <h1 style="color: #f97316; font-size: 20px;">%s</h1>
                <p style="font-size: 16px; line-height: 1.6;">%s</p>
                %s
                <p style="font-size: 16px; line-height: 1.6;">%s</p>
                %s
            """.formatted(
                msg.get("email.training.adminadd.greeting", esc(firstName)),
                msg.get("email.training.adminadd.body", esc(slot.trainingName())),
                detailsBlock(slot, startMonth, months, billingMonth, sessions, monthlyAmount),
                msg.get("email.training.enroll.payment"),
                trainingsButton()
        );
        mail.send(email, msg.get("email.training.adminadd.subject", slot.trainingName()), branded(content, true));
    }

    // ── B: organizer notification about enrollment/cancellation (admin) ──────────
    @Async("mailExecutor")
    public void sendAdminEnrollmentNotification(boolean enrolled, String fullName, String email,
                                                SlotInfo slot, String periodLabel, long taken, int max) {
        String headingKey = enrolled ? "email.training.admin.enrolled.heading" : "email.training.admin.cancelled.heading";
        String bodyKey = enrolled ? "email.training.admin.enrolled.body" : "email.training.admin.cancelled.body";
        String content = """
                <h1 style="color: #f97316; font-size: 20px;">%s</h1>
                <p style="font-size: 16px; line-height: 1.6;">%s</p>
                <div style="background-color: #292524; border-radius: 8px; padding: 16px; margin: 16px 0;">
                    <p style="font-size: 14px; margin: 4px 0;"><strong>%s</strong> %s (%s)</p>
                    <p style="font-size: 14px; margin: 4px 0;"><strong>%s</strong> %s</p>
                    <p style="font-size: 14px; margin: 4px 0;"><strong>%s</strong> %s</p>
                    <p style="font-size: 14px; margin: 4px 0;"><strong>%s</strong> %d/%d</p>
                </div>
            """.formatted(
                msg.get(headingKey),
                msg.get(bodyKey, esc(slot.trainingName()), slot.schedule()),
                msg.get("email.training.admin.participant"), esc(fullName), esc(email),
                msg.get("email.training.admin.schedule"), slot.schedule(),
                msg.get("email.training.admin.period"), periodLabel,
                msg.get("email.training.admin.occupancy"), taken, max
        );
        String subjectKey = enrolled ? "email.training.admin.enrolled.subject" : "email.training.admin.cancelled.subject";
        String subject = msg.get(subjectKey, slot.trainingName());
        for (String adminEmail : adminEmailConfig.getAdminEmails()) {
            mail.send(adminEmail, subject, branded(content, true));
        }
    }

    // ── C: cancellation confirmation (user) ──────────────────────────────────
    @Async("mailExecutor")
    public void sendCancellationConfirmation(String email, String firstName, SlotInfo slot,
                                             @Nullable YearMonth activeUntil) {
        String untilLine = activeUntil != null
                ? msg.get("email.training.cancel.until", monthLabel(activeUntil))
                : msg.get("email.training.cancel.immediate");
        String content = """
                <h1 style="color: #f97316; font-size: 20px;">%s</h1>
                <p style="font-size: 16px; line-height: 1.6;">%s</p>
                <p style="font-size: 16px; line-height: 1.6;">%s</p>
                <p style="font-size: 16px; line-height: 1.6;">%s</p>
            """.formatted(
                msg.get("email.training.cancel.greeting", esc(firstName)),
                msg.get("email.training.cancel.body", esc(slot.trainingName()), slot.schedule()),
                untilLine,
                msg.get("email.training.cancel.comeback")
        );
        mail.send(email, msg.get("email.training.cancel.subject", slot.trainingName()), branded(content, false));
    }

    // ── D: slot change (user) ───────────────────────────────────────────
    @Async("mailExecutor")
    public void sendSlotModification(String email, String firstName, SlotInfo slot, List<FieldChange> changes) {
        String content = """
                <h1 style="color: #f97316; font-size: 20px;">%s</h1>
                <p style="font-size: 16px; line-height: 1.6;">%s</p>
                <div style="background-color: #3d3a37; border-radius: 8px; padding: 16px; margin: 16px 0;">
                    <p style="font-size: 14px; font-weight: bold; margin-bottom: 8px;">%s</p>
                    %s
                </div>
                <p style="font-size: 16px; line-height: 1.6;">%s</p>
                %s
            """.formatted(
                msg.get("email.training.modified.greeting", esc(firstName)),
                msg.get("email.training.modified.body", esc(slot.trainingName())),
                msg.get("email.training.modified.changes"),
                mail.renderChanges(changes),
                msg.get("email.training.modified.footer"),
                trainingsButton()
        );
        mail.send(email, msg.get("email.training.modified.subject", slot.trainingName()), branded(content, true));
    }

    // ── E: slot deletion (user) ───────────────────────────────────────────
    @Async("mailExecutor")
    public void sendSlotDeletion(String email, String firstName, SlotInfo slot) {
        String content = """
                <h1 style="color: #f97316; font-size: 20px;">%s</h1>
                <p style="font-size: 16px; line-height: 1.6;">%s</p>
                <p style="font-size: 16px; line-height: 1.6;">%s</p>
                %s
            """.formatted(
                msg.get("email.training.deleted.greeting", esc(firstName)),
                msg.get("email.training.deleted.body", esc(slot.trainingName()), slot.schedule()),
                msg.get("email.training.deleted.footer"),
                trainingsButton()
        );
        mail.send(email, msg.get("email.training.deleted.subject", slot.trainingName()), branded(content, true));
    }

    // ── F: cancelled individual session (user) ───────────────────────────────
    @Async("mailExecutor")
    public void sendSessionCancelled(String email, String firstName, SlotInfo slot, LocalDate date) {
        String timeLabel = slot.startTime().format(TIME_FMT)
                + (slot.endTime() != null ? "–" + slot.endTime().format(TIME_FMT) : "");
        String content = """
                <h1 style="color: #f97316; font-size: 20px;">%s</h1>
                <p style="font-size: 16px; line-height: 1.6;">%s</p>
                <p style="font-size: 16px; line-height: 1.6;">%s</p>
                %s
            """.formatted(
                msg.get("email.training.session.greeting", esc(firstName)),
                msg.get("email.training.session.body", esc(slot.trainingName()),
                        date.format(DATE_FMT), timeLabel),
                msg.get("email.training.session.footer"),
                trainingsButton()
        );
        mail.send(email, msg.get("email.training.session.subject", date.format(DATE_FMT), slot.trainingName()), branded(content, false));
    }

    // ── H: organizer removed the user (user) ─────────────────────────────────
    @Async("mailExecutor")
    public void sendAdminRemoved(String email, String firstName, SlotInfo slot) {
        String content = """
                <h1 style="color: #f97316; font-size: 20px;">%s</h1>
                <p style="font-size: 16px; line-height: 1.6;">%s</p>
                <p style="font-size: 16px; line-height: 1.6;">%s</p>
            """.formatted(
                msg.get("email.training.adminremove.greeting", esc(firstName)),
                msg.get("email.training.adminremove.body", esc(slot.trainingName()), slot.schedule()),
                msg.get("email.training.adminremove.footer")
        );
        mail.send(email, msg.get("email.training.adminremove.subject", slot.trainingName()), branded(content, true));
    }

    // ── J: slot deactivated from a specific date (user) ─────────────────────
    @Async("mailExecutor")
    public void sendSlotDeactivation(String email, String firstName, SlotInfo slot, LocalDate from) {
        String content = """
                <h1 style="color: #f97316; font-size: 20px;">%s</h1>
                <p style="font-size: 16px; line-height: 1.6;">%s</p>
                <p style="font-size: 16px; line-height: 1.6;">%s</p>
                %s
            """.formatted(
                msg.get("email.training.deactivated.greeting", esc(firstName)),
                msg.get("email.training.deactivated.body", esc(slot.trainingName()), slot.schedule(), from.format(DATE_FMT)),
                msg.get("email.training.deactivated.footer"),
                trainingsButton()
        );
        mail.send(email, msg.get("email.training.deactivated.subject", from.format(DATE_FMT), slot.trainingName()), branded(content, true));
    }

    // ── K: fixed-term subscription has ended (user) ──────────────────────
    @Async("mailExecutor")
    public void sendSubscriptionExpired(String email, String firstName, SlotInfo slot) {
        String content = """
                <h1 style="color: #f97316; font-size: 20px;">%s</h1>
                <p style="font-size: 16px; line-height: 1.6;">%s</p>
                <p style="font-size: 16px; line-height: 1.6;">%s</p>
                %s
            """.formatted(
                msg.get("email.training.expired.greeting", esc(firstName)),
                msg.get("email.training.expired.body", esc(slot.trainingName()), slot.schedule()),
                msg.get("email.training.expired.footer"),
                trainingsButton()
        );
        mail.send(email, msg.get("email.training.expired.subject", slot.trainingName()), branded(content, false));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────
    private String detailsBlock(SlotInfo slot, YearMonth startMonth, @Nullable Integer months,
                                YearMonth billingMonth, int sessions, @Nullable BigDecimal monthlyAmount) {
        String instructorLine = slot.instructorName() != null
                ? "<p style=\"font-size: 14px; margin: 4px 0;\"><strong>%s</strong> %s</p>"
                        .formatted(msg.get("email.training.details.instructor"), esc(slot.instructorName()))
                : "";
        String priceLine = slot.price() != null
                ? "<p style=\"font-size: 14px; margin: 4px 0;\"><strong>%s</strong> %s</p>"
                        .formatted(msg.get("email.training.details.price"),
                                msg.get("email.training.details.price.value", slot.price().toPlainString()))
                : "";
        String durationLabel = months != null
                ? msg.get("email.training.details.duration.fixed", months)
                : msg.get("email.training.details.duration.indefinite");
        String amountLine = monthlyAmount != null
                ? "<p style=\"font-size: 14px; margin: 4px 0;\"><strong>%s</strong> %s</p>".formatted(
                        msg.get("email.training.details.amount", monthLabel(billingMonth)),
                        msg.get("email.training.details.amount.value", monthlyAmount.toPlainString(), sessions))
                : "";
        return """
            <div style="background-color: #292524; border-radius: 8px; padding: 16px; margin: 16px 0;">
                <p style="font-size: 14px; margin: 4px 0;"><strong>%s</strong> %s</p>
                %s
                <p style="font-size: 14px; margin: 4px 0;"><strong>%s</strong> %s</p>
                %s
                <p style="font-size: 14px; margin: 4px 0;"><strong>%s</strong> %s (%s)</p>
                %s
            </div>
            """.formatted(
                msg.get("email.training.details.name"), esc(slot.trainingName()),
                instructorLine,
                msg.get("email.training.details.schedule"), slot.schedule(),
                priceLine,
                msg.get("email.training.details.from"), monthLabel(startMonth), durationLabel,
                amountLine
        );
    }

    private String trainingsButton() {
        return mail.button(mail.siteUrl() + "/treningi", msg.get("email.training.view"));
    }

    /** @param signOff false for emails ending with „Do zobaczenia" (to avoid duplicating the sign-off). */
    private String branded(String content, boolean signOff) {
        return mail.brandedTemplate(content, EventCategory.TRAINING, signOff);
    }

    private static String esc(String value) {
        return HtmlUtils.htmlEscape(value);
    }
}
