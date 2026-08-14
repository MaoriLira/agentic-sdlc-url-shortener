package com.example.urlshortener.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "urlshortener.rate-limit")
public record RateLimitProperties(int createPerMinute, int redirectPerMinute) {
}
