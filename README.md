# Agentic SDLC URL Shortener

**Version 1.1.0** — see [`CHANGELOG.md`](CHANGELOG.md) for release history.

An enterprise-grade URL shortener — core APIs, Redis cache-aside, async Kafka analytics,
PostgreSQL persistence — built end-to-end using an **Agentic Execution Model with
human-in-the-loop governance**. Every architectural decision, ticket, guardrail, risk finding,
and brownfield fix in this repository was proposed by an AI agent and required explicit human
approval before being built — not generated once and left unreviewed.

## Start here

This repository's real front door is not this file — it's the knowledge graph:

> **Open `docs/` as an Obsidian vault, then open [`docs/00-Executive-Summary.md`](docs/00-Executive-Summary.md).**

That page is the entry point into the full architectural knowledge graph: system design and
diagrams, every Architecture Decision Record, every guardrail, the complete ticket
decomposition, the risk and failure-scenario analysis, and the brownfield remediation case
study — all cross-linked, all audited against the actual source in `project/`.

## Repository layout

| Path | What it is |
|---|---|
| [`docs/`](docs/00-Executive-Summary.md) | The knowledge graph (Obsidian vault). Start at `00-Executive-Summary.md`. |
| `project/` | The Spring Boot application source, tests, Flyway migrations, and the Maven Wrapper. |
| `docker-compose.yml` | Local runtime infrastructure (Postgres, Redis, Kafka) — single source of truth, run from here. |
| [`CHANGELOG.md`](CHANGELOG.md) | Release history, following Keep a Changelog + SemVer. |
| [`PROMPT_HISTORY.md`](PROMPT_HISTORY.md) | "Prompt-as-Code" — the steering prompt behind every phase of this build, for reproducibility. |
| `Documentation/` | The original assessment requirements this project was built against. |
| `_insomnia_collection/` | An Insomnia collection exercising the full API lifecycle end to end. |

## The Maven Wrapper is the standard

This project builds and runs through **`./mvnw`** (`project/mvnw`), not a locally-installed
`mvn`. The wrapper pins the exact Maven version this project was built and verified against
(3.9.10), so a build behaves the same on any machine regardless of what Maven — if any — is
already installed. Every command below uses it; don't substitute a bare `mvn`.

## Quick Start & Developer Guide

All commands below run from `project/` unless noted otherwise. Do these three steps in order.

### 1. Run the Automated Test Suite

```bash
cd project
./mvnw clean install
```

> [!IMPORTANT]
> Requires a running Docker daemon. This runs the full 38-test suite (unit, Mockito
> fault-injection, and Testcontainers integration tests) — Testcontainers starts and stops
> its own Postgres/Redis/Kafka containers automatically, independent of the `docker-compose`
> stack below. If this passes, the build is verified healthy end to end.

### 2. Start Local Infrastructure

```bash
# from the repository root (not project/)
docker-compose up -d
```

Brings up long-lived Postgres, Redis, and Kafka containers with the credentials, database, and
ports `application.yml` expects by default. This is separate from step 1's throwaway
Testcontainers instances — this is the infrastructure the *live app* connects to.

### 3. Boot the Live Application

```bash
cd project
DB_PORT=5435 ./mvnw spring-boot:run
```

`spring-boot:run` does not re-run the test suite — this just boots the app against the
infrastructure started in step 2. `DB_PORT=5435` is required because Postgres is published on
host port 5435, not the default 5432 (avoids clashing with other local Postgres instances).

The app is live at `http://localhost:8080` once you see `Started UrlShortenerApplication` in
the log. Demo API key: `demo-key-12345` (header `X-API-Key`). Try it with the Insomnia
collection in [`_insomnia_collection/`](_insomnia_collection/insomnia_collection.json), or see
[`docs/Dashboards/04-Setup-and-Run.md`](docs/Dashboards/04-Setup-and-Run.md) for `curl` examples
and Colima/non-default-Docker-socket notes.

---

*This project is a work in progress under active review — do not treat it as finalized.*
