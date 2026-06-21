package pl.fireacademy.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String INSTRUCTORS = "publicInstructors";
    public static final String EVENT_TYPES = "publicEventTypes";
    public static final String EVENTS = "publicEvents";
    public static final String INSTRUCTOR = "publicInstructor";
    public static final String EVENT_TYPE = "publicEventType";
    public static final String EVENT = "publicEvent";

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        // TTL unified at 30 min: every write (admin + self-enroll/cancel) does
        // @CacheEvict(allEntries=true), so consistency comes from eviction, not expiry.
        // A short TTL (formerly 2 min on EVENTS/EVENT) only forced needless cold DB
        // hits on a CPU-constrained box — with no gain in data freshness.
        manager.setCaches(List.of(
                build(INSTRUCTORS, 10, 30),
                build(EVENT_TYPES, 10, 30),
                build(EVENTS, 10, 30),
                build(INSTRUCTOR, 50, 30),
                build(EVENT_TYPE, 50, 30),
                build(EVENT, 50, 30)
        ));
        return manager;
    }

    private static CaffeineCache build(String name, int maxSize, int ttlMinutes) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
                .build());
    }
}
