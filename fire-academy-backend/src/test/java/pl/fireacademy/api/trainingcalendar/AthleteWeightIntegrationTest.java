package pl.fireacademy.api.trainingcalendar;

import org.junit.jupiter.api.Test;
import pl.fireacademy.BaseIntegrationTest;
import pl.fireacademy.domain.user.User;
import pl.fireacademy.domain.user.UserRole;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AthleteWeightIntegrationTest extends BaseIntegrationTest {

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

    private void weighIn(String client, LocalDate date, String kg) throws Exception {
        mockMvc.perform(put("/api/user/my-training/weights")
                        .header("Authorization", "Bearer " + client)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"date":"%s","weightKg":%s}""".formatted(date, kg)))
                .andExpect(status().isOk());
    }

    /** Two steady weeks a week apart, so the weekly comparison has both windows. */
    private void twoWeeks(String client, String firstWeekKg, String secondWeekKg) throws Exception {
        for (int i = 13; i >= 7; i--) {
            weighIn(client, LocalDate.now().minusDays(i), firstWeekKg);
        }
        for (int i = 6; i >= 0; i--) {
            weighIn(client, LocalDate.now().minusDays(i), secondWeekKg);
        }
    }

    @Test
    void shouldRecordTodaysWeightWithoutBeingToldTheDate() throws Exception {
        // The normal case is weighing yourself this morning — the date should be optional.
        String client = flagClient();

        mockMvc.perform(put("/api/user/my-training/weights")
                        .header("Authorization", "Bearer " + client)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"weightKg":74.2}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.weightKg").value(74.2));
    }

    @Test
    void shouldTreatASecondWeighInAsACorrection() throws Exception {
        // Stepping on the scale twice is a correction, not a second data point.
        String client = flagClient();
        weighIn(client, LocalDate.now(), "74.2");
        weighIn(client, LocalDate.now(), "73.8");

        mockMvc.perform(get("/api/user/my-training/weights").header("Authorization", "Bearer " + client))
                .andExpect(jsonPath("$.points.length()").value(1))
                .andExpect(jsonPath("$.points[0].weightKg").value(73.8));
    }

    @Test
    void shouldRejectASlippedDecimalPoint() throws Exception {
        // 7.42 instead of 74.2 is the realistic typo, and it would poison the trend for a fortnight.
        String client = flagClient();

        mockMvc.perform(put("/api/user/my-training/weights")
                        .header("Authorization", "Bearer " + client)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"weightKg":7.42}"""))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/user/my-training/weights")
                        .header("Authorization", "Bearer " + client)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"weightKg":742}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectAWeighInFromTheFuture() throws Exception {
        String client = flagClient();

        mockMvc.perform(put("/api/user/my-training/weights")
                        .header("Authorization", "Bearer " + client)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"date":"%s","weightKg":74.2}""".formatted(LocalDate.now().plusDays(1))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnTheTrendAlongsideEachReading() throws Exception {
        // The window is defined server-side once, so the chart never reimplements it.
        String client = flagClient();
        twoWeeks(client, "80.0", "80.0");

        mockMvc.perform(get("/api/user/my-training/weights").header("Authorization", "Bearer " + client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.length()").value(14))
                .andExpect(jsonPath("$.points[13].trendKg").value(80.0))
                .andExpect(jsonPath("$.currentTrendKg").value(80.0))
                .andExpect(jsonPath("$.weeklyChangePercent").value(0.0));
    }

    @Test
    void shouldSayHowManyMorningsTheTrendRestsOn() throws Exception {
        // Otherwise a trend off two readings and a trend off seven look identical on the page.
        // The goal threshold ships with it so the copy explaining the rule cannot drift from it.
        String client = flagClient();
        weighIn(client, LocalDate.now().minusDays(10), "80.0");
        weighIn(client, LocalDate.now().minusDays(2), "80.0");
        weighIn(client, LocalDate.now(), "80.0");

        mockMvc.perform(get("/api/user/my-training/weights").header("Authorization", "Bearer " + client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.length()").value(3))
                // The reading from ten days ago is outside the window and does not count
                .andExpect(jsonPath("$.trendReadings").value(2))
                .andExpect(jsonPath("$.minReadingsToCloseGoal").value(3));
    }

    @Test
    void shouldKeepTheRapidLossWarningOutOfTheClientsResponse() throws Exception {
        // Coach-only, and absent rather than false — the same reasoning as the overtraining signal.
        String admin = adminToken();
        String client = flagClient();
        twoWeeks(client, "80.0", "78.0");

        mockMvc.perform(get("/api/admin/personal-trainings/weights?athleteId=" + clientId())
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rapidLoss").value(true))
                .andExpect(jsonPath("$.weeklyChangePercent").value(-2.5));

        mockMvc.perform(get("/api/user/my-training/weights").header("Authorization", "Bearer " + client))
                .andExpect(status().isOk())
                // The client still sees their own trend and can read it themselves
                .andExpect(jsonPath("$.weeklyChangePercent").value(-2.5))
                .andExpect(jsonPath("$.rapidLoss").doesNotExist());
    }

    @Test
    void shouldNotWarnAboutASensibleCut() throws Exception {
        String admin = adminToken();
        String client = flagClient();
        twoWeeks(client, "80.0", "79.5");

        mockMvc.perform(get("/api/admin/personal-trainings/weights?athleteId=" + clientId())
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.rapidLoss").value(false));
    }

    @Test
    void shouldNotLetTheCoachWeighAnybody() throws Exception {
        // No write endpoint on the admin side: a coach-entered weight would quietly become a second
        // source of truth next to the client's own scale.
        String admin = adminToken();
        String client = flagClient();

        // Rejected (the request falls through to PUT /{id} and fails to parse an id, so 400 rather
        // than 405 — the status is an artifact of routing; what matters is that nothing is written)
        mockMvc.perform(put("/api/admin/personal-trainings/weights?athleteId=" + clientId())
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"weightKg":74.2}"""))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(get("/api/user/my-training/weights").header("Authorization", "Bearer " + client))
                .andExpect(jsonPath("$.points.length()").value(0));
    }

    @Test
    void shouldLetTheClientRemoveAMistakenEntry() throws Exception {
        String client = flagClient();
        weighIn(client, LocalDate.now(), "74.2");

        mockMvc.perform(delete("/api/user/my-training/weights/" + LocalDate.now())
                        .header("Authorization", "Bearer " + client))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/user/my-training/weights").header("Authorization", "Bearer " + client))
                .andExpect(jsonPath("$.points.length()").value(0))
                .andExpect(jsonPath("$.currentTrendKg").doesNotExist());
    }

    @Test
    void shouldKeepWeightsOutOfReachForOtherPeople() throws Exception {
        flagClient();
        String stranger = createUserAndGetToken("other@fireacademy.test", "Ola", "Obca", UserRole.USER);

        mockMvc.perform(get("/api/admin/personal-trainings/weights?athleteId=" + clientId())
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isForbidden());
        // An account without the flag has no weight log of its own either
        mockMvc.perform(get("/api/user/my-training/weights").header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
    }
}
