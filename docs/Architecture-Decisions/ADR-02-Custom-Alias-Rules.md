---
tags: [adr, architecture-decision, alias]
---

# ADR-02: Custom Alias Rules

Related: [[../Dashboards/01-Architecture-and-Design]] · [[../Guardrails/Base62-Collision-Free-Codes]] · [[../Jira-Tickets/Epic-URL-100-Core-APIs]]

**Status:** Accepted (Phase 2) | **Ambiguity resolved:** #3 (custom alias rules)

## Context

Letting callers request a human-readable alias instead of a generated code needed bounds:
allowed character set, length, and whether an alias could shadow the service's own routes
(e.g. an alias literally named `api` or `admin`).

## Decision

Aliases must match `^[a-zA-Z0-9_-]{3,20}$` and are checked against a configurable
reserved-word denylist (`api`, `admin`, `urls`, `actuator`, `swagger-ui`, `v3`, `health`,
`static`, `assets`, `www`).

## Consequences

- Standard, unsurprising convention — no Unicode/emoji handling complexity.
- The denylist is externally configurable (`urlshortener.alias.reserved-words`), so new
  reserved routes don't require a code change.
- Unlike generated codes, aliases can legitimately collide between two callers — see
  [[../Guardrails/Base62-Collision-Free-Codes]] for the DB-level uniqueness mechanism.

**Implementation:** `service/UrlValidationService.java#validateAlias`,
`config/AliasProperties.java`
