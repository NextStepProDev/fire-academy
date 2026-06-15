package pl.fireacademy.api.training;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pl.fireacademy.BaseIntegrationTest;
import pl.fireacademy.infrastructure.mail.TrainingMailService;
import pl.fireacademy.domain.event.EventCategory;
import pl.fireacademy.domain.event.EventType;
import pl.fireacademy.domain.event.EventTypeRepository;
import pl.fireacademy.domain.training.TrainingEnrollment;
import pl.fireacademy.domain.training.TrainingEnrollmentRepository;
import pl.fireacademy.domain.training.TrainingSlot;
import pl.fireacademy.domain.training.TrainingSlotRepository;
import pl.fireacademy.domain.user.UserRole;
import pl.fireacademy.infrastructure.scheduler.TrainingSubscriptionExpiryScheduler;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Pełna ścieżka treningów cyklicznych przez realne endpointy (admin + public + user)
 * na prawdziwym Postgresie (Testcontainers). Pokrywa happy-path oraz trudne brzegi:
 * dostępność per miesiąc (bezterminowi vs miesięczni), limit, duplikat, rezygnacja
 * od kolejnego miesiąca, płatność, dopisywanie ponad limit i wypisywanie przez admina.
 */
class TrainingFlowIntegrationTest extends BaseIntegrationTest {

    @Autowired private EventTypeRepository eventTypeRepository;
    @Autowired private TrainingSlotRepository trainingSlotRepository;
    @Autowired private TrainingEnrollmentRepository trainingEnrollmentRepository;
    @Autowired private TrainingSubscriptionExpiryScheduler expiryScheduler;

    /** Mock, by weryfikować wysyłkę maili bez realnego SMTP (i bez @Async po stronie mocka). */
    @MockitoBean private TrainingMailService trainingMail;

    private static final String USER_EMAIL = "testuser@fireacademy.test";
    private static final String CURRENT = YearMonth.now().toString();
    private static final String NEXT = YearMonth.now().plusMonths(1).toString();
    private static final int DAY = 1; // poniedziałek

    private EventType seedType() {
        EventType et = new EventType(EventCategory.TRAINING, "Trening personalny");
        et.setActive(true);
        et.setDisplayOrder(0);
        return eventTypeRepository.save(et);
    }

    private TrainingSlot seedSlot(int maxParticipants) {
        TrainingSlot slot = new TrainingSlot(seedType(), DAY, LocalTime.of(8, 0), maxParticipants);
        slot.setPrice(BigDecimal.valueOf(90));
        slot.setActive(true);
        return trainingSlotRepository.save(slot);
    }

    private void enroll(String token, UUID slotId, String body) throws Exception {
        mockMvc.perform(post("/api/user/training-slots/" + slotId + "/enroll")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated());
    }

    private int availableSpots(String month, UUID slotId) throws Exception {
        var json = mockMvc.perform(get("/api/public/training-slots").param("month", month))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        // jeden slot na test (cleanup czyści między testami) — bierzemy pierwszy
        return ((Number) com.jayway.jsonpath.JsonPath.read(json, "$[0].availableSpots")).intValue();
    }

    private int sessionsInCurrentMonth() {
        YearMonth ym = YearMonth.now();
        int fromDay = LocalDate.now().getDayOfMonth(); // pozostałe od dziś (jak w serwisie)
        int count = 0;
        for (int d = fromDay; d <= ym.lengthOfMonth(); d++) {
            if (LocalDate.of(ym.getYear(), ym.getMonthValue(), d).getDayOfWeek().getValue() == DAY) count++;
        }
        return count;
    }

