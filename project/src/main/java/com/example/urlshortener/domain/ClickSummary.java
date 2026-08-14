package com.example.urlshortener.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * URL-701: no {@code @Setter}/{@code @Builder} here, deliberately — this class has never had
 * any setters. Mutation only happens through {@link #recordClick}, which is the point: an
 * external caller can't set {@code totalClicks} to an arbitrary value. See ADR-14.
 */
@Entity
@Table(name = "click_summary", schema = "analytics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClickSummary {

    @Id
    @Column(name = "short_code", length = 20)
    private String shortCode;

    @Column(name = "total_clicks", nullable = false)
    private long totalClicks;

    @Column(name = "last_clicked_at")
    private Instant lastClickedAt;

    public ClickSummary(String shortCode) {
        this.shortCode = shortCode;
        this.totalClicks = 0;
    }

    public void recordClick(Instant clickedAt) {
        this.totalClicks++;
        this.lastClickedAt = clickedAt;
    }
}
