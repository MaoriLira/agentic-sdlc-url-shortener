---
tags: [setup, testing, docker-compose, testcontainers]
---

# Setup & Run

Related: [[../00-Executive-Summary]] · [[01-Architecture-and-Design]] · [[../Scenarios/B-Brownfield-Refactoring]]

Source lives in `project/`. See [[01-Architecture-and-Design]] for what each piece does.

## Prerequisites

- Java 21 (only needed to *run* the wrapper's bootstrap — the wrapper downloads its own
  pinned Maven, it doesn't use whatever `mvn` is already installed)
- Docker (Postgres, Redis, Kafka run as containers)

> [!info] Use `./mvnw`, not `mvn`
> This project builds through the **Maven Wrapper** (`project/mvnw` / `mvnw.cmd`), pinned to
> Maven 3.9.10. It's the standard for this repo specifically because a locally-installed `mvn`
> is not guaranteed to resolve the same JDK the wrapper does — see the JAVA_HOME note below for
> the exact failure this caused before the wrapper existed. Every command on this page uses
> `./mvnw`.

## Run it

`docker-compose.yml` lives at the **repository root** (one level up from `project/`), not
inside `project/` — it's the single source of truth for local infra, shared by anything that
needs it.

```bash
# from the repository root
docker-compose up -d          # Postgres (5435→5432), Redis (6379), Kafka (9092)

# from project/
cd project
DB_PORT=5435 ./mvnw spring-boot:run
```

The app starts on `http://localhost:8080`. Flyway runs migrations automatically on startup
(`db/migration/V1`–`V5`), including a seed demo API client.

**Demo API key:** `demo-key-12345` (send as header `X-API-Key`)

> [!note] Why `DB_PORT=5435`
> Postgres is mapped to host port **5435**, not the default 5432, to avoid clashing with
> other local Postgres instances. The app isn't part of the `docker-compose` network when run
> via `./mvnw spring-boot:run`, so it needs to be told the host-mapped port explicitly.

## Try it

```bash
# Create a short URL
curl -X POST http://localhost:8080/api/v1/urls \
  -H "Content-Type: application/json" -H "X-API-Key: demo-key-12345" \
  -d '{"longUrl":"https://github.com/anthropics/claude-code"}'
# => {"shortCode":"1000000","shortUrl":"http://localhost:8080/1000000", ...}

# Follow the redirect (302)
curl -i http://localhost:8080/1000000

# Read metadata / analytics (analytics lag a few seconds — async via Kafka)
curl http://localhost:8080/api/v1/urls/1000000 -H "X-API-Key: demo-key-12345"
curl http://localhost:8080/api/v1/urls/1000000/stats -H "X-API-Key: demo-key-12345"

# Delete
curl -X DELETE http://localhost:8080/api/v1/urls/1000000 -H "X-API-Key: demo-key-12345"
```

Full API contract: OpenAPI spec from the Phase 3 design (matches this implementation exactly).

## Automated test suite

```bash
cd project
./mvnw verify
```

**30 tests**, all passing:

| Suite | Type | What it covers |
|---|---|---|
| `Base62EncoderTest` | Pure unit | Encoding correctness, uniqueness, edge cases |
| `UrlValidationServiceTest` | Pure unit | Internal-target/open-redirect and scheme/length rules, alias rules |
| `UrlCacheServiceTest` | Mockito fault injection | Cache-aside resilience — every Redis call site degrades gracefully instead of throwing (URL-502) |
| `RateLimiterServiceTest` | Testcontainers (Redis) | Fixed-window limiting, per-identifier isolation |
| `UrlShortenerIntegrationTest` | Testcontainers (Postgres+Redis+Kafka) | Full create → redirect → async-stats → delete lifecycle, deleted-alias reuse (URL-501), and 400/401/404/409/410 error paths |

> [!tip] Colima / non-default Docker socket
> If Docker is reachable through a non-default socket (e.g. Colima instead of Docker
> Desktop), export `DOCKER_HOST` first — Testcontainers won't auto-detect it otherwise:
> ```bash
> export DOCKER_HOST=unix:///path/to/colima/docker.sock
> ```
> Ryuk (Testcontainers' cleanup sidecar) can fail to start under Colima with a socket
> mount error. If you hit that, set `TESTCONTAINERS_RYUK_DISABLED=true` — containers are
> still stopped at the end of each test class via the `@Testcontainers` JUnit extension, so
> this doesn't leak resources within a normal run.

> [!warning] Why the wrapper exists: JAVA_HOME / wrong-JDK failures
> Before the Maven Wrapper was added, running the bare `mvn` installed on this machine failed
> `UrlCacheServiceTest` with `Mockito cannot mock this class` — a ByteBuddy/JDK-version
> incompatibility, because that machine's `mvn` wrapper script hardcoded `JAVA_HOME` to a
> newer JDK (24) than this project targets (21). `./mvnw` sidesteps this: it resolves its own
> JDK independently of any host `mvn` script, and was verified to pick up JDK 21 correctly
> with no `JAVA_HOME` override needed. If you ever see this error again, it means something
> in your environment is overriding `JAVA_HOME` to a non-21 JDK — unset it, or point it at a
> JDK 21 install.

## Local infra teardown

```bash
# from the repository root
docker-compose down
```

## Known limitations (prototype scope)

See [[../00-Executive-Summary]] for the full list — expiry-reaper job, producer-failure
buffering, top-referrer accuracy, single-region scope, and unpopulated geo-analytics.
