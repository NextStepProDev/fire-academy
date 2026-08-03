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

class AthleteGoalIntegrationTest extends BaseIntegrationTest {

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

    private String addGoal(String admin, UUID athleteId, String horizon, String content) throws Exception {
        String json = mockMvc.perform(post("/api/admin/personal-trainings/goals?athleteId=" + athleteId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"horizon":"%s","content":"%s"}""".formatted(horizon, content)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    @Test
    void shouldAllowOneActiveGoalPerHorizonButManyAchievedOnes() throws Exception {
        // A plain unique constraint would cap the client at three goals for life; the partial index
        // caps only the ACTIVE ones, and everything achieved piles up in the trophy case.
        String admin = adminToken();
        flagClient();
        UUID id = clientId();
        String first = addGoal(admin, id, "SHORT", "Podciągnięcie x10");

        mockMvc.perform(post("/api/admin/personal-trainings/goals?athleteId=" + id)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"horizon":"SHORT","content":"Coś innego"}"""))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/admin/personal-trainings/goals/" + first + "/achieve")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        // The slot is free again, and the achieved one is kept
        addGoal(admin, id, "SHORT", "Podciągnięcie x15");
        mockMvc.perform(get("/api/admin/personal-trainings/goals?athleteId=" + id)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.active.length()").value(1))
                .andExpect(jsonPath("$.achieved.length()").value(1))
                .andExpect(jsonPath("$.achieved[0].content").value("Podciągnięcie x10"));
    }

    @Test
    void shouldMakeAnAchievedGoalImmutable() throws Exception {
        // The trophy case is history — editing or deleting an entry would rewrite it.
        String admin = adminToken();
        flagClient();
        String goalId = addGoal(admin, clientId(), "LONG", "Zawody");
        mockMvc.perform(post("/api/admin/personal-trainings/goals/" + goalId + "/achieve")
                .header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON).content("{}"));

        mockMvc.perform(put("/api/admin/personal-trainings/goals/" + goalId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"horizon":"LONG","content":"Zmienione"}"""))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/admin/personal-trainings/goals/" + goalId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/admin/personal-trainings/goals/" + goalId + "/achieve")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldAllowBackDatingAchievementButNotPostDatingIt() throws Exception {
        // The coach usually notices days later; claiming a future achievement is meaningless.
        String admin = adminToken();
        flagClient();
        String goalId = addGoal(admin, clientId(), "MEDIUM", "Bieg 10 km");

        mockMvc.perform(post("/api/admin/personal-trainings/goals/" + goalId + "/achieve")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"achievedDate":"%s"}""".formatted(LocalDate.now().plusDays(1))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/admin/personal-trainings/goals/" + goalId + "/achieve")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"achievedDate":"%s"}""".formatted(LocalDate.now().minusDays(5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.achievedAt").value(LocalDate.now().minusDays(5).toString()));
    }

    @Test
    void shouldKeepGoalsReadOnlyForTheClient() throws Exception {
        String admin = adminToken();
        String client = flagClient();
        addGoal(admin, clientId(), "SHORT", "Podciągnięcie x10");

        mockMvc.perform(get("/api/user/my-training/goals").header("Authorization", "Bearer " + client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active.length()").value(1));

        // There is no client-side write path at all — the admin one is out of reach
        mockMvc.perform(post("/api/admin/personal-trainings/goals?athleteId=" + clientId())
                        .header("Authorization", "Bearer " + client)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"horizon":"LONG","content":"Sam sobie ustawiam"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldNotifyTheClientAboutANewGoal() throws Exception {
        // Goals are the one-directional seventh unread source.
        String admin = adminToken();
        String client = flagClient();
        mockMvc.perform(post("/api/user/my-training/mark-seen").header("Authorization", "Bearer " + client));

        addGoal(admin, clientId(), "SHORT", "Podciągnięcie x10");

        mockMvc.perform(get("/api/user/my-training/summary").header("Authorization", "Bearer " + client))
                .andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    void shouldOrderActiveGoalsByHorizon() throws Exception {
        // Declaration order is the order the three cards render in.
        String admin = adminToken();
        flagClient();
        UUID id = clientId();
        addGoal(admin, id, "LONG", "Za rok");
        addGoal(admin, id, "SHORT", "Za miesiąc");
        addGoal(admin, id, "MEDIUM", "Za pół roku");

        mockMvc.perform(get("/api/admin/personal-trainings/goals?athleteId=" + id)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.active[0].horizon").value("SHORT"))
                .andExpect(jsonPath("$.active[1].horizon").value("MEDIUM"))
                .andExpect(jsonPath("$.active[2].horizon").value("LONG"));
    }

    @Test
    void shouldPutGoalsBeyondReachWhenTheClientFlagIsCleared() throws Exception {
        // Dropping the flag is what puts a person's plan away — the calendar already refuses, and a
        // goal left editable behind it would be the one piece of their record that stayed open.
        String admin = adminToken();
        flagClient();
        UUID id = clientId();
        String goal = addGoal(admin, id, "SHORT", "Podciągnięcie x10");

        mockMvc.perform(delete("/api/admin/users/" + id + "/athlete")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/admin/personal-trainings/goals/" + goal)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"horizon":"SHORT","content":"Zmienione"}"""))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/admin/personal-trainings/goals/" + goal + "/achieve")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/admin/personal-trainings/goals/" + goal)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
    }
}
