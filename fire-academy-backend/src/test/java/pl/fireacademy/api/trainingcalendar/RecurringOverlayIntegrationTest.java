package pl.fireacademy.api.trainingcalendar;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.fireacademy.BaseIntegrationTest;
import pl.fireacademy.domain.user.User;
import pl.fireacademy.domain.user.UserRole;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RecurringOverlayIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * The subscription starts NEXT month on purpose. A subscription created today is prorated from
     * today's day of the month, so sessions earlier in the current month are correctly excluded from
     * both the bill and the calendar — using this month would test the proration rule, not the overlay.
     */
    private static final YearMonth SUBSCRIPTION_MONTH = YearMonth.now().plusMonths(1);

    /** First Monday of the subscription month, with a second Monday still inside the same month. */
    private LocalDate anchorMonday() {
        return SUBSCRIPTION_MONTH.atDay(1)
                .with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }

    private String flagClient() {
        String token = createUserAndGetToken("client@fireacademy.test", "Ala", "Testowa", UserRole.USER);
        User user = userRepository.findByEmail("client@fireacademy.test").orElseThrow();
        user.setAthlete(true);
        userRepository.save(user);
        return token;
    }

    private UUID clientId() {
        return userRepository.findByEmail("client@fireacademy.test").orElseThrow().getId();
    }

    /** Creates a Monday slot and subscribes the client to it for the current month. */
    private UUID seedMondaySlot(String admin) throws Exception {
        String typeJson = mockMvc.perform(post("/api/admin/event-types")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"category":"TRAINING","name":"Kickboxing"}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String typeId = JsonPath.read(typeJson, "$.id");

        String slotJson = mockMvc.perform(post("/api/admin/training-slots")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"eventTypeId":"%s","dayOfWeek":1,"startTime":"18:00","endTime":"19:00",
                             "price":50,"maxParticipants":6}""".formatted(typeId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String slotId = JsonPath.read(slotJson, "$.id");

        mockMvc.perform(post("/api/admin/training-slots/" + slotId + "/enrollments")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"userId":"%s","startMonth":"%s"}"""
                                .formatted(clientId(), SUBSCRIPTION_MONTH)))
                .andExpect(status().isCreated());

        return UUID.fromString(slotId);
    }

    private String fetchWeek(String client, LocalDate monday) throws Exception {
        return mockMvc.perform(get("/api/user/my-training/calendar?from=" + monday + "&to=" + monday.plusDays(6))
                        .header("Authorization", "Bearer " + client))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void shouldShowGroupSessionsOnThePersonalCalendarWithoutStoringThem() throws Exception {
        // THE rule of this feature: the overlay is computed, never materialised. If a future change
        // starts writing rows, the count below stops being zero and this test says so.
        String admin = adminToken();
        String client = flagClient();
        seedMondaySlot(admin);
        LocalDate monday = anchorMonday();

        String json = fetchWeek(client, monday);

        assertEquals(1, ((java.util.List<?>) JsonPath.read(json, "$.recurring")).size());
        assertEquals(monday.toString(), JsonPath.read(json, "$.recurring[0].date"));
        assertEquals("Kickboxing", JsonPath.read(json, "$.recurring[0].name"));

        Integer stored = jdbcTemplate.queryForObject("SELECT count(*) FROM personal_trainings", Integer.class);
        assertEquals(0, stored, "the recurring overlay must never write personal_trainings rows");
    }

    @Test
    void shouldDropASessionTheMomentTheClubDeclaresADayOff() throws Exception {
        // The calendar and the invoice answer the same question, so they cannot disagree: declaring
        // a day off removes the session from the plan in the same instant it reduces what is owed.
        String admin = adminToken();
        String client = flagClient();
        seedMondaySlot(admin);
        LocalDate monday = anchorMonday();
        assertEquals(1, ((java.util.List<?>) JsonPath.read(fetchWeek(client, monday), "$.recurring")).size());

        mockMvc.perform(post("/api/admin/training-holidays")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"date":"%s","label":"Święto"}""".formatted(monday)))
                .andExpect(status().isCreated());

        assertEquals(0, ((java.util.List<?>) JsonPath.read(fetchWeek(client, monday), "$.recurring")).size());
    }

    @Test
    void shouldDropASingleCancelledSession() throws Exception {
        String admin = adminToken();
        String client = flagClient();
        UUID slotId = seedMondaySlot(admin);
        LocalDate monday = anchorMonday();

        mockMvc.perform(post("/api/admin/training-slots/" + slotId + "/cancel-session")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"sessionDate":"%s"}""".formatted(monday)))
                .andExpect(status().is2xxSuccessful());

        String json = fetchWeek(client, monday);
        assertEquals(0, ((java.util.List<?>) JsonPath.read(json, "$.recurring")).size());
        // and the following week is untouched — one cancellation closes one date
        String next = fetchWeek(client, monday.plusWeeks(1));
        assertEquals(1, ((java.util.List<?>) JsonPath.read(next, "$.recurring")).size());
    }

    @Test
    void shouldShowNothingForAClientWithNoSubscriptions() throws Exception {
        String client = flagClient();
        LocalDate monday = anchorMonday();

        assertEquals(0, ((java.util.List<?>) JsonPath.read(fetchWeek(client, monday), "$.recurring")).size());
    }

    @Test
    void shouldKeepPersonalTrainingsAndGroupSessionsApart() throws Exception {
        // They render side by side but are different things: one is editable, the other is a fact
        // about the group schedule, and the API keeps them in separate arrays so the UI cannot
        // accidentally offer an edit button on something with no row behind it.
        String admin = adminToken();
        String client = flagClient();
        seedMondaySlot(admin);
        LocalDate monday = anchorMonday();

        mockMvc.perform(post("/api/admin/personal-trainings?athleteId=" + clientId())
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"date":"%s","title":"Siła"}""".formatted(monday)))
                .andExpect(status().isCreated());

        String json = fetchWeek(client, monday);
        assertEquals(1, ((java.util.List<?>) JsonPath.read(json, "$.trainings")).size());
        assertEquals(1, ((java.util.List<?>) JsonPath.read(json, "$.recurring")).size());
    }

    @Test
    void shouldCostTheSameNumberOfQueriesForAWeekAndForAMonth() throws Exception {
        // The overlay is three queries regardless of span. A regression to per-month or per-slot
        // fetching would show up here as the wider range costing more.
        String admin = adminToken();
        String client = flagClient();
        seedMondaySlot(admin);
        LocalDate monday = anchorMonday();

        // Warm up first: the very first request also loads caches and the user, which would otherwise
        // be counted against whichever range happened to run first.
        fetchWeek(client, monday);
        long weekQueries = countQueries(() -> fetchWeek(client, monday));
        long monthQueries = countQueries(() -> mockMvc.perform(
                get("/api/user/my-training/calendar?from=" + monday + "&to=" + monday.plusDays(41))
                        .header("Authorization", "Bearer " + client))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertTrue(monthQueries <= weekQueries,
                "a six-week page must not cost more queries than a one-week page — the overlay "
                        + "batches the whole span (week=" + weekQueries + ", month=" + monthQueries + ")");
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
