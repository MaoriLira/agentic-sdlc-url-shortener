---
tags: [jira-ticket, epic, tech-debt, lombok]
---

# Epic: URL-700 — Codebase Modernization

Dashboard: [[../Dashboards/02-Agentic-Workflow-and-Jira-Tickets]] · Related: [[../Architecture-Decisions/ADR-14-Lombok-Adoption-for-Domain-Models]]

> [!info] A different kind of epic
> Not driven by a feature requirement or a risk finding — a maintainability/DX improvement
> requested directly. One ticket (URL-701): reduce hand-written boilerplate in the JPA entity
> layer using Project Lombok.

| Ticket | Title | Type | Depends on |
|---|---|---|---|
| URL-701 | Integrate Lombok for domain-model boilerplate reduction | Task | — |

---

## URL-701 — Integrate Lombok for domain-model boilerplate reduction

**Type:** Task (new dependency — required human approval per this project's own governance
model) | **Depends on:** — | **Status:** Done

**Problem:** The six JPA entity classes (`UrlMapping`, `ApiClient`, `ClickSummary`,
`ClickDailyRollup`, `ClickDailyRollupId`, `ClickEventDlq`) carry a lot of hand-written
getter/setter/constructor boilerplate — `UrlMapping` alone has 8 fields and ~15 accessor
methods. Production code that builds one (`UrlShortenerService.create()`) does so via
`new X(); x.setA(...); x.setB(...); ...`, and test setup code does the same. Every new field
means another manual getter, setter, and constructor edit.

**Goal:** Cut the boilerplate with Project Lombok (`@Getter`, `@Setter`,
`@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`) without changing the actual public
behavior of any class — encapsulation choices that were deliberate (e.g. `ClickSummary` has
no setters at all; mutation only happens through `recordClick()`) stay deliberate.

**Technical implementation plan:**
1. Add `org.projectlombok:lombok` to `pom.xml`, `<optional>true</optional>`, no explicit
   version (managed by the `spring-boot-starter-parent` BOM already in use).
2. Apply Lombok to the 6 JPA entities. Per-class annotation choice is not uniform — see
   [[../Architecture-Decisions/ADR-14-Lombok-Adoption-for-Domain-Models]] for exactly which
   annotations went on which class and why (e.g. no `@Setter`/`@Builder` on classes that were
   intentionally immutable-after-construction).
3. Add `@Builder` to `UrlMapping` and `ApiClient` specifically — the two classes actually
   constructed with `new X(); x.setY(...)` chains in production code and tests — and refactor
   `UrlShortenerService.create()` and `ApiKeyAuthServiceTest` to use the builder.
4. **Explicitly do not touch the DTO records** (`CreateUrlRequest`, `UrlResponse`,
   `ProblemResponse`, `StatsResponse`) or the other records (`ClickEvent`, `CacheableValue`).
   They're already Java `record` types — zero boilerplate, immutable, correct
   `equals`/`hashCode`/`toString` for free. Converting a record to a Lombok-annotated class
   would be a regression, not a boilerplate reduction; a record already *is* what `@Value`
   would try to approximate.

**Acceptance criteria:**
- `./mvnw clean install` compiles cleanly and all tests pass (Lombok-generated code included).
- No behavioral change to any entity's public API except where explicitly documented as a
  deliberate, minor widening (e.g. `UrlMapping` gaining a full `@AllArgsConstructor`/
  `@Builder`, which it never had before).
- Production code (`UrlShortenerService.create()`) and at least one test
  (`ApiKeyAuthServiceTest`) demonstrably use the builder instead of `new X()` + setters.

**Implementation:** `pom.xml`,
`domain/UrlMapping.java`, `domain/ApiClient.java`, `domain/ClickSummary.java`,
`domain/ClickDailyRollup.java`, `domain/ClickDailyRollupId.java`, `domain/ClickEventDlq.java`,
`service/UrlShortenerService.java`,
`src/test/java/.../service/ApiKeyAuthServiceTest.java`
