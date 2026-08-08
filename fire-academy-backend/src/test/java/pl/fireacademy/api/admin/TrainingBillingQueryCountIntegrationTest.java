package pl.fireacademy.api.admin;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import pl.fireacademy.BaseIntegrationTest;
import pl.fireacademy.domain.user.UserRole;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The organizer's two billing screens must not cost a query per subscriber.
 * <p>
 * Both the slot roster and the monthly payment overview ask, for every subscriber, how many sessions
 * the month holds, when the first one falls and whether payment is overdue. Each of those answers is
 * derived from the same closed dates — the club's days off and the slot's cancellations — which
 * depend on the slot and the month, never on the person. Derived per subscriber they cost two queries
 * each, so the page grew linearly with the group; fetched once for the whole page they cost two, full
 * stop. The failure mode is invisible in a seeded test database and unpleasant on a small production
 * box, so it is pinned here rather than left to review.
 */
class TrainingBillingQueryCountIntegrationTest extends BaseIntegrationTest {

    /**
     * NEXT month on purpose: a subscription created today is prorated from today's day of the month,
     * which would make the seeded group's bills depend on the day the suite happens to run.
     */
    private static final YearMonth SUBSCRIPTION_MONTH = YearMonth.now().plusMonths(1);

    // Budgets per extra row, one per screen, each a measured figure plus at most one query of headroom.
    // A single shared ceiling was useless: set loose enough for the dearest screen it stopped failing on
    // the cheapest one, and a reintroduced per-row lookup slipped straight through.

    /** Roster: the subscriber's own surplus balance (two sums). Was 9 with the closed dates per row. */
    private static final int ROSTER_BUDGET = 3;

    /** Monthly payments: the same two sums per subscription. Was 9. */
    private static final int PAYMENTS_BUDGET = 3;

    /**
     * Cancelled sessions: exactly the session's own refund rows behind "restorable". No headroom — at 2
     * this test passed with the per-row subscriber lookup put back, which is the regression it exists to
     * catch. Every seeded session sits on one slot in one month, so the figure is deterministic.
     */
    private static final int OVERVIEW_BUDGET = 1;

    /** Deleted slots: nothing at all — every archived member arrives in one query. Was 1 per slot. */
    private static final int ARCHIVE_BUDGET = 0;

    @Test
    void rosterMustNotCostQueriesPerSubscriber() throws Exception {
        String admin = adminToken();
        UUID slotId = seedSlot(admin, "Kickboxing");
        subscribe(admin, slotId, client(1));

        // Warm-up: the first call also loads the admin, the message source and Hibernate's metadata,
        // none of which belong to either measurement.
        roster(admin, slotId);
        long oneSubscriber = countQueries(() -> roster(admin, slotId));

        subscribe(admin, slotId, client(2));
        subscribe(admin, slotId, client(3));
        long threeSubscribers = countQueries(() -> roster(admin, slotId));

        assertEquals(3, ((List<?>) JsonPath.read(roster(admin, slotId), "$")).size());
        // Each extra subscriber still costs their own credit balance (two sums) and nothing more.
        // Measured: 2 per subscriber batched, 9 when the closed dates were derived per row.
        long perSubscriber = (threeSubscribers - oneSubscriber) / 2;
        assertTrue(perSubscriber <= ROSTER_BUDGET,
                "the roster must not re-derive the slot's closed dates per subscriber — "
                        + perSubscriber + " queries per extra subscriber "
                        + "(1 subscriber=" + oneSubscriber + ", 3 subscribers=" + threeSubscribers + ")");
    }

    @Test
    void monthlyPaymentOverviewMustNotCostQueriesPerSubscription() throws Exception {
        String admin = adminToken();
        UUID mondaySlot = seedSlot(admin, "Kickboxing");
        subscribe(admin, mondaySlot, client(1));

        payments(admin);
        long oneSubscription = countQueries(() -> payments(admin));

        // A second slot and two more people — the overview spans every subscription of the month.
        UUID tuesdaySlot = seedSlot(admin, "Boks", 2);
        subscribe(admin, tuesdaySlot, client(2));
        subscribe(admin, tuesdaySlot, client(3));
        long threeSubscriptions = countQueries(() -> payments(admin));

        assertEquals(3, ((List<?>) JsonPath.read(payments(admin), "$")).size());
        long perSubscription = (threeSubscriptions - oneSubscription) / 2;
        assertTrue(perSubscription <= PAYMENTS_BUDGET,
                "the monthly payment overview must batch the closed dates across the whole page — "
                        + perSubscription + " queries per extra subscription "
                        + "(1 subscription=" + oneSubscription + ", 3 subscriptions=" + threeSubscriptions + ")");
    }

