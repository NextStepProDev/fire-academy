package pl.fireacademy.api.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import pl.fireacademy.BaseIntegrationTest;
import pl.fireacademy.domain.enrollment.Enrollment;
import pl.fireacademy.domain.enrollment.EnrollmentRepository;
import pl.fireacademy.domain.event.Event;
import pl.fireacademy.domain.event.EventCategory;
import pl.fireacademy.domain.event.EventRepository;
import pl.fireacademy.domain.user.User;
import pl.fireacademy.domain.user.UserRole;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminUserControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired private EventRepository eventRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;

    // app.admin.email w BaseIntegrationTest = "admin@fireacademy.test" → super-admin z .env
    private String superAdminToken() {
        return createUserAndGetToken("admin@fireacademy.test", "Env", "Admin", UserRole.ADMIN);
    }

    private User createUser(String email, String firstName, String lastName, UserRole role) {
        User u = new User(email, firstName, lastName, "123456789");
        u.setPasswordHash("$2a$12$dummyhash");
        u.setRole(role);
        u.markEmailVerified();
        return userRepository.save(u);
    }

    // --- security ---

    @Test
    void shouldRejectListForAnonymous() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectListForRegularUser() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                .header("Authorization", "Bearer " + userToken()))
            .andExpect(status().isForbidden());
    }

    // --- list / search ---

    @Test
    void shouldListUsers() throws Exception {
        createUser("alice@test.com", "Alicja", "Nowak", UserRole.USER);

        mockMvc.perform(get("/api/admin/users")
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.totalElements").isNumber());
    }

    @Test
    void shouldSearchUsersByPhrase() throws Exception {
        createUser("alice@test.com", "Alicja", "Kowalska", UserRole.USER);
        createUser("bob@test.com", "Robert", "Zieliński", UserRole.USER);
        adminToken();

        mockMvc.perform(get("/api/admin/users")
                .param("search", "kowal")
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].email").value("alice@test.com"));
    }

    @Test
    void shouldSortUsersByNameAscending() throws Exception {
        createUser("z@test.com", "Zenon", "Zalewski", UserRole.USER);
        createUser("a@test.com", "Adam", "Adamski", UserRole.USER);
        createUser("n@test.com", "Natalia", "Nowak", UserRole.USER);

        mockMvc.perform(get("/api/admin/users")
                .param("sort", "name").param("direction", "asc")
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].lastName").value("Adamski"));
    }

    @Test
    void shouldPaginateUsers() throws Exception {
        for (int i = 0; i < 5; i++) {
            createUser("user" + i + "@test.com", "Imie" + i, "Nazwisko" + i, UserRole.USER);
        }

        mockMvc.perform(get("/api/admin/users")
                .param("page", "0").param("size", "2")
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(2))
            // 5 utworzonych + testadmin z adminToken() = 6 → 3 strony po 2
            .andExpect(jsonPath("$.totalElements").value(6))
            .andExpect(jsonPath("$.totalPages").value(3));
    }

    // --- detail ---

    @Test
    void shouldReturnUserDetailWithEnrollments() throws Exception {
        User u = createUser("detail@test.com", "Jan", "Kowalski", UserRole.USER);
        Event futureEvent = eventRepository.save(
                new Event(EventCategory.TRAINING, "Nadchodzący", LocalDate.now().plusDays(5)));
        Event pastEvent = eventRepository.save(
                new Event(EventCategory.CAMP, "Miniony", LocalDate.now().minusDays(20)));
        enrollmentRepository.save(Enrollment.forUser(futureEvent, u, null, false));
        enrollmentRepository.save(Enrollment.forUser(pastEvent, u, null, false));

        mockMvc.perform(get("/api/admin/users/" + u.getId())
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("detail@test.com"))
            .andExpect(jsonPath("$.currentEnrollments.length()").value(1))
            .andExpect(jsonPath("$.pastEnrollments.length()").value(1))
            .andExpect(jsonPath("$.pastEnrollments[0].past").value(true));
    }

    @Test
    void shouldReturnNotFoundForMissingUserDetail() throws Exception {
        mockMvc.perform(get("/api/admin/users/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isNotFound());
    }

    @Test
    void shouldDeleteArchivedEnrollmentWithoutNotification() throws Exception {
        Event pastEvent = eventRepository.save(
                new Event(EventCategory.CAMP, "Miniony", LocalDate.now().minusDays(20)));
        Enrollment en = enrollmentRepository.save(
                new Enrollment(pastEvent, "Jan", "Kowalski", "x@test.com", "123456789", null, false));

        mockMvc.perform(delete("/api/admin/enrollments/" + en.getId())
                .param("notify", "false")
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isNoContent());

        org.junit.jupiter.api.Assertions.assertTrue(enrollmentRepository.findById(en.getId()).isEmpty());
    }

    // --- roles ---

    @Test
    void shouldPromoteUserBySuperAdmin() throws Exception {
        User target = createUser("promote@test.com", "Do", "Awansu", UserRole.USER);

        mockMvc.perform(post("/api/admin/users/" + target.getId() + "/promote")
                .header("Authorization", "Bearer " + superAdminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void shouldRejectPromoteByPlainAdmin() throws Exception {
        User target = createUser("promote2@test.com", "Do", "Awansu", UserRole.USER);

        // testadmin@fireacademy.test NIE jest super-adminem z .env → nadanie uprawnień zabronione (409)
        mockMvc.perform(post("/api/admin/users/" + target.getId() + "/promote")
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isConflict());
    }

    @Test
    void shouldRejectDemoteByPlainAdmin() throws Exception {
        User target = createUser("admin3@test.com", "Inny", "Admin", UserRole.ADMIN);

        // testadmin@fireacademy.test NIE jest super-adminem z .env → degradacja zabroniona (409)
        mockMvc.perform(post("/api/admin/users/" + target.getId() + "/demote")
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isConflict());
    }

    @Test
    void shouldDemoteAdminBySuperAdmin() throws Exception {
        User target = createUser("admin4@test.com", "Do", "Degradacji", UserRole.ADMIN);

        mockMvc.perform(post("/api/admin/users/" + target.getId() + "/demote")
                .header("Authorization", "Bearer " + superAdminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void shouldRejectDemoteOfSuperAdminTarget() throws Exception {
        // super-admin nie może odebrać sobie ani innemu super-adminowi (tu: samemu sobie → 409)
        User superAdmin = userRepository.findByEmail("admin@fireacademy.test")
                .orElseGet(() -> createUser("admin@fireacademy.test", "Env", "Admin", UserRole.ADMIN));

        mockMvc.perform(post("/api/admin/users/" + superAdmin.getId() + "/demote")
                .header("Authorization", "Bearer " + superAdminToken()))
            .andExpect(status().isConflict());
    }

    // --- delete ---

    @Test
    void shouldDeleteUserFreeingFutureAndAnonymizingPast() throws Exception {
        User target = createUser("todelete@test.com", "Do", "Usunięcia", UserRole.USER);

        Event futureEvent = eventRepository.save(
                new Event(EventCategory.TRAINING, "Nadchodzący", LocalDate.now().plusDays(5)));
        Event pastEvent = eventRepository.save(
                new Event(EventCategory.CAMP, "Archiwalny", LocalDate.now().minusDays(30)));
        Enrollment future = enrollmentRepository.save(Enrollment.forUser(futureEvent, target, null, false));
        Enrollment past = enrollmentRepository.save(Enrollment.forUser(pastEvent, target, null, false));

        mockMvc.perform(delete("/api/admin/users/" + target.getId())
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.freedEnrollments").value(1))
            .andExpect(jsonPath("$.anonymizedEnrollments").value(1));

        org.junit.jupiter.api.Assertions.assertTrue(userRepository.findById(target.getId()).isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(enrollmentRepository.findById(future.getId()).isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(
                enrollmentRepository.findById(past.getId()).orElseThrow().isAnonymized());
    }

    @Test
    void shouldRejectDeleteOfSuperAdmin() throws Exception {
        User superAdmin = userRepository.findByEmail("admin@fireacademy.test")
                .orElseGet(() -> createUser("admin@fireacademy.test", "Env", "Admin", UserRole.ADMIN));

        mockMvc.perform(delete("/api/admin/users/" + superAdmin.getId())
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isConflict());
    }

    @Test
    void shouldReturnNotFoundWhenDeletingMissingUser() throws Exception {
        mockMvc.perform(delete("/api/admin/users/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isNotFound());
    }

    // --- send email ---

    @Test
    void shouldSendEmailToSelectedUsers() throws Exception {
        User target = createUser("recipient@test.com", "Adresat", "Testowy", UserRole.USER);

        mockMvc.perform(post("/api/admin/users/email")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"subject":"Witaj","message":"Treść wiadomości","audience":"SELECTED","userIds":["%s"]}
                    """.formatted(target.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recipientCount").value(1));
    }

    @Test
    void shouldRejectEmailWithNoRecipients() throws Exception {
        mockMvc.perform(post("/api/admin/users/email")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"subject":"Witaj","message":"Treść","audience":"SELECTED","userIds":[]}
                    """))
            .andExpect(status().isConflict());
    }

    @Test
    void shouldRejectEmailWithBlankSubject() throws Exception {
        mockMvc.perform(post("/api/admin/users/email")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"subject":"","message":"Treść","audience":"ALL"}
                    """))
            .andExpect(status().isBadRequest());
    }
}
