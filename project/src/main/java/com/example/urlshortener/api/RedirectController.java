package com.example.urlshortener.api;

import com.example.urlshortener.config.RateLimitProperties;
import com.example.urlshortener.domain.event.ClickEvent;
import com.example.urlshortener.service.ClickEventPublisher;
import com.example.urlshortener.service.RateLimiterService;
import com.example.urlshortener.service.UrlShortenerService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RedirectController {

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
        rateLimiterService.checkLimit("redirect", request.getRemoteAddr(), rateLimitProperties.redirectPerMinute());
        String longUrl = urlShortenerService.resolveForRedirect(shortCode);

        clickEventPublisher.publish(ClickEvent.of(shortCode, request.getHeader("Referer"), request.getHeader("User-Agent")));

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, longUrl)
                .build();
    }
}
