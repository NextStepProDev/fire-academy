package pl.fireacademy.api.pub;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import pl.fireacademy.BaseIntegrationTest;
import pl.fireacademy.domain.event.Event;
import pl.fireacademy.domain.event.EventCategory;
import pl.fireacademy.domain.event.EventRepository;
import pl.fireacademy.domain.event.EventType;
import pl.fireacademy.domain.event.EventTypeRepository;
import pl.fireacademy.domain.instructor.Instructor;
import pl.fireacademy.domain.instructor.InstructorRepository;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SitemapControllerIntegrationTest extends BaseIntegrationTest {

    private static final String BASE = "http://localhost:5174";

    @Autowired private EventRepository eventRepository;
    @Autowired private EventTypeRepository eventTypeRepository;
    @Autowired private InstructorRepository instructorRepository;

    @Test
    void shouldReturnWellFormedXmlWithStaticUrls() throws Exception {
        MvcResult result = mockMvc.perform(get("/sitemap.xml"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/xml"))
            .andReturn();

        List<String> locs = parseLocs(result);
        // Static pages are always present — they hold the index of the main tabs.
        assertTrue(locs.contains(BASE + "/"));
        assertTrue(locs.contains(BASE + "/treningi"));
        assertTrue(locs.contains(BASE + "/obozy"));
        assertTrue(locs.contains(BASE + "/szkolenia"));
    }

    @Test
    void shouldIncludeActiveEntitiesAndExcludeInactiveOrPast() throws Exception {
        Event futureEvent = new Event(EventCategory.TRAINING, "Trening otwarty", LocalDate.now().plusDays(14));
        futureEvent.setStartTime(LocalTime.of(18, 0));
        futureEvent.setActive(true);
        UUID futureEventId = eventRepository.save(futureEvent).getId();

        Event pastEvent = new Event(EventCategory.TRAINING, "Miniony trening", LocalDate.now().minusDays(14));
        pastEvent.setStartTime(LocalTime.of(18, 0));
        pastEvent.setActive(true);
        UUID pastEventId = eventRepository.save(pastEvent).getId();

        Event inactiveEvent = new Event(EventCategory.CAMP, "Ukryty obóz", LocalDate.now().plusDays(20));
        inactiveEvent.setActive(false);
        UUID inactiveEventId = eventRepository.save(inactiveEvent).getId();

        EventType activeType = new EventType(EventCategory.CAMP, "Obóz letni");
        activeType.setActive(true);
        activeType.setDisplayOrder(0);
        UUID activeTypeId = eventTypeRepository.save(activeType).getId();

        EventType inactiveType = new EventType(EventCategory.COURSE, "Stare szkolenie");
        inactiveType.setActive(false);
        inactiveType.setDisplayOrder(1);
        UUID inactiveTypeId = eventTypeRepository.save(inactiveType).getId();

        Instructor activeInstructor = new Instructor("Anna", "Trener");
        activeInstructor.setCategories(Set.of(EventCategory.TRAINING));
        activeInstructor.setActive(true);
        activeInstructor.setDisplayOrder(0);
        UUID activeInstructorId = instructorRepository.save(activeInstructor).getId();

        Instructor inactiveInstructor = new Instructor("Hidden", "Coach");
        inactiveInstructor.setCategories(Set.of(EventCategory.TRAINING));
        inactiveInstructor.setActive(false);
        inactiveInstructor.setDisplayOrder(1);
        UUID inactiveInstructorId = instructorRepository.save(inactiveInstructor).getId();

        MvcResult result = mockMvc.perform(get("/sitemap.xml"))
            .andExpect(status().isOk())
            .andReturn();

        List<String> locs = parseLocs(result);
        // Active and current entities are in the sitemap.
        assertTrue(locs.contains(BASE + "/treningi/termin/" + futureEventId));
        assertTrue(locs.contains(BASE + "/obozy/rodzaj/" + activeTypeId));
        assertTrue(locs.contains(BASE + "/kadra/" + activeInstructorId));
        // Past and inactive entities must not reach the sitemap (SEO: no dead/hidden URLs).
        assertFalse(locs.contains(BASE + "/treningi/termin/" + pastEventId));
        assertFalse(locs.contains(BASE + "/obozy/termin/" + inactiveEventId));
        assertFalse(locs.contains(BASE + "/szkolenia/rodzaj/" + inactiveTypeId));
        assertFalse(locs.contains(BASE + "/kadra/" + inactiveInstructorId));
    }

    /** Parses the response as XML (a hard guarantee of correct structure) and extracts all {@code <loc>}. */
    private List<String> parseLocs(MvcResult result) throws Exception {
        byte[] body = result.getResponse().getContentAsByteArray();
        var factory = DocumentBuilderFactory.newInstance();
        Document doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(body));
        assertEquals("urlset", doc.getDocumentElement().getNodeName());

        NodeList locNodes = doc.getElementsByTagName("loc");
        List<String> locs = new ArrayList<>();
        for (int i = 0; i < locNodes.getLength(); i++) {
            locs.add(((Element) locNodes.item(i)).getTextContent());
        }
        assertFalse(locs.isEmpty(), "Sitemap nie może być pusta");
        // Sanity: each <url> entry has exactly one <loc>.
        assertEquals(doc.getElementsByTagName("url").getLength(), locs.size());
        return locs;
    }
}
