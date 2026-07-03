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
}
