package pl.fireacademy.domain.event;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class EventTest {

    @Test
    void shouldReturnEventTypeNameAsDisplayName() {
        EventType eventType = new EventType(EventCategory.TRAINING, "Trening personalny");
        Event event = new Event(EventCategory.TRAINING, eventType, LocalDate.now().plusDays(7));

        assertEquals("Trening personalny", event.getDisplayName());
    }

    @Test
    void shouldReturnCustomNameAsDisplayName() {
        Event event = new Event(EventCategory.CAMP, "Obóz specjalny", LocalDate.now().plusDays(30));

        assertEquals("Obóz specjalny", event.getDisplayName());
    }

    @Test
    void shouldConvertToCustomName() {
        EventType eventType = new EventType(EventCategory.TRAINING, "Trening");
        Event event = new Event(EventCategory.TRAINING, eventType, LocalDate.now().plusDays(7));

        event.convertToCustomName("Trening specjalny");

        assertEquals("Trening specjalny", event.getDisplayName());
        assertNull(event.getEventType());
        assertEquals("Trening specjalny", event.getCustomName());
    }

    @Test
    void shouldSetEventType() {
        Event event = new Event(EventCategory.CAMP, "Obóz", LocalDate.now().plusDays(30));
        EventType eventType = new EventType(EventCategory.CAMP, "Nowy typ");

        event.setEventType(eventType);

        assertEquals("Nowy typ", event.getDisplayName());
        assertNull(event.getCustomName());
        assertNotNull(event.getEventType());
    }

    @Test
    void shouldDefaultToActive() {
        Event event = new Event(EventCategory.TRAINING, "Trening", LocalDate.now().plusDays(7));

        assertTrue(event.isActive());
    }

    @Test
    void shouldToggleActive() {
        Event event = new Event(EventCategory.TRAINING, "Trening", LocalDate.now().plusDays(7));

        event.setActive(false);

        assertFalse(event.isActive());
    }
}
