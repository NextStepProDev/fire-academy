package pl.fireacademy.infrastructure.youtube;

import org.junit.jupiter.api.Test;
import pl.fireacademy.domain.training.YouTubeMetadata;
import pl.fireacademy.domain.training.YouTubeMetadata.Status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Parsing only — fetch() goes over the network and stays out of unit tests. A missing or JSON-null
 * field has to come back as Java null, not as an empty string or the text "null": that is what
 * decides between a title the form pre-fills and "YouTube did not tell us".
 */
class YouTubeOEmbedClientTest {

    @Test
    void shouldReadTitleAndAuthorWhenBothArePresent() {
        YouTubeMetadata meta = YouTubeOEmbedClient.parse(
            "{\"title\":\"Boks — praca nóg\",\"author_name\":\"Fire Academy\",\"type\":\"video\"}");

        assertEquals(Status.OK, meta.status());
        assertEquals("Boks — praca nóg", meta.title());
        assertEquals("Fire Academy", meta.authorName());
    }

    @Test
    void shouldLeaveAuthorEmptyWhenItIsMissingOrNull() {
        assertNull(YouTubeOEmbedClient.parse("{\"title\":\"Kopnięcie okrężne\"}").authorName());
        assertNull(YouTubeOEmbedClient.parse("{\"title\":\"Kopnięcie okrężne\",\"author_name\":null}").authorName());
    }

    @Test
    void shouldReportUnknownWhenTitleIsMissingNullOrBlank() {
        assertEquals(Status.UNKNOWN, YouTubeOEmbedClient.parse("{\"author_name\":\"x\"}").status());
        assertEquals(Status.UNKNOWN, YouTubeOEmbedClient.parse("{\"title\":null}").status());
        assertEquals(Status.UNKNOWN, YouTubeOEmbedClient.parse("{\"title\":\"   \"}").status());
    }
}
