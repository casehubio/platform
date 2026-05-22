# 0007 — SlaBreachPolicy belongs in casehub-work-api, not casehub-platform

Date: 2026-05-22
Status: Accepted

## Context and Problem Statement

Platform#24 proposed a new `casehub-platform-apps-api` module to house
"domain-agnostic application-layer SPIs," with `SlaBreachPolicy` as the initial
content. During brainstorming, the question arose: is this the right home for
breach handling, or do the types belong closer to casehub-work?

## Decision Drivers

* `BreachedTask` carries `taskId`, `callerRef`, `candidateGroups` — work vocabulary
* `SlaBreachContext` needs `Path` and `Preferences` from `casehub-platform-api` — a casehubio dependency
* Any casehub repo may depend on `casehub-platform-api` — this dependency is explicitly allowed
* A `casehub-platform-apps-api` module that claims to be domain-agnostic but contains only work-specific types is misleading
* `casehub-work-api` already owns all SLA-related types: `EscalationPolicy`, `ClaimSlaPolicy`, `ClaimSlaContext`

## Considered Options

* **Option A** — `casehub-platform-apps-api` (as proposed in platform#24)
* **Option B** — `casehub-work-api` directly; `casehub-work-api` adds `casehub-platform-api` compile dependency
* **Option C** — New `casehub-work-apps-api/` module bridging both

## Decision Outcome

Chosen option: **Option B**. `SlaBreachPolicy`, `BreachDecision`, `SlaBreachContext`,
`BreachedTask`, and `BreachType` belong in `casehub-work-api`. `casehub-work-api` adds
`casehub-platform-api` as a compile dependency — this is acceptable, as any casehub
repo may depend on `casehub-platform-api`. The breach types are work vocabulary; placing
them in platform creates a work-flavoured platform module that misrepresents its scope.

`casehub-platform-apps-api` remains a valid concept for truly cross-cutting application
SPIs that span all casehub modules equally. No such SPI has been identified yet; the
module should be created when one appears.

### Positive Consequences

* All SLA/breach types in one module (`casehub-work-api`) — no cross-module discovery
* `casehub-platform` stays free of work-specific vocabulary
* Simpler dependency graph — no new platform module

### Negative Consequences / Tradeoffs

* `casehub-work-api` is no longer zero-casehubio-deps (gains `casehub-platform-api`)
* If other foundation repos need application-layer SPIs with platform types, they face the same question independently

## Links

* casehubio/platform#24 — closed with this decision
* casehubio/work#213 — implements `SlaBreachPolicy` and types in `casehub-work-api`
* casehubio/work#212 — wires `SlaBreachPolicy` into the expiry service
