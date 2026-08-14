---
tags: [adr, architecture-decision, short-codes]
---

# ADR-01: Short-Code Generation & Collision Strategy

Related: [[../Dashboards/01-Architecture-and-Design]] · [[../Guardrails/Base62-Collision-Free-Codes]] · [[../Jira-Tickets/Epic-URL-100-Core-APIs]]

**Status:** Accepted (Phase 2) | **Ambiguities resolved:** #1 (code length/strategy), #2 (collision handling)

## Context

Phase 1 flagged two open questions before any code could be written: what strategy generates
a short code (fixed-length random string? hash-based? counter-based?), and how collisions get
handled if two requests ever produce the same code.

## Decision

Generate codes from a monotonically increasing PostgreSQL sequence
(`core.short_code_seq`), Base62-encoded. The sequence starts at `62^6` so its output is
exactly 7 characters from the first value onward, growing only if the keyspace is ever
exhausted. Because the sequence guarantees a distinct integer per call, **collision handling
for system-generated codes is unnecessary** — no pre-check, no retry loop.

## Consequences

- The hot create-path never pays for a collision-retry loop.
- A single shared sequence is a soft serialization point under extreme create throughput —
  acceptable at the [[ADR-10-Scale-Target|target scale]]; would move to block/Hi-Lo allocation
  if that changes.
- Custom aliases are the one path that *can* still collide — see
  [[ADR-02-Custom-Alias-Rules]] and [[../Guardrails/Base62-Collision-Free-Codes]] for how
  that's handled (a DB unique index, not application-level pre-checking).

**Implementation:** `service/ShortCodeGenerator.java`, `service/Base62Encoder.java`,
`db/migration/V3__short_code_sequence.sql`
