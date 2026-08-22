package pl.fireacademy.api.admin.note;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.fireacademy.BaseIntegrationTest;
import pl.fireacademy.domain.event.Event;
import pl.fireacademy.domain.event.EventCategory;
import pl.fireacademy.domain.event.EventRepository;
import pl.fireacademy.domain.event.EventType;
import pl.fireacademy.domain.event.EventTypeRepository;
import pl.fireacademy.domain.training.TrainingSlot;
import pl.fireacademy.domain.training.TrainingSlotRepository;
import pl.fireacademy.domain.user.User;
import pl.fireacademy.domain.user.UserRole;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * One notebook per admin, checked on every surface rather than on the one that happened to get a
 * test first.
 *
 * <p>The premise of the whole feature is that a note reaches its author and nobody else — not the
 * client, not the athlete, and not a second admin. A second admin is the easiest of those to forget,
 * because there is usually only one; the accounts exist though, since admins can be promoted from
 * the panel.
 *
 * <p>Two admins, ALFA and BETA. ALFA writes on all four targets; BETA must find nothing, overwrite
 * nothing, delete nothing, and learn nothing — including from a count.
 */
class AdminNotePerAuthorIsolationIntegrationTest extends BaseIntegrationTest {

    @Autowired private EventRepository eventRepository;
    @Autowired private EventTypeRepository eventTypeRepository;
    @Autowired private TrainingSlotRepository trainingSlotRepository;
    @Autowired private JdbcTemplate jdbc;

    private String beta() {
        return createUserAndGetToken("beta@fireacademy.test", "Beta", "Adminka", UserRole.ADMIN);
    }

    private UUID betaId() {
        return userRepository.findByEmail("beta@fireacademy.test").orElseThrow().getId();
    }

    private UUID flagAthlete() {
        createUserAndGetToken("marek@fireacademy.test", "Marek", "Testowy", UserRole.USER);
        User user = userRepository.findByEmail("marek@fireacademy.test").orElseThrow();
        user.setAthlete(true);
        user.grantTrainingConsent();
        userRepository.saveAndFlush(user);
        return user.getId();
    }

