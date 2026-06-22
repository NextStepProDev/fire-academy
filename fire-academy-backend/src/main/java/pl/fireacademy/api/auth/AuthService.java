package pl.fireacademy.api.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.fireacademy.api.auth.AuthDtos.*;
import pl.fireacademy.domain.auth.AuthToken;
import pl.fireacademy.domain.auth.AuthTokenRepository;
import pl.fireacademy.domain.auth.TokenType;
import pl.fireacademy.config.AdminEmailConfig;
import pl.fireacademy.domain.user.User;
import pl.fireacademy.domain.user.UserRepository;
import pl.fireacademy.domain.user.UserRole;
import pl.fireacademy.infrastructure.i18n.MessageService;
import pl.fireacademy.infrastructure.mail.AuthMailService;
import pl.fireacademy.infrastructure.security.JwtService;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("pl");
    // A format-valid BCrypt hash (cost 10) used solely for a dummy comparison
    // when the account does not exist — it evens out the response time and blocks user enumeration.
    private static final String DUMMY_PASSWORD_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
    private static final Duration EMAIL_VERIFICATION_EXPIRATION = Duration.ofMinutes(15);
    private static final Duration PASSWORD_RESET_EXPIRATION = Duration.ofHours(1);
    private static final Duration RESEND_COOLDOWN = Duration.ofMinutes(1);

    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthMailService authMailService;
    private final AdminEmailConfig adminEmailConfig;
    private final MessageService msg;

    public AuthService(
            UserRepository userRepository,
            AuthTokenRepository authTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthMailService authMailService,
            AdminEmailConfig adminEmailConfig,
            MessageService msg) {
        this.userRepository = userRepository;
        this.authTokenRepository = authTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authMailService = authMailService;
        this.adminEmailConfig = adminEmailConfig;
        this.msg = msg;
    }

    @Transactional
    public MessageResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new IllegalArgumentException(msg.get("auth.email.exists"));
        }

        User user = new User(
            request.email(),
            request.firstName(),
            request.lastName(),
            request.phone()
        );
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setPreferredLanguage(resolveLanguage(request.preferredLanguage()));
        // Privacy policy consent is required (@AssertTrue validation) — we record the acceptance moment (GDPR).
        user.setPrivacyAcceptedAt(Instant.now());
        // Marketing consent is voluntary (opt-in) — the field is optional (may be absent) → null is treated as no consent.
        if (Boolean.TRUE.equals(request.acceptedMarketing())) {
            user.setMarketingConsentAt(Instant.now());
        }

        if (adminEmailConfig.isAdminEmail(user.getEmail())) {
            user.setRole(UserRole.ADMIN);
            log.info("AUTO-ADMIN-PROMOTION: {} promoted to ADMIN during registration", user.getEmail());
        }

        userRepository.save(user);
        log.info("User registered: {}", user.getEmail());

        sendVerificationEmail(user);

        return new MessageResponse(msg.get("auth.register.success"));
    }

    // Intentionally NOT @Transactional: a transaction would pin a Hikari connection for the whole
    // method, including the ~100ms+ BCrypt comparison. Under a login flood that starves the small
    // pool (idle-in-transaction connections), cascading 500s to every DB-backed endpoint. Each
    // repository write here is independent, so a per-call auto-commit transaction is sufficient and
    // releases the connection before BCrypt runs.
    public AuthTokensResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email()).orElse(null);
        if (user == null) {
            // Dummy comparison so the response time does not reveal the account's existence.
            passwordEncoder.matches(request.password(), DUMMY_PASSWORD_HASH);
            throw new IllegalArgumentException(msg.get("auth.login.invalid"));
        }

        if (user.getPasswordHash() == null) {
            throw new IllegalArgumentException(msg.get("auth.login.oauth"));
        }

        // Check if account is locked due to too many failed attempts
        if (user.isAccountLocked()) {
            log.warn("Login attempt for locked account: {}", request.email());
            throw new IllegalStateException(msg.get("auth.login.locked", user.getRemainingLockoutMinutes()));
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.debug("Invalid password attempt for: {}", request.email());
            // Increment failed attempts and potentially lock the account
            user.incrementFailedLoginAttempts();
            userRepository.save(user);
            if (user.isAccountLocked()) {
                log.warn("Account locked due to failed attempts: {}", request.email());
                throw new IllegalStateException(msg.get("auth.login.locked", user.getRemainingLockoutMinutes()));
            }
            throw new IllegalArgumentException(msg.get("auth.login.invalid"));
        }

        if (!user.isEmailVerified()) {
            throw new IllegalStateException(msg.get("auth.email.not.verified"));
        }

        // Successful login - reset failed attempts
        if (user.getFailedLoginAttempts() > 0) {
            user.resetFailedLoginAttempts();
            userRepository.save(user);
        }

        if (!user.isAdmin() && adminEmailConfig.isAdminEmail(user.getEmail())) {
            user.setRole(UserRole.ADMIN);
            userRepository.save(user);
            log.info("AUTO-ADMIN-PROMOTION: {} promoted to ADMIN during login", user.getEmail());
        }

        log.info("User logged in: {}", user.getEmail());
        return generateTokens(user);
    }

    @Transactional
    public MessageResponse verifyEmail(String token) {
        String tokenHash = jwtService.hashToken(token);

        AuthToken authToken = authTokenRepository.findValidToken(tokenHash, TokenType.EMAIL_VERIFICATION, Instant.now())
            .orElseThrow(() -> new IllegalArgumentException(msg.get("auth.verify.invalid")));

        User user = authToken.getUser();
        user.markEmailVerified();
        authToken.markAsUsed();

        userRepository.save(user);
        authTokenRepository.save(authToken);

        authMailService.sendWelcomeEmail(user);
        authMailService.sendNewUserAdminNotification(user);

        log.info("Email verified for: {}", user.getEmail());
        return new MessageResponse(msg.get("auth.verify.success"));
    }

    @Transactional
    public MessageResponse resendVerification(ResendVerificationRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email()).orElse(null);

        // Always the same message — we do not reveal whether the account exists, is already verified,
        // or whether a cooldown is in progress (anti-enumeration). We send the email only when actually needed.
        boolean shouldSend = user != null
            && !user.isEmailVerified()
            && !authTokenRepository.hasRecentUnusedToken(user.getId(), TokenType.EMAIL_VERIFICATION, Instant.now().minus(RESEND_COOLDOWN));

        if (shouldSend) {
            sendVerificationEmail(user);
        }

        return new MessageResponse(msg.get("auth.resend.success"));
    }

    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email()).orElse(null);

        // A uniform message regardless of account existence, OAuth login (no password),
        // or cooldown (anti-enumeration). We send the email only when actually needed.
        boolean shouldSend = user != null
            && user.getPasswordHash() != null
            && !authTokenRepository.hasRecentUnusedToken(user.getId(), TokenType.PASSWORD_RESET, Instant.now().minus(RESEND_COOLDOWN));

        if (shouldSend) {
            sendPasswordResetEmail(user);
        }

        return new MessageResponse(msg.get("auth.forgot.success"));
    }

    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        String tokenHash = jwtService.hashToken(request.token());

        AuthToken authToken = authTokenRepository.findValidToken(tokenHash, TokenType.PASSWORD_RESET, Instant.now())
            .orElseThrow(() -> new IllegalArgumentException(msg.get("auth.reset.invalid")));

        User user = authToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        // A password reset confirms mailbox access → we lift any account lockout
        // (after 5 failed attempts) so the user can log in right away.
        user.resetFailedLoginAttempts();
        authToken.markAsUsed();

        // Invalidate all refresh tokens for this user (force re-login on all devices)
        authTokenRepository.deleteByUserIdAndTokenType(user.getId(), TokenType.REFRESH_TOKEN);

        userRepository.save(user);
        authTokenRepository.save(authToken);

        authMailService.sendPasswordChangedNotification(user);

        log.info("Password reset for: {}", user.getEmail());
        return new MessageResponse(msg.get("auth.reset.success"));
    }

    @Transactional
    public AuthTokensResponse refreshTokens(RefreshTokenRequest request) {
        String refreshToken = request.refreshToken();

        if (!jwtService.validateToken(refreshToken)) {
            throw new IllegalArgumentException(msg.get("auth.refresh.invalid"));
        }

        if (!jwtService.isRefreshToken(refreshToken)) {
            throw new IllegalArgumentException(msg.get("auth.refresh.invalid.type"));
        }

        // Verify refresh token exists in database (not revoked)
        String tokenHash = jwtService.hashToken(refreshToken);
        AuthToken storedToken = authTokenRepository.findValidToken(tokenHash, TokenType.REFRESH_TOKEN, Instant.now())
            .orElseThrow(() -> new IllegalArgumentException(msg.get("auth.refresh.revoked")));

        User user = storedToken.getUser();

        // Invalidate old refresh token (rotation)
        storedToken.markAsUsed();
        authTokenRepository.save(storedToken);

        log.debug("Tokens refreshed for: {}", user.getEmail());
        return generateTokens(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        String tokenHash = jwtService.hashToken(refreshToken);
        authTokenRepository.findByTokenHashAndTokenType(tokenHash, TokenType.REFRESH_TOKEN)
            .ifPresent(token -> {
                token.markAsUsed();
                authTokenRepository.save(token);
            });
    }

    private AuthTokensResponse generateTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        // Store refresh token hash in database for revocation capability
        String refreshTokenHash = jwtService.hashToken(refreshToken);
        Instant refreshExpiration = Instant.now().plusMillis(jwtService.getRefreshTokenExpirationMs());

        AuthToken storedRefreshToken = new AuthToken(user, refreshTokenHash, TokenType.REFRESH_TOKEN, refreshExpiration);
        authTokenRepository.save(storedRefreshToken);

        return new AuthTokensResponse(
            accessToken,
            refreshToken,
            jwtService.getAccessTokenExpirationSeconds()
        );
    }

    private void sendVerificationEmail(User user) {
        String token = jwtService.generateSecureToken();
        String tokenHash = jwtService.hashToken(token);
        Instant expiration = Instant.now().plus(EMAIL_VERIFICATION_EXPIRATION);

        AuthToken authToken = new AuthToken(user, tokenHash, TokenType.EMAIL_VERIFICATION, expiration);
        authTokenRepository.save(authToken);

        authMailService.sendVerificationEmail(user, token);
    }

    private void sendPasswordResetEmail(User user) {
        String token = jwtService.generateSecureToken();
        String tokenHash = jwtService.hashToken(token);
        Instant expiration = Instant.now().plus(PASSWORD_RESET_EXPIRATION);

        AuthToken authToken = new AuthToken(user, tokenHash, TokenType.PASSWORD_RESET, expiration);
        authTokenRepository.save(authToken);

        authMailService.sendPasswordResetEmail(user, token);
    }

    private String resolveLanguage(String requested) {
        if (requested != null && SUPPORTED_LANGUAGES.contains(requested)) {
            return requested;
        }
        return "pl";
    }
}
