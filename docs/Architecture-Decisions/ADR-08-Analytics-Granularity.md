---
tags: [adr, architecture-decision, analytics]
---

# ADR-08: Analytics Granularity & Consistency

Related: [[../Dashboards/01-Architecture-and-Design]] · [[../Jira-Tickets/Epic-URL-200-Async-Analytics]]

**Status:** Accepted (Phase 2) | **Ambiguity resolved:** #9 (analytics granularity)

## Context

Does click analytics need to be real-time and exact, or is near-real-time/approximate
acceptable? This determines whether an approximate structure (e.g. HyperLogLog) is warranted.

## Decision

Near-real-time (async via Kafka, seconds of lag), eventual consistency, **exact** counts — no
probabilistic/approximate counting structures.

## Consequences

- Simpler implementation: plain `BIGINT` counters in `click_summary`/`click_daily_rollup`,
  incremented per event.
- Correct as long as consumer idempotency holds (see
  [[../Guardrails/Kafka-Idempotent-Aggregation-and-DLQ]]) — exact counting is only as exact as
  the dedup guarantee behind it.
- Revisit if scale grows well past the [[ADR-10-Scale-Target|current target]]; approximate
  structures trade exactness for bounded memory at very high cardinality.

**Implementation:** `service/AnalyticsConsumerService.java`,
`db/migration/V2__analytics_schema.sql`
