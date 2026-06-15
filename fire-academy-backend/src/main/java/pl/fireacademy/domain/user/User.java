package pl.fireacademy.domain.user;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column
    @Nullable
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role = UserRole.USER;

    @Column(name = "oauth_provider")
    @Nullable
    private String oauthProvider;

    @Column(name = "oauth_id")
    @Nullable
    private String oauthId;

    @Column(name = "password_hash")
    @Nullable
    private String passwordHash;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "email_verified_at")
    @Nullable
    private Instant emailVerifiedAt;

    @Column(name = "avatar_filename")
    @Nullable
    private String avatarFilename;

    @Column(name = "email_notifications_enabled", nullable = false)
    private boolean emailNotificationsEnabled = true;

    @Column(name = "preferred_language", nullable = false)
    private String preferredLanguage = "pl";

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    @Nullable
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {}

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public User(String email, String firstName, String lastName, @Nullable String phone) {
        this.email = normalizeEmail(email);
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    @Nullable
    public String getPhone() {
        return phone;
    }

    public void setPhone(@Nullable String phone) {
        this.phone = phone;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    @Nullable
    public String getOauthProvider() {
        return oauthProvider;
    }

    public void setOauthProvider(@Nullable String oauthProvider) {
        this.oauthProvider = oauthProvider;
    }

    @Nullable
    public String getOauthId() {
        return oauthId;
    }

    public void setOauthId(@Nullable String oauthId) {
        this.oauthId = oauthId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    @Nullable
    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(@Nullable String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    @Nullable
    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public void setEmailVerifiedAt(@Nullable Instant emailVerifiedAt) {
        this.emailVerifiedAt = emailVerifiedAt;
    }

    @Nullable
    public String getAvatarFilename() {
        return avatarFilename;
    }

    public void setAvatarFilename(@Nullable String avatarFilename) {
        this.avatarFilename = avatarFilename;
    }

    public void markEmailVerified() {
        this.emailVerified = true;
        this.emailVerifiedAt = Instant.now();
    }

    public boolean hasPassword() {
        return passwordHash != null;
    }

    public boolean isEmailNotificationsEnabled() {
        return emailNotificationsEnabled;
    }

    public void setEmailNotificationsEnabled(boolean emailNotificationsEnabled) {
        this.emailNotificationsEnabled = emailNotificationsEnabled;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public void setPreferredLanguage(String preferredLanguage) {
        this.preferredLanguage = preferredLanguage;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    @Nullable
    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public boolean isAccountLocked() {
        return lockedUntil != null && Instant.now().isBefore(lockedUntil);
    }

    public void incrementFailedLoginAttempts() {
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= 5) {
            this.lockedUntil = Instant.now().plusSeconds(15 * 60);
        }
    }

    public long getRemainingLockoutMinutes() {
        if (lockedUntil == null) return 0;
        long seconds = Duration.between(Instant.now(), lockedUntil).getSeconds();
        if (seconds <= 0) return 0;
        return (seconds + 59) / 60;
    }

    public void resetFailedLoginAttempts() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }
}
