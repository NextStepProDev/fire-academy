package pl.fireacademy.api;

/**
 * Rzucany, gdy żądany zasób nie istnieje (lub jest nieaktywny/ukryty dla żądającego).
 * Mapowany na HTTP 404 w {@link GlobalExceptionHandler}.
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
