package com.example.urlshortener.service;

import com.example.urlshortener.config.CacheProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Cache-aside layer for shortCode -> longUrl lookups (URL-301/302/303/304/305, and the
 * URL-502 graceful-degradation fix).
 *
 * - Reads check Redis first; on miss, the caller-supplied loader hits the DB and the
 *   result is written back to Redis (cache-aside).
 * - TTL is jittered (+0-10%) so a batch of entries written around the same time doesn't
 *   expire in the same instant.
 * - A short Redis lock (SETNX) prevents a stampede of concurrent DB reads for the same
 *   hot key on miss; losers wait briefly for the winner to populate the cache, falling
 *   back to a direct DB read if the wait is exceeded (bounded, never blocks indefinitely).
 * - Every Redis call is individually resilient: a connection failure or timeout is caught,
 *   logged at ERROR, and treated as "no cache" — callers always get a correct answer from
 *   PostgreSQL, never a 500 just because Redis is unavailable. See URL-502 / R-1 in
 *   docs/05-Risk-and-Failure-Scenario-Analysis.md. This resilience is scoped to the
 *   cache-aside path only — RateLimiterService and AnalyticsConsumerService's Redis usage
 *   are deliberately untouched (separate, not-yet-scoped follow-ups).
 */
@Service
public class UrlCacheService {

    private static final Logger log = LoggerFactory.getLogger(UrlCacheService.class);

    private static final String KEY_PREFIX = "url:";
    private static final String LOCK_PREFIX = "lock:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(2);
    private static final int MAX_WAIT_RETRIES = 5;
    private static final long WAIT_RETRY_MS = 50;

    private final StringRedisTemplate redisTemplate;
    private final CacheProperties cacheProperties;

    public UrlCacheService(StringRedisTemplate redisTemplate, CacheProperties cacheProperties) {
        this.redisTemplate = redisTemplate;
        this.cacheProperties = cacheProperties;
    }

    public Duration defaultTtl() {
        return Duration.ofSeconds(cacheProperties.ttlSeconds());
    }

    /** Never throws: a Redis failure is logged and treated as a cache miss. */
    public Optional<String> get(String shortCode) {
        try {
            return Optional.ofNullable(redisTemplate.opsForValue().get(key(shortCode)));
        } catch (DataAccessException e) {
            log.error("Redis unavailable reading cache for shortCode={}; treating as cache miss", shortCode, e);
            return Optional.empty();
        }
    }

    /** Never throws: a Redis failure is logged and swallowed — the DB row is already the source of truth. */
    public void evict(String shortCode) {
        try {
            redisTemplate.delete(key(shortCode));
        } catch (DataAccessException e) {
            log.error("Redis unavailable evicting cache for shortCode={}", shortCode, e);
        }
    }

    public Optional<String> getOrLoad(String shortCode, Supplier<Optional<CacheableValue>> dbLoader) {
        Optional<String> cached = get(shortCode);
        if (cached.isPresent()) {
            return cached;
        }

        String lockKey = LOCK_PREFIX + shortCode;
        boolean acquired;
        try {
            acquired = Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL));
        } catch (DataAccessException e) {
            log.error("Redis unavailable for shortCode={}; falling back directly to PostgreSQL", shortCode, e);
            return dbLoader.get().map(CacheableValue::longUrl);
        }

        if (acquired) {
            try {
                Optional<CacheableValue> loaded = dbLoader.get();
                loaded.ifPresent(v -> put(shortCode, v.longUrl(), v.ttl()));
                return loaded.map(CacheableValue::longUrl);
            } finally {
                releaseLock(lockKey);
            }
        }

        for (int i = 0; i < MAX_WAIT_RETRIES; i++) {
            try {
                Thread.sleep(WAIT_RETRY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            Optional<String> retried = get(shortCode);
            if (retried.isPresent()) {
                return retried;
            }
        }

        return dbLoader.get().map(CacheableValue::longUrl);
    }

    /** Never throws: failing to cache a freshly-loaded value just means the next read misses too. */
    private void put(String shortCode, String longUrl, Duration ttl) {
        try {
            long jitterMillis = ThreadLocalRandom.current().nextLong(0, Math.max(1000, ttl.toMillis() / 10));
            redisTemplate.opsForValue().set(key(shortCode), longUrl, ttl.plusMillis(jitterMillis));
        } catch (DataAccessException e) {
            log.error("Redis unavailable writing cache for shortCode={}; value was served from PostgreSQL but not cached",
                    shortCode, e);
        }
    }

    private void releaseLock(String lockKey) {
        try {
            redisTemplate.delete(lockKey);
        } catch (DataAccessException e) {
            log.error("Redis unavailable releasing cache lock {}", lockKey, e);
        }
    }

    private String key(String shortCode) {
        return KEY_PREFIX + shortCode;
    }
}
