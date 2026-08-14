package com.example.urlshortener.api;

import com.example.urlshortener.api.dto.CreateUrlRequest;
import com.example.urlshortener.api.dto.StatsResponse;
import com.example.urlshortener.api.dto.UrlResponse;
import com.example.urlshortener.config.RateLimitProperties;
import com.example.urlshortener.domain.ApiClient;
import com.example.urlshortener.service.AnalyticsQueryService;
import com.example.urlshortener.service.ApiKeyAuthService;
import com.example.urlshortener.service.RateLimiterService;
import com.example.urlshortener.service.UrlShortenerService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Never logs the {@code X-API-Key} header value, in any form — see URL-601 /
 * docs/Architecture-Decisions/ADR-13-Logging-and-Data-Masking-Policy.md. Business events
 * (create/delete) are logged in {@link UrlShortenerService}, not here, to avoid double-logging
 * the same event at two layers; this class only traces request receipt at DEBUG.
 */
@RestController
@RequestMapping("/api/v1/urls")
public class UrlController {

    private static final Logger log = LoggerFactory.getLogger(UrlController.class);

    private final UrlShortenerService urlShortenerService;
    private final ApiKeyAuthService apiKeyAuthService;
    private final RateLimiterService rateLimiterService;
    private final RateLimitProperties rateLimitProperties;
    private final AnalyticsQueryService analyticsQueryService;

    public UrlController(UrlShortenerService urlShortenerService,
                          ApiKeyAuthService apiKeyAuthService,
                          RateLimiterService rateLimiterService,
                          RateLimitProperties rateLimitProperties,
                          AnalyticsQueryService analyticsQueryService) {
        this.urlShortenerService = urlShortenerService;
        this.apiKeyAuthService = apiKeyAuthService;
        this.rateLimiterService = rateLimiterService;
        this.rateLimitProperties = rateLimitProperties;
        this.analyticsQueryService = analyticsQueryService;
    }

    @PostMapping
    public ResponseEntity<UrlResponse> create(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                               @Valid @RequestBody CreateUrlRequest request) {
        log.debug("Received create request");
        ApiClient owner = apiKeyAuthService.authenticate(apiKey);
        rateLimiterService.checkLimit("create", String.valueOf(owner.getId()), rateLimitProperties.createPerMinute());
        UrlResponse response = urlShortenerService.create(request, owner);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<UrlResponse> getMetadata(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                                    @PathVariable String shortCode) {
        log.debug("Received metadata request for shortCode={}", shortCode);
        apiKeyAuthService.authenticate(apiKey);
        return ResponseEntity.ok(urlShortenerService.getMetadata(shortCode));
    }

    @DeleteMapping("/{shortCode}")
    public ResponseEntity<Void> delete(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                        @PathVariable String shortCode) {
        log.debug("Received delete request for shortCode={}", shortCode);
        apiKeyAuthService.authenticate(apiKey);
        urlShortenerService.delete(shortCode);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{shortCode}/stats")
    public ResponseEntity<StatsResponse> getStats(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                                   @PathVariable String shortCode) {
        log.debug("Received stats request for shortCode={}", shortCode);
        apiKeyAuthService.authenticate(apiKey);
        return ResponseEntity.ok(analyticsQueryService.getStats(shortCode));
    }
}