    @Test
    void shouldCreateSlotViaAdminEndpoint() throws Exception {
        EventType et = seedType();
        mockMvc.perform(post("/api/admin/training-slots")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"eventTypeId":"%s","dayOfWeek":1,"startTime":"08:00","maxParticipants":6,"price":90}
                    """.formatted(et.getId())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.eventTypeName").value("Trening personalny"))
            .andExpect(jsonPath("$.maxParticipants").value(6))
            .andExpect(jsonPath("$.enrolledThisMonth").value(0));
    }

    @Test
    void shouldCreateMultipleSlotsViaBatch() throws Exception {
        EventType et = seedType();
        mockMvc.perform(post("/api/admin/training-slots/batch")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"eventTypeId":"%s","slots":[
                      {"dayOfWeek":1,"startTime":"08:00","maxParticipants":6,"price":90},
                      {"dayOfWeek":3,"startTime":"18:00","endTime":"19:30","maxParticipants":8,"price":100}
                    ]}
                    """.formatted(et.getId())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/public/training-slots").param("month", CURRENT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void shouldExposeSlotWithFullAvailability() throws Exception {
        TrainingSlot slot = seedSlot(8);
        mockMvc.perform(get("/api/public/training-slots").param("month", CURRENT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(slot.getId().toString()))
            .andExpect(jsonPath("$[0].availableSpots").value(8))
            .andExpect(jsonPath("$[0].eventTypeName").value("Trening personalny"));
    }

    @Test
    void shouldReserveSpotAcrossCurrentAndFutureMonthsForIndefinite() throws Exception {
        TrainingSlot slot = seedSlot(8);
        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + CURRENT + "\"}");

        // bezterminowy zajmuje miejsce i teraz, i w przyszłych miesiącach
        org.junit.jupiter.api.Assertions.assertEquals(7, availableSpots(CURRENT, slot.getId()));
        org.junit.jupiter.api.Assertions.assertEquals(7, availableSpots(NEXT, slot.getId()));
    }

    @Test
    void shouldReserveOnlyChosenMonthsForFixedDuration() throws Exception {
        TrainingSlot slot = seedSlot(8);
        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + CURRENT + "\",\"months\":1}");

