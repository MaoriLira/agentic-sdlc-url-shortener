package com.example.urlshortener.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Click analytics event schema (URL-201), versioned so future consumer changes stay
 * backward compatible with events already in flight. eventId enables idempotent
 * aggregation on the consumer side under at-least-once Kafka delivery.
 */
public record ClickEvent(
        String eventId,
        int schemaVersion,
        String shortCode,
        Instant timestamp,
        String referrer,
        String userAgent,
        String geoRegion
) {
    public static ClickEvent of(String shortCode, String referrer, String userAgent) {
        return new ClickEvent(UUID.randomUUID().toString(), 1, shortCode, Instant.now(), referrer, userAgent, null);
    }
}
