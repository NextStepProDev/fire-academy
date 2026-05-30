package pl.fireacademy.api.pub;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.fireacademy.BaseIntegrationTest;
import pl.fireacademy.domain.event.*;
import pl.fireacademy.domain.instructor.Instructor;
import pl.fireacademy.domain.instructor.InstructorRepository;

import java.time.LocalDate;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OgControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired private EventTypeRepository eventTypeRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private InstructorRepository instructorRepository;

    @Test
    void shouldReturnOgHtmlForHome() throws Exception {
        mockMvc.perform(get("/og/"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/html"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("og:title")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Fire Academy")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("og:image")));
    }

    @Test
    void shouldReturnOgForEventType() throws Exception {
        EventType et = new EventType(EventCategory.TRAINING, "Trening OG Test");
        et.setDescription("Opis treningu do OG");
        et.setDisplayOrder(0);
        et.setActive(true);
        et.setThumbnailFilename(null);
        et = eventTypeRepository.save(et);

        mockMvc.perform(get("/og/treningi/rodzaj/" + et.getId()))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Trening OG Test")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Opis treningu do OG")));
    }

    @Test
    void shouldReturnOgForEvent() throws Exception {
        Event event = new Event(EventCategory.CAMP, "Camp OG Test", LocalDate.of(2026, 7, 15));
        event.setLocation("Zakopane");
        event.setActive(true);
        event = eventRepository.save(event);

        mockMvc.perform(get("/og/obozy/termin/" + event.getId()))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Camp OG Test")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Zakopane")));
    }

    @Test
    void shouldReturnOgForInstructor() throws Exception {
        Instructor instructor = new Instructor("Marek", "Trener");
        instructor.setBio("Doświadczony trener z 10-letnim stażem");
        instructor.setCategories(Set.of(EventCategory.TRAINING));
        instructor.setActive(true);
        instructor.setDisplayOrder(0);
        instructor = instructorRepository.save(instructor);

        mockMvc.perform(get("/og/kadra/" + instructor.getId()))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Marek Trener")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Fire Academy")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("10-letnim")));
    }

    @Test
    void shouldReturn404ForInvalidCategorySlug() throws Exception {
        mockMvc.perform(get("/og/invalid/rodzaj/" + java.util.UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    @Test
    void shouldEscapeHtmlInOgOutput() throws Exception {
        EventType et = new EventType(EventCategory.COURSE, "Test <script>alert('xss')</script>");
        et.setDisplayOrder(0);
        et.setActive(true);
        et = eventTypeRepository.save(et);

        mockMvc.perform(get("/og/szkolenia/rodzaj/" + et.getId()))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<script>"))));
    }

    @Test
    void shouldIncludeMetaRefreshRedirect() throws Exception {
        mockMvc.perform(get("/og/"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("http-equiv=\"refresh\"")));
    }
}