    /**
     * The cancelled-sessions overview is an archive: rows accumulate for as long as the club runs and the
     * endpoint returns all of them. Anything derived per row therefore has to be batched, or the page gets
     * slower every month it exists.
     */
    @Test
    void cancelledSessionsOverviewMustNotCostQueriesPerSession() throws Exception {
        String admin = adminToken();
        UUID slotId = seedSlot(admin, "Kickboxing");
        subscribe(admin, slotId, client(1));
        var mondays = mondaysOfSubscriptionMonth();

        cancelSession(admin, slotId, mondays.get(0));
        overview(admin);
        long oneSession = countQueries(() -> overview(admin));

        cancelSession(admin, slotId, mondays.get(1));
        cancelSession(admin, slotId, mondays.get(2));
        long threeSessions = countQueries(() -> overview(admin));

        assertEquals(3, ((List<?>) JsonPath.read(overview(admin), "$")).size());
        long perSession = (threeSessions - oneSession) / 2;
        assertTrue(perSession <= OVERVIEW_BUDGET,
                "the cancelled-sessions overview must batch what it can across the page — "
                        + perSession + " queries per extra session "
                        + "(1 session=" + oneSession + ", 3 sessions=" + threeSessions + ")");
    }

    /** The deleted-slot archive has the same shape: every slot's former members, on one page. */
    @Test
    void deletedSlotArchiveMustNotCostQueriesPerSlot() throws Exception {
        String admin = adminToken();
        UUID first = seedSlot(admin, "Kickboxing");
        subscribe(admin, first, client(1));
        deleteSlot(admin, first);

        archive(admin);
        long oneSlot = countQueries(() -> archive(admin));

        UUID second = seedSlot(admin, "Boks", 2);
        subscribe(admin, second, client(2));
        deleteSlot(admin, second);
        UUID third = seedSlot(admin, "Zapasy", 3);
        subscribe(admin, third, client(3));
        deleteSlot(admin, third);
        long threeSlots = countQueries(() -> archive(admin));

        assertEquals(3, ((List<?>) JsonPath.read(archive(admin), "$")).size());
        long perSlot = (threeSlots - oneSlot) / 2;
        assertTrue(perSlot <= ARCHIVE_BUDGET,
                "the deleted-slot archive must fetch its members in one go — "
                        + perSlot + " queries per extra slot "
                        + "(1 slot=" + oneSlot + ", 3 slots=" + threeSlots + ")");
    }

    // --- seeding -------------------------------------------------------------------------------

    /** The first three Mondays of the subscription month — three cancellable sessions of one slot. */
    private List<java.time.LocalDate> mondaysOfSubscriptionMonth() {
        var first = SUBSCRIPTION_MONTH.atDay(1)
                .with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.MONDAY));
        return List.of(first, first.plusWeeks(1), first.plusWeeks(2));
    }

    private void cancelSession(String admin, UUID slotId, java.time.LocalDate date) throws Exception {
        mockMvc.perform(post("/api/admin/training-slots/" + slotId + "/cancel-session")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"sessionDate":"%s"}""".formatted(date)))
                .andExpect(status().is2xxSuccessful());
    }

    private void deleteSlot(String admin, UUID slotId) throws Exception {
        mockMvc.perform(delete("/api/admin/training-slots/" + slotId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().is2xxSuccessful());
    }

    private String overview(String admin) throws Exception {
        return mockMvc.perform(get("/api/admin/training-slots/cancelled-sessions/overview")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String archive(String admin) throws Exception {
        return mockMvc.perform(get("/api/admin/training-slots/deleted")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private UUID client(int n) {
        createUserAndGetToken("client" + n + "@fireacademy.test", "Klient", "Numer" + n, UserRole.USER);
        return userRepository.findByEmail("client" + n + "@fireacademy.test").orElseThrow().getId();
    }

    private UUID seedSlot(String admin, String name) throws Exception {
        return seedSlot(admin, name, 1);
    }

    private UUID seedSlot(String admin, String name, int dayOfWeek) throws Exception {
        String typeJson = mockMvc.perform(post("/api/admin/event-types")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"category":"TRAINING","name":"%s"}""".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String typeId = JsonPath.read(typeJson, "$.id");

        String slotJson = mockMvc.perform(post("/api/admin/training-slots")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"eventTypeId":"%s","dayOfWeek":%d,"startTime":"18:00","endTime":"19:00",
                             "price":50,"maxParticipants":6}""".formatted(typeId, dayOfWeek)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(slotJson, "$.id"));
    }

    private void subscribe(String admin, UUID slotId, UUID userId) throws Exception {
        mockMvc.perform(post("/api/admin/training-slots/" + slotId + "/enrollments")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"userId":"%s","startMonth":"%s"}""".formatted(userId, SUBSCRIPTION_MONTH)))
                .andExpect(status().isCreated());
    }

    private String roster(String admin, UUID slotId) throws Exception {
        return mockMvc.perform(get("/api/admin/training-slots/" + slotId
                        + "/enrollments?month=" + SUBSCRIPTION_MONTH)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String payments(String admin) throws Exception {
        return mockMvc.perform(get("/api/admin/training-payments?month=" + SUBSCRIPTION_MONTH)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
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
