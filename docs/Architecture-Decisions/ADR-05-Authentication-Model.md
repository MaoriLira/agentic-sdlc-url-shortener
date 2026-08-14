---
tags: [adr, architecture-decision, auth]
---

# ADR-05: Authentication Model

Related: [[../Dashboards/01-Architecture-and-Design]] · [[../Risks/R-4-Rate-Limiting-Coverage-Gaps]]

**Status:** Accepted (Phase 2) | **Ambiguity resolved:** #6 (auth/ownership model)

## Context

Should URL creation require authentication? Should redirects?

## Decision

API-key authentication is required for all write endpoints (create, delete, metadata, stats).
The redirect endpoint (`GET /{shortCode}`) stays fully public/anonymous — it's the product.

## Consequences

- Public redirects mean anyone with a short link can use it, with no friction — correct for
  the product's purpose.
- Writes are attributable to a client (`owner_client_id`), which is what makes per-client rate
  limiting on create possible at all.
- API keys are hashed (SHA-256) at rest and never logged in plaintext; confidentiality in
  transit depends on TLS termination in front of the service — not provisioned in this
  prototype. See [[../Risks/R-9-TLS-Termination-Assumption]].
- [[../Risks/R-4-Rate-Limiting-Coverage-Gaps]] later found the rate-limit check on create runs
  *after* authentication succeeds, so a brute-force attempt against the API key itself isn't
  rate-limited — a gap in URL-108's execution of this ADR, not in the ADR's decision.

**Implementation:** `service/ApiKeyAuthService.java`, `domain/ApiClient.java`,
`db/migration/V1__init_schema.sql` (`core.api_clients`)
