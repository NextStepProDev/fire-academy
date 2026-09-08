package pl.fireacademy.api.admin;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import pl.fireacademy.BaseIntegrationTest;
import pl.fireacademy.domain.user.UserRole;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cancelling a session must not cost a handful of queries per paid subscriber.
 *
 * <p>Registering the refunds walks the slot's subscribers and, for each one that has paid, asks three
 * separate questions: is there already a refund for this date, what did that month's payment collect,
 * and how much has been refunded against it so far. All three depend on (enrollment, month) — the same
 * month for the whole group — so they batch. Per subscriber they turn a group of six into eighteen
 * round trips, and a club-wide day off multiplies that by every slot on the weekday.
 *
 * <p>Measured on the narrowest path (one slot, one date) rather than through a holiday, so the figure
 * is about the per-subscriber work and nothing else. The holiday and instructor-day paths are the same
 * loop wrapped in another one, so fixing it here fixes it there.
 */
class RefundRegistrationQueryCountIntegrationTest extends BaseIntegrationTest {

    /**
     * The CURRENT month, because a refund only exists for a month already paid and a future month
     * cannot be paid ahead of time — that guard is deliberate and returns 409. Proration is neutralised
     * by billing from the 1st (see subscribe), so every Monday of the month is a billable session
     * whatever day the suite runs on.
     */
    private static final YearMonth MONTH = YearMonth.now();

    /**
     * What one more PAID subscriber may add to a cancellation. The refund row itself is an insert and
     * has to be there; everything else about that person is answerable in the batch fetched for the
     * whole group. Measured at 4 before batching, 1 after — the headroom is deliberately nil, because
     * at 2 this test still passed with one of the three per-row lookups put back.
     */
    private static final int PER_PAID_SUBSCRIBER_BUDGET = 1;

    @Test
    void cancellingASessionMustNotCostQueriesPerPaidSubscriber() throws Exception {
        String admin = adminToken();
        UUID slotId = seedSlot(admin);

        UUID first = client(1);
        subscribe(admin, slotId, first);
        markPaid(admin, slotId, first);

        // Warm-up: the first call also loads the admin, the message source and Hibernate's metadata.
        cancel(admin, slotId, sessionDate(1));
        long onePaidSubscriber = countQueries(() -> cancel(admin, slotId, sessionDate(2)));

        for (int n = 2; n <= 4; n++) {
            UUID extra = client(n);
            subscribe(admin, slotId, extra);
            markPaid(admin, slotId, extra);
        }
        long fourPaidSubscribers = countQueries(() -> cancel(admin, slotId, sessionDate(3)));

        long perSubscriber = (fourPaidSubscribers - onePaidSubscriber) / 3;
        assertTrue(perSubscriber <= PER_PAID_SUBSCRIBER_BUDGET,
                "registering refunds must batch its per-subscriber lookups — "
                        + perSubscriber + " queries per extra paid subscriber "
                        + "(1 paid=" + onePaidSubscriber + ", 4 paid=" + fourPaidSubscribers + ")");
    }

    /**
     * A different session each time, and never restored.
     * <p>
     * Restoring would have kept the ledger identical between measurements, but it is refused for a
     * date in the past — deliberately, since a session that already failed to happen still owes its
     * refund. Cancelling a past session, on the other hand, is explicitly supported for exactly that
     * reason. So each measurement cancels its own Monday and leaves it cancelled: the dates are
     * distinct, so nothing one measurement does is visible to the next.
     */
    private String cancel(String admin, UUID slotId, LocalDate date) throws Exception {
        mockMvc.perform(post("/api/admin/training-slots/" + slotId + "/cancel-session")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"sessionDate":"%s"}""".formatted(date)))
                .andExpect(status().isCreated());
        return "";
    }

    /** The nth Monday of the billed month — the slot's weekday, so every date is a real session. */
    private static LocalDate sessionDate(int nth) {
        return MONTH.atDay(1).with(TemporalAdjusters.dayOfWeekInMonth(nth, java.time.DayOfWeek.MONDAY));
    }

    private UUID client(int n) {
        createUserAndGetToken("refundclient" + n + "@fireacademy.test", "Klient", "Numer" + n, UserRole.USER);
        return userRepository.findByEmail("refundclient" + n + "@fireacademy.test").orElseThrow().getId();
    }

    private UUID seedSlot(String admin) throws Exception {
        String typeJson = mockMvc.perform(post("/api/admin/event-types")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"category":"TRAINING","name":"Kickboxing zwroty"}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String typeId = JsonPath.read(typeJson, "$.id");

        String slotJson = mockMvc.perform(post("/api/admin/training-slots")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"eventTypeId":"%s","dayOfWeek":1,"startTime":"18:00","endTime":"19:00",
                             "price":50,"maxParticipants":10}""".formatted(typeId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(slotJson, "$.id"));
    }

    private void subscribe(String admin, UUID slotId, UUID userId) throws Exception {
        mockMvc.perform(post("/api/admin/training-slots/" + slotId + "/enrollments")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"userId":"%s","startMonth":"%s","billableFrom":"%s"}"""
                            .formatted(userId, MONTH, MONTH.atDay(1))))
                .andExpect(status().isCreated());
    }

    /** A refund only arises for a month already paid, so the group has to be paid up to measure anything. */
    private void markPaid(String admin, UUID slotId, UUID userId) throws Exception {
        String roster = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/admin/training-slots/" + slotId + "/enrollments?month=" + MONTH)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String enrollmentId = JsonPath.read(roster,
                "$[?(@.userId == '" + userId + "')].enrollmentId").toString().replaceAll("[\\[\\]\"]", "");
        mockMvc.perform(put("/api/admin/training-enrollments/" + enrollmentId + "/payment")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"month":"%s","paid":true}""".formatted(MONTH)))
                .andExpect(status().isNoContent());
    }

    private long countQueries(ThrowingSupplier action) throws Exception {
        var stats = webApplicationContext.getBean(jakarta.persistence.EntityManagerFactory.class)
                .unwrap(org.hibernate.SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();
        action.get();
        return stats.getPrepareStatementCount();
    }

    @FunctionalInterface
    private interface ThrowingSupplier {
        String get() throws Exception;
    }
}
