# Design: Mem0 Parallel storeAll, ReactiveCaseMemoryStore → platform-api, Cross-Tenant Erasure

**Date:** 2026-06-17  
**Branch:** `issue-70-storeall-spi-gdpr`  
**Covers:** platform#70, platform#90, platform#99

---

## Overview

Three sequential changes on one branch:

1. **#70** — Mem0 + SQLite `storeAll()` bounded-parallel execution + 3-arg `assertTenant` pre-flight bug fix
2. **#90** — Move `ReactiveCaseMemoryStore` SPI from `casehub-platform` to `casehub-platform-api`; fix `UnsupportedOperationException` → `MemoryCapabilityException` in existing reactive defaults
3. **#99** — Add `eraseEntityAcrossTenants(entityId, tenantIds)` for GDPR Art.17 cross-tenancy data-subject erasure

Ordering is strict: #90 must complete before #99 so the new cross-tenant method lands directly in `platform-api`.

---

## #70 — storeAll() Parallel Batch

### Problem

`Mem0CaseMemoryStore.storeAll()` issues HTTP calls sequentially: O(N × RTT). The pre-flight uses the 2-arg `assertTenant` form — inconsistent with every other method which uses the 3-arg async-aware form (PP-20260529-57cc3b violation).

`SqliteMemoryStore.storeAll()` has the identical 2-arg pre-flight bug:
```java
inputs.forEach(i -> MemoryPermissions.assertTenant(i.tenantId(), principal));  // wrong — 2-arg
```
The per-item check inside the transaction loop is 3-arg (correct), making the pre-flight inconsistent. Both adapters are in scope for this fix. The parallel execution change is Mem0-only — SQLite `storeAll` wraps all inserts in a single JDBC transaction, so SQL-level atomicity already bounds the operation; parallelising individual inserts inside the same connection would gain nothing.

### Design

**Bug fix — Mem0:** Change pre-flight to `MemoryPermissions.assertTenant(i.tenantId(), principal, requestContextActive())`.

**Bug fix — SQLite:** Change pre-flight to `MemoryPermissions.assertTenant(i.tenantId(), principal, requestContextActive())`. No structural change to the JDBC transaction.

**Config addition** (`Mem0Config`):
```java
@WithDefault("4")
int storeAllConcurrency();
```

**New `Mem0CaseMemoryStore.storeAll()` implementation:**
```java
@Timed(value = "casehub.memory.mem0", histogram = true, extraTags = {"operation", "storeAll"})
@Override
public List<String> storeAll(List<MemoryInput> inputs) {
    if (inputs.isEmpty()) return List.of();
    inputs.forEach(i -> MemoryPermissions.assertTenant(i.tenantId(), principal, requestContextActive()));
    final int cap = Math.max(1, Math.min(config.storeAllConcurrency(), inputs.size()));
    final var sem = new Semaphore(cap);
    final var unis = inputs.stream()
        .map(i -> Uni.createFrom().callable(() -> {
            sem.acquireUninterruptibly();
            try { return sendAdd(i); }
            finally { sem.release(); }
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool()))
        .toList();
    return Uni.join().all(unis).andFailFast().await().indefinitely();
}
```

**Semaphore guard:** `Math.max(1, ...)` prevents `Semaphore(0)` if `storeAllConcurrency` is misconfigured to 0 or negative.

**Properties:**
- Pre-flight assertTenant for ALL inputs before any HTTP call fires (PP-20260605-e63850 REST atomicity contract)
- Results returned in input order (`Uni.join` preserves subscription order)
- `andFailFast()`: first HTTP error propagates; Unis not yet subscribed are skipped. **In-flight blocking HTTP calls already executing in the worker pool (`sendAdd()`) run to their natural completion** — blocking code does not respond to Mutiny cancellation. The guarantee is "no new calls start after the first failure," not "in-flight calls abort."
- Semaphore is per-call; no shared state between invocations
- `acquireUninterruptibly()` on worker threads is acceptable: threads block briefly at the semaphore, not during the HTTP call itself

