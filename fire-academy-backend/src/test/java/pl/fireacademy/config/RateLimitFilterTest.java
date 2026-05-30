package pl.fireacademy.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock private MessageSource messageSource;
    @Mock private FilterChain filterChain;

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(messageSource);
    }

    @Test
    void shouldAllowRequestBelowLimit() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/login");
        request.setRemoteAddr("192.168.1.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldBlockAuthRequestsAfterLimit() throws ServletException, IOException {
        when(messageSource.getMessage(eq("rate.limit.exceeded"), isNull(), any(Locale.class)))
            .thenReturn("Zbyt wiele żądań");

        MockHttpServletResponse blockedResponse = null;
        for (int i = 0; i <= 15; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setRemoteAddr("10.0.0.1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, filterChain);
            blockedResponse = response;
        }

        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), blockedResponse.getStatus());
        assertTrue(blockedResponse.getContentAsString().contains("TOO_MANY_REQUESTS"));
    }

    @Test
    void shouldPassThroughPublicEndpoints() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public/events");
        request.setRemoteAddr("192.168.1.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldTrackDifferentBucketsSeparately() throws ServletException, IOException {
        for (int i = 0; i < 15; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setRemoteAddr("172.16.0.1");
            filter.doFilterInternal(request, new MockHttpServletResponse(), filterChain);
        }

        MockHttpServletRequest userRequest = new MockHttpServletRequest("GET", "/api/user/me");
        userRequest.setRemoteAddr("172.16.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(userRequest, response, filterChain);

        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldUseXForwardedForHeader() throws ServletException, IOException {
        when(messageSource.getMessage(eq("rate.limit.exceeded"), isNull(), any(Locale.class)))
            .thenReturn("Zbyt wiele żądań");

        for (int i = 0; i <= 15; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setRemoteAddr("127.0.0.1");
            request.addHeader("X-Forwarded-For", "203.0.113.50, 70.41.3.18");
            filter.doFilterInternal(request, new MockHttpServletResponse(), filterChain);
        }

        MockHttpServletRequest differentIp = new MockHttpServletRequest("POST", "/api/auth/login");
        differentIp.setRemoteAddr("127.0.0.1");
        differentIp.addHeader("X-Forwarded-For", "198.51.100.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(differentIp, response, filterChain);

        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldAllowHigherLimitForAdminEndpoints() throws ServletException, IOException {
        for (int i = 0; i < 60; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/instructors");
            request.setRemoteAddr("10.10.10.1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, filterChain);
            assertEquals(200, response.getStatus());
        }
    }

    @Test
    void shouldReturnJsonErrorResponse() throws ServletException, IOException {
        when(messageSource.getMessage(eq("rate.limit.exceeded"), isNull(), any(Locale.class)))
            .thenReturn("Zbyt wiele żądań");

        for (int i = 0; i <= 15; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");
            request.setRemoteAddr("10.20.30.1");
            filter.doFilterInternal(request, new MockHttpServletResponse(), filterChain);
        }

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/register");
        request.setRemoteAddr("10.20.30.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, filterChain);

        assertTrue(response.getContentType().startsWith("application/json"));
        assertTrue(response.getContentAsString().contains("\"message\":\"Zbyt wiele żądań\""));
    }
}
