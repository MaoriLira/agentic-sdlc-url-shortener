package com.example.urlshortener.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * URL-701: {@code clickCount} has no setter — mutation only through {@link #increment}, same
 * reasoning as {@link ClickSummary}. {@code topReferrer} keeps its setter since it always had
 * one. See ADR-14.
 */
@Entity
@Table(name = "click_daily_rollup", schema = "analytics")
@IdClass(ClickDailyRollupId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClickDailyRollup {

    @Id
    @Column(name = "short_code", length = 20)
    private String shortCode;

    @Id
    @Column(name = "click_date")
    private LocalDate clickDate;

    @Column(name = "click_count", nullable = false)
    private long clickCount;

    @Setter
    @Column(name = "top_referrer")
    private String topReferrer;

    @Column(name = "geo_region", length = 10)
    private String geoRegion;

    public ClickDailyRollup(String shortCode, LocalDate clickDate) {
        this.shortCode = shortCode;
        this.clickDate = clickDate;
        this.clickCount = 0;
    }

    public void increment() {
        this.clickCount++;
    }
}
