package com.example.urlshortener.repository;

import com.example.urlshortener.domain.ClickDailyRollup;
import com.example.urlshortener.domain.ClickDailyRollupId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClickDailyRollupRepository extends JpaRepository<ClickDailyRollup, ClickDailyRollupId> {

    List<ClickDailyRollup> findByShortCodeOrderByClickDateDesc(String shortCode);
}
