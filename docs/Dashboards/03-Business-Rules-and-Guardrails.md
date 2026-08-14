---
tags: [business-rules, guardrails, moc]
---

# Business Rules & Guardrails

Related: [[../00-Executive-Summary]] · [[01-Architecture-and-Design]] · [[02-Agentic-Workflow-and-Jira-Tickets]] · [[05-Risk-and-Failure-Scenario-Analysis]]

Five guardrails protect this service. Each link below is the atomic note with the real code,
the trade-offs, and the ticket.

* [[../Guardrails/Base62-Collision-Free-Codes]] - DB-sequence codes, collision-free by construction; alias reuse fixed in URL-501.
* [[../Guardrails/Internal-Target-Open-Redirect-Validation]] - scheme allowlist + private/internal host blocking. See [[../Risks/R-5-SSRF-Threat-Model-Correction]] for the naming correction.
* [[../Guardrails/Redis-Fixed-Window-Rate-Limiting]] - 100/min create, 1000/min redirect. See [[../Risks/R-4-Rate-Limiting-Coverage-Gaps]] for known gaps.
* [[../Guardrails/Kafka-Idempotent-Aggregation-and-DLQ]] - `eventId` dedup guard + 2-retry-then-DLQ.
* [[../Guardrails/Cache-Aside-Graceful-Degradation]] - Redis failure falls back to PostgreSQL, scoped to the cache path only (URL-502).

Full failure analysis of each → [[05-Risk-and-Failure-Scenario-Analysis]]
