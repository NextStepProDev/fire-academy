package pl.fireacademy.api.pub;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.fireacademy.api.pub.PublicDtos.*;
import pl.fireacademy.config.CacheConfig;
import pl.fireacademy.domain.enrollment.Enrollment;
import pl.fireacademy.domain.enrollment.EnrollmentRepository;
import pl.fireacademy.domain.event.*;
import pl.fireacademy.domain.instructor.Instructor;
import pl.fireacademy.domain.instructor.InstructorRepository;
import pl.fireacademy.infrastructure.i18n.MessageService;
import pl.fireacademy.infrastructure.mail.EnrollmentMailService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PublicService {

    private final InstructorRepository instructorRepository;
    private final EventTypeRepository eventTypeRepository;
    private final EventRepository eventRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentMailService enrollmentMailService;
    private final MessageService msg;

    public PublicService(InstructorRepository instructorRepository,
                         EventTypeRepository eventTypeRepository,
                         EventRepository eventRepository,
                         EnrollmentRepository enrollmentRepository,
                         EnrollmentMailService enrollmentMailService,
                         MessageService msg) {
        this.instructorRepository = instructorRepository;
        this.eventTypeRepository = eventTypeRepository;
        this.eventRepository = eventRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.enrollmentMailService = enrollmentMailService;
        this.msg = msg;
    }

    @Cacheable(CacheConfig.INSTRUCTORS)
    public List<InstructorCard> getActiveInstructors(EventCategory category) {
        return instructorRepository.findActiveByCategoryOrdered(category).stream()
                .map(i -> new InstructorCard(
                        i.getId(), i.getFirstName(), i.getLastName(), i.getBio(),
                        i.getPhotoFilename() != null ? "/api/files/instructors/" + i.getPhotoFilename() : null
                ))
                .toList();
    }

    @Cacheable(CacheConfig.EVENT_TYPES)
    @Transactional(readOnly = true)
    public List<EventTypeCard> getActiveEventTypes(EventCategory category) {
        return eventTypeRepository.findByCategoryAndActiveTrueOrderByDisplayOrderAsc(category).stream()
                .map(et -> new EventTypeCard(
                        et.getId(), et.getName(), et.getDescription(),
                        et.getThumbnailFilename() != null ? "/api/files/eventtypes/" + et.getThumbnailFilename() : null,
                        et.getPhotos().stream()
                                .map(p -> new PublicDtos.PhotoItem(p.getId(), "/api/files/eventtypephotos/" + p.getFilename(), p.getDisplayOrder()))
                                .toList()
                ))
                .toList();
    }

    @Cacheable(CacheConfig.EVENTS)
    @Transactional(readOnly = true)
    public List<EventCard> getUpcomingEvents(EventCategory category) {
        var events = eventRepository
                .findByCategoryAndActiveTrueAndStartDateGreaterThanEqualOrderByStartDateAsc(
                        category, LocalDate.now());

        if (events.isEmpty()) {
            return List.of();
        }

        var eventIds = events.stream().map(Event::getId).toList();
        Map<UUID, Long> countMap = enrollmentRepository.countByEventIds(eventIds).stream()
                .collect(Collectors.toMap(r -> (UUID) r[0], r -> (Long) r[1]));

        return events.stream().map(e -> {
            long enrolled = countMap.getOrDefault(e.getId(), 0L);
            Integer max = e.getMaxParticipants();
            int available = max != null ? Math.max(0, max - (int) enrolled) : -1;
            var et = e.getEventType();
            return new EventCard(
                    e.getId(), et != null ? et.getId() : null, e.getDisplayName(), e.getDescription(),
                    e.getStartDate(), e.getEndDate(), e.getStartTime(), e.getEndTime(), e.getLocation(),
                    e.getPrice(), max, available
            );
        }).toList();
    }

    @Cacheable(CacheConfig.INSTRUCTOR)
    public InstructorCard getInstructorById(UUID id) {
        var i = instructorRepository.findById(id)
                .filter(Instructor::isActive)
                .orElseThrow(() -> new IllegalArgumentException(msg.get("instructor.not.found")));
        return new InstructorCard(
                i.getId(), i.getFirstName(), i.getLastName(), i.getBio(),
                i.getPhotoFilename() != null ? "/api/files/instructors/" + i.getPhotoFilename() : null
        );
    }

    @Cacheable(CacheConfig.EVENT_TYPE)
    @Transactional(readOnly = true)
    public EventTypeCard getEventTypeById(UUID id) {
        var et = eventTypeRepository.findById(id)
                .filter(EventType::isActive)
                .orElseThrow(() -> new IllegalArgumentException(msg.get("eventtype.not.found")));
        return new EventTypeCard(
                et.getId(), et.getName(), et.getDescription(),
                et.getThumbnailFilename() != null ? "/api/files/eventtypes/" + et.getThumbnailFilename() : null,
                et.getPhotos().stream()
                        .map(p -> new PublicDtos.PhotoItem(p.getId(), "/api/files/eventtypephotos/" + p.getFilename(), p.getDisplayOrder()))
                        .toList()
        );
    }

    @Cacheable(CacheConfig.EVENT)
    @Transactional(readOnly = true)
    public EventCard getEventById(UUID id) {
        var event = eventRepository.findById(id)
                .filter(Event::isActive)
                .orElseThrow(() -> new IllegalArgumentException(msg.get("event.not.found")));
        long enrolled = enrollmentRepository.countByEventId(event.getId());
        Integer max = event.getMaxParticipants();
        int available = max != null ? Math.max(0, max - (int) enrolled) : -1;
        var et = event.getEventType();
        return new EventCard(
                event.getId(), et != null ? et.getId() : null, event.getDisplayName(), event.getDescription(),
                event.getStartDate(), event.getEndDate(), event.getStartTime(), event.getEndTime(), event.getLocation(),
                event.getPrice(), max, available
        );
    }

    @CacheEvict(value = {CacheConfig.EVENTS, CacheConfig.EVENT}, allEntries = true)
    @Transactional
    public void enroll(UUID eventId, EnrollRequest request) {
        var event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new IllegalArgumentException(msg.get("event.not.found")));

        if (!event.isActive()) {
            throw new IllegalStateException(msg.get("enrollment.event.inactive"));
        }

        var startDateTime = event.getStartTime() != null
                ? event.getStartDate().atTime(event.getStartTime())
                : event.getStartDate().atStartOfDay();
        if (java.time.LocalDateTime.now().plusHours(24).isAfter(startDateTime)) {
            throw new IllegalStateException(msg.get("enrollment.too.late"));
        }

        if (enrollmentRepository.existsByEventIdAndEmail(eventId, request.email())) {
            throw new IllegalStateException(msg.get("enrollment.duplicate"));
        }

        Integer max = event.getMaxParticipants();
        if (max != null) {
            long enrolled = enrollmentRepository.countByEventId(eventId);
            if (enrolled >= max) {
                throw new IllegalStateException(msg.get("enrollment.event.full"));
            }
        }

        var enrollment = new Enrollment(event, request.firstName(), request.lastName(),
                request.email(), request.phone(), request.note(), false);
        enrollmentRepository.save(enrollment);

        String schedule = EnrollmentMailService.formatSchedule(event);

        enrollmentMailService.sendEnrollmentConfirmation(
                request.email(), request.firstName(),
                event.getDisplayName(), schedule, event.getLocation(),
                event.getCategory(), event.getId().toString());

        enrollmentMailService.sendEnrollmentNotification(
                event.getDisplayName(),
                request.firstName() + " " + request.lastName(),
                request.email(), request.phone(), request.note(), schedule,
                event.getCategory(), event.getId().toString());
    }
}
