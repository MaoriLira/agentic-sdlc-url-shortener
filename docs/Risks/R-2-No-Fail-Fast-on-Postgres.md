---
tags: [risk, postgresql, high-severity, open]
---

# R-2: No Fail-Fast on PostgreSQL Unavailability

Related: [[../Dashboards/05-Risk-and-Failure-Scenario-Analysis]] · [[../Architecture-Decisions/ADR-11-High-Availability-Approach]]

**Severity:** High | **Status:** ⬜ Open — not authorized in the Phase 6 scoping round

## Finding

There's no circuit breaker or aggressive connection-timeout tuning in front of the datasource.
Under a slow (not fully down) Postgres, requests queue on the default HikariCP pool and
Tomcat's request threads, which can turn a database slowdown into full thread-pool
exhaustion — a classic cascading failure, worse than a clean fast failure would be.

## Recommendation

Explicit `hikari.connection-timeout` tuned below the client's patience threshold, and a
documented decision on whether to fail fast with `503` or queue.

**Effort:** Medium. Proposed as priority 5 in the Phase 5 follow-up list; not selected for
the Phase 6 brownfield batch (see [[../Scenarios/B-Brownfield-Refactoring]] for why only two
of five candidates were taken).
