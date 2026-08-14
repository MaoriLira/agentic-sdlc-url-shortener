---
tags: [jira-ticket, epic, greenfield, caching, redis]
---

# Epic: URL-300 — Caching Layer

Dashboard: [[../Dashboards/02-Agentic-Workflow-and-Jira-Tickets]] · Related: [[../Dashboards/01-Architecture-and-Design]] · [[../Dashboards/03-Business-Rules-and-Guardrails]]

Scope: keeping the highest-traffic path (redirect lookups) off the database in the common
case, safely. **Status: Done (Phase 4); resilience hardened further in URL-502 — see
[[Epic-URL-500-Brownfield-Remediation]].**

---

## URL-301 — Caching Technology Selection

**Type:** Task (architecture decision — required human approval) | **Depends on:** URL-401

**Problem / Goal:** Choose and provision the cache sitting in front of the redirect-lookup
path.

**Technical implementation plan:** Redis selected (ADR in [[../Dashboards/01-Architecture-and-Design]]);
`StringRedisTemplate` (Spring Boot auto-configured); single instance for the prototype, local
dev via `docker-compose`.

**Acceptance criteria:** decision documented with the HA trade-off explicitly acknowledged
(ADR #12 — single-instance is an accepted prototype-scope limitation, not an oversight).

**Implementation:** `docker-compose.yml`, `application.yml` (`spring.data.redis.*`)

---

## URL-302 — Cache-Aside Read Path for Redirects

**Type:** Story | **Depends on:** URL-301, URL-402

**Problem / Goal:** Serve the redirect lookup from cache in the common case, without adding a
separate write-through path to keep in sync.

**Technical implementation plan:** `UrlCacheService.getOrLoad()` — Redis `GET` first; on
miss, load from PostgreSQL via a caller-supplied `Supplier<Optional<CacheableValue>>` and
write the result back.

**Acceptance criteria:** a cache hit never touches the database; a cache miss transparently
falls back to Postgres and repopulates the cache for the next request.

**Implementation:** `service/UrlCacheService.java`

---

## URL-303 — Cache Invalidation on Update/Delete/Expiry

**Type:** Task | **Depends on:** URL-302, URL-106

**Problem / Goal:** Never serve a redirect for a URL that's been deleted, and never cache a
soon-to-expire URL for longer than it's actually valid.

**Technical implementation plan:** `UrlShortenerService.delete()` explicitly evicts the cache
entry; on write, the cache TTL is capped at `min(configuredTtl, time-until-expiry)` rather
than always using the full default TTL.

**Acceptance criteria:** a deleted URL stops redirecting immediately (evicted, not waiting out
its TTL); a URL expiring in 5 minutes is never cached for the full 24-hour default.

**Implementation:** `service/UrlShortenerService.java#delete`,
`service/UrlShortenerService.java#effectiveTtl`, `service/UrlCacheService.java`

---

## URL-304 — Hot-Key / Cache-Stampede Protection

**Type:** Task | **Depends on:** URL-302

**Problem / Goal:** Prevent a burst of concurrent requests for the same just-expired popular
key from all hitting Postgres at once.

**Technical implementation plan:** a Redis `SETNX` lock (2s TTL) on cache miss — the request
that wins the lock loads from the DB and repopulates the cache; others wait briefly (bounded:
5 attempts, 50ms apart) and re-check, falling back to a direct, unlocked DB read only if that
bounded wait is exceeded.

**Acceptance criteria:** only one DB read occurs per stampede event under normal conditions;
losing requests never block indefinitely — see the sequence diagram in
[[../Dashboards/01-Architecture-and-Design]].

**Implementation:** `service/UrlCacheService.java#getOrLoad`

---

## URL-305 — TTL & Eviction Policy Definition

**Type:** Task | **Depends on:** URL-301

**Problem / Goal:** Define how long a cached entry lives and bound staleness without causing
synchronized mass-expiry spikes.

**Technical implementation plan:** a configurable base TTL
(`urlshortener.cache.ttl-seconds`, default 86400) plus 0–10% random jitter applied on every
write.

**Acceptance criteria:** TTL is externally configurable without a redeploy; jitter is present
on every cache write, not just the first.

**Implementation:** `application.yml` (`urlshortener.cache.ttl-seconds`),
`config/CacheProperties.java`, `service/UrlCacheService.java`
