package pl.fireacademy.api;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import pl.fireacademy.infrastructure.i18n.MessageService;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final MessageService msg;

    public GlobalExceptionHandler(MessageService msg) {
        this.msg = msg;
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }

    /**
     * A refused refresh token is 401, not the 400 its supertype would get — the frontend ends a
     * session on 401/403 and on nothing else, so a dead token answered with 400 left the app
     * convinced the user was still logged in while nothing worked. Declared above the
     * IllegalArgumentException handler for readability only; Spring picks the closest match by class
     * hierarchy, not by order.
     */
    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidRefreshToken(InvalidRefreshTokenException e) {
        return error(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException e) {
        return error(HttpStatus.CONFLICT, "CONFLICT", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        String errors = e.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining(", "));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", errors);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequestParam(Exception e) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableBody(HttpMessageNotReadableException e) {
        // Malformed/incomplete JSON body (e.g. missing required primitive field, wrong type).
        // e.getMessage() can be verbose and leak internal details — return a generic message.
        log.warn("Malformed request body: {}", e.getMessage());
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", msg.get("error.request.body.invalid"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", msg.get("error.method.not.allowed"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException e) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", msg.get("error.resource.not.found"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("Upload size exceeded: {}", e.getMessage());
        return error(HttpStatus.CONTENT_TOO_LARGE, "PAYLOAD_TOO_LARGE", msg.get("file.too.large"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception e) {
        log.error("Unexpected error", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal server error");
    }

    /**
     * The one place an error body is built — and the reason it is not {@code Map.of}.
     * <p>
     * {@code Map.of} rejects a null value, so an exception carrying no message (a bare
     * {@code new IllegalStateException()}, or one thrown from a library that did not bother) blew up
     * the handler itself. What reached the client was then a container-generated 500 with none of
     * the {@code code}/{@code message}/{@code timestamp} shape the frontend reads — a missing
     * sentence turning a 400 into an unexplained server error.
     */
    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, @Nullable String message) {
        String body = message == null || message.isBlank() ? msg.get("error.unexpected") : message;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", code);
        payload.put("message", body);
        payload.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(status).body(payload);
    }
}
