package pl.fireacademy.api.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import pl.fireacademy.BaseIntegrationTest;
import pl.fireacademy.domain.enrollment.Enrollment;
import pl.fireacademy.domain.enrollment.EnrollmentRepository;
import pl.fireacademy.domain.event.Event;
import pl.fireacademy.domain.event.EventCategory;
import pl.fireacademy.domain.event.EventRepository;
import pl.fireacademy.domain.user.User;
import pl.fireacademy.domain.user.UserRole;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserControllerIntegrationTest extends BaseIntegrationTest {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(12);

    @Autowired private EventRepository eventRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;

    @Test
    void shouldGetCurrentUserProfile() throws Exception {
        String token = userToken();
        mockMvc.perform(get("/api/user/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("testuser@fireacademy.test"))
            .andExpect(jsonPath("$.firstName").value("User"))
            .andExpect(jsonPath("$.role").value("USER"))
            .andExpect(jsonPath("$.isAdmin").value(false))
            .andExpect(jsonPath("$.emailVerified").value(true));
    }

    @Test
    void shouldUpdateUserProfile() throws Exception {
        String token = userToken();
        mockMvc.perform(put("/api/user/me")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"firstName":"Updated","lastName":"UserName","phone":"111222333"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.firstName").value("Updated"));
    }

    @Test
    void shouldRejectUpdateWithInvalidData() throws Exception {
        String token = userToken();
        mockMvc.perform(put("/api/user/me")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"firstName":"Ab","lastName":"C","phone":null}
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldChangePassword() throws Exception {
        User user = new User("changepw-it@test.com", "Jan", "Kowalski", null);
        user.setPasswordHash(ENCODER.encode("OldPassword123"));
        user.setRole(UserRole.USER);
        user.markEmailVerified();
        userRepository.save(user);
        String token = jwtService.generateAccessToken(user);

        mockMvc.perform(put("/api/user/me/password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"currentPassword":"OldPassword123","newPassword":"NewPassword456"}
                    """))
            .andExpect(status().isOk());
    }

    @Test
    void shouldRejectWrongCurrentPassword() throws Exception {
        User user = new User("wrongpw-it@test.com", "Jan", "Kowalski", null);
        user.setPasswordHash(ENCODER.encode("CorrectPassword"));
        user.setRole(UserRole.USER);
        user.markEmailVerified();
        userRepository.save(user);
        String token = jwtService.generateAccessToken(user);

        mockMvc.perform(put("/api/user/me/password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"currentPassword":"WrongPassword","newPassword":"NewPassword456"}
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldDeleteAccount() throws Exception {
        User user = new User("delete-it@test.com", "Jan", "Kowalski", null);
        user.setPasswordHash(ENCODER.encode("DeleteMe123"));
        user.setRole(UserRole.USER);
        user.markEmailVerified();
        userRepository.save(user);
        String token = jwtService.generateAccessToken(user);

        mockMvc.perform(delete("/api/user/me")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"DeleteMe123\"}"))
            .andExpect(status().isNoContent());
    }

    @Test
    void shouldAnonymizeHistoryAndFreeFutureWhenSelfDeletingAccount() throws Exception {
        User user = new User("erase-it@test.com", "Jan", "Kowalski", "123456789");
        user.setPasswordHash(ENCODER.encode("DeleteMe123"));
        user.setRole(UserRole.USER);
        user.markEmailVerified();
        userRepository.save(user);
        String token = jwtService.generateAccessToken(user);

        Event futureEvent = eventRepository.save(
                new Event(EventCategory.CAMP, "Przyszły obóz", LocalDate.now().plusDays(10)));
        Event pastEvent = eventRepository.save(
                new Event(EventCategory.COURSE, "Minione szkolenie", LocalDate.now().minusDays(20)));
        Enrollment future = enrollmentRepository.save(Enrollment.forUser(futureEvent, user, null, false));
        Enrollment past = enrollmentRepository.save(Enrollment.forUser(pastEvent, user, "notka", false));

        mockMvc.perform(delete("/api/user/me")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"DeleteMe123\"}"))
            .andExpect(status().isNoContent());

        // Konto usunięte, przyszły zapis skasowany (miejsce wolne), przeszły zanonimizowany i odłączony.
        assertTrue(userRepository.findById(user.getId()).isEmpty());
        assertTrue(enrollmentRepository.findById(future.getId()).isEmpty());
        Enrollment archived = enrollmentRepository.findById(past.getId()).orElseThrow();
        assertTrue(archived.isAnonymized());
        assertNull(archived.getUser());
    }

    @Test
    void shouldRecordMandatoryPrivacyConsentForOauthUser() throws Exception {
        User user = new User("consents-it@test.com", "Ola", "Google", null);
        user.setOauthProvider("google");
        user.setOauthId("oauth-consents-1");
        user.setRole(UserRole.USER);
        user.markEmailVerified();
        userRepository.save(user);
        assertFalse(user.hasPrivacyAccepted());
        String token = jwtService.generateAccessToken(user);

        mockMvc.perform(post("/api/user/me/consents")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"acceptedPrivacy\":true,\"acceptedMarketing\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.privacyAccepted").value(true))
            .andExpect(jsonPath("$.marketingConsent").value(true));

        User refreshed = userRepository.findById(user.getId()).orElseThrow();
        assertTrue(refreshed.hasPrivacyAccepted());
        assertTrue(refreshed.hasMarketingConsent());
    }

    @Test
    void shouldRejectConsentsWhenMandatoryPrivacyDeclined() throws Exception {
        User user = new User("consents-decline-it@test.com", "Ola", "Google", null);
        user.setOauthProvider("google");
        user.setOauthId("oauth-consents-2");
        user.setRole(UserRole.USER);
        user.markEmailVerified();
        userRepository.save(user);
        String token = jwtService.generateAccessToken(user);

        mockMvc.perform(post("/api/user/me/consents")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"acceptedPrivacy\":false,\"acceptedMarketing\":false}"))
            .andExpect(status().isBadRequest());

        // Polityka obowiązkowa: brak akceptacji nie może zapisać żadnej zgody.
        User refreshed = userRepository.findById(user.getId()).orElseThrow();
        assertFalse(refreshed.hasPrivacyAccepted());
        assertFalse(refreshed.hasMarketingConsent());
    }

    @Test
    void shouldReturnAdminProfileForAdminUser() throws Exception {
        String token = adminToken();
        mockMvc.perform(get("/api/user/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("ADMIN"))
            .andExpect(jsonPath("$.isAdmin").value(true));
    }
}
