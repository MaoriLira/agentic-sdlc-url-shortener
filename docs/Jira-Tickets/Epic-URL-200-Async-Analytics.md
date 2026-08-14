---
tags: [jira-ticket, epic, greenfield, analytics, kafka]
---

# Epic: URL-200 — Async Analytics Pipeline

Dashboard: [[../Dashboards/02-Agentic-Workflow-and-Jira-Tickets]] · Related: [[../Dashboards/01-Architecture-and-Design]] · [[../Dashboards/03-Business-Rules-and-Guardrails]]

Scope: turning redirect clicks into queryable analytics without adding latency or risk to the
redirect path itself. **Status: Done (Phase 4).**

---

## URL-201 — Analytics Event Schema Definition

**Type:** Task | **Depends on:** URL-101

**Problem / Goal:** Define a versioned click-event shape before wiring the producer/consumer,
so a future schema change doesn't break consumers processing events still in flight.

**Technical implementation plan:** a `ClickEvent` record: `eventId` (UUID, for idempotency),
`schemaVersion`, `shortCode`, `timestamp`, `referrer`, `userAgent`, `geoRegion` (reserved,
nullable).

**Acceptance criteria:** schema is explicitly versioned (`schemaVersion = 1`); geo field
exists in the schema but is intentionally left unpopulated (ADR #13 — out of scope).

**Implementation:** `domain/event/ClickEvent.java`

---

## URL-202 — Async Click-Event Publisher

**Type:** Story | **Depends on:** URL-104, URL-201, URL-203

**Problem / Goal:** Publish a click event on every redirect without making the redirect
response depend on Kafka being available or fast.

**Technical implementation plan:** `KafkaTemplate.send()` from `RedirectController`
(fire-and-forget); the returned `CompletableFuture`'s `whenComplete` callback only logs on
failure — it never blocks or fails the redirect.

**Acceptance criteria:** redirect response latency is unaffected by publish success/failure;
a publish failure never surfaces to the client as an error.

> [!warning] Disclosed trade-off
> Publish failures beyond the Kafka client's own retry config are logged and dropped, not
> locally buffered — see R-6 in [[../Dashboards/05-Risk-and-Failure-Scenario-Analysis]].

**Implementation:** `service/ClickEventPublisher.java`, `api/RedirectController.java`

---

## URL-203 — Message Broker Selection & Provisioning

**Type:** Task (architecture decision — required human approval) | **Depends on:** URL-201

**Problem / Goal:** Choose and configure the event transport for async analytics.

**Technical implementation plan:** Kafka selected over SQS/RabbitMQ (ADR in
[[../Dashboards/01-Architecture-and-Design]]); topic `click-events`, 3 partitions, replication factor 1
(single-broker dev/prototype topology); local infra via `docker-compose` (`apache/kafka`
image).

**Acceptance criteria:** broker choice documented with explicit trade-offs before
implementation began (high-impact ticket, human-approved); topic auto-provisioned at startup.

**Implementation:** `config/KafkaTopicConfig.java`, `docker-compose.yml`, `application.yml`

---

## URL-204 — Analytics Consumer & Aggregation Service

**Type:** Story | **Depends on:** URL-203, URL-401

**Problem / Goal:** Turn the click-event stream into queryable aggregates without
double-counting under Kafka's at-least-once delivery guarantee.

**Technical implementation plan:** a `@KafkaListener` consumes `ClickEvent`s; a Redis
`SETNX processed:{eventId}` guard (24h TTL) makes aggregation idempotent; the handler upserts
both `analytics.click_summary` (running totals) and `analytics.click_daily_rollup`
(day-bucketed counts).

**Acceptance criteria:** a redelivered event (consumer restart or rebalance) is not
double-counted; verified live during the Phase 4 smoke test, where the consumer group
correctly rejoined and resumed from the committed offset.

**Implementation:** `service/AnalyticsConsumerService.java`

---

## URL-205 — Analytics Query API

**Type:** Story | **Depends on:** URL-204

**Problem / Goal:** Expose aggregated click data per short URL through a read path that
doesn't contend with the create/redirect hot path.

**Technical implementation plan:** `GET /api/v1/urls/{shortCode}/stats` reads directly from
`click_summary`/`click_daily_rollup` — tables separate from `url_mappings`, so analytics reads
never compete with redirect-path locks or indexes.

**Acceptance criteria:** returns `totalClicks`, `lastClickedAt`, and a daily breakdown; `404`
if the shortCode never existed at all (checked independently of whether it's ever recorded a
click).

**Implementation:** `service/AnalyticsQueryService.java`, `api/UrlController.java#getStats`,
`api/dto/StatsResponse.java`

---

## URL-206 — Dead-Letter Queue & Retry Handling

**Type:** Task | **Depends on:** URL-203, URL-204

**Problem / Goal:** Don't silently lose or endlessly block on an event that genuinely fails to
process (as distinct from a harmless duplicate, which URL-204's idempotency guard already
handles).

**Technical implementation plan:** `DefaultErrorHandler` configured with a
`FixedBackOff(1000ms, 2 retries)`; on retry exhaustion, a recoverer persists the raw event
payload and failure reason to `analytics.click_events_dlq` instead of blocking the partition.

**Acceptance criteria:** a processing exception is retried twice with backoff, then lands in
the DLQ table with its failure reason — never silently dropped, never stuck.

**Implementation:** `config/KafkaConsumerConfig.java`, `domain/ClickEventDlq.java`,
`repository/ClickEventDlqRepository.java`

---

## URL-207 — Analytics Data Retention Policy

**Type:** Task | **Depends on:** URL-204

**Problem / Goal:** Bound the long-term storage growth of analytics data.

**Technical implementation plan:** raw events are never persisted to Postgres at all — only
Kafka's own topic retention governs them; only the aggregated rollups live in the database
(ADR #10: 90-day raw/Kafka retention, 2-year rollup retention target).

**Acceptance criteria:** retention windows are documented against ADR #10.
**Known limitation:** the rollup purge job is **not yet automated** — this is an explicit,
disclosed scope boundary (see [[../00-Executive-Summary]]), not an assumption of completeness.

**Implementation:** ADR #10 in [[../Dashboards/01-Architecture-and-Design]]; no automated purge job
exists yet
