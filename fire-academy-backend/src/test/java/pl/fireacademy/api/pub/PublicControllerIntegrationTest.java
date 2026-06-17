package pl.fireacademy.api.pub;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import pl.fireacademy.BaseIntegrationTest;
import pl.fireacademy.domain.event.*;
import pl.fireacademy.domain.instructor.Instructor;
import pl.fireacademy.domain.instructor.InstructorRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PublicControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired private InstructorRepository instructorRepository;
    @Autowired private EventTypeRepository eventTypeRepository;
    @Autowired private EventRepository eventRepository;

    @Test
    void shouldGetActiveInstructors() throws Exception {
        Instructor instructor = new Instructor("Anna", "Trener");
        instructor.setCategories(Set.of(EventCategory.TRAINING));
        instructor.setActive(true);
        instructor.setDisplayOrder(0);
        instructorRepository.save(instructor);

        mockMvc.perform(get("/api/public/instructors").param("category", "TRAINING"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.firstName == 'Anna')]").exists());
    }

    @Test
    void shouldGetActiveEventTypes() throws Exception {
        EventType et = new EventType(EventCategory.CAMP, "Obóz letni");
        et.setDisplayOrder(0);
        et.setActive(true);
        eventTypeRepository.save(et);

        mockMvc.perform(get("/api/public/event-types").param("category", "CAMP"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.name == 'Obóz letni')]").exists());
    }

    @Test
    void shouldGetUpcomingEvents() throws Exception {
        Event event = new Event(EventCategory.TRAINING, "Trening otwarty", LocalDate.now().plusDays(14));
        event.setStartTime(LocalTime.of(18, 0));
        event.setActive(true);
        eventRepository.save(event);

        mockMvc.perform(get("/api/public/events").param("category", "TRAINING"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.eventTypeName == 'Trening otwarty')]").exists());
    }

    @Test
    void shouldGetInstructorById() throws Exception {
        Instructor instructor = new Instructor("Piotr", "Instruktor");
        instructor.setCategories(Set.of(EventCategory.COURSE));
        instructor.setActive(true);
        instructor.setDisplayOrder(0);
        instructor = instructorRepository.save(instructor);

        mockMvc.perform(get("/api/public/instructors/" + instructor.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.firstName").value("Piotr"));
    }

    @Test
    void shouldReturn404ForInactiveInstructor() throws Exception {
        Instructor instructor = new Instructor("Inactive", "Instructor");
        instructor.setCategories(Set.of(EventCategory.TRAINING));
        instructor.setActive(false);
        instructor.setDisplayOrder(0);
        instructor = instructorRepository.save(instructor);

        mockMvc.perform(get("/api/public/instructors/" + instructor.getId()))
            .andExpect(status().isNotFound());
    }
}
