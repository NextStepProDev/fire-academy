package pl.fireacademy.api.user;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pl.fireacademy.api.NotFoundException;
import pl.fireacademy.config.AdminEmailConfig;
import pl.fireacademy.config.CacheConfig;
import pl.fireacademy.domain.auth.AuthTokenRepository;
import pl.fireacademy.domain.enrollment.EnrollmentErasureService;
import pl.fireacademy.domain.user.User;
import pl.fireacademy.domain.user.UserRepository;
import pl.fireacademy.infrastructure.i18n.MessageService;
import pl.fireacademy.infrastructure.mail.EnrollmentMailService;
import pl.fireacademy.infrastructure.security.JwtAuthenticationFilter;
import pl.fireacademy.infrastructure.storage.FileStorageService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class UserService {
    private static final String AVATAR_FOLDER = "avatars";

    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;
    private final EnrollmentErasureService enrollmentErasureService;
    private final EnrollmentMailService enrollmentMailService;
    private final PasswordEncoder passwordEncoder;
    private final MessageService msg;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final FileStorageService fileStorageService;
    private final AdminEmailConfig adminEmailConfig;

    public UserService(UserRepository userRepository, AuthTokenRepository authTokenRepository,
                       EnrollmentErasureService enrollmentErasureService, EnrollmentMailService enrollmentMailService,
                       PasswordEncoder passwordEncoder, MessageService msg, JwtAuthenticationFilter jwtAuthenticationFilter,
                       FileStorageService fileStorageService, AdminEmailConfig adminEmailConfig) {
        this.userRepository = userRepository;
        this.authTokenRepository = authTokenRepository;
        this.enrollmentErasureService = enrollmentErasureService;
        this.enrollmentMailService = enrollmentMailService;
        this.passwordEncoder = passwordEncoder;
        this.msg = msg;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.fileStorageService = fileStorageService;
        this.adminEmailConfig = adminEmailConfig;
    }

    public UserDtos.UserResponse getMe(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException(msg.get("error.user.not.found")));
        return toResponse(user);
    }

    @Transactional
    public UserDtos.UserResponse updateMe(UUID userId, UserDtos.UpdateUserRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException(msg.get("error.user.not.found")));
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPhone(request.phone());
        userRepository.save(user);
        jwtAuthenticationFilter.evictUser(userId);
        return toResponse(user);
    }

    @Transactional
    public void changePassword(UUID userId, UserDtos.ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException(msg.get("error.user.not.found")));
        if (user.getPasswordHash() == null) {
            throw new IllegalStateException(msg.get("user.password.oauth"));
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException(msg.get("user.password.invalid"));
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        jwtAuthenticationFilter.evictUser(userId);
    }

    @Caching(evict = {
            @CacheEvict(value = CacheConfig.EVENTS, allEntries = true),
            @CacheEvict(value = CacheConfig.EVENT, allEntries = true)
    })
    @Transactional
    public void deleteMe(UUID userId, UserDtos.DeleteAccountRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException(msg.get("error.user.not.found")));
        if (user.getPasswordHash() != null) {
            if (request.password() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                throw new IllegalArgumentException(msg.get("user.password.invalid"));
            }
        }
        // RODO: przyszłe zapisy zwalniamy, przeszłe anonimizujemy — PRZED skasowaniem konta
        // (po delete FK wyzeruje user_id i zapisów nie da się odnaleźć). Wspólna logika z panelem admina.
        var erasure = enrollmentErasureService.eraseForUser(userId);

        // Samodzielna rezygnacja przez usunięcie konta — organizator nie wie inaczej, że zwolniły się
        // miejsca na przyszłych wydarzeniach. Jeden zbiorczy mail (przy usuwaniu z panelu admin sam zna wynik).
        if (!erasure.freedEvents().isEmpty()) {
            List<String> eventLines = erasure.freedEvents().stream()
                    .map(ev -> ev.getDisplayName() + " — " + EnrollmentMailService.formatSchedule(ev))
                    .toList();
            enrollmentMailService.sendAccountDeletionSeatsFreedNotification(
                    user.getFullName(), user.getEmail(), eventLines);
        }

        if (user.getAvatarFilename() != null) {
            fileStorageService.delete(AVATAR_FOLDER, user.getAvatarFilename());
        }
        authTokenRepository.deleteAllByUserId(userId);
        userRepository.deleteById(userId);
        // Bez tego usunięty user nadal uwierzytelniałby się z cache filtra JWT przez ~60s.
        jwtAuthenticationFilter.evictUser(userId);
    }

    @Transactional
    public UserDtos.UserResponse uploadAvatar(UUID userId, MultipartFile file) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException(msg.get("error.user.not.found")));
        String oldFilename = user.getAvatarFilename();
        String filename = fileStorageService.store(AVATAR_FOLDER, file);
        user.setAvatarFilename(filename);
        userRepository.save(user);
        if (oldFilename != null) {
            fileStorageService.delete(AVATAR_FOLDER, oldFilename);
        }
        jwtAuthenticationFilter.evictUser(userId);
        return toResponse(user);
    }

    @Transactional
    public UserDtos.UserResponse deleteAvatar(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException(msg.get("error.user.not.found")));
        String oldFilename = user.getAvatarFilename();
        if (oldFilename != null) {
            user.setAvatarFilename(null);
            userRepository.save(user);
            fileStorageService.delete(AVATAR_FOLDER, oldFilename);
            jwtAuthenticationFilter.evictUser(userId);
        }
        return toResponse(user);
    }

    @Transactional
    public UserDtos.UserResponse updateMarketing(UUID userId, UserDtos.UpdateMarketingRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException(msg.get("error.user.not.found")));
        // Włączenie ustawia moment zgody (jeśli jeszcze brak), wyłączenie ją zeruje — pełne wycofanie zgody (RODO).
        if (request.enabled()) {
            if (!user.hasMarketingConsent()) {
                user.setMarketingConsentAt(Instant.now());
            }
        } else {
            user.setMarketingConsentAt(null);
        }
        userRepository.save(user);
        jwtAuthenticationFilter.evictUser(userId);
        return toResponse(user);
    }

    @Transactional
    public UserDtos.UserResponse submitConsents(UUID userId, UserDtos.ConsentsRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException(msg.get("error.user.not.found")));
        // Polityka prywatności obowiązkowa, dopóki nie była zaakceptowana (domknięcie kont Google — RODO).
        // Konta email/hasło mają ją już ustawioną przy rejestracji, więc tu jej nie wymuszamy ponownie.
        if (!user.hasPrivacyAccepted()) {
            if (!request.acceptedPrivacy()) {
                throw new IllegalArgumentException(msg.get("validation.privacy.required"));
            }
            user.setPrivacyAcceptedAt(Instant.now());
        }
        // Marketing dobrowolny — ustawiamy tylko, gdy zaznaczony i jeszcze nieudzielony.
        if (request.acceptedMarketing() && !user.hasMarketingConsent()) {
            user.setMarketingConsentAt(Instant.now());
        }
        userRepository.save(user);
        jwtAuthenticationFilter.evictUser(userId);
        return toResponse(user);
    }

    private UserDtos.UserResponse toResponse(User user) {
        String avatarUrl = user.getAvatarFilename() != null
            ? "/api/files/" + AVATAR_FOLDER + "/" + user.getAvatarFilename()
            : null;
        return new UserDtos.UserResponse(
            user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
            user.getPhone(), user.getRole().name(),
            user.getRole() == pl.fireacademy.domain.user.UserRole.ADMIN,
            adminEmailConfig.isAdminEmail(user.getEmail()),
            user.isEmailVerified(),
            user.hasPrivacyAccepted(), user.hasMarketingConsent(),
            user.getPreferredLanguage(), user.getPasswordHash() != null,
            user.getOauthProvider() != null, avatarUrl, user.getCreatedAt()
        );
    }
}
