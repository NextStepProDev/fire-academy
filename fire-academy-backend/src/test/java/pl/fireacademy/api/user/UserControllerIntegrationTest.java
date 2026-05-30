package pl.fireacademy.api.user;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import pl.fireacademy.BaseIntegrationTest;
import pl.fireacademy.domain.user.User;
import pl.fireacademy.domain.user.UserRole;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserControllerIntegrationTest extends BaseIntegrationTest {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(12);

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
    void shouldUpdateNotificationSettings() throws Exception {
        String token = userToken();
        mockMvc.perform(put("/api/user/me/notifications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}"))
            .andExpect(status().isNoContent());
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