### Tests (`Mem0CaseMemoryStoreTest`)

- `storeAll_fires_requests_concurrently` — stub tracks in-flight call count using a `CountDownLatch(cap)` gate: each call decrements the latch on entry and blocks until the latch releases; the latch releases when all `cap` calls are simultaneously in-flight. Use 2×cap inputs to ensure enough calls to fill the cap. Assert **`peak == cap`** (exact — the latch guarantees exactly cap simultaneous calls at release; `peak ≤ cap` would be vacuously satisfied and is incorrect).
- `storeAll_preserves_input_order` — stub returns distinct IDs per input; assert output matches input index order
- `storeAll_first_error_propagates` — stub throws on one item; assert whole call throws (note: other in-flight calls may complete before the failure is observed — test only verifies the exception propagates, not that zero other calls complete)
- `storeAll_uses_3arg_assertTenant` — simulate absent request context; assert pre-flight passes (async-aware bypass)
- `storeAll_concurrency_zero_uses_one` — set storeAllConcurrency to 0; assert no deadlock

---

## #90 — ReactiveCaseMemoryStore → platform-api

### Problem

`ReactiveCaseMemoryStore` lives in `casehub-platform` (the mock module) forcing production consumers (e.g., `casehub-engine` runtime `CaseHubReactor`) to take a compile-scope dependency on the mock module — violating PP-20260524-a8f597. The interface is pure Mutiny; Mutiny is explicitly acceptable in Tier 1 per PLATFORM.md.

Additionally, the two existing reactive default methods throw `UnsupportedOperationException` while their blocking counterparts throw `MemoryCapabilityException`. This inconsistency is fixed in the same commit as the move.

### Design

**`platform-api/pom.xml`** — add:
```xml
<dependency>
    <groupId>io.smallrye.reactive</groupId>
    <artifactId>mutiny</artifactId>
    <scope>provided</scope>
</dependency>
```

**Move:**
- Source: `platform/src/main/java/io/casehub/platform/memory/ReactiveCaseMemoryStore.java`
- Destination: `platform-api/src/main/java/io/casehub/platform/api/memory/ReactiveCaseMemoryStore.java`
- Package: `io.casehub.platform.memory` → `io.casehub.platform.api.memory`

**Fix existing inconsistencies while touching the file:**
```java
// Before (wrong):
default Uni<Integer> eraseEntity(String entityId, String tenantId) {
    return Uni.createFrom().failure(
        new UnsupportedOperationException("eraseEntity not supported by this adapter"));
}
default Uni<Void> eraseById(String memoryId, String entityId, String tenantId) {
    return Uni.createFrom().failure(
        new UnsupportedOperationException("eraseById not supported by this adapter"));
}

// After (correct — matches blocking SPI contract):
default Uni<Integer> eraseEntity(String entityId, String tenantId) {
    return Uni.createFrom().failure(
        new MemoryCapabilityException(MemoryCapability.ERASE_ENTITY, getClass()));
}
default Uni<Void> eraseById(String memoryId, String entityId, String tenantId) {
    return Uni.createFrom().failure(
        new MemoryCapabilityException(MemoryCapability.ERASE_BY_ID, getClass()));
}
```

**Updates in `casehub-platform`:**
- `BlockingToReactiveBridge` — update import
- Any test files referencing the old package

**Doc updates:**
- `CLAUDE.md` module table — move `ReactiveCaseMemoryStore SPI` entry from `platform/` to `platform-api/`
- `casehub-parent/docs/PLATFORM.md` — capability ownership row for memory: update `ReactiveCaseMemoryStore SPI` location to `casehub-platform-api`

**Downstream (not in this branch):**
- `engine#466` — `casehub-engine` runtime downgrades `casehub-platform` from compile to test scope

### Tests

**New: `ReactiveCaseMemoryStoreSpiTest`** (in `platform-api/src/test/java/io/casehub/platform/api/memory/`) — direct parallel of `CaseMemoryStoreSpiTest` for the blocking interface:

