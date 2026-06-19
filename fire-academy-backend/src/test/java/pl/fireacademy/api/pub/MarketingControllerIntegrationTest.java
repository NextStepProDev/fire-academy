package pl.fireacademy.api.pub;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import pl.fireacademy.BaseIntegrationTest;
import pl.fireacademy.domain.user.User;
import pl.fireacademy.domain.user.UserRole;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MarketingControllerIntegrationTest extends BaseIntegrationTest {

    @Test
    void shouldRevokeMarketingConsentWithoutAuthenticationWhenTokenIsValid() throws Exception {
        User user = createUserWithMarketingConsent("opted-in@test.com");

        mockMvc.perform(post("/api/public/marketing/unsubscribe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\"}".formatted(user.getMarketingUnsubscribeToken())))
            .andExpect(status().isNoContent());

        assertFalse(userRepository.findById(user.getId()).orElseThrow().hasMarketingConsent());
    }

    @Test
    void shouldReturnNoContentForUnknownTokenWithoutLeakingAccountExistence() throws Exception {
        mockMvc.perform(post("/api/public/marketing/unsubscribe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\"}".formatted(UUID.randomUUID())))
            .andExpect(status().isNoContent());
    }

    @Test
    void shouldReturnNoContentForMalformedTokenWithoutLeakingValidation() throws Exception {
        mockMvc.perform(post("/api/public/marketing/unsubscribe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"not-a-uuid\"}"))
            .andExpect(status().isNoContent());
    }

    @Test
    void shouldBeIdempotentWhenUserHasNoMarketingConsent() throws Exception {
        User user = createUserWithoutMarketingConsent("never-opted-in@test.com");

        mockMvc.perform(post("/api/public/marketing/unsubscribe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\"}".formatted(user.getMarketingUnsubscribeToken())))
            .andExpect(status().isNoContent());

        assertFalse(userRepository.findById(user.getId()).orElseThrow().hasMarketingConsent());
    }

    @Test
    void shouldNotAffectOtherUsersConsentWhenOneUnsubscribes() throws Exception {
        User target = createUserWithMarketingConsent("target@test.com");
        User bystander = createUserWithMarketingConsent("bystander@test.com");

        mockMvc.perform(post("/api/public/marketing/unsubscribe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\"}".formatted(target.getMarketingUnsubscribeToken())))
            .andExpect(status().isNoContent());

        assertTrue(userRepository.findById(bystander.getId()).orElseThrow().hasMarketingConsent());
    }

    private User createUserWithMarketingConsent(String email) {
        User user = new User(email, "Jan", "Kowalski", null);
        user.setRole(UserRole.USER);
        user.markEmailVerified();
        user.setMarketingConsentAt(Instant.now());
        return userRepository.save(user);
    }

    private User createUserWithoutMarketingConsent(String email) {
        User user = new User(email, "Anna", "Nowak", null);
        user.setRole(UserRole.USER);
        user.markEmailVerified();
        return userRepository.save(user);
    }
}
