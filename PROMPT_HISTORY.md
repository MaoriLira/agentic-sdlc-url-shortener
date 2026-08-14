# 🤖 Prompt Engineering History & Architecture Decisions

This document tracks the key "Steering Prompts" used to guide the AI agent during the
development of the URL Shortener project. It serves as "Prompt-as-Code" to ensure
reproducibility and document architectural decisions.

> [!NOTE] On accuracy
> Where **The Prompt** is marked *(paraphrased)*, it's a faithful compression of a longer
> numbered instruction, not a verbatim transcript. An earlier draft of this document's Phase 6
> entry stated the Redis fallback "logs a warning" and quoted a generic prompt that didn't
> match what was actually sent — neither was true. See the note at the bottom of this file.

---

## Phase 1: Requirement Understanding & Task Decomposition

**Context:** Starting point — a master prompt (`00-master-prompt.md`) establishing phased
execution with human-approval gates, and a raw assessment requirements document to interpret.
**Target File(s):** None (planning only — the master prompt explicitly forbade code during
this phase).

**The Prompt:**
> "read this file 00-master-prompt.md" → then, after the phase's scope was described back:
> "Yes, go ahead"

The master prompt itself specified the actual task: interpret `schwab-requirements.md`,
decompose it into simulated JIRA tickets, and surface ambiguities before approval.

**Result / Architectural Note:** URL-101 through URL-407 (26 tickets across 4 epics), 13
requirement ambiguities identified for human resolution rather than silently assumed.

---

## Phase 2: Architecture & Orchestration Design

**Context:** The 13 ambiguities from Phase 1 needed resolution before any schema or API
contract could be fixed.
**Target File(s):** None (design only).

**The Prompt:**
> "Approved"

A single-word gate confirming the Phase 1 ticket graph, per the master prompt's
phased-execution rule: nothing advances without explicit approval.

**Result / Architectural Note:** 13 ADRs (short-code strategy, redirect status code, rate
limits, retention, etc.), system architecture, and the ticket-level orchestration model
(entry/exit gates, human-approval checkpoints for high-impact tickets).

---

## Phase 3: API Contract & Database Schema

**Context:** Architecture decided; needed a concrete, reviewable contract before writing any
implementation code.
**Target File(s):** None (design artifacts only — OpenAPI spec and Flyway DDL as text).

**The Prompt:**
> "Approved"

**Result / Architectural Note:** Full OpenAPI 3.1 specification and `V1`/`V2` Flyway migration
DDL, cross-referenced to the Phase 2 ADRs.

---

## Phase 4: Implementation

**Context:** Design approved; time to build the actual Spring Boot application.
**Target File(s):** Full `project/` tree — 40+ Java files, Flyway migrations,
`docker-compose.yml`, tests.

**The Prompt:**
> "Approved"

**Result / Architectural Note:** Working Spring Boot 3.3 / Java 21 application implementing
all 26 Greenfield tickets; 23 tests passing; live-smoke-tested end to end (create → redirect →
async analytics → delete) against real Postgres/Redis/Kafka via `docker-compose`, not just
Testcontainers.

---

## Phase 5a: Documentation Meta-Artifact (Obsidian Knowledge Graph)

**Context:** Before adding more test coverage or risk analysis, the user required a
"Final Engineering Summary"-grade documentation layer — a reviewable, cross-linked knowledge
graph rather than scattered prose.
**Target File(s):** `docs/00-Executive-Summary.md` through `docs/04-Setup-and-Run.md` (5 new
files).

**The Prompt** *(paraphrased)*:
> "Approved for Phase 5, but with a strict architectural condition regarding documentation.
> Before we add any more test coverage or risk analysis in code, we must construct the
> 'Meta-Artifact'... generate our documentation as an Obsidian Knowledge Graph inside a new
> `docs/` directory. Use Obsidian features like Mermaid.js for diagrams, Callouts, and
> bidirectional links... Generate these 5 files now. Once created, halt and wait for
> 'Approved' to continue with the risk/failure-scenario analysis part of Phase 5."

**Result / Architectural Note:** 5 cross-linked Obsidian notes with Mermaid sequence/flowchart
diagrams, callouts, and an ADR table.

---

## Phase 5b: Risk & Failure-Scenario Analysis

**Context:** Documentation scaffold approved; now the actual validation/risk work the phase
was named for.
**Target File(s):** `docs/05-Risk-and-Failure-Scenario-Analysis.md`.

**The Prompt:**
> "Approved"

**Result / Architectural Note:** 9 risks (R-1 through R-9) found by reading the shipped code
under failure conditions — including two genuinely new findings not previously documented
anywhere: Redis as a hidden triple point of failure (R-1), and the "SSRF protection" naming
being architecturally imprecise for a service with no server-side fetch (R-5).

