package pl.fireacademy.api.admin;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.fireacademy.api.NotFoundException;
import pl.fireacademy.api.admin.EnrollmentDtos.*;
import pl.fireacademy.config.CacheConfig;
import pl.fireacademy.domain.enrollment.Enrollment;
import pl.fireacademy.domain.enrollment.EnrollmentRepository;
import pl.fireacademy.domain.event.EventCategory;
import pl.fireacademy.domain.event.EventRepository;
import pl.fireacademy.domain.user.UserRepository;
import pl.fireacademy.infrastructure.i18n.MessageService;
import pl.fireacademy.infrastructure.mail.EnrollmentMailService;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
public class AdminEnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final EnrollmentMailService enrollmentMailService;
    private final MessageService msg;

    public AdminEnrollmentService(EnrollmentRepository enrollmentRepository,
                                  EventRepository eventRepository,
                                  UserRepository userRepository,
                                  EnrollmentMailService enrollmentMailService,
                                  MessageService msg) {
        this.enrollmentRepository = enrollmentRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
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
                .orElseThrow(() -> new NotFoundException(msg.get("event.not.found")));

        if (enrollmentRepository.existsByEventIdAndEmail(event.getId(), request.email())) {
            throw new IllegalStateException(msg.get("enrollment.already.exists"));
        }

        // Telefon opcjonalny przy dopisywaniu przez admina (RODO) — pusty zapisujemy jako null.
        String phone = (request.phone() == null || request.phone().isBlank()) ? null : request.phone();

        var enrollment = new Enrollment(event, request.firstName(), request.lastName(),
                request.email(), phone, request.note(), true);
        var saved = enrollmentRepository.save(enrollment);

        String schedule = EnrollmentMailService.formatSchedule(event);

        enrollmentMailService.sendAdminEnrollmentConfirmation(
                request.email(), request.firstName(),
                event.getDisplayName(), schedule, event.getLocation(),
                event.getCategory(), event.getId().toString());

        enrollmentMailService.sendAdminEnrollmentNotification(
                event.getDisplayName(),
                request.firstName() + " " + request.lastName(),
                request.email(), phone == null ? "—" : phone, request.note(), schedule,
                event.getCategory(), event.getId().toString());

        return toResponse(saved);
    }

    @Caching(evict = {
            @CacheEvict(value = CacheConfig.EVENTS, allEntries = true),
            @CacheEvict(value = CacheConfig.EVENT, allEntries = true)
    })
    @Transactional
    public void delete(UUID id, boolean notify) {
        var enrollment = enrollmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(msg.get("enrollment.not.found")));

        var event = enrollment.getEvent();
        var participantName = enrollment.getFirstName() + " " + enrollment.getLastName();
        var schedule = EnrollmentMailService.formatSchedule(event);

        enrollmentRepository.delete(enrollment);

        // notify=false dla korekty archiwum (wydarzenie już było) — uczestnik nie dostaje maila o „odwołaniu".
        if (notify) {
            enrollmentMailService.sendEnrollmentDeletionNotification(
                    enrollment.getEmail(), enrollment.getFirstName(),
                    event.getDisplayName(), schedule,
                    event.getCategory(), event.getId().toString());

            enrollmentMailService.sendEnrollmentDeletionAdminNotification(
                    event.getDisplayName(), participantName,
                    enrollment.getEmail(), schedule,
                    event.getCategory(), event.getId().toString());
        }
    }

    @Transactional(readOnly = true)
    public List<EnrollmentResponse> searchByQuery(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return enrollmentRepository.searchByQuery(query.trim()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public EnrollmentDtos.BulkEmailResponse sendBulkEmail(UUID adminId, EnrollmentDtos.BulkEmailRequest request) {
        var event = eventRepository.findById(request.eventId())
                .orElseThrow(() -> new NotFoundException(msg.get("event.not.found")));

        var enrollments = enrollmentRepository.findByEventIdOrderByCreatedAtDesc(event.getId());

        var seen = new LinkedHashSet<String>();
        var recipients = enrollments.stream()
                .filter(e -> !e.isAnonymized())
                .filter(e -> seen.add(e.getEmail().toLowerCase()))
                .toList();

        if (recipients.isEmpty()) {
            throw new IllegalStateException(msg.get("email.bulk.no.enrollments"));
        }

        String senderName = adminId == null ? null : userRepository.findById(adminId)
                .map(u -> (u.getFirstName() + " " + u.getLastName()).trim())
                .filter(name -> !name.isBlank())
                .orElse(null);

        var schedule = EnrollmentMailService.formatSchedule(event);
        for (var enrollment : recipients) {
            enrollmentMailService.sendBulkEventMessage(
                    enrollment.getEmail(), enrollment.getFirstName(),
                    event.getDisplayName(), schedule,
                    event.getLocation(), request.message(), senderName,
                    event.getCategory(), event.getId().toString());
        }

        return new EnrollmentDtos.BulkEmailResponse(recipients.size());
    }

    @Transactional
    public EnrollmentDtos.AnonymizeResponse anonymizeByQuery(String query) {
        if (query == null || query.isBlank()) {
            return new EnrollmentDtos.AnonymizeResponse(0);
        }
        // Anonimizujemy dokładnie te wpisy, które admin widzi w wynikach wyszukiwania (ta sama fraza).
        var enrollments = enrollmentRepository.searchByQuery(query.trim());
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
