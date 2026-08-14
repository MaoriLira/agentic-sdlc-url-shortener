---
tags: [risk, security, threat-model, informational, docs-reframed]
---

# R-5: "SSRF Protection" Is Mis-Scoped for This Architecture

Related: [[../Dashboards/05-Risk-and-Failure-Scenario-Analysis]] · [[../Guardrails/Internal-Target-Open-Redirect-Validation]]

**Severity:** Medium (informational / design-record correction) | **Status:** 🟡 Docs reframed, code comment unchanged

## Finding

`UrlValidationService` blocks private/internal targets at **create time**. But
`RedirectController` never itself fetches the long URL — it returns a `302` and the
**client's own browser** follows it. There is no server-side outbound request anywhere in this
service, so the classic "server-side request forgery" framing originally used in the
guardrails doc slightly overstates the mechanism. What the check *actually* prevents is
different and still valuable: **the shortener being weaponized as a trusted-looking open
redirect into an internal network** — e.g. a short link on an internal domain silently
pointing an employee's browser at `http://10.x.x.x/admin`. That's a real and worth-blocking
scenario; it's just not "SSRF" in the strict sense.

This matters for defensibility: if a future feature adds a server-side fetch (link preview,
metadata scraping, thumbnail generation), this check would need to move from
"validate-then-trust-forever" to "resolve, pin the IP, and connect to the pinned IP" —
otherwise it's vulnerable to DNS rebinding (the host validates safely at creation, then
re-resolves to an internal address by the time anything fetches it). Today, with no
server-side fetch, rebinding isn't exploitable — but the create-time-only check would become
the wrong control the moment that changes.

## Recommendation

Rename the internal framing from "SSRF protection" to "internal-target / open-redirect
validation" in docs and code comments, and flag DNS-rebinding-safe fetch validation as a
prerequisite for any future server-side-fetch feature — not a gap to fix now.

> [!success] Status: Docs reframed
> [[../Guardrails/Internal-Target-Open-Redirect-Validation]] now carries this correction as
> its primary name. The code comment in `UrlValidationService` still says "SSRF protection" —
> a code-comment wording pass was judged not worth its own ticket, but is a fair thing to
> bundle into the next time that file is touched.
