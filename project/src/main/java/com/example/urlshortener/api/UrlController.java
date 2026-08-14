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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/urls")
public class UrlController {

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
        ApiClient owner = apiKeyAuthService.authenticate(apiKey);
        rateLimiterService.checkLimit("create", String.valueOf(owner.getId()), rateLimitProperties.createPerMinute());
        UrlResponse response = urlShortenerService.create(request, owner);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<UrlResponse> getMetadata(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                                    @PathVariable String shortCode) {
        apiKeyAuthService.authenticate(apiKey);
        return ResponseEntity.ok(urlShortenerService.getMetadata(shortCode));
    }

    @DeleteMapping("/{shortCode}")
    public ResponseEntity<Void> delete(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                        @PathVariable String shortCode) {
        apiKeyAuthService.authenticate(apiKey);
        urlShortenerService.delete(shortCode);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{shortCode}/stats")
    public ResponseEntity<StatsResponse> getStats(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                                   @PathVariable String shortCode) {
        apiKeyAuthService.authenticate(apiKey);
        return ResponseEntity.ok(analyticsQueryService.getStats(shortCode));
    }
}
