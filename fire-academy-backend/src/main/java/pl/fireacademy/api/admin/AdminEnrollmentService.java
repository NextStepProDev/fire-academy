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

        var user = userRepository.findById(request.userId())
                .orElseThrow(() -> new NotFoundException(msg.get("enrollment.user.not.found")));

        if (enrollmentRepository.existsByEventIdAndUserId(event.getId(), user.getId())) {
            throw new IllegalStateException(msg.get("enrollment.already.exists"));
        }

        String note = (request.note() == null || request.note().isBlank()) ? null : request.note();
        var saved = enrollmentRepository.save(Enrollment.forUser(event, user, note, true));

        String schedule = EnrollmentMailService.formatSchedule(event);

        enrollmentMailService.sendAdminEnrollmentConfirmation(
                user.getEmail(), user.getFirstName(),
                event.getDisplayName(), schedule, event.getLocation(),
                event.getCategory(), event.getId().toString());

        enrollmentMailService.sendAdminEnrollmentNotification(
                event.getDisplayName(), user.getFullName(),
                user.getEmail(), user.getPhone() == null ? "—" : user.getPhone(), note, schedule,
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
