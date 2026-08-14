# CLAUDE.md

Persistent context for working in this repository. Read this before making changes.

## Tech Stack & Environment

- **Language/Runtime:** Java 21, managed strictly via SDKMAN! (`sdk use java 21.x-...`). Do not
  rely on a Homebrew-installed `mvn`/JDK — it will not resolve JDK 21 reliably.
- **Build:** Maven Wrapper only — `./mvnw`, run from `project/`. Never invoke a bare `mvn`.
- **Framework:** Spring Boot 3.3.4.
- **Datastores/Infra:** PostgreSQL (Flyway-managed schema, `db/migration/V1`–`V5`), Redis
  (cache-aside + rate limiting + Kafka idempotency guard), Kafka (async click analytics).
- **Local containers:** Docker via Colima (not Docker Desktop).

## Colima / Testcontainers Socket Rule

Docker is reachable through Colima's non-default socket, not the default Docker Desktop one.
Testcontainers will not auto-detect it — export this before running any test/build that spins
up containers:

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
```

If Ryuk (Testcontainers' cleanup sidecar) fails to start with a socket-mount error under
Colima, also set:

```bash
export TESTCONTAINERS_RYUK_DISABLED=true
```

Containers still stop cleanly at the end of each test class via the `@Testcontainers` JUnit
extension, so this doesn't leak resources in a normal run.

## Documentation Standards

- Primary entrypoint: [`docs/00-Executive-Summary.md`](docs/00-Executive-Summary.md) — the only
  file allowed at the `docs/` root. Everything else lives in a per-type subfolder:
  `Architecture-Decisions/`, `Guardrails/`, `Jira-Tickets/`, `Risks/`, `Scenarios/`,
  `Dashboards/`, `Runbooks/`.
- Runbooks must reside under `docs/Runbooks/` — no loose runbook files anywhere else.
- Docs are an Obsidian knowledge graph — use `[[wikilinks]]` between notes, and verify link
  targets exist before committing (broken links are a build-quality issue, not a nitpick).
- Versioning: Semantic Versioning tracked in `project/pom.xml`, with every version bump
  documented in `CHANGELOG.md` (Keep a Changelog format).

## Branching & Workflow

- Every change traces to a Jira-style ticket under `docs/Jira-Tickets/`.
- Branch naming:
  - Code changes: `feature/{TICKET}-short-description`
  - Docs-only changes: `docs/{TICKET}-short-description`
- Commits follow Conventional Commits (`feat:`, `fix:`, `docs:`, `chore:`, etc.), referencing
  the ticket ID.
- Standard flow: ticket committed to `master` first → feature/docs branch cut → PR back to
  `master` (the repository's actual default branch) → merge after review.
- `master` is the default branch — there is no `main` branch in this repository.

## Common Commands

```bash
# Run the full test suite (from project/)
./mvnw clean install

# Start local infra — from the repository root (docker-compose.yml lives there, not in project/)
docker-compose up -d

# Run the app locally (from project/) — Postgres is host-mapped to 5435, not 5432
DB_PORT=5435 ./mvnw spring-boot:run
```

App runs at `http://localhost:8080`; demo API key `demo-key-12345` (header `X-API-Key`).
