package pl.fireacademy;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import pl.fireacademy.domain.user.User;
import pl.fireacademy.domain.user.UserRepository;
import pl.fireacademy.domain.user.UserRole;
import pl.fireacademy.infrastructure.security.JwtService;

import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@org.springframework.test.context.jdbc.Sql(scripts = "/cleanup.sql", executionPhase = org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public abstract class BaseIntegrationTest {

    static final PostgreSQLContainer<?> postgres;

    static {
        postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("fireacademy_test")
            .withUsername("test")
            .withPassword("test");
        postgres.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.jwt.secret", () -> "test-secret-key-that-is-at-least-32-characters-long-for-hmac-sha");
        registry.add("app.admin.email", () -> "admin@fireacademy.test");
        registry.add("app.storage.root", () -> System.getProperty("java.io.tmpdir") + "/fire-academy-test-uploads");
        registry.add("app.base-url", () -> "http://localhost:8081");
        registry.add("app.site-url", () -> "http://localhost:5174");
        registry.add("app.cors.allowed-origins", () -> "http://localhost:5174");
    }

    @Autowired
    protected WebApplicationContext webApplicationContext;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected JwtService jwtService;

    protected MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();
    }

    protected String createUserAndGetToken(String email, String firstName, String lastName, UserRole role) {
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User u = new User(email, firstName, lastName, null);
            u.setPasswordHash("$2a$12$dummyhash");
            u.setRole(role);
            u.markEmailVerified();
            return userRepository.save(u);
        });
        return jwtService.generateAccessToken(user);
    }

    protected String adminToken() {
        return createUserAndGetToken("testadmin@fireacademy.test", "Admin", "Testowy", UserRole.ADMIN);
    }

    protected String userToken() {
        return createUserAndGetToken("testuser@fireacademy.test", "User", "Testowy", UserRole.USER);
    }

    protected UUID adminUserId() {
        return userRepository.findByEmail("testadmin@fireacademy.test")
            .orElseThrow().getId();
    }

    protected UUID regularUserId() {
        return userRepository.findByEmail("testuser@fireacademy.test")
            .orElseThrow().getId();
    }
}
