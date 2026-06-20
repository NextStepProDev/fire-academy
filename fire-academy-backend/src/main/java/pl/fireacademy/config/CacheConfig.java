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
        // TTL ujednolicony na 30 min: każdy zapis (admin + self-enroll/cancel) robi
        // @CacheEvict(allEntries=true), więc spójność gwarantuje eviction, nie wygaśnięcie.
        // Krótki TTL (dawniej 2 min na EVENTS/EVENT) tylko wymuszał zbędne zimne trafienia
        // w DB na boksie o ograniczonym CPU — bez zysku dla świeżości danych.
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
