package com.example.urlshortener.service;

import java.time.Duration;

/**
 * A value to cache along with the TTL it should be stored under — capped by the caller
 * (e.g. to a URL's remaining time-to-expiry) so a soon-to-expire link is never cached
 * longer than it's actually valid (URL-303).
 */
public record CacheableValue(String longUrl, Duration ttl) {
}
