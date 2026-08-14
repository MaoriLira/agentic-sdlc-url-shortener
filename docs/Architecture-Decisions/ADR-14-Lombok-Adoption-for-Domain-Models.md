---
tags: [adr, architecture-decision, lombok, tech-debt]
---

# ADR-14: Lombok Adoption for Domain Models (Not DTOs)

Related: [[../Dashboards/01-Architecture-and-Design]] · [[../Jira-Tickets/Epic-URL-700-Codebase-Modernization]]

**Status:** Accepted (URL-701) | **Ambiguity resolved:** none from Phase 1 — a maintainability
request, not a requirement gap.

## Context

The six JPA entity classes carried a lot of hand-written getter/setter/constructor
boilerplate, and production code (`UrlShortenerService.create()`) and test setup code both
built objects via `new X(); x.setA(...); x.setB(...); ...` chains.

## Decision

Adopt Project Lombok, scoped to the **entity layer only** — `<optional>true</optional>` on
the dependency (compile-time annotation processor, never on the runtime classpath, no version
pin needed since `spring-boot-starter-parent` manages it).

**Per-class annotation choice is not uniform** — it mirrors what the class's API surface
already was, not a blanket `@Data`-everywhere approach:

| Class | Annotations | Why |
|---|---|---|
| `UrlMapping` | `@Getter @NoArgsConstructor @AllArgsConstructor @Builder`, field-level `@Setter` (not on `id`/`createdAt`) | The one class actually built via `new X()` + setter chain in production code — real `@Builder` payoff |
| `ApiClient` | Same shape as `UrlMapping` | Same reasoning; used in tests via `.builder()` |
| `ClickSummary` | `@Getter` only, `@NoArgsConstructor(PROTECTED)` | Never had a setter — mutation is `recordClick()` only. Adding `@Setter`/`@Builder` would have broken an intentional encapsulation boundary |
| `ClickDailyRollup` | `@Getter`, `@NoArgsConstructor(PROTECTED)`, field-level `@Setter` on `topReferrer` only | `clickCount` mutates only via `increment()`; `topReferrer` always had a setter |
| `ClickDailyRollupId` | `@EqualsAndHashCode @NoArgsConstructor(PROTECTED) @AllArgsConstructor` | Composite JPA ID — no getters existed before, none added |
| `ClickEventDlq` | `@Getter`, `@NoArgsConstructor(PROTECTED)` | Fully immutable after construction; the 3-arg constructor stays hand-written because it auto-stamps `failedAt = Instant.now()`, which Lombok can't express |

**DTOs are explicitly excluded.** `CreateUrlRequest`, `UrlResponse`, `ProblemResponse`,
`StatsResponse`, `ClickEvent`, `CacheableValue` are Java `record` types — already immutable,
already boilerplate-free, already correct `equals`/`hashCode`/`toString`. Converting a record
to a Lombok-annotated class would be a regression: records are language-native (zero
dependency) and are already what `@Value` tries to approximate.

## Consequences

- `UrlShortenerService.create()` now reads as `UrlMapping.builder().shortCode(...)...build()`
  instead of a `new X()` + 5-line setter chain — genuinely more readable.
- `ApiKeyAuthServiceTest` demonstrates the same in test code.
- **Minor, deliberate API widening**: `UrlMapping` and `ApiClient` never had an all-args
  constructor before; now they do (via `@AllArgsConstructor`/`@Builder`), which technically
  allows a caller to set `id` and `createdAt` explicitly where it couldn't before. Nothing in
  application code does this — `id` is still DB-generated, `createdAt` is still stamped by
  `@PrePersist` — but it's a real, if narrow, change to what's possible, not just how it's
  written. Flagged here rather than left implicit.
- New build-time dependency: contributors need the Lombok IDE plugin for their editor to
  understand generated methods (IntelliJ/VS Code both have first-class support; this is a
  low-friction, extremely common requirement, not exotic tooling).
- `./mvnw clean install` verified clean (compile + all 38 tests) after every entity was
  converted.

**Implementation:** `pom.xml`, `domain/*.java` (6 entity classes),
`service/UrlShortenerService.java`, `src/test/java/.../service/ApiKeyAuthServiceTest.java`
