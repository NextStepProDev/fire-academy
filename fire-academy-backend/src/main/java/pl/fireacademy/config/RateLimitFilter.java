package pl.fireacademy.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.MessageSource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final List<Locale> SUPPORTED_LOCALES = List.of(
        Locale.of("pl")
    );

    private static final int AUTH_LIMIT = 15;
    private static final int USER_LIMIT = 20;
    private static final int ADMIN_LIMIT = 60;
    // The personal training calendar needs its own budget. Browsing it generates far more requests than
    // the rest of /api/user/** put together (range fetch per week/month navigation, unread summary on every
    // window focus, mark-seen, comment threads), and sharing the 20/min user bucket would lock a coaching
    // client out of their own plan after a couple of minutes of normal use.
    private static final int MY_TRAINING_LIMIT = 120;
    // Anonymous read-only traffic (catalog, OG stubs, sitemap). Set well above what a person browsing the
    // site generates, so it never bites a real visitor — it exists to cap a flood. These endpoints hit the
    // DB on every request (availability counts are deliberately no-store, and the sitemap scans three
    // tables), so without a ceiling an unauthenticated client can drain the small Hikari pool on its own.
    private static final int PUBLIC_LIMIT = 120;

    private final Cache<String, AtomicInteger> requestCounts = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(1))
        .maximumSize(10_000)
        .build();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final MessageSource messageSource;

    public RateLimitFilter(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        int limit = resolveLimit(path);

        if (limit <= 0) {
            filterChain.doFilter(request, response);
            return;
        }

        String cacheKey = getClientIp(request) + ":" + resolveBucket(path);
        AtomicInteger counter = requestCounts.get(cacheKey, k -> new AtomicInteger(0));
        int count = counter.incrementAndGet();

        if (count > limit) {
            Locale locale = resolveLocale(request);
            String message = messageSource.getMessage("rate.limit.exceeded", null, locale);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            OBJECT_MAPPER.writeValue(response.getWriter(),
                Map.of("code", "TOO_MANY_REQUESTS", "message", message, "timestamp", Instant.now().toString()));
            return;
        }

        filterChain.doFilter(request, response);
    }

    // NOTE: the two methods below must test prefixes in the SAME order, most specific first. A bucket
    // resolved from a different branch than its limit would silently pair the wrong ceiling with the
    // wrong counter — hence MY_TRAINING_PATH before the generic /api/user/ in both.
    private static final String MY_TRAINING_PATH = "/api/user/my-training/";

    private int resolveLimit(String path) {
        if (path.startsWith("/api/auth/")) return AUTH_LIMIT;
        if (path.startsWith(MY_TRAINING_PATH)) return MY_TRAINING_LIMIT;
        if (path.startsWith("/api/user/")) return USER_LIMIT;
        if (path.startsWith("/api/admin/")) return ADMIN_LIMIT;
        if (isPublicPath(path)) return PUBLIC_LIMIT;
        return 0;
    }

    private String resolveBucket(String path) {
        if (path.startsWith("/api/auth/")) return "auth";
        if (path.startsWith(MY_TRAINING_PATH)) return "mytraining";
        if (path.startsWith("/api/user/")) return "user";
        if (path.startsWith("/api/admin/")) return "admin";
        if (isPublicPath(path)) return "public";
        return "default";
    }

    /** Unauthenticated, DB-backed reads: the public catalog API, the OG crawler stubs and the sitemap. */
    private static boolean isPublicPath(String path) {
        return path.startsWith("/api/public/") || path.startsWith("/og/") || path.equals("/sitemap.xml");
    }

    private Locale resolveLocale(HttpServletRequest request) {
        String header = request.getHeader("Accept-Language");
        if (header != null && !header.isEmpty()) {
            String lang = header.split("[,;_-]")[0].trim().toLowerCase();
            for (Locale supported : SUPPORTED_LOCALES) {
                if (supported.getLanguage().equals(lang)) {
                    return supported;
                }
            }
        }
        return Locale.of("pl");
    }

    private String getClientIp(HttpServletRequest request) {
        // Cloudflare sets CF-Connecting-IP to the true client IP on every proxied request, and a
        // client cannot forge it as long as the origin only accepts traffic from Cloudflare. Trust
        // it first: unlike the first X-Forwarded-For token (which a client can set to rotate past
        // the per-IP limiter — confirmed bypassable in prod), this header is controlled by our edge.
        // Fall back to X-Forwarded-For, then the socket address, for non-Cloudflare envs (local/dev).
        String cfIp = request.getHeader("CF-Connecting-IP");
        if (cfIp != null && !cfIp.isBlank()) {
            return cfIp.trim();
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
