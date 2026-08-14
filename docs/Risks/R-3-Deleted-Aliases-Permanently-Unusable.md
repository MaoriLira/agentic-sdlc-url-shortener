---
tags: [risk, database, high-severity, fixed]
---

# R-3: Deleted Custom Aliases Are Permanently Unusable

Related: [[../Dashboards/05-Risk-and-Failure-Scenario-Analysis]] · [[../Guardrails/Base62-Collision-Free-Codes]] · [[../Jira-Tickets/Epic-URL-500-Brownfield-Remediation]] · [[../Scenarios/B-Brownfield-Refactoring]]

**Severity:** High (and easy to fix) | **Status:** ✅ Fixed — URL-501

> [!success] Status: Fixed — URL-501
> `V5__alias_reuse_partial_unique_index.sql` replaced the table-wide constraint with a
> partial unique index scoped to `status = 'ACTIVE'`. Verified by
> `deletedCustomAlias_canBeReused` in the integration suite. Full detail in
> [[../Scenarios/B-Brownfield-Refactoring]].

## Finding

`DELETE /api/v1/urls/{shortCode}` is a **soft** delete — `status` flips to `DELETED`, but the
row (and its `short_code`) stayed in the table forever. The `short_code` column originally had
a plain `UNIQUE` constraint, not a partial-unique-among-`ACTIVE` constraint. That meant once a
custom alias like `my-launch` was created and later deleted, **nobody — including the original
owner — could ever create `my-launch` again.** This wasn't caught during the original schema
design because that review didn't reason about the delete lifecycle explicitly.

## Recommendation (as originally written)

Change the unique constraint to a partial index (`UNIQUE ... WHERE status = 'ACTIVE'`),
matching the pattern already used for the `short_code_active` lookup index. Low-risk,
high-value fix — see [[../Guardrails/Base62-Collision-Free-Codes]] for the fix as built.
