package pl.fireacademy.api.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.fireacademy.BaseIntegrationTest;
import pl.fireacademy.domain.enrollment.EnrollmentRepository;
import pl.fireacademy.domain.event.Event;
import pl.fireacademy.domain.event.EventCategory;
import pl.fireacademy.domain.event.EventRepository;
import pl.fireacademy.domain.user.User;
import pl.fireacademy.domain.user.UserRole;
import pl.fireacademy.infrastructure.i18n.MessageService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Event capacity under concurrency pressure. Two parallel attempts to enroll in an event
 * with a single free spot must not both succeed (overbooking). The protection is
 * {@code EventRepository.findByIdForUpdate} with {@code @Lock(PESSIMISTIC_WRITE)} — the event
 * row is locked for the duration of the transaction, so the second thread waits and, after the
 * first commits, already sees a full event. This test guards against anyone removing that lock.
 */
class UserEnrollmentConcurrencyIntegrationTest extends BaseIntegrationTest {

    @Autowired private UserEnrollmentService enrollmentService;
    @Autowired private EventRepository eventRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private MessageService msg;

    private User enrollableUser(String email) {
        User user = new User(email, "Jan", "Kowalski", "123456789");
        user.setPasswordHash("$2a$12$dummyhash");
        user.setRole(UserRole.USER);
        user.markEmailVerified();
        user.setPrivacyAcceptedAt(Instant.now());
        return userRepository.save(user);
    }

    @Test
    void shouldNotOverbookLastSpotUnderConcurrentEnrollments() throws Exception {
        Event event = new Event(EventCategory.TRAINING, "Trening 1:1", LocalDate.now().plusDays(10));
        event.setStartTime(LocalTime.of(10, 0));
        event.setMaxParticipants(1);
        UUID eventId = eventRepository.save(event).getId();

        UUID[] userIds = { enrollableUser("race-1@test.com").getId(), enrollableUser("race-2@test.com").getId() };
        String fullMessage = msg.get("enrollment.event.full");

        AtomicInteger successes = new AtomicInteger();
        AtomicInteger fullRejections = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        for (UUID userId : userIds) {
            pool.submit(() -> {
                try {
                    ready.countDown();
                    go.await();
                    enrollmentService.enroll(userId, new UserEnrollmentDtos.EnrollRequest(eventId, null));
                    successes.incrementAndGet();
                } catch (IllegalStateException e) {
                    if (fullMessage.equals(e.getMessage())) {
                        fullRejections.incrementAndGet();
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS), "Wątki nie wystartowały");
        go.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS), "Zapisy nie zakończyły się w czasie");

        assertEquals(1, successes.get(), "Dokładnie jeden zapis może się powieść");
        assertEquals(1, fullRejections.get(), "Drugi zapis musi zostać odrzucony jako komplet");
        assertEquals(1, enrollmentRepository.countByEventId(eventId), "W bazie może być tylko jeden zapis");
    }
}
