package com.example.urlshortener.api;

import com.example.urlshortener.config.RateLimitProperties;
import com.example.urlshortener.domain.event.ClickEvent;
import com.example.urlshortener.service.ClickEventPublisher;
import com.example.urlshortener.service.RateLimiterService;
import com.example.urlshortener.service.UrlShortenerService;
import com.example.urlshortener.util.LogSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * DEBUG, not INFO, on the success path — this is the highest-traffic endpoint in the system
 * (ADR-10 design target: 1,000 RPS), and INFO-per-request here would flood the log. Client IP
 * is masked before logging (never the full address) — see URL-601 /
 * docs/Architecture-Decisions/ADR-13-Logging-and-Data-Masking-Policy.md.
 */
@RestController
public class RedirectController {

    private static final Logger log = LoggerFactory.getLogger(RedirectController.class);

    private final UrlShortenerService urlShortenerService;
    private final RateLimiterService rateLimiterService;
    private final RateLimitProperties rateLimitProperties;
    private final ClickEventPublisher clickEventPublisher;

    public RedirectController(UrlShortenerService urlShortenerService,
                               RateLimiterService rateLimiterService,
                               RateLimitProperties rateLimitProperties,
                               ClickEventPublisher clickEventPublisher) {
        this.urlShortenerService = urlShortenerService;
        this.rateLimiterService = rateLimiterService;
        this.rateLimitProperties = rateLimitProperties;
        this.clickEventPublisher = clickEventPublisher;
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode, HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        rateLimiterService.checkLimit("redirect", remoteAddr, rateLimitProperties.redirectPerMinute());
        String longUrl = urlShortenerService.resolveForRedirect(shortCode);
        log.debug("Resolved shortCode={} for client={}", shortCode, LogSanitizer.maskIp(remoteAddr));

        clickEventPublisher.publish(ClickEvent.of(shortCode, request.getHeader("Referer"), request.getHeader("User-Agent")));

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, longUrl)
                .build();
    }
}
