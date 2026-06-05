# Memory CDI Priority and Emission Design

**Date:** 2026-06-05 (revised 2026-06-05)
**Issues:** platform#39 (CDI priority conflict), platform#49 (CDI emission pattern + storeAll batch)
**Branch:** issue-39-memory-cdi-and-priority

---

## Context

platform#39 was filed when `memory-mem0/` shipped — both `memory-inmem/` and production adapters
(sqlite, mem0) are `@Alternative @Priority(1)`, causing `AmbiguousResolutionException` in any
`@QuarkusTest` that has a production adapter on compile scope and `memory-inmem/` on test scope.

platform#49 tracked the emission pattern investigation opened by platform#48 (now closed). Three
options (A/B/C) were documented in the `CaseMemoryStore.store()` Javadoc pending consumer feedback.
The `storeAll()` default issues N individual transactions; `memory-jpa/` does not override it
(SQLite and InMemory already do).

---

## Section 1 — CDI Priority Fix (#39)

### Decision

`InMemoryMemoryStore` changes from `@Alternative @Priority(1)` to `@Alternative @Priority(10)`.

### Rationale

Production adapters (sqlite, mem0) sit at Priority(1) — Tier 2 of the persistence-backend-cdi
priority ladder. `memory-inmem/` is the test-override adapter: it must reliably win when colocated
with a production adapter on the test classpath. Priority(10) encodes that intent without ambiguity.

In production augmentation, `memory-inmem/` is test-scoped and invisible — no conflict regardless
of priority value.

### CDI resolution for co-deployed adapters

`JpaMemoryStore` is a plain `@ApplicationScoped` bean (no `@Alternative`). The other adapters are
`@Alternative @Priority(1)`. CDI resolution rules:

| Co-deployed combination | Resolution |
|---|---|
| JPA + SQLite or JPA + Mem0 | Alternative wins silently — no exception |
| SQLite + Mem0 | Two `@Alternative @Priority(1)` beans → `AmbiguousResolutionException` at startup |
| JPA + SQLite + `memory-inmem/` (test) | `memory-inmem/` at Priority(10) wins |
| SQLite/Mem0 + `memory-inmem/` (test) | `memory-inmem/` at Priority(10) wins |

JPA + a Tier-2 alternative co-deployment resolves silently to the alternative. There is no
ambiguity or exception for that combination. Only two Tier-2 alternatives simultaneously cause
`AmbiguousResolutionException`. The "Do NOT combine" convention remains the safety gate for
JPA + alternative; CDI will not catch that error.

### Ephemeral install footgun

CLAUDE.md permits `memory-inmem/` as compile scope for ephemeral installs. At Priority(10), a
compile-scoped `memory-inmem/` silently beats every production adapter. A misconfigured production
build (compile scope instead of test scope) would silently use the in-memory store with no
startup error. This is intentional for ephemeral installs but undetectable for accidental
mis-scoping. Prevention is build-level (Maven scope review, CI), not CDI.

### Priority reserved bands

| Range | Tier | Purpose |
|---|---|---|
| `@DefaultBean` | 0 — no-op | `NoOpCaseMemoryStore` — active when no adapter on classpath |
| `@ApplicationScoped` (no qualifier) | 1 — production SQL | `JpaMemoryStore` — default when module on classpath |
| Priority(1–9) | 2 — production override | `SqliteMemoryStore`, `Mem0CaseMemoryStore` (and future adapters) |
| Priority(10+) | 3 — test override | `InMemoryMemoryStore` — test scope only (compile only for ephemeral) |

Future production adapters land in Priority(1–9). Future test-override adapters land in Priority(10+).
If a future adapter needs to beat an existing production adapter, use Priority(2–9).

### Multi-store topology note

`JpaMemoryStore` is the only non-alternative bean — it is "default production" and is silently
displaced by any Tier-2 adapter. This model assumes one active store per deployment. If co-deployed
relational + vector stores (JPA + Mem0) are ever needed simultaneously, the correct CDI solution
is a composite adapter that delegates writes/reads to both. The priority ladder is not the path for
multi-store topologies.

### Files changed

- `memory-inmem/.../InMemoryMemoryStore.java` — `@Priority(1)` → `@Priority(10)`
- `casehub-parent/docs/protocols/universal/persistence-backend-cdi-priority.md` — reference table
  updated with Tier 3 test-override band and clarification on non-alternative resolution
