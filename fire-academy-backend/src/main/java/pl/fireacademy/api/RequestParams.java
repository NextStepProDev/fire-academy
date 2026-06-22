package pl.fireacademy.api;

import org.jspecify.annotations.Nullable;
import pl.fireacademy.infrastructure.i18n.MessageService;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/** Helper for parsing request parameters with a readable 400 error instead of a 500. */
public final class RequestParams {

    private RequestParams() {}

    /** Parses a month 'YYYY-MM'; empty → current; invalid format → IllegalArgumentException (HTTP 400). */
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
