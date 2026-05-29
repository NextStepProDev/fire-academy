package pl.fireacademy.api.pub;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import pl.fireacademy.api.pub.PublicDtos.EventCard;
import pl.fireacademy.api.pub.PublicDtos.EventTypeCard;
import pl.fireacademy.config.AppConfig;
import pl.fireacademy.domain.event.EventCategory;

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

        return ogResponse(et.name(), truncate(et.description(), 200), imageUrl, pageUrl);
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
            } catch (IllegalArgumentException ignored) {}
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

        return ogResponse(event.eventTypeName(), sb.toString(), imageUrl, pageUrl);
    }

    @GetMapping("/kadra/{id}")
    public ResponseEntity<String> instructorOg(@PathVariable UUID id) {
        PublicDtos.InstructorCard instructor = service.getInstructorById(id);

        String siteUrl = appConfig.getSiteUrl();
        String pageUrl = siteUrl + "/kadra/" + id;
        String imageUrl = instructor.photoUrl() != null ? siteUrl + instructor.photoUrl() : siteUrl + "/og-default.png";
        String name = instructor.firstName() + " " + instructor.lastName();

        return ogResponse(name + " — Fire Academy", truncate(instructor.bio(), 200), imageUrl, pageUrl);
    }

    @GetMapping("/")
    public ResponseEntity<String> homeOg() {
        String siteUrl = appConfig.getSiteUrl();
        return ogResponse(
                "Fire Academy",
                "Treningi indywidualne i małe grupy. Obozy, szkolenia i kursy dla ambitnych sportowców.",
                siteUrl + "/og-default.png",
                siteUrl
        );
    }

    private ResponseEntity<String> ogResponse(String title, String description, String imageUrl, String pageUrl) {
        String safeTitle = escapeHtml(title);
        String safeDesc = escapeHtml(description != null ? description : "Fire Academy");
        String html = """
                <!DOCTYPE html>
                <html lang="pl">
                <head>
                <meta charset="utf-8"/>
                <meta property="og:title" content="%s"/>
                <meta property="og:description" content="%s"/>
                <meta property="og:image" content="%s"/>
                <meta property="og:url" content="%s"/>
                <meta property="og:type" content="website"/>
                <meta property="og:site_name" content="Fire Academy"/>
                <meta property="og:locale" content="pl_PL"/>
                <meta http-equiv="refresh" content="0;url=%s"/>
                <title>%s | Fire Academy</title>
                </head>
                <body><p>Przekierowanie do <a href="%s">Fire Academy</a>...</p></body>
                </html>
                """.formatted(safeTitle, safeDesc, imageUrl, pageUrl, pageUrl, safeTitle, pageUrl);

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return null;
        return text.length() <= maxLen ? text : text.substring(0, maxLen - 3) + "...";
    }
}
