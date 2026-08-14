package com.example.urlshortener.service;

import com.example.urlshortener.api.dto.StatsResponse;
import com.example.urlshortener.domain.ClickSummary;
import com.example.urlshortener.exception.ShortUrlNotFoundException;
import com.example.urlshortener.repository.ClickDailyRollupRepository;
import com.example.urlshortener.repository.ClickSummaryRepository;
import com.example.urlshortener.repository.UrlMappingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsQueryService {

    private final UrlMappingRepository urlMappingRepository;
    private final ClickSummaryRepository summaryRepository;
    private final ClickDailyRollupRepository rollupRepository;

    public AnalyticsQueryService(UrlMappingRepository urlMappingRepository,
                                  ClickSummaryRepository summaryRepository,
                                  ClickDailyRollupRepository rollupRepository) {
        this.urlMappingRepository = urlMappingRepository;
        this.summaryRepository = summaryRepository;
        this.rollupRepository = rollupRepository;
    }

    @Transactional(readOnly = true)
    public StatsResponse getStats(String shortCode) {
        if (!urlMappingRepository.existsByShortCode(shortCode)) {
            throw new ShortUrlNotFoundException(shortCode);
        }

        ClickSummary summary = summaryRepository.findById(shortCode).orElse(null);
        long totalClicks = summary != null ? summary.getTotalClicks() : 0L;

        var daily = rollupRepository.findByShortCodeOrderByClickDateDesc(shortCode).stream()
                .map(r -> new StatsResponse.DailyClicks(r.getClickDate(), r.getClickCount()))
                .toList();

        return new StatsResponse(
                shortCode,
                totalClicks,
                summary != null ? summary.getLastClickedAt() : null,
                daily
        );
    }
}
