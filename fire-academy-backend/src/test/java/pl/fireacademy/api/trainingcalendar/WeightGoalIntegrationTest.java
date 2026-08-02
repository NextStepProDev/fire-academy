package pl.fireacademy.api.trainingcalendar;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import pl.fireacademy.BaseIntegrationTest;
import pl.fireacademy.domain.user.User;
import pl.fireacademy.domain.user.UserRole;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WeightGoalIntegrationTest extends BaseIntegrationTest {

    private String flagClient() {
        String token = createUserAndGetToken("client@fireacademy.test", "Ala", "Testowa", UserRole.USER);
        User user = userRepository.findByEmail("client@fireacademy.test").orElseThrow();
        user.setAthlete(true);
        // The client side of the calendar sits behind the GDPR art. 9 consent gate (V38)
        user.grantTrainingConsent();
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

    /** A steady week at the given weight, so the 7-day trend equals it exactly. */
    private void steadyWeek(String client, String kg) throws Exception {
        for (int i = 6; i >= 0; i--) {
            weighIn(client, LocalDate.now().minusDays(i), kg);
        }
    }

    private String createWeightGoal(String admin, String targetKg) throws Exception {
        String json = mockMvc.perform(post("/api/admin/personal-trainings/goals?athleteId=" + clientId())
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"horizon":"SHORT","content":"Zejść do %s kg","targetWeightKg":%s}"""
                                .formatted(targetKg, targetKg)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    @Test
    void shouldCloseAWeightGoalOnceTheTrendReachesTheTarget() throws Exception {
        String admin = adminToken();
        String client = flagClient();
        steadyWeek(client, "80.0");
        String goalId = createWeightGoal(admin, "78.0");

        // A whole week at the target, so the trend — not a single reading — actually gets there
        for (int i = 6; i >= 0; i--) {
            weighIn(client, LocalDate.now().minusDays(i), "78.0");
        }

        mockMvc.perform(get("/api/user/my-training/goals").header("Authorization", "Bearer " + client))
                .andExpect(jsonPath("$.active.length()").value(0))
                .andExpect(jsonPath("$.achieved.length()").value(1))
                .andExpect(jsonPath("$.achieved[0].id").value(goalId))
                .andExpect(jsonPath("$.achieved[0].achievedAutomatically").value(true));
    }

    @Test
    void shouldNotCloseAGoalOnOneLuckyMorning() throws Exception {
        // A single reading can touch the target through dehydration and bounce back tomorrow.
        // Celebrating that would contradict everything else the weight module says.
        String admin = adminToken();
        String client = flagClient();
        steadyWeek(client, "80.0");
        createWeightGoal(admin, "78.0");

        weighIn(client, LocalDate.now(), "77.5");

        mockMvc.perform(get("/api/user/my-training/goals").header("Authorization", "Bearer " + client))
                .andExpect(jsonPath("$.active.length()").value(1))
                .andExpect(jsonPath("$.achieved.length()").value(0));
    }

    @Test
    void shouldWaitForThreeReadingsBeforeClosingAGoal() throws Exception {
        // Somebody weighing in twice a week has a "trend" that is one morning wearing a trend's
        // name. The target is reached on paper; the goal waits for the week to say so.
        String admin = adminToken();
        String client = flagClient();
        weighIn(client, LocalDate.now().minusDays(1), "80.0");
        weighIn(client, LocalDate.now(), "80.0");
        createWeightGoal(admin, "78.0");

        weighIn(client, LocalDate.now().minusDays(1), "77.0");
        weighIn(client, LocalDate.now(), "77.0");

        mockMvc.perform(get("/api/user/my-training/goals").header("Authorization", "Bearer " + client))
                .andExpect(jsonPath("$.active.length()").value(1))
                .andExpect(jsonPath("$.achieved.length()").value(0));

        // The third morning gives the window enough to stand on, and it closes on that weigh-in
        weighIn(client, LocalDate.now().minusDays(2), "77.0");

        mockMvc.perform(get("/api/user/my-training/goals").header("Authorization", "Bearer " + client))
                .andExpect(jsonPath("$.achieved.length()").value(1))
                .andExpect(jsonPath("$.achieved[0].achievedAutomatically").value(true));
    }

    @Test
    void shouldCloseAGainGoalWhenTheTrendRisesToIt() throws Exception {
        // Direction comes from the starting weight — the goal cannot know it any other way.
        String admin = adminToken();
        String client = flagClient();
        steadyWeek(client, "70.0");
        createWeightGoal(admin, "72.0");

        for (int i = 6; i >= 0; i--) {
            weighIn(client, LocalDate.now().minusDays(i), "72.0");
        }

        mockMvc.perform(get("/api/user/my-training/goals").header("Authorization", "Bearer " + client))
                .andExpect(jsonPath("$.achieved.length()").value(1));
    }

    @Test
    void shouldRefuseAWeightGoalBeforeThereIsAStartingWeight() throws Exception {
        // Without a starting point the goal cannot tell which direction is progress.
        String admin = adminToken();
        flagClient();

        mockMvc.perform(post("/api/admin/personal-trainings/goals?athleteId=" + clientId())
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"horizon":"SHORT","content":"Zejść do 78 kg","targetWeightKg":78.0}"""))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldLetTheCoachReopenAnAutomaticAchievementButNotAManualOne() throws Exception {
        // A typo inside the valid range drags the trend across the target; a person's decision stands.
        String admin = adminToken();
        String client = flagClient();
        steadyWeek(client, "80.0");
        String weightGoal = createWeightGoal(admin, "78.0");
        for (int i = 6; i >= 0; i--) {
            weighIn(client, LocalDate.now().minusDays(i), "78.0");
        }

        mockMvc.perform(post("/api/admin/personal-trainings/goals/" + weightGoal + "/reopen")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.achievedAt").doesNotExist());

        // A goal the coach closed by hand cannot be reopened
        String manual = mockMvc.perform(post("/api/admin/personal-trainings/goals?athleteId=" + clientId())
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"horizon":"LONG","content":"Zawody"}"""))
                .andReturn().getResponse().getContentAsString();
        String manualId = JsonPath.read(manual, "$.id");
        mockMvc.perform(post("/api/admin/personal-trainings/goals/" + manualId + "/achieve")
                .header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON).content("{}"));

        mockMvc.perform(post("/api/admin/personal-trainings/goals/" + manualId + "/reopen")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldLetAWeightGoalAndAGeneralGoalShareAHorizon() throws Exception {
        // They are different kinds of thing and should not compete for one slot.
        String admin = adminToken();
        String client = flagClient();
        steadyWeek(client, "80.0");
        createWeightGoal(admin, "78.0");

        mockMvc.perform(post("/api/admin/personal-trainings/goals?athleteId=" + clientId())
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"horizon":"SHORT","content":"Podciągnięcie x10"}"""))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/user/my-training/goals").header("Authorization", "Bearer " + client))
                .andExpect(jsonPath("$.active.length()").value(2));

        // But a second weight goal on the same horizon is still refused
        mockMvc.perform(post("/api/admin/personal-trainings/goals?athleteId=" + clientId())
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"horizon":"SHORT","content":"Inny cel","targetWeightKg":76.0}"""))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldCarryTheStartingWeightSoProgressCanBeShown() throws Exception {
        String admin = adminToken();
        String client = flagClient();
        steadyWeek(client, "80.0");
        createWeightGoal(admin, "78.0");

        mockMvc.perform(get("/api/user/my-training/goals").header("Authorization", "Bearer " + client))
                .andExpect(jsonPath("$.active[0].kind").value("WEIGHT"))
                .andExpect(jsonPath("$.active[0].startWeightKg").value(80.0))
                .andExpect(jsonPath("$.active[0].targetWeightKg").value(78.0));
    }

    @Test
    void shouldRefuseATargetEqualToTheCurrentWeight() throws Exception {
        String admin = adminToken();
        String client = flagClient();
        steadyWeek(client, "80.0");

        mockMvc.perform(post("/api/admin/personal-trainings/goals?athleteId=" + clientId())
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"horizon":"SHORT","content":"Nic","targetWeightKg":80.0}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldStillCloseAGoalReachedAfterItsDeadline() throws Exception {
        // Hitting 78 kg a week late is still hitting 78 kg — refusing to record it would be petty.
        String admin = adminToken();
        String client = flagClient();
        steadyWeek(client, "80.0");
        mockMvc.perform(post("/api/admin/personal-trainings/goals?athleteId=" + clientId())
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"horizon":"SHORT","content":"Zejść do 78","targetDate":"%s","targetWeightKg":78.0}"""
                                .formatted(LocalDate.now().minusDays(1))))
                .andExpect(status().isCreated());

        for (int i = 6; i >= 0; i--) {
            weighIn(client, LocalDate.now().minusDays(i), "78.0");
        }

        mockMvc.perform(get("/api/user/my-training/goals").header("Authorization", "Bearer " + client))
                .andExpect(jsonPath("$.achieved.length()").value(1));
    }
}
