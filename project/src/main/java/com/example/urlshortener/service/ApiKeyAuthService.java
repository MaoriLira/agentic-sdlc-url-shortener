package com.example.urlshortener.service;

import com.example.urlshortener.domain.ApiClient;
import com.example.urlshortener.domain.ClientStatus;
import com.example.urlshortener.exception.UnauthorizedException;
import com.example.urlshortener.repository.ApiClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * The raw API key (and its SHA-256 hash) must NEVER appear in a log line from this class, on
 * any path, success or failure — not even masked or truncated. See URL-601 /
 * docs/Architecture-Decisions/ADR-13-Logging-and-Data-Masking-Policy.md. Verified by
 * {@code ApiKeyAuthServiceTest}, which attaches a Logback ListAppender and asserts the key
 * string is absent from every captured event.
 */
@Service
public class ApiKeyAuthService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthService.class);

    private final ApiClientRepository apiClientRepository;

    public ApiKeyAuthService(ApiClientRepository apiClientRepository) {
        this.apiClientRepository = apiClientRepository;
    }

    public ApiClient authenticate(String rawApiKey) {
        if (rawApiKey == null || rawApiKey.isBlank()) {
            log.warn("Authentication rejected: missing X-API-Key header");
            throw new UnauthorizedException("Missing X-API-Key header");
        }
        String hash = sha256Hex(rawApiKey);
        ApiClient client = apiClientRepository.findByApiKeyHash(hash)
                .orElseThrow(() -> {
                    log.warn("Authentication rejected: no client found for the provided API key");
                    return new UnauthorizedException("Invalid API key");
                });
        if (client.getStatus() != ClientStatus.ACTIVE) {
            log.warn("Authentication rejected: API key belongs to a suspended client, id={}", client.getId());
            throw new UnauthorizedException("API key is suspended");
        }
        log.debug("Authenticated client id={} name={}", client.getId(), client.getName());
        return client;
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
