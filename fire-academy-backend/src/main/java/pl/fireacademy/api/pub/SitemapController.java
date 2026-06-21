package pl.fireacademy.api.pub;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.fireacademy.config.AppConfig;
import pl.fireacademy.domain.event.Event;
import pl.fireacademy.domain.event.EventCategory;
import pl.fireacademy.domain.event.EventRepository;
import pl.fireacademy.domain.event.EventType;
import pl.fireacademy.domain.event.EventTypeRepository;
import pl.fireacademy.domain.instructor.Instructor;
import pl.fireacademy.domain.instructor.InstructorRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
public class SitemapController {

    private static final Map<EventCategory, String> CATEGORY_SLUGS = Map.of(
            EventCategory.TRAINING, "treningi",
            EventCategory.CAMP, "obozy",
            EventCategory.COURSE, "szkolenia"
    );

    private final AppConfig appConfig;
    private final EventRepository eventRepository;
    private final EventTypeRepository eventTypeRepository;
    private final InstructorRepository instructorRepository;

    public SitemapController(AppConfig appConfig,
                             EventRepository eventRepository,
                             EventTypeRepository eventTypeRepository,
                             InstructorRepository instructorRepository) {
        this.appConfig = appConfig;
        this.eventRepository = eventRepository;
        this.eventTypeRepository = eventTypeRepository;
        this.instructorRepository = instructorRepository;
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap() {
        String siteUrl = appConfig.getSiteUrl();
        String today = formatDate(Instant.now());

        var sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        addUrl(sb, siteUrl + "/", today, "daily", "1.0");
        addUrl(sb, siteUrl + "/treningi", today, "daily", "0.9");
        addUrl(sb, siteUrl + "/obozy", today, "daily", "0.9");
        addUrl(sb, siteUrl + "/szkolenia", today, "daily", "0.9");

        List<Event> activeEvents = new java.util.ArrayList<>(eventRepository
                .findActiveCurrentByCategory(
                        EventCategory.TRAINING, LocalDate.now()));
        activeEvents.addAll(eventRepository
                .findActiveCurrentByCategory(
                        EventCategory.CAMP, LocalDate.now()));
        activeEvents.addAll(eventRepository
                .findActiveCurrentByCategory(
                        EventCategory.COURSE, LocalDate.now()));

        for (Event event : activeEvents) {
            String slug = CATEGORY_SLUGS.get(event.getCategory());
            String lastmod = formatDate(event.getUpdatedAt());
            addUrl(sb, siteUrl + "/" + slug + "/termin/" + event.getId(), lastmod, "weekly", "0.8");
        }

        for (EventCategory category : EventCategory.values()) {
            String slug = CATEGORY_SLUGS.get(category);
            List<EventType> types = eventTypeRepository.findByCategoryAndActiveTrueOrderByDisplayOrderAsc(category);
            for (EventType et : types) {
                String lastmod = formatDate(et.getUpdatedAt());
                addUrl(sb, siteUrl + "/" + slug + "/rodzaj/" + et.getId(), lastmod, "weekly", "0.7");
            }
        }

        List<Instructor> instructors = instructorRepository.findAll().stream()
                .filter(Instructor::isActive)
                .toList();
        for (Instructor i : instructors) {
            String lastmod = formatDate(i.getUpdatedAt());
            addUrl(sb, siteUrl + "/kadra/" + i.getId(), lastmod, "monthly", "0.6");
        }

        sb.append("</urlset>");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(sb.toString());
    }

    private static void addUrl(StringBuilder sb, String loc, String lastmod, String changefreq, String priority) {
        sb.append("  <url>\n");
        sb.append("    <loc>").append(escapeXml(loc)).append("</loc>\n");
        sb.append("    <lastmod>").append(lastmod).append("</lastmod>\n");
        sb.append("    <changefreq>").append(changefreq).append("</changefreq>\n");
        sb.append("    <priority>").append(priority).append("</priority>\n");
        sb.append("  </url>\n");
    }

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_DATE.withZone(ZoneOffset.UTC);

    private static String formatDate(Instant instant) {
        return DATE_FORMAT.format(instant);
    }

    private static String escapeXml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
