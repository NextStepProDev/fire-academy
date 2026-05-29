package pl.fireacademy.api.pub;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.fireacademy.api.pub.PublicDtos.*;
import pl.fireacademy.domain.enrollment.Enrollment;
import pl.fireacademy.domain.enrollment.EnrollmentRepository;
import pl.fireacademy.domain.event.*;
import pl.fireacademy.domain.instructor.InstructorRepository;
import pl.fireacademy.infrastructure.i18n.MessageService;
import pl.fireacademy.infrastructure.mail.EnrollmentMailService;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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

    public List<InstructorCard> getActiveInstructors(EventCategory category) {
        return instructorRepository.findActiveByCategoryOrdered(category).stream()
                .map(i -> new InstructorCard(
                        i.getId(), i.getFirstName(), i.getLastName(), i.getBio(),
                        i.getPhotoFilename() != null ? "/api/files/instructors/" + i.getPhotoFilename() : null
                ))
                .toList();
    }

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

    @Transactional(readOnly = true)
    public List<EventCard> getUpcomingEvents(EventCategory category) {
        var events = eventRepository
                .findByEventType_CategoryAndActiveTrueAndStartDateGreaterThanEqualOrderByStartDateAsc(
                        category, LocalDate.now());
        return events.stream().map(e -> {
            long enrolled = enrollmentRepository.countByEventId(e.getId());
            Integer max = e.getMaxParticipants();
            int available = max != null ? Math.max(0, max - (int) enrolled) : -1;
            return new EventCard(
                    e.getId(), e.getEventType().getId(), e.getEventType().getName(),
                    e.getStartDate(), e.getEndDate(), e.getStartTime(), e.getLocation(),
                    e.getPrice(), max, e.getDuration(), available
            );
        }).toList();
    }

    @Transactional
    public void enroll(UUID eventId, EnrollRequest request) {
        var event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new IllegalArgumentException(msg.get("event.not.found")));

        if (!event.isActive()) {
            throw new IllegalStateException(msg.get("enrollment.event.inactive"));
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
                request.email(), request.phone(), false);
        enrollmentRepository.save(enrollment);

        enrollmentMailService.sendEnrollmentConfirmation(
                request.email(), request.firstName(),
                event.getEventType().getName(), event.getStartDate(), event.getLocation());

        enrollmentMailService.sendEnrollmentNotification(
                event.getEventType().getName(),
                request.firstName() + " " + request.lastName(),
                request.email(), event.getStartDate());
    }
}
