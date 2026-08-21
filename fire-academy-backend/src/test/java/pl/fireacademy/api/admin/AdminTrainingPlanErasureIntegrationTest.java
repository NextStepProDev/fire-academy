package pl.fireacademy.api.admin;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.fireacademy.BaseIntegrationTest;
import pl.fireacademy.domain.event.EventCategory;
import pl.fireacademy.domain.event.EventType;
import pl.fireacademy.domain.event.EventTypeRepository;
import pl.fireacademy.domain.training.TrainingSlot;
import pl.fireacademy.domain.training.TrainingSlotRepository;
import pl.fireacademy.domain.user.User;
import pl.fireacademy.domain.user.UserRole;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Erasing one person's 1-on-1 plan — and, more importantly, what it must leave alone.
 *
 * <p>The expensive mistake here is not failing to delete something. It is deleting the group
 * subscription of somebody who still trains and still pays.
 */
class AdminTrainingPlanErasureIntegrationTest extends BaseIntegrationTest {

    @Autowired private EventTypeRepository eventTypeRepository;
    @Autowired private TrainingSlotRepository trainingSlotRepository;
    @Autowired private JdbcTemplate jdbc;

    private String flagAthlete() {
        String token = createUserAndGetToken("marek@fireacademy.test", "Marek", "Testowy", UserRole.USER);
        User user = userRepository.findByEmail("marek@fireacademy.test").orElseThrow();
        user.setAthlete(true);
        user.grantTrainingConsent();
        userRepository.saveAndFlush(user);
        return token;
    }

    private UUID athleteId() {
        return userRepository.findByEmail("marek@fireacademy.test").orElseThrow().getId();
    }

    private long count(String table, String where, Object arg) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + where, Long.class, arg);
        return n == null ? 0 : n;
    }

    /** A client with a group subscription, a 1-on-1 plan, a weight, and notes of both kinds. */
    private TrainingSlot seedEverything(String admin, String athlete) throws Exception {
        LocalDate today = LocalDate.now();
        EventType type = eventTypeRepository.saveAndFlush(new EventType(EventCategory.TRAINING, "Boks"));
        TrainingSlot slot = new TrainingSlot(type, today.getDayOfWeek().getValue(), LocalTime.of(18, 0), 8);
        slot.setActive(true);
        trainingSlotRepository.saveAndFlush(slot);

        mockMvc.perform(post("/api/user/training-slots/" + slot.getId() + "/enroll")
                .header("Authorization", "Bearer " + athlete)
                .contentType(APPLICATION_JSON)
                .content("{\"startMonth\":\"" + YearMonth.now() + "\"}"))
            .andExpect(status().is2xxSuccessful());

        String created = mockMvc.perform(post("/api/admin/personal-trainings?athleteId=" + athleteId())
                .header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content("{\"date\":\"" + today + "\",\"title\":\"Trening\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String trainingId = JsonPath.read(created, "$.id");

        mockMvc.perform(put("/api/user/my-training/weights")
                .header("Authorization", "Bearer " + athlete)
                .contentType(APPLICATION_JSON)
                .content("{\"date\":\"" + today + "\",\"weightKg\":80.5}"))
            .andExpect(status().is2xxSuccessful());

        note("/api/admin/notes/training/" + trainingId, admin, "o jego treningu");
        note("/api/admin/notes/session/" + slot.getId() + "?athleteId=" + athleteId() + "&date=" + today,
            admin, "o jego zajeciach");
        note("/api/admin/notes/slot/" + slot.getId(), admin, "grupa za duza");
        return slot;
    }

    private void note(String path, String token, String body) throws Exception {
        mockMvc.perform(put(path).header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON).content("{\"body\":\"" + body + "\"}"))
            .andExpect(status().isNoContent());
    }

    @Test
    void shouldEraseThePlanAndTheNotesAboutThePersonButLeaveTheGroupSubscriptionStanding()
            throws Exception {
        String admin = adminToken();
        String athlete = flagAthlete();
        TrainingSlot slot = seedEverything(admin, athlete);
        UUID id = athleteId();

        assertEquals(1, count("personal_trainings", "athlete_id = ?", id));
        assertEquals(1, count("athlete_weights", "athlete_id = ?", id));
        assertEquals(3, count("admin_private_notes", "author_id IS NOT NULL AND ? IS NOT NULL", id));
        assertEquals(1, count("training_enrollments", "user_id = ?", id));

        mockMvc.perform(delete("/api/admin/users/" + id + "/training-plan")
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.trainings").value(1))
            .andExpect(jsonPath("$.weights").value(1))
            .andExpect(jsonPath("$.notes").value(1));

        // Gone: the whole 1-on-1 side.
        assertEquals(0, count("personal_trainings", "athlete_id = ?", id));
        assertEquals(0, count("athlete_weights", "athlete_id = ?", id));
        assertEquals(0, count("athlete_goals", "athlete_id = ?", id));
        assertEquals(0, count("admin_private_notes", "athlete_id = ?", id));

        // THE POINT: he still trains on Wednesdays and still owes for this month.
        assertEquals(1, count("training_enrollments", "user_id = ?", id));
        // The note about the SLOT is an observation about the business, not about him.
        assertEquals(1, count("admin_private_notes", "slot_id = ? AND session_date IS NULL", slot.getId()));
        // And the account itself is untouched.
        assertEquals(1, count("users", "id = ?", id));

        User after = userRepository.findById(id).orElseThrow();
        assertEquals(false, after.isAthlete());
        assertEquals(null, after.getTrainingConsentAt());
    }

    @Test
    void shouldStillEraseAfterTheAthleteFlagWasAlreadyCleared() throws Exception {
        // The stranded case: with the flag gone the coach's calendar is unreachable, so this is the
        // only route left to the data. If it refused here, the notes could never be removed at all.
        String admin = adminToken();
        String athlete = flagAthlete();
        seedEverything(admin, athlete);
        UUID id = athleteId();

        mockMvc.perform(delete("/api/admin/users/" + id + "/athlete")
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk());
        assertEquals(1, count("personal_trainings", "athlete_id = ?", id));

        mockMvc.perform(delete("/api/admin/users/" + id + "/training-plan")
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk());

        assertEquals(0, count("personal_trainings", "athlete_id = ?", id));
        assertEquals(0, count("admin_private_notes", "athlete_id = ?", id));
    }

    @Test
    void shouldRefuseTheErasureToANonAdmin() throws Exception {
        flagAthlete();
        mockMvc.perform(delete("/api/admin/users/" + athleteId() + "/training-plan")
                .header("Authorization", "Bearer " + userToken()))
            .andExpect(status().isForbidden());
    }
}
