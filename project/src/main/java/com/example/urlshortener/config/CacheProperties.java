package com.example.urlshortener.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "urlshortener.cache")
public record CacheProperties(long ttlSeconds) {
}
