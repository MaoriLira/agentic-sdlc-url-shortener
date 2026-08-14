package com.example.urlshortener.service;

import com.example.urlshortener.domain.ApiClient;
import com.example.urlshortener.domain.ClientStatus;
import com.example.urlshortener.exception.UnauthorizedException;
import com.example.urlshortener.repository.ApiClientRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class ApiKeyAuthService {

    private final ApiClientRepository apiClientRepository;

    public ApiKeyAuthService(ApiClientRepository apiClientRepository) {
        this.apiClientRepository = apiClientRepository;
    }

    public ApiClient authenticate(String rawApiKey) {
        if (rawApiKey == null || rawApiKey.isBlank()) {
            throw new UnauthorizedException("Missing X-API-Key header");
        }
        String hash = sha256Hex(rawApiKey);
        ApiClient client = apiClientRepository.findByApiKeyHash(hash)
                .orElseThrow(() -> new UnauthorizedException("Invalid API key"));
        if (client.getStatus() != ClientStatus.ACTIVE) {
            throw new UnauthorizedException("API key is suspended");
        }
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
