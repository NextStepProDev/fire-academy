package pl.fireacademy.api.admin.note;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The leak test, taken from the opposite side to the architecture gate.
 *
 * <p>The gate proves nothing outside the notebook's packages can READ a note. This one proves that
 * the shapes which are already shared with the client do not CARRY one. Both are needed: the gate
 * would not notice a note copied into a DTO inside the notebook's own package, and this test would
 * not notice a new service reading notes for some other purpose.
 *
 * <p>The assertion runs against the SERIALIZED JSON rather than the record's components, because a
 * field somebody adds later travels to the browser whether or not they remembered this test existed.
 */
class AdminNoteLeakIntegrationTest extends BaseIntegrationTest {

    private static final String TRAINING_NOTE = "PRYWATNA-NOTATKA-O-TRENINGU-nie-dla-podopiecznego";
    private static final String SESSION_NOTE = "PRYWATNA-NOTATKA-O-ZAJECIACH-nie-dla-podopiecznego";

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

    @Test
    void shouldNeverPutANoteIntoTheCalendarSharedWithTheClient() throws Exception {
        String admin = adminToken();
        String athlete = flagAthlete();
        LocalDate today = LocalDate.now();

        // A group slot on today's weekday, subscribed to this month, so `recurring` is populated.
        EventType type = eventTypeRepository.saveAndFlush(new EventType(EventCategory.TRAINING, "Boks"));
        TrainingSlot slot = new TrainingSlot(type, today.getDayOfWeek().getValue(), LocalTime.of(18, 0), 8);
        slot.setActive(true);
        trainingSlotRepository.saveAndFlush(slot);
        mockMvc.perform(post("/api/user/training-slots/" + slot.getId() + "/enroll")
                .header("Authorization", "Bearer " + athlete)
                .contentType(APPLICATION_JSON)
                .content("{\"startMonth\":\"" + YearMonth.now() + "\"}"))
            .andExpect(status().is2xxSuccessful());

        // A 1-on-1 training the coach then writes a private note about.
        String created = mockMvc.perform(post("/api/admin/personal-trainings?athleteId=" + athleteId())
                .header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content("{\"date\":\"" + today + "\",\"title\":\"Trening\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String trainingId = JsonPath.read(created, "$.id");

        writeNote("/api/admin/notes/training/" + trainingId, admin, TRAINING_NOTE);
        writeNote("/api/admin/notes/session/" + slot.getId()
            + "?athleteId=" + athleteId() + "&date=" + today, admin, SESSION_NOTE);

        // ⚠ Without this the test also passes when the fixture quietly saved nothing — which makes it
        // indistinguishable from a test that checks nothing at all.
        assertEquals(2L, jdbc.queryForObject("SELECT count(*) FROM admin_private_notes", Long.class),
            "the notes were never written, so finding nothing in the JSON would prove nothing");

        String coachView = mockMvc.perform(get("/api/admin/personal-trainings"
                + "?athleteId=" + athleteId() + "&from=" + today + "&to=" + today.plusDays(6))
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        String clientView = mockMvc.perform(get("/api/user/my-training/calendar"
                + "?from=" + today + "&to=" + today.plusDays(6))
                .header("Authorization", "Bearer " + athlete))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        // The shapes really are populated — otherwise "no note here" is a statement about an empty page.
        assertTrue(clientView.contains("\"trainings\":[{"), "the client's calendar carried no trainings");
        assertTrue(clientView.contains("\"recurring\":[{"), "the client's calendar carried no group sessions");

        assertNoteFree(clientView, "the client's own calendar");
        // The coach's response is the SAME record type. It may not carry notes either: they are served
        // from their own endpoint, so no shared shape and no cache has anything to leak.
        assertNoteFree(coachView, "the coach's calendar");
    }

    private void writeNote(String path, String token, String body) throws Exception {
        mockMvc.perform(put(path).header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON).content("{\"body\":\"" + body + "\"}"))
            .andExpect(status().isNoContent());
    }

    private static void assertNoteFree(String json, String what) {
        assertFalse(json.contains(TRAINING_NOTE), "a private note reached " + what + ": " + json);
        assertFalse(json.contains(SESSION_NOTE), "a private note reached " + what + ": " + json);
    }
}
