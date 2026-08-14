package com.example.urlshortener.service;

import com.example.urlshortener.exception.RateLimitExceededException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class RateLimiterServiceTest {

    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    static LettuceConnectionFactory connectionFactory;
    static RateLimiterService rateLimiterService;

    @BeforeAll
    static void startContainer() {
        REDIS.start();
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        rateLimiterService = new RateLimiterService(redisTemplate);
    }

    @AfterAll
    static void stopContainer() {
        connectionFactory.destroy();
        REDIS.stop();
    }

    @Test
    void allowsRequestsWithinLimit() {
        String identifier = UUID.randomUUID().toString();
        assertThatCode(() -> {
            for (int i = 0; i < 5; i++) {
                rateLimiterService.checkLimit("test", identifier, 5);
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void rejectsRequestsOverLimit() {
        String identifier = UUID.randomUUID().toString();
        for (int i = 0; i < 3; i++) {
            rateLimiterService.checkLimit("test", identifier, 3);
        }
        assertThatThrownBy(() -> rateLimiterService.checkLimit("test", identifier, 3))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void tracksDifferentIdentifiersIndependently() {
        String a = UUID.randomUUID().toString();
        String b = UUID.randomUUID().toString();
        rateLimiterService.checkLimit("test", a, 1);
        assertThatCode(() -> rateLimiterService.checkLimit("test", b, 1))
                .doesNotThrowAnyException();
    }
}