---

## Phase 6: Targeted Brownfield Refactoring & Workflow Documentation

**Context:** Demonstrating the required "Brownfield" scenario and scope-management discipline
— the user deliberately authorized only 2 of the 5 prioritized risk fixes, not all of them.
**Target File(s):** `UrlCacheService.java`, `UrlCacheServiceTest.java`,
`V5__alias_reuse_partial_unique_index.sql`, `UrlMapping.java`,
`UrlShortenerIntegrationTest.java`, `docs/Scenarios/B-Brownfield-Refactoring.md`.

**The Prompt:**
> "Outstanding analysis. The insight on R-5 (Open Redirect vs SSRF) is exactly the level of
> architectural scrutiny I expect. To demonstrate Scenario B (Brownfield Refactor & Bug
> Fixes), and exercise our scope management, we will NOT implement all 5. We will implement a
> targeted, high-value subset. Task (Phase 6): 1. Generate Jira Tickets for R-3 (Alias Unique
> Constraint) and R-1 (Redis Graceful Degradation for Cache)... 2. Execute R-3: Create a new
> Flyway migration... to safely alter the unique constraint on custom aliases so that deleted
> aliases can be reused... 3. Execute R-1 (Graceful Degradation): Refactor the Redis
> cache-aside logic. Implement a fallback mechanism... If Redis is down or times out, the
> application MUST NOT return a 500 error; it must log an error and fall back to querying
> PostgreSQL directly. 4. Document the Dual-Process (Agentic vs. Implementation)... 5. Review
> and Update..."

**Result / Architectural Note:** URL-501 (partial unique index, alias reuse after delete) and
URL-502 (Redis graceful degradation, scoped to the cache-aside path only — the rate limiter
and Kafka idempotency guard were deliberately left unguarded and documented as such, not
silently fixed). The fallback logs at **`ERROR`**, not warning, exactly as instructed above.

---

## Phase 7: Documentation Atomic Extraction & Code-Alignment Audit

**Context:** The ticket dashboard had become an unreadable wall of 30 fully-detailed tickets;
needed genuine "atomic notes" per Obsidian convention, plus a real verification pass that
documentation claims matched shipped code.
**Target File(s):** `docs/Jira-Tickets/*.md` (5), `docs/Architecture-Decisions/*.md` (12),
`docs/Guardrails/*.md` (5), `docs/Risks/*.md` (9), `docs/00`–`05` (updated).

**The Prompt** *(paraphrased, two consecutive instructions)*:
> 1. "I noticed you took a shortcut in docs/02... detailed descriptions for the URL-500 Epic,
>    but only summary tables for Epics 100-400. Task (Documentation Refactor - Jira
>    Simulation): Create `docs/Jira-Tickets/`, generate detailed files for Epics
>    100/200/300/400, refactor the dashboard to link out, extract URL-500 too."
> 2. "The extraction... is perfect. However, you failed to slim down the root files (00-05)...
>    Task (Phase 7: Documentation Refactoring & Code Alignment Audit): 1. Root Files as
>    Dashboards (MOCs)... 2. Atomic Extraction: extract deep technical details (ADRs, Kafka
>    config, security rules) into new subfolders like `docs/Architecture-Decisions/` or
>    `docs/Guardrails/`... 3. The Code-to-Docs Alignment Audit: read the actual `.java` files,
>    `application.yml`, and Flyway scripts... verify class/method names, migration versions,
>    and Mermaid diagrams match the final implemented flow. 4. Fix Inconsistencies."

**Result / Architectural Note:** Two real, previously-undocumented discrepancies were found
and fixed: a Mermaid diagram label still said "SSRF guard" (inconsistent with the R-5
correction), and `urlshortener.retention.daily-rollup-days` in `application.yml` was dead
configuration that nothing actually read.

---

## Phase 7 (follow-up): Aggressive MOC Trim

