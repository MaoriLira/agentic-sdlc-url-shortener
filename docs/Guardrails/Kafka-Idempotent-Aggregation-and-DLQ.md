---
tags: [guardrail, kafka, idempotency, dlq]
---

# Guardrail: Kafka Idempotent Aggregation & Dead-Letter Handling

Related: [[../Architecture-Decisions/ADR-08-Analytics-Granularity]] · [[../Jira-Tickets/Epic-URL-200-Async-Analytics]]

**Ticket:** [[../Jira-Tickets/Epic-URL-200-Async-Analytics#URL-204 — Analytics Consumer & Aggregation Service|URL-204]] / [[../Jira-Tickets/Epic-URL-200-Async-Analytics#URL-206 — Dead-Letter Queue & Retry Handling|URL-206]] — `service/AnalyticsConsumerService.java`, `config/KafkaConsumerConfig.java`

Kafka's delivery guarantee here is **at-least-once** — a consumer rebalance or restart can
redeliver an already-processed event. Double-counting a click is a correctness bug, so:

- Every `ClickEvent` carries a UUID `eventId`, generated at publish time
- The consumer does `SETNX processed:{eventId}` in Redis with a 24-hour TTL before
  aggregating; if the key already exists, the event is a duplicate and aggregation is skipped

```java
private boolean markProcessed(String eventId) {
    Boolean firstTime = redisTemplate.opsForValue()
            .setIfAbsent("processed:" + eventId, "1", Duration.ofHours(24));
    return Boolean.TRUE.equals(firstTime);
}
```

> [!warning] Trade-off
> The dedup window is bounded at 24 hours, not permanent. A redelivery older than that
> window (very unusual in practice) would be double-counted. A permanent solution would need
> an audit-grade processed-offset ledger — out of scope for this prototype.

## Dead-letter handling

For genuine processing failures (not duplicates — actual exceptions), `DefaultErrorHandler`
retries twice with a 1-second backoff, then a recoverer persists the raw event to
`analytics.click_events_dlq` instead of blocking the partition or silently dropping data:

```java
DefaultErrorHandler handler = new DefaultErrorHandler((record, exception) -> {
    Object value = record.value();
    String shortCode = (value instanceof ClickEvent event) ? event.shortCode() : "unknown";
    String payload = objectMapper.writeValueAsString(value);
    dlqRepository.save(new ClickEventDlq(shortCode, payload, exception.getMessage()));
}, new FixedBackOff(1000L, 2));
```

This satisfies the requirement's "bounded retries, fallback, rollback" principle at the
message-processing level.

> [!info] Idempotency guard is unguarded against Redis failure
> The `SETNX processed:{eventId}` call above depends on Redis and has **no** fallback — unlike
> the cache-aside path (see [[Cache-Aside-Graceful-Degradation]]), this call site was
> deliberately left out of the URL-502 fix. See
> [[../Risks/R-1-Redis-Triple-Point-of-Failure]] for why.

**Implementation:** `service/AnalyticsConsumerService.java`, `config/KafkaConsumerConfig.java`,
`domain/ClickEventDlq.java`, `repository/ClickEventDlqRepository.java`
