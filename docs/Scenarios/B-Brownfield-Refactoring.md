---
tags: [brownfield, scenario-b, agentic-workflow, ticket-execution]
---

# Scenario B: Brownfield Refactoring & Bug Fixes

Related: [[../00-Executive-Summary]] · [[../Dashboards/02-Agentic-Workflow-and-Jira-Tickets]] · [[../Jira-Tickets/Epic-URL-500-Brownfield-Remediation]] · [[../Dashboards/05-Risk-and-Failure-Scenario-Analysis]] · [[../Dashboards/03-Business-Rules-and-Guardrails]]

This document demonstrates the **Brownfield** scenario required alongside Greenfield and
Ambiguous-requirements handling: an enhancement to an already-shipped, already-tested system,
triggered by defects the system itself surfaced under analysis rather than by a new feature
request.

> [!info] Where this sits among the three required scenarios
> **Greenfield** is the URL shortener build itself ([[../00-Executive-Summary]] through
> Phase 4). **Ambiguous requirements** was handled earlier and differently — the 13 ADRs in
> [[../Dashboards/01-Architecture-and-Design]] resolved genuine requirement gaps *before* code existed.
> **Brownfield** (this document) is different again: the requirements were never ambiguous,
> the code already worked and was fully tested — [[../Dashboards/05-Risk-and-Failure-Scenario-Analysis]]
> found real defects in it anyway, and this is the fix.

The critical thing this scenario is meant to exercise isn't the code change itself — it's
**scope discipline**: five follow-ups were prioritized in the risk analysis; only two were
authorized. What follows deliberately separates *how that scoping decision got made* from
*what got built*, because conflating them is exactly what makes brownfield work hard to audit
in retrospect.

---

## Sub-process 1: The Agentic Workflow (governance layer)

This is the meta-process — how the decision to build exactly these two fixes, and no others,
actually happened. No code lives in this layer; it's reasoning and approvals.

1. **Codebase analysis, not a fresh spec.** [[../Dashboards/05-Risk-and-Failure-Scenario-Analysis]] was
   produced by re-reading already-shipped, already-tested code path-by-path under failure —
   "what happens when Redis is down" — rather than from a requirements document. This is the
   defining trait of brownfield work: the input is the system as it exists, not a blank page.
2. **Nine risks surfaced; five were prioritized as actionable follow-ups**, ranked by a
   value/effort read (R-3's fix is a one-migration change; R-8's observability gap is the
   highest-leverage but broadest). The agent proposed this prioritization and *waited* rather
   than proceeding — the analysis document ended on an explicit open question, not an
   in-progress implementation.
3. **Human governance selected a subset.** The architect's instruction was explicit:
   *"We will NOT implement all 5. We will implement a targeted, high-value subset"* — R-3 and
   R-1 (scoped to cache only). This is the human-approval checkpoint the orchestration model
   in [[../Dashboards/01-Architecture-and-Design]] describes for high-impact decisions, exercised in
   practice rather than just designed on paper.
