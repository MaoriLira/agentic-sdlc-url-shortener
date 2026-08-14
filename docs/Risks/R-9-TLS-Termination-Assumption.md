---
tags: [risk, security, deployment, low-severity, disclosed]
---

# R-9: API Key Transport Assumes External TLS Termination

Related: [[../Dashboards/05-Risk-and-Failure-Scenario-Analysis]] · [[../Architecture-Decisions/ADR-05-Authentication-Model]]

**Severity:** Low (disclosed deployment prerequisite) | **Status:** ✅ Not a code defect — documented assumption

## Finding

The prototype runs on plain HTTP; `X-API-Key` confidentiality depends entirely on TLS being
terminated in front of it (ingress/load balancer) in any real deployment.

## Recommendation

None needed in application code. This is a reasonable and common assumption for a prototype —
flagged here explicitly as a deployment prerequisite so it isn't silently assumed by anyone
standing this up outside a local dev environment. See
[[../Architecture-Decisions/ADR-05-Authentication-Model]] for the auth model this protects.
