# 0008 — CaseMemoryStore adapter repository placement

Date: 2026-05-28
Status: Accepted — amended 2026-05-29 (see bottom)

## Context and Problem Statement

`CaseMemoryStore` is a new platform SPI for queryable, permission-aware agent memory.
Concrete adapters (Memori, Mem0, Graphiti) must live somewhere. Two viable locations:
within `casehub-platform` as sibling submodules (like `persistence-jpa/`), or in a
separate `casehub-memory` repo.

## Decision Drivers

* Adapter independence — memory backends are useful to any Quarkus multi-agent system,
  not just CaseHub deployments
* Operational weight — adapters are REST clients to external services; not DB-direct
  like `persistence-jpa/`
* Extraction cost — `casehub-eidos` was extracted from another repo later; the migration
  is mechanical but touches every consumer's `pom.xml`
* Platform cohesion — `persistence-jpa/` and `persistence-mongodb/` are within platform
  because they are direct infrastructure adapters, not external service clients

## Considered Options

* **Option A** — Adapters within `casehub-platform` as sibling modules
* **Option B** — Adapters in a new standalone `casehub-memory` repo
* **Option C** — Adapters in separate per-adapter repos

## Decision Outcome

Chosen option: **Option B — `casehub-memory` standalone repo**, because the capability
is independently useful outside CaseHub, the adapters are operationally substantial
(REST clients, retry logic, external service config), and starting separately avoids the
extraction cost that `casehub-eidos` paid later. The SPI and `@DefaultBean` no-op stay
in `casehub-platform` regardless.

### Positive Consequences

* Adapters can be independently versioned and consumed without pulling in all of platform
* Mirrors the pattern of `casehub-work` and `casehub-ledger` — independently useful
  capabilities get their own repo
* Platform repo stays focused on zero-dep SPIs and lightweight CDI infrastructure

### Negative Consequences / Tradeoffs

* One more repo to maintain, CI to wire, and publish step to manage
* Cross-repo issue tracking needed for adapter work (platform#31–36 filed)

## Pros and Cons of the Options

### Option A — Within casehub-platform

* ✅ Consistent with `persistence-jpa/`, `persistence-mongodb/` pattern
* ✅ One repo, one publish step
* ❌ REST client deps enter a repo currently free of external service dependencies
* ❌ Extraction cost if the decision is reversed later

### Option B — Standalone casehub-memory repo

* ✅ Independently useful outside CaseHub
* ✅ Avoids extraction cost (eidos precedent)
* ✅ Each adapter independently versionable
* ❌ Additional repo overhead

### Option C — Separate per-adapter repos

* ✅ Maximum isolation
* ❌ Three repos for closely related adapters; shared plumbing duplicated
* ❌ Disproportionate operational overhead for adapter-tier code

## Links

* casehubio/platform#27 — CaseMemoryStore implementation
* casehubio/platform#31 — Create casehub-memory repo (closed — see amendment)

---

## Amendment — 2026-05-29

**Revised status:** Option A (in-platform modules) adopted initially; Option B (standalone repo) deferred.

**Context:** ADR-0008 chose Option B before any adapters existed. Platform#31 was filed to create the
`casehub-memory` repo infrastructure as the next immediate step. On review, this is premature: repo setup,
CI wiring, and parent-POM plumbing add real overhead for a capability with zero concrete adapters and no
confirmed non-CaseHub consumer.

**Revised decision:** Adapters start as submodules within `casehub-platform` — `memory-memori/`,
`memory-mem0/` etc. — following the same pattern as `persistence-jpa/` and `persistence-mongodb/`.
The SPI (`CaseMemoryStore`) and `@DefaultBean` no-op (`NoOpCaseMemoryStore`) remain in `platform-api` /
`platform` as specified. Platform#31 is closed.

**Extraction triggers:** Extract to `casehub-memory` when either:
- A non-CaseHub consumer needs the adapters independently, OR
- Adapter complexity (release cadence, versioning) outgrows the platform repo's scope.

**Effect on original rationale:** The extraction-cost concern (casehub-eidos precedent) remains valid —
it is now a deferred concern rather than a day-zero forcing function. Module isolation already provides
the dependency separation that a standalone repo would give.

See also: protocol `PP-20260529-spi-adapter-placement` (casehub garden).