        org.junit.jupiter.api.Assertions.assertEquals(7, availableSpots(CURRENT, slot.getId()));
        org.junit.jupiter.api.Assertions.assertEquals(8, availableSpots(NEXT, slot.getId())); // następny miesiąc wolny
    }

    @Test
    void shouldRejectEnrollmentWhenFull() throws Exception {
        TrainingSlot slot = seedSlot(1);
        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + CURRENT + "\"}");

        String secondToken = createUserAndGetToken("second@fireacademy.test", "Drugi", "Uczestnik", UserRole.USER);
        mockMvc.perform(post("/api/user/training-slots/" + slot.getId() + "/enroll")
                .header("Authorization", "Bearer " + secondToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startMonth\":\"" + CURRENT + "\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void shouldRejectDuplicateEnrollmentForSameUserAndSlot() throws Exception {
        TrainingSlot slot = seedSlot(8);
        String token = userToken();
        enroll(token, slot.getId(), "{\"startMonth\":\"" + CURRENT + "\"}");

        mockMvc.perform(post("/api/user/training-slots/" + slot.getId() + "/enroll")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startMonth\":\"" + CURRENT + "\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void shouldListReservationOnAccountWithSessionsAndAmount() throws Exception {
        TrainingSlot slot = seedSlot(8);
        String token = userToken();
        enroll(token, slot.getId(), "{\"startMonth\":\"" + CURRENT + "\"}");

        mockMvc.perform(get("/api/user/training-enrollments").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].eventTypeName").value("Trening personalny"))
            .andExpect(jsonPath("$[0].sessionsInBillingMonth").value(sessionsInCurrentMonth()))
            .andExpect(jsonPath("$[0].monthlyAmount").exists());
    }

    @Test
    void shouldFreeSpotFromNextMonthAfterCancel() throws Exception {
        TrainingSlot slot = seedSlot(8);
        String token = userToken();
        enroll(token, slot.getId(), "{\"startMonth\":\"" + CURRENT + "\"}");

        UUID enrollmentId = trainingEnrollmentRepository.findActiveByUser(regularUserId(), CURRENT).getFirst().getId();
        mockMvc.perform(delete("/api/user/training-enrollments/" + enrollmentId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());

        // zostaje na bieżący miesiąc, zwolnione od kolejnego
        org.junit.jupiter.api.Assertions.assertEquals(7, availableSpots(CURRENT, slot.getId()));
        org.junit.jupiter.api.Assertions.assertEquals(8, availableSpots(NEXT, slot.getId()));
    }

    @Test
    void shouldReflectPaymentStatusInRoster() throws Exception {
        TrainingSlot slot = seedSlot(8);
        String token = userToken();
        enroll(token, slot.getId(), "{\"startMonth\":\"" + CURRENT + "\"}");
        UUID enrollmentId = trainingEnrollmentRepository.findActiveByUser(regularUserId(), CURRENT).getFirst().getId();
        String admin = adminToken();

        mockMvc.perform(put("/api/admin/training-enrollments/" + enrollmentId + "/payment")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"month\":\"" + CURRENT + "\",\"paid\":true}"))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/training-slots/" + slot.getId() + "/enrollments")
                .param("month", CURRENT)
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].firstName").value("User"))
            .andExpect(jsonPath("$[0].paid").value(true));
    }

    @Test
    void shouldAllowAdminToAddParticipantOverCapacity() throws Exception {
        TrainingSlot slot = seedSlot(1);
        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + CURRENT + "\"}"); // slot pełny (max 1)

        createUserAndGetToken("extra@fireacademy.test", "Ekstra", "Osoba", UserRole.USER);
        UUID extraId = userRepository.findByEmail("extra@fireacademy.test").orElseThrow().getId();

        mockMvc.perform(post("/api/admin/training-slots/" + slot.getId() + "/enrollments")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + extraId + "\",\"startMonth\":\"" + CURRENT + "\"}"))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/admin/training-slots/" + slot.getId() + "/enrollments")
                .param("month", CURRENT)
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2)); // ponad limit (max 1)
    }

    @Test
    void shouldRemoveParticipantImmediatelyByAdmin() throws Exception {
        TrainingSlot slot = seedSlot(8);
        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + CURRENT + "\"}");
        UUID enrollmentId = trainingEnrollmentRepository.findActiveByUser(regularUserId(), CURRENT).getFirst().getId();

        mockMvc.perform(delete("/api/admin/training-enrollments/" + enrollmentId)
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isNoContent());

        org.junit.jupiter.api.Assertions.assertEquals(8, availableSpots(CURRENT, slot.getId())); // miejsce natychmiast wolne
    }

    // ── Wiring maili (mock TrainingMailService) ─────────────────────────────

    @Test
    void shouldSendEnrollmentAndAdminEmailsOnEnroll() throws Exception {
        TrainingSlot slot = seedSlot(8);
        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + CURRENT + "\"}");
        verify(trainingMail).sendEnrollmentConfirmation(eq(USER_EMAIL), anyString(), any(), any(), any(), any(), anyInt(), any());
        verify(trainingMail).sendAdminEnrollmentNotification(eq(true), anyString(), eq(USER_EMAIL), any(), anyString(), anyLong(), anyInt());
    }

    @Test
    void shouldSendCancellationEmailsOnCancel() throws Exception {
        TrainingSlot slot = seedSlot(8);
        String token = userToken();
        enroll(token, slot.getId(), "{\"startMonth\":\"" + CURRENT + "\"}");
        UUID enrollmentId = trainingEnrollmentRepository.findActiveByUser(regularUserId(), CURRENT).getFirst().getId();
        mockMvc.perform(delete("/api/user/training-enrollments/" + enrollmentId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());
        verify(trainingMail).sendCancellationConfirmation(eq(USER_EMAIL), anyString(), any(), any());
        verify(trainingMail).sendAdminEnrollmentNotification(eq(false), anyString(), eq(USER_EMAIL), any(), anyString(), anyLong(), anyInt());
    }

    @Test
    void shouldSendAdminAddedEmailWhenAdminEnrolls() throws Exception {
        TrainingSlot slot = seedSlot(8);
        createUserAndGetToken("extra@fireacademy.test", "Ekstra", "Osoba", UserRole.USER);
        UUID extraId = userRepository.findByEmail("extra@fireacademy.test").orElseThrow().getId();
        mockMvc.perform(post("/api/admin/training-slots/" + slot.getId() + "/enrollments")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + extraId + "\",\"startMonth\":\"" + CURRENT + "\"}"))
            .andExpect(status().isCreated());
        verify(trainingMail).sendAdminAddedConfirmation(eq("extra@fireacademy.test"), anyString(), any(), any(), any(), any(), anyInt(), any());
    }

    @Test
    void shouldSendAdminRemovedEmailWhenAdminRemoves() throws Exception {
        TrainingSlot slot = seedSlot(8);
        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + CURRENT + "\"}");
        UUID enrollmentId = trainingEnrollmentRepository.findActiveByUser(regularUserId(), CURRENT).getFirst().getId();
        mockMvc.perform(delete("/api/admin/training-enrollments/" + enrollmentId)
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isNoContent());
        verify(trainingMail).sendAdminRemoved(eq(USER_EMAIL), anyString(), any());
    }

    @Test
    void shouldSendModificationEmailWhenSlotUpdated() throws Exception {
        TrainingSlot slot = seedSlot(8);
        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + CURRENT + "\"}");
        String etId = slot.getEventType().getId().toString();
        mockMvc.perform(put("/api/admin/training-slots/" + slot.getId())
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventTypeId\":\"" + etId + "\",\"dayOfWeek\":1,\"startTime\":\"09:00\",\"maxParticipants\":8}"))
            .andExpect(status().isOk());
        verify(trainingMail).sendSlotModification(eq(USER_EMAIL), anyString(), any(), any());
    }

    @Test
    void shouldHideSoftDeletedSlotFromUserAccount() throws Exception {
        TrainingSlot slot = seedSlot(8);
        String token = userToken();
        enroll(token, slot.getId(), "{\"startMonth\":\"" + CURRENT + "\"}");
        mockMvc.perform(get("/api/user/training-enrollments").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(delete("/api/admin/training-slots/" + slot.getId())
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isNoContent());

        // po soft-delete slot znika z „Moje rezerwacje"
        mockMvc.perform(get("/api/user/training-enrollments").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldReturn400ForMalformedMonthParam() throws Exception {
        mockMvc.perform(get("/api/public/training-slots").param("month", "abc"))
            .andExpect(status().isBadRequest());
    }

    /** Najbliższe wystąpienie dnia slotu (poniedziałek) od dziś — data realnych zajęć. */
    private LocalDate nextSlotDate() {
        LocalDate d = LocalDate.now();
        while (d.getDayOfWeek().getValue() != DAY) d = d.plusDays(1);
        return d;
    }

    @Test
    void shouldSoftDeleteSlotHideFromPublicAndKeepArchive() throws Exception {
        TrainingSlot slot = seedSlot(8);
        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + CURRENT + "\"}");

        mockMvc.perform(delete("/api/admin/training-slots/" + slot.getId())
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isNoContent());

        // zniknął z katalogu publicznego i z listy admina
        mockMvc.perform(get("/api/public/training-slots").param("month", CURRENT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/admin/training-slots").param("month", CURRENT)
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));

        // ale jest w archiwum z danymi kontaktowymi uczestnika
        mockMvc.perform(get("/api/admin/training-slots/deleted")
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].participants.length()").value(1))
            .andExpect(jsonPath("$[0].participants[0].firstName").value("User"))
            .andExpect(jsonPath("$[0].participants[0].email").exists());

        verify(trainingMail).sendSlotDeletion(eq(USER_EMAIL), anyString(), any());
    }

    @Test
    void shouldCancelSingleSessionExposeInPublicThenRestore() throws Exception {
        TrainingSlot slot = seedSlot(8);
        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + CURRENT + "\"}");
        LocalDate date = nextSlotDate();
        String month = YearMonth.from(date).toString();
        String admin = adminToken();

        mockMvc.perform(post("/api/admin/training-slots/" + slot.getId() + "/cancel-session")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionDate\":\"" + date + "\"}"))
            .andExpect(status().isCreated());

        verify(trainingMail).sendSessionCancelled(eq(USER_EMAIL), anyString(), any(), eq(date));

        mockMvc.perform(get("/api/public/training-slots").param("month", month))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].cancelledDates[0]").value(date.toString()));

        mockMvc.perform(delete("/api/admin/training-slots/" + slot.getId() + "/cancel-session")
                .param("date", date.toString())
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/public/training-slots").param("month", month))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].cancelledDates.length()").value(0));
    }

    @Test
    void shouldRejectCancelSessionOnWrongWeekday() throws Exception {
        TrainingSlot slot = seedSlot(8); // poniedziałek
        LocalDate tuesday = LocalDate.now();
        while (tuesday.getDayOfWeek().getValue() != 2) tuesday = tuesday.plusDays(1);

        mockMvc.perform(post("/api/admin/training-slots/" + slot.getId() + "/cancel-session")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionDate\":\"" + tuesday + "\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldHideSlotFromPublicWhenDeactivatedFromToday() throws Exception {
        TrainingSlot slot = seedSlot(8);
        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + CURRENT + "\"}");
        String admin = adminToken();

        mockMvc.perform(post("/api/admin/training-slots/" + slot.getId() + "/deactivate")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"from\":\"" + LocalDate.now() + "\"}"))
            .andExpect(status().isOk());

        verify(trainingMail).sendSlotDeactivation(eq(USER_EMAIL), anyString(), any(), eq(LocalDate.now()));

        mockMvc.perform(get("/api/public/training-slots").param("month", CURRENT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));

        // reaktywacja przywraca slot do katalogu
        mockMvc.perform(post("/api/admin/training-slots/" + slot.getId() + "/reactivate")
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/public/training-slots").param("month", CURRENT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void shouldKeepSlotVisibleWhenDeactivationIsInFuture() throws Exception {
        TrainingSlot slot = seedSlot(8);
        mockMvc.perform(post("/api/admin/training-slots/" + slot.getId() + "/deactivate")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"from\":\"" + LocalDate.now().plusMonths(1) + "\"}"))
            .andExpect(status().isOk());

        // przyszła data dezaktywacji → slot nadal w katalogu (odbywa się do tej daty)
        mockMvc.perform(get("/api/public/training-slots").param("month", CURRENT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void shouldRejectDeactivationWithPastDate() throws Exception {
        TrainingSlot slot = seedSlot(8);
        mockMvc.perform(post("/api/admin/training-slots/" + slot.getId() + "/deactivate")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"from\":\"" + LocalDate.now().minusDays(1) + "\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldFlagExpiredSubscriptionViaScheduler() {
        TrainingSlot slot = seedSlot(8);
        userToken(); // utwórz standardowego usera
        var user = userRepository.findById(regularUserId()).orElseThrow();
        YearMonth past = YearMonth.now().minusMonths(2);
        TrainingEnrollment te = trainingEnrollmentRepository.save(new TrainingEnrollment(slot, user, past, past));
        org.junit.jupiter.api.Assertions.assertFalse(te.isExpiryNotified());

        expiryScheduler.notifyExpiredSubscriptions();

        var reloaded = trainingEnrollmentRepository.findById(te.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(reloaded.isExpiryNotified());
        verify(trainingMail).sendSubscriptionExpired(eq(USER_EMAIL), anyString(), any());
    }

    @Test
    void shouldSearchRegisteredUsersByName() throws Exception {
        createUserAndGetToken("maria@fireacademy.test", "Maria", "Kowalska", UserRole.USER);

        mockMvc.perform(get("/api/admin/users/search").param("query", "Maria")
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.firstName == 'Maria')]").exists());
    }
}
