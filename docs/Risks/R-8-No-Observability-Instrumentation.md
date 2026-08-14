---
tags: [risk, observability, high-severity, open, force-multiplier]
---

# R-8: No Reliability/Observability Instrumentation

Related: [[../Dashboards/05-Risk-and-Failure-Scenario-Analysis]] · [[../00-Executive-Summary]] · [[R-1-Redis-Triple-Point-of-Failure]] · [[R-2-No-Fail-Fast-on-Postgres]] · [[R-6-Silent-Analytics-Data-Loss]]

**Severity:** High (force-multiplier) | **Status:** ⬜ Open — not authorized in the Phase 6 scoping round

## Finding

The requirements explicitly call for audit-grade observability and reliability metrics
(success rate, retry/rollback frequency, MTTR, end-to-end latency). Today only Actuator's
`health` and `info` endpoints are exposed. Worth stating plainly: **without this, none of the
other failure modes in this register are detectable in practice** until a human notices
something's wrong — this is *why* [[R-1-Redis-Triple-Point-of-Failure]],
[[R-2-No-Fail-Fast-on-Postgres]], and [[R-6-Silent-Analytics-Data-Loss]] stay invisible.

## Recommendation

This is the highest-leverage single fix in the whole register — Micrometer is already on the
classpath transitively via Actuator, so enabling a Prometheus registry and instrumenting the
four numbers the requirements name is comparatively low effort for high payoff.

**Effort:** Small–Medium. Proposed as priority 2 in the Phase 5 follow-up list — the
highest-value item not selected for the Phase 6 brownfield batch, deliberately, to keep that
batch small (see [[../Scenarios/B-Brownfield-Refactoring]]).
