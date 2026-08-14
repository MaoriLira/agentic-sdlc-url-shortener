---
tags: [jira-ticket, epic, observability, logging]
---

# Epic: URL-600 — Observability & Structured Logging

Dashboard: [[../Dashboards/02-Agentic-Workflow-and-Jira-Tickets]] · Related: [[../Risks/R-8-No-Observability-Instrumentation]] · [[../Architecture-Decisions/ADR-13-Logging-and-Data-Masking-Policy]]

> [!info] A retroactive, cross-cutting epic
> URL-100–URL-500 shipped with almost no application logging — a couple of `service` classes
> had ad hoc loggers, most had none. This epic instruments the *already-built* system rather
> than a new feature, which is why its single ticket touches nearly every existing class
> instead of depending on one prior epic. It's related to but distinct from
> [[../Risks/R-8-No-Observability-Instrumentation]] (R-8): R-8 is about *metrics*
> (success rate, MTTR, latency via Micrometer/Prometheus — still unauthorized, still open).
> This epic is about *logs*. Shipping one doesn't close the other.

| Ticket | Title | Type | Depends on |
|---|---|---|---|
| URL-601 | Add structured logging & data-masking policy across the application | Task | — (cross-cutting, retroactive) |

---

## URL-601 — Add structured logging & data-masking policy across the application

**Type:** Task | **Depends on:** — | **Status:** Done

**Problem:** Most classes in `project/src/main/java` have no logging at all. Business events
(a URL created, deleted, an auth failure, a rejected input, a rate-limit breach) leave no
trace. The handful of existing log statements (`UrlCacheService`, `KafkaConsumerConfig`,
`ClickEventPublisher`) are inconsistent in level and were added ad hoc while fixing specific
bugs (URL-502, URL-206), not as a deliberate logging strategy. There is also no explicit
policy anywhere preventing a future change from accidentally logging the `X-API-Key` header,
a client IP in full, or the stored `api_key_hash`.

**Goal:** Every significant business event and failure path logs at an appropriate level,
consistently, using parameterized SLF4J calls — and there's a written, testable policy that
sensitive values (API keys, in particular) are never written to a log, checked in code review
against an explicit rule rather than left to individual judgment each time.

**Technical implementation plan:**
1. Add SLF4J (`org.slf4j.Logger`/`LoggerFactory`) to every class handling a request, a
   security decision, or a failure path that previously had none:
   `UrlController`, `RedirectController`, `UrlShortenerService`, `ApiKeyAuthService`,
   `RateLimiterService`, `UrlValidationService`, `AnalyticsConsumerService`,
   `GlobalExceptionHandler`.
2. Level policy (see [[../Architecture-Decisions/ADR-13-Logging-and-Data-Masking-Policy]] for
   the full rationale):
   - `DEBUG` — routine successful hot-path operations (redirect resolution, metadata reads,
     duplicate-event skips). Not `INFO`, because at the ADR-10 design target (1,000 RPS
     redirect) that would flood the log at `INFO`.
   - `INFO` — significant, low-frequency business events: URL created, URL deleted.
   - `WARN` — recoverable/expected-but-notable conditions: auth failures, rejected input
     (SSRF/open-redirect guard tripped), rate-limit breaches, `404`/`410`/`409` responses.
   - `ERROR` — genuine failures needing attention: unhandled exceptions (new catch-all in
     `GlobalExceptionHandler`, previously falling through to Spring Boot's default handler
     with no application-level log at all), Redis/Kafka failures (already correct from
     URL-502/URL-206, left as-is).
3. New `util/LogSanitizer` with `maskIp(String)` — masks the last IPv4 octet
   (`203.0.113.42` → `203.0.113.xxx`), applied everywhere a client IP is logged (the
   `RateLimiterService` redirect-scope identifier).
4. **Never log the raw `X-API-Key` value or the stored `api_key_hash`, in any form — not
   masked, not truncated, not on any code path.** `ApiKeyAuthService` logs the resolved
   client's *name* on success and a generic message with no key material on failure.
5. `logback-spring.xml` — a single, consistent console pattern (timestamp, level, logger,
   thread, message) applied uniformly, replacing Spring Boot's default pattern.

**Acceptance criteria:**
- Every class listed in step 1 has a logger and at least one log statement at the level
  specified by the policy.
- `LogSanitizerTest` proves `maskIp` masks correctly and is not reversible from the log line
  alone.
- `ApiKeyAuthServiceTest` attaches a Logback `ListAppender` to the class under test and
  asserts the raw API key string never appears in any captured log event, across both the
  success and failure paths.
- `GlobalExceptionHandler` has a catch-all `Exception` handler returning `500` and logging at
  `ERROR` — previously, an unhandled exception produced no application log line at all.

> [!warning] Explicitly out of scope
> JSON/machine-parseable log output (e.g. via `logstash-logback-encoder`, or Spring Boot
> 3.4's native structured-logging support — this project is pinned to Boot 3.3.4) would add a
> new dependency and is exactly the kind of decision this project's own orchestration model
> requires human approval for before shipping. Not added here. `logback-spring.xml` defines a
> consistent plain-text pattern, which is "structured" in the sense of consistent fields, not
> in the machine-parseable sense. A follow-up ADR would be the right vehicle if JSON output is
> wanted later — see [[../Architecture-Decisions/ADR-13-Logging-and-Data-Masking-Policy]].

**Implementation:** `util/LogSanitizer.java`, `logback-spring.xml`, and log statements added
to `api/UrlController.java`, `api/RedirectController.java`, `api/GlobalExceptionHandler.java`,
`service/UrlShortenerService.java`, `service/ApiKeyAuthService.java`,
`service/RateLimiterService.java`, `service/UrlValidationService.java`,
`service/AnalyticsConsumerService.java`.
