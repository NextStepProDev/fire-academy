package pl.fireacademy.domain.adminnote;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One noted group-session occurrence: which slot, which day. Never carries the text -- the marker
 * query answers "is there a note here" and nothing more.
 */
public record SessionMarker(UUID slotId, LocalDate date) {
}
