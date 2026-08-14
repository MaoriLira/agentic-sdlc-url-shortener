---
tags: [guardrail, rate-limiting, redis]
---

# Guardrail: Redis Fixed-Window Rate Limiting

Related: [[../Architecture-Decisions/ADR-07-Rate-Limit-Thresholds]] · [[../Risks/R-4-Rate-Limiting-Coverage-Gaps]] · [[../Risks/R-7-Rate-Limiter-Boundary-Burst]]

**Ticket:** [[../Jira-Tickets/Epic-URL-100-Core-APIs#URL-108 — Rate Limiting on Public Endpoints|URL-108]] — `service/RateLimiterService.java`

> [!info] Naming correction
> The ticket was scoped as a "token bucket." The implementation is a **fixed-window
> counter** (Redis `INCR` against a key scoped to the current minute, with a short expiry).
> Simpler to reason about and sufficient for an abuse-guard use case; it does allow a burst at
> window boundaries that a true token bucket would smooth out — quantified in
> [[../Risks/R-7-Rate-Limiter-Boundary-Burst]]. Documented here rather than silently
> relabeled.

```java
public void checkLimit(String scope, String identifier, int limitPerMinute) {
    long nowEpochSeconds = Instant.now().getEpochSecond();
    long windowMinute = nowEpochSeconds / 60;
    String key = "ratelimit:" + scope + ":" + identifier + ":" + windowMinute;

    Long count = redisTemplate.opsForValue().increment(key);
    if (count != null && count == 1L) {
        redisTemplate.expire(key, Duration.ofSeconds(65));
    }
    if (count != null && count > limitPerMinute) {
        long retryAfter = 60 - (nowEpochSeconds % 60);
        throw new RateLimitExceededException(retryAfter);
    }
}
```

Limits ([[../Architecture-Decisions/ADR-07-Rate-Limit-Thresholds|ADR-07]]): 100/min per API key
on create, 1000/min per IP on redirect. Exceeding the limit returns `429` with a `Retry-After`
header computed from the remaining seconds in the current window.

> [!warning] Coverage gaps found later
> [[../Risks/R-4-Rate-Limiting-Coverage-Gaps]] found this guardrail doesn't cover
> metadata/stats/delete endpoints at all, and the create-path check runs *after*
> authentication — an invalid API key is never rate-limited. Not yet remediated.

**Implementation:** `service/RateLimiterService.java`, `config/RateLimitProperties.java`
