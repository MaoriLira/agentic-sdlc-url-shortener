---
tags: [jira-ticket, epic, greenfield, database, postgresql]
---

# Epic: URL-400 — Database & Persistence

Dashboard: [[../Dashboards/02-Agentic-Workflow-and-Jira-Tickets]] · Related: [[../Dashboards/01-Architecture-and-Design]] · [[../Dashboards/03-Business-Rules-and-Guardrails]]

Scope: the system of record for URL mappings and analytics aggregates — schema, access layer,
and migration discipline. **Status: Done (Phase 4); schema revised in URL-501 — see
[[Epic-URL-500-Brownfield-Remediation]].**

---

## URL-401 — Database Technology Selection

**Type:** Task (architecture decision — required human approval) | **Depends on:** URL-101

**Problem / Goal:** Choose the system of record before any entity or repository code exists.

**Technical implementation plan:** PostgreSQL selected over NoSQL alternatives (ADR in
[[../Dashboards/01-Architecture-and-Design]]) — strong consistency for the short-code uniqueness
guarantee, mature indexing, and a straightforward read-replica story for future HA.

**Acceptance criteria:** decision documented against the actual access pattern (read-heavy,
key-value-dominant lookups); HA approach (replica) noted as design-ready, not provisioned in
the prototype.

**Implementation:** `pom.xml` (postgresql driver), `docker-compose.yml`, `application.yml`

---

## URL-402 — Schema Design for URL Mappings

**Type:** Story (schema change — required human approval) | **Depends on:** URL-401

**Problem / Goal:** Design the core and analytics schemas before any repository/entity code is
written against them.

**Technical implementation plan:** `core.url_mappings`, `core.api_clients` in the first
migration; `analytics.click_summary`, `analytics.click_daily_rollup`,
`analytics.click_events_dlq` in the second.

**Acceptance criteria:** constraints and uniqueness guarantees defined up front; schema
matches the Phase 3 design doc exactly.

> [!warning] Revised later
> The original table-wide `UNIQUE` constraint on `short_code` turned out to permanently lock
> deleted aliases — fixed in URL-501 ([[Epic-URL-500-Brownfield-Remediation]]) with a partial
> unique index. This ticket's acceptance criteria were met as originally written; the defect
> was in what "uniqueness" should have meant, not in whether it was implemented.

**Implementation:** `db/migration/V1__init_schema.sql`, `db/migration/V2__analytics_schema.sql`

---

## URL-403 — Repository/DAO Layer Implementation

**Type:** Story | **Depends on:** URL-402

**Problem / Goal:** Abstract persistence behind a repository interface rather than hand-rolled
SQL/JDBC scattered through the service layer.

**Technical implementation plan:** Spring Data JPA repositories per entity; HikariCP
connection pooling (Spring Boot default); `spring.jpa.hibernate.ddl-auto: validate` so
Hibernate never mutates schema at runtime — Flyway owns that exclusively.

**Acceptance criteria:** CRUD operations available for all five entities; no schema drift
between what Flyway created and what Hibernate expects (validated at startup).

**Implementation:** `repository/*.java`, `domain/*.java`

---

## URL-404 — Short-Code Collision Detection & Handling

**Type:** Task | **Depends on:** URL-402, URL-102

**Problem / Goal:** Guarantee uniqueness for both generated codes (which shouldn't ever
collide) and custom aliases (which legitimately can, when two callers want the same one).

**Technical implementation plan:** generated codes are unique by construction (monotonic DB
sequence — see URL-102), so no collision-retry logic runs on that path; custom aliases rely on
the DB's unique index, and a `DataIntegrityViolationException` from a violated constraint is
caught in the service layer and translated to `409 Conflict`.

**Acceptance criteria:** no retry loop exists (or is needed) for system-generated codes; an
alias collision surfaces as `409`, never a silent overwrite or an unhandled `500`.

**Implementation:** `service/UrlShortenerService.java#create`,
`db/migration/V3__short_code_sequence.sql`

---

## URL-405 — Indexing Strategy for High-Read Throughput

**Type:** Task | **Depends on:** URL-402

**Problem / Goal:** Keep the hot `shortCode → longUrl` lookup cheap even as deleted/expired
rows accumulate over time.

**Technical implementation plan:** a partial index on `short_code` scoped to
`WHERE status = 'ACTIVE'`, so the index stays proportional to *live* URLs, not total rows ever
created.

**Acceptance criteria:** the redirect-lookup query plan uses the partial index, confirmed
during Phase 4 review.

**Implementation:** originally `db/migration/V1__init_schema.sql`; consolidated into the
unique partial index from `V5` in URL-501 (see [[Epic-URL-500-Brownfield-Remediation]]) —
one index now serves both the lookup-performance and uniqueness goals.

---

## URL-406 — Partitioning/Sharding Strategy (Future Scale)

**Type:** Task | **Depends on:** URL-402

**Problem / Goal:** Demonstrate scale-awareness in the design without over-building
infrastructure a prototype doesn't need.

**Technical implementation plan:** documented, not implemented — the plausible shard key would
be a hash of `short_code` or a range over the generation sequence, either of which would keep
routing simple given how the code is generated.

**Acceptance criteria:** a sharding approach is written down for future reference.
**Explicitly not implemented** — a deliberate scope boundary matching the ADR #11 design
target, not an oversight.

**Implementation:** none (design note only) — see [[../Dashboards/01-Architecture-and-Design]] ADR #11

---

## URL-407 — Schema Migration Tooling

**Type:** Task | **Depends on:** URL-402

**Problem / Goal:** Every schema change must be versioned and repeatable — never applied by
hand against a running database.

**Technical implementation plan:** Flyway, with migrations under
`src/main/resources/db/migration`, auto-run on application startup before the app accepts
traffic.

**Acceptance criteria:** every schema change (V1 through V5 as of this writing, including the
brownfield fix in URL-501) is a versioned Flyway migration; zero manual DDL against any
environment.

**Implementation:** `pom.xml` (flyway-core, flyway-database-postgresql),
`db/migration/V*.sql`, `application.yml`
