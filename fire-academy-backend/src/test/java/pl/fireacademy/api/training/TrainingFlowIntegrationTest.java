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

import org.mockito.ArgumentCaptor;
import com.jayway.jsonpath.JsonPath;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full cyclical-training flow through the real endpoints (admin + public + user)
 * on a real Postgres (Testcontainers). Covers the happy path and tricky edges:
 * per-month availability (indefinite vs monthly), capacity limit, duplicate, cancellation
 * from the next month, payment, adding beyond the limit, and removal by the admin.
 */
class TrainingFlowIntegrationTest extends BaseIntegrationTest {

    @Autowired private EventTypeRepository eventTypeRepository;
    @Autowired private pl.fireacademy.domain.instructor.InstructorRepository instructorRepository;
    @Autowired private TrainingSlotRepository trainingSlotRepository;
    @Autowired private TrainingEnrollmentRepository trainingEnrollmentRepository;
    @Autowired private pl.fireacademy.domain.training.TrainingPaymentRepository trainingPaymentRepository;
    @Autowired private TrainingSubscriptionExpiryScheduler expiryScheduler;

    /** Mock to verify email sending without real SMTP (and without @Async on the mock side). */
    @MockitoBean private TrainingMailService trainingMail;

    private static final String USER_EMAIL = "testuser@fireacademy.test";
    private static final String CURRENT = YearMonth.now().toString();
    private static final String NEXT = YearMonth.now().plusMonths(1).toString();
    private static final int DAY = 1; // Monday

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

    /** A priced (90) active slot on the given weekday/hour for an instructor — for the instructor-day tests. */
    private TrainingSlot instructorSlot(pl.fireacademy.domain.instructor.Instructor instr, EventType type, int weekday, int hour) {
        TrainingSlot slot = new TrainingSlot(type, weekday, LocalTime.of(hour, 0), 8);
        slot.setInstructor(instr);
        slot.setPrice(BigDecimal.valueOf(90));
        slot.setActive(true);
        return trainingSlotRepository.save(slot);
    }

