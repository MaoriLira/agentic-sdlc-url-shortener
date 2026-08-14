---
tags: [jira-ticket, epic, brownfield, scenario-b]
---

# Epic: URL-500 — Brownfield Risk Remediation (Scenario B)

Dashboard: [[../Dashboards/02-Agentic-Workflow-and-Jira-Tickets]] · Related: [[../Dashboards/05-Risk-and-Failure-Scenario-Analysis]] · [[../Scenarios/B-Brownfield-Refactoring]]

> [!info] A different kind of epic
> URL-100–URL-400 were **Greenfield**: decomposed before any code existed. URL-500 is
> **Brownfield**: it exists because [[../Dashboards/05-Risk-and-Failure-Scenario-Analysis]] found real
> defects in already-shipped, already-tested code. The full narrative — how the process
> arrived at these two tickets out of nine candidate risks, and how each was actually built —
> is in [[../Scenarios/B-Brownfield-Refactoring|Scenario B: Brownfield Refactoring]]. This
> file is the ticket record; that document is the process record.

| Ticket | Title | Type | Depends on | Source risk |
|---|---|---|---|---|
| URL-501 | Alias unique constraint → partial index | Bug fix | URL-402 | R-3 |
| URL-502 | Redis graceful degradation for cache-aside | Bug fix | URL-302 | R-1 (scoped) |

---

## URL-501 — Alias unique constraint → partial index

**Type:** Bug fix | **Depends on:** URL-402 | **Status:** Done

**Problem:** `core.url_mappings.short_code` has a table-wide `UNIQUE` constraint. Deleting a
URL only soft-deletes it (`status = 'DELETED'`) — the row, and its `short_code` value, stay in
the table forever. Result: a deleted custom alias (or, in principle, a deleted generated code)
can never be reused, by anyone, permanently.

**Goal:** A deleted `short_code` must become available for reuse immediately after deletion,
without weakening uniqueness among currently-active URLs.

**Technical implementation plan:**
1. Flyway migration `V5__alias_reuse_partial_unique_index.sql`: drop the
   `url_mappings_short_code_key` constraint; replace it with a unique index scoped to
   `WHERE status = 'ACTIVE'`.
2. Consolidate with the pre-existing non-unique lookup index on the same
   `(short_code) WHERE status = 'ACTIVE'` shape — one index now serves both jobs, rather than
   carrying two redundant indexes.
3. Remove the now-inaccurate `unique = true` hint from the `UrlMapping` JPA entity (partial
   uniqueness can't be expressed declaratively in JPA; the real constraint lives in SQL).
4. No application-code behavior change needed: `UrlShortenerService.create()` already
   translates a unique-violation into `409 Conflict` generically, and that translation still
   fires correctly against the new partial index.

**Acceptance criteria:** an integration test creates a custom alias, deletes it, then
successfully re-creates a URL under the same alias — see
`UrlShortenerIntegrationTest#deletedCustomAlias_canBeReused`.

**Implementation:** `db/migration/V5__alias_reuse_partial_unique_index.sql`,
`domain/UrlMapping.java`

---

## URL-502 — Redis graceful degradation for cache-aside

**Type:** Bug fix | **Depends on:** URL-302 | **Status:** Done (scoped)

**Problem:** [[../Dashboards/05-Risk-and-Failure-Scenario-Analysis#R-1 Redis is a hidden triple point of failure|R-1]]
found that `UrlCacheService` has no fallback on any of its Redis calls. A Redis outage throws
an uncaught `DataAccessException` on every redirect, surfacing as a blanket `500` instead of
the site simply running uncached.

**Goal:** A redirect must still resolve correctly from PostgreSQL when Redis is unavailable —
never a `500` for that reason alone.

**Technical implementation plan:**
1. Wrap every Redis call in `UrlCacheService` (`get`, the stampede lock's `setIfAbsent`, the
   cache `put`, lock release, and `evict`) in a try/catch on
   `org.springframework.dao.DataAccessException`.
2. On catch: log at `ERROR` (this is a real degraded-mode condition worth alerting on, not a
   routine miss) and fall through to the DB loader / treat as a no-op, rather than
   propagating.
3. Preserve existing behavior when Redis is healthy — no change to cache-aside semantics,
   TTL/jitter, or stampede-lock behavior in the non-failure path.

> [!warning] Explicitly out of scope
> `RateLimiterService` and `AnalyticsConsumerService`'s idempotency guard also depend on
> Redis and were **not** touched by this ticket — R-1's original finding covered all three,
> but this ticket was deliberately scoped to the cache-aside path only (see
> [[../Scenarios/B-Brownfield-Refactoring|Scenario B]] for why). One consequence: if Redis is
> *completely* down, `RateLimiterService.checkLimit()` still throws before a request ever
> reaches the now-resilient cache code — the rate limiter, not the cache, is the failure point
> in that scenario. This is expected given the scope decision, not a regression, and is
> tracked as a distinct follow-up.

**Acceptance criteria:** `UrlCacheServiceTest` verifies, via Mockito fault injection (Redis
throwing on every call), that `get`, `evict`, and `getOrLoad` never throw and always resolve
to the correct value via the DB loader.

**Implementation:** `service/UrlCacheService.java`,
`src/test/java/.../service/UrlCacheServiceTest.java`