```java
class ReactiveCaseMemoryStoreSpiTest {
    static final MemoryDomain DOMAIN = new MemoryDomain("d");

    // Stubs for all three abstract methods; default methods under test are NOT overridden.
    // Compiler error on any omitted abstract method = it is abstract (RED state).
    // Compiles without implementing defaults = they are default (GREEN proves contract).
    private final ReactiveCaseMemoryStore sut = new ReactiveCaseMemoryStore() {
        @Override public Uni<String> store(MemoryInput i) { return Uni.createFrom().item("mem-1"); }
        @Override public Uni<List<Memory>> query(MemoryQuery q) { return Uni.createFrom().item(List.of()); }
        @Override public Uni<Integer> erase(EraseRequest r) { return Uni.createFrom().item(0); }
    };

    @Test void storeAll_delegates_to_store() {
        var a = new MemoryInput("e1", DOMAIN, "t1", null, "a", Map.of());
        var b = new MemoryInput("e1", DOMAIN, "t1", null, "b", Map.of());
        assertEquals(List.of("mem-1", "mem-1"),
            sut.storeAll(List.of(a, b)).await().indefinitely());
    }
    @Test void eraseEntity_default_fails_with_MemoryCapabilityException() {
        final var ex = assertThrows(MemoryCapabilityException.class,
            () -> sut.eraseEntity("e", "t").await().indefinitely());
        assertEquals(MemoryCapability.ERASE_ENTITY, ex.required());
    }
    @Test void eraseById_default_fails_with_MemoryCapabilityException() {
        final var ex = assertThrows(MemoryCapabilityException.class,
            () -> sut.eraseById("id", "e", "t").await().indefinitely());
        assertEquals(MemoryCapability.ERASE_BY_ID, ex.required());
    }
    @Test void eraseEntityAcrossTenants_default_fails_with_MemoryCapabilityException() {
        final var ex = assertThrows(MemoryCapabilityException.class,
            () -> sut.eraseEntityAcrossTenants("e", Set.of("t")).await().indefinitely());
        assertEquals(MemoryCapability.CROSS_TENANT_ERASE, ex.required());
    }
}
```

The claim "existing reactive bridge tests for eraseEntity and eraseById verify MemoryCapabilityException propagation" is false — `BlockingToReactiveBridgeThreadingTest` uses a spy that overrides both methods to succeed (capture thread ID, return 0). No existing test covers the reactive default exception type, making this new test file mandatory coverage for the fix.

---

## #99 — eraseEntityAcrossTenants (GDPR Art.17)

### Problem

`CaseMemoryStore.eraseEntity(entityId, tenantId)` is per-tenant. GDPR Art.17 data-subject erasure applies across all tenancies. Currently callers must loop — but `assertTenant()` rejects cross-tenant calls, making this impossible through the existing API without bypassing the SPI security model. A platform method is needed that (a) gates on admin privilege, (b) bypasses per-tenant `assertTenant` internally, and (c) allows JDBC adapters to issue a single optimized query.

### Design

**`MemoryCapability`** — new value:
```java
CROSS_TENANT_ERASE,  // eraseEntityAcrossTenants() — GDPR Art.17 across all supplied tenantIds
```

**`MemoryPermissions`** — new static method:
```java
/**
 * Requires cross-tenant admin privilege. No async bypass form — this check must always
 * enforce. Cross-tenant GDPR erasure is a deliberate administrative operation never
 * initiated from @ObservesAsync context; unconditional enforcement is correct and
 * required. Capturing the principal before entering any reactive pipeline is the caller's
 * responsibility, as with all @RequestScoped beans.
 */
public static void assertCrossTenantAdmin(CurrentPrincipal principal) {
    if (!principal.isCrossTenantAdmin())
        throw new SecurityException(
            "Cross-tenant erasure requires cross-tenant admin privilege; actor=" + principal.actorId());
}
```

