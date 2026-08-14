package com.example.urlshortener.service;

import com.example.urlshortener.domain.ClickDailyRollup;
import com.example.urlshortener.domain.ClickDailyRollupId;
import com.example.urlshortener.domain.ClickSummary;
import com.example.urlshortener.domain.event.ClickEvent;
import com.example.urlshortener.repository.ClickDailyRollupRepository;
import com.example.urlshortener.repository.ClickSummaryRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Aggregates click events into rollups (URL-204). Idempotency: each event carries a UUID;
 * a Redis SETNX guard (24h window) prevents double-counting on consumer redelivery/rebalance,
 * matching the at-least-once delivery semantics of the producer/broker configuration.
 */
@Service
public class AnalyticsConsumerService {

    private final ClickSummaryRepository summaryRepository;
    private final ClickDailyRollupRepository rollupRepository;
    private final StringRedisTemplate redisTemplate;

    public AnalyticsConsumerService(ClickSummaryRepository summaryRepository,
                                     ClickDailyRollupRepository rollupRepository,
                                     StringRedisTemplate redisTemplate) {
        this.summaryRepository = summaryRepository;
        this.rollupRepository = rollupRepository;
        this.redisTemplate = redisTemplate;
    }

    @KafkaListener(topics = "${kafka.topics.click-events}")
    @Transactional
    public void onClickEvent(ClickEvent event) {
        if (!markProcessed(event.eventId())) {
            return;
        }

        ClickSummary summary = summaryRepository.findById(event.shortCode())
                .orElseGet(() -> new ClickSummary(event.shortCode()));
        summary.recordClick(event.timestamp());
        summaryRepository.save(summary);

        LocalDate clickDate = event.timestamp().atZone(ZoneOffset.UTC).toLocalDate();
        ClickDailyRollupId rollupId = new ClickDailyRollupId(event.shortCode(), clickDate);
        ClickDailyRollup rollup = rollupRepository.findById(rollupId)
                .orElseGet(() -> new ClickDailyRollup(event.shortCode(), clickDate));
        rollup.increment();
        if (event.referrer() != null && !event.referrer().isBlank()) {
            rollup.setTopReferrer(event.referrer());
        }
        rollupRepository.save(rollup);
    }

    private boolean markProcessed(String eventId) {
        Boolean firstTime = redisTemplate.opsForValue()
                .setIfAbsent("processed:" + eventId, "1", Duration.ofHours(24));
        return Boolean.TRUE.equals(firstTime);
    }
}
