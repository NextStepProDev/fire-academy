package pl.fireacademy.api.admin;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.fireacademy.api.NotFoundException;
import pl.fireacademy.api.admin.EventDtos.*;
import pl.fireacademy.config.CacheConfig;
import pl.fireacademy.domain.enrollment.Enrollment;
import pl.fireacademy.domain.enrollment.EnrollmentRepository;
import pl.fireacademy.domain.event.*;
import pl.fireacademy.infrastructure.i18n.MessageService;
import pl.fireacademy.infrastructure.mail.EnrollmentMailService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class AdminEventService {

    private final EventRepository eventRepository;
    private final EventTypeRepository eventTypeRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentMailService enrollmentMailService;
    private final MessageService msg;

    public AdminEventService(EventRepository eventRepository,
                             EventTypeRepository eventTypeRepository,
                             EnrollmentRepository enrollmentRepository,
                             EnrollmentMailService enrollmentMailService,
                             MessageService msg) {
        this.eventRepository = eventRepository;
        this.eventTypeRepository = eventTypeRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.enrollmentMailService = enrollmentMailService;
        this.msg = msg;
    }

    @Transactional(readOnly = true)
    public List<EventResponse> getAll(EventCategory category) {
        return eventRepository.findByCategoryOrderByStartDateAsc(category).stream()
                .map(this::toResponse)
                .toList();
    }

    @Caching(evict = {
            @CacheEvict(value = CacheConfig.EVENTS, allEntries = true),
            @CacheEvict(value = CacheConfig.EVENT, allEntries = true)
    })
    @Transactional
    public EventResponse create(CreateEventRequest request) {
        if (request.startDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException(msg.get("event.date.past"));
        }

        Event event;

        if (request.eventTypeId() != null) {
            var eventType = eventTypeRepository.findById(request.eventTypeId())
                    .orElseThrow(() -> new NotFoundException(msg.get("eventtype.not.found")));
            event = new Event(request.category(), eventType, request.startDate());
        } else if (request.customName() != null && !request.customName().isBlank()) {
            event = new Event(request.category(), request.customName(), request.startDate());
        } else {
            throw new IllegalArgumentException(msg.get("event.name.required"));
        }

        event.setDescription(request.description());
        event.setEndDate(request.endDate());
        event.setStartTime(request.startTime());
        event.setEndTime(request.endTime());
        event.setLocation(request.location());
        event.setPrice(request.price());
        event.setMaxParticipants(request.maxParticipants());
        return toResponse(eventRepository.save(event));
    }

    @Caching(evict = {
            @CacheEvict(value = CacheConfig.EVENTS, allEntries = true),
            @CacheEvict(value = CacheConfig.EVENT, allEntries = true)
    })
    @Transactional
    public EventResponse update(UUID id, UpdateEventRequest request) {
        if (request.startDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException(msg.get("event.date.past"));
        }

        var event = findOrThrow(id);
        var changes = new ArrayList<FieldChange>();

        String oldName = event.getDisplayName();
        if (request.eventTypeId() != null) {
            var eventType = eventTypeRepository.findById(request.eventTypeId())
                    .orElseThrow(() -> new NotFoundException(msg.get("eventtype.not.found")));
            event.setEventType(eventType);
            addChange(changes, msg.get("email.change.name"), oldName, eventType.getName());
        } else if (request.customName() != null && !request.customName().isBlank()) {
            event.convertToCustomName(request.customName());
            addChange(changes, msg.get("email.change.name"), oldName, request.customName());
        }

        addChange(changes, msg.get("email.change.date"), fmt(event.getStartDate()), fmt(request.startDate()));
        event.setStartDate(request.startDate());

        addChange(changes, msg.get("email.change.endDate"), fmt(event.getEndDate()), fmt(request.endDate()));
        event.setEndDate(request.endDate());

        addChange(changes, msg.get("email.change.startTime"), fmt(event.getStartTime()), fmt(request.startTime()));
        event.setStartTime(request.startTime());

        addChange(changes, msg.get("email.change.endTime"), fmt(event.getEndTime()), fmt(request.endTime()));
        event.setEndTime(request.endTime());

        addChange(changes, msg.get("email.change.location"), orEmpty(event.getLocation()), orEmpty(request.location()));
        event.setLocation(request.location());

        addChange(changes, msg.get("email.change.price"), fmtPrice(event.getPrice()), fmtPrice(request.price()));
        event.setPrice(request.price());

        addChange(changes, msg.get("email.change.maxParticipants"), fmt(event.getMaxParticipants()), fmt(request.maxParticipants()));
        event.setMaxParticipants(request.maxParticipants());

        event.setDescription(request.description());

        var saved = eventRepository.save(event);

        if (!changes.isEmpty()) {
            var schedule = EnrollmentMailService.formatSchedule(saved);
            var enrollments = enrollmentRepository.findByEventIdOrderByCreatedAtDesc(id);
            for (Enrollment enrollment : enrollments) {
                enrollmentMailService.sendEventModificationNotification(
                        enrollment.displayEmail(), enrollment.displayFirstName(),
                        saved.getDisplayName(), schedule, changes,
                        saved.getCategory(), saved.getId().toString());
            }
            enrollmentMailService.sendEventModificationAdminNotification(
                    saved.getDisplayName(), schedule, changes,
                    saved.getCategory(), saved.getId().toString());
        }

        return toResponse(saved);
    }

    @Caching(evict = {
            @CacheEvict(value = CacheConfig.EVENTS, allEntries = true),
            @CacheEvict(value = CacheConfig.EVENT, allEntries = true)
    })
    @Transactional
    public EventResponse toggleActive(UUID id) {
        var event = findOrThrow(id);
        event.setActive(!event.isActive());
        return toResponse(eventRepository.save(event));
    }

    @Caching(evict = {
            @CacheEvict(value = CacheConfig.EVENTS, allEntries = true),
            @CacheEvict(value = CacheConfig.EVENT, allEntries = true)
    })
    @Transactional
    public void delete(UUID id, boolean force) {
        var event = findOrThrow(id);
        var enrollments = enrollmentRepository.findByEventIdOrderByCreatedAtDesc(id);
        if (!enrollments.isEmpty()) {
            if (!force) {
                throw new IllegalStateException(msg.get("event.has.enrollments"));
            }
            // Past event (archive) → silent deletion of enrollments (the event already happened).
            // Future/active event → notify the enrolled about the cancellation before we delete the enrollments.
            if (!event.isPastOn(LocalDate.now())) {
                var schedule = EnrollmentMailService.formatSchedule(event);
                for (Enrollment enrollment : enrollments) {
                    enrollmentMailService.sendEnrollmentDeletionNotification(
                            enrollment.displayEmail(), enrollment.displayFirstName(),
                            event.getDisplayName(), schedule,
                            event.getCategory(), event.getId().toString());
                }
            }
            enrollmentRepository.deleteByEventId(id);
        }
        eventRepository.delete(event);
    }

    private Event findOrThrow(UUID id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(msg.get("event.not.found")));
    }

    private EventResponse toResponse(Event e) {
        long enrollmentCount = enrollmentRepository.countByEventId(e.getId());
        var et = e.getEventType();
        return new EventResponse(
                e.getId(),
                et != null ? et.getId() : null,
                e.getDisplayName(),
                e.getDescription(),
                e.getStartDate(), e.getEndDate(), e.getStartTime(), e.getEndTime(), e.getLocation(),
                e.getPrice(), e.getMaxParticipants(),
                enrollmentCount, e.isActive(), e.getCreatedAt()
        );
    }

    private void addChange(List<FieldChange> changes, String field, String oldVal, String newVal) {
        if (!Objects.equals(oldVal, newVal)) {
            changes.add(new FieldChange(field, oldVal.isEmpty() ? "–" : oldVal, newVal.isEmpty() ? "–" : newVal));
        }
    }

    private static String fmt(Object val) { return val != null ? val.toString() : ""; }
    private static String orEmpty(String val) { return val != null ? val : ""; }
    private static String fmtPrice(BigDecimal val) { return val != null ? val.stripTrailingZeros().toPlainString() + " PLN" : ""; }
}
