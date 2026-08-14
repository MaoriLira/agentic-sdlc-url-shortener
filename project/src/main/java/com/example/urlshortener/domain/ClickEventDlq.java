package com.example.urlshortener.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * URL-701: fully immutable after construction — no setters ever existed. The 3-arg
 * constructor stays hand-written (not {@code @AllArgsConstructor}) because it auto-stamps
 * {@code failedAt = Instant.now()}, which Lombok can't express. See ADR-14.
 */
@Entity
@Table(name = "click_events_dlq", schema = "analytics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    public ClickEventDlq(String shortCode, String payload, String failureReason) {
        this.shortCode = shortCode;
        this.payload = payload;
        this.failureReason = failureReason;
        this.failedAt = Instant.now();
    }
}
