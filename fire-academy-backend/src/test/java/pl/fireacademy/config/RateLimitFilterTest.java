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
import java.util.UUID;

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
    void shouldAllowNormalBrowsingOfPublicEndpoints() throws ServletException, IOException {
        // Given: a visitor browsing the catalog — well below the anonymous ceiling
        MockHttpServletResponse response = null;
        for (int i = 0; i < 30; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public/events");
            request.setRemoteAddr("192.168.1.1");
            response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, filterChain);
        }

        // Then: never throttled
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldBlockPublicRequestsAfterLimit() throws ServletException, IOException {
        // Given: an anonymous flood of the no-store catalog endpoint (every hit reaches the DB)
        when(messageSource.getMessage(eq("rate.limit.exceeded"), isNull(), any(Locale.class)))
            .thenReturn("Zbyt wiele żądań");

        MockHttpServletResponse blockedResponse = null;
        for (int i = 0; i <= 120; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public/events");
            request.setRemoteAddr("10.0.0.9");
            blockedResponse = new MockHttpServletResponse();
            filter.doFilterInternal(request, blockedResponse, filterChain);
        }

        // Then: the 121st request in the window is rejected
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), blockedResponse.getStatus());
        assertTrue(blockedResponse.getContentAsString().contains("TOO_MANY_REQUESTS"));
    }

    @Test
    void shouldRateLimitSitemapAndOgStubs() throws ServletException, IOException {
        // Given: the sitemap (three table scans) and the OG stubs share the anonymous bucket
        when(messageSource.getMessage(eq("rate.limit.exceeded"), isNull(), any(Locale.class)))
            .thenReturn("Zbyt wiele żądań");

        MockHttpServletResponse blockedResponse = null;
        for (int i = 0; i <= 120; i++) {
            // Alternating paths — one shared "public" bucket, not one per path
            String path = i % 2 == 0 ? "/sitemap.xml" : "/og/kadra/" + UUID.randomUUID();
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            request.setRemoteAddr("10.0.0.10");
            blockedResponse = new MockHttpServletResponse();
            filter.doFilterInternal(request, blockedResponse, filterChain);
        }

        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), blockedResponse.getStatus());
    }

    @Test
    void shouldNotRateLimitUnmatchedPaths() throws ServletException, IOException {
        // Given: paths outside the known buckets (e.g. the actuator health probe) stay unlimited
        MockHttpServletResponse response = null;
        for (int i = 0; i <= 200; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
            request.setRemoteAddr("192.168.1.50");
            response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, filterChain);
        }

        assertEquals(200, response.getStatus());
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
    void shouldPreferCfConnectingIpOverSpoofedXForwardedFor() throws ServletException, IOException {
        when(messageSource.getMessage(eq("rate.limit.exceeded"), isNull(), any(Locale.class)))
            .thenReturn("Zbyt wiele żądań");

        // Attacker rotates a spoofed X-Forwarded-For per request, but Cloudflare pins
        // CF-Connecting-IP to their real IP -> every request shares one bucket and gets limited.
        MockHttpServletResponse last = null;
        for (int i = 0; i <= 15; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setRemoteAddr("127.0.0.1");
            request.addHeader("CF-Connecting-IP", "203.0.113.7");
            request.addHeader("X-Forwarded-For", "10." + i + "." + i + ".1");
            last = new MockHttpServletResponse();
            filter.doFilterInternal(request, last, filterChain);
        }

        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), last.getStatus());
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
