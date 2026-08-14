package com.example.urlshortener.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "click_events_dlq", schema = "analytics")
public class ClickEventDlq {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_code", nullable = false, length = 20)
    private String shortCode;

    @Column(nullable = false, columnDefinition = "JSONB")
    private String payload;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "failed_at", nullable = false)
    private Instant failedAt;

    protected ClickEventDlq() {
    }

    public ClickEventDlq(String shortCode, String payload, String failureReason) {
        this.shortCode = shortCode;
        this.payload = payload;
        this.failureReason = failureReason;
        this.failedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getShortCode() {
        return shortCode;
    }

    public String getPayload() {
        return payload;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getFailedAt() {
        return failedAt;
    }
}