- `CLAUDE.md` module table — `memory-inmem/` priority note updated

---

## Section 2 — Emission Pattern Decision (#49 Part 1)

### Decision

**Option A is canonical.** Call `CaseMemoryStore.store()` directly from application code.
Platform ships no CDI emission infrastructure.

### Why not Option C (MemoryStoreRequest event + memory-cdi/ module)

1. **Exception propagation.** `store()` may throw `SecurityException` from
   `MemoryPermissions.assertTenant()`. With `Event.fireAsync()`, exceptions are invisible to the
   caller — a compliance-adjacent write silently "succeeds" even if the store rejected it.

2. **Request context loss.** `JpaMemoryStore` injects `@RequestScoped CurrentPrincipal`. Calling
   `store()` from an `@ObservesAsync` observer runs on a worker thread where the request scope is
   not propagated by default in Quarkus. The call fails with `ContextNotActiveException` before
   reaching `assertTenant()` — the same failure mode as any `@RequestScoped` bean invoked from
   an async observer without explicit context propagation.

3. **API inconsistency.** Apps already inject `CaseMemoryStore` for reads (`query()`). Making
   writes go through CDI events while reads are direct injection creates two call-site patterns
   for the same SPI.

4. **YAGNI.** Multi-observer fan-out is hypothetical. If fan-out across stores is needed (e.g.
   relational + vector stores), it belongs in a composite adapter, not in a CDI relay layer.

### Javadoc update

`CaseMemoryStore.store()` Javadoc removes the three-option list and "under investigation" language.
Replacement:

> **Emission pattern:** inject `CaseMemoryStore` directly and call `store()` from your domain
> event handler. This is the canonical pattern — direct injection keeps exception propagation
> intact (`SecurityException` from `assertTenant()` reaches the caller), keeps request context
> active for `@RequestScoped` implementations, and is consistent with the read API (`query()`).
>
> CDI event indirection — firing a platform or domain event that a CDI observer then stores —
> is not recommended. `@ObservesAsync` observers run on a thread pool where the request scope
> is not propagated, causing `ContextNotActiveException` on `@RequestScoped CurrentPrincipal`
> injection. `@Observes` (synchronous) preserves request context but couples the caller's
> transaction to the store call — which is usually correct for compliance-adjacent writes.

### Transaction atomicity note

`JpaMemoryStore.store()` is `@Transactional(REQUIRED)`. Called from within a transaction (the
typical case for a domain event handler), it joins that transaction and rolls back atomically with
it. Called from `@ObservesAsync`, it runs outside the original transaction — a rollback of the
publisher's transaction does not roll back the stored memory. This is the dual-write problem.
Direct synchronous injection avoids it. Callers using async patterns must handle idempotency
themselves; this is out of scope for the platform SPI.

### PLATFORM.md update

Capability ownership entry for `CaseMemoryStore` gains: `emission: direct injection — see SPI Javadoc`.

---

## Section 3 — storeAll() Batch (#49 Part 2)

### Existing implementations

`SqliteMemoryStore` already overrides `storeAll()` (line 108): opens a single JDBC connection,
calls `assertTenant(inputs.get(0), principal)` once, then loops with per-item `assertTenant()`
to detect mixed-tenant inputs, calls `Instant.now()` per item, and executes a prepared statement
per entry in one batch.

`memory-inmem/` and `memory-mem0/` do not override `storeAll()` — they use the SPI default
(`inputs.stream().map(this::store).toList()`), issuing N individual calls.

### JPA override

`JpaMemoryStore` overrides `storeAll()` with a single `@Transactional(REQUIRED)` method, aligned
with SQLite's approach:

```java
@Override
@Transactional(TxType.REQUIRED)
public List<String> storeAll(List<MemoryInput> inputs) {
    if (inputs.isEmpty()) return List.of();
    MemoryPermissions.assertTenant(inputs.getFirst().tenantId(), principal);
    var entries = inputs.stream().map(input -> {
        MemoryPermissions.assertTenant(input.tenantId(), principal);  // per-item guard
        MemoryEntry e = new MemoryEntry();
        e.memoryId   = UUID.randomUUID().toString();
        e.tenantId   = input.tenantId();
        e.entityId   = input.entityId();
        e.domain     = input.domain().name();
        e.caseId     = input.caseId();
        e.text       = input.text();
        e.attributes = serializeAttributes(input.attributes());
        e.createdAt  = Instant.now();  // per-item — consistent with SQLite
        return e;
    }).toList();
    MemoryEntry.persist(entries);
    return entries.stream().map(e -> e.memoryId).toList();
}
```

