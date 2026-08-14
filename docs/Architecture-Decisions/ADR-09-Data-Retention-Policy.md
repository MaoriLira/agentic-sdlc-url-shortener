---
tags: [adr, architecture-decision, retention]
---

# ADR-09: Data Retention Policy

Related: [[../Dashboards/01-Architecture-and-Design]] · [[../Jira-Tickets/Epic-URL-200-Async-Analytics]]

**Status:** Accepted (Phase 2) | **Ambiguity resolved:** #10 (data retention)

## Context

How long is analytics data kept, and are there privacy implications (e.g. storing raw client
IPs) worth bounding?

## Decision

Raw click events: retained via Kafka topic retention only (90 days) — never persisted to
Postgres. Daily rollups: 2-year retention target. No raw IP address is stored in the event
schema at all (`ClickEvent` has no IP field).

## Consequences

- Raw-event storage cost is bounded by Kafka's own retention config, not an ever-growing table.
- Rollup retention protects long-term query performance on `click_daily_rollup`.

> [!danger] Audit finding — orphaned configuration
> `application.yml` defines `urlshortener.retention.daily-rollup-days: 730` (2 years, matching
> this ADR's target), but **no code reads this property** — no `RetentionProperties` class, no
> scheduled purge job. This was found during the Phase 7 code-to-docs audit: the config value
> is correct and consistent with this ADR, but it's dead configuration, not a wired control.
> [[../Jira-Tickets/Epic-URL-200-Async-Analytics#URL-207 — Analytics Data Retention Policy|URL-207]]
> already disclosed "purge job not yet automated" — this finding is more specific: the
> retention *number* exists in config as if it were enforced, which is slightly misleading on
> its own. Fix would be either wiring a scheduled job to it, or removing the property until one
> exists.

**Implementation:** `application.yml` (`urlshortener.retention.daily-rollup-days` — unused),
Kafka topic retention config (broker-side, not in application code)
