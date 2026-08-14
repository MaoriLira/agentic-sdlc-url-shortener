---
tags: [adr, architecture-decision, analytics]
---

# ADR-12: Geo-Analytics Scope

Related: [[../Dashboards/01-Architecture-and-Design]] · [[../Jira-Tickets/Epic-URL-200-Async-Analytics]]

**Status:** Accepted (Phase 2) | **Ambiguity resolved:** #13 (geo-analytics data source)

## Context

Should click events carry geo-location (derived from IP), and if so, from what data source?

## Decision

Out of scope for this build. The event schema reserves a `geoRegion` field, left `null`.

## Consequences

- Avoids pulling in a third-party GeoIP dependency (data file licensing, update cadence) that
  the assessment doesn't require.
- The schema doesn't need to change if geo-analytics is added later — only the producer side
  (`ClickEvent.of(...)`) and consumer aggregation would need updating.

**Implementation:** `domain/event/ClickEvent.java` (`geoRegion` field, always `null`),
`db/migration/V2__analytics_schema.sql` (`click_daily_rollup.geo_region` column, unpopulated)
