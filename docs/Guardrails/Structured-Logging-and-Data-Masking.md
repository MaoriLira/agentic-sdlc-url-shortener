---
tags: [guardrail, logging, security, data-masking]
---

# Guardrail: Structured Logging & Data-Masking Policy

Related: [[../Architecture-Decisions/ADR-13-Logging-and-Data-Masking-Policy]] · [[../Jira-Tickets/Epic-URL-600-Observability-Logging]] · [[../Risks/R-8-No-Observability-Instrumentation]]

**Ticket:** [[../Jira-Tickets/Epic-URL-600-Observability-Logging#URL-601 — Add structured logging & data-masking policy across the application|URL-601]]

SLF4J + Logback (already on the classpath via Spring Boot, no new dependency). A written
level policy and a hard rule on what never gets logged.

## Level policy

| Level | Used for | Example |
|---|---|---|
| `DEBUG` | Routine hot-path success | `RedirectController`: resolved shortCode; `AnalyticsConsumerService`: recorded/duplicate-skipped click |
| `INFO` | Low-frequency business events | `UrlShortenerService`: URL created, URL deleted |
| `WARN` | Expected-but-notable | `ApiKeyAuthService`: auth rejected; `RateLimiterService`: limit exceeded; `UrlValidationService`: internal-target rejected; `GlobalExceptionHandler`: every 4xx response |
| `ERROR` | Genuine failures | `GlobalExceptionHandler`: unhandled exception (new — previously no log line at all); `UrlCacheService`/`KafkaConsumerConfig`: Redis/Kafka failures (unchanged from URL-502/URL-206) |

> [!info] Why DEBUG, not INFO, on the redirect hot path
> `RedirectController` and `AnalyticsConsumerService` sit on the highest-traffic path in the
> system — the [[../Architecture-Decisions/ADR-10-Scale-Target|ADR-10 design target]] is
> 1,000 RPS on redirect. Logging every successful redirect at `INFO` would flood output at
> that volume; `DEBUG` keeps it available for troubleshooting without being the default.

## The masking rule

> [!danger] Never logged, in any form
> The `X-API-Key` header value and the stored `api_key_hash` are never written to a log line
> — not masked, not truncated, not even at `TRACE`. Masking a high-entropy secret doesn't
> meaningfully protect it; the only safe answer is to not log it at all. `ApiKeyAuthService`
> logs the *resolved client's* id/name after successful auth, and a generic reason on
> failure — never the key itself, on either path.

```java
// service/ApiKeyAuthService.java
if (rawApiKey == null || rawApiKey.isBlank()) {
    log.warn("Authentication rejected: missing X-API-Key header");   // no key material
    throw new UnauthorizedException("Missing X-API-Key header");
}
```

Client IP addresses are a softer case — useful for abuse investigation, but still commonly
treated as personal data. `util/LogSanitizer.maskIp` masks the last octet before anything
logs an IP:

```java
public static String maskIp(String ip) {
    // 203.0.113.42 -> 203.0.113.xxx; non-IPv4 input returned unchanged
}
```

Applied in `RateLimiterService` (breach log) and `RedirectController` (DEBUG resolution log).

> [!success] Proven, not just claimed
> `ApiKeyAuthServiceTest` attaches a real Logback `ListAppender` to the class's actual logger
> and asserts the raw key string is absent from every captured log event, across all four
> authentication outcomes (success, missing header, unknown key, suspended client).
> `LogSanitizerTest` proves the IP-masking behavior directly.

## What this doesn't cover

> [!warning] Scope boundary
> This is logs only. [[../Risks/R-8-No-Observability-Instrumentation|R-8]] (metrics: success
> rate, retry/rollback frequency, MTTR, latency) is untouched and remains an open risk. JSON
> log output is also out of scope — see
> [[../Architecture-Decisions/ADR-13-Logging-and-Data-Masking-Policy]] for why that would need
> its own ADR before adding a new dependency.

**Implementation:** `util/LogSanitizer.java`, `logback-spring.xml`, `application.yml`
(`logging.level.*`), and log statements across `api/UrlController.java`,
`api/RedirectController.java`, `api/GlobalExceptionHandler.java`,
`service/UrlShortenerService.java`, `service/ApiKeyAuthService.java`,
`service/RateLimiterService.java`, `service/UrlValidationService.java`,
`service/AnalyticsConsumerService.java`.
