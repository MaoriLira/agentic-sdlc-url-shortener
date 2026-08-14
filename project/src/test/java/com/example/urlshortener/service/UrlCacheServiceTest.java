package com.example.urlshortener.service;

import com.example.urlshortener.config.CacheProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Verifies URL-502 (R-1): the cache-aside layer must degrade gracefully on Redis failure,
 * never propagate a Redis exception to the caller, and always fall back to the DB loader.
 * Fault injection is done via Mockito (StringRedisTemplate throwing on every call) rather
 * than killing a Testcontainers Redis mid-suite — deterministic and fast, and it isolates
 * exactly the code path this fix touches (see docs/05-Risk-and-Failure-Scenario-Analysis.md
 * for why RateLimiterService is deliberately NOT covered by this same fix).
 */
@SuppressWarnings("unchecked")
class UrlCacheServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private UrlCacheService cacheService;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        cacheService = new UrlCacheService(redisTemplate, new CacheProperties(86400));
    }

    @Test
    void getReturnsEmptyInsteadOfThrowingWhenRedisUnavailable() {
        when(valueOperations.get(anyString())).thenThrow(new RedisConnectionFailureException("down"));

        assertThatCode(() -> {
            Optional<String> result = cacheService.get("abc1234");
            assertThat(result).isEmpty();
        }).doesNotThrowAnyException();
    }

    @Test
    void evictDoesNotThrowWhenRedisUnavailable() {
        doThrow(new RedisConnectionFailureException("down")).when(redisTemplate).delete(anyString());

        assertThatCode(() -> cacheService.evict("abc1234")).doesNotThrowAnyException();
    }

    @Test
    void getOrLoadFallsBackToDbWhenRedisCompletelyUnavailable() {
        when(valueOperations.get(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("down"));

        Optional<String> result = cacheService.getOrLoad("abc1234",
                () -> Optional.of(new CacheableValue("https://example.com", Duration.ofSeconds(60))));

        assertThat(result).contains("https://example.com");
    }

    @Test
    void getOrLoadReturnsEmptyWhenRedisUnavailableAndDbHasNoValue() {
        when(valueOperations.get(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("down"));

        Optional<String> result = cacheService.getOrLoad("missing", Optional::empty);

        assertThat(result).isEmpty();
    }

    @Test
    void getOrLoadDoesNotFailWhenRedisRejectsTheWriteBack() {
        when(valueOperations.get(anyString())).thenReturn(null); // cache miss
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        doThrow(new RedisConnectionFailureException("down"))
                .when(valueOperations).set(anyString(), anyString(), any(Duration.class));

        Optional<String> result = cacheService.getOrLoad("writefail",
                () -> Optional.of(new CacheableValue("https://example.com/served-not-cached", Duration.ofSeconds(60))));

        assertThat(result).contains("https://example.com/served-not-cached");
    }

    @Test
    void getOrLoadStillCachesNormallyWhenRedisIsHealthy() {
        when(valueOperations.get(anyString())).thenReturn(null); // cache miss
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        Optional<String> result = cacheService.getOrLoad("healthy1",
                () -> Optional.of(new CacheableValue("https://example.com/ok", Duration.ofSeconds(60))));

        assertThat(result).contains("https://example.com/ok");
        verify(valueOperations).set(eq("url:healthy1"), eq("https://example.com/ok"), any(Duration.class));
    }
}
