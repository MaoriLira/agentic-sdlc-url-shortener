package com.example.urlshortener.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record StatsResponse(
        String shortCode,
        long totalClicks,
        Instant lastClickedAt,
        List<DailyClicks> dailyBreakdown
) {
    public record DailyClicks(LocalDate date, long clicks) {
    }
}
