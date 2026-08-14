---
tags: [risk, redis, high-severity, partially-fixed]
---

# R-1: Redis Is a Hidden Triple Point of Failure

Related: [[../Dashboards/05-Risk-and-Failure-Scenario-Analysis]] · [[../Guardrails/Cache-Aside-Graceful-Degradation]] · [[../Architecture-Decisions/ADR-11-High-Availability-Approach]] · [[../Jira-Tickets/Epic-URL-500-Brownfield-Remediation]]

**Severity:** High | **Status:** 🟡 Partially fixed — URL-502

> [!success] Status: Partially fixed — URL-502
> The cache-aside path (`UrlCacheService`) now degrades gracefully on Redis failure — see
> [[../Guardrails/Cache-Aside-Graceful-Degradation]] and
> [[../Scenarios/B-Brownfield-Refactoring]] for what was built and, importantly, what
> **wasn't**: `RateLimiterService` and the Kafka-consumer idempotency guard are unchanged.
> This was a deliberate scoping decision, not an oversight — the finding below stands in full
> for those two.

## Finding

Three unrelated concerns share one Redis instance with no fallback: **cache-aside reads**
(`UrlCacheService`), **rate limiting** (`RateLimiterService`), and **Kafka-consumer
idempotency** (`AnalyticsConsumerService`). Originally, none of the three call sites caught a
Redis connection failure. A Redis outage therefore didn't just slow the cache — it threw an
unhandled exception on every redirect and every create (rate-limit check), surfacing as a
blanket `500` with no dedicated error path, and simultaneously stalled analytics aggregation.

A single-instance dependency was an explicit, reasonable scope decision for a prototype
([[../Architecture-Decisions/ADR-11-High-Availability-Approach|ADR-11]]) — the issue isn't
that Redis is unclustered, it's that its failure mode was **fail-closed everywhere**,
including for rate limiting, where fail-open (let the request through, log a warning) is the
safer default: an unavailable rate limiter should not itself become an outage.

## Recommendation

Wrap all three call sites so a Redis failure degrades gracefully — rate limiting fails open,
cache-aside falls through to a direct DB read, and the idempotency guard fails open with a
logged warning (accepting rare over-counting over blocked aggregation).

**Cache-aside: done** ([[../Guardrails/Cache-Aside-Graceful-Degradation]]).
**Rate limiting and the idempotency guard: open follow-ups**, not yet ticketed.