4. **The scope boundary was carried into the work, not just the decision.** R-1's original
   finding named three Redis-dependent components (cache, rate limiter, idempotency guard).
   The ticket that resulted — URL-502 — covers exactly one. That boundary is documented in
   three places by design: the ticket itself
   ([[../Jira-Tickets/Epic-URL-500-Brownfield-Remediation#URL-502 — Redis graceful degradation for cache-aside|URL-502]]),
   the code's own doc comment (`UrlCacheService.java`), and this document — so a future reader
   hitting the rate-limiter gap doesn't mistake it for a regression.
5. **Halt for review is the default, not an exception.** As with every prior phase, this work
   stops here for explicit approval before being considered done — consistent with every
   phase gate since Phase 1, not a one-off for brownfield work specifically.

> [!success] What this demonstrates
> Controlled autonomy isn't "the agent didn't do anything without asking" — it's that the
> agent *did* propose a full prioritized list (five items), and the human's job was to narrow
> it, not to generate it from scratch. The narrowing is the governance action; producing the
> options was the agent's.

---

## Sub-process 2: Jira Tickets & Implementation (engineering layer)

This is the actual work product — code, migrations, and tests, tracked as tickets under the
Brownfield epic **URL-500**. Full ticket text (problem/goal/technical plan) is in
[[../Jira-Tickets/Epic-URL-500-Brownfield-Remediation|URL-500]]; this section covers *how*
each was actually built.

### URL-501 — Alias unique constraint → partial index

| | |
|---|---|
| **Root cause** | `core.url_mappings.short_code` had a table-wide `UNIQUE` constraint; soft-delete (`status='DELETED'`) never freed the value |
| **Fix** | `V5__alias_reuse_partial_unique_index.sql` — drop the constraint, replace with `CREATE UNIQUE INDEX ... WHERE status = 'ACTIVE'`, consolidated with the pre-existing lookup index of the same shape |
| **Entity change** | Removed the now-inaccurate `unique = true` hint from `UrlMapping.shortCode` — JPA can't express a partial constraint, so the annotation was actively misleading |
| **Verification** | New integration test `deletedCustomAlias_canBeReused` (Testcontainers Postgres): create alias → delete → re-create under the same alias → `201`, not `409` |

The constraint's real name (`url_mappings_short_code_key`) was confirmed against a live
Postgres instance before writing the `DROP CONSTRAINT` statement, rather than assumed from
Postgres's naming convention — a brownfield-specific discipline: you're altering something
that already exists and already has real (if synthetic) data shape, so verify before you
drop.

### URL-502 — Redis graceful degradation for cache-aside

| | |
|---|---|
| **Root cause** | Every Redis call in `UrlCacheService` (`get`, lock `setIfAbsent`, `put`, lock release, `evict`) was unguarded; a connection failure became an uncaught `DataAccessException` → `500` |
| **Fix** | Each call site now catches `org.springframework.dao.DataAccessException`, logs at `ERROR`, and falls through to the DB loader (or no-ops, for evict/put) instead of propagating |
| **Verification** | New `UrlCacheServiceTest` (6 cases): Mockito-based fault injection — `StringRedisTemplate` throwing on every call — proves `get`/`evict`/`getOrLoad` never throw and `getOrLoad` still resolves the correct value from the supplied DB loader; a sixth case confirms the healthy-Redis path (caching still happens) is unchanged |

> [!warning] The scope boundary shows up as a real test-design decision
> A tempting way to "prove the fix end-to-end" would be an HTTP-level test with the Redis
> *container* stopped entirely. That test would actually **fail** — not because URL-502 is
> broken, but because `RateLimiterService.checkLimit()` (untouched, out of scope) throws
> before the request ever reaches the now-resilient cache code. Writing that test would have
> meant either accepting a confusing failure or quietly disabling rate limiting in the test
> config to make it pass — the latter would have hidden exactly the boundary this document is
> trying to make explicit. Mockito-level fault injection on `UrlCacheService` alone was chosen
> instead: it tests precisely the code that changed, without silently absorbing an
> out-of-scope gap into the test's setup.

### Incidental finding during verification

Running the updated suite (`mvn test`) initially failed six new tests with
`Mockito cannot mock this class: StringRedisTemplate`. Root cause: this machine's Homebrew
`mvn` wrapper hardcodes `JAVA_HOME` to a Homebrew-installed JDK 24 unless overridden, while the
project targets Java 21 (`pom.xml`) — Mockito's bundled ByteBuddy version doesn't yet support
JDK 24's class file version. Fixed by exporting `JAVA_HOME` to the SDKMAN-managed JDK 21
before running Maven; not a code defect, but recorded here because "the test tooling was
silently running the wrong JDK" is itself exactly the kind of brownfield surprise this
scenario is meant to catch.

### Test suite growth

24 tests → **30 tests**, all passing (verified via `mvn test` against Postgres, Redis, and
Kafka Testcontainers). See [[../Dashboards/04-Setup-and-Run]] for how to run them.

---

## Contrasting the two layers

| | Sub-process 1: Agentic Workflow | Sub-process 2: Ticket Execution |
|---|---|---|
| **Nature** | Conversational, governance-driven | Code, migrations, tests |
| **Primary artifact** | This document + [[../Dashboards/05-Risk-and-Failure-Scenario-Analysis]] + the architect's scoping instruction | `V5__*.sql`, `UrlCacheService.java`, `UrlCacheServiceTest.java`, integration test additions |
| **Who decides scope** | The human architect (narrowed 5 candidates to 2) | N/A — the agent executes within the scope already approved |
| **Traceability unit** | Risk ID (`R-3`, `R-1`) → decision | Ticket ID (`URL-501`, `URL-502`) → file/migration |
| **Halts for approval** | Yes — before this work is considered done | No — implementation proceeds once a ticket is in scope |
| **Failure mode if skipped** | Scope creep (the exact thing this exercise tests for) | Untested or undocumented code change |

> [!question] Awaiting review
> Say **"Approved"** to accept this scenario as complete, or flag anything — including
> whether the rate-limiter gap called out in URL-502 should become its own follow-up ticket.
