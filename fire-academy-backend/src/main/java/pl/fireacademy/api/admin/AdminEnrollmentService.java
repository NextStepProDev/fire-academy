package pl.fireacademy.api.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.fireacademy.api.admin.EnrollmentDtos.*;
import pl.fireacademy.domain.enrollment.Enrollment;
import pl.fireacademy.domain.enrollment.EnrollmentRepository;
import pl.fireacademy.domain.event.EventCategory;
import pl.fireacademy.domain.event.EventRepository;
import pl.fireacademy.infrastructure.i18n.MessageService;
import pl.fireacademy.infrastructure.mail.EnrollmentMailService;

import java.util.List;
import java.util.UUID;

@Service
public class AdminEnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final EventRepository eventRepository;
    private final EnrollmentMailService enrollmentMailService;
    private final MessageService msg;

    public AdminEnrollmentService(EnrollmentRepository enrollmentRepository,
                                  EventRepository eventRepository,
                                  EnrollmentMailService enrollmentMailService,
                                  MessageService msg) {
        this.enrollmentRepository = enrollmentRepository;
        this.eventRepository = eventRepository;
        this.enrollmentMailService = enrollmentMailService;
        this.msg = msg;
    }

    @Transactional(readOnly = true)
    public List<EnrollmentResponse> getByEvent(UUID eventId) {
        return enrollmentRepository.findByEventIdOrderByCreatedAtDesc(eventId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EnrollmentResponse> getByCategory(EventCategory category) {
        return enrollmentRepository.findByEventCategory(category).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public EnrollmentResponse adminEnroll(AdminEnrollRequest request) {
        var event = eventRepository.findById(request.eventId())
                .orElseThrow(() -> new IllegalArgumentException(msg.get("event.not.found")));

        var enrollment = new Enrollment(event, request.firstName(), request.lastName(),
                request.email(), request.phone(), request.note(), true);
        var saved = enrollmentRepository.save(enrollment);

        enrollmentMailService.sendAdminEnrollmentConfirmation(
                request.email(), request.firstName(),
                event.getDisplayName(), event.getStartDate(), event.getLocation());

        enrollmentMailService.sendAdminEnrollmentNotification(
                event.getDisplayName(),
                request.firstName() + " " + request.lastName(),
                request.email(), event.getStartDate());

        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID id) {
        var enrollment = enrollmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(msg.get("enrollment.not.found")));
        enrollmentRepository.delete(enrollment);
    }

    private EnrollmentResponse toResponse(Enrollment e) {
        return new EnrollmentResponse(
                e.getId(), e.getEvent().getId(),
                e.getEvent().getDisplayName(),
                e.getEvent().getStartDate(),
                e.getFirstName(), e.getLastName(), e.getEmail(), e.getPhone(),
                e.getNote(), e.isAddedByAdmin(), e.getCreatedAt()
        );
    }
}
