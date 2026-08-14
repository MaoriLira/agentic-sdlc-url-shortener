---
tags: [risk, rate-limiting, low-severity, accepted]
---

# R-7: Fixed-Window Rate Limiter Allows a Boundary Burst

Related: [[../Dashboards/05-Risk-and-Failure-Scenario-Analysis]] · [[../Guardrails/Redis-Fixed-Window-Rate-Limiting]]

**Severity:** Low | **Status:** ✅ Accepted trade-off — no action planned

## Finding

Already disclosed in [[../Guardrails/Redis-Fixed-Window-Rate-Limiting]]. Quantifying it: a
client can send up to ~2x the stated limit across a window boundary (e.g. 100 requests at
`T=0:59` and 100 more at `T=1:00`).

## Recommendation

None. Accepted given the generous configured limits (100/min create, 1000/min redirect) — the
burst amplification isn't meaningful at these thresholds.
