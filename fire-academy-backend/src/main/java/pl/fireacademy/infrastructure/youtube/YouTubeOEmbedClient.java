package pl.fireacademy.infrastructure.youtube;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pl.fireacademy.domain.training.YouTubeMetadata;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Reads a clip's title from YouTube's public oEmbed endpoint.
 * <p>
 * No API key and no quota: oEmbed is the documented public way to resolve a video's title and
 * channel. The alternative, the Data API, would mean a key in the environment and a daily budget
 * for something that runs a handful of times a week.
 * <p>
 * <b>The request is built from the parsed video id, never from the pasted string.</b> Otherwise
 * this class would fetch whatever URL an admin typed — including a machine inside the private
 * network — which is a request-forgery hole rather than a convenience.
 */
@Component
public class YouTubeOEmbedClient {

    private static final Logger log = LoggerFactory.getLogger(YouTubeOEmbedClient.class);

    /** Short on purpose: this runs while somebody watches a form field, and it is optional help. */
    private static final Duration TIMEOUT = Duration.ofSeconds(4);

    /**
     * Built here rather than injected: a plain mapper for a third-party payload, independent of the
     * application's Jackson configuration (Jackson 3, like the one Spring Boot 4 auto-configures).
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient http;

    public YouTubeOEmbedClient() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                // A shortened link would be a redirect to somewhere unvetted; we only ever call the
                // canonical endpoint, so following redirects buys nothing and costs control.
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public YouTubeMetadata fetch(String videoKey) {
        URI uri = URI.create("https://www.youtube.com/oembed?format=json&url="
                + URLEncoder.encode("https://www.youtube.com/watch?v=" + videoKey, StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build();

        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            // 401 for private, 403 for embedding disabled, 404 for gone. All the same to the person
            // pasting the link: this clip will not play for their client.
            if (response.statusCode() == 401 || response.statusCode() == 403 || response.statusCode() == 404) {
                return YouTubeMetadata.unavailable();
            }
            if (response.statusCode() != 200) {
                log.warn("YOUTUBE_OEMBED_UNEXPECTED_STATUS status={}", response.statusCode());
                return YouTubeMetadata.unknown();
            }
            return parse(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return YouTubeMetadata.unknown();
        } catch (Exception e) {
            // Never fatal: the title is a convenience, and the form still saves without it.
            log.warn("YOUTUBE_OEMBED_FAILED message={}", e.getMessage());
            return YouTubeMetadata.unknown();
        }
    }

    /** Reads the oEmbed body. Without a usable title the answer is "unknown", not an empty title. */
    static YouTubeMetadata parse(String json) {
        JsonNode body = MAPPER.readTree(json);
        String title = body.path("title").asString(null);
        if (title == null || title.isBlank()) {
            return YouTubeMetadata.unknown();
        }
        return YouTubeMetadata.ok(title, body.path("author_name").asString(null));
    }
}
