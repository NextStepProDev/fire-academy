package pl.fireacademy.architecture;

import org.junit.jupiter.api.Test;
import pl.fireacademy.config.RateLimitFilter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every controller base path must land in a rate-limit bucket, and land in the same one whether it
 * is called on the bare base or on a sub-path.
 *
 * <p>Both halves come from the same failure mode. A rule written as {@code startsWith("/api/x/")}
 * covers a feature to the eye, but misses any endpoint mapped on the bare base — it carries no
 * trailing slash. In the sibling climbing app that is exactly how the heaviest query in the
 * application ran unthrottled for months: nothing about the rule looked wrong, you had to hold the
 * URI shape and the prefix in your head at the same time to see it. Fire Academy has no such
 * endpoint today, which is precisely why the trap is worth closing now rather than after one appears.
 *
 * <p>The filter denies by default, so a forgotten controller falls into the generic bucket instead
 * of through the filter untouched. This gate is what turns "it gets some limit" into "somebody chose
 * its limit": a new base path nobody thought about shows up here as the generic bucket at the moment
 * it is added, rather than in an audit two years later.
 */
class RateLimitCoverageTest {

    private static final Path API_ROOT = SourceFiles.mainJavaRoot().resolve("pl/fireacademy/api");

    /**
     * Class-level base path, e.g. {@code @RequestMapping("/api/admin/events")}. Deliberately not
     * limited to {@code /api}: the OG crawler stubs live at {@code /og} and are DB-backed reads like
     * any other, so they have to be covered too.
     */
    private static final Pattern CLASS_MAPPING = Pattern.compile(
        "@RequestMapping\\s*\\(\\s*(?:value\\s*=\\s*)?\"(/[^\"]*)\"");

    /**
     * Bases whose traffic is deliberately generic rather than given a bucket of its own. Keep this an
     * explicit allowlist: an entry here is a decision someone made, an entry missing from the rule
     * table is an oversight, and the two must not look alike.
     */
    private static final List<String> INTENTIONALLY_GENERIC = List.of(
        "/api/dev" // dev profile only; never mapped in production
    );

    @Test
    void shouldGiveEveryControllerBasePathItsOwnRateLimitBucket() {
        for (String base : controllerBasePaths()) {
            String bucket = RateLimitFilter.bucketFor(base);
            assertNotNull(bucket, base + " is not rate limited at all.");

            if (INTENTIONALLY_GENERIC.contains(base)) {
                continue;
            }
            assertNotEquals("default", bucket, """
                %s falls into the generic "default" bucket, so nobody picked a limit for it.

                Add a rule for it in RateLimitFilter.RULES with a ceiling that fits what the endpoint
                actually costs (a write, a cached read, a file stream, a multipart upload), or — if
                generic really is the right answer — say so by listing the base in
                INTENTIONALLY_GENERIC here.
                """.formatted(base));
        }
    }

    @Test
    void shouldCountTheBareBaseAndItsSubPathsIntoTheSameBucket() {
        for (String base : controllerBasePaths()) {
            String bareBucket = RateLimitFilter.bucketFor(base);
            String subPathBucket = RateLimitFilter.bucketFor(base + "/anything");

            assertEquals(subPathBucket, bareBucket, """
                %s is counted into "%s" on the bare path but "%s" on a sub-path.

                An endpoint mapped on the bare base carries no trailing slash, so a rule written as
                startsWith(base + "/") misses it entirely and the request passes with whatever
                ceiling a broader rule happens to give it — or none. Match with under(path, base),
                which accepts both.
                """.formatted(base, bareBucket, subPathBucket));
        }
    }

    /** Google sign-in is a sign-in attempt, and it does not live under /api. */
    @Test
    void shouldThrottleTheOauth2LoginPathsAsAuthentication() {
        assertEquals("auth", RateLimitFilter.bucketFor("/oauth2/authorization/google"));
        assertEquals("auth", RateLimitFilter.bucketFor("/login/oauth2/code/google"));
    }

    /** Mapped with @GetMapping rather than a class-level base, so the scan above cannot see it. */
    @Test
    void shouldThrottleTheSitemapWithTheOtherAnonymousReads() {
        assertEquals("public", RateLimitFilter.bucketFor("/sitemap.xml"));
    }

    /**
     * The container healthcheck polls this every few seconds from one address. A 429 there would flap
     * the container to unhealthy, which is why the catch-all stops at /api instead of covering
     * everything.
     */
    @Test
    void shouldLeaveTheHealthEndpointUnthrottled() {
        assertNull(RateLimitFilter.bucketFor("/actuator/health"));
    }

    /**
     * Guards the gate itself: if the regex or the source path goes stale, every assertion above
     * passes over an empty list and this file becomes indistinguishable from one that checks nothing.
     */
    @Test
    void shouldFindTheControllersItClaimsToCheck() {
        List<String> bases = controllerBasePaths();
        assertTrue(bases.size() >= 20,
            "Expected at least 20 controller base paths, found " + bases.size() + " " + bases
                + ". The mapping regex or the api/ source path is probably stale.");
    }

    private static List<String> controllerBasePaths() {
        List<String> bases = new ArrayList<>();
        for (Path file : controllerSources()) {
            Matcher matcher = CLASS_MAPPING.matcher(SourceFiles.readWithoutComments(file));
            while (matcher.find()) {
                String base = matcher.group(1);
                if (!bases.contains(base)) {
                    bases.add(base);
                }
            }
        }
        return bases;
    }

    private static List<Path> controllerSources() {
        return SourceFiles.mainJavaFiles().stream()
            .filter(path -> path.startsWith(API_ROOT))
            .filter(path -> path.getFileName().toString().endsWith("Controller.java"))
            .toList();
    }
}
