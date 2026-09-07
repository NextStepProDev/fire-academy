package pl.fireacademy.api;

import org.jspecify.annotations.Nullable;
import pl.fireacademy.infrastructure.i18n.MessageService;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/** Helper for parsing request parameters with a readable 400 error instead of a 500. */
public final class RequestParams {

    /**
     * How far from today a requested month may sit, in either direction.
     * <p>
     * This is a ceiling on WORK, not a business rule. Downstream code walks month by month from a
     * subscription's start to the month asked for — {@code TrainingCreditService.liveAppliedFor}
     * does exactly that, two database queries per step — and the only other exit is an exhausted
     * credit balance. A month whose bill comes to zero (a slot priced at nothing, or one whose
     * scheduled deactivation has closed every remaining session) subtracts nothing, so on those the
     * balance never runs out and the walk goes the whole distance. {@code ?month=9999-12} then
     * costs a request thread and one of the five pooled connections for minutes, and it does not
     * fail — it hangs, which is worse.
     * <p>
     * Sixty months either way is far beyond anything the panel or the public catalogue navigates to
     * (both step a month at a time from today) and still bounds that walk at ~120 steps. Widen it
     * only alongside a real bound inside the walk itself.
     */
    private static final int MAX_MONTHS_FROM_NOW = 60;

    private RequestParams() {}

    /**
     * Parses a month 'YYYY-MM'; empty → current; invalid format or absurdly far from today →
     * IllegalArgumentException (HTTP 400).
     */
    public static YearMonth parseMonth(@Nullable String month, MessageService msg) {
        if (month == null || month.isBlank()) {
            return YearMonth.now();
        }
        YearMonth parsed;
        try {
            parsed = YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(msg.get("validation.month.invalid"));
        }
        YearMonth now = YearMonth.now();
        if (parsed.isBefore(now.minusMonths(MAX_MONTHS_FROM_NOW))
                || parsed.isAfter(now.plusMonths(MAX_MONTHS_FROM_NOW))) {
            throw new IllegalArgumentException(msg.get("validation.month.out.of.range"));
        }
        return parsed;
    }
}
