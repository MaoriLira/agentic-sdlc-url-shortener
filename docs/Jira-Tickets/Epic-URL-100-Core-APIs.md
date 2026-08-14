---
tags: [jira-ticket, epic, greenfield, core-api]
---

# Epic: URL-100 — Core URL Shortening APIs

Dashboard: [[../Dashboards/02-Agentic-Workflow-and-Jira-Tickets]] · Related: [[../Dashboards/01-Architecture-and-Design]] · [[../Dashboards/03-Business-Rules-and-Guardrails]]

Scope: the create/redirect/metadata/delete surface, short-code generation, input validation,
and rate limiting — everything a client interacts with directly. **Status: Done (Phase 4).**

---

## URL-101 — API Contract & Domain Model Definition

**Type:** Story | **Depends on:** —

**Problem / Goal:** Before any endpoint could be built, the resource model and contract shape
needed to be fixed so every downstream ticket (102–109, 201, 401) had a stable target instead
of each inventing its own shape.

**Technical implementation plan:** Define the `ShortUrl` resource (shortCode, longUrl,
createdAt, expiresAt, owner, status); endpoint contracts for
`POST /api/v1/urls`, `GET /api/v1/urls/{shortCode}`, `DELETE /api/v1/urls/{shortCode}`,
`GET /{shortCode}`; standardize errors on RFC 7807 (`application/problem+json`).

**Acceptance criteria:** OpenAPI-shaped contract drafted before code; error response format
consistent across all endpoints.

**Implementation:** `api/dto/CreateUrlRequest.java`, `api/dto/UrlResponse.java`,
`api/dto/ProblemResponse.java`, `api/GlobalExceptionHandler.java`

---

## URL-102 — Short Code Generation Strategy (Base62)

**Type:** Story (architecture decision — required human approval) | **Depends on:** URL-101

**Problem / Goal:** Need a code-generation strategy that's collision-free under concurrent
writes and bounded in length, decided deliberately rather than defaulted to "random string."

**Technical implementation plan:** A PostgreSQL sequence (`core.short_code_seq`) started at
`62^6` so its Base62 encoding is exactly 7 characters from the first value onward; encode via
a small `Base62Encoder` utility. See ADR #1/#2 in [[../Dashboards/01-Architecture-and-Design]].

**Acceptance criteria:** codes are 7 characters at the current volume; collision probability
for system-generated codes is zero by construction, not "very low" — no retry logic needed on
the hot create path.

**Implementation:** `service/ShortCodeGenerator.java`, `service/Base62Encoder.java`,
`db/migration/V3__short_code_sequence.sql`

---

## URL-103 — Create Short URL Endpoint

**Type:** Story | **Depends on:** URL-101, URL-102, URL-402, URL-403

**Problem / Goal:** Expose the write path: accept a long URL (plus optional custom alias and
expiry), and return the created short URL's metadata.

**Technical implementation plan:** `UrlController.create()` authenticates the API key, checks
the rate limit, then delegates to `UrlShortenerService.create()`, which validates the URL,
resolves a short code (generated or custom), persists, and returns `201`.

