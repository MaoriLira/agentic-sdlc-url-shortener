---
tags: [jira-ticket, epic, developer-operations, runbook]
---

# Epic: URL-800 — Developer Operations & Runbooks

Dashboard: [[../Dashboards/02-Agentic-Workflow-and-Jira-Tickets]] · Related: [[../Dashboards/04-Setup-and-Run]] · [[../Runbooks/Infrastructure-Connectivity-Runbook]]

> [!info] A different kind of epic
> Not a feature, a risk fix, or a refactor — an operational documentation gap.
> [[../Dashboards/04-Setup-and-Run]] tells you how to *start* the local infrastructure; it
> never told you how to *look inside* it once it's running. One ticket (URL-801).

| Ticket | Title | Type | Depends on |
|---|---|---|---|
| URL-801 | Infrastructure connectivity & diagnostics runbook | Task | — |

---

## URL-801 — Infrastructure connectivity & diagnostics runbook

**Type:** Task (documentation) | **Depends on:** — | **Status:** Done

**Problem:** [[../Dashboards/04-Setup-and-Run]] covers `docker-compose up -d` and
`./mvnw spring-boot:run` — enough to get the app running, nothing about how to actually
connect to Postgres/Redis/Kafka directly to inspect state while debugging. Anyone who isn't
already fluent in this stack has to reverse-engineer connection details from
`docker-compose.yml` and `application.yml` by hand.

**Goal:** A single runbook covering, for each of the three infrastructure components: how to
connect (CLI and GUI), how to verify it's in the expected state, and a couple of concrete
diagnostic commands worth knowing — not a general Postgres/Redis/Kafka tutorial, specific to
*this* application's schemas, key namespaces, and topics.

**Technical implementation plan:**
1. **PostgreSQL** — `psql` via `docker-compose exec`, GUI connection parameters, a query
   against `flyway_schema_history` to verify migration state, and diagnostic queries against
   `core.url_mappings` and `analytics.click_summary`.
2. **Redis** — `redis-cli` via `docker-compose exec`, the four key namespaces this
   application actually uses (`url:*` cache entries, `lock:*` stampede locks, `ratelimit:*`
   counters, `processed:*` idempotency guards), and how to observe cache hit/miss behavior
   live (`MONITOR`, cross-referenced against the `DEBUG`-level logs added in URL-601).
3. **Kafka** — topic listing/description via the broker's own bundled CLI scripts inside the
   container, and a live consumer to watch `click-events` messages flow in as redirects
   happen.
4. Every command in the runbook is **run against a real local stack before being written
   down** — not written from documentation/memory — so the runbook doesn't silently drift
   from the actual `docker-compose.yml` (image versions, container names, script paths all
   verified live).

**Acceptance criteria:**
- A reader with no prior familiarity with this specific stack can connect to all three
  services and run at least one meaningful diagnostic command against each, copy-pasting
  directly from the runbook.
- Every command shown was actually executed against this project's `docker-compose.yml`
  during authoring, not assumed.

**Implementation:** `docs/Runbooks/Infrastructure-Connectivity-Runbook.md`