    private String cancelInstructorDay(UUID instructorId, LocalDate date, String admin) throws Exception {
        return mockMvc.perform(post("/api/admin/training-slots/cancel-instructor-day")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"instructorId\":\"" + instructorId + "\",\"date\":\"" + date + "\"}"))
            .andReturn().getResponse().getContentAsString();
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
        // one slot per test (cleanup clears between tests) — take the first
        return ((Number) com.jayway.jsonpath.JsonPath.read(json, "$[0].availableSpots")).intValue();
    }

    private int sessionsInCurrentMonth() {
        YearMonth ym = YearMonth.now();
        int fromDay = LocalDate.now().getDayOfMonth(); // remaining ones from today (as in the service)
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
            // Real-time availability must not be browser-cached, or a fresh enrollment wouldn't reduce the spots.
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
            .andExpect(jsonPath("$[0].id").value(slot.getId().toString()))
            .andExpect(jsonPath("$[0].availableSpots").value(8))
            .andExpect(jsonPath("$[0].eventTypeName").value("Trening personalny"));
    }

    @Test
    void shouldReserveSpotAcrossCurrentAndFutureMonthsForIndefinite() throws Exception {
        TrainingSlot slot = seedSlot(8);
        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + CURRENT + "\"}");

        // an indefinite subscription occupies a spot both now and in future months
        org.junit.jupiter.api.Assertions.assertEquals(7, availableSpots(CURRENT, slot.getId()));
        org.junit.jupiter.api.Assertions.assertEquals(7, availableSpots(NEXT, slot.getId()));
    }

    @Test
    void shouldReserveOnlyChosenMonthsForFixedDuration() throws Exception {
        TrainingSlot slot = seedSlot(8);
        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + CURRENT + "\",\"months\":1}");

        org.junit.jupiter.api.Assertions.assertEquals(7, availableSpots(CURRENT, slot.getId()));
        org.junit.jupiter.api.Assertions.assertEquals(8, availableSpots(NEXT, slot.getId())); // next month free
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

        // stays for the current month, freed from the next one
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
        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + CURRENT + "\"}"); // slot full (max 1)

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
            .andExpect(jsonPath("$.length()").value(2)); // beyond the limit (max 1)
    }

    @Test
    void shouldRemoveParticipantImmediatelyByAdmin() throws Exception {
        TrainingSlot slot = seedSlot(8);
        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + CURRENT + "\"}");
        UUID enrollmentId = trainingEnrollmentRepository.findActiveByUser(regularUserId(), CURRENT).getFirst().getId();

        mockMvc.perform(delete("/api/admin/training-enrollments/" + enrollmentId)
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isNoContent());

        org.junit.jupiter.api.Assertions.assertEquals(8, availableSpots(CURRENT, slot.getId())); // spot immediately free
    }

    // ── Email wiring (mock TrainingMailService) ─────────────────────────────

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

        // after soft-delete the slot disappears from "My reservations"
        mockMvc.perform(get("/api/user/training-enrollments").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldReturn400ForMalformedMonthParam() throws Exception {
        mockMvc.perform(get("/api/public/training-slots").param("month", "abc"))
            .andExpect(status().isBadRequest());
    }

    /** Nearest occurrence of the slot's day (Monday) from today — date of a real session. */
    private LocalDate nextSlotDate() {
        LocalDate d = LocalDate.now();
        while (d.getDayOfWeek().getValue() != DAY) d = d.plusDays(1);
        return d;
    }

    /** First occurrence of the slot weekday in the given month. */
    private LocalDate firstSlotDateIn(YearMonth ym) {
        LocalDate d = ym.atDay(1);
        while (d.getDayOfWeek().getValue() != DAY) d = d.plusDays(1);
        return d;
    }

    /** Last occurrence of the slot weekday in the given month. */
    private LocalDate lastSlotDateIn(YearMonth ym) {
        LocalDate d = ym.atEndOfMonth();
        while (d.getDayOfWeek().getValue() != DAY) d = d.minusDays(1);
        return d;
    }

    /** How many times the slot weekday occurs across the whole given month. */
    private int slotDaysIn(YearMonth ym) {
        int count = 0;
        for (int d = 1; d <= ym.lengthOfMonth(); d++) {
            if (LocalDate.of(ym.getYear(), ym.getMonthValue(), d).getDayOfWeek().getValue() == DAY) count++;
        }
        return count;
    }

    @Test
    void shouldSoftDeleteSlotHideFromPublicAndKeepArchive() throws Exception {
        TrainingSlot slot = seedSlot(8);
        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + CURRENT + "\"}");

        mockMvc.perform(delete("/api/admin/training-slots/" + slot.getId())
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isNoContent());

        // disappeared from the public catalog and from the admin list
        mockMvc.perform(get("/api/public/training-slots").param("month", CURRENT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/admin/training-slots").param("month", CURRENT)
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));

        // but it is in the archive with the participant's contact data
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
        LocalDate date = nextSlotDate();
        String month = YearMonth.from(date).toString();
        String admin = adminToken();
        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + month + "\"}");
        UUID enrollmentId = trainingEnrollmentRepository.findActiveByUser(regularUserId(), month).getFirst().getId();
        markPaid(enrollmentId, month, admin);   // only paid subscribers are emailed about a cancellation

        mockMvc.perform(post("/api/admin/training-slots/" + slot.getId() + "/cancel-session")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionDate\":\"" + date + "\"}"))
            .andExpect(status().isCreated());

        verify(trainingMail).sendSessionCancelled(eq(USER_EMAIL), anyString(), any(), eq(date), any());

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
        TrainingSlot slot = seedSlot(8); // Monday
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

        // reactivation restores the slot to the catalog
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

        // future deactivation date → slot still in the catalog (takes place until that date)
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
        userToken(); // create a standard user
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

    @Test
    void shouldSetBillingStartDateAndBlockChangeOncePaid() throws Exception {
        TrainingSlot slot = seedSlot(8);
        String admin = adminToken();
        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + CURRENT + "\"}");
        UUID enrollmentId = trainingEnrollmentRepository.findActiveByUser(regularUserId(), CURRENT).getFirst().getId();

        // A discretionary start date within the start month is accepted and persisted.
        LocalDate startDate = YearMonth.now().atDay(1);
        setStart(enrollmentId, startDate.toString(), admin).andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertEquals(startDate,
                trainingEnrollmentRepository.findById(enrollmentId).orElseThrow().getBillableFrom());

        // A date outside the start month is rejected (400).
        setStart(enrollmentId, YearMonth.now().plusMonths(1).atDay(5).toString(), admin)
                .andExpect(status().isBadRequest());

        // Once the month is paid its NET is frozen → the start date can no longer be changed (409).
        markPaid(enrollmentId, CURRENT, admin);
        setStart(enrollmentId, YearMonth.now().atDay(2).toString(), admin).andExpect(status().isConflict());
    }

    @Test
    void shouldNotRefundCancelledSessionBeforeBillingStartDate() throws Exception {
        // Regression: a subscriber billed from a later start day (organizer's billableFrom override) is owed NO
        // refund for a cancelled session that falls BEFORE that start — those sessions were never part of the bill.
        TrainingSlot slot = seedSlot(8);
        String admin = adminToken();
        YearMonth month = YearMonth.now();
        LocalDate firstDay = firstSlotDateIn(month);
        LocalDate lastDay = lastSlotDateIn(month);
        org.junit.jupiter.api.Assumptions.assumeTrue(lastDay.isAfter(firstDay),
                "needs at least two slot-weekday occurrences this month");

        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + CURRENT + "\"}");
        UUID enrollmentId = trainingEnrollmentRepository.findActiveByUser(regularUserId(), CURRENT).getFirst().getId();

        // Bill from the last occurrence → only that session is paid for.
        setStart(enrollmentId, lastDay.toString(), admin).andExpect(status().isNoContent());
        markPaid(enrollmentId, CURRENT, admin);

        // Cancelling the FIRST occurrence (before the start day) must not invent a refund.
        mockMvc.perform(post("/api/admin/training-slots/" + slot.getId() + "/cancel-session")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionDate\":\"" + firstDay + "\"}"))
            .andExpect(status().isCreated());
        mockMvc.perform(get("/api/admin/training-refunds").header("Authorization", "Bearer " + admin))
            .andExpect(jsonPath("$.length()").value(0));

        // But cancelling the billed (last) session still owes a refund — the fix is precise, not a blanket off-switch.
        mockMvc.perform(post("/api/admin/training-slots/" + slot.getId() + "/cancel-session")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionDate\":\"" + lastDay + "\"}"))
            .andExpect(status().isCreated());
        mockMvc.perform(get("/api/admin/training-refunds").header("Authorization", "Bearer " + admin))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].sessionDate").value(lastDay.toString()));
    }

    private org.springframework.test.web.servlet.ResultActions setStart(UUID enrollmentId, String date, String admin) throws Exception {
        return mockMvc.perform(put("/api/admin/training-enrollments/" + enrollmentId + "/start")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startDate\":\"" + date + "\"}"));
    }

    private void markPaid(UUID enrollmentId, String month, String admin) throws Exception {
        mockMvc.perform(put("/api/admin/training-enrollments/" + enrollmentId + "/payment")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"month\":\"" + month + "\",\"paid\":true}"))
            .andExpect(status().isNoContent());
    }

    @Test
    void shouldRegisterRefundWhenCancellingPaidSessionAndSettleIt() throws Exception {
        TrainingSlot slot = seedSlot(8);
        LocalDate date = nextSlotDate();
        String month = YearMonth.from(date).toString();
        String admin = adminToken();

        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + month + "\"}");
        UUID enrollmentId = trainingEnrollmentRepository.findActiveByUser(regularUserId(), month).getFirst().getId();
        markPaid(enrollmentId, month, admin);

        // Cancelling a paid session creates a refund of one session's price (90).
        mockMvc.perform(post("/api/admin/training-slots/" + slot.getId() + "/cancel-session")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionDate\":\"" + date + "\"}"))
            .andExpect(status().isCreated());

        var refunds = mockMvc.perform(get("/api/admin/training-refunds").header("Authorization", "Bearer " + admin));
        refunds.andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].amount").value(90))
            .andExpect(jsonPath("$[0].type").value("SESSION"))
            .andExpect(jsonPath("$[0].sessionDate").value(date.toString()));

        String refundId = com.jayway.jsonpath.JsonPath.read(
                mockMvc.perform(get("/api/admin/training-refunds").header("Authorization", "Bearer " + admin))
                        .andReturn().getResponse().getContentAsString(), "$[0].id");

        // Settling it (money back) moves the refund out of the pending list into the settled history.
        mockMvc.perform(post("/api/admin/training-refunds/" + refundId + "/settle")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"settlementType\":\"REFUNDED\"}"))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/admin/training-refunds").header("Authorization", "Bearer " + admin))
            .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/admin/training-refunds").param("settled", "true")
                .header("Authorization", "Bearer " + admin))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].settlementType").value("REFUNDED"));
    }

    @Test
    void shouldRevokePendingRefundWhenSessionRestored() throws Exception {
        TrainingSlot slot = seedSlot(8);
        LocalDate date = nextSlotDate();
        String month = YearMonth.from(date).toString();
        String admin = adminToken();

        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + month + "\"}");
        UUID enrollmentId = trainingEnrollmentRepository.findActiveByUser(regularUserId(), month).getFirst().getId();
        markPaid(enrollmentId, month, admin);

        mockMvc.perform(post("/api/admin/training-slots/" + slot.getId() + "/cancel-session")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionDate\":\"" + date + "\"}"))
            .andExpect(status().isCreated());
        mockMvc.perform(delete("/api/admin/training-slots/" + slot.getId() + "/cancel-session")
                .param("date", date.toString())
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isNoContent());

        // Restoring the session drops the pending refund.
        mockMvc.perform(get("/api/admin/training-refunds").header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldRefundPaidSubscriberWhenHistoricalSessionCancelled() throws Exception {
        // A session that already happened (yesterday) but did not take place must still refund a client who paid
        // that month — the price they paid covered it. Skip only on the 1st (yesterday would be last month).
        LocalDate yesterday = LocalDate.now().minusDays(1);
        org.junit.jupiter.api.Assumptions.assumeTrue(yesterday.getMonthValue() == LocalDate.now().getMonthValue(),
                "first of month — no past session date in the current month yet");
        String admin = adminToken();
        String month = YearMonth.now().toString();

        TrainingSlot slot = new TrainingSlot(seedType(), yesterday.getDayOfWeek().getValue(), LocalTime.of(8, 0), 8);
        slot.setPrice(BigDecimal.valueOf(90));
        slot.setActive(true);
        trainingSlotRepository.save(slot);

        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + month + "\"}");
        UUID enrollmentId = trainingEnrollmentRepository.findActiveByUser(regularUserId(), month).getFirst().getId();
        // Established full-month client: billed from the 1st, so yesterday's session is genuinely part of the paid bill.
        setStart(enrollmentId, YearMonth.now().atDay(1).toString(), admin).andExpect(status().isNoContent());
        markPaid(enrollmentId, month, admin);

        mockMvc.perform(post("/api/admin/training-slots/" + slot.getId() + "/cancel-session")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionDate\":\"" + yesterday + "\"}"))
            .andExpect(status().isCreated());

        // The paid client is owed a refund for the historical session that did not happen.
        mockMvc.perform(get("/api/admin/training-refunds").header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].type").value("SESSION"))
            .andExpect(jsonPath("$[0].amount").value(90));

        // No "don't come" email for a past session — it already happened; only the refund is created.
        verify(trainingMail, org.mockito.Mockito.never())
                .sendSessionCancelled(eq(USER_EMAIL), anyString(), any(), any(), any());
    }

    @Test
    void shouldBlockRestoringSessionWhenRefundAlreadyPaidInCash() throws Exception {
        TrainingSlot slot = seedSlot(8);
        LocalDate date = nextSlotDate();
        String month = YearMonth.from(date).toString();
        String admin = adminToken();
        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + month + "\"}");
        UUID enrollmentId = trainingEnrollmentRepository.findActiveByUser(regularUserId(), month).getFirst().getId();
        markPaid(enrollmentId, month, admin);
        mockMvc.perform(post("/api/admin/training-slots/" + slot.getId() + "/cancel-session")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionDate\":\"" + date + "\"}"))
            .andExpect(status().isCreated());
        settleRefund(firstRefundId(admin), "REFUNDED", admin);   // cash handed back

        // Cash already paid out → restore is refused.
        mockMvc.perform(delete("/api/admin/training-slots/" + slot.getId() + "/cancel-session")
                .param("date", date.toString())
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isConflict());
    }

    @Test
    void shouldAllowRestoringSessionWhenRefundOnlyCreditedAndRevokeIt() throws Exception {
        TrainingSlot slot = seedSlot(8);
        LocalDate date = nextSlotDate();
        String month = YearMonth.from(date).toString();
        String admin = adminToken();
        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + month + "\"}");
        UUID enrollmentId = trainingEnrollmentRepository.findActiveByUser(regularUserId(), month).getFirst().getId();
        markPaid(enrollmentId, month, admin);
        mockMvc.perform(post("/api/admin/training-slots/" + slot.getId() + "/cancel-session")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionDate\":\"" + date + "\"}"))
            .andExpect(status().isCreated());
        settleRefund(firstRefundId(admin), "CREDITED", admin);   // moved to next month → reversible
        assertAmount(rosterJson(slot.getId(), month, admin), "$[0].creditBalance", 90);

        // Restore is allowed and the credited surplus is revoked with it.
        mockMvc.perform(delete("/api/admin/training-slots/" + slot.getId() + "/cancel-session")
                .param("date", date.toString())
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isNoContent());
        assertAmount(rosterJson(slot.getId(), month, admin), "$[0].creditBalance", 0);
    }

    @Test
    void shouldListCancelledSessionOverviewWithAffectedPaidParticipant() throws Exception {
        TrainingSlot slot = seedSlot(8);
        LocalDate date = nextSlotDate();
        String month = YearMonth.from(date).toString();
        String admin = adminToken();

        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + month + "\"}");
        UUID enrollmentId = trainingEnrollmentRepository.findActiveByUser(regularUserId(), month).getFirst().getId();
        markPaid(enrollmentId, month, admin);

        mockMvc.perform(post("/api/admin/training-slots/" + slot.getId() + "/cancel-session")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionDate\":\"" + date + "\"}"))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/admin/training-slots/cancelled-sessions/overview")
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].sessionDate").value(date.toString()))
            .andExpect(jsonPath("$[0].future").value(true))
            .andExpect(jsonPath("$[0].participants.length()").value(1))
            .andExpect(jsonPath("$[0].participants[0].email").value(USER_EMAIL))
            .andExpect(jsonPath("$[0].participants[0].paid").value(true))
            .andExpect(jsonPath("$[0].participants[0].owedRefund").value(true));
    }

    @Test
    void shouldClearOwedBadgeOnceRefundIsSettledButKeepSessionCancelled() throws Exception {
        TrainingSlot slot = seedSlot(8);
        LocalDate date = nextSlotDate();
        String month = YearMonth.from(date).toString();
        String admin = adminToken();

        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + month + "\"}");
        UUID enrollmentId = trainingEnrollmentRepository.findActiveByUser(regularUserId(), month).getFirst().getId();
        markPaid(enrollmentId, month, admin);

        mockMvc.perform(post("/api/admin/training-slots/" + slot.getId() + "/cancel-session")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionDate\":\"" + date + "\"}"))
            .andExpect(status().isCreated());

        // The session was made up in another group → resolve the refund as MADE_UP (no cash, no credit).
        settleRefund(firstRefundId(admin), "MADE_UP", admin);

        // The cancelled session still shows in the overview, but the "do zwrotu" badge is gone.
        mockMvc.perform(get("/api/admin/training-slots/cancelled-sessions/overview")
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].sessionDate").value(date.toString()))
            .andExpect(jsonPath("$[0].participants[0].paid").value(true))
            .andExpect(jsonPath("$[0].participants[0].owedRefund").value(false));

        // And the client owes/gets nothing back — the pending refund is cleared.
        String mine = mockMvc.perform(get("/api/user/training-enrollments").header("Authorization", "Bearer " + userToken()))
                .andReturn().getResponse().getContentAsString();
        assertAmount(mine, "$[0].pendingRefundAmount", 0);
    }

    @Test
    void shouldRejectRestoringPastSession() throws Exception {
        TrainingSlot slot = seedSlot(8);
        String admin = adminToken();
        // A past occurrence of the slot's weekday — restore must be refused regardless of any cancellation.
        LocalDate past = nextSlotDate().minusWeeks(4);

        mockMvc.perform(delete("/api/admin/training-slots/" + slot.getId() + "/cancel-session")
                .param("date", past.toString())
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isBadRequest());
    }

    // ── Surplus credit: a refund settled as CREDITED discounts future bills ───

    private String settleRefund(String refundId, String type, String admin) throws Exception {
        mockMvc.perform(post("/api/admin/training-refunds/" + refundId + "/settle")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"settlementType\":\"" + type + "\"}"))
            .andExpect(status().isNoContent());
        return refundId;
    }

    private String firstRefundId(String admin) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(
                mockMvc.perform(get("/api/admin/training-refunds").header("Authorization", "Bearer " + admin))
                        .andReturn().getResponse().getContentAsString(), "$[0].id");
    }

    /** Reads a numeric JSON field as BigDecimal and compares ignoring scale (90 == 90.00). */
    private void assertAmount(String json, String path, long expected) {
        Object v = com.jayway.jsonpath.JsonPath.read(json, path);
        assertEquals(0, new BigDecimal(v.toString()).compareTo(BigDecimal.valueOf(expected)),
                () -> path + " expected " + expected + " but was " + v);
    }

    private String rosterJson(UUID slotId, String month, String admin) throws Exception {
        return mockMvc.perform(get("/api/admin/training-slots/" + slotId + "/enrollments").param("month", month)
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void shouldCreateSurplusFromCreditedSingleSessionRefund() throws Exception {
        TrainingSlot slot = seedSlot(8);
        String admin = adminToken();
        LocalDate date = nextSlotDate();
        String month = YearMonth.from(date).toString();

        // A single cancelled session in a paid month → refund → settled as CREDITED → surplus on the roster.
        // (Consumption on payment, roll-forward and the un-credit guard are covered by the unit tests.)
        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + month + "\"}");
        UUID enrollmentId = trainingEnrollmentRepository.findActiveByUser(regularUserId(), month).getFirst().getId();
        markPaid(enrollmentId, month, admin);
        mockMvc.perform(post("/api/admin/training-slots/" + slot.getId() + "/cancel-session")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionDate\":\"" + date + "\"}"))
            .andExpect(status().isCreated());
        settleRefund(firstRefundId(admin), "CREDITED", admin);

        assertAmount(rosterJson(slot.getId(), month, admin), "$[0].creditBalance", 90);
    }

    private void setPaid(UUID enrollmentId, String month, boolean paid, String admin, org.springframework.test.web.servlet.ResultMatcher expect) throws Exception {
        mockMvc.perform(put("/api/admin/training-enrollments/" + enrollmentId + "/payment")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"month\":\"" + month + "\",\"paid\":" + paid + "}"))
            .andExpect(expect);
    }

    @Test
    void shouldRejectPayingAFutureMonthAheadOfTime() throws Exception {
        TrainingSlot slot = seedSlot(8);
        String admin = adminToken();
        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + CURRENT + "\"}");
        UUID enrollmentId = trainingEnrollmentRepository.findActiveByUser(regularUserId(), CURRENT).getFirst().getId();

        // A future month cannot be paid ahead of time (too early and/or out of order while CURRENT is open).
        setPaid(enrollmentId, NEXT, true, admin, status().isConflict());
        // The current month is always payable, and can be reverted.
        setPaid(enrollmentId, CURRENT, true, admin, status().isNoContent());
        setPaid(enrollmentId, CURRENT, false, admin, status().isNoContent());
    }

    @Test
    void shouldPayAndRevertAWholeMonthForOneUserAcrossAllTrainings() throws Exception {
        TrainingSlot s1 = seedSlot(8);
        TrainingSlot s2 = seedSlot(8);
        String admin = adminToken();
        String user = userToken();
        enroll(user, s1.getId(), "{\"startMonth\":\"" + CURRENT + "\"}");
        enroll(user, s2.getId(), "{\"startMonth\":\"" + CURRENT + "\"}");

        // The monthly roster lists the person once with both trainings and the combined amount, not yet paid.
        String body = mockMvc.perform(get("/api/admin/training-payments").param("month", CURRENT)
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].trainings.length()").value(2))
            .andExpect(jsonPath("$[0].allPaid").value(false))
            .andReturn().getResponse().getContentAsString();
        assertAmount(body, "$[0].totalAmount", 2L * 90 * sessionsInCurrentMonth());

        // One click settles the whole month across both trainings.
        mockMvc.perform(post("/api/admin/training-payments/pay-user/" + regularUserId())
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"month\":\"" + CURRENT + "\",\"paid\":true}"))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/admin/training-payments").param("month", CURRENT)
                .header("Authorization", "Bearer " + admin))
            .andExpect(jsonPath("$[0].allPaid").value(true));

        // And reverting the month un-pays every training again.
        mockMvc.perform(post("/api/admin/training-payments/pay-user/" + regularUserId())
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"month\":\"" + CURRENT + "\",\"paid\":false}"))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/admin/training-payments").param("month", CURRENT)
                .header("Authorization", "Bearer " + admin))
            .andExpect(jsonPath("$[0].allPaid").value(false));
    }

    @Test
    void shouldPreserveIndividuallyPaidTrainingWhenPayingWholeMonth() throws Exception {
        TrainingSlot s1 = seedSlot(8);
        TrainingSlot s2 = seedSlot(8);
        String admin = adminToken();
        String user = userToken();
        enroll(user, s1.getId(), "{\"startMonth\":\"" + CURRENT + "\"}");
        enroll(user, s2.getId(), "{\"startMonth\":\"" + CURRENT + "\"}");

        UUID e1 = trainingEnrollmentRepository.findActiveByUser(regularUserId(), CURRENT).stream()
                .filter(te -> te.getSlot().getId().equals(s1.getId())).findFirst().orElseThrow().getId();

        // Mark only s1 paid individually first (e.g. via the per-slot roster toggle).
        markPaid(e1, CURRENT, admin);
        var before = trainingPaymentRepository.findByEnrollmentIdAndYearMonth(e1, CURRENT).orElseThrow();

        // Now pay the whole month for the user (both trainings at once, via the "monthly payments" tab).
        mockMvc.perform(post("/api/admin/training-payments/pay-user/" + regularUserId())
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"month\":\"" + CURRENT + "\",\"paid\":true}"))
            .andExpect(status().isNoContent());

        // s1's original payment row must be untouched (same id/timestamp) — not deleted+reinserted.
        var after = trainingPaymentRepository.findByEnrollmentIdAndYearMonth(e1, CURRENT).orElseThrow();
        assertEquals(before.getId(), after.getId());
        assertEquals(before.getCreatedAt(), after.getCreatedAt());

        mockMvc.perform(get("/api/admin/training-payments").param("month", CURRENT)
                .header("Authorization", "Bearer " + admin))
            .andExpect(jsonPath("$[0].allPaid").value(true));
    }

    @Test
    void shouldKeepIndividuallyPaidTrainingWhenRevertingWholeMonth() throws Exception {
        TrainingSlot s1 = seedSlot(8);
        TrainingSlot s2 = seedSlot(8);
        String admin = adminToken();
        String user = userToken();
        enroll(user, s1.getId(), "{\"startMonth\":\"" + CURRENT + "\"}");
        enroll(user, s2.getId(), "{\"startMonth\":\"" + CURRENT + "\"}");

        UUID e1 = trainingEnrollmentRepository.findActiveByUser(regularUserId(), CURRENT).stream()
                .filter(te -> te.getSlot().getId().equals(s1.getId())).findFirst().orElseThrow().getId();
        UUID e2 = trainingEnrollmentRepository.findActiveByUser(regularUserId(), CURRENT).stream()
                .filter(te -> te.getSlot().getId().equals(s2.getId())).findFirst().orElseThrow().getId();

        // The admin marks ONLY s1 as paid individually (per-slot roster toggle).
        markPaid(e1, CURRENT, admin);

        // Then pays the whole month for the person (adds s2), then reverts the whole month.
        payUserMonth(CURRENT, true, admin);
        payUserMonth(CURRENT, false, admin);

        // The individually-paid s1 must remain paid; only the batch-added s2 is reverted.
        org.junit.jupiter.api.Assertions.assertTrue(
                trainingPaymentRepository.findByEnrollmentIdAndYearMonth(e1, CURRENT).isPresent(),
                "individually paid training should survive the whole-month revert");
        org.junit.jupiter.api.Assertions.assertTrue(
                trainingPaymentRepository.findByEnrollmentIdAndYearMonth(e2, CURRENT).isEmpty(),
                "batch-added training should be reverted");
    }

    private void payUserMonth(String month, boolean paid, String admin) throws Exception {
        mockMvc.perform(post("/api/admin/training-payments/pay-user/" + regularUserId())
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"month\":\"" + month + "\",\"paid\":" + paid + "}"))
            .andExpect(status().isNoContent());
    }

    @Test
    void shouldNotDiscountBillWhenRefundSettledAsCash() throws Exception {
        TrainingSlot slot = seedSlot(8);
        String admin = adminToken();
        LocalDate date = nextSlotDate();
        String month = YearMonth.from(date).toString();

        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + month + "\"}");
        UUID enrollmentId = trainingEnrollmentRepository.findActiveByUser(regularUserId(), month).getFirst().getId();
        markPaid(enrollmentId, month, admin);
        mockMvc.perform(post("/api/admin/training-slots/" + slot.getId() + "/cancel-session")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionDate\":\"" + date + "\"}"))
            .andExpect(status().isCreated());
        settleRefund(firstRefundId(admin), "REFUNDED", admin);

        // Cash refund → no surplus balance.
        assertAmount(rosterJson(slot.getId(), month, admin), "$[0].creditBalance", 0);
    }

    @Test
    void shouldExposePendingRefundToTheClientUntilResolved() throws Exception {
        TrainingSlot slot = seedSlot(8);
        String admin = adminToken();
        LocalDate date = nextSlotDate();
        String month = YearMonth.from(date).toString();

        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + month + "\"}");
        UUID enrollmentId = trainingEnrollmentRepository.findActiveByUser(regularUserId(), month).getFirst().getId();
        markPaid(enrollmentId, month, admin);
        mockMvc.perform(post("/api/admin/training-slots/" + slot.getId() + "/cancel-session")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionDate\":\"" + date + "\"}"))
            .andExpect(status().isCreated());

        // Before the organizer resolves it, the client sees the amount owed for the cancelled paid session.
        String before = mockMvc.perform(get("/api/user/training-enrollments")
                .header("Authorization", "Bearer " + userToken()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertAmount(before, "$[0].pendingRefundAmount", 90);

        // Once resolved (here: credited toward a future month), it is no longer "pending to discuss" — instead it
        // shows as a surplus the client can see year-round (not only inside the 7-day estimate window).
        settleRefund(firstRefundId(admin), "CREDITED", admin);
        String after = mockMvc.perform(get("/api/user/training-enrollments")
                .header("Authorization", "Bearer " + userToken()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertAmount(after, "$[0].pendingRefundAmount", 0);
        assertAmount(after, "$[0].upcomingCreditBalance", 90);
    }

    @Test
    void shouldSettleAllPendingRefundsForAUserAtOnce() throws Exception {
        TrainingSlot slot = seedSlot(8);
        String admin = adminToken();
        LocalDate d1 = nextSlotDate();
        LocalDate d2 = d1.plusWeeks(1);
        String month = YearMonth.from(d1).toString();
        // Both cancelled sessions must fall in the same (paid) month for two refunds to arise.
        org.junit.jupiter.api.Assumptions.assumeTrue(YearMonth.from(d2).toString().equals(month));

        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + month + "\"}");
        UUID enrollmentId = trainingEnrollmentRepository.findActiveByUser(regularUserId(), month).getFirst().getId();
        markPaid(enrollmentId, month, admin);
        for (LocalDate d : new LocalDate[]{d1, d2}) {
            mockMvc.perform(post("/api/admin/training-slots/" + slot.getId() + "/cancel-session")
                    .header("Authorization", "Bearer " + admin)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"sessionDate\":\"" + d + "\"}"))
                .andExpect(status().isCreated());
        }
        mockMvc.perform(get("/api/admin/training-refunds").header("Authorization", "Bearer " + admin))
            .andExpect(jsonPath("$.length()").value(2));

        // One bulk decision resolves both.
        mockMvc.perform(post("/api/admin/training-refunds/settle-user/" + regularUserId())
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"settlementType\":\"CREDITED\"}"))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/admin/training-refunds").header("Authorization", "Bearer " + admin))
            .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/admin/training-refunds").param("settled", "true")
                .header("Authorization", "Bearer " + admin))
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].settlementType").value("CREDITED"));
    }

    @Test
    void shouldRefundPaidSubscriberOnDeactivationAndRevokeOnReactivation() throws Exception {
        TrainingSlot slot = seedSlot(8);
        String admin = adminToken();
        int remaining = sessionsInCurrentMonth();
        org.junit.jupiter.api.Assumptions.assumeTrue(remaining > 0); // need at least one upcoming session this month
        LocalDate from = nextSlotDate();                             // first upcoming session — deactivate the rest

        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + CURRENT + "\"}");
        UUID enrollmentId = trainingEnrollmentRepository.findActiveByUser(regularUserId(), CURRENT).getFirst().getId();
        markPaid(enrollmentId, CURRENT, admin);

        mockMvc.perform(post("/api/admin/training-slots/" + slot.getId() + "/deactivate")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"from\":\"" + from + "\"}"))
            .andExpect(status().isOk());

        // Every no-longer-happening paid session becomes a refund; the client's bill drops to zero and
        // shows the amount owed.
        mockMvc.perform(get("/api/admin/training-refunds").header("Authorization", "Bearer " + admin))
            .andExpect(jsonPath("$.length()").value(remaining));
        String body = mockMvc.perform(get("/api/user/training-enrollments")
                .header("Authorization", "Bearer " + userToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].billingMonthPaid").value(true))
            .andReturn().getResponse().getContentAsString();
        assertAmount(body, "$[0].monthlyAmount", 0);
        assertAmount(body, "$[0].pendingRefundAmount", 90L * remaining);
        // The account still shows the real amount the client paid (not the recomputed 0).
        assertAmount(body, "$[0].billingMonthPaidAmount", 90L * remaining);

        // Reactivating brings the sessions back and revokes the pending refunds.
        mockMvc.perform(post("/api/admin/training-slots/" + slot.getId() + "/reactivate")
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/training-refunds").header("Authorization", "Bearer " + admin))
            .andExpect(jsonPath("$.length()").value(0));
        String after = mockMvc.perform(get("/api/user/training-enrollments")
                .header("Authorization", "Bearer " + userToken()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertAmount(after, "$[0].monthlyAmount", 90L * remaining);
        assertAmount(after, "$[0].pendingRefundAmount", 0);
    }



    @Test
    void shouldAddDayOffExposeInPublicReduceSessionsAndRefundPaid() throws Exception {
        TrainingSlot slot = seedSlot(8);
        LocalDate date = nextSlotDate();
        String month = YearMonth.from(date).toString();
        String admin = adminToken();

        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + month + "\"}");
        UUID enrollmentId = trainingEnrollmentRepository.findActiveByUser(regularUserId(), month).getFirst().getId();
        markPaid(enrollmentId, month, admin);

        mockMvc.perform(post("/api/admin/training-holidays")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"" + date + "\",\"label\":\"Test wolne\"}"))
            .andExpect(status().isCreated());

        // Public schedule exposes the day off…
        mockMvc.perform(get("/api/public/training-holidays").param("month", month))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].date").value(date.toString()))
            .andExpect(jsonPath("$[0].label").value("Test wolne"));

        // …and a refund is owed to the paid subscriber, tagged as a day off.
        mockMvc.perform(get("/api/admin/training-refunds").header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].type").value("HOLIDAY"))
            .andExpect(jsonPath("$[0].label").value("Test wolne"));

        // The paid subscriber gets ONE grouped "day off" email listing their cancelled session(s) that day.
        verify(trainingMail).sendDayOffCancellation(eq(USER_EMAIL), anyString(), eq(date), any(), any(), any());
    }

    @Test
    void shouldRevokeHolidayRefundWhenDayOffRemoved() throws Exception {
        // Clean symmetric case with no other closure stacked: adding a day off in a PAID month owes a refund,
        // removing that same day off takes it straight back off the ledger.
        TrainingSlot slot = seedSlot(8);
        LocalDate date = nextSlotDate();
        String month = YearMonth.from(date).toString();
        String admin = adminToken();

        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + month + "\"}");
        UUID enrollmentId = trainingEnrollmentRepository.findActiveByUser(regularUserId(), month).getFirst().getId();
        markPaid(enrollmentId, month, admin);

        mockMvc.perform(post("/api/admin/training-holidays")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"" + date + "\"}"))
            .andExpect(status().isCreated());
        mockMvc.perform(get("/api/admin/training-refunds").header("Authorization", "Bearer " + admin))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].type").value("HOLIDAY"));

        String holidayId = JsonPath.read(
                mockMvc.perform(get("/api/admin/training-holidays").param("month", month)
                        .header("Authorization", "Bearer " + admin))
                    .andReturn().getResponse().getContentAsString(), "$[0].id");
        mockMvc.perform(delete("/api/admin/training-holidays/" + holidayId)
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isNoContent());

        // Nothing else keeps the session closed → the refund is gone.
        mockMvc.perform(get("/api/admin/training-refunds").header("Authorization", "Bearer " + admin))
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldUpdateEstimateWithoutRefundWhenDayOffAddedAndRemovedForUnpaidFutureMonth() throws Exception {
        // An UNPAID future month never produces a refund — the bill is recomputed live. Adding a day off in
        // that month lowers the estimate by one session; removing it restores it. No ledger entry either way.
        TrainingSlot slot = seedSlot(8);
        YearMonth next = YearMonth.now().plusMonths(1);
        LocalDate date = firstSlotDateIn(next);
        String admin = adminToken();
        String token = userToken();

        // Indefinite subscription starting next month → its billing month (and estimate) is that future month.
        enroll(token, slot.getId(), "{\"startMonth\":\"" + next + "\"}");
        int fullMonth = slotDaysIn(next);
        mockMvc.perform(get("/api/user/training-enrollments").header("Authorization", "Bearer " + token))
            .andExpect(jsonPath("$[0].sessionsInBillingMonth").value(fullMonth));

        mockMvc.perform(post("/api/admin/training-holidays")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"" + date + "\"}"))
            .andExpect(status().isCreated());

        // Estimate dropped by one session, but NO refund (nothing was paid).
        mockMvc.perform(get("/api/user/training-enrollments").header("Authorization", "Bearer " + token))
            .andExpect(jsonPath("$[0].sessionsInBillingMonth").value(fullMonth - 1));
        mockMvc.perform(get("/api/admin/training-refunds").header("Authorization", "Bearer " + admin))
            .andExpect(jsonPath("$.length()").value(0));

        String holidayId = JsonPath.read(
                mockMvc.perform(get("/api/admin/training-holidays").param("month", next.toString())
                        .header("Authorization", "Bearer " + admin))
                    .andReturn().getResponse().getContentAsString(), "$[0].id");
        mockMvc.perform(delete("/api/admin/training-holidays/" + holidayId)
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isNoContent());

        // Removing the day off brings the estimate back to the full month.
        mockMvc.perform(get("/api/user/training-enrollments").header("Authorization", "Bearer " + token))
            .andExpect(jsonPath("$[0].sessionsInBillingMonth").value(fullMonth));
    }

    @Test
    void shouldNotEmailUnpaidSubscriberAboutCancelledSession() throws Exception {
        TrainingSlot slot = seedSlot(8);
        LocalDate date = nextSlotDate();
        String admin = adminToken();
        // Enrolled but NOT paid → no cancellation email; the training just gets cheaper (estimate updates).
        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + YearMonth.from(date) + "\"}");

        mockMvc.perform(post("/api/admin/training-slots/" + slot.getId() + "/cancel-session")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionDate\":\"" + date + "\"}"))
            .andExpect(status().isCreated());

        verify(trainingMail, org.mockito.Mockito.never())
                .sendSessionCancelled(eq(USER_EMAIL), anyString(), any(), any(), any());
    }

    @Test
    void shouldCancelAllInstructorSessionsOnDate() throws Exception {
        var instr = instructorRepository.save(new pl.fireacademy.domain.instructor.Instructor("Anna", "Trener"));
        EventType type = seedType();
        TrainingSlot s1 = instructorSlot(instr, type, DAY, 8);
        TrainingSlot s2 = instructorSlot(instr, type, DAY, 18);

        LocalDate date = nextSlotDate();
        String month = YearMonth.from(date).toString();
        String admin = adminToken();

        // A paid subscriber to BOTH of the instructor's sessions should get ONE grouped email, not two.
        enroll(userToken(), s1.getId(), "{\"startMonth\":\"" + month + "\"}");
        enroll(userToken(), s2.getId(), "{\"startMonth\":\"" + month + "\"}");
        for (var te : trainingEnrollmentRepository.findActiveByUser(regularUserId(), month)) {
            markPaid(te.getId(), month, admin);
        }

        assertEquals(2, (int) JsonPath.read(cancelInstructorDay(instr.getId(),date, admin), "$.cancelled"));

        // Both of the instructor's sessions show as cancelled on that date in the public schedule.
        mockMvc.perform(get("/api/public/training-slots").param("month", month))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].cancelledDates[0]").value(date.toString()))
            .andExpect(jsonPath("$[1].cancelledDates[0]").value(date.toString()));

        // ONE grouped, descriptive email: names the trainer, lists BOTH sessions, totals the refund (2 × 90).
        var sessions = ArgumentCaptor.forClass(List.class);
        var refund = ArgumentCaptor.forClass(BigDecimal.class);
        verify(trainingMail, org.mockito.Mockito.times(1)).sendInstructorDayCancellation(
                eq(USER_EMAIL), anyString(), eq("Anna Trener"), eq(date), sessions.capture(), refund.capture());
        assertEquals(2, sessions.getValue().size());
        assertEquals(0, refund.getValue().compareTo(new BigDecimal("180")));

        // Same refund logic as elsewhere: one refund per paid session (2 × 90).
        mockMvc.perform(get("/api/admin/training-refunds").header("Authorization", "Bearer " + admin))
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].type").value("SESSION"))
            .andExpect(jsonPath("$[0].amount").value(90))
            .andExpect(jsonPath("$[1].amount").value(90));
    }

    @Test
    void shouldRefundAndEmailOnlyPaidSessionsOnInstructorDay() throws Exception {
        // Per-training paid filter: the subscriber paid ONE of the trainer's two slots. Only that session is
        // refunded and listed in the email; the unpaid one just gets cheaper (no refund, not in the email).
        var instr = instructorRepository.save(new pl.fireacademy.domain.instructor.Instructor("Anna", "Trener"));
        EventType type = seedType();
        TrainingSlot paidSlot = instructorSlot(instr, type, DAY, 8);
        TrainingSlot unpaidSlot = instructorSlot(instr, type, DAY, 18);
        LocalDate date = nextSlotDate();
        String month = YearMonth.from(date).toString();
        String admin = adminToken();

        enroll(userToken(), paidSlot.getId(), "{\"startMonth\":\"" + month + "\"}");
        UUID paidEnrollment = trainingEnrollmentRepository.findActiveByUser(regularUserId(), month).getFirst().getId();
        markPaid(paidEnrollment, month, admin);           // pay this one BEFORE the second enrollment exists
        enroll(userToken(), unpaidSlot.getId(), "{\"startMonth\":\"" + month + "\"}");

        assertEquals(2, (int) JsonPath.read(cancelInstructorDay(instr.getId(),date, admin), "$.cancelled"));

        var sessions = ArgumentCaptor.forClass(List.class);
        var refund = ArgumentCaptor.forClass(BigDecimal.class);
        verify(trainingMail, org.mockito.Mockito.times(1)).sendInstructorDayCancellation(
                eq(USER_EMAIL), anyString(), eq("Anna Trener"), eq(date), sessions.capture(), refund.capture());
        assertEquals(1, sessions.getValue().size());       // only the paid session listed
        assertEquals(0, refund.getValue().compareTo(new BigDecimal("90")));

        // Only the paid session generates a refund.
        mockMvc.perform(get("/api/admin/training-refunds").header("Authorization", "Bearer " + admin))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].amount").value(90));
    }

    @Test
    void shouldRefundButNotEmailWhenInstructorDayIsHistorical() throws Exception {
        // A past instructor-day: the "don't come" email is pointless retroactively, but a paid client is still
        // owed a refund. Skip only on the 1st (yesterday would be last month).
        LocalDate yesterday = LocalDate.now().minusDays(1);
        org.junit.jupiter.api.Assumptions.assumeTrue(yesterday.getMonthValue() == LocalDate.now().getMonthValue(),
                "first of month — no past session date in the current month yet");
        var instr = instructorRepository.save(new pl.fireacademy.domain.instructor.Instructor("Anna", "Trener"));
        TrainingSlot slot = instructorSlot(instr, seedType(), yesterday.getDayOfWeek().getValue(), 8);
        String month = YearMonth.now().toString();
        String admin = adminToken();
        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + month + "\"}");
        UUID enrollmentId = trainingEnrollmentRepository.findActiveByUser(regularUserId(), month).getFirst().getId();
        // Established full-month client: billed from the 1st, so yesterday's session is genuinely part of the paid bill.
        setStart(enrollmentId, YearMonth.now().atDay(1).toString(), admin).andExpect(status().isNoContent());
        markPaid(enrollmentId, month, admin);

        assertEquals(1, (int) JsonPath.read(cancelInstructorDay(instr.getId(),yesterday, admin), "$.cancelled"));

        // No email for a past day…
        verify(trainingMail, org.mockito.Mockito.never())
                .sendInstructorDayCancellation(any(), any(), any(), any(), any(), any());
        // …but the paid client is still owed the refund.
        mockMvc.perform(get("/api/admin/training-refunds").header("Authorization", "Bearer " + admin))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].amount").value(90));
    }

    @Test
    void shouldNotEmailOrRefundUnpaidSubscriberOnInstructorDay() throws Exception {
        // An unpaid subscriber gets no email and no refund — the training just gets cheaper (estimate updates).
        var instr = instructorRepository.save(new pl.fireacademy.domain.instructor.Instructor("Anna", "Trener"));
        TrainingSlot slot = instructorSlot(instr, seedType(), DAY, 8);
        LocalDate date = nextSlotDate();
        String month = YearMonth.from(date).toString();
        String admin = adminToken();
        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + month + "\"}");   // NOT paid

        assertEquals(1, (int) JsonPath.read(cancelInstructorDay(instr.getId(),date, admin), "$.cancelled"));

        verify(trainingMail, org.mockito.Mockito.never())
                .sendInstructorDayCancellation(any(), any(), any(), any(), any(), any());
        mockMvc.perform(get("/api/admin/training-refunds").header("Authorization", "Bearer " + admin))
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldRejectDayOffInThePast() throws Exception {
        seedSlot(8);
        mockMvc.perform(post("/api/admin/training-holidays")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"" + LocalDate.now().minusDays(1) + "\"}"))
            .andExpect(status().isBadRequest());
    }

    // ── Cross-closure correctness: a date can be closed by a holiday, a single cancellation and a
    //    deactivation at once — refunds must arise once and survive undoing only the OTHER closures. ──

    private void cancelSession(UUID slotId, LocalDate date, String admin,
                               org.springframework.test.web.servlet.ResultMatcher expect) throws Exception {
        mockMvc.perform(post("/api/admin/training-slots/" + slotId + "/cancel-session")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionDate\":\"" + date + "\"}"))
            .andExpect(expect);
    }

    @Test
    void shouldKeepSessionRefundWhenOverlappingHolidayRemoved() throws Exception {
        TrainingSlot slot = seedSlot(8);
        LocalDate date = nextSlotDate();
        String month = YearMonth.from(date).toString();
        String admin = adminToken();
        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + month + "\"}");
        markPaid(trainingEnrollmentRepository.findActiveByUser(regularUserId(), month).getFirst().getId(), month, admin);

        // Single cancellation first → one refund. A holiday stacked on the same date must NOT add a second one.
        cancelSession(slot.getId(), date, admin, status().isCreated());
        mockMvc.perform(post("/api/admin/training-holidays")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"" + date + "\"}"))
            .andExpect(status().isCreated());
        mockMvc.perform(get("/api/admin/training-refunds").header("Authorization", "Bearer " + admin))
            .andExpect(jsonPath("$.length()").value(1));

        // Removing the day off must keep the refund — the session is still individually cancelled.
        String holidayId = JsonPath.read(
                mockMvc.perform(get("/api/admin/training-holidays").param("month", month)
                        .header("Authorization", "Bearer " + admin))
                    .andReturn().getResponse().getContentAsString(), "$[0].id");
        mockMvc.perform(delete("/api/admin/training-holidays/" + holidayId)
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/admin/training-refunds").header("Authorization", "Bearer " + admin))
            .andExpect(jsonPath("$.length()").value(1));

        // Only undoing the cancellation itself (the last closure) revokes it.
        mockMvc.perform(delete("/api/admin/training-slots/" + slot.getId() + "/cancel-session")
                .param("date", date.toString())
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/admin/training-refunds").header("Authorization", "Bearer " + admin))
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldRejectCancellingSessionOnAnExistingDayOff() throws Exception {
        // The classic money leak: holiday added while the month was unpaid (bill already reduced), payment made
        // afterwards, then a redundant per-slot cancellation of the same date must NOT mint a refund for a
        // session the subscriber never paid for — it is rejected outright.
        TrainingSlot slot = seedSlot(8);
        LocalDate date = nextSlotDate();
        String month = YearMonth.from(date).toString();
        String admin = adminToken();
        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + month + "\"}");
        mockMvc.perform(post("/api/admin/training-holidays")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"" + date + "\"}"))
            .andExpect(status().isCreated());
        markPaid(trainingEnrollmentRepository.findActiveByUser(regularUserId(), month).getFirst().getId(), month, admin);

        cancelSession(slot.getId(), date, admin, status().isConflict());
        mockMvc.perform(get("/api/admin/training-refunds").header("Authorization", "Bearer " + admin))
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldRejectUnpayingMonthWithSettledRefundUntilUnsettled() throws Exception {
        TrainingSlot slot = seedSlot(8);
        LocalDate date = nextSlotDate();
        String month = YearMonth.from(date).toString();
        String admin = adminToken();
        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + month + "\"}");
        UUID enrollmentId = trainingEnrollmentRepository.findActiveByUser(regularUserId(), month).getFirst().getId();
        markPaid(enrollmentId, month, admin);
        cancelSession(slot.getId(), date, admin, status().isCreated());
        String refundId = settleRefund(firstRefundId(admin), "CREDITED", admin);

        // The credited surplus hangs off this payment — reverting it is blocked until the refund is unsettled.
        setPaid(enrollmentId, month, false, admin, status().isConflict());
        mockMvc.perform(post("/api/admin/training-refunds/" + refundId + "/unsettle")
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isNoContent());
        setPaid(enrollmentId, month, false, admin, status().isNoContent());
    }

    @Test
    void shouldBlockUserCancellationWhenAFutureMonthIsPaid() throws Exception {
        TrainingSlot slot = seedSlot(8);
        String token = userToken();
        enroll(token, slot.getId(), "{\"startMonth\":\"" + CURRENT + "\"}");
        var te = trainingEnrollmentRepository.findActiveByUser(regularUserId(), CURRENT).getFirst();
        // Simulate a payment made in the pre-month window (the endpoint is time-gated, so insert directly).
        trainingPaymentRepository.save(new pl.fireacademy.domain.training.TrainingPayment(
                te, YearMonth.now().plusMonths(1)));

        // Cancelling would orphan the paid future month — blocked until the organizer settles it.
        mockMvc.perform(delete("/api/user/training-enrollments/" + te.getId())
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isConflict());
    }

    @Test
    void shouldRemovePaidParticipantRegisteringRefundAndKeepingArchive() throws Exception {
        TrainingSlot slot = seedSlot(8);
        String admin = adminToken();
        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + CURRENT + "\"}");
        var te = trainingEnrollmentRepository.findActiveByUser(regularUserId(), CURRENT).getFirst();
        UUID enrollmentId = te.getId();
        // A paid future month (insert directly — the pay endpoint is time-gated to the week before the month).
        trainingPaymentRepository.save(new pl.fireacademy.domain.training.TrainingPayment(te, YearMonth.now().plusMonths(1)));

        // Removal is now allowed even with a paid month: the unused sessions become a pending refund.
        mockMvc.perform(delete("/api/admin/training-enrollments/" + enrollmentId)
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isNoContent());

        // The subscription is kept as an archived record (it anchors the refund) and no longer covers the future month.
        org.junit.jupiter.api.Assertions.assertTrue(trainingEnrollmentRepository.findById(enrollmentId).isPresent());
        org.junit.jupiter.api.Assertions.assertEquals(8, availableSpots(NEXT, slot.getId())); // spot freed going forward
        // The paid month's row stays (not reverted) and a pending refund is now owed for every session of it.
        org.junit.jupiter.api.Assertions.assertTrue(
                trainingPaymentRepository.findByEnrollmentIdAndYearMonth(enrollmentId, NEXT).isPresent());
        int mondaysNext = mondaysIn(YearMonth.now().plusMonths(1));
        mockMvc.perform(get("/api/admin/training-refunds").header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(mondaysNext)); // full future month refunded
    }

    @Test
    void shouldReject400WhenRemovalDateIsInTheFuture() throws Exception {
        TrainingSlot slot = seedSlot(8);
        String admin = adminToken();
        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + CURRENT + "\"}");
        UUID enrollmentId = trainingEnrollmentRepository.findActiveByUser(regularUserId(), CURRENT).getFirst().getId();

        mockMvc.perform(delete("/api/admin/training-enrollments/" + enrollmentId)
                .param("date", LocalDate.now().plusDays(1).toString())
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRemoveUserFromAllTrainingsAtOnceWithGroupedRefund() throws Exception {
        TrainingSlot slot1 = seedSlot(8);
        TrainingSlot slot2 = seedSlot(8);
        String admin = adminToken();
        String token = userToken();
        enroll(token, slot1.getId(), "{\"startMonth\":\"" + CURRENT + "\"}");
        enroll(token, slot2.getId(), "{\"startMonth\":\"" + CURRENT + "\"}");
        UUID userId = regularUserId();
        // Prepay a future month on each subscription (insert directly — the pay endpoint is time-gated).
        for (var te : trainingEnrollmentRepository.findActiveByUser(userId, CURRENT)) {
            trainingPaymentRepository.save(new pl.fireacademy.domain.training.TrainingPayment(te, YearMonth.now().plusMonths(1)));
        }

        mockMvc.perform(delete("/api/admin/training-enrollments/user/" + userId)
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isNoContent());

        // Both trainings ended (no longer active for the future month) but kept as archives anchoring the refunds.
        org.junit.jupiter.api.Assertions.assertTrue(
                trainingEnrollmentRepository.findActiveByUser(userId, NEXT).isEmpty());
        org.junit.jupiter.api.Assertions.assertEquals(8, availableSpots(NEXT, slot1.getId()));
        // A pending refund for every session of both prepaid future months.
        int expected = mondaysIn(YearMonth.now().plusMonths(1)) * 2;
        mockMvc.perform(get("/api/admin/training-refunds").header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(expected));
        // ONE grouped e-mail per person — never a per-training one for a bulk removal.
        verify(trainingMail).sendAdminRemovedAll(eq(USER_EMAIL), anyString(), any(), any());
        verify(trainingMail, org.mockito.Mockito.never()).sendAdminRemoved(any(), any(), any());
    }

    @Test
    void shouldAddParticipantWithAMidMonthStartDayAndProrateFirstMonth() throws Exception {
        TrainingSlot slot = seedSlot(8);
        String admin = adminToken();
        createUserAndGetToken("mid@fireacademy.test", "Mid", "Month", UserRole.USER);
        UUID uid = userRepository.findByEmail("mid@fireacademy.test").orElseThrow().getId();
        String from = NEXT + "-15";

        mockMvc.perform(post("/api/admin/training-slots/" + slot.getId() + "/enrollments")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + uid + "\",\"startMonth\":\"" + NEXT + "\",\"billableFrom\":\"" + from + "\"}"))
            .andExpect(status().isCreated());

        // The first month bills only the sessions on/after the start day, not the whole month.
        int expectedSessions = mondaysInFrom(YearMonth.now().plusMonths(1), 15);
        var json = mockMvc.perform(get("/api/admin/training-slots/" + slot.getId() + "/enrollments")
                .param("month", NEXT).header("Authorization", "Bearer " + admin))
            .andReturn().getResponse().getContentAsString();
        double amount = ((Number) com.jayway.jsonpath.JsonPath.read(json, "$[0].amount")).doubleValue();
        org.junit.jupiter.api.Assertions.assertEquals(90.0 * expectedSessions, amount, 0.001);
    }

    @Test
    void shouldReject400WhenAddStartDayIsOutsideTheStartMonth() throws Exception {
        TrainingSlot slot = seedSlot(8);
        String admin = adminToken();
        createUserAndGetToken("out@fireacademy.test", "Out", "Range", UserRole.USER);
        UUID uid = userRepository.findByEmail("out@fireacademy.test").orElseThrow().getId();
        // Start day in the current month while the subscription starts NEXT month → invalid.
        mockMvc.perform(post("/api/admin/training-slots/" + slot.getId() + "/enrollments")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + uid + "\",\"startMonth\":\"" + NEXT + "\",\"billableFrom\":\"" + CURRENT + "-01\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnTrainingHistoryForOneClient() throws Exception {
        TrainingSlot slot = seedSlot(8);
        String admin = adminToken();
        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + CURRENT + "\"}");
        UUID userId = regularUserId();
        UUID enrollmentId = trainingEnrollmentRepository.findActiveByUser(userId, CURRENT).getFirst().getId();
        markPaid(enrollmentId, CURRENT, admin);

        mockMvc.perform(get("/api/admin/training-enrollments/user/" + userId + "/history")
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subscriptions.length()").value(1))
            .andExpect(jsonPath("$.subscriptions[0].active").value(true))
            .andExpect(jsonPath("$.payments.length()").value(1))
            .andExpect(jsonPath("$.payments[0].yearMonth").value(CURRENT))
            .andExpect(jsonPath("$.refunds.length()").value(0));
    }

    /** Count of Mondays (the seeded slot's weekday) in a month — for asserting a full-month refund deterministically. */
    private static int mondaysIn(YearMonth month) {
        int count = 0;
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            if (month.atDay(day).getDayOfWeek().getValue() == DAY) count++;
        }
        return count;
    }

    /** Count of Mondays on/after {@code fromDay} in a month — for asserting a prorated first-month bill. */
    private static int mondaysInFrom(YearMonth month, int fromDay) {
        int count = 0;
        for (int day = fromDay; day <= month.lengthOfMonth(); day++) {
            if (month.atDay(day).getDayOfWeek().getValue() == DAY) count++;
        }
        return count;
    }

    @Test
    void shouldRejectEnrollingForMonthAfterSlotStops() throws Exception {
        TrainingSlot slot = seedSlot(8);
        String admin = adminToken();
        LocalDate stops = YearMonth.now().plusMonths(1).atDay(1);
        mockMvc.perform(post("/api/admin/training-slots/" + slot.getId() + "/deactivate")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"from\":\"" + stops + "\"}"))
            .andExpect(status().isOk());

        // The month after the slot stops has zero sessions — booking it makes no sense.
        mockMvc.perform(post("/api/user/training-slots/" + slot.getId() + "/enroll")
                .header("Authorization", "Bearer " + userToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startMonth\":\"" + NEXT + "\"}"))
            .andExpect(status().isConflict());

        // The public catalog hides the slot when browsing that month, but still shows it for the current one.
        mockMvc.perform(get("/api/public/training-slots").param("month", NEXT))
            .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/public/training-slots").param("month", CURRENT))
            .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void shouldRejectAdminAddToDeletedSlot() throws Exception {
        TrainingSlot slot = seedSlot(8);
        String admin = adminToken();
        mockMvc.perform(delete("/api/admin/training-slots/" + slot.getId())
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isNoContent());

        createUserAndGetToken("extra@fireacademy.test", "Ekstra", "Osoba", UserRole.USER);
        UUID extraId = userRepository.findByEmail("extra@fireacademy.test").orElseThrow().getId();
        mockMvc.perform(post("/api/admin/training-slots/" + slot.getId() + "/enrollments")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + extraId + "\",\"startMonth\":\"" + CURRENT + "\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void shouldNotifyOrganizerWhenAccountWithSubscriptionIsDeleted() throws Exception {
        TrainingSlot slot = seedSlot(8);
        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + CURRENT + "\"}");

        mockMvc.perform(delete("/api/admin/users/" + regularUserId())
                .param("notify", "false")
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk());

        // The subscription is cascade-deleted with the account — the organizer learns the spot freed up.
        verify(trainingMail).sendAdminEnrollmentNotification(eq(false), anyString(), eq(USER_EMAIL),
                any(), anyString(), anyLong(), anyInt());
        assertEquals(0, trainingEnrollmentRepository.count());
    }

    @Test
    void shouldFreezePaidAmountAgainstLaterCancellations() throws Exception {
        TrainingSlot slot = seedSlot(8);
        LocalDate date = nextSlotDate();
        String month = YearMonth.from(date).toString();
        String admin = adminToken();
        int remaining = sessionsInCurrentMonth();
        org.junit.jupiter.api.Assumptions.assumeTrue(YearMonth.from(date).toString().equals(CURRENT) && remaining > 0);

        enroll(userToken(), slot.getId(), "{\"startMonth\":\"" + month + "\"}");
        UUID enrollmentId = trainingEnrollmentRepository.findActiveByUser(regularUserId(), month).getFirst().getId();
        markPaid(enrollmentId, month, admin);

        // Cancel one paid session, then settle its refund in CASH — the pending amount drops to zero,
        // yet the account keeps showing the amount that was actually collected (frozen at payment time).
        cancelSession(slot.getId(), date, admin, status().isCreated());
        settleRefund(firstRefundId(admin), "REFUNDED", admin);

        String body = mockMvc.perform(get("/api/user/training-enrollments")
                .header("Authorization", "Bearer " + userToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].billingMonthPaid").value(true))
            .andReturn().getResponse().getContentAsString();
        assertAmount(body, "$[0].billingMonthPaidAmount", 90L * remaining);
        assertAmount(body, "$[0].monthlyAmount", 90L * (remaining - 1));
        assertAmount(body, "$[0].pendingRefundAmount", 0);
    }
}
