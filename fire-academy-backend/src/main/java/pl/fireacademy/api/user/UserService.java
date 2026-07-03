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
import pl.fireacademy.domain.auth.TokenType;
import pl.fireacademy.domain.enrollment.EnrollmentErasureService;
import pl.fireacademy.domain.user.User;
import pl.fireacademy.domain.user.UserRepository;
import pl.fireacademy.infrastructure.i18n.MessageService;
import pl.fireacademy.infrastructure.mail.EnrollmentMailService;
import pl.fireacademy.infrastructure.security.JwtAuthenticationFilter;
import pl.fireacademy.infrastructure.security.PasswordPolicyValidator;
import pl.fireacademy.infrastructure.storage.FileStorageService;

import java.time.Duration;
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
    private final PasswordPolicyValidator passwordPolicy;
    private final TrainingEnrollmentService trainingEnrollmentService;

    public UserService(UserRepository userRepository, AuthTokenRepository authTokenRepository,
                       EnrollmentErasureService enrollmentErasureService, EnrollmentMailService enrollmentMailService,
                       PasswordEncoder passwordEncoder, MessageService msg, JwtAuthenticationFilter jwtAuthenticationFilter,
                       FileStorageService fileStorageService, AdminEmailConfig adminEmailConfig,
                       PasswordPolicyValidator passwordPolicy, TrainingEnrollmentService trainingEnrollmentService) {
        this.userRepository = userRepository;
        this.authTokenRepository = authTokenRepository;
        this.enrollmentErasureService = enrollmentErasureService;
        this.enrollmentMailService = enrollmentMailService;
        this.passwordEncoder = passwordEncoder;
        this.msg = msg;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.fileStorageService = fileStorageService;
        this.adminEmailConfig = adminEmailConfig;
        this.passwordPolicy = passwordPolicy;
        this.trainingEnrollmentService = trainingEnrollmentService;
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
        passwordPolicy.validate(request.newPassword(), user.getEmail(), user.getFirstName(), user.getLastName());
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
        // We capture the data for the notification before deletion (after delete the entity is detached).
        String fullName = user.getFullName();
        String email = user.getEmail();
        var erasure = eraseAndDeleteAccount(user);

        // Self-service account deletion — the organizer wouldn't otherwise learn about it. Always one summary email
        // (when deleting from the admin panel the admin already knows the result); when spots on future events were
        // freed, we attach their list.
        List<String> eventLines = erasure.freedEvents().stream()
                .map(ev -> ev.getDisplayName() + " — " + EnrollmentMailService.formatSchedule(ev))
                .toList();
        enrollmentMailService.sendAccountSelfDeletedNotification(fullName, email, eventLines);
    }

    /**
     * Automatic cleanup of abandoned OAuth accounts: created at login (Google passes
     * name/e-mail) that never accepted the privacy policy and are older than the threshold. GDPR —
     * since the user did not finalize consent and never came back, we have no basis to keep storing their data.
     * Invoked by a scheduler. Returns the number of deleted accounts.
     */
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.EVENTS, allEntries = true),
            @CacheEvict(value = CacheConfig.EVENT, allEntries = true)
    })
    @Transactional
    public int purgeAbandonedUnconsentedAccounts(int olderThanDays) {
        Instant cutoff = Instant.now().minus(Duration.ofDays(olderThanDays));
        List<User> abandoned = userRepository
                .findByOauthProviderIsNotNullAndPrivacyAcceptedAtIsNullAndCreatedAtBefore(cutoff);
        // Abandoned accounts have no enrollments (the backend blocks enrollment without consent), so we don't send
        // freed-spot notifications — just delete the data.
        abandoned.forEach(this::eraseAndDeleteAccount);
        return abandoned.size();
    }

    // The shared "tail" of account deletion: anonymizing/freeing enrollments (GDPR), deleting the avatar,
    // tokens, the account itself, and eviction from the JWT filter cache. Call within a transaction, after permission checks.
    private EnrollmentErasureService.ErasureResult eraseAndDeleteAccount(User user) {
        UUID userId = user.getId();
        // Training subscriptions go with the account — notify the organizer of the freed spots and remove
        // them first, exactly like a cancellation would (no-op when the user has none).
        trainingEnrollmentService.closeSubscriptionsBeforeAccountDeletion(userId);
        // Future enrollments are freed, past ones anonymized — BEFORE deleting the account
        // (after delete the FK nulls user_id and the enrollments can't be found). Shared logic with the admin panel.
        var erasure = enrollmentErasureService.eraseForUser(userId);
        if (user.getAvatarFilename() != null) {
            fileStorageService.delete(AVATAR_FOLDER, user.getAvatarFilename());
        }
        authTokenRepository.deleteAllByUserId(userId);
        userRepository.deleteById(userId);
        // Without this, a deleted user would still authenticate from the JWT filter cache for ~60s.
        jwtAuthenticationFilter.evictUser(userId);
        return erasure;
    }

    /**
     * "Log out everywhere": deletes ALL of the user's refresh tokens, so no device can refresh its
     * session — every device drops to logged-out within ≤15 min (when its access token expires).
     * Not instant and not device-specific by design (all-or-nothing). The caller's own session is
     * affected too, so the frontend logs the user out locally as well.
     */
    @Transactional
    public void logoutAllDevices(UUID userId) {
        authTokenRepository.deleteByUserIdAndTokenType(userId, TokenType.REFRESH_TOKEN);
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
        // Enabling sets the consent moment (if not already present), disabling clears it — full consent withdrawal (GDPR).
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
        // Privacy policy mandatory until it has been accepted (completing Google accounts — GDPR).
        // Email/password accounts already have it set at registration, so we don't enforce it again here.
        if (!user.hasPrivacyAccepted()) {
            if (!request.acceptedPrivacy()) {
                throw new IllegalArgumentException(msg.get("validation.privacy.required"));
            }
            user.setPrivacyAcceptedAt(Instant.now());
        }
        // Marketing voluntary — we set it only when checked and not yet granted.
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
