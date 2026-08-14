package com.example.urlshortener.service;

import com.example.urlshortener.api.dto.CreateUrlRequest;
import com.example.urlshortener.api.dto.UrlResponse;
import com.example.urlshortener.domain.ApiClient;
import com.example.urlshortener.domain.UrlMapping;
import com.example.urlshortener.domain.UrlStatus;
import com.example.urlshortener.exception.AliasConflictException;
import com.example.urlshortener.exception.ShortUrlGoneException;
import com.example.urlshortener.exception.ShortUrlNotFoundException;
import com.example.urlshortener.repository.UrlMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Logs shortCode + owner id on create/delete (INFO — low-frequency, audit-worthy business
 * events), never the long URL itself: a shortened URL can carry a third party's token or
 * credential embedded in its query string, so it's treated as data, not a safe log field —
 * see URL-601 / docs/Architecture-Decisions/ADR-13-Logging-and-Data-Masking-Policy.md.
 */
@Service
public class UrlShortenerService {

    private static final Logger log = LoggerFactory.getLogger(UrlShortenerService.class);

    private final UrlMappingRepository repository;
    private final ShortCodeGenerator codeGenerator;
    private final UrlValidationService validationService;
    private final UrlCacheService cacheService;
    private final String baseUrl;

    public UrlShortenerService(UrlMappingRepository repository,
                                ShortCodeGenerator codeGenerator,
                                UrlValidationService validationService,
                                UrlCacheService cacheService,
                                @Value("${urlshortener.base-url}") String baseUrl) {
        this.repository = repository;
        this.codeGenerator = codeGenerator;
        this.validationService = validationService;
        this.cacheService = cacheService;
        this.baseUrl = baseUrl;
    }

    @Transactional
    public UrlResponse create(CreateUrlRequest request, ApiClient owner) {
        validationService.validateLongUrl(request.longUrl());

        boolean isCustom = request.customAlias() != null && !request.customAlias().isBlank();
        String shortCode;
        if (isCustom) {
            validationService.validateAlias(request.customAlias());
            shortCode = request.customAlias();
        } else {
            shortCode = codeGenerator.nextCode();
        }

        UrlMapping mapping = UrlMapping.builder()
                .shortCode(shortCode)
                .longUrl(request.longUrl())
                .ownerClientId(owner.getId())
                .customAlias(isCustom)
                .expiresAt(request.expiresAt())
                .build();

        try {
            mapping = repository.saveAndFlush(mapping);
        } catch (DataIntegrityViolationException e) {
            log.warn("Alias conflict on create: shortCode={} already active", shortCode);
            throw new AliasConflictException(shortCode);
        }

        log.info("Created shortCode={} ownerClientId={} customAlias={}", shortCode, owner.getId(), isCustom);
        return UrlResponse.from(mapping, baseUrl);
    }

    @Transactional(readOnly = true)
    public UrlResponse getMetadata(String shortCode) {
        UrlMapping mapping = repository.findByShortCode(shortCode)
                .filter(m -> m.getStatus() != UrlStatus.DELETED)
                .orElseThrow(() -> new ShortUrlNotFoundException(shortCode));
        return UrlResponse.from(mapping, baseUrl);
    }

    @Transactional
    public void delete(String shortCode) {
        UrlMapping mapping = repository.findByShortCode(shortCode)
                .filter(m -> m.getStatus() != UrlStatus.DELETED)
                .orElseThrow(() -> new ShortUrlNotFoundException(shortCode));
        mapping.setStatus(UrlStatus.DELETED);
        repository.save(mapping);
        cacheService.evict(shortCode);
        log.info("Deleted shortCode={}", shortCode);
    }

    @Transactional(readOnly = true)
    public String resolveForRedirect(String shortCode) {
        Optional<String> resolved = cacheService.getOrLoad(shortCode, () -> loadActiveForCache(shortCode));
        if (resolved.isPresent()) {
            return resolved.get();
        }
        UrlMapping mapping = repository.findByShortCode(shortCode).orElse(null);
        if (mapping == null) {
            throw new ShortUrlNotFoundException(shortCode);
        }
        throw new ShortUrlGoneException(shortCode);
    }

    private Optional<CacheableValue> loadActiveForCache(String shortCode) {
        return repository.findByShortCode(shortCode)
                .filter(m -> m.getStatus() == UrlStatus.ACTIVE && !m.isExpired())
                .map(m -> new CacheableValue(m.getLongUrl(), effectiveTtl(m)));
    }

    private Duration effectiveTtl(UrlMapping mapping) {
        Duration configured = cacheService.defaultTtl();
        if (mapping.getExpiresAt() == null) {
            return configured;
        }
        Duration untilExpiry = Duration.between(Instant.now(), mapping.getExpiresAt());
        return untilExpiry.compareTo(configured) < 0 ? untilExpiry : configured;
    }
}
