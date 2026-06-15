package pl.fireacademy.api.pub;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.fireacademy.api.RequestParams;
import pl.fireacademy.api.pub.PublicDtos.*;
import pl.fireacademy.domain.event.EventCategory;
import pl.fireacademy.infrastructure.i18n.MessageService;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/public")
public class PublicController {

    private static final CacheControl LIST_CACHE = CacheControl.maxAge(60, TimeUnit.SECONDS).cachePublic();
    private static final CacheControl DETAIL_CACHE = CacheControl.maxAge(300, TimeUnit.SECONDS).cachePublic();

    private final PublicService service;
    private final MessageService msg;

    public PublicController(PublicService service, MessageService msg) {
        this.service = service;
        this.msg = msg;
    }

    @GetMapping("/instructors")
    public ResponseEntity<List<InstructorCard>> getInstructors(@RequestParam EventCategory category) {
        return ResponseEntity.ok().cacheControl(LIST_CACHE).body(service.getActiveInstructors(category));
    }

    @GetMapping("/event-types")
    public ResponseEntity<List<EventTypeCard>> getEventTypes(@RequestParam EventCategory category) {
        return ResponseEntity.ok().cacheControl(LIST_CACHE).body(service.getActiveEventTypes(category));
    }

    @GetMapping("/events")
    public ResponseEntity<List<EventCard>> getEvents(@RequestParam EventCategory category) {
        return ResponseEntity.ok().cacheControl(LIST_CACHE).body(service.getUpcomingEvents(category));
    }

    @GetMapping("/training-slots")
    public ResponseEntity<List<TrainingSlotCard>> getTrainingSlots(
            @RequestParam(required = false) @Nullable String month) {
        var ym = RequestParams.parseMonth(month, msg);
        return ResponseEntity.ok().cacheControl(LIST_CACHE).body(service.getTrainingSlots(ym));
    }

    @GetMapping("/instructors/{id}")
    public ResponseEntity<InstructorCard> getInstructor(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(DETAIL_CACHE).body(service.getInstructorById(id));
    }

    @GetMapping("/event-types/{id}")
    public ResponseEntity<EventTypeCard> getEventType(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(DETAIL_CACHE).body(service.getEventTypeById(id));
    }

    @GetMapping("/events/{eventId}")
    public ResponseEntity<EventCard> getEvent(@PathVariable UUID eventId) {
        return ResponseEntity.ok().cacheControl(DETAIL_CACHE).body(service.getEventById(eventId));
    }
}
