---
tags: [adr, architecture-decision, availability]
---

# ADR-11: High Availability Approach

Related: [[../Dashboards/01-Architecture-and-Design]] · [[../Risks/R-1-Redis-Triple-Point-of-Failure]] · [[../Risks/R-2-No-Fail-Fast-on-Postgres]]

**Status:** Accepted (Phase 2) | **Ambiguity resolved:** #12 (HA requirements)

## Context

Is a single-region deployment acceptable, or does the design need to demonstrate multi-AZ/DR
from the start?

## Decision

Single-region, single-instance Postgres and Redis for the prototype. The design leaves
"replica seams" (connection config that would point at a read replica, a clustered Redis) but
doesn't provision them.

## Consequences

- Matches the actual deliverable scope (a prototype, not a production deployment).
- The real cost of this decision showed up later, in the Phase 5 risk analysis: single-instance
  dependencies with **no fallback behavior** turned out to be a bigger issue than
  single-instance *placement* — see [[../Risks/R-1-Redis-Triple-Point-of-Failure]] and
  [[../Risks/R-2-No-Fail-Fast-on-Postgres]]. This ADR accepted the availability trade-off; it
  didn't anticipate that the failure *handling* around it would also be missing.

**Implementation:** `docker-compose.yml` (single instances), `application.yml`
