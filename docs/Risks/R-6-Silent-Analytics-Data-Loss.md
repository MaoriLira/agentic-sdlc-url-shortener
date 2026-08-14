---
tags: [risk, kafka, medium-severity, open]
---

# R-6: Silent Analytics Data Loss During a Kafka Outage

Related: [[../Dashboards/05-Risk-and-Failure-Scenario-Analysis]] · [[../Dashboards/01-Architecture-and-Design]] · [[../Risks/R-8-No-Observability-Instrumentation]]

**Severity:** Medium | **Status:** ⬜ Open — covered by R-8's recommendation

## Finding

Already disclosed as a design trade-off: click-event publishing is fire-and-forget
(`ClickEventPublisher`), by design, so a Kafka outage never fails the redirect response.
Formalizing the operational consequence: there is currently no counter or alert on publish
failures — `ClickEventPublisher` only logs at `WARN`. An extended outage would silently
under-count clicks with nothing surfacing it short of someone reading application logs.

## Recommendation

Covered by [[R-8-No-Observability-Instrumentation]] — a publish-failure counter is one of the
first metrics worth adding once instrumentation exists at all.
