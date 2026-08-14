---
tags: [guardrail, short-codes, database]
---

# Guardrail: Base62 Collision-Free Short Codes

Related: [[../Architecture-Decisions/ADR-01-Short-Code-Generation-Strategy]] · [[../Jira-Tickets/Epic-URL-100-Core-APIs]] · [[../Jira-Tickets/Epic-URL-400-Database-Persistence]]

**Ticket:** [[../Jira-Tickets/Epic-URL-100-Core-APIs#URL-102 — Short Code Generation Strategy (Base62)|URL-102]] / [[../Jira-Tickets/Epic-URL-400-Database-Persistence#URL-404 — Short-Code Collision Detection & Handling|URL-404]]

Short codes are generated from a monotonically increasing PostgreSQL sequence
(`core.short_code_seq`), Base62-encoded — not randomly generated.

```sql
-- V3__short_code_sequence.sql
CREATE SEQUENCE core.short_code_seq START WITH 56800235584 INCREMENT BY 1;
```

`56800235584` is `62^6` — the first value whose Base62 encoding is exactly 7 characters.
Every subsequent value is also unique by definition of a sequence, so **collision handling
for system-generated codes is unnecessary** — there's no retry loop on the hot create path.

> [!success] Verified live
> The very first code minted in the live smoke test was `1000000` — the Base62 representation
> of `62^6` — confirming the sequence math against the running app, not just unit tests.

## Custom aliases: the one path that *can* collide

Two callers requesting the same alias is handled defensively at the database layer:
`short_code` has a **partial unique index scoped to `status = 'ACTIVE'`** (not a plain column
constraint), and `UrlShortenerService.create()` catches `DataIntegrityViolationException` and
converts it to a `409 Conflict` (`AliasConflictException`) — belt-and-suspenders, not the
primary mechanism.

```sql
-- V5__alias_reuse_partial_unique_index.sql
CREATE UNIQUE INDEX idx_url_mappings_short_code_active
    ON core.url_mappings (short_code) WHERE status = 'ACTIVE';
```

> [!success] Fixed: deleted aliases are reusable (URL-501)
> The constraint was originally a table-wide `UNIQUE`, which meant a deleted alias could
> **never** be reused by anyone — a real defect found in
> [[../Risks/R-3-Deleted-Aliases-Permanently-Unusable]] and fixed via the migration above.
> Full before/after in [[../Scenarios/B-Brownfield-Refactoring]].

> [!warning] Trade-off
> A single shared sequence is a soft serialization point under extreme create throughput.
> Acceptable at the [[../Architecture-Decisions/ADR-10-Scale-Target|design target]] (50 RPS
> creates). At materially higher throughput, this would move to block/Hi-Lo allocation.

**Implementation:** `service/ShortCodeGenerator.java`, `service/Base62Encoder.java`,
`service/UrlShortenerService.java#create`, `db/migration/V3__short_code_sequence.sql`,
`db/migration/V5__alias_reuse_partial_unique_index.sql`
