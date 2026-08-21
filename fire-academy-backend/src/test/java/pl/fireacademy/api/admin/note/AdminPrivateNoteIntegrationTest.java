package pl.fireacademy.api.admin.note;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The notebook's rules: one note per (author, target), private to its author, gone with its target. */
class AdminPrivateNoteIntegrationTest extends BaseIntegrationTest {

    @Autowired private EventRepository eventRepository;
    @Autowired private EventTypeRepository eventTypeRepository;
    @Autowired private TrainingSlotRepository trainingSlotRepository;
    @Autowired private JdbcTemplate jdbc;

    // --- fixtures --------------------------------------------------------------------------------

    private Event seedEvent() {
        return eventRepository.saveAndFlush(
            new Event(EventCategory.CAMP, "Obóz letni", LocalDate.now().minusDays(30)));
    }

    private TrainingSlot seedSlot() {
        EventType type = eventTypeRepository.saveAndFlush(new EventType(EventCategory.TRAINING, "Boks"));
        TrainingSlot slot = new TrainingSlot(type, 3, LocalTime.of(18, 0), 8);
        slot.setActive(true);
        return trainingSlotRepository.saveAndFlush(slot);
    }

    private String secondAdminToken() {
        return createUserAndGetToken("other-admin@fireacademy.test", "Druga", "Adminka", UserRole.ADMIN);
    }

    private String flagAthlete() {
        String token = createUserAndGetToken("athlete@fireacademy.test", "Marek", "Testowy", UserRole.USER);
        User user = userRepository.findByEmail("athlete@fireacademy.test").orElseThrow();
        user.setAthlete(true);
        user.grantTrainingConsent();
        userRepository.saveAndFlush(user);
        return token;
    }

    private UUID athleteId() {
        return userRepository.findByEmail("athlete@fireacademy.test").orElseThrow().getId();
    }

