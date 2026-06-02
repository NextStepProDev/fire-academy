package pl.fireacademy.api.admin;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.fireacademy.api.admin.EnrollmentDtos.*;
import pl.fireacademy.config.CacheConfig;
import pl.fireacademy.domain.enrollment.Enrollment;
import pl.fireacademy.domain.enrollment.EnrollmentRepository;
import pl.fireacademy.domain.event.EventCategory;
import pl.fireacademy.domain.event.EventRepository;
import pl.fireacademy.infrastructure.i18n.MessageService;
import pl.fireacademy.infrastructure.mail.EnrollmentMailService;

import java.util.LinkedHashSet;
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

    @Caching(evict = {
            @CacheEvict(value = CacheConfig.EVENTS, allEntries = true),
            @CacheEvict(value = CacheConfig.EVENT, allEntries = true)
    })
    @Transactional
    public EnrollmentResponse adminEnroll(AdminEnrollRequest request) {
        var event = eventRepository.findById(request.eventId())
                .orElseThrow(() -> new IllegalArgumentException(msg.get("event.not.found")));

        var enrollment = new Enrollment(event, request.firstName(), request.lastName(),
                request.email(), request.phone(), request.note(), true);
        var saved = enrollmentRepository.save(enrollment);

        enrollmentMailService.sendAdminEnrollmentConfirmation(
                request.email(), request.firstName(),
                event.getDisplayName(), event.getStartDate(), event.getLocation(),
                event.getCategory(), event.getId().toString());

        enrollmentMailService.sendAdminEnrollmentNotification(
                event.getDisplayName(),
                request.firstName() + " " + request.lastName(),
                request.email(), event.getStartDate(),
                event.getCategory(), event.getId().toString());

        return toResponse(saved);
    }

    @Caching(evict = {
            @CacheEvict(value = CacheConfig.EVENTS, allEntries = true),
            @CacheEvict(value = CacheConfig.EVENT, allEntries = true)
    })
    @Transactional
    public void delete(UUID id) {
        var enrollment = enrollmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(msg.get("enrollment.not.found")));

        var event = enrollment.getEvent();
        var participantName = enrollment.getFirstName() + " " + enrollment.getLastName();

        enrollmentRepository.delete(enrollment);

        enrollmentMailService.sendEnrollmentDeletionNotification(
                enrollment.getEmail(), enrollment.getFirstName(),
                event.getDisplayName(), event.getStartDate(),
                event.getCategory(), event.getId().toString());

        enrollmentMailService.sendEnrollmentDeletionAdminNotification(
                event.getDisplayName(), participantName,
                enrollment.getEmail(), event.getStartDate(),
                event.getCategory(), event.getId().toString());
    }

    @Transactional(readOnly = true)
    public List<EnrollmentResponse> searchByEmail(String email) {
        return enrollmentRepository.findByEmailIgnoreCase(email.trim()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public EnrollmentDtos.BulkEmailResponse sendBulkEmail(EnrollmentDtos.BulkEmailRequest request) {
        var event = eventRepository.findById(request.eventId())
                .orElseThrow(() -> new IllegalArgumentException(msg.get("event.not.found")));

        var enrollments = enrollmentRepository.findByEventIdOrderByCreatedAtDesc(event.getId());

        var seen = new LinkedHashSet<String>();
        var recipients = enrollments.stream()
                .filter(e -> !e.isAnonymized())
                .filter(e -> seen.add(e.getEmail().toLowerCase()))
                .toList();

        if (recipients.isEmpty()) {
            throw new IllegalStateException(msg.get("email.bulk.no.enrollments"));
        }

        for (var enrollment : recipients) {
            enrollmentMailService.sendBulkEventMessage(
                    enrollment.getEmail(), enrollment.getFirstName(),
                    event.getDisplayName(), event.getStartDate(),
                    event.getLocation(), request.message(),
                    event.getCategory(), event.getId().toString());
        }

        return new EnrollmentDtos.BulkEmailResponse(recipients.size());
    }

    @Transactional
    public EnrollmentDtos.AnonymizeResponse anonymizeByEmail(String email) {
        var enrollments = enrollmentRepository.findByEmailIgnoreCase(email.trim());
        enrollments.forEach(Enrollment::anonymize);
        enrollmentRepository.saveAll(enrollments);
        return new EnrollmentDtos.AnonymizeResponse(enrollments.size());
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
