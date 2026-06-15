package pl.fireacademy.api.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import pl.fireacademy.api.NotFoundException;
import pl.fireacademy.api.user.UserDtos.*;
import pl.fireacademy.domain.auth.AuthTokenRepository;
import pl.fireacademy.domain.user.User;
import pl.fireacademy.domain.user.UserRepository;
import pl.fireacademy.domain.user.UserRole;
import pl.fireacademy.infrastructure.i18n.MessageService;
import pl.fireacademy.infrastructure.security.JwtAuthenticationFilter;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AuthTokenRepository authTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private MessageService msg;
    @Mock private JwtAuthenticationFilter jwtAuthenticationFilter;
    @Mock private pl.fireacademy.infrastructure.storage.FileStorageService fileStorageService;

    @InjectMocks private UserService service;

    private User user;
    private UUID userId;

    @BeforeEach
    void setUp() throws Exception {
        userId = UUID.randomUUID();
        user = new User("jan@test.com", "Jan", "Kowalski", "123456789");
        setId(user, userId);
        user.setPasswordHash("encoded-password");
        user.setRole(UserRole.USER);
        user.markEmailVerified();
    }

    @Test
    void shouldGetCurrentUser() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserResponse result = service.getMe(userId);

        assertEquals("jan@test.com", result.email());
        assertEquals("Jan", result.firstName());
        assertEquals("USER", result.role());
        assertFalse(result.isAdmin());
        assertTrue(result.emailVerified());
        assertTrue(result.hasPassword());
        assertFalse(result.oauthLinked());
    }

    @Test
    void shouldThrowWhenUserNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        when(msg.get("error.user.not.found")).thenReturn("Nie znaleziono");

        assertThrows(NotFoundException.class, () -> service.getMe(userId));
    }

    @Test
    void shouldUpdateUserProfile() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        UpdateUserRequest request = new UpdateUserRequest("Anna", "Nowak", "987654321");
        service.updateMe(userId, request);

        assertEquals("Anna", user.getFirstName());
        assertEquals("Nowak", user.getLastName());
        assertEquals("987654321", user.getPhone());
        verify(jwtAuthenticationFilter).evictUser(userId);
    }

    @Test
    void shouldChangePassword() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPassword", "encoded-password")).thenReturn(true);
        when(passwordEncoder.encode("NewPassword123")).thenReturn("new-encoded");

        ChangePasswordRequest request = new ChangePasswordRequest("OldPassword", "NewPassword123");
        service.changePassword(userId, request);

        assertEquals("new-encoded", user.getPasswordHash());
        verify(jwtAuthenticationFilter).evictUser(userId);
    }

    @Test
    void shouldThrowWhenChangingPasswordForOAuthUser() {
        user.setPasswordHash(null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(msg.get("user.password.oauth")).thenReturn("OAuth user");

        assertThrows(IllegalStateException.class,
            () -> service.changePassword(userId, new ChangePasswordRequest("old", "new12345")));
    }

    @Test
    void shouldThrowWhenCurrentPasswordInvalid() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded-password")).thenReturn(false);
        when(msg.get("user.password.invalid")).thenReturn("Nieprawidłowe hasło");

        assertThrows(IllegalArgumentException.class,
            () -> service.changePassword(userId, new ChangePasswordRequest("wrong", "new12345")));
    }

    @Test
    void shouldDeleteAccountWithPasswordVerification() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123", "encoded-password")).thenReturn(true);

        service.deleteMe(userId, new DeleteAccountRequest("Password123"));

        verify(authTokenRepository).deleteAllByUserId(userId);
        verify(userRepository).deleteById(userId);
    }

    @Test
    void shouldDeleteOAuthAccountWithoutPassword() {
        user.setPasswordHash(null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        service.deleteMe(userId, new DeleteAccountRequest(null));

        verify(authTokenRepository).deleteAllByUserId(userId);
        verify(userRepository).deleteById(userId);
    }

    @Test
    void shouldThrowWhenDeletePasswordInvalid() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded-password")).thenReturn(false);
        when(msg.get("user.password.invalid")).thenReturn("Nieprawidłowe hasło");

        assertThrows(IllegalArgumentException.class,
            () -> service.deleteMe(userId, new DeleteAccountRequest("wrong")));
    }

    @Test
    void shouldThrowWhenDeleteWithNullPasswordForPasswordUser() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(msg.get("user.password.invalid")).thenReturn("Nieprawidłowe hasło");

        assertThrows(IllegalArgumentException.class,
            () -> service.deleteMe(userId, new DeleteAccountRequest(null)));
    }

    @Test
    void shouldUpdateNotifications() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        service.updateNotifications(userId, new UpdateNotificationsRequest(false));

        assertFalse(user.isEmailNotificationsEnabled());
        verify(jwtAuthenticationFilter).evictUser(userId);
    }

    @Test
    void shouldReturnAdminFlagForAdminUser() {
        user.setRole(UserRole.ADMIN);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserResponse result = service.getMe(userId);

        assertTrue(result.isAdmin());
        assertEquals("ADMIN", result.role());
    }

    private static void setId(User user, UUID id) throws Exception {
        Field idField = User.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(user, id);
    }
}
