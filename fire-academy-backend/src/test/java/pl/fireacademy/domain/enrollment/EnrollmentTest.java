package pl.fireacademy.domain.enrollment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.fireacademy.domain.event.Event;
import pl.fireacademy.domain.event.EventCategory;
import pl.fireacademy.domain.user.User;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EnrollmentTest {

    private Enrollment enrollment;

    @BeforeEach
    void setUp() throws Exception {
        Event event = new Event(EventCategory.TRAINING, "Trening", LocalDate.now().plusDays(7));
        enrollment = new Enrollment(event, "Jan", "Kowalski", "jan@test.com", "123456789", "Notatka", false);
        Field idField = Enrollment.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(enrollment, UUID.randomUUID());
    }

    @Test
    void shouldNotBeAnonymizedInitially() {
        assertFalse(enrollment.isAnonymized());
    }

    @Test
    void shouldAnonymizeData() {
        enrollment.anonymize();

        assertEquals("Dane", enrollment.getFirstName());
        assertEquals("usunięte", enrollment.getLastName());
        assertTrue(enrollment.getEmail().endsWith("@usuniety.rodo"));
        assertEquals("000000000", enrollment.getPhone());
        assertNull(enrollment.getNote());
        assertTrue(enrollment.isAnonymized());
    }

    @Test
    void shouldCopySnapshotFromUserAndDetachOnAnonymize() {
        Event event = new Event(EventCategory.CAMP, "Obóz", LocalDate.now().plusDays(7));
        User user = new User("anna@test.com", "Anna", "Nowak", "500100200");
        Enrollment e = Enrollment.forUser(event, user, "notka", false);

        assertSame(user, e.getUser());
        assertEquals("Anna", e.getFirstName());
        assertEquals("anna@test.com", e.getEmail());
        assertEquals("500100200", e.getPhone());

        e.anonymize();

        assertNull(e.getUser());
        assertTrue(e.isAnonymized());
    }

    @Test
    void shouldPreserveAdminFlag() {
        Event event = new Event(EventCategory.TRAINING, "Trening", LocalDate.now().plusDays(7));
        Enrollment adminEnrollment = new Enrollment(event, "Admin", "User", "admin@test.com", "111111111", null, true);

        assertTrue(adminEnrollment.isAddedByAdmin());
    }

    @Test
    void shouldReturnBasicProperties() {
        assertEquals("Jan", enrollment.getFirstName());
        assertEquals("Kowalski", enrollment.getLastName());
        assertEquals("jan@test.com", enrollment.getEmail());
        assertEquals("123456789", enrollment.getPhone());
        assertEquals("Notatka", enrollment.getNote());
        assertFalse(enrollment.isAddedByAdmin());
    }
}
