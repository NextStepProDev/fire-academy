package pl.fireacademy.api.pub;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.fireacademy.api.auth.AuthDtos.MessageResponse;
import pl.fireacademy.api.pub.PublicDtos.*;
import pl.fireacademy.domain.event.EventCategory;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/public")
public class PublicController {

    private final PublicService service;

    public PublicController(PublicService service) {
        this.service = service;
    }

    @GetMapping("/instructors")
    public List<InstructorCard> getInstructors() {
        return service.getActiveInstructors();
    }

    @GetMapping("/event-types")
    public List<EventTypeCard> getEventTypes(@RequestParam EventCategory category) {
        return service.getActiveEventTypes(category);
    }

    @GetMapping("/events")
    public List<EventCard> getEvents(@RequestParam EventCategory category) {
        return service.getUpcomingEvents(category);
    }

    @PostMapping("/events/{eventId}/enroll")
    public ResponseEntity<MessageResponse> enroll(@PathVariable UUID eventId,
                                                   @Valid @RequestBody EnrollRequest request) {
        service.enroll(eventId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new MessageResponse("Zapis potwierdzony. Sprawdź swoją skrzynkę email."));
    }
}
