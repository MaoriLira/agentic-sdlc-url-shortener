package com.example.urlshortener.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "click_summary", schema = "analytics")
public class ClickSummary {

    @Id
    @Column(name = "short_code", length = 20)
    private String shortCode;

    @Column(name = "total_clicks", nullable = false)
    private long totalClicks;

    @Column(name = "last_clicked_at")
    private Instant lastClickedAt;

    protected ClickSummary() {
    }

    public ClickSummary(String shortCode) {
        this.shortCode = shortCode;
        this.totalClicks = 0;
    }

    public String getShortCode() {
        return shortCode;
    }

    public long getTotalClicks() {
        return totalClicks;
    }

    public Instant getLastClickedAt() {
        return lastClickedAt;
    }

    public void recordClick(Instant clickedAt) {
        this.totalClicks++;
        this.lastClickedAt = clickedAt;
    }
}
