package pl.fireacademy.api.user;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pl.fireacademy.api.NotFoundException;
import pl.fireacademy.domain.auth.AuthTokenRepository;
import pl.fireacademy.domain.user.User;
import pl.fireacademy.domain.user.UserRepository;
import pl.fireacademy.infrastructure.i18n.MessageService;
import pl.fireacademy.infrastructure.security.JwtAuthenticationFilter;
import pl.fireacademy.infrastructure.storage.FileStorageService;

import java.util.UUID;

@Service
public class UserService {
    private static final String AVATAR_FOLDER = "avatars";

    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final MessageService msg;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final FileStorageService fileStorageService;

    public UserService(UserRepository userRepository, AuthTokenRepository authTokenRepository,
                       PasswordEncoder passwordEncoder, MessageService msg, JwtAuthenticationFilter jwtAuthenticationFilter,
                       FileStorageService fileStorageService) {
        this.userRepository = userRepository;
        this.authTokenRepository = authTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.msg = msg;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.fileStorageService = fileStorageService;
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

    @Transactional
    public void deleteMe(UUID userId, UserDtos.DeleteAccountRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException(msg.get("error.user.not.found")));
        if (user.getPasswordHash() != null) {
            if (request.password() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                throw new IllegalArgumentException(msg.get("user.password.invalid"));
            }
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
    public void updateNotifications(UUID userId, UserDtos.UpdateNotificationsRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException(msg.get("error.user.not.found")));
        user.setEmailNotificationsEnabled(request.enabled());
        userRepository.save(user);
        jwtAuthenticationFilter.evictUser(userId);
    }

    private UserDtos.UserResponse toResponse(User user) {
        String avatarUrl = user.getAvatarFilename() != null
            ? "/api/files/" + AVATAR_FOLDER + "/" + user.getAvatarFilename()
            : null;
        return new UserDtos.UserResponse(
            user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
            user.getPhone(), user.getRole().name(),
            user.getRole() == pl.fireacademy.domain.user.UserRole.ADMIN,
            user.isEmailVerified(), user.isEmailNotificationsEnabled(),
            user.getPreferredLanguage(), user.getPasswordHash() != null,
            user.getOauthProvider() != null, avatarUrl, user.getCreatedAt()
        );
    }
}
