package pl.fireacademy.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import pl.fireacademy.BaseIntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SecurityIntegrationTest extends BaseIntegrationTest {

    @Test
    void shouldAllowPublicEndpointsWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/public/instructors").param("category", "TRAINING"))
            .andExpect(status().isOk());
    }

    @Test
    void shouldAllowAuthEndpointsWithoutAuth() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"x@x.com\",\"password\":\"12345678\"}"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    void shouldReject401ForUserEndpointWithoutToken() throws Exception {
        mockMvc.perform(get("/api/user/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldReject401ForAdminEndpointWithoutToken() throws Exception {
        mockMvc.perform(get("/api/admin/instructors"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReject403ForAdminEndpointWithUserToken() throws Exception {
        String token = userToken();
        mockMvc.perform(get("/api/admin/instructors")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void shouldAllowAdminEndpointWithAdminToken() throws Exception {
        String token = adminToken();
        mockMvc.perform(get("/api/admin/instructors")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
    }

    @Test
    void shouldAllowUserEndpointWithUserToken() throws Exception {
        String token = userToken();
        mockMvc.perform(get("/api/user/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
    }

    @Test
    void shouldRejectInvalidJwtToken() throws Exception {
        mockMvc.perform(get("/api/user/me")
                .header("Authorization", "Bearer invalid.jwt.token"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowAdminToAccessUserEndpoints() throws Exception {
        String token = adminToken();
        mockMvc.perform(get("/api/user/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
    }

    @Test
    void shouldReturnJsonErrorForUnauthorized() throws Exception {
        mockMvc.perform(get("/api/user/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.timestamp").exists());
    }
}
