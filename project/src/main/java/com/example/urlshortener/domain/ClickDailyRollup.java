package com.example.urlshortener.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "click_daily_rollup", schema = "analytics")
@IdClass(ClickDailyRollupId.class)
public class ClickDailyRollup {

    @Id
    @Column(name = "short_code", length = 20)
    private String shortCode;

    @Id
    @Column(name = "click_date")
    private LocalDate clickDate;

    @Column(name = "click_count", nullable = false)
    private long clickCount;

    @Column(name = "top_referrer")
    private String topReferrer;

    @Column(name = "geo_region", length = 10)
    private String geoRegion;

    protected ClickDailyRollup() {
    }

    public ClickDailyRollup(String shortCode, LocalDate clickDate) {
        this.shortCode = shortCode;
        this.clickDate = clickDate;
        this.clickCount = 0;
    }

    public String getShortCode() {
        return shortCode;
    }

    public LocalDate getClickDate() {
        return clickDate;
    }

    public long getClickCount() {
        return clickCount;
    }

    public void increment() {
        this.clickCount++;
    }

    public String getTopReferrer() {
        return topReferrer;
    }

    public void setTopReferrer(String topReferrer) {
        this.topReferrer = topReferrer;
    }

    public String getGeoRegion() {
        return geoRegion;
    }
}