    private void write(String path, String token, String body) throws Exception {
        mockMvc.perform(put(path).header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON).content("{\"body\":\"" + body + "\"}"))
            .andExpect(status().isNoContent());
    }

    private void expectBody(String path, String token, String expected) throws Exception {
        mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.body").value(expected));
    }

    private void expectNothing(String path, String token) throws Exception {
        mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.body").doesNotExist())
            .andExpect(jsonPath("$.updatedAt").doesNotExist());
    }

    @Test
    void shouldHideEveryKindOfNoteFromASecondAdmin() throws Exception {
        String alfa = adminToken();
        String beta = beta();
        UUID athlete = flagAthlete();
        LocalDate today = LocalDate.now();

        Event event = eventRepository.saveAndFlush(
            new Event(EventCategory.CAMP, "Obóz", today.minusDays(20)));
        EventType type = eventTypeRepository.saveAndFlush(new EventType(EventCategory.TRAINING, "Boks"));
        TrainingSlot slot = new TrainingSlot(type, 3, LocalTime.of(18, 0), 8);
        slot.setActive(true);
        trainingSlotRepository.saveAndFlush(slot);

        String trainingId = JsonPath.read(mockMvc.perform(
                post("/api/admin/personal-trainings?athleteId=" + athlete)
                    .header("Authorization", "Bearer " + alfa)
                    .contentType(APPLICATION_JSON)
                    .content("{\"date\":\"" + today + "\",\"title\":\"Trening\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString(), "$.id");

        String eventPath = "/api/admin/notes/event/" + event.getId();
        String slotPath = "/api/admin/notes/slot/" + slot.getId();
        String trainingPath = "/api/admin/notes/training/" + trainingId;
        String sessionPath = "/api/admin/notes/session/" + slot.getId()
            + "?athleteId=" + athlete + "&date=" + today;

        write(eventPath, alfa, "ALFA-o-terminie");
        write(slotPath, alfa, "ALFA-o-slocie");
        write(trainingPath, alfa, "ALFA-o-treningu");
        write(sessionPath, alfa, "ALFA-o-zajeciach");

        // All four, read by the other admin: nothing there.
        expectNothing(eventPath, beta);
        expectNothing(slotPath, beta);
        expectNothing(trainingPath, beta);
        expectNothing(sessionPath, beta);

        // BETA writing on the same targets makes their OWN notes — it does not overwrite ALFA's.
        write(eventPath, beta, "BETA-o-terminie");
        write(trainingPath, beta, "BETA-o-treningu");
        expectBody(eventPath, alfa, "ALFA-o-terminie");
        expectBody(trainingPath, alfa, "ALFA-o-treningu");
        expectBody(eventPath, beta, "BETA-o-terminie");

        // BETA deleting removes only their own row.
        mockMvc.perform(delete(eventPath).header("Authorization", "Bearer " + beta))
            .andExpect(status().isNoContent());
        expectBody(eventPath, alfa, "ALFA-o-terminie");
        expectNothing(eventPath, beta);
    }

    @Test
    void shouldKeepMarkersPerAdminOnEverySurface() throws Exception {
        String alfa = adminToken();
        String beta = beta();
        UUID athlete = flagAthlete();
        LocalDate today = LocalDate.now();

        EventType type = eventTypeRepository.saveAndFlush(new EventType(EventCategory.TRAINING, "Boks"));
        TrainingSlot slot = new TrainingSlot(type, 3, LocalTime.of(18, 0), 8);
        slot.setActive(true);
        trainingSlotRepository.saveAndFlush(slot);
        Event event = eventRepository.saveAndFlush(new Event(EventCategory.CAMP, "Obóz", today));

        String trainingId = JsonPath.read(mockMvc.perform(
                post("/api/admin/personal-trainings?athleteId=" + athlete)
                    .header("Authorization", "Bearer " + alfa)
                    .contentType(APPLICATION_JSON)
                    .content("{\"date\":\"" + today + "\",\"title\":\"Trening\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString(), "$.id");

        write("/api/admin/notes/slot/" + slot.getId(), alfa, "ALFA-slot");
        write("/api/admin/notes/event/" + event.getId(), alfa, "ALFA-event");
        write("/api/admin/notes/training/" + trainingId, alfa, "ALFA-training");
        write("/api/admin/notes/session/" + slot.getId()
            + "?athleteId=" + athlete + "&date=" + today, alfa, "ALFA-session");

        // Unranged variant (the admin tabs) and the athlete-scoped one (the calendar) — both empty.
        mockMvc.perform(get("/api/admin/notes/markers").header("Authorization", "Bearer " + beta))
            .andExpect(jsonPath("$.slotIds").isEmpty())
            .andExpect(jsonPath("$.eventIds").isEmpty());

        mockMvc.perform(get("/api/admin/notes/markers?athleteId=" + athlete
                + "&from=" + today + "&to=" + today.plusDays(6))
                .header("Authorization", "Bearer " + beta))
            .andExpect(jsonPath("$.trainingIds").isEmpty())
            .andExpect(jsonPath("$.sessions").isEmpty());

        // ...and ALFA still sees all four, so the emptiness above is isolation and not a broken query.
        mockMvc.perform(get("/api/admin/notes/markers?athleteId=" + athlete
                + "&from=" + today + "&to=" + today.plusDays(6))
                .header("Authorization", "Bearer " + alfa))
            .andExpect(jsonPath("$.slotIds[0]").value(slot.getId().toString()))
            .andExpect(jsonPath("$.eventIds[0]").value(event.getId().toString()))
            .andExpect(jsonPath("$.trainingIds[0]").value(trainingId))
            .andExpect(jsonPath("$.sessions[0].slotId").value(slot.getId().toString()));
    }

    @Test
    void shouldNotTellOneAdminHowManyNotesTheOtherKeeps() throws Exception {
        // Erasing a plan deletes every note about that person, whoever wrote it — that is the point
        // of erasure. The COUNT that comes back must still describe only the caller's own, or the
        // response quietly reports the existence and size of the other admin's notebook.
        String alfa = adminToken();
        String beta = beta();
        UUID athlete = flagAthlete();
        LocalDate today = LocalDate.now();

        EventType type = eventTypeRepository.saveAndFlush(new EventType(EventCategory.TRAINING, "Boks"));
        TrainingSlot slot = new TrainingSlot(type, 3, LocalTime.of(18, 0), 8);
        slot.setActive(true);
        trainingSlotRepository.saveAndFlush(slot);

        String sessionPath = "/api/admin/notes/session/" + slot.getId()
            + "?athleteId=" + athlete + "&date=" + today;
        write(sessionPath, alfa, "ALFA-o-zajeciach");
        write(sessionPath, beta, "BETA-o-zajeciach");
        assertEquals(2, jdbc.queryForObject(
            "SELECT count(*) FROM admin_private_notes WHERE athlete_id = ?", Long.class, athlete));

        mockMvc.perform(delete("/api/admin/users/" + athlete + "/training-plan")
                .header("Authorization", "Bearer " + beta))
            .andExpect(status().isOk())
            // BETA wrote exactly one. Two would be BETA learning that somebody else keeps notes here.
            .andExpect(jsonPath("$.notes").value(1));

        // Both are gone from the database — erasure is still complete.
        assertEquals(0, jdbc.queryForObject(
            "SELECT count(*) FROM admin_private_notes WHERE athlete_id = ?", Long.class, athlete));
        assertEquals(betaId(), betaId());
    }
}
