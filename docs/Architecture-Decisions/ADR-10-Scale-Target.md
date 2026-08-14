---
tags: [adr, architecture-decision, scale]
---

# ADR-10: Scale Target

Related: [[../Dashboards/01-Architecture-and-Design]] · [[ADR-01-Short-Code-Generation-Strategy]] · [[ADR-11-High-Availability-Approach]]

**Status:** Accepted (Phase 2) | **Ambiguity resolved:** #11 (expected scale)

## Context

Cache, DB, and broker sizing decisions all depend on an expected load — undefined in the
original requirements.

## Decision

Design target: 1,000 RPS redirect, 50 RPS create, 100M total links. This is a *design* target
used to justify architectural choices, not a load-tested SLA for the prototype build.

## Consequences

- Justifies Redis in front of the redirect path (read-heavy, key-value-dominant).
- Justifies the single-sequence short-code generator (see
  [[ADR-01-Short-Code-Generation-Strategy]]) — 50 RPS create is comfortably within what a
  single Postgres sequence handles.
- The prototype itself was **not** load-tested at this target — it was smoke-tested for
  correctness, not benchmarked for throughput. Don't read "design target" as "verified
  capacity."

**Implementation:** informs sizing choices throughout — no single file
