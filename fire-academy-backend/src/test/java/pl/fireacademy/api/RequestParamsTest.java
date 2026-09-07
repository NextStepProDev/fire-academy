package pl.fireacademy.api;

import org.junit.jupiter.api.Test;
import pl.fireacademy.infrastructure.i18n.MessageService;

import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequestParamsTest {

    private final MessageService msg = mock(MessageService.class);

    RequestParamsTest() {
        when(msg.get(anyString())).thenReturn("nieprawidłowy miesiąc");
    }

    @Test
    void shouldDefaultToTheCurrentMonthWhenAbsent() {
        assertEquals(YearMonth.now(), RequestParams.parseMonth(null, msg));
        assertEquals(YearMonth.now(), RequestParams.parseMonth("  ", msg));
    }

    @Test
    void shouldAcceptTheMonthsThePanelActuallyNavigatesTo() {
        YearMonth now = YearMonth.now();
        assertEquals(now.plusMonths(1), RequestParams.parseMonth(now.plusMonths(1).toString(), msg));
        assertEquals(now.minusMonths(24), RequestParams.parseMonth(now.minusMonths(24).toString(), msg));
        // Both edges of the window are still legal — the rejection starts one month outside it.
        assertEquals(now.plusMonths(60), RequestParams.parseMonth(now.plusMonths(60).toString(), msg));
        assertEquals(now.minusMonths(60), RequestParams.parseMonth(now.minusMonths(60).toString(), msg));
    }

    @Test
    void shouldRejectAMalformedMonth() {
        assertThrows(IllegalArgumentException.class, () -> RequestParams.parseMonth("2026-13", msg));
        assertThrows(IllegalArgumentException.class, () -> RequestParams.parseMonth("wrzesień", msg));
    }

    /**
     * The one that matters. A far-future month is not a formatting mistake — it is a request for an
     * unbounded amount of work: TrainingCreditService.liveAppliedFor walks month by month up to it,
     * two queries a step, and on months that bill to nothing the credit balance never runs out to
     * stop it. Answered as a 400 rather than served slowly.
     */
    @Test
    void shouldRejectAMonthFarEnoughAwayToBeAWalkRatherThanALookup() {
        assertThrows(IllegalArgumentException.class, () -> RequestParams.parseMonth("9999-12", msg));
        assertThrows(IllegalArgumentException.class, () -> RequestParams.parseMonth("1900-01", msg));
        assertThrows(IllegalArgumentException.class,
            () -> RequestParams.parseMonth(YearMonth.now().plusMonths(61).toString(), msg));
    }
}
