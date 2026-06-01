package pl.fireacademy.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import pl.fireacademy.infrastructure.i18n.MessageService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private final MessageService msg = mock(MessageService.class);
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(msg);

    @Test
    void shouldReturn413WithLocalizedMessageWhenUploadSizeExceeded() {
        when(msg.get("file.too.large")).thenReturn("Plik jest za duży. Maksymalny rozmiar to 10 MB.");
        var ex = new MaxUploadSizeExceededException(10 * 1024 * 1024);

        var response = handler.handleMaxUploadSize(ex);

        assertEquals(HttpStatus.CONTENT_TOO_LARGE, response.getStatusCode());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals("PAYLOAD_TOO_LARGE", body.get("code"));
        assertEquals("Plik jest za duży. Maksymalny rozmiar to 10 MB.", body.get("message"));
        assertNotNull(body.get("timestamp"));
    }

    @Test
    void shouldReturn413WhenUploadSizeExceededWithCause() {
        when(msg.get("file.too.large")).thenReturn("Plik jest za duży. Maksymalny rozmiar to 10 MB.");
        var cause = new RuntimeException("size exceeds configured maximum");
        var ex = new MaxUploadSizeExceededException(50 * 1024 * 1024, cause);

        var response = handler.handleMaxUploadSize(ex);

        assertEquals(HttpStatus.CONTENT_TOO_LARGE, response.getStatusCode());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals("PAYLOAD_TOO_LARGE", body.get("code"));
    }

    @Test
    void shouldReturn400ForIllegalArgument() {
        var response = handler.handleIllegalArgument(new IllegalArgumentException("bad input"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals("BAD_REQUEST", body.get("code"));
        assertEquals("bad input", body.get("message"));
    }

    @Test
    void shouldReturn409ForIllegalState() {
        var response = handler.handleIllegalState(new IllegalStateException("conflict"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals("CONFLICT", body.get("code"));
    }

    @Test
    void shouldReturn500ForUnexpectedException() {
        var response = handler.handleGeneral(new RuntimeException("unexpected"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals("INTERNAL_ERROR", body.get("code"));
    }
}