**Key properties:**
- Single `assertTenant` on item 0 before the loop, then per-item `assertTenant` inside the stream
  for mixed-tenant detection — throws `SecurityException` on the offending item, consistent with
  SQLite's behavior. No entries are persisted if any item fails.
- Per-entry `Instant.now()` — consistent with SQLite; avoids identical-timestamp ordering ambiguity
  in `createdAt DESC` queries.
- `MemoryEntry.persist(Iterable)` — all N inserts in one transaction. Hibernate JDBC batching
  activates automatically with `quarkus.hibernate-orm.jdbc.statement-batch-size` (consumer config).
- IDs returned in input order.

### Mixed-tenant contract (SPI-level)

Mixed-tenant input to `storeAll()` (`inputs[i].tenantId() != principal.tenancyId()`) throws
`SecurityException`. This is the SPI contract. Both JPA and SQLite now implement it this way.
No entries are persisted for the batch — the exception fires before or during the stream map,
and the transaction rolls back.

### Mem0 storeAll() — deferred

`Mem0CaseMemoryStore` has no `storeAll()` override. The SPI default issues N sequential REST
calls to the Mem0 server. The Mem0 OSS REST API (`POST /memories`) does not support batch
creation, so N round-trips is unavoidable without a client-side workaround. Mem0 batch
optimization is deferred; a follow-up issue will be filed.

### NoOpCaseMemoryStore storeAll()

`NoOpCaseMemoryStore` will get an explicit `storeAll()` override returning
`Collections.nCopies(inputs.size(), "")` — consistent with `store()` returning `""`, but
explicit rather than inherited from the SPI default. Callers should not use IDs returned from
the no-op for anything meaningful.

### ReactiveCaseMemoryStore.storeAll() and the bridge

`ReactiveCaseMemoryStore.storeAll()` default fires all `store()` calls concurrently via
`Uni.join().all(...).andFailFast()`. `BlockingToReactiveBridge` overrides this and delegates
to `delegate.storeAll(inputs)` — the optimized single-transaction path. The concurrent default
only applies to native reactive adapters implementing `ReactiveCaseMemoryStore` directly, and
none exist in the current codebase. If a native reactive adapter is added, it must override
`storeAll()` explicitly for correct sequential batch semantics.

---

## Tests

| Test | Assertion |
|------|-----------|
| `storeAll([])` | Returns `[]`, no DB interaction |
| `storeAll([a, b, c])` | Returns 3 distinct IDs in order; all 3 entries visible on `query()` within `@TestTransaction` |
| Mixed-tenant: principal=A, inputs=[{tenant:A}, {tenant:B}] | Throws `SecurityException` on item 1; 0 rows inserted |
| All-wrong-tenant: principal=A, inputs=[{tenant:B}] | Throws `SecurityException` on item 0; 0 rows inserted |
| `storeAll([a, b, c])` vs 3 individual `store()` calls | Both produce entries with identical fields (text, domain, entityId, caseId, attributes); timestamps and IDs differ |

---

## Coherence Review

**PLATFORM.md:** Capability ownership updated (emission note). Priority ladder clarified (non-alternative
resolution, reserved bands, multi-store note).

**Protocols:** `casehub-parent/docs/protocols/universal/persistence-backend-cdi-priority.md`
(existing file) — updated reference table with Tier 3 band.

**Consumers:** `memory-inmem/` priority change is invisible to consumers. Javadoc update removes
"under investigation." `storeAll()` override is additive with stricter multi-tenant semantics
(`SecurityException` vs default's per-store isolation). `NoOpCaseMemoryStore.storeAll()`
override is purely explicit.

**Deferred:** Mem0 `storeAll()` batch optimization — to be filed as a follow-up issue.

**Out of scope:** Multi-store composite adapter design, trust-routing implications of memory
storage failures, Quarkus request context propagation patterns for async memory emission.
