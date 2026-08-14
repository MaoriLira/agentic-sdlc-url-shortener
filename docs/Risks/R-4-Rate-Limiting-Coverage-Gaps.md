---
tags: [risk, rate-limiting, medium-severity, open]
---

# R-4: Rate Limiting Is Incomplete and Auth-Gated

Related: [[../Dashboards/05-Risk-and-Failure-Scenario-Analysis]] · [[../Guardrails/Redis-Fixed-Window-Rate-Limiting]] · [[../Architecture-Decisions/ADR-05-Authentication-Model]] · [[../Architecture-Decisions/ADR-07-Rate-Limit-Thresholds]]

**Severity:** Medium-High | **Status:** ⬜ Open — not authorized in the Phase 6 scoping round

## Finding

Two gaps, found by re-reading `UrlController` against
[[../Guardrails/Redis-Fixed-Window-Rate-Limiting]]'s claims:

1. **Metadata, delete, and stats endpoints have no rate limiting at all** — only `create` and
   `redirect` call `RateLimiterService`. The guardrail doc implied broader coverage than the
   code actually has (since corrected there).
2. **The create endpoint's rate limit check runs *after* authentication succeeds**, keyed on
   `owner.getId()`. A failed API key never gets rate-limited, so `ApiKeyAuthService`'s DB
   lookup (`findByApiKeyHash`) is exercised at unlimited rate for invalid keys — an
   unauthenticated actor can hammer it as fast as the network allows.

## Recommendation

Add a coarse per-IP rate limit ahead of authentication on all write endpoints (independent of
whether the key turns out to be valid), and extend limiting to metadata/stats/delete.

**Effort:** Small–Medium. Proposed as priority 4 in the Phase 5 follow-up list; not selected
for the Phase 6 brownfield batch.
