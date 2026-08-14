package com.example.urlshortener.service;

import com.example.urlshortener.exception.RateLimitExceededException;
import com.example.urlshortener.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Fixed-window request counter backed by Redis (URL-108). One INCR per request against a
 * key scoped to the current minute; the key's first writer sets a short-lived expiry so
 * old windows clean up automatically. Simpler than a true token bucket and sufficient for
 * the abuse-guard use case here (see ADR #8 for the configured thresholds).
 *
 * {@code identifier} is a client IP on the redirect path and a numeric client ID on the
 * create path. It's masked before logging either way — {@link LogSanitizer#maskIp} is a
 * no-op passthrough for anything that isn't an IPv4 address, so this is safe for both.
 */
@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

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
            log.warn("Rate limit exceeded: scope={} identifier={} limitPerMinute={}",
                    scope, LogSanitizer.maskIp(identifier), limitPerMinute);
            throw new RateLimitExceededException(retryAfter);
        }
    }
}
