package pl.fireacademy.api.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.fireacademy.api.admin.EventDtos.*;
import pl.fireacademy.domain.enrollment.EnrollmentRepository;
import pl.fireacademy.domain.event.*;
import pl.fireacademy.infrastructure.i18n.MessageService;

import java.util.List;
import java.util.UUID;

@Service
public class AdminEventService {

    private final EventRepository eventRepository;
    private final EventTypeRepository eventTypeRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final MessageService msg;

    public AdminEventService(EventRepository eventRepository,
                             EventTypeRepository eventTypeRepository,
                             EnrollmentRepository enrollmentRepository,
                             MessageService msg) {
        this.eventRepository = eventRepository;
        this.eventTypeRepository = eventTypeRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.msg = msg;
    }

    @Transactional(readOnly = true)
    public List<EventResponse> getAll(EventCategory category) {
        return eventRepository.findByEventType_CategoryOrderByStartDateDesc(category).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public EventResponse create(CreateEventRequest request) {
        EventType eventType;

        if (request.eventTypeId() != null) {
            eventType = eventTypeRepository.findById(request.eventTypeId())
                    .orElseThrow(() -> new IllegalArgumentException(msg.get("eventtype.not.found")));
        } else if (request.customName() != null && !request.customName().isBlank()) {
            eventType = eventTypeRepository.findByNameAndCategory(request.customName(), request.category())
                    .orElseGet(() -> {
                        int maxOrder = eventTypeRepository.findTopByCategoryOrderByDisplayOrderDesc(request.category())
                                .map(EventType::getDisplayOrder)
                                .orElse(-1);
                        var et = new EventType(request.category(), request.customName());
                        et.setDisplayOrder(maxOrder + 1);
                        return eventTypeRepository.save(et);
                    });
        } else {
            throw new IllegalArgumentException(msg.get("event.name.required"));
        }

        var event = new Event(eventType, request.startDate());
        event.setEndDate(request.endDate());
        event.setStartTime(request.startTime());
        event.setEndTime(request.endTime());
        event.setLocation(request.location());
        event.setPrice(request.price());
        event.setMaxParticipants(request.maxParticipants());
        return toResponse(eventRepository.save(event));
    }

    @Transactional
    public EventResponse update(UUID id, UpdateEventRequest request) {
        var event = findOrThrow(id);
        event.setStartDate(request.startDate());
        event.setEndDate(request.endDate());
        event.setStartTime(request.startTime());
        event.setEndTime(request.endTime());
        event.setLocation(request.location());
        event.setPrice(request.price());
        event.setMaxParticipants(request.maxParticipants());
        return toResponse(eventRepository.save(event));
    }

    @Transactional
    public EventResponse toggleActive(UUID id) {
        var event = findOrThrow(id);
        event.setActive(!event.isActive());
        return toResponse(eventRepository.save(event));
    }

    @Transactional
    public void delete(UUID id) {
        var event = findOrThrow(id);
        long enrollments = enrollmentRepository.countByEventId(id);
        if (enrollments > 0) {
            throw new IllegalStateException(msg.get("event.has.enrollments"));
        }
        eventRepository.delete(event);
    }

    private Event findOrThrow(UUID id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(msg.get("event.not.found")));
    }

    private EventResponse toResponse(Event e) {
        long enrollmentCount = enrollmentRepository.countByEventId(e.getId());
        return new EventResponse(
                e.getId(), e.getEventType().getId(), e.getEventType().getName(),
                e.getStartDate(), e.getEndDate(), e.getStartTime(), e.getEndTime(), e.getLocation(),
                e.getPrice(), e.getMaxParticipants(),
                enrollmentCount, e.isActive(), e.getCreatedAt()
        );
    }
}
