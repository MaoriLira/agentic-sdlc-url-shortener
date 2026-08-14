---
tags: [adr, architecture-decision, ttl]
---

# ADR-04: Default & Max TTL

Related: [[../Dashboards/01-Architecture-and-Design]] · [[../Jira-Tickets/Epic-URL-100-Core-APIs]]

**Status:** Accepted (Phase 2) | **Ambiguity resolved:** #5 (default/max TTL)

## Context

Should a short URL expire by default, and if a caller sets an expiry, how far out can it be?

## Decision

No default expiration — a URL lives forever unless `expiresAt` is explicitly set at create
time. If set, it's capped at 5 years out.

## Consequences

- Matches typical URL-shortener UX (permanent by default).
- The 5-year cap bounds unbounded data growth without being restrictive for realistic use.
- Expiry enforcement is **lazy** — checked at read time (`UrlMapping.isExpired()`), not by a
  background job flipping status. See [[../00-Executive-Summary]] known scope boundaries.

**Implementation:** `domain/UrlMapping.java#isExpired`,
`service/UrlShortenerService.java#effectiveTtl`
