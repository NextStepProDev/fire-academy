package pl.fireacademy.api.user;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.fireacademy.api.NotFoundException;
import pl.fireacademy.api.user.UserEnrollmentDtos.*;
import pl.fireacademy.config.CacheConfig;
import pl.fireacademy.domain.enrollment.Enrollment;
import pl.fireacademy.domain.enrollment.EnrollmentRepository;
import pl.fireacademy.domain.enrollment.EnrollmentTimeline;
import pl.fireacademy.domain.event.Event;
import pl.fireacademy.domain.event.EventRepository;
import pl.fireacademy.domain.user.User;
import pl.fireacademy.domain.user.UserRepository;
import pl.fireacademy.infrastructure.i18n.MessageService;
import pl.fireacademy.infrastructure.mail.EnrollmentMailService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Zapisy własne zalogowanego użytkownika (zakładka „Moje rezerwacje").
 * <p>
 * Dane uczestnika pochodzą z konta (jedno źródło prawdy PII) — formularz nie zbiera już
 * imienia/maila/telefonu. Walidacja terminu (aktywność, 24h, limit miejsc) jest taka sama
 * jak w dawnym zapisie publicznym.
 */
@Service
public class UserEnrollmentService {

    private static final int CUTOFF_HOURS = 24;

    private final EnrollmentRepository enrollmentRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final EnrollmentMailService enrollmentMailService;
    private final MessageService msg;

    public UserEnrollmentService(EnrollmentRepository enrollmentRepository,
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

    @Caching(evict = {
            @CacheEvict(value = CacheConfig.EVENTS, allEntries = true),
            @CacheEvict(value = CacheConfig.EVENT, allEntries = true)
    })
    @Transactional
    public void enroll(UUID userId, EnrollRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(msg.get("error.user.not.found")));

        // RODO: bez zaakceptowanej polityki prywatności konto nie może korzystać z aplikacji
        // (twarda bramka jest też na froncie — to zabezpieczenie na wypadek pominięcia UI).
        if (!user.hasPrivacyAccepted()) {
            throw new IllegalStateException(msg.get("enrollment.privacy.required"));
        }

        // Organizator potrzebuje numeru kontaktowego — bez telefonu w profilu zapis jest blokowany.
        if (user.getPhone() == null || user.getPhone().isBlank()) {
            throw new IllegalStateException(msg.get("enrollment.phone.required"));
        }

        Event event = eventRepository.findByIdForUpdate(request.eventId())
                .orElseThrow(() -> new NotFoundException(msg.get("event.not.found")));

        if (!event.isActive()) {
            throw new IllegalStateException(msg.get("enrollment.event.inactive"));
        }

        if (LocalDateTime.now().plusHours(CUTOFF_HOURS).isAfter(event.startDateTime())) {
            throw new IllegalStateException(msg.get("enrollment.too.late"));
        }

        if (enrollmentRepository.existsByEventIdAndUserId(event.getId(), userId)) {
            throw new IllegalStateException(msg.get("enrollment.duplicate"));
        }

        Integer max = event.getMaxParticipants();
        if (max != null && enrollmentRepository.countByEventId(event.getId()) >= max) {
            throw new IllegalStateException(msg.get("enrollment.event.full"));
        }

        String note = Enrollment.normalizeNote(request.note());
        enrollmentRepository.save(Enrollment.forUser(event, user, note, false));

        String schedule = EnrollmentMailService.formatSchedule(event);
        enrollmentMailService.sendEnrollmentConfirmation(
                user.getEmail(), user.getFirstName(),
                event.getDisplayName(), schedule, event.getLocation(),
                event.getCategory(), event.getId().toString());
        enrollmentMailService.sendEnrollmentNotification(
                event.getDisplayName(), user.getFullName(),
                user.getEmail(), user.getPhone(), note, schedule,
                event.getCategory(), event.getId().toString());
    }

    @Transactional(readOnly = true)
    public MyEnrollmentsResponse getMyEnrollments(UUID userId) {
        var split = EnrollmentTimeline.split(enrollmentRepository.findByUserIdOrderByCreatedAtDesc(userId));
        List<MyEnrollmentResponse> current = split.current().stream().map(e -> toResponse(e, false)).toList();
        List<MyEnrollmentResponse> past = split.past().stream().map(e -> toResponse(e, true)).toList();
        return new MyEnrollmentsResponse(current, past);
    }

    @Caching(evict = {
            @CacheEvict(value = CacheConfig.EVENTS, allEntries = true),
            @CacheEvict(value = CacheConfig.EVENT, allEntries = true)
    })
    @Transactional
    public void cancelMyEnrollment(UUID userId, UUID enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findByIdAndUserId(enrollmentId, userId)
                .orElseThrow(() -> new NotFoundException(msg.get("enrollment.not.found")));

        Event event = enrollment.getEvent();
        if (LocalDateTime.now().plusHours(CUTOFF_HOURS).isAfter(event.startDateTime())) {
            throw new IllegalStateException(msg.get("enrollment.cancel.too.late"));
        }

        String schedule = EnrollmentMailService.formatSchedule(event);
        String participantName = enrollment.getFirstName() + " " + enrollment.getLastName();
        String participantEmail = enrollment.getEmail();

        enrollmentRepository.delete(enrollment);

        // Użytkownik anuluje sam — nie wysyłamy mu maila „anulowano przez organizatora";
        // powiadamiamy tylko organizatora, że zwolniło się miejsce.
        enrollmentMailService.sendEnrollmentDeletionAdminNotification(
                event.getDisplayName(), participantName, participantEmail, schedule,
                event.getCategory(), event.getId().toString());
    }

    private MyEnrollmentResponse toResponse(Enrollment e, boolean past) {
        Event event = e.getEvent();
        boolean canCancel = !past && LocalDateTime.now().plusHours(CUTOFF_HOURS).isBefore(event.startDateTime());
        return new MyEnrollmentResponse(
                e.getId(), event.getId(), event.getDisplayName(), event.getCategory(),
                event.getStartDate(), event.getEndDate(), event.getStartTime(), event.getEndTime(),
                event.getLocation(), e.getNote(), past, canCancel, e.getCreatedAt());
    }
}
