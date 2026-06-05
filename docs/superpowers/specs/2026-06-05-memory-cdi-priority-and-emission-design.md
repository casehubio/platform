# Memory CDI Priority and Emission Design

**Date:** 2026-06-05  
**Issues:** platform#39 (CDI priority conflict), platform#49 (CDI emission pattern + storeAll batch)  
**Branch:** issue-39-memory-cdi-and-priority

---

## Context

platform#39 was filed when `memory-mem0/` shipped — both `memory-inmem/` and production adapters
(sqlite, mem0) are `@Alternative @Priority(1)`, causing `AmbiguousResolutionException` in any
`@QuarkusTest` that has a production adapter on compile scope and `memory-inmem/` on test scope.

platform#49 tracked the emission pattern investigation opened by platform#48 (now closed). Three
options (A/B/C) were documented in the `CaseMemoryStore.store()` Javadoc pending consumer feedback.
The `storeAll()` default issues N individual transactions; no adapter overrides it.

---

## Section 1 — CDI Priority Fix (#39)

### Decision

`InMemoryMemoryStore` changes from `@Alternative @Priority(1)` to `@Alternative @Priority(10)`.

### Rationale

Production adapters (sqlite, mem0) sit at Priority(1) — Tier 2 of the persistence-backend-cdi
priority ladder. `memory-inmem/` is the test-override adapter: it must reliably win when colocated
with a production adapter on the test classpath. Priority(10) encodes that intent without ambiguity.

In production augmentation, `memory-inmem/` is test-scoped and invisible to CDI — no conflict
regardless of priority value.

Priority(1) for production adapters is preserved. If two production adapters appear simultaneously
(violating the "Do NOT combine" convention), `AmbiguousResolutionException` fires correctly at
startup — loud failure, not silent wrong-adapter selection.

### Priority ladder for CaseMemoryStore after this change

| Tier | Priority | Beans | When active |
|------|----------|-------|-------------|
| 0 — no-op default | `@DefaultBean` | `NoOpCaseMemoryStore` | No adapter on classpath |
| 1 — production SQL | `@ApplicationScoped` | `JpaMemoryStore` | `memory-jpa/` compile scope |
| 2 — production override | `@Alternative @Priority(1)` | `SqliteMemoryStore`, `Mem0CaseMemoryStore` | respective module compile scope |
| 3 — test override | `@Alternative @Priority(10)` | `InMemoryMemoryStore` | `memory-inmem/` test scope |

### Files changed

- `memory-inmem/.../InMemoryMemoryStore.java` — `@Priority(1)` → `@Priority(10)`
- `persistence-backend-cdi-priority.md` — reference table updated with new tier
- `CLAUDE.md` module table — `memory-inmem/` priority note updated

---

## Section 2 — Emission Pattern Decision (#49 Part 1)

### Decision

**Option A is canonical.** Direct injection of `CaseMemoryStore` and explicit `store()` calls
from application code. Platform ships no CDI emission infrastructure.

### Why not Option C (MemoryStoreRequest event + memory-cdi/ module)

1. **Exception propagation.** `store()` may throw `SecurityException` from
   `MemoryPermissions.assertTenant()`. With `Event.fireAsync()`, exceptions are invisible to
   the caller — a compliance-adjacent write silently succeeds from the app's perspective even
   if the store rejected it. Unacceptable.

2. **API inconsistency.** Apps already inject `CaseMemoryStore` for reads (`query()`). Making
   writes go through CDI events while reads are direct injection creates two call-site patterns
   for the same SPI.

3. **Threading loss.** `@ObservesAsync` moves the store call off the caller's thread. Apps lose
   control over transaction boundaries and observability.

4. **YAGNI.** The "multiple observers" extensibility argument is hypothetical. If platform-level
   fan-out is needed, it belongs in the adapter, not in a CDI relay layer.

### Javadoc update

`CaseMemoryStore.store()` Javadoc removes the three-option list and "under investigation" language.
Replacement:

