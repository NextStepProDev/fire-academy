package pl.fireacademy.api.trainingcalendar;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Shared response shapes for the 1-on-1 training calendar.
 * <p>
 * Both the coach view ({@code /api/admin/...}) and the client view ({@code /api/user/my-training/...})
 * return the SAME records — that is the whole point of keeping the two controllers in one package.
 * The frontend renders one component for both roles, so any divergence here would immediately show up
 * as a second code path on the client.
 */
public final class TrainingCalendarDtos {

    private TrainingCalendarDtos() {}

    /** One row of the coach's roster. Unread counters and the overtraining flag land here later. */
    public record AthleteSummary(
            UUID id,
            String firstName,
            String lastName,
            String email,
            @Nullable String avatarUrl
    ) {}
}