Note: this introduces a new pattern — a `MemoryPermissions` check with no async bypass form. The existing `assertTenant` accommodates `@ObservesAsync` callers via the 3-arg form; `assertCrossTenantAdmin` deliberately does not. This pattern should be captured as a protocol entry during implementation (file in `casehubio/garden`): "Privileged administrative operations must not bypass their security gate in async context — only unauthenticated async-hop operations qualify for the async bypass."

**`CaseMemoryStore`** — new default-throw method:
```java
/**
 * GDPR Art.17 full-entity wipe across all supplied tenantIds.
 * Caller must be a cross-tenant admin. Supply the complete set of tenantIds
 * for the data subject from the tenant management system.
 *
 * <p>Adapters MUST call {@link MemoryPermissions#assertCrossTenantAdmin} before
 * delegating to the backend. Do NOT call eraseEntity() internally — assertTenant()
 * rejects cross-tenant access. Implement deletion directly against the backend.
 *
 * <p>Default throws {@link MemoryCapabilityException}. {@link NoOpCaseMemoryStore}
 * overrides with {@code return 0} — nothing stored → erasure trivially satisfied —
 * but does NOT declare {@link MemoryCapability#CROSS_TENANT_ERASE} in capabilities().
 *
 * @param tenantIds the set of tenantIds to erase from; caller supplies from tenant management.
 *                  Set semantics are enforced at the type level — duplicates are impossible.
 * @return total count of records erased across all tenantIds (best-effort for REST adapters)
 */
default int eraseEntityAcrossTenants(String entityId, Set<String> tenantIds) {
    throw new MemoryCapabilityException(MemoryCapability.CROSS_TENANT_ERASE, getClass());
}
```

