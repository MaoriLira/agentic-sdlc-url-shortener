---
tags: [adr, architecture-decision, redirect]
---

# ADR-06: Redirect HTTP Status Code

Related: [[../Dashboards/01-Architecture-and-Design]] · [[../Jira-Tickets/Epic-URL-100-Core-APIs]]

**Status:** Accepted (Phase 2) | **Ambiguity resolved:** #7 (redirect status code)

## Context

`301 Moved Permanently` vs. `302 Found` for the redirect response — a real trade-off, not a
detail. Browsers cache `301` responses locally and may stop hitting the server on repeat
visits entirely.

## Decision

`302 Found`, not `301`.

## Consequences

- Every click reaches the server, which is what makes accurate click analytics
  ([[../Dashboards/02-Agentic-Workflow-and-Jira-Tickets|URL-200 epic]]) possible at all — a `301`
  would silently starve the analytics pipeline of data for repeat visitors.
- Trade-off: marginally higher server load than a browser-cached `301`, and no SEO
  "permanence" signal — both acceptable, since analytics accuracy is a stated core
  requirement and this isn't a search-indexed redirect use case.

**Implementation:** `api/RedirectController.java` (`HttpStatus.FOUND`)
