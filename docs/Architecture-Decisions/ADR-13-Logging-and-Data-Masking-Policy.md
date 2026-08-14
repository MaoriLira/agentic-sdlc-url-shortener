---
tags: [adr, architecture-decision, logging, security]
---

# ADR-13: Logging Framework & Data-Masking Policy

Related: [[../Dashboards/01-Architecture-and-Design]] · [[../Guardrails/Structured-Logging-and-Data-Masking]] · [[../Jira-Tickets/Epic-URL-600-Observability-Logging]] · [[../Risks/R-8-No-Observability-Instrumentation]]

**Status:** Accepted (URL-601) | **Ambiguity resolved:** none from Phase 1 — this is a
retroactive instrumentation decision, not a Phase 1 requirement gap.

## Context

The application shipped through 1.0.0 with almost no logging: a few classes touched during
the URL-502 brownfield fix had ad hoc `log.error(...)` calls, everything else had none. There
was no written policy on log levels, and nothing preventing a future change from accidentally
logging the `X-API-Key` header or a full client IP.

## Decision

1. **Framework:** SLF4J + Logback — already on the classpath transitively via
   `spring-boot-starter-web`/`spring-boot-starter-logging`. No new dependency.
2. **Levels:** `DEBUG` for routine hot-path success (redirect resolution, duplicate-event
   skips); `INFO` for low-frequency business events (create, delete); `WARN` for
   expected-but-notable conditions (auth failures, rejected input, rate-limit breaches, 4xx
   responses); `ERROR` for genuine failures (unhandled exceptions, Redis/Kafka failures).
3. **Masking policy:**
   - The `X-API-Key` header value and the stored `api_key_hash` are **never logged, in any
     form** — not masked, not truncated. Masking a high-entropy secret is not a substitute
     for not logging it.
   - Client IP addresses are masked to the /24 (last octet) via `util/LogSanitizer.maskIp`
     wherever they're logged (`RateLimiterService`, `RedirectController`).
   - Long URLs are **not** treated as secrets and are logged where useful (they're already
     stored in PostgreSQL in plaintext by design — see [[ADR-04-Default-and-Max-TTL]]), except
     in the create/delete business-event log, which logs only `shortCode` — a URL a caller
     submits can carry a third party's token embedded in its query string, so it's excluded
     from that specific log line as a conservative default, not because URLs in general are
     classified as sensitive in this system.
4. **Pattern:** a single `logback-spring.xml` console pattern replaces Spring Boot's default;
   levels stay in `application.yml` (`logging.level.*`) rather than hardcoded in the XML, so
   they're environment-overridable without a rebuild.

## Consequences

- Every class touching a request, a security decision, or a failure path now has a logger —
  see [[../Guardrails/Structured-Logging-and-Data-Masking]] for the full list and the exact
  level policy with examples.
- `ApiKeyAuthServiceTest` attaches a real Logback `ListAppender` to prove the masking policy
  holds, rather than trusting it by inspection alone.
- **Not decided here:** JSON/machine-parseable log output. That would need a new dependency
  (`logstash-logback-encoder`, or Spring Boot 3.4's native structured-logging support — this
  project is pinned to 3.3.4) and is exactly the kind of decision this project's own
  orchestration model requires human approval for. A follow-up ADR is the right vehicle if
  JSON output is wanted later.
- **Not decided here:** metrics/tracing instrumentation (success rate, MTTR, latency). That's
  [[../Risks/R-8-No-Observability-Instrumentation|R-8]], still open. Logs and metrics are
  different concerns; shipping one doesn't close the other.

**Implementation:** `util/LogSanitizer.java`, `logback-spring.xml`,
`application.yml` (`logging.level.*`)
