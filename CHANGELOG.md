# Changelog

All notable changes to this project are documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versioning follows
[Semantic Versioning](https://semver.org/).

## [1.1.0] - 2026-08-13

Adds structured application logging and a data-masking policy — ticket
[`URL-601`](docs/Jira-Tickets/Epic-URL-600-Observability-Logging.md), branch
`feature/URL-601-add-logs`.

### Added

- **Structured Logging** — SLF4J/Logback log statements across the API, service, and
  exception-handling layers, with a defined level policy: `DEBUG` for routine hot-path
  success, `INFO` for business events (create/delete), `WARN` for expected-but-notable
  conditions (auth failures, rejected input, rate-limit breaches, 4xx responses), `ERROR` for
  genuine failures.
- **`GlobalExceptionHandler` catch-all** — a previously-unhandled exception now returns a
  logged `500` instead of falling through to Spring Boot's default error page with no
  application-level log line at all.
- **Data-masking policy** — the `X-API-Key` header value and stored `api_key_hash` are never
  logged, in any form; client IPs are masked to the last octet via the new
  `util/LogSanitizer` before being logged. See
  [`docs/Architecture-Decisions/ADR-13-Logging-and-Data-Masking-Policy.md`](docs/Architecture-Decisions/ADR-13-Logging-and-Data-Masking-Policy.md).
- **`logback-spring.xml`** — a single consistent console pattern, replacing Spring Boot's
  default; levels remain configurable via `logging.level.*` in `application.yml`.
- **Tests** — `LogSanitizerTest` (IP-masking correctness) and `ApiKeyAuthServiceTest` (a real
  Logback `ListAppender` attached to the class under test, proving the raw API key is absent
  from every log event across all four authentication outcomes). Suite: 30 → 38 tests.

### Not included (explicitly out of scope — see ADR-13)

- JSON/machine-parseable log output — would require a new dependency
  (`logstash-logback-encoder`) and its own ADR before adding.
- Metrics/tracing instrumentation (success rate, MTTR, latency) — still
  [R-8](docs/Risks/R-8-No-Observability-Instrumentation.md), still open. This release is logs
  only.

## [1.0.0] - 2026-08-13

Initial release. Built end-to-end through an Agentic Execution Model with human-in-the-loop
governance — full process history in [`docs/00-Executive-Summary.md`](docs/00-Executive-Summary.md).

### Added

- **Core URL Shortening APIs** — create, redirect (`302`), metadata, delete, custom aliases,
  TTL/expiration. Short codes generated from a collision-free Base62-encoded DB sequence.
- **Redis Caching** — cache-aside read path in front of the redirect hot path, with jittered
  TTL and stampede protection.
- **Graceful Degradation** — the cache-aside path falls back to PostgreSQL on any Redis
  failure instead of returning `500`; every call site is individually fault-tolerant.
- **Async Analytics** — Kafka-based click-event pipeline with idempotent aggregation
  (`eventId` dedup) and a dead-letter queue with bounded retry.
- **PostgreSQL Persistence** — schema managed entirely through versioned Flyway migrations
  (`V1`–`V5`), including a partial unique index enabling short-code/alias reuse after delete.
- **Guardrails** — internal-target/open-redirect validation, Redis-backed rate limiting,
  RFC 7807 error responses, API-key authentication on all write endpoints.
- **Docker Infrastructure** — a single root-level `docker-compose.yml` provisioning
  Postgres, Redis, and Kafka with the exact credentials/ports the application expects.
- **Maven Wrapper** — `./mvnw`, pinned to Maven 3.9.10, as the standard build entry point.
- **Automated Test Suite** — 30 tests: pure unit, Mockito fault-injection, and
  Testcontainers-backed integration tests covering the full API lifecycle and error paths.
- **Documentation Knowledge Graph** — an Obsidian vault under `docs/`, starting at
  `00-Executive-Summary.md`: architecture diagrams, 12 Architecture Decision Records, 5
  guardrails, 26+ Jira-style tickets, a 9-item risk register, and a brownfield remediation
  case study.
- **Insomnia Collection** — a self-cleaning end-to-end request chain
  (`_insomnia_collection/`) exercising create → redirect → metadata → delete.

### Fixed (brownfield remediation, pre-1.0.0)

- Deleted custom aliases were previously unusable forever due to a table-wide unique
  constraint; replaced with a partial index scoped to active rows.
- The cache-aside layer previously had no fallback on Redis failure, turning a cache outage
  into a site-wide `500`; every call site now degrades gracefully.

### Known limitations

Documented explicitly rather than silently assumed away — see
[`docs/00-Executive-Summary.md`](docs/00-Executive-Summary.md) "Known scope boundaries" and
[`docs/Dashboards/05-Risk-and-Failure-Scenario-Analysis.md`](docs/Dashboards/05-Risk-and-Failure-Scenario-Analysis.md)
for the full list: no background expiry-reaper job, rate limiting and the Kafka idempotency
guard remain unguarded against Redis failure, no reliability/observability instrumentation
yet, single-region deployment target.