> **Emission pattern:** inject `CaseMemoryStore` and call `store()` directly from your domain
> event observer. Use `@ObservesAsync` on the domain event for fire-and-continue semantics while
> keeping exceptions on a managed thread where they can be logged and acted on. CDI event
> indirection (firing a platform event that a platform module then stores) is not recommended —
> `fireAsync()` swallows `SecurityException` from `assertTenant()`, making compliance failures
> invisible to callers.

### PLATFORM.md update

Capability ownership entry for `CaseMemoryStore` gains: `emission: direct injection — see SPI Javadoc`.

---

## Section 3 — storeAll() Batch Override (#49 Part 2)

### Problem

`CaseMemoryStore.storeAll()` default streams inputs and calls `store()` for each. Each `store()`
call is `@Transactional(TxType.REQUIRED)`. Called from outside a transaction, this issues N
separate DB round-trips. For bulk emission paths (e.g. storing reviewer outcomes for a merge
queue batch), this is N × (network + commit) overhead.

### Solution

`JpaMemoryStore` overrides `storeAll()` with a single `@Transactional(TxType.REQUIRED)` method:

```java
@Override
@Transactional(TxType.REQUIRED)
public List<String> storeAll(List<MemoryInput> inputs) {
    if (inputs.isEmpty()) return List.of();
    MemoryPermissions.assertTenant(inputs.getFirst().tenantId(), principal);
    validateSameTenant(inputs);
    var now = Instant.now();
    var entries = inputs.stream().map(input -> {
        MemoryEntry e = new MemoryEntry();
        e.memoryId   = UUID.randomUUID().toString();
        e.tenantId   = input.tenantId();
        e.entityId   = input.entityId();
        e.domain     = input.domain().name();
        e.caseId     = input.caseId();
        e.text       = input.text();
        e.attributes = serializeAttributes(input.attributes());
        e.createdAt  = now;
        return e;
    }).toList();
    MemoryEntry.persist(entries);
    return entries.stream().map(e -> e.memoryId).toList();
}

private void validateSameTenant(List<MemoryInput> inputs) {
    String tenantId = inputs.getFirst().tenantId();
    for (MemoryInput input : inputs) {
        if (!tenantId.equals(input.tenantId())) {
            throw new IllegalArgumentException(
                "storeAll() requires all inputs to share the same tenantId");
        }
    }
}
```

**Key properties:**
- All N inserts in one transaction — atomic, one commit
- Client-side UUID generation — no DB round-trip per ID
- Single `assertTenant` + mixed-tenant guard before any persist — fail-fast, no partial writes
- Returns IDs in input order — callers can correlate by position
- Hibernate JDBC batching activates automatically via `quarkus.hibernate-orm.jdbc.statement-batch-size`
  (consumer's configuration concern)

### Tests

| Test | Assertion |
|------|-----------|
| `storeAll([])` | Returns empty list, no DB interaction |
| `storeAll([a, b, c])` | Returns 3 IDs in order; all 3 visible on re-query within `@TestTransaction` |
| `storeAll` with mixed tenants | Throws `IllegalArgumentException` before any persist; 0 rows inserted |
| `storeAll` vs N individual `store()` calls | Both produce identical entries (same fields, different IDs) |

---

## Coherence Review

**PLATFORM.md:** Capability ownership table updated (emission note). CDI priority ladder table
in `persistence-backend-cdi-priority.md` updated. No new module or artifact — no cross-repo
dependency map change needed.

**Protocols:** `persistence-backend-cdi-priority.md` is the only protocol affected.
`memory-inmem/` priority change is a single-annotation edit with no API surface impact.

**Consumers:** `memory-inmem/` priority change is invisible to consumers — they never reference
the priority value. The Javadoc update removes "under investigation" language; no consumer code
references the Javadoc. `storeAll()` override is additive (default existed; override is strictly
faster with identical semantics on the happy path, plus stricter multi-tenant guard).

**Open decisions deferred:** None. All decisions are complete.

**Out of scope (filed as issues if needed):** No new issues required — all three changes are
self-contained and unambiguous.
