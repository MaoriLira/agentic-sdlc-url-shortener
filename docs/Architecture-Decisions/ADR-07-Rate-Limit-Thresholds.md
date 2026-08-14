---
tags: [adr, architecture-decision, rate-limiting]
---

# ADR-07: Rate Limit Thresholds

Related: [[../Dashboards/01-Architecture-and-Design]] · [[../Guardrails/Redis-Fixed-Window-Rate-Limiting]] · [[../Risks/R-4-Rate-Limiting-Coverage-Gaps]] · [[../Risks/R-7-Rate-Limiter-Boundary-Burst]]

**Status:** Accepted (Phase 2) | **Ambiguity resolved:** #8 (rate limiting expectations)

## Context

Phase 1 flagged that "rate limiting expectations" were unstated — no target QPS per client,
no distinction between per-IP and per-key limiting.

## Decision

100 requests/minute per API key on create; 1000 requests/minute per IP on redirect. Both
externally configurable (`urlshortener.rate-limit.*`) without a redeploy.

## Consequences

- Generous enough for legitimate use, tight enough to blunt casual abuse.
- The actual implementation is a fixed-window counter, not a token bucket — see
  [[../Guardrails/Redis-Fixed-Window-Rate-Limiting]] for that naming correction and
  [[../Risks/R-7-Rate-Limiter-Boundary-Burst]] for the ~2x boundary-burst consequence of that
  choice.
- Coverage gaps (metadata/stats/delete unlimited; pre-auth create attempts unlimited) were
  found later — see [[../Risks/R-4-Rate-Limiting-Coverage-Gaps]]. This ADR set the *numbers*;
  it didn't fully anticipate *where* they'd be enforced.

**Implementation:** `config/RateLimitProperties.java`, `service/RateLimiterService.java`
