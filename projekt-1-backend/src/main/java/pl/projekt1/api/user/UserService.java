package pl.projekt1.api.user;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.projekt1.domain.auth.AuthTokenRepository;
import pl.projekt1.domain.user.User;
import pl.projekt1.domain.user.UserRepository;
import pl.projekt1.infrastructure.i18n.MessageService;
import pl.projekt1.infrastructure.security.JwtAuthenticationFilter;

import java.util.UUID;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final MessageService msg;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public UserService(UserRepository userRepository, AuthTokenRepository authTokenRepository,
                       PasswordEncoder passwordEncoder, MessageService msg, JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.userRepository = userRepository;
        this.authTokenRepository = authTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.msg = msg;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    public UserDtos.UserResponse getMe(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("error.user.not.found")));
        return toResponse(user);
    }

    @Transactional
    public UserDtos.UserResponse updateMe(UUID userId, UserDtos.UpdateUserRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("error.user.not.found")));
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
            .orElseThrow(() -> new IllegalArgumentException(msg.get("error.user.not.found")));
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

    @Transactional
    public void deleteMe(UUID userId, UserDtos.DeleteAccountRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("error.user.not.found")));
        if (user.getPasswordHash() != null) {
            if (request.password() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                throw new IllegalArgumentException(msg.get("user.password.invalid"));
            }
        }
        authTokenRepository.deleteAllByUserId(userId);
        userRepository.deleteById(userId);
    }

    @Transactional
    public void updateNotifications(UUID userId, UserDtos.UpdateNotificationsRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException(msg.get("error.user.not.found")));
        user.setEmailNotificationsEnabled(request.enabled());
        userRepository.save(user);
        jwtAuthenticationFilter.evictUser(userId);
    }

    private UserDtos.UserResponse toResponse(User user) {
        return new UserDtos.UserResponse(
            user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
            user.getPhone(), user.getRole().name(),
            user.getRole() == pl.projekt1.domain.user.UserRole.ADMIN,
            user.isEmailVerified(), user.isEmailNotificationsEnabled(),
            user.getPreferredLanguage(), user.getPasswordHash() != null,
            user.getOauthProvider() != null, user.getCreatedAt()
        );
    }
}
