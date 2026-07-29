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

class StatsVisibilityIntegrationTest extends BaseIntegrationTest {

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

    /** Creates a past training and has the client tick it off with the given effort. */
    private void completedTraining(String admin, String client, LocalDate date, int rpe) throws Exception {
        String json = mockMvc.perform(post("/api/admin/personal-trainings?athleteId=" + clientId())
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"date":"%s","title":"Trening"}""".formatted(date)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(json, "$.id");
        mockMvc.perform(post("/api/user/my-training/trainings/" + id + "/complete")
                        .header("Authorization", "Bearer " + client)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"rpe":%d}""".formatted(rpe)))
                .andExpect(status().isOk());
    }

    @Test
    void shouldKeepTheOvertrainingSignalOutOfTheClientsResponseEntirely() throws Exception {
        // Not "false" — absent. The client asked for their own numbers, not for a web page's verdict
        // on whether they are overreaching; that is a conversation for their coach to start.
        String admin = adminToken();
        String client = flagClient();
        for (int i = 1; i <= 6; i++) {
            completedTraining(admin, client, LocalDate.now().minusDays(i), 10);
        }

        mockMvc.perform(get("/api/admin/personal-trainings/stats?athleteId=" + clientId())
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overtraining").value(true));

        mockMvc.perform(get("/api/user/my-training/stats").header("Authorization", "Bearer " + client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overtraining").doesNotExist());
    }

    @Test
    void shouldNotFlagOvertrainingUntilSixConsecutiveMaximalSessions() throws Exception {
        String admin = adminToken();
        String client = flagClient();
        for (int i = 1; i <= 5; i++) {
            completedTraining(admin, client, LocalDate.now().minusDays(i), 10);
        }

        mockMvc.perform(get("/api/admin/personal-trainings/stats?athleteId=" + clientId())
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.overtraining").value(false));
    }

    @Test
    void shouldReflectAnUndoneCompletionImmediately() throws Exception {
        // Why the panel is not cached: a coach who unticks a session and still sees it counted stops
        // trusting the numbers.
        String admin = adminToken();
        String client = flagClient();
        LocalDate yesterday = LocalDate.now().minusDays(1);
        String json = mockMvc.perform(post("/api/admin/personal-trainings?athleteId=" + clientId())
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"date":"%s","title":"Trening"}""".formatted(yesterday)))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(json, "$.id");
        mockMvc.perform(post("/api/user/my-training/trainings/" + id + "/complete")
                .header("Authorization", "Bearer " + client)
                .contentType(APPLICATION_JSON).content("""
                    {"rpe":7}"""));

        mockMvc.perform(get("/api/user/my-training/stats").header("Authorization", "Bearer " + client))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.avgRpeOverall").value(7.0));

        mockMvc.perform(delete("/api/user/my-training/trainings/" + id + "/complete")
                .header("Authorization", "Bearer " + client));

        mockMvc.perform(get("/api/user/my-training/stats").header("Authorization", "Bearer " + client))
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.avgRpeOverall").doesNotExist());
    }

    @Test
    void shouldReportNoAttendanceRateBeforeAnythingWasPlanned() throws Exception {
        // 0% would claim a failure that never happened.
        String client = flagClient();

        mockMvc.perform(get("/api/user/my-training/stats").header("Authorization", "Bearer " + client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendancePercent").doesNotExist())
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    void shouldCountMissedTrainingsAgainstAttendance() throws Exception {
        String admin = adminToken();
        String client = flagClient();
        completedTraining(admin, client, LocalDate.now().minusDays(2), 6);
        // Planned, in the past, never ticked off
        mockMvc.perform(post("/api/admin/personal-trainings?athleteId=" + clientId())
                .header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"date":"%s","title":"Pominięty"}""".formatted(LocalDate.now().minusDays(3))));

        mockMvc.perform(get("/api/user/my-training/stats").header("Authorization", "Bearer " + client))
                .andExpect(jsonPath("$.attendancePercent").value(50));
    }

    @Test
    void shouldNotCountTodaysUntickedTrainingAsMissed() throws Exception {
        // An untimed training runs until the end of its day. Counting it as missed at 10:00 tells the
        // client they failed a session they still have all day to do.
        String admin = adminToken();
        String client = flagClient();
        mockMvc.perform(post("/api/admin/personal-trainings?athleteId=" + clientId())
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("""
                            {"date":"%s","title":"Dzisiejszy"}""".formatted(LocalDate.now())))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/user/my-training/stats").header("Authorization", "Bearer " + client))
                .andExpect(status().isOk())
                // Nothing has been completed and nothing has been missed, so there is no rate to give
                .andExpect(jsonPath("$.attendancePercent").doesNotExist());
    }

    @Test
    void shouldKeepStatsOutOfReachForOtherPeople() throws Exception {
        String admin = adminToken();
        flagClient();
        String stranger = createUserAndGetToken("other@fireacademy.test", "Ola", "Obca", UserRole.USER);

        mockMvc.perform(get("/api/admin/personal-trainings/stats?athleteId=" + clientId())
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isForbidden());
        // An account without the flag has no stats of its own either
        mockMvc.perform(get("/api/user/my-training/stats").header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/personal-trainings/stats?athleteId=" + clientId())
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
    }
}