    private void writeNote(String path, String token, String body, int status) throws Exception {
        mockMvc.perform(put(path).header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON).content("{\"body\":\"" + body + "\"}"))
            .andExpect(status().is(status));
    }

    private long countNotes() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM admin_private_notes", Long.class);
        return n == null ? 0 : n;
    }

    // --- privacy ---------------------------------------------------------------------------------

    @Test
    void shouldKeepOneAdminsNoteInvisibleToAnother() throws Exception {
        Event event = seedEvent();
        writeNote("/api/admin/notes/event/" + event.getId(), adminToken(), "Padał deszcz, mało osób", 204);

        mockMvc.perform(get("/api/admin/notes/event/" + event.getId())
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.body").value("Padał deszcz, mało osób"));

        // The other admin asks the same question about the same event and is told there is nothing.
        mockMvc.perform(get("/api/admin/notes/event/" + event.getId())
                .header("Authorization", "Bearer " + secondAdminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.body").doesNotExist())
            .andExpect(jsonPath("$.updatedAt").doesNotExist());
    }

    @Test
    void shouldRefuseTheNotebookToANonAdmin() throws Exception {
        Event event = seedEvent();
        mockMvc.perform(get("/api/admin/notes/event/" + event.getId())
                .header("Authorization", "Bearer " + userToken()))
            .andExpect(status().isForbidden());
    }

    // --- upsert ----------------------------------------------------------------------------------

    @Test
    void shouldTreatASecondWriteAsACorrectionRatherThanASecondNote() throws Exception {
        Event event = seedEvent();
        writeNote("/api/admin/notes/event/" + event.getId(), adminToken(), "Pierwsza wersja", 204);
        writeNote("/api/admin/notes/event/" + event.getId(), adminToken(), "Druga wersja", 204);

        assertEquals(1, countNotes());
        mockMvc.perform(get("/api/admin/notes/event/" + event.getId())
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(jsonPath("$.body").value("Druga wersja"));
    }

    @Test
    void shouldKeepQuotesAndApostrophesExactlyAsTyped() throws Exception {
        Event event = seedEvent();
        // No HTML escaping on the way in: the author is the only reader, and escaping here would
        // force every render site to undo it.
        writeNote("/api/admin/notes/event/" + event.getId(), adminToken(), "Powiedział \\\"pas\\\" & wyszedł", 204);

        mockMvc.perform(get("/api/admin/notes/event/" + event.getId())
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(jsonPath("$.body").value("Powiedział \"pas\" & wyszedł"));
    }

    @Test
    void shouldRefuseABlankNoteBecauseTheBinIsWhatDeletes() throws Exception {
        Event event = seedEvent();
        writeNote("/api/admin/notes/event/" + event.getId(), adminToken(), "   ", 400);
        assertEquals(0, countNotes());
    }

    @Test
    void shouldRejectAnUnknownTargetSegment() throws Exception {
        writeNote("/api/admin/notes/kalendarz/" + UUID.randomUUID(), adminToken(), "cokolwiek", 400);
    }

    @Test
    void shouldAnswer404ForATargetThatDoesNotExist() throws Exception {
        writeNote("/api/admin/notes/event/" + UUID.randomUUID(), adminToken(), "o niczym", 404);
    }

    // --- the athlete gate, and the hole it must NOT have -----------------------------------------

    @Test
    void shouldRefuseWritingAfterTheAthleteFlagIsClearedButStillAllowDeleting() throws Exception {
        // This is one test on purpose: the gate and the escape hatch are the same decision. Giving
        // all three operations the same guard is what stranded another person's data in the sibling
        // app — invisible AND undeletable at once.
        String admin = adminToken();
        flagAthlete();
        TrainingSlot slot = seedSlot();
        LocalDate date = LocalDate.now().minusDays(7);
        String path = "/api/admin/notes/session/" + slot.getId()
            + "?athleteId=" + athleteId() + "&date=" + date;

        writeNote(path, admin, "Dalej boi się wysokości", 204);
        assertEquals(1, countNotes());

        User athlete = userRepository.findById(athleteId()).orElseThrow();
        athlete.setAthlete(false);
        userRepository.saveAndFlush(athlete);

        // Reading and writing are gated: the calendar is out of reach, so the notebook is too.
        mockMvc.perform(get(path).header("Authorization", "Bearer " + admin))
            .andExpect(status().isNotFound());
        writeNote(path, admin, "Poprawka", 404);

        // Deleting is NOT gated — otherwise this row could never be removed.
        mockMvc.perform(delete(path).header("Authorization", "Bearer " + admin))
            .andExpect(status().isNoContent());
        assertEquals(0, countNotes());
    }

    @Test
    void shouldRequireBothTheAthleteAndTheDateForASessionNote() throws Exception {
        flagAthlete();
        TrainingSlot slot = seedSlot();
        writeNote("/api/admin/notes/session/" + slot.getId() + "?athleteId=" + athleteId(), adminToken(), "bez daty", 400);
    }

    @Test
    void shouldDeleteIdempotentlyWhenThereIsNoNote() throws Exception {
        Event event = seedEvent();
        mockMvc.perform(delete("/api/admin/notes/event/" + event.getId())
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isNoContent());
    }

    // --- what the database refuses, checked through raw SQL --------------------------------------

    private void rawInsert(String columns, String values) {
        adminToken();
        jdbc.update("INSERT INTO admin_private_notes (author_id, " + columns + ", body, updated_at) "
            + "VALUES (?, " + values + ", 'tekst', now())", adminUserId());
    }

    /** Asserting the exception alone would pass for ANY reason — the constraint name is the assertion. */
    private void assertRefusedBy(String constraint, Runnable statement) {
        DataIntegrityViolationException e = assertThrows(DataIntegrityViolationException.class, statement::run);
        String message = String.valueOf(e.getMessage());
        assertTrue(message.contains(constraint),
            "expected " + constraint + " to refuse this, but the database said: " + message);
    }

    @Test
    void shouldRefuseANoteWithNoTargetAtAll() {
        assertRefusedBy("chk_apn_single_target", () -> rawInsert("event_id", "NULL"));
    }

    @Test
    void shouldRefuseANoteWithTwoTargetsAtOnce() {
        adminToken();
        Event event = seedEvent();
        TrainingSlot slot = seedSlot();
        assertRefusedBy("chk_apn_single_target",
            () -> jdbc.update("INSERT INTO admin_private_notes (author_id, event_id, slot_id, body, updated_at)"
                + " VALUES (?, ?, ?, 'tekst', now())", adminUserId(), event.getId(), slot.getId()));
    }

    @Test
    void shouldRefuseASessionDateWithoutAnAthlete() {
        adminToken();
        TrainingSlot slot = seedSlot();
        assertRefusedBy("chk_apn_session_shape",
            () -> jdbc.update("INSERT INTO admin_private_notes (author_id, slot_id, session_date, body, updated_at)"
                + " VALUES (?, ?, DATE '2026-09-02', 'tekst', now())", adminUserId(), slot.getId()));
    }

    @Test
    void shouldRefuseAWhitespaceOnlyBodyAtTheDatabaseToo() {
        adminToken();
        Event event = seedEvent();
        assertRefusedBy("chk_apn_body_not_blank",
            () -> jdbc.update("INSERT INTO admin_private_notes (author_id, event_id, body, updated_at)"
                + " VALUES (?, ?, '   ', now())", adminUserId(), event.getId()));
    }

    @Test
    void shouldRefuseASecondNoteOnTheSameTargetByTheSameAuthor() throws Exception {
        Event event = seedEvent();
        writeNote("/api/admin/notes/event/" + event.getId(), adminToken(), "pierwsza", 204);
        // The partial index is what makes this a correction rather than a pile. Without the WHERE
        // predicate the NULL target columns would never collide and this would silently succeed.
        assertRefusedBy("uq_apn_event",
            () -> jdbc.update("INSERT INTO admin_private_notes (author_id, event_id, body, updated_at)"
                + " VALUES (?, ?, 'druga', now())", adminUserId(), event.getId()));
    }

    @Test
    void shouldAllowTwoSlotNotesOnlyWhenTheyBelongToDifferentAuthors() throws Exception {
        TrainingSlot slot = seedSlot();
        writeNote("/api/admin/notes/slot/" + slot.getId(), adminToken(), "za duża grupa", 204);
        writeNote("/api/admin/notes/slot/" + slot.getId(), secondAdminToken(), "moim zdaniem w porządku", 204);
        assertEquals(2, countNotes());
    }

    // --- markers ---------------------------------------------------------------------------------

    @Test
    void shouldReportMarkersAsIdentifiersAndNeverAsText() throws Exception {
        String admin = adminToken();
        TrainingSlot slot = seedSlot();
        Event event = seedEvent();
        writeNote("/api/admin/notes/slot/" + slot.getId(), admin, "za duża grupa", 204);
        writeNote("/api/admin/notes/event/" + event.getId(), admin, "padał deszcz", 204);

        String json = mockMvc.perform(get("/api/admin/notes/markers")
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.slotIds[0]").value(slot.getId().toString()))
            .andExpect(jsonPath("$.eventIds[0]").value(event.getId().toString()))
            .andReturn().getResponse().getContentAsString();

        // The whole point of a separate endpoint per note is undone if the month view hands back the
        // notebook. This answers "is there a note here" and nothing else.
        assertFalse(json.contains("za duża grupa"), json);
        assertFalse(json.contains("padał deszcz"), json);
    }

    @Test
    void shouldNotShowOneAdminsMarkersToAnother() throws Exception {
        TrainingSlot slot = seedSlot();
        writeNote("/api/admin/notes/slot/" + slot.getId(), adminToken(), "moja uwaga", 204);

        mockMvc.perform(get("/api/admin/notes/markers")
                .header("Authorization", "Bearer " + secondAdminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.slotIds").isEmpty());
    }

    @Test
    void shouldRefuseAnAthleteScopedMarkerQueryWithoutAWindow() throws Exception {
        flagAthlete();
        mockMvc.perform(get("/api/admin/notes/markers?athleteId=" + athleteId())
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRefuseAMarkerWindowWiderThanTheCalendarEverAsksFor() throws Exception {
        mockMvc.perform(get("/api/admin/notes/markers?from=2026-01-01&to=2026-12-31")
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isBadRequest());
    }

    // --- cascades --------------------------------------------------------------------------------

    @Test
    void shouldTakeTheNoteDownWithItsTarget() throws Exception {
        Event event = seedEvent();
        writeNote("/api/admin/notes/event/" + event.getId(), adminToken(), "o tym terminie", 204);
        assertEquals(1, countNotes());

        jdbc.update("DELETE FROM events WHERE id = ?", event.getId());
        assertEquals(0, countNotes());
    }

    @Test
    void shouldTakeTheNoteDownWithItsAuthorsAccount() throws Exception {
        Event event = seedEvent();
        String other = secondAdminToken();
        writeNote("/api/admin/notes/event/" + event.getId(), other, "notatka drugiej adminki", 204);
        assertEquals(1, countNotes());

        UUID otherId = userRepository.findByEmail("other-admin@fireacademy.test").orElseThrow().getId();
        jdbc.update("DELETE FROM users WHERE id = ?", otherId);
        assertEquals(0, countNotes());
    }

    @Test
    void shouldTakeASessionNoteDownWithTheAthleteItIsAbout() throws Exception {
        flagAthlete();
        TrainingSlot slot = seedSlot();
        LocalDate date = LocalDate.now().minusDays(3);
        writeNote("/api/admin/notes/session/" + slot.getId() + "?athleteId=" + athleteId() + "&date=" + date,
            adminToken(), "spóźnił się", 204);
        assertEquals(1, countNotes());

        jdbc.update("DELETE FROM users WHERE id = ?", athleteId());
        assertEquals(0, countNotes());
    }
}
