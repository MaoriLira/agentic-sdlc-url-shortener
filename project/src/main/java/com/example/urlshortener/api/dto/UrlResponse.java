package com.example.urlshortener.api.dto;

import com.example.urlshortener.domain.UrlMapping;

import java.time.Instant;

public record UrlResponse(
        String shortCode,
        String shortUrl,
        String longUrl,
        Instant createdAt,
        Instant expiresAt,
        String status
) {
    public static UrlResponse from(UrlMapping mapping, String baseUrl) {
        return new UrlResponse(
                mapping.getShortCode(),
                baseUrl + "/" + mapping.getShortCode(),
                mapping.getLongUrl(),
                mapping.getCreatedAt(),
                mapping.getExpiresAt(),
                mapping.getStatus().name()
        );
    }
}
