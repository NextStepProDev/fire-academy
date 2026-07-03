package pl.fireacademy.api.pub;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.fireacademy.api.NotFoundException;
import pl.fireacademy.api.pub.PublicDtos.*;
import pl.fireacademy.config.CacheConfig;
import pl.fireacademy.domain.enrollment.EnrollmentRepository;
import pl.fireacademy.domain.event.*;
import pl.fireacademy.domain.instructor.Instructor;
import pl.fireacademy.domain.instructor.InstructorRepository;
import pl.fireacademy.domain.training.TrainingCancelledSessionRepository;
import pl.fireacademy.domain.training.TrainingEnrollmentRepository;
import pl.fireacademy.domain.training.TrainingHolidayRepository;
import pl.fireacademy.domain.training.TrainingSlotRepository;
import pl.fireacademy.infrastructure.i18n.MessageService;

import java.time.LocalDate;
import java.time.YearMonth;
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
    private final TrainingSlotRepository trainingSlotRepository;
    private final TrainingEnrollmentRepository trainingEnrollmentRepository;
    private final TrainingCancelledSessionRepository trainingCancelledSessionRepository;
    private final TrainingHolidayRepository trainingHolidayRepository;
    private final MessageService msg;

    public PublicService(InstructorRepository instructorRepository,
                         EventTypeRepository eventTypeRepository,
                         EventRepository eventRepository,
                         EnrollmentRepository enrollmentRepository,
                         TrainingSlotRepository trainingSlotRepository,
                         TrainingEnrollmentRepository trainingEnrollmentRepository,
                         TrainingCancelledSessionRepository trainingCancelledSessionRepository,
                         TrainingHolidayRepository trainingHolidayRepository,
                         MessageService msg) {
        this.instructorRepository = instructorRepository;
        this.eventTypeRepository = eventTypeRepository;
        this.eventRepository = eventRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.trainingSlotRepository = trainingSlotRepository;
        this.trainingEnrollmentRepository = trainingEnrollmentRepository;
        this.trainingCancelledSessionRepository = trainingCancelledSessionRepository;
        this.trainingHolidayRepository = trainingHolidayRepository;
        this.msg = msg;
    }

    @Transactional(readOnly = true)
    public List<TrainingHolidayItem> getTrainingHolidays(YearMonth month) {
        return trainingHolidayRepository
                .findByHolidayDateBetweenOrderByHolidayDateAsc(month.atDay(1), month.atEndOfMonth())
                .stream()
                .map(h -> new TrainingHolidayItem(h.getHolidayDate(), h.getLabel()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TrainingSlotCard> getTrainingSlots(YearMonth month) {
        var slots = trainingSlotRepository.findPublicSlots();
        if (slots.isEmpty()) {
            return List.of();
        }
        var slotIds = slots.stream().map(s -> s.getId()).toList();
        Map<UUID, Long> countMap = trainingEnrollmentRepository
                .countCoveringBySlotIds(slotIds, month.toString()).stream()
                .collect(Collectors.toMap(r -> (UUID) r[0], r -> (Long) r[1]));

        // Cancelled individual sessions within the month (to mark them in the schedule).
        Map<UUID, List<LocalDate>> cancelledMap = trainingCancelledSessionRepository
                .findForSlotsInRange(slotIds, month.atDay(1), month.atEndOfMonth()).stream()
                .collect(Collectors.groupingBy(cs -> cs.getSlot().getId(),
                        Collectors.mapping(cs -> cs.getSessionDate(), Collectors.toList())));

        return slots.stream().map(s -> {
            long taken = countMap.getOrDefault(s.getId(), 0L);
            int available = Math.max(0, s.getMaxParticipants() - (int) taken);
            var et = s.getEventType();
            var instr = s.getInstructor();
            return new TrainingSlotCard(
                    s.getId(), et.getId(), et.getName(),
                    instr != null ? instr.getId() : null,
                    instr != null ? instr.getFirstName() + " " + instr.getLastName() : null,
                    s.getDayOfWeek(), s.getStartTime(), s.getEndTime(), s.getPrice(),
                    s.getMaxParticipants(), available,
                    cancelledMap.getOrDefault(s.getId(), List.of())
            );
        }).toList();
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
                .findActiveCurrentByCategory(category, LocalDate.now());

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
                .orElseThrow(() -> new NotFoundException(msg.get("instructor.not.found")));
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
                .orElseThrow(() -> new NotFoundException(msg.get("eventtype.not.found")));
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
                .orElseThrow(() -> new NotFoundException(msg.get("event.not.found")));
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
}
