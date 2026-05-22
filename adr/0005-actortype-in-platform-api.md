# 0005 — ActorType ownership in casehub-platform-api

Date: 2026-05-22
Status: Accepted

## Context and Problem Statement

`ActorType` (HUMAN / AGENT / SYSTEM) classifies actors by kind. It was
originally defined in `casehub-ledger-api` as a ledger concern, but is
needed by `CurrentPrincipal` in `casehub-platform-api` and any repo that
works with principal identity without a ledger dependency. Keeping it in
`casehub-ledger-api` forces a transitive ledger dependency on all consumers
that only need identity primitives.

## Decision Drivers

* `CurrentPrincipal.actorType()` must be expressible without a ledger dependency
* `casehub-platform-api` is zero-dependency; adding `casehub-ledger-api` would break the tier model
* Actor type resolution rules (`system:*`, versioned persona, A2A roles) are identity concerns, not ledger concerns

## Considered Options

* **Option A** — Keep ActorType in `casehub-ledger-api`; add `actorType()` to `CurrentPrincipal` via a bridge module
* **Option B** — Migrate ActorType to `casehub-platform-api`; remove from ledger-api after consumers migrate
* **Option C** — Define a duplicate `ActorType` in `casehub-platform-api` and deprecate the ledger one

## Decision Outcome

Chosen option: **Option B**, because identity classification belongs in the identity
tier. `casehub-platform-api` owns `CurrentPrincipal`, `GroupMembershipProvider`, and
all actor-identity primitives. `ActorType` is a classification of those primitives —
it has no inherent ledger semantics. A bridge module (Option A) would add accidental
complexity; a duplicate type (Option C) creates ambiguity for consumers.

### Positive Consequences

* `CurrentPrincipal.actorType()` ships as a default method with no new dependencies
* Consumers needing actor type without the ledger avoid a transitive dependency
* `casehub-ledger-api` loses a non-ledger concept, improving cohesion

### Negative Consequences / Tradeoffs

* `casehub-ledger-api` consumers using `ActorType` must migrate their import to `casehub-platform-api` (tracked: casehubio/ledger#88)

## Links

* Closes casehubio/platform#22
* Unblocks casehubio/ledger#88
* Protocol PP-20260522-359dfc — CurrentPrincipal boolean methods must delegate to actorType()