**Acceptance criteria:** returns `201` with shortCode/shortUrl/longUrl/createdAt/expiresAt/
status; rejects unsafe URLs (delegates to URL-107); each call mints a new code even for a
duplicate long URL (ADR #4 — no dedup).

**Implementation:** `api/UrlController.java#create`, `service/UrlShortenerService.java#create`

---

## URL-104 — Redirect Endpoint

**Type:** Story | **Depends on:** URL-103, URL-302

**Problem / Goal:** Resolve a short code to its long URL and redirect the client on the
highest-traffic path in the system, with correct semantics for "never existed" vs. "existed,
now gone."

**Technical implementation plan:** `RedirectController` checks the per-IP rate limit, calls
`UrlShortenerService.resolveForRedirect()` (cache-aside via `UrlCacheService`, falling back to
a direct repository lookup only to distinguish 404 from 410), and returns the redirect.

**Acceptance criteria:** `302 Found` with a `Location` header (ADR #7 — not `301`, so clicks
always hit the server for accurate analytics); `404` for a code that never existed; `410` for
one that's expired or deleted.

**Implementation:** `api/RedirectController.java`,
`service/UrlShortenerService.java#resolveForRedirect`

---

## URL-105 — Custom Alias Support

**Type:** Task | **Depends on:** URL-103, URL-404

**Problem / Goal:** Let callers request a human-readable alias instead of an opaque generated
code, without allowing collisions with reserved routes or other users' aliases.

**Technical implementation plan:** `UrlValidationService.validateAlias()` enforces a
`^[a-zA-Z0-9_-]{3,20}$` pattern and a configurable reserved-word denylist;
`UrlShortenerService.create()` branches on whether a custom alias was supplied, and a DB
unique-constraint violation is translated to `409 Conflict`.

**Acceptance criteria:** aliases outside the length/character rule are rejected with `400`;
reserved words (`api`, `admin`, `urls`, …) are rejected; a duplicate active alias returns
`409`, not a silent overwrite.

**Implementation:** `service/UrlValidationService.java#validateAlias`,
`config/AliasProperties.java`, `exception/AliasConflictException.java`

---

## URL-106 — URL Expiration & TTL Enforcement

**Type:** Task | **Depends on:** URL-402

**Problem / Goal:** Support an optional expiration on a URL, and make sure an expired link
stops resolving without needing a background sweep.

**Technical implementation plan:** `expiresAt` column on `url_mappings`;
`UrlMapping.isExpired()` computed from the current time; the cache-aside loader only caches
(and only returns) rows that are `ACTIVE` *and* not expired; cache TTL on write is capped at
`min(configuredTtl, time-until-expiry)`.

**Acceptance criteria:** no default expiry (permanent unless specified); 5-year max cap (ADR
#5); an expired link returns `410`. **Known limitation:** there is no background reaper job —
expiry is enforced lazily at read time, not by flipping `status` to `EXPIRED` proactively (see
[[../00-Executive-Summary]] scope boundaries).

**Implementation:** `domain/UrlMapping.java#isExpired`,
`service/UrlShortenerService.java#effectiveTtl`, `service/UrlCacheService.java`

---

## URL-107 — Input Validation & Malicious URL Protection

**Type:** Task (security-sensitive — required human approval) | **Depends on:** URL-101

**Problem / Goal:** Reject unsafe or malformed URLs before they're ever persisted, and prevent
the shortener from being usable as a trusted-looking redirect into internal infrastructure.

**Technical implementation plan:** scheme allowlist (`http`/`https` only), a 2048-char length
bound, and a host-resolution check (`InetAddress.getAllByName`) that rejects loopback,
link-local (covers the `169.254.169.254` cloud-metadata address), site-local, multicast, and
wildcard addresses.

**Acceptance criteria:** non-http(s) schemes, oversized URLs, and internal-target hosts all
return `400`. See [[../Dashboards/05-Risk-and-Failure-Scenario-Analysis]] (R-5) for an important
after-the-fact correction: since `RedirectController` never itself fetches the long URL, this
is more accurately an internal-target/open-redirect guard than classic server-side SSRF
protection — still worth keeping, just not the threat model its original name implied.

**Implementation:** `service/UrlValidationService.java`

---

## URL-108 — Rate Limiting on Public Endpoints

**Type:** Story (security-sensitive — required human approval) | **Depends on:** URL-103, URL-104

**Problem / Goal:** Blunt abuse on the create and redirect endpoints without the operational
overhead of per-client provisioning.

**Technical implementation plan:** a Redis fixed-window counter (`INCR` against a
minute-scoped key, with a short expiry) keyed by authenticated client ID on create and by
client IP on redirect; a breach returns `429` with a computed `Retry-After`.

**Acceptance criteria:** 100 req/min per API key on create, 1000 req/min per IP on redirect
(ADR #8), both externally configurable via `application.yml` without a redeploy.

> [!warning] Gaps found later
> [[../Dashboards/05-Risk-and-Failure-Scenario-Analysis]] (R-4) found this ticket's coverage is
> incomplete: metadata/stats/delete endpoints aren't rate-limited at all, and the create-path
> check runs *after* authentication, so invalid-key attempts are unlimited. Not yet
> remediated — tracked as an open follow-up, not silently fixed here.

**Implementation:** `service/RateLimiterService.java`, `config/RateLimitProperties.java`

---

## URL-109 — OpenAPI/Swagger Documentation

**Type:** Task | **Depends on:** URL-103, URL-104, URL-105

**Problem / Goal:** Keep a machine-readable, browsable API contract in sync with the
implementation, rather than a hand-maintained doc that drifts.

**Technical implementation plan:** `springdoc-openapi-starter-webmvc-ui` generates the spec
directly from controller and DTO annotations; served at `/swagger-ui.html`.

**Acceptance criteria:** the full spec is reachable at runtime and matches the Phase 3
hand-designed spec in shape (same resources, same status codes).

**Implementation:** `pom.xml` (springdoc dependency), `application.yml` (`springdoc.*`),
controller/DTO classes
