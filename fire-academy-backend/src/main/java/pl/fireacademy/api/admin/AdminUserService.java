package pl.fireacademy.api.admin;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.fireacademy.api.NotFoundException;
import pl.fireacademy.api.admin.AdminUserDtos.*;
import pl.fireacademy.config.AdminEmailConfig;
import pl.fireacademy.config.CacheConfig;
import pl.fireacademy.domain.auth.AuthTokenRepository;
import pl.fireacademy.domain.enrollment.Enrollment;
import pl.fireacademy.domain.enrollment.EnrollmentErasureService;
import pl.fireacademy.domain.enrollment.EnrollmentRepository;
import pl.fireacademy.domain.enrollment.EnrollmentTimeline;
import pl.fireacademy.domain.event.Event;
import pl.fireacademy.domain.user.User;
import pl.fireacademy.domain.user.UserRepository;
import pl.fireacademy.domain.user.UserRole;
import pl.fireacademy.infrastructure.i18n.MessageService;
import pl.fireacademy.infrastructure.mail.AdminUserMailService;
import pl.fireacademy.infrastructure.mail.EnrollmentMailService;
import pl.fireacademy.infrastructure.security.JwtAuthenticationFilter;
import pl.fireacademy.infrastructure.storage.FileStorageService;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AdminUserService {

    private static final String AVATAR_FOLDER = "avatars";
    private static final int DEFAULT_PAGE_SIZE = 30;
    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentErasureService enrollmentErasureService;
    private final AdminEmailConfig adminEmailConfig;
    private final AdminUserMailService adminUserMailService;
    private final FileStorageService fileStorageService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final MessageService msg;

    public AdminUserService(UserRepository userRepository,
                            AuthTokenRepository authTokenRepository,
                            EnrollmentRepository enrollmentRepository,
                            EnrollmentErasureService enrollmentErasureService,
                            AdminEmailConfig adminEmailConfig,
                            AdminUserMailService adminUserMailService,
                            FileStorageService fileStorageService,
                            JwtAuthenticationFilter jwtAuthenticationFilter,
                            MessageService msg) {
        this.userRepository = userRepository;
        this.authTokenRepository = authTokenRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.enrollmentErasureService = enrollmentErasureService;
        this.adminEmailConfig = adminEmailConfig;
        this.adminUserMailService = adminUserMailService;
        this.fileStorageService = fileStorageService;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.msg = msg;
    }

    @Transactional(readOnly = true)
    public PagedUsersResponse list(String search, int page, int size, String sort, String direction) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, resolveSort(sort, direction));

        // Konta techniczne/deweloperskie (np. konto programisty) są ukryte na liście — mają admina do testów,
        // ale nie powinny zaśmiecać widoku ani mylić pozostałych administratorów. Filtr w SQL, by liczniki/paginacja
        // były spójne; gdy brak ukrytych e-maili, używamy zwykłych zapytań (NOT IN () byłoby niepoprawne).
        Set<String> hidden = adminEmailConfig.getHiddenEmails();
        boolean blank = search == null || search.isBlank();
        Page<User> result = hidden.isEmpty()
                ? (blank ? userRepository.findAll(pageable)
                         : userRepository.searchByPhrase(search.trim(), pageable))
                : (blank ? userRepository.findAllExcludingEmails(hidden, pageable)
                         : userRepository.searchByPhraseExcludingEmails(search.trim(), hidden, pageable));

        List<AdminUserResponse> content = result.getContent().stream().map(this::toResponse).toList();
        return new PagedUsersResponse(
                content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    // Whitelist sortowanych pól (chroni przed wstrzyknięciem dowolnej właściwości w Sort).
    // Telefon celowo nie jest sortowalny.
    private Sort resolveSort(String sort, String direction) {
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return switch (sort == null ? "" : sort) {
            case "name" -> Sort.by(dir, "lastName", "firstName");
            case "email" -> Sort.by(dir, "email");
            case "role" -> Sort.by(dir, "role");
            default -> Sort.by(dir, "createdAt");
        };
    }

    @Transactional(readOnly = true)
    public AdminUserDetailResponse getDetail(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(msg.get("error.user.not.found")));

        var split = EnrollmentTimeline.split(enrollmentRepository.findByUserIdOrderByCreatedAtDesc(user.getId()));
        List<UserEnrollmentResponse> current = split.current().stream().map(e -> toEnrollmentResponse(e, false)).toList();
        List<UserEnrollmentResponse> past = split.past().stream().map(e -> toEnrollmentResponse(e, true)).toList();

        String avatarUrl = user.getAvatarFilename() != null
                ? "/api/files/" + AVATAR_FOLDER + "/" + user.getAvatarFilename()
                : null;

        return new AdminUserDetailResponse(
                user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getPhone(), user.getRole().name(), user.isAdmin(),
                adminEmailConfig.isAdminEmail(user.getEmail()),
                user.isEmailVerified(), user.isEmailNotificationsEnabled(),
                user.getPreferredLanguage(), user.getPasswordHash() != null,
                user.getOauthProvider() != null, avatarUrl, user.getCreatedAt(),
                current, past);
    }

    private UserEnrollmentResponse toEnrollmentResponse(Enrollment e, boolean past) {
        Event event = e.getEvent();
        return new UserEnrollmentResponse(
                e.getId(), event.getId(), event.getDisplayName(), event.getCategory(),
                event.getStartDate(), event.getEndDate(), event.getStartTime(), event.getEndTime(),
                event.getLocation(), e.getNote(), e.isAddedByAdmin(), past, e.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public SendEmailResponse sendEmail(SendEmailRequest request) {
        List<User> recipients = (request.allUsers()
                ? userRepository.findAllByOrderByCreatedAtDesc()
                : (request.userIds() == null || request.userIds().isEmpty()
                        ? List.<User>of()
                        : userRepository.findAllById(request.userIds())))
                .stream()
                // Konta ukryte (techniczne/deweloperskie) nie są adresatami maila „do wszystkich".
                .filter(u -> !adminEmailConfig.isHiddenEmail(u.getEmail()))
                .toList();

        if (recipients.isEmpty()) {
            throw new IllegalStateException(msg.get("email.admin.no.recipients"));
        }

        for (User user : recipients) {
            adminUserMailService.sendCustomMessage(
                    user.getEmail(), user.getFirstName(), request.subject(), request.message());
        }

        return new SendEmailResponse(recipients.size());
    }

    @Caching(evict = {
            @CacheEvict(value = CacheConfig.EVENTS, allEntries = true),
            @CacheEvict(value = CacheConfig.EVENT, allEntries = true)
    })
    @Transactional
    public DeleteUserResponse delete(UUID adminId, UUID targetId, boolean notify) {
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new NotFoundException(msg.get("error.user.not.found")));

        if (target.getId().equals(adminId)) {
            throw new IllegalStateException(msg.get("error.user.cannot.delete.self"));
        }
        if (adminEmailConfig.isAdminEmail(target.getEmail())) {
            throw new IllegalStateException(msg.get("error.user.cannot.delete.superadmin"));
        }

        // Zapisy: przyszłe zwalniamy (miejsce wraca do puli), przeszłe anonimizujemy (archiwum bez PII,
        // user_id → NULL). Wspólna logika z samodzielnym usunięciem konta — przed skasowaniem usera.
        var erasure = enrollmentErasureService.eraseForUser(targetId);

        // Powiadomienie usera o usunięciu konta (opcjonalne — admin decyduje; np. dla RODO/realnej osoby tak,
        // dla kont testowych/spamu nie). Dane i lista zwolnionych rezerwacji złapane przed delete.
        if (notify) {
            List<String> cancelled = erasure.freedEvents().stream()
                    .map(ev -> ev.getDisplayName() + " — " + EnrollmentMailService.formatSchedule(ev))
                    .toList();
            adminUserMailService.sendAccountDeletedNotification(
                    target.getEmail(), target.getFirstName(), cancelled);
        }

        if (target.getAvatarFilename() != null) {
            fileStorageService.delete(AVATAR_FOLDER, target.getAvatarFilename());
        }
        authTokenRepository.deleteAllByUserId(targetId);
        userRepository.delete(target);
        // Bez evict usunięty/odświeżony user nadal uwierzytelniałby się z cache filtra JWT przez ~60s.
        jwtAuthenticationFilter.evictUser(targetId);

        return new DeleteUserResponse(erasure.freed(), erasure.anonymized());
    }

    @Transactional
    public AdminUserResponse promote(UUID adminId, UUID targetId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException(msg.get("error.user.not.found")));
        // Nadanie roli admina może wykonać wyłącznie super-admin zdefiniowany w .env (ADMIN_EMAIL).
        if (!adminEmailConfig.isAdminEmail(admin.getEmail())) {
            throw new IllegalStateException(msg.get("error.user.promote.forbidden"));
        }
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new NotFoundException(msg.get("error.user.not.found")));
        if (target.isAdmin()) {
            throw new IllegalStateException(msg.get("error.user.already.admin"));
        }
        target.setRole(UserRole.ADMIN);
        userRepository.save(target);
        jwtAuthenticationFilter.evictUser(targetId);
        return toResponse(target);
    }

    @Transactional
    public AdminUserResponse demote(UUID adminId, UUID targetId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException(msg.get("error.user.not.found")));
        // Odebranie roli admina może wykonać wyłącznie super-admin zdefiniowany w .env (ADMIN_EMAIL).
        if (!adminEmailConfig.isAdminEmail(admin.getEmail())) {
            throw new IllegalStateException(msg.get("error.user.demote.forbidden"));
        }
        if (targetId.equals(adminId)) {
            throw new IllegalStateException(msg.get("error.user.cannot.demote.self"));
        }

        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new NotFoundException(msg.get("error.user.not.found")));
        // Super-admin z .env i tak zostałby ponownie awansowany przy logowaniu → blokujemy bezcelową operację.
        if (adminEmailConfig.isAdminEmail(target.getEmail())) {
            throw new IllegalStateException(msg.get("error.user.cannot.demote.superadmin"));
        }
        if (!target.isAdmin()) {
            throw new IllegalStateException(msg.get("error.user.not.admin"));
        }
        target.setRole(UserRole.USER);
        userRepository.save(target);
        jwtAuthenticationFilter.evictUser(targetId);
        return toResponse(target);
    }

    private AdminUserResponse toResponse(User user) {
        return new AdminUserResponse(
                user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getPhone(), user.getRole().name(), user.isAdmin(),
                adminEmailConfig.isAdminEmail(user.getEmail()),
                user.isEmailVerified(), user.isEmailNotificationsEnabled(),
                user.getCreatedAt()
        );
    }
}