**`ReactiveCaseMemoryStore`** — mirror (in `platform-api` after #90):
```java
default Uni<Integer> eraseEntityAcrossTenants(String entityId, Set<String> tenantIds) {
    return Uni.createFrom().failure(
        new MemoryCapabilityException(MemoryCapability.CROSS_TENANT_ERASE, getClass()));
}
```

**`BlockingToReactiveBridge`** — bridge:
```java
@Override
public Uni<Integer> eraseEntityAcrossTenants(String entityId, Set<String> tenantIds) {
    return Uni.createFrom().item(() -> delegate.eraseEntityAcrossTenants(entityId, tenantIds))
        .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
}
```

### Adapter Implementations

**`NoOpCaseMemoryStore`:**
```java
@Override
public int eraseEntityAcrossTenants(String entityId, Set<String> tenantIds) { return 0; }
// capabilities(): stays Set.of() — NoOp invariant: Set.of() yet erase methods do not throw.
// CROSS_TENANT_ERASE is NOT added. Callers checking requireCapability(CROSS_TENANT_ERASE)
// will get MemoryCapabilityException even against NoOp — consistent with real adapters
// needing explicit implementation. NoOp override exists only to avoid the default throw.
// Note: no assertCrossTenantAdmin call — NoOpCaseMemoryStore has no CurrentPrincipal injection
// and guards no real data, matching the same omission in eraseEntity().
```

**`NoOpCaseMemoryStoreTest`** — add two tests following the established blocking + reactive bridge pattern (every interface method has both):
```java
@Test void eraseEntityAcrossTenants_returns_zero() {
    assertEquals(0, store.eraseEntityAcrossTenants("entity-1", Set.of("tenant-1")));
}
@Test void bridge_eraseEntityAcrossTenants_returns_zero() {
    assertEquals(0, reactiveStore.eraseEntityAcrossTenants("entity-1", Set.of("tenant-1"))
        .await().indefinitely());
}
```

**`InMemoryMemoryStore`** — single-pass scan across supplied tenantIds:
```java
@Override
public int eraseEntityAcrossTenants(String entityId, Set<String> tenantIds) {
    MemoryPermissions.assertCrossTenantAdmin(principal);
    var count = new AtomicInteger();
    store.entrySet().removeIf(e -> {
        if (tenantIds.contains(e.getKey().tenantId()) && e.getKey().entityId().equals(entityId)) {
            count.addAndGet(e.getValue().size());
            return true;
        }
        return false;
    });
    return count.get();
}
// capabilities(): add CROSS_TENANT_ERASE
```

**`JpaMemoryStore`** — single-transaction DELETE IN:
```java
@Override
@Transactional(TxType.REQUIRED)
public int eraseEntityAcrossTenants(String entityId, Set<String> tenantIds) {
    MemoryPermissions.assertCrossTenantAdmin(principal);
    if (tenantIds.isEmpty()) return 0;
    int count = em.createQuery(
            "DELETE FROM MemoryEntry WHERE entityId = :entityId AND tenantId IN :tenantIds")
        .setParameter("entityId", entityId)
        .setParameter("tenantIds", List.copyOf(tenantIds))
        .executeUpdate();
    em.clear();
    return count;
}
// capabilities(): add CROSS_TENANT_ERASE
// Note: PostgreSQL bind parameter limit is 32767 (Short.MAX_VALUE).
// Deployments with >10k tenantIds should consider chunked iteration.
// Normal multi-tenant deployments (hundreds of tenants) are well within this limit.
```

**`SqliteMemoryStore`** — chunked DELETE IN (SQLite `SQLITE_LIMIT_VARIABLE_NUMBER` default = 999):
```java
private static final int SQLITE_IN_CHUNK = 500;  // well below 999 limit

@Override
public int eraseEntityAcrossTenants(String entityId, Set<String> tenantIds) {
    MemoryPermissions.assertCrossTenantAdmin(principal);
    if (tenantIds.isEmpty()) return 0;
    var tenantList = new ArrayList<>(tenantIds);  // Set → List for subList() chunking
    int total = 0;
    for (int offset = 0; offset < tenantList.size(); offset += SQLITE_IN_CHUNK) {
        var chunk = tenantList.subList(offset, Math.min(offset + SQLITE_IN_CHUNK, tenantList.size()));
        total += deleteChunk(entityId, chunk);
    }
    return total;
}

private int deleteChunk(String entityId, List<String> tenantChunk) {
    String placeholders = tenantChunk.stream().map(_ -> "?").collect(Collectors.joining(", "));
    String sql = "DELETE FROM memory_entry WHERE entity_id = ? AND tenant_id IN (" + placeholders + ")";
    try (Connection conn = dataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, entityId);
        int idx = 2;
        for (String t : tenantChunk) ps.setString(idx++, t);
        return ps.executeUpdate();
    } catch (SQLException e) {
        throw new IllegalStateException("eraseEntityAcrossTenants() failed", e);
    }
}
// capabilities(): add CROSS_TENANT_ERASE
// Retry-is-safe: each deleteChunk opens its own connection (auto-commit). A failure at chunk N
// leaves chunks 0..N-1 permanently erased. On retry, DELETE against already-absent rows is a
// no-op (returns 0 rows affected) — the method converges from where it failed.
```

**`Mem0CaseMemoryStore`** — iterate tenantIds, call Mem0 directly. Sequential by design:
```java
@Timed(value = "casehub.memory.mem0", histogram = true, extraTags = {"operation", "eraseEntityAcrossTenants"})
@Override
public int eraseEntityAcrossTenants(String entityId, Set<String> tenantIds) {
    MemoryPermissions.assertCrossTenantAdmin(principal);
    int total = 0;
    for (String tenantId : tenantIds) {
        final String userId = compoundUserId(tenantId, entityId);
        try {
            final Mem0ListResponse listed = client.list(userId, null, null);
            total += listed.results() != null ? listed.results().size() : 0;
            client.deleteAll(userId, null, null);
        } catch (WebApplicationException e) {
            throw toStoreException(e);
        }
    }
    return total;
}
// capabilities(): add CROSS_TENANT_ERASE
// Race caveat identical to eraseEntity(): count is best-effort.
// Sequential: simplicity + retry-is-safe. deleteAll is idempotent — already-erased tenants
// return an empty list on retry, so re-invoking after a partial failure is safe and converges.
// Parallel with error-collection semantics would require a MultiEraseException type and
// materially more code for no architectural benefit. GDPR Art.17 mandates "without undue
// delay" (typically 30 days), not sub-second execution; occasional retries are compliant.
```

**`GraphitiCaseMemoryStore`** — known-domains × tenantIds, sequential:
```java
@Timed(value = "casehub.memory.graphiti", histogram = true, extraTags = {"operation", "eraseEntityAcrossTenants"})
@Override
public int eraseEntityAcrossTenants(String entityId, Set<String> tenantIds) {
    MemoryPermissions.assertCrossTenantAdmin(principal);
    final List<String> domains = config.knownDomains().orElse(List.of());
    if (domains.isEmpty())
        throw new MemoryCapabilityException(CROSS_TENANT_ERASE, getClass());
    int total = 0;
    for (String tenantId : tenantIds)
        for (String domain : domains)
            total += eraseGroup(compoundGroupId(tenantId, entityId, domain));
    return total;
}
// capabilities(): add CROSS_TENANT_ERASE only when knownDomains non-empty (same gate as ERASE_ENTITY)
// Sequential: same reasoning as Mem0 — simplicity + retry-is-safe. eraseGroup handles 404
// as a no-op, so already-erased groups are skipped safely on retry. Consistent with
// Graphiti's existing sequential storeAll.
```

### Tests

**`CaseMemoryStoreSpiTest`:**
```java
@Test void eraseEntityAcrossTenants_default_throws_MemoryCapabilityException() {
    final var ex = assertThrows(MemoryCapabilityException.class,
        () -> sut.eraseEntityAcrossTenants("entity-1", Set.of("tenant-1")));
    assertEquals(MemoryCapability.CROSS_TENANT_ERASE, ex.required());
}
```

**`BlockingToReactiveBridgeThreadingTest`** — add 7th test (extends spy to override `eraseEntityAcrossTenants`):
```java
@Test
void eraseEntityAcrossTenants_executes_delegate_on_worker_thread() {
    var capturedId = new AtomicLong(Thread.currentThread().getId());
    bridgeWith(capturedId).eraseEntityAcrossTenants("entity-1", Set.of(TENANT))
        .await().indefinitely();
    assertNotEquals(Thread.currentThread().getId(), capturedId.get(),
        "eraseEntityAcrossTenants() must offload delegate to a worker thread, not run on the subscribing thread");
}
```
The `bridgeWith(capturedId)` spy must be extended to override `eraseEntityAcrossTenants(String, Set<String>)`, capturing the thread ID and returning 0.

**`MemoryPermissionsTest`:**

Add a second helper alongside the existing `principal(tenancyId)` — the existing helper always returns `isCrossTenantAdmin() = false` and cannot supply the passing case:
```java
private static CurrentPrincipal crossTenantAdminPrincipal() {
    return new CurrentPrincipal() {
        @Override public String actorId()             { return "admin"; }
        @Override public Set<String> groups()         { return Set.of(); }
        @Override public String tenancyId()           { return "platform"; }
        @Override public boolean isCrossTenantAdmin() { return true; }
    };
}
```

Then the two new tests:
```java
@Test void assertCrossTenantAdmin_throws_SecurityException_when_not_admin() {
    SecurityException ex = assertThrows(SecurityException.class,
        () -> MemoryPermissions.assertCrossTenantAdmin(principal("t")));
    assertTrue(ex.getMessage().contains("actor"));  // actorId from principal("t") = "actor"
}
@Test void assertCrossTenantAdmin_passes_when_is_cross_tenant_admin() {
    assertDoesNotThrow(() ->
        MemoryPermissions.assertCrossTenantAdmin(crossTenantAdminPrincipal()));
}
```

**`CaseMemoryStoreContractTest`** — security contract only (in the base suite):

```java
@Test
void eraseEntityAcrossTenants_throws_SecurityException_when_not_cross_tenant_admin() {
    // Default principal is NOT a cross-tenant admin — assertCrossTenantAdmin must reject.
    assertThrows(SecurityException.class,
        () -> store().eraseEntityAcrossTenants("entity-1", Set.of(TENANT)));
}
```

The happy-path cross-tenant data setup test (storing under multiple tenants and verifying deletion) **cannot live in the contract suite** — the existing `input(entityId, text)` factory hardcodes `TENANT`, and calling `store().store(inputForTenant("entity-2"))` would throw `SecurityException` because the principal's tenancyId is fixed to `TENANT`. Multi-tenant data setup requires principal switching which is adapter-specific CDI or constructor-injection setup not available at the abstract contract level. Happy-path tests are placed in each adapter's own test class where the full principal context is controllable.

**Per-adapter happy-path tests** (each adapter's test class):
- Store memories under `TENANT` and `OTHER_TENANT` (using adapter-specific principal configuration to switch between them during setup)
- Call `eraseEntityAcrossTenants(entityId, Set.of(TENANT, OTHER_TENANT))` using a cross-tenant-admin principal
- Verify count ≥ 2 and both tenant's data is gone

---

## Doc Updates

Required documentation changes beyond CLAUDE.md and PLATFORM.md (already listed in #90):

**`ARC42STORIES.MD` — §12 Risks and Technical Debt:**
Remove the following row — resolved by this branch:
```
| Mem0 storeAll batch (#70) — N sequential REST calls for a batch | Low — batch infrequent today; deferred pending Mem0 OSS batch endpoint | Workaround: call `store()` in a loop; no atomicity guarantee across REST calls |
```

**`ARC42STORIES.MD` — §8 L6 (Memory Adapters):**

1. Update the `**Participates in chapters:**` line to include the new chapter for this branch (to be assigned at commit time).

2. Update the "Not closed here" note (currently: "Mem0 `storeAll()` batch (#70 — deferred pending upstream batch endpoint; #69 superseded)") — remove the `#70` reference since it ships in this chapter.

3. Add a new chapter block documenting what this branch adds:
   - `#70` — `Mem0CaseMemoryStore.storeAll()` bounded-parallel execution (Semaphore + Mutiny, configurable cap); 3-arg assertTenant pre-flight bug fixed in Mem0 + SQLite
   - `#90` — `ReactiveCaseMemoryStore` moved from `casehub-platform` to `casehub-platform-api`; reactive defaults fixed to throw `MemoryCapabilityException`
   - `#99` — `eraseEntityAcrossTenants(entityId, Set<String>)` added to `CaseMemoryStore` and `ReactiveCaseMemoryStore`; `assertCrossTenantAdmin` in `MemoryPermissions`; JDBC adapters use single-query optimisation; REST adapters sequential with retry-is-safe

4. Update the **Issues** line to add `#70, #90, #99`.

**`ARC42STORIES.MD` — §13 Glossary:**
Add entry for `eraseEntityAcrossTenants()`:
```
| `eraseEntityAcrossTenants()` | GDPR Art.17 full-entity wipe across all supplied tenantIds. Requires `isCrossTenantAdmin()`. Caller supplies the complete set of tenantIds from the tenant management system. Distinct from `eraseEntity()` which is per-tenant. Default throws `MemoryCapabilityException(CROSS_TENANT_ERASE)`. |
```

Update the `MemoryPermissions` glossary entry to mention `assertCrossTenantAdmin(principal)`.

---

## Protocol References

- PP-20260605-e63850 — storeAll REST atomicity (pre-flight before any HTTP call)
- PP-20260529-57cc3b — assertTenant as first statement, 3-arg form
- PP-20260522-platform-api-scope — Mutiny SPI qualifies for Tier 1 (platform-api)
- PP-20260524-a8f597 — library modules use test scope for casehub-platform
- PP-20260520-439daf — no conditional tenancy filtering (cross-tenant erase is a deliberate privileged operation, not a bypass)
- **New protocol to file during implementation:** "Privileged administrative operations must not bypass their security gate in async context — the 3-arg `assertTenant` async bypass applies only to unauthenticated async-hop operations, not to privilege checks like `assertCrossTenantAdmin`."
