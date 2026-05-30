package pl.fireacademy.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.fireacademy.domain.user.User;
import pl.fireacademy.domain.user.UserRole;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private User testUser;

    @BeforeEach
    void setUp() throws Exception {
        JwtConfig config = new JwtConfig();
        config.setSecret("test-secret-key-that-is-at-least-32-characters-long-for-hmac");
        config.setAccessTokenExpirationMs(900_000);
        config.setRefreshTokenExpirationMs(604_800_000);
        config.setIssuer("fire-academy-test");

        jwtService = new JwtService(config);

        testUser = new User("test@example.com", "Jan", "Kowalski", null);
        setId(testUser, UUID.randomUUID());
        testUser.setRole(UserRole.USER);
    }

    @Test
    void shouldGenerateValidAccessToken() {
        String token = jwtService.generateAccessToken(testUser);

        assertNotNull(token);
        assertTrue(jwtService.validateToken(token));
        assertTrue(jwtService.isAccessToken(token));
        assertFalse(jwtService.isRefreshToken(token));
    }

    @Test
    void shouldGenerateValidRefreshToken() {
        String token = jwtService.generateRefreshToken(testUser);

        assertNotNull(token);
        assertTrue(jwtService.validateToken(token));
        assertTrue(jwtService.isRefreshToken(token));
        assertFalse(jwtService.isAccessToken(token));
    }

    @Test
    void shouldExtractUserIdFromToken() {
        String token = jwtService.generateAccessToken(testUser);

        UUID userId = jwtService.extractUserId(token);

        assertEquals(testUser.getId(), userId);
    }

    @Test
    void shouldExtractEmailFromToken() {
        String token = jwtService.generateAccessToken(testUser);

        String email = jwtService.extractEmail(token);

        assertEquals("test@example.com", email);
    }

    @Test
    void shouldRejectInvalidToken() {
        assertFalse(jwtService.validateToken("invalid.token.here"));
    }

    @Test
    void shouldRejectTokenWithWrongSecret() {
        String token = jwtService.generateAccessToken(testUser);

        JwtConfig otherConfig = new JwtConfig();
        otherConfig.setSecret("different-secret-key-that-is-also-at-least-32-characters-long");
        otherConfig.setAccessTokenExpirationMs(900_000);
        otherConfig.setRefreshTokenExpirationMs(604_800_000);
        otherConfig.setIssuer("fire-academy-test");
        JwtService otherService = new JwtService(otherConfig);

        assertFalse(otherService.validateToken(token));
    }

    @Test
    void shouldRejectExpiredToken() {
        JwtConfig config = new JwtConfig();
        config.setSecret("test-secret-key-that-is-at-least-32-characters-long-for-hmac");
        config.setAccessTokenExpirationMs(0);
        config.setRefreshTokenExpirationMs(0);
        config.setIssuer("fire-academy-test");
        JwtService expiredService = new JwtService(config);

        String token = expiredService.generateAccessToken(testUser);

        assertFalse(expiredService.validateToken(token));
    }

    @Test
    void shouldReturnFalseForIsAccessTokenWhenInvalidToken() {
        assertFalse(jwtService.isAccessToken("invalid.token"));
    }

    @Test
    void shouldReturnFalseForIsRefreshTokenWhenInvalidToken() {
        assertFalse(jwtService.isRefreshToken("invalid.token"));
    }

    @Test
    void shouldGenerateUniqueSecureTokens() {
        String token1 = jwtService.generateSecureToken();
        String token2 = jwtService.generateSecureToken();

        assertNotNull(token1);
        assertNotNull(token2);
        assertNotEquals(token1, token2);
    }

    @Test
    void shouldHashTokenConsistently() {
        String token = "test-token-value";
        String hash1 = jwtService.hashToken(token);
        String hash2 = jwtService.hashToken(token);

        assertEquals(hash1, hash2);
        assertEquals(64, hash1.length());
    }

    @Test
    void shouldProduceDifferentHashesForDifferentTokens() {
        String hash1 = jwtService.hashToken("token-a");
        String hash2 = jwtService.hashToken("token-b");

        assertNotEquals(hash1, hash2);
    }

    @Test
    void shouldReturnCorrectExpirationSeconds() {
        assertEquals(900, jwtService.getAccessTokenExpirationSeconds());
    }

    @Test
    void shouldReturnCorrectRefreshExpirationMs() {
        assertEquals(604_800_000, jwtService.getRefreshTokenExpirationMs());
    }

    @Test
    void shouldGenerateTokenForAdminUser() {
        testUser.setRole(UserRole.ADMIN);
        String token = jwtService.generateAccessToken(testUser);

        assertTrue(jwtService.validateToken(token));
        assertEquals(testUser.getId(), jwtService.extractUserId(token));
    }

    private static void setId(User user, UUID id) throws Exception {
        Field idField = User.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(user, id);
    }
}