**Context:** The first MOC pass still left explanatory paragraphs and multi-line callouts in
the root files; the user wanted them mechanically reduced to scannable link-lists.
**Target File(s):** `docs/00-Executive-Summary.md`, `01-Architecture-and-Design.md`,
`03-Business-Rules-and-Guardrails.md`, `05-Risk-and-Failure-Scenario-Analysis.md` (still at
`docs/` root at this point — the `Dashboards/` folder didn't exist yet).

**The Prompt:**
> "You MUST aggressively DELETE the dense paragraphs inside the root files (00 to 05) and
> replace them with clean, scannable Dashboards. Execute these exact transformations: [01:
> keep diagrams, bulleted ADR links; 03: short summary + bulleted Guardrail links; 05: keep
> quadrant chart/table, bulleted Risk links; 00: ultimate landing page]. Do not duplicate
> content."

**Result / Architectural Note:** Line counts dropped sharply (e.g.
`03-Business-Rules-and-Guardrails.md`: 41 → 18 lines) while every fact removed remained one
click away in the atomic notes.

---

## Phase 7.1: File Hierarchy Strict Refactoring

**Context:** `01`–`05` still sat loose at `docs/` root alongside the new atomic-note
subfolders, which read as visually unprofessional.
**Target File(s):** `docs/Dashboards/` (new), all 33 files containing cross-links to `01`–`05`.

**The Prompt:**
> "Having files 01 through 05 floating at the root level alongside subdirectories looks
> cluttered and unprofessional. Task (Phase 7.1): 1. Create `docs/Dashboards/`. 2. Move
> 01-05 into it. 3. `00-Executive-Summary.md` MUST remain the only file at `docs/` root.
> 4. Fix all bidirectional links across the entire knowledge graph so they point to the new
> paths without breaking."

**Result / Architectural Note:** 264 wikilinks rewritten across 33 files, verified two ways —
Obsidian's basename resolution and a strict literal-relative-path check — both clean.

---

## Phase 7.2: Root README & Insomnia Collection

**Context:** The knowledge graph was complete but the repository itself had no front door, and
there was no way to exercise the API without hand-writing `curl` commands.
**Target File(s):** `README.md` (new, repo root), `_insomnia_collection/insomnia_collection.json`
(new).

**The Prompt** *(paraphrased)*:
> "Generate the README.md at the root... direct the reviewer immediately to open the `docs/`
> folder as an Obsidian Vault... Generate a fully configured Insomnia collection (V4 export
> format)... Request 1 (Create) must automatically extract the generated `shortCode`...
> Request 4 (Cleanup/Delete) must ensure the database is left clean after running the folder."

**Result / Architectural Note:** A 4-request self-cleaning Insomnia collection using an
`afterResponseScript` to chain `shortCode` between requests, and a README pointing reviewers
at the knowledge graph as the real entry point.

---

## Phase 7.4: Developer Experience & Local Runtime Environment

**Context:** `spring-boot:run` failed with a Flyway connection error because local
infrastructure wasn't running, and the project had no Maven Wrapper — a reproducibility gap.
**Target File(s):** `project/mvnw`, `project/mvnw.cmd`, `project/.mvn/wrapper/`,
`docker-compose.yml` (moved to repo root).

**The Prompt** *(paraphrased)*:
> "Execute `mvn wrapper:wrapper`... Create a complete `docker-compose.yml` in the root
> directory... matching the exact credentials, databases, and ports expected by
> `application.yml`... Update README and `docs/04-Setup-and-Run.md` to state `./mvnw` is the
> standard..."

**Result / Architectural Note:** `./mvnw` verified to resolve JDK 21 correctly with no
environment override needed — unlike this machine's Homebrew-installed `mvn`, whose own
wrapper script had been silently hardcoding JDK 24, the actual root cause of earlier Mockito
test failures.

---

## Phase 8: Final Polish & Release 1.0.0

**Context:** Application verified running locally by the human reviewer via both terminal and
IDE; time to formalize versioning for a "production-ready" repository.
**Target File(s):** `README.md`, `CHANGELOG.md` (new), `project/pom.xml`,
`_insomnia_collection/insomnia_collection.json`.

**The Prompt** *(paraphrased)*:
> "Update README.md to reflect Enterprise DX standard... exactly these 3 steps: 1. Run the
> Automated Test Suite: `./mvnw clean install`... 2. Start Local Infrastructure:
> `docker-compose up -d`... 3. Boot the Live Application: `DB_PORT=5435 ./mvnw
> spring-boot:run`... Implement enterprise SemVer. Create a CHANGELOG.md... Add an entry for
> `## [1.0.0] - 2026-08-13`... update pom.xml version to 1.0.0... Ensure the Insomnia
> workspace description explicitly mentions `DB_PORT=5435`..."

**Result / Architectural Note:** `project/pom.xml` → `1.0.0`, confirmed by the actual built
artifact (`url-shortener-1.0.0.jar`) after running the exact `./mvnw clean install` command
from the new README, verified against a live `docker-compose up -d` + `./mvnw
spring-boot:run` + smoke-test cycle before tearing everything back down.

---

## A note on this document's own accuracy

This file's Phase 6 entry originally (in an earlier draft) said the Redis fallback "logs a
warning" and quoted a generic "Act as a Senior Software Engineer" prompt that didn't match
what was actually sent. Neither was true: the shipped code uses `log.error(...)`, and the real
instruction was scoped specifically to Scenario B / ticket URL-502, with an explicit
constraint that the fix should **not** extend to the rate limiter or Kafka idempotency guard.
Corrected above, per explicit review request, rather than left as a more flattering but
inaccurate account.
