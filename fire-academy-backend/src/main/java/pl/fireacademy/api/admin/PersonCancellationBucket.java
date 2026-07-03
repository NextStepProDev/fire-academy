package pl.fireacademy.api.admin;

import org.jspecify.annotations.Nullable;
import pl.fireacademy.domain.user.User;
import pl.fireacademy.infrastructure.mail.TrainingMailService.SessionLine;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Accumulates one person's cancelled sessions so a whole-day cancellation (day off / instructor day) sends them
 * ONE grouped email listing all their sessions, plus the total refund owed for the paid ones.
 */
final class PersonCancellationBucket {

    final User user;
    final List<SessionLine> lines = new ArrayList<>();
    BigDecimal refund = BigDecimal.ZERO;

    PersonCancellationBucket(User user) {
        this.user = user;
    }

    void add(String trainingName, LocalTime start, @Nullable LocalTime end, @Nullable BigDecimal price) {
        lines.add(new SessionLine(trainingName, start, end));
        if (price != null) {
            refund = refund.add(price);
        }
    }

    @Nullable
    BigDecimal refundOrNull() {
        return refund.signum() > 0 ? refund : null;
    }
}
