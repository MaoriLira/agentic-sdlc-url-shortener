---
tags: [adr, architecture-decision]
---

# ADR-03: Duplicate Long-URL Submissions

Related: [[../Dashboards/01-Architecture-and-Design]] · [[../Jira-Tickets/Epic-URL-100-Core-APIs]]

**Status:** Accepted (Phase 2) | **Ambiguity resolved:** #4 (duplicate long-URL handling)

## Context

If two `POST /api/v1/urls` calls submit the same `longUrl`, should the service return the
existing short code (dedup) or mint a new one?

## Decision

No deduplication. Every create call mints a new short code, even for an identical long URL.

## Consequences

- Different callers (or the same caller running different campaigns) get independently
  trackable codes for the same destination — each has its own click analytics.
- The trade-off: no way to look up "has this URL already been shortened," and the table grows
  one row per create call regardless of long-URL repetition. Not a concern at the
  [[ADR-10-Scale-Target|target scale]].

**Implementation:** `service/UrlShortenerService.java#create` (no existence check before
generating a new code)
