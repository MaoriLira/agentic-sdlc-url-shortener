---
tags: [guardrail, caching, redis, resilience, brownfield]
---

# Guardrail: Cache-Aside Graceful Degradation

Related: [[../Risks/R-1-Redis-Triple-Point-of-Failure]] · [[../Jira-Tickets/Epic-URL-500-Brownfield-Remediation]] · [[../Scenarios/B-Brownfield-Refactoring]]

**Ticket:** [[../Jira-Tickets/Epic-URL-500-Brownfield-Remediation#URL-502 — Redis graceful degradation for cache-aside|URL-502]] — `service/UrlCacheService.java`

[[../Risks/R-1-Redis-Triple-Point-of-Failure]] found that every Redis call in the cache-aside
path was unguarded — a Redis outage threw an uncaught exception on every redirect, turning a
cache problem into a site-wide `500`. Every call site now catches
`org.springframework.dao.DataAccessException`, logs at `ERROR`, and falls through to
PostgreSQL (or no-ops, for writes) instead of propagating.

## Every call site's fallback behavior

| Method | On Redis failure |
|---|---|
| `get(shortCode)` | Logs `ERROR`, returns `Optional.empty()` — treated as a cache miss |
| `getOrLoad(...)` → lock `setIfAbsent` | Logs `ERROR`, skips locking entirely, calls `dbLoader.get()` directly |
| `put(...)` (cache write-back) | Logs `ERROR`, silently no-ops — value was already served from the DB |
| lock release (`DEL`) | Logs `ERROR`, silently no-ops |
| `evict(shortCode)` | Logs `ERROR`, silently no-ops — the DB row is already the source of truth |

## Sequence: `getOrLoad` under a Redis outage vs. healthy Redis

This diagram reflects the actual `getOrLoad` control flow, including exactly where the
`DataAccessException` catch sits — at lock acquisition, not at the initial read (`get()` is
internally resilient, so a Redis-down read is indistinguishable from a genuine cache miss;
the *explicit* "Redis is unavailable" branch below is the one that matters for behavior).

```mermaid
sequenceDiagram
    participant Caller
    participant Cache as UrlCacheService
    participant Redis
    participant DB as PostgreSQL (dbLoader)

    Caller->>Cache: getOrLoad(shortCode, dbLoader)
    Cache->>Redis: GET url:{shortCode}
    Note over Cache,Redis: get() catches DataAccessException internally —<br/>a Redis outage here looks identical to a genuine miss
    alt cache hit
        Redis-->>Cache: value
        Cache-->>Caller: value
    else cache miss (or Redis was down on the read)
        Cache->>Redis: SETNX lock:{shortCode} (TTL 2s)
        alt Redis throws here — URL-502 fallback
            Cache->>Cache: log.error("Redis unavailable for {}; falling back directly to PostgreSQL")
            Cache->>DB: dbLoader.get()
            DB-->>Cache: value
            Cache-->>Caller: value
        else lock acquired
            Cache->>DB: dbLoader.get()
            DB-->>Cache: value
            Cache->>Redis: SET url:{shortCode} (best-effort — failure logged, never thrown)
            Cache->>Redis: DEL lock:{shortCode} (best-effort — failure logged, never thrown)
            Cache-->>Caller: value
        else lock contended (another request already populating)
            loop up to 5x, 50ms apart
                Cache->>Redis: GET url:{shortCode}
            end
            Cache->>DB: dbLoader.get() (only if still miss after waiting)
            DB-->>Cache: value
            Cache-->>Caller: value
        end
    end
```

```java
public Optional<String> get(String shortCode) {
    try {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(shortCode)));
    } catch (DataAccessException e) {
        log.error("Redis unavailable reading cache for shortCode={}; treating as cache miss", shortCode, e);
        return Optional.empty();
    }
}
```

> [!warning] Explicitly out of scope — this fix does not cover all of Redis's blast radius
> `RateLimiterService` and `AnalyticsConsumerService`'s idempotency guard
> ([[Kafka-Idempotent-Aggregation-and-DLQ]]) also depend on Redis and were **not** touched by
> this ticket. If Redis is *completely* down, `RateLimiterService.checkLimit()` still throws
> before a request ever reaches this now-resilient cache code — the rate limiter, not the
> cache, is the failure point in that scenario. Expected given the deliberate scope decision
> in [[../Scenarios/B-Brownfield-Refactoring]], not a regression.

**Verification:** `UrlCacheServiceTest` — Mockito fault injection (Redis throwing on every
call) proves `get`, `evict`, and `getOrLoad` never throw and always resolve correctly via the
DB loader; a healthy-Redis case confirms normal caching is unchanged.

**Implementation:** `service/UrlCacheService.java`
