package pl.fireacademy.api;

import org.jspecify.annotations.Nullable;
import pl.fireacademy.infrastructure.i18n.MessageService;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/** Pomocnicze parsowanie parametrów żądania z czytelnym błędem 400 zamiast 500. */
public final class RequestParams {

    private RequestParams() {}

    /** Parsuje miesiąc 'RRRR-MM'; pusty → bieżący; zły format → IllegalArgumentException (HTTP 400). */
    public static YearMonth parseMonth(@Nullable String month, MessageService msg) {
        if (month == null || month.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(msg.get("validation.month.invalid"));
        }
    }
}
