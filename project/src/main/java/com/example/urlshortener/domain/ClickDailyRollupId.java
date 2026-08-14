package com.example.urlshortener.domain;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

public class ClickDailyRollupId implements Serializable {

    private String shortCode;
    private LocalDate clickDate;

    protected ClickDailyRollupId() {
    }

    public ClickDailyRollupId(String shortCode, LocalDate clickDate) {
        this.shortCode = shortCode;
        this.clickDate = clickDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClickDailyRollupId that)) return false;
        return Objects.equals(shortCode, that.shortCode) && Objects.equals(clickDate, that.clickDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(shortCode, clickDate);
    }
}
