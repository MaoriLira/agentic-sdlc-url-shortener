---
tags: [guardrail, security, validation]
---

# Guardrail: Internal-Target / Open-Redirect Validation

Related: [[../Jira-Tickets/Epic-URL-100-Core-APIs]] · [[../Risks/R-5-SSRF-Threat-Model-Correction]]

**Ticket:** [[../Jira-Tickets/Epic-URL-100-Core-APIs#URL-107 — Input Validation & Malicious URL Protection|URL-107]] — `service/UrlValidationService.java`

> [!info] Naming note
> This guardrail was originally documented as "SSRF protection." [[../Risks/R-5-SSRF-Threat-Model-Correction]]
> found that framing overstates the mechanism — see that file for the full reasoning. This
> page uses the corrected name; `UrlValidationService`'s own code comment still says "SSRF" (a
> known, disclosed, not-yet-updated inconsistency — see the note at the bottom).

Validation runs at **create time**, before a URL is ever persisted:

1. **Scheme allowlist** — only `http` and `https` accepted (blocks `javascript:`, `file:`,
   `ftp:`, etc.)
2. **Length bound** — 2048 chars max
3. **Host resolution check** — the host is resolved via `InetAddress.getAllByName()` and
   rejected if it resolves to any of: loopback, link-local (covers the `169.254.169.254`
   cloud-metadata address), site-local/private ranges, multicast, or wildcard addresses

```java
if (address.isLoopbackAddress() || address.isLinkLocalAddress()
        || address.isSiteLocalAddress() || address.isMulticastAddress()
        || address.isAnyLocalAddress()) {
    throw new InvalidUrlException(
            "longUrl resolves to a private/internal address and is not allowed");
}
```

Custom aliases get a separate check (see [[../Architecture-Decisions/ADR-02-Custom-Alias-Rules]]):
`^[a-zA-Z0-9_-]{3,20}$` plus a reserved-word denylist so a crafted alias can't shadow the
service's own management routes.

> [!danger] Verified live
> `POST /api/v1/urls` with `longUrl: http://169.254.169.254/latest/meta-data` returns `400`
> against the running app — hand-tested, not just asserted in a unit test.

## Why "internal-target validation," not "SSRF protection"

`RedirectController` never itself fetches the long URL — it returns a `302` and the
**client's own browser** follows it. There's no server-side outbound request anywhere in this
service, so classic SSRF framing overstates the mechanism. What this actually prevents:
**the shortener being weaponized as a trusted-looking open redirect into an internal
network** — still a real, worth-blocking scenario, just a different one. Full analysis in
[[../Risks/R-5-SSRF-Threat-Model-Correction]], including why this reframing matters if a
future feature ever adds a server-side fetch (link preview, thumbnailing).

**Implementation:** `service/UrlValidationService.java`
