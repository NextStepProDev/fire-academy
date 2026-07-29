package pl.fireacademy.api.trainingcalendar;

import org.junit.jupiter.api.Test;
import pl.fireacademy.BaseIntegrationTest;
import pl.fireacademy.domain.user.UserRole;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AthleteFlagIntegrationTest extends BaseIntegrationTest {

    @Test
    void shouldFlagUserAsAthleteAndListThemOnTheRoster() throws Exception {
        // Given: a plain account, not on the roster yet
        String admin = adminToken();
        userToken();
        var userId = regularUserId();

        mockMvc.perform(get("/api/admin/athletes").header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));

        // When: the admin flags them
        mockMvc.perform(post("/api/admin/users/" + userId + "/athlete")
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isAthlete").value(true));

        // Then: they show up on the roster
        mockMvc.perform(get("/api/admin/athletes").header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(userId.toString()))
            .andExpect(jsonPath("$[0].firstName").value("User"));
    }

    @Test
    void shouldExposeAthleteFlagOnOwnProfile() throws Exception {
        // Given: a flagged account — the frontend shows the calendar tile off this flag,
        // so it must ride along on /me instead of costing an extra request
        String admin = adminToken();
        String user = userToken();
        var userId = regularUserId();

        mockMvc.perform(get("/api/user/me").header("Authorization", "Bearer " + user))
            .andExpect(jsonPath("$.isAthlete").value(false));

        mockMvc.perform(post("/api/admin/users/" + userId + "/athlete")
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/user/me").header("Authorization", "Bearer " + user))
            .andExpect(jsonPath("$.isAthlete").value(true));
    }

    @Test
    void shouldRemoveFromRosterWithoutDeletingTheAccount() throws Exception {
        // Given: a flagged account
        String admin = adminToken();
        userToken();
        var userId = regularUserId();
        mockMvc.perform(post("/api/admin/users/" + userId + "/athlete")
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk());

        // When: the flag is cleared
        mockMvc.perform(delete("/api/admin/users/" + userId + "/athlete")
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isAthlete").value(false));

        // Then: gone from the roster, but the account itself is untouched
        mockMvc.perform(get("/api/admin/athletes").header("Authorization", "Bearer " + admin))
            .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/admin/users/" + userId).header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isAthlete").value(false));
    }

    @Test
    void shouldRejectRosterAccessForNonAdmins() throws Exception {
        // Given: an ordinary account — the roster is coach-only
        String user = userToken();

        mockMvc.perform(get("/api/admin/athletes").header("Authorization", "Bearer " + user))
            .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnNotFoundWhenFlaggingAnUnknownAccount() throws Exception {
        String admin = adminToken();

        mockMvc.perform(post("/api/admin/users/" + java.util.UUID.randomUUID() + "/athlete")
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isNotFound());
    }

    @Test
    void shouldKeepFlaggingSeparateFromTheAdminRole() throws Exception {
        // Given: flagging someone as a coaching client must not touch their role — it is plain admin
        // work, not the super-admin privilege that promote/demote requires
        String admin = adminToken();
        String coachClient = createUserAndGetToken("client@fireacademy.test", "Ala", "Nowak", UserRole.USER);
        var clientId = userRepository.findByEmail("client@fireacademy.test").orElseThrow().getId();

        mockMvc.perform(post("/api/admin/users/" + clientId + "/athlete")
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isAthlete").value(true))
            .andExpect(jsonPath("$.isAdmin").value(false))
            .andExpect(jsonPath("$.role").value("USER"));

        // And: they still cannot reach admin endpoints
        mockMvc.perform(get("/api/admin/athletes").header("Authorization", "Bearer " + coachClient))
            .andExpect(status().isForbidden());
    }
}
