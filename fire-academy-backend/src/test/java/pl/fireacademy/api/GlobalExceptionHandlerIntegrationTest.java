package pl.fireacademy.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import pl.fireacademy.BaseIntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Sprawdza, że standardowe wyjątki Spring MVC są mapowane na właściwe kody HTTP przez
 * GlobalExceptionHandler (a nie połykane przez catch-all jako 500). Regresja: rejestracja
 * z pominiętym polem prymitywnym (`acceptedPrivacy`) zwracała 500 zamiast 400.
 */
class GlobalExceptionHandlerIntegrationTest extends BaseIntegrationTest {

    @Test
    void shouldReturn400WhenRegisterBodyOmitsPrimitiveField() throws Exception {
        // Brak `acceptedPrivacy` (prymityw boolean) → HttpMessageNotReadableException, nie 500.
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"omit-primitive@test.com","password":"StrongPass123","firstName":"Jan","lastName":"Kowalski","phone":"123456789"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldReturn404ForUnknownApiPath() throws Exception {
        mockMvc.perform(get("/api/public/nonexistent"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void shouldReturn405ForUnsupportedHttpMethod() throws Exception {
        mockMvc.perform(post("/api/public/events"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }
}
