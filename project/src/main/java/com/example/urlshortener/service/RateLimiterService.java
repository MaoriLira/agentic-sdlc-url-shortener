package com.example.urlshortener.service;

import com.example.urlshortener.exception.RateLimitExceededException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Fixed-window request counter backed by Redis (URL-108). One INCR per request against a
 * key scoped to the current minute; the key's first writer sets a short-lived expiry so
 * old windows clean up automatically. Simpler than a true token bucket and sufficient for
 * the abuse-guard use case here (see ADR #8 for the configured thresholds).
 */
@Service
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;

    public RateLimiterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void checkLimit(String scope, String identifier, int limitPerMinute) {
        long nowEpochSeconds = Instant.now().getEpochSecond();
        long windowMinute = nowEpochSeconds / 60;
        String key = "ratelimit:" + scope + ":" + identifier + ":" + windowMinute;

        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofSeconds(65));
        }

        if (count != null && count > limitPerMinute) {
            long retryAfter = 60 - (nowEpochSeconds % 60);
            throw new RateLimitExceededException(retryAfter);
        }
    }
}
