package pl.fireacademy.api.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import pl.fireacademy.BaseIntegrationTest;
import pl.fireacademy.domain.enrollment.Enrollment;
import pl.fireacademy.domain.enrollment.EnrollmentRepository;
import pl.fireacademy.domain.event.*;
import pl.fireacademy.domain.instructor.Instructor;
import pl.fireacademy.domain.instructor.InstructorRepository;
import pl.fireacademy.domain.user.User;
import pl.fireacademy.domain.user.UserRole;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired private InstructorRepository instructorRepository;
    @Autowired private EventTypeRepository eventTypeRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;

    // --- Instructor CRUD ---

    @Test
    void shouldCreateInstructor() throws Exception {
        mockMvc.perform(post("/api/admin/instructors")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"firstName":"Anna","lastName":"Trener","bio":"Doświadczona","categories":["TRAINING"]}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.firstName").value("Anna"))
            .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void shouldUpdateInstructor() throws Exception {
        Instructor instructor = new Instructor("Old", "Name");
        instructor.setCategories(Set.of(EventCategory.TRAINING));
        instructor.setDisplayOrder(0);
        instructor = instructorRepository.save(instructor);

        mockMvc.perform(put("/api/admin/instructors/" + instructor.getId())
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"firstName":"New","lastName":"Name","bio":"Updated bio","categories":["CAMP","TRAINING"]}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.firstName").value("New"))
            .andExpect(jsonPath("$.bio").value("Updated bio"));
    }

    @Test
    void shouldDeleteInstructor() throws Exception {
        Instructor instructor = new Instructor("ToDelete", "Person");
        instructor.setCategories(Set.of(EventCategory.COURSE));
        instructor.setDisplayOrder(0);
        instructor = instructorRepository.save(instructor);

        mockMvc.perform(delete("/api/admin/instructors/" + instructor.getId())
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isNoContent());
    }

    @Test
    void shouldToggleInstructorActive() throws Exception {
        Instructor instructor = new Instructor("Toggle", "Test");
        instructor.setCategories(Set.of(EventCategory.TRAINING));
        instructor.setDisplayOrder(0);
        instructor = instructorRepository.save(instructor);

        mockMvc.perform(patch("/api/admin/instructors/" + instructor.getId() + "/toggle-active")
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));
    }

    // --- Event CRUD ---

    @Test
    void shouldCreateEventWithCustomName() throws Exception {
        mockMvc.perform(post("/api/admin/events")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "customName": "Trening specjalny",
                        "category": "TRAINING",
                        "startDate": "%s",
                        "startTime": "18:00",
                        "location": "Kraków",
                        "maxParticipants": 6
                    }
                    """.formatted(LocalDate.now().plusDays(14))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.eventTypeName").value("Trening specjalny"));
    }

    @Test
    void shouldRejectEventInThePast() throws Exception {
        mockMvc.perform(post("/api/admin/events")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "customName": "Past event",
                        "category": "TRAINING",
                        "startDate": "2020-01-01"
                    }
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldDeleteEventWithoutEnrollments() throws Exception {
        Event event = new Event(EventCategory.TRAINING, "To delete", LocalDate.now().plusDays(7));
        event.setActive(true);
        event = eventRepository.save(event);

        mockMvc.perform(delete("/api/admin/events/" + event.getId())
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isNoContent());
    }

    @Test
    void shouldRejectDeleteEventWithEnrollments() throws Exception {
        Event event = new Event(EventCategory.TRAINING, "Has enrollments", LocalDate.now().plusDays(7));
        event.setActive(true);
        event = eventRepository.save(event);

        Enrollment enrollment = new Enrollment(event, "Jan", "Test", "jan@test.com", "123456789", null, false);
        enrollmentRepository.save(enrollment);

        mockMvc.perform(delete("/api/admin/events/" + event.getId())
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isConflict());
    }

    // --- Event Type CRUD ---

    @Test
    void shouldCreateEventType() throws Exception {
        mockMvc.perform(post("/api/admin/event-types")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"category":"CAMP","name":"Obóz letni","description":"Obóz w górach"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Obóz letni"))
            .andExpect(jsonPath("$.category").value("CAMP"));
    }

    @Test
    void shouldToggleEventTypeActive() throws Exception {
        EventType et = new EventType(EventCategory.COURSE, "Szkolenie BHP");
        et.setDisplayOrder(0);
        et = eventTypeRepository.save(et);

        mockMvc.perform(patch("/api/admin/event-types/" + et.getId() + "/toggle-active")
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));
    }

    // --- Enrollment admin ---

    @Test
    void shouldAdminEnroll() throws Exception {
        Event event = new Event(EventCategory.TRAINING, "Admin enroll test", LocalDate.now().plusDays(14));
        event.setStartTime(LocalTime.of(10, 0));
        event.setActive(true);
        event = eventRepository.save(event);

        UUID userId = createUser("admin-added@test.com", "Admin", "Added", "111222333");

        mockMvc.perform(post("/api/admin/enrollments")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "eventId": "%s",
                        "userId": "%s"
                    }
                    """.formatted(event.getId(), userId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.addedByAdmin").value(true))
            .andExpect(jsonPath("$.email").value("admin-added@test.com"));
    }

    @Test
    void shouldAdminEnrollUserWithoutPhone() throws Exception {
        Event event = new Event(EventCategory.TRAINING, "Enroll no phone", LocalDate.now().plusDays(14));
        event.setActive(true);
        event = eventRepository.save(event);

        UUID userId = createUser("no-phone@test.com", "Bez", "Telefonu", null);

        mockMvc.perform(post("/api/admin/enrollments")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "eventId": "%s",
                        "userId": "%s"
                    }
                    """.formatted(event.getId(), userId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.addedByAdmin").value(true))
            .andExpect(jsonPath("$.phone").value(org.hamcrest.Matchers.nullValue()));
    }

    private UUID createUser(String email, String firstName, String lastName, String phone) {
        User u = new User(email, firstName, lastName, phone);
        u.setPasswordHash("$2a$12$dummyhash");
        u.setRole(UserRole.USER);
        u.markEmailVerified();
        return userRepository.save(u).getId();
    }

}
