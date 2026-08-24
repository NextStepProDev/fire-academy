package pl.fireacademy.api.pub;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import pl.fireacademy.api.NotFoundException;
import pl.fireacademy.api.pub.PublicDtos.EventCard;
import pl.fireacademy.api.pub.PublicDtos.EventTypeCard;
import pl.fireacademy.config.AppConfig;
import pl.fireacademy.domain.event.EventCategory;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/og")
public class OgController {

    private static final Map<String, EventCategory> SLUG_TO_CATEGORY = Map.of(
            "treningi", EventCategory.TRAINING,
            "obozy", EventCategory.CAMP,
            "szkolenia", EventCategory.COURSE
    );

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.of("pl"));

    private final PublicService service;
    private final AppConfig appConfig;

    public OgController(PublicService service, AppConfig appConfig) {
        this.service = service;
        this.appConfig = appConfig;
    }

    @GetMapping("/{categorySlug}/rodzaj/{id}")
    public ResponseEntity<String> eventTypeOg(@PathVariable String categorySlug,
                                               @PathVariable UUID id) {
        if (!SLUG_TO_CATEGORY.containsKey(categorySlug)) {
            return ResponseEntity.notFound().build();
        }
        EventTypeCard et = service.getEventTypeById(id);

        String siteUrl = appConfig.getSiteUrl();
        String pageUrl = siteUrl + "/" + categorySlug + "/rodzaj/" + id;
        String imageUrl = et.thumbnailUrl() != null ? siteUrl + et.thumbnailUrl() : siteUrl + "/og-default.png";

        String jsonLd = """
                {"@context":"https://schema.org","@type":"Course","name":"%s"%s,"provider":{"@type":"Organization","name":"Fire Academy","url":"%s"},"url":"%s","inLanguage":"pl"}"""
                .formatted(
                        escapeJson(et.name()),
                        et.description() != null ? ",\"description\":\"" + escapeJson(truncate(et.description(), 200)) + "\"" : "",
                        siteUrl, pageUrl);
        return ogResponse(et.name(), truncate(et.description(), 200), imageUrl, pageUrl, jsonLd);
    }

    @GetMapping("/{categorySlug}/termin/{id}")
    public ResponseEntity<String> eventOg(@PathVariable String categorySlug,
                                           @PathVariable UUID id) {
        if (!SLUG_TO_CATEGORY.containsKey(categorySlug)) {
            return ResponseEntity.notFound().build();
        }
        EventCard event = service.getEventById(id);

        String siteUrl = appConfig.getSiteUrl();
        String pageUrl = siteUrl + "/" + categorySlug + "/termin/" + id;

        String imageUrl = siteUrl + "/og-default.png";
        if (event.eventTypeId() != null) {
            try {
                EventTypeCard et = service.getEventTypeById(event.eventTypeId());
                if (et.thumbnailUrl() != null) {
                    imageUrl = siteUrl + et.thumbnailUrl();
                }
            } catch (NotFoundException ignored) {
                // The term is live but its type has been switched off, which is an ordinary thing for
                // the organizer to do and no reason to answer a crawler with 404. Catching
                // IllegalArgumentException here caught nothing at all -- PublicService throws
                // NotFoundException -- so sharing such a term produced no preview whatsoever.
                // Falling back to the default artwork is the whole point of this block.
            }
        }

        var sb = new StringBuilder();
        sb.append(event.startDate().format(DATE_FMT));
        if (event.endDate() != null) {
            sb.append(" – ").append(event.endDate().format(DATE_FMT));
        }
        if (event.location() != null) {
            sb.append(" | ").append(event.location());
        }
        if (event.price() != null) {
            sb.append(" | ").append(event.price()).append(" PLN");
        }
        if (event.description() != null) {
            sb.append(". ").append(truncate(event.description(), 120));
        }

        String jsonLd = buildEventJsonLd(event, imageUrl, pageUrl, siteUrl);
        return ogResponse(event.eventTypeName(), sb.toString(), imageUrl, pageUrl, jsonLd);
    }

    @GetMapping("/kadra/{id}")
    public ResponseEntity<String> instructorOg(@PathVariable UUID id) {
        PublicDtos.InstructorCard instructor = service.getInstructorById(id);

        String siteUrl = appConfig.getSiteUrl();
        String pageUrl = siteUrl + "/kadra/" + id;
        String imageUrl = instructor.photoUrl() != null ? siteUrl + instructor.photoUrl() : siteUrl + "/og-default.png";
        String name = instructor.firstName() + " " + instructor.lastName();

        String jsonLd = """
                {"@context":"https://schema.org","@type":"Person","name":"%s","givenName":"%s","familyName":"%s"%s,"jobTitle":"Instruktor","worksFor":{"@type":"Organization","name":"Fire Academy","url":"%s"},"url":"%s"}"""
                .formatted(
                        escapeJson(name), escapeJson(instructor.firstName()), escapeJson(instructor.lastName()),
                        instructor.bio() != null ? ",\"description\":\"" + escapeJson(truncate(instructor.bio(), 200)) + "\"" : "",
                        siteUrl, pageUrl);
        // No brand suffix here: ogResponse appends " | Fire Academy" to every title it renders.
        return ogResponse(name, truncate(instructor.bio(), 200), imageUrl, pageUrl, jsonLd);
    }

    @GetMapping("/")
    public ResponseEntity<String> homeOg() {
        String siteUrl = appConfig.getSiteUrl();
        String jsonLd = """
                [{"@context":"https://schema.org","@type":"SportsActivityLocation","name":"Fire Academy","description":"Szkoła sztuk walki w Katowicach — MMA, kickboxing, boks, zapasy i przygotowanie motoryczne. Trening personalny w małych grupach, realne efekty.","url":"%s","telephone":"+48534823667","address":{"@type":"PostalAddress","addressLocality":"Katowice","addressRegion":"śląskie","addressCountry":"PL"},"areaServed":["Katowice","Podlesie","Piotrowice","Bażantowo"],"keywords":"sztuki walki, MMA, kickboxing, boks, zapasy, trening personalny, przygotowanie motoryczne, Katowice"},{"@context":"https://schema.org","@type":"WebSite","name":"Fire Academy","url":"%s","inLanguage":"pl-PL"}]"""
                .formatted(siteUrl, siteUrl);
        return ogResponse(
                "Szkoła sztuk walki Katowice — MMA, kickboxing, boks",
                "Szkoła sztuk walki w Katowicach — MMA, kickboxing, boks, zapasy i przygotowanie motoryczne. Trening personalny w małych grupach, realne efekty.",
                siteUrl + "/og-default.png",
                siteUrl,
                jsonLd
        );
    }

    private ResponseEntity<String> ogResponse(String title, String description, String imageUrl, String pageUrl, String jsonLd) {
        String safeTitle = escapeHtml(title);
        String safeDesc = escapeHtml(description != null ? description : "Fire Academy");
        String jsonLdTag = jsonLd != null
                ? "<script type=\"application/ld+json\">" + jsonLd + "</script>\n"
                : "";
        String html = """
                <!DOCTYPE html>
                <html lang="pl">
                <head>
                <meta charset="utf-8"/>
                <meta name="robots" content="index, follow"/>
                <meta name="description" content="%s"/>
                <link rel="canonical" href="%s"/>
                <meta property="og:title" content="%s"/>
                <meta property="og:description" content="%s"/>
                <meta property="og:image" content="%s"/>
                <meta property="og:url" content="%s"/>
                <meta property="og:type" content="website"/>
                <meta property="og:site_name" content="Fire Academy"/>
                <meta property="og:locale" content="pl_PL"/>
                %s<meta http-equiv="refresh" content="0;url=%s"/>
                <title>%s | Fire Academy</title>
                </head>
                <body><p>Przekierowanie do <a href="%s">Fire Academy</a>...</p></body>
                </html>
                """.formatted(safeDesc, pageUrl, safeTitle, safeDesc, imageUrl, pageUrl, jsonLdTag, pageUrl, safeTitle, pageUrl);

        // Charset stated in the header, not only in the <meta> tag. Both work — HTML5 falls back to
        // the meta when the header is silent, and it sits in the first sixty bytes here, which is why
        // Polish titles have always rendered correctly in shared previews. But "correct because the
        // parser guesses right" is one fallback away from wrong, and a response that does not
        // describe its own bytes reads as ISO-8859-1 to everything that goes by the header alone:
        // curl, logs, and MockMvc — where a test asserting Polish output fails while production is
        // fine, which is a trap rather than a bug report.
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(html);
    }

    private String buildEventJsonLd(EventCard event, String imageUrl, String pageUrl, String siteUrl) {
        var sb = new StringBuilder();
        sb.append("{\"@context\":\"https://schema.org\",\"@type\":\"Event\"");
        sb.append(",\"name\":\"").append(escapeJson(event.eventTypeName())).append("\"");
        sb.append(",\"startDate\":\"").append(event.startDate());
        if (event.startTime() != null) sb.append("T").append(event.startTime());
        sb.append("\"");
        if (event.endDate() != null) {
            sb.append(",\"endDate\":\"").append(event.endDate());
            if (event.endTime() != null) sb.append("T").append(event.endTime());
            sb.append("\"");
        }
        if (event.location() != null) {
            sb.append(",\"location\":{\"@type\":\"Place\",\"name\":\"").append(escapeJson(event.location())).append("\"}");
        }
        if (event.description() != null) {
            sb.append(",\"description\":\"").append(escapeJson(truncate(event.description(), 200))).append("\"");
        }
        sb.append(",\"image\":\"").append(imageUrl).append("\"");
        if (event.price() != null) {
            sb.append(",\"offers\":{\"@type\":\"Offer\",\"price\":\"").append(event.price())
                    .append("\",\"priceCurrency\":\"PLN\",\"availability\":\"https://schema.org/")
                    .append(event.maxParticipants() != null && event.availableSpots() <= 0 ? "SoldOut" : "InStock")
                    .append("\",\"url\":\"").append(pageUrl).append("\"}");
        }
        sb.append(",\"organizer\":{\"@type\":\"Organization\",\"name\":\"Fire Academy\",\"url\":\"").append(siteUrl).append("\"}");
        sb.append(",\"eventAttendanceMode\":\"https://schema.org/OfflineEventAttendanceMode\"");
        sb.append(",\"eventStatus\":\"https://schema.org/EventScheduled\"");
        sb.append(",\"url\":\"").append(pageUrl).append("\"}");
        return sb.toString();
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String escapeJson(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")
                .replace("<", "\\u003c").replace(">", "\\u003e");
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return null;
        return text.length() <= maxLen ? text : text.substring(0, maxLen - 3) + "...";
    }
}
