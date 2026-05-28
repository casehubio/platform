# CaseMemoryStore — Design Spec

**Date:** 2026-05-27  
**Issue:** casehubio/platform#27  
**Replaces:** `docs/specs/case-memory-store.md` (earlier research spec — retained as backend evaluation reference)

---

## 1. What This Is

`CaseMemoryStore` is a queryable, permission-aware, persistent memory SPI for the CaseHub
platform. It solves the cold-start problem: every CaseHub case currently begins with no
knowledge of facts established in prior cases. `CaseMemoryStore` is the layer that bridges
case-to-case knowledge — what the platform *knows* about entities, as distinct from the
ledger's record of what *happened*.

It is a general platform primitive, not a feature of any specific consumer. All CaseHub
consumers (devtown, clinical, aml, life) benefit. Consumer adoption issues are pre-filed:
devtown#43, clinical#33, aml#32.

### Three-Store Comparison

| Store | Purpose | Mutability | Consumer |
|-------|---------|------------|----------|
| Ledger | Tamper-evident record of events | Append-only | All (Level 4+) |
| **CaseMemoryStore** | Queryable record of what we know | Append + erase | All (Level 1+) |
| ChannelContextWindow | Active session context for LLM injection | Ephemeral | OpenClaw only |

`store()` is **append-only** at the SPI level. Adapters may apply backend-specific
deduplication (Mem0 and Graphiti both have their own merge logic), but the SPI contract
makes no guarantee about it. "Update" is not a supported SPI operation — correction is
done by erasing the wrong memory and storing the correct one.

---

## 2. Module Layout

The SPI and no-ops live in `casehub-platform`. Adapters live in a separate `casehub-memory`
repo — the same decision that gave `casehub-work` and `casehub-ledger` their own repos.
The capability is independently useful (any Quarkus multi-agent system needs agent memory,
not just CaseHub deployments), the adapters are operationally substantial (REST clients,
external service configuration, retry logic), and starting separately avoids the
extraction cost that `casehub-eidos` paid later.

The no-ops stay in `casehub-platform/platform/` regardless — the SPI owner ships the
no-op. Consumers who never use memory add nothing; `casehub-memory` is purely opt-in.

```
casehub-platform/
  platform-api/        ← CaseMemoryStore SPI + all value types (zero-dep, Tier 1)
  platform/            ← @DefaultBean no-op + BlockingToReactiveBridge + ReactiveCaseMemoryStore
  persistence-jpa/
  persistence-mongodb/
  ...

casehub-memory/        ← separate repo (casehubio/memory)
  memory-memori/       ← Tier 1 adapter: Memori REST client (@ApplicationScoped)
  memory-mem0/         ← Tier 2 adapter: Mem0 REST client (@Alternative @Priority(1))
  memory-graphiti/     ← Tier 3 adapter: Graphiti REST client (@Alternative @Priority(2))
```

Artifact IDs: `casehub-memory-memori`, `casehub-memory-mem0`, `casehub-memory-graphiti`.  
Folder naming follows `maven-submodule-folder-naming.md` (short names, no repo prefix).

### CDI Priority Ladder

| Tier | Module | Interface | Annotation | Active when |
|------|--------|-----------|-----------|-------------|
| 0 — no-op | `platform/` | `CaseMemoryStore` | `@DefaultBean @ApplicationScoped` | No adapter on classpath |
| — bridge | `platform/` | `ReactiveCaseMemoryStore` | `@DefaultBean @ApplicationScoped` | Always (wraps the active blocking impl) |
| 1 — Memori | `memory-memori/` | `CaseMemoryStore` | `@ApplicationScoped` | Memori dep declared |
| 2 — Mem0 | `memory-mem0/` | `CaseMemoryStore` | `@Alternative @Priority(1)` | Mem0 dep declared |
| 3 — Graphiti | `memory-graphiti/` | `CaseMemoryStore` or `ReactiveCaseMemoryStore` | `@Alternative @Priority(2)` | Graphiti dep declared |

Only one blocking adapter is active at runtime. Reactive callers always use the bridge
unless a native reactive adapter (e.g. Graphiti) overrides it at a higher priority.

`@Priority(2)` for Graphiti is a deliberate extension of the standard three-tier ladder
defined in `persistence-backend-cdi-priority.md`. That protocol reserves the decision to
the SPI owner — casehub-platform is the SPI owner here. `@Priority(1)` cannot be used
for both Mem0 and Graphiti; the values must be distinct for ARC to resolve unambiguously.

---

## 3. Value Types (`platform-api`)

Package: `io.casehub.platform.api.memory`

### `MemoryDomain`

```java
public record MemoryDomain(String name) {
    public MemoryDomain {
        Objects.requireNonNull(name, "domain name must not be null");
        if (name.isBlank()) throw new IllegalArgumentException("domain name must not be blank");
    }
}
```

Domain names are consumer-defined. Domain isolation is **strict equality**: a memory stored
with domain `X` is only recalled by a query with domain `X`. No ancestor-of semantics.
Domain tags are set at store time and are immutable.

### `MemoryPermissions`

Static utility in `platform-api` — callable from both blocking and reactive adapters.
`CaseMemoryStore.assertTenant()` delegates here; native reactive adapters (e.g. Graphiti,
which implements `ReactiveCaseMemoryStore` directly and never touches `CaseMemoryStore`)
call this directly.

```java
public final class MemoryPermissions {
    private MemoryPermissions() {}

    public static void assertTenant(String tenantId, CurrentPrincipal principal) {
        if (!principal.tenancyId().equals(tenantId))
            throw new SecurityException(
                "Tenant ID mismatch: claimed=" + tenantId
                + ", authenticated=" + principal.tenancyId());
    }
}
```

`CaseMemoryStore.assertTenant()` becomes:

```java
default void assertTenant(String tenantId, CurrentPrincipal principal) {
    MemoryPermissions.assertTenant(tenantId, principal);
}
```

---

### `MemoryInput` (input to `store()`)

```java
public record MemoryInput(
    String entityId,
    MemoryDomain domain,
    String tenantId,               // MUST derive from CurrentPrincipal.tenancyId()
    String caseId,                 // nullable — null for case-independent memories
    String text,                   // human-readable statement; used for semantic indexing
    Map<String, String> attributes // structured metadata: category, confidence, source, etc.
) {
    public MemoryInput {
        Objects.requireNonNull(entityId,  "entityId required");
        Objects.requireNonNull(domain,    "domain required");
        Objects.requireNonNull(tenantId,  "tenantId required");
        Objects.requireNonNull(text,      "text required");
        attributes = Map.copyOf(attributes); // defensive copy; rejects null values
    }
}
```

### `Memory` (output from `store()` and `query()`)

```java
public record Memory(
    String memoryId,               // assigned by store impl; returned by store()
    String entityId,
    MemoryDomain domain,
    String tenantId,
    String caseId,
    String text,
    Map<String, String> attributes,
    Instant createdAt              // assigned by store impl
) {
    public Memory {
        attributes = Map.copyOf(attributes); // defensive copy — adapters may pass mutable maps
    }
}
```

`updatedAt` is intentionally absent — `store()` is append-only; there is no update
operation in v1. If `update()` is added later, `updatedAt` will be added to `Memory` then.
The mechanical cost of that addition is low given no external consumers at that point.

`MemoryInput`/`Memory` are separate types: callers never construct a `Memory` directly;
the store assigns `memoryId` and `createdAt`. This keeps inputs null-free.

### `MemoryQuery`

```java
public record MemoryQuery(
    String entityId,               // required — entity to recall memories about
    MemoryDomain domain,           // required — strict equality enforced by adapter
    String tenantId,               // required — MUST derive from CurrentPrincipal.tenancyId()
    String caseId,                 // nullable — null matches any case
    String question,               // nullable — semantic search query
    int limit,                     // must be >= 1; adapters enforce
    Instant since                  // nullable — only memories stored after this instant
) {
    public MemoryQuery {
        Objects.requireNonNull(entityId, "entityId required");
        Objects.requireNonNull(domain,   "domain required");
        Objects.requireNonNull(tenantId, "tenantId required");
        if (limit < 1) throw new IllegalArgumentException("limit must be >= 1");
    }
}
```

**`question` semantics for non-semantic adapters:** when `question` is non-null but the
adapter does not support semantic search, the adapter MUST ignore `question` and return
entity+domain+tenant+caseId-scoped results ordered by `createdAt` descending. Callers
must not rely on relevance ranking from non-semantic adapters.

**`since` asymmetry is deliberate:** `since` covers the primary use case (recall recent
memories after a given point). `until` (bounded range queries) is deferred — not an
oversight.

### `EraseRequest`

```java
public record EraseRequest(
    String entityId,               // required
    MemoryDomain domain,           // nullable — null erases across ALL domains (GDPR Art.17)
    String tenantId,               // required
    String caseId                  // nullable — null matches any case
) {
    public EraseRequest {
        Objects.requireNonNull(entityId, "entityId required");
        Objects.requireNonNull(tenantId, "tenantId required");
    }
}
```

---

## 4. SPI Interfaces

### `CaseMemoryStore` — blocking, in `platform-api/`

```java
package io.casehub.platform.api.memory;

public interface CaseMemoryStore {

    /**
     * Store a memory about an entity. Returns the assigned memoryId.
     *
     * <p>Append-only at the SPI level. Adapters may apply backend-specific deduplication
     * but the SPI makes no guarantee. The no-op returns {@code ""}.
     *
     * <p>{@code input.tenantId()} MUST derive from {@code CurrentPrincipal.tenancyId()}.
     * Adapters MUST call {@link #assertTenant} before delegating to the backend.
     */
    String store(MemoryInput input);

    /**
     * Recall memories relevant to a query context.
     *
     * <p>Domain isolation is strict equality: only memories tagged with
     * {@code query.domain()} are returned. Non-semantic adapters ignore {@code question}
     * and return entity+domain+tenant+caseId-scoped results ordered by createdAt desc.
     * Returns an empty list when no adapter is installed.
     */
    List<Memory> query(MemoryQuery query);

    /**
     * Erase memories matching the request.
     *
     * <p>GDPR Art.17: {@code request.domain() == null} erases across ALL domains
     * for the entity within the tenant. Adapters MUST perform hard deletion.
     * Adapters MUST call {@link #assertTenant} before delegating to the backend.
     */
    void erase(EraseRequest request);

    /**
     * Erase a specific memory by its assigned memoryId.
     *
     * <p>For operational correction of wrong or stale memories. Requires the memoryId
     * returned by {@link #store}. Adapters MUST call {@link #assertTenant} before
     * delegating to the backend.
     *
     * <p>The default throws {@link UnsupportedOperationException} — a silent no-op on
     * a GDPR-adjacent erasure would return a false success signal. {@code NoOpCaseMemoryStore}
     * overrides with a true no-op (nothing was stored, so nothing can be erased).
     * Real adapters override with actual deletion.
     */
    default void eraseById(String memoryId, String tenantId) {
        throw new UnsupportedOperationException("eraseById not supported by this adapter");
    }

    /**
     * Convenience bulk store. Returns assigned memoryIds in input order.
     * Adapters may override for efficiency.
     */
    default List<String> storeAll(List<MemoryInput> inputs) {
        return inputs.stream().map(this::store).toList();
    }

    /**
     * Security guard: asserts that the claimed tenantId matches the authenticated principal.
     *
     * <p>Adapters MUST call this at the top of every store(), query(), and erase()
     * implementation. In reactive contexts, capture {@code CurrentPrincipal} before
     * entering the {@code Uni} pipeline — the request scope is not guaranteed on the
     * executor thread.
     *
     * @throws SecurityException if tenantId does not match principal.tenancyId()
     */
    default void assertTenant(String tenantId, CurrentPrincipal principal) {
        MemoryPermissions.assertTenant(tenantId, principal);
    }
}
```

### `ReactiveCaseMemoryStore` — reactive, in `platform/`

Lives in `platform/` because `Uni<>` (Smallrye Mutiny) cannot be in `platform-api/`
(zero-dep, Tier 1). `platform/pom.xml` must add `quarkus-mutiny` as a dependency.

```java
package io.casehub.platform.memory;

public interface ReactiveCaseMemoryStore {
    Uni<String> store(MemoryInput input);
    Uni<List<Memory>> query(MemoryQuery query);
    Uni<Void> erase(EraseRequest request);
    default Uni<Void> eraseById(String memoryId, String tenantId) {
        return Uni.createFrom().failure(
            new UnsupportedOperationException("eraseById not supported by this adapter"));
    }
}
```

`ReactiveCaseMemoryStore` does not extend `CaseMemoryStore`. No inheritance between
blocking and reactive.

---

## 5. No-Op Default and Reactive Bridge (`platform/`)

### `NoOpCaseMemoryStore`

`@DefaultBean @ApplicationScoped` — displaced automatically when any real adapter is
on the classpath.

```java
@DefaultBean @ApplicationScoped
public class NoOpCaseMemoryStore implements CaseMemoryStore {
    @Override public String store(MemoryInput input) { return ""; }
    @Override public List<Memory> query(MemoryQuery query) { return List.of(); }
    @Override public void erase(EraseRequest request) {}
    @Override public void eraseById(String memoryId, String tenantId) {}
}
```

### `BlockingToReactiveBridge`

`@DefaultBean @ApplicationScoped` — wraps whichever `CaseMemoryStore` is active (no-op
or a real blocking adapter). This is the default reactive path for all blocking adapters
(Memori, Mem0). Native async adapters that benefit from true non-blocking execution
(e.g. Graphiti with async graph traversal) may provide their own `@Alternative @Priority(N)`
`ReactiveCaseMemoryStore` to bypass the bridge entirely — the bridge's `@DefaultBean`
yields automatically.

Bridge methods wrap blocking delegate calls. Quarkus's `ExecutionModelAnnotationsProcessor`
hard-rejects `@Blocking` on plain `@ApplicationScoped` CDI bean methods returning `Uni`
— it is only valid on framework entrypoints (JAX-RS, reactive messaging, etc.).
Thread dispatch is the responsibility of the entrypoint calling the bridge (e.g. a
`@Blocking` JAX-RS resource method). Real adapters that need true non-blocking execution
should implement `ReactiveCaseMemoryStore` directly as `@Alternative @Priority(N)`.

```java
@DefaultBean @ApplicationScoped
public class BlockingToReactiveBridge implements ReactiveCaseMemoryStore {

    @Inject CaseMemoryStore delegate;

    @Override
    public Uni<String> store(MemoryInput input) {
        return Uni.createFrom().item(() -> delegate.store(input));
    }

    @Override
    public Uni<List<Memory>> query(MemoryQuery query) {
        return Uni.createFrom().item(() -> delegate.query(query));
    }

    @Override
    public Uni<Void> erase(EraseRequest request) {
        return Uni.createFrom().voidItem().invoke(() -> delegate.erase(request));
    }

    @Override
    public Uni<Void> eraseById(String memoryId, String tenantId) {
        return Uni.createFrom().voidItem()
            .invoke(() -> delegate.eraseById(memoryId, tenantId));
    }
}
```

`NoOpReactiveCaseMemoryStore` is eliminated — the bridge handles reactive no-op
behaviour by delegating to `NoOpCaseMemoryStore`.

---

## 6. Tests

### `CaseMemoryStoreSpiTest` — `platform-api/test/`

Proves `storeAll()`, `eraseById()`, and `assertTenant()` are genuinely `default`.
Anonymous impl pattern per `spi-default-method-contract-test.md`:

```java
class CaseMemoryStoreSpiTest {

    // Compiles without implementing any default method — proves they are default.
    // A compiler error on any omitted method is the RED state.
    final CaseMemoryStore sut = new CaseMemoryStore() {
        @Override public String store(MemoryInput i) { return "mem-1"; }
        @Override public List<Memory> query(MemoryQuery q) { return List.of(); }
        @Override public void erase(EraseRequest r) {}
    };

    @Test
    void storeAll_delegatesToStore() {
        var a = new MemoryInput("e1", new MemoryDomain("d"), "t1", null, "a", Map.of());
        var b = new MemoryInput("e1", new MemoryDomain("d"), "t1", null, "b", Map.of());
        assertThat(sut.storeAll(List.of(a, b))).containsExactly("mem-1", "mem-1");
    }

    @Test
    void eraseById_defaultThrows() {
        assertThatThrownBy(() -> sut.eraseById("mem-1", "tenant-1"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    // Via interface default (proves delegation to MemoryPermissions)
    @Test
    void assertTenant_throws_on_mismatch() {
        assertThrows(SecurityException.class,
            () -> sut.assertTenant("wrong", principal("real")));
    }

    @Test
    void assertTenant_passes_on_match() {
        assertDoesNotThrow(() -> sut.assertTenant("real", principal("real")));
    }

    // Via static utility directly (callable by native reactive adapters)
    @Test
    void memoryPermissions_throws_on_mismatch() {
        assertThrows(SecurityException.class,
            () -> MemoryPermissions.assertTenant("wrong", principal("real")));
    }

    @Test
    void memoryPermissions_passes_on_match() {
        assertDoesNotThrow(() -> MemoryPermissions.assertTenant("real", principal("real")));
    }

    private static CurrentPrincipal principal(String tenancyId) {
        return new CurrentPrincipal() {
            @Override public String actorId() { return "actor"; }
            @Override public Set<String> groups() { return Set.of(); }
            @Override public String tenancyId() { return tenancyId; }
            @Override public boolean isCrossTenantAdmin() { return false; }
        };
    }
}
```

### `NoOpCaseMemoryStoreTest` — `platform/test/`

```java
@QuarkusTest
class NoOpCaseMemoryStoreTest {

    @Inject CaseMemoryStore store;
    @Inject ReactiveCaseMemoryStore reactiveStore;

    static final MemoryDomain DOMAIN = new MemoryDomain("test");
    static final MemoryInput SAMPLE = new MemoryInput(
        "entity-1", DOMAIN, "tenant-1", null, "sample", Map.of());
    static final MemoryInput SAMPLE_WITH_CASE = new MemoryInput(
        "entity-1", DOMAIN, "tenant-1", "case-99", "sample", Map.of());
    static final MemoryQuery QUERY = new MemoryQuery(
        "entity-1", DOMAIN, "tenant-1", null, null, 10, null);
    static final EraseRequest ERASE_SCOPED = new EraseRequest(
        "entity-1", DOMAIN, null, "tenant-1");
    static final EraseRequest ERASE_ALL_DOMAINS = new EraseRequest(
        "entity-1", null, null, "tenant-1"); // GDPR full-wipe variant

    @Test void store_returnsEmptyId()  { assertThat(store.store(SAMPLE)).isEmpty(); }
    @Test void query_returnsEmpty()    { assertThat(store.query(QUERY)).isEmpty(); }
    @Test void erase_scoped_doesNotThrow()    { store.erase(ERASE_SCOPED); }
    @Test void erase_allDomains_doesNotThrow() { store.erase(ERASE_ALL_DOMAINS); }
    @Test void eraseById_doesNotThrow() { store.eraseById("mem-1", "tenant-1"); }
    @Test void storeAll_returnsEmptyIds() {
        assertThat(store.storeAll(List.of(SAMPLE, SAMPLE_WITH_CASE)))
            .containsExactly("", "");
    }

    @Test void bridge_query_returnsEmpty() {
        assertThat(reactiveStore.query(QUERY).await().indefinitely()).isEmpty();
    }
    @Test void bridge_store_returnsEmptyId() {
        assertThat(reactiveStore.store(SAMPLE).await().indefinitely()).isEmpty();
    }
    @Test void bridge_erase_doesNotThrow() {
        reactiveStore.erase(ERASE_SCOPED).await().indefinitely();
    }
    @Test void bridge_eraseById_doesNotThrow() {
        reactiveStore.eraseById("mem-1", "tenant-1").await().indefinitely();
    }
}
```

---

## 7. Permission Enforcement Contract

Permission enforcement is an **adapter responsibility**, not a SPI signature concern.
The SPI stays clean. Adapters:

1. MUST call `MemoryPermissions.assertTenant(tenantId, currentPrincipal)` at the top of
   every `store()`, `query()`, `erase()`, and `eraseById()` implementation before any
   backend call
2. In reactive adapters, MUST capture `CurrentPrincipal` before entering the `Uni`
   pipeline — the `@RequestScoped` context is not guaranteed on the executor thread
3. Enforce domain isolation as strict equality (`memory.domain().equals(query.domain())`)
4. Apply result filtering before returning to the caller
5. Never forward the principal's identity, roles, or group memberships to the backend —
   the backend is a dumb store; all permission logic lives in the adapter

The no-op performs no enforcement — it returns nothing and stores nothing, so there is
nothing to leak.

---

## 8. Deferred Items

All must be tracked as GitHub issues before implementation begins.

| Item | Repo | Notes |
|------|------|-------|
| Create `casehub-memory` repo | `casehubio/memory` | Prerequisite for all adapter issues; parent POM, CI |
| Memori adapter (`memory-memori/`) | `casehubio/memory` | Tier 1 — SQL-native, zero extra infra |
| Mem0 adapter (`memory-mem0/`) | `casehubio/memory` | Tier 2 — Docker + pgvector |
| Graphiti adapter (`memory-graphiti/`) | `casehubio/memory` | Tier 3 — Neo4j/FalkorDB/Kuzu; may implement `ReactiveCaseMemoryStore` directly, bypassing bridge |
| CDI observer emission mechanism | `casehubio/engine` | Auto-capture case completion events into CaseMemoryStore |
| Reactive `storeAll()` on `ReactiveCaseMemoryStore` | `casehubio/platform` | If an adapter needs bulk reactive store |
| Adapter contract tests — verify `assertTenant()` called on all mutating paths | `casehubio/memory` | Compilation cannot enforce this; must be test-verified per adapter |

---

## 9. PLATFORM.md Updates (in scope for platform#27)

- Add `CaseMemoryStore` row to Capability Ownership table
- Add `casehub-memory` row to Repository Map (Foundation tier, alongside casehub-work and casehub-ledger)
- Add cross-repo dependency map rows once `casehub-memory-*` artifacts are consumed by application repos
- Add `casehub-memory` to Build / Dependency Order (after `casehub-platform`, before application tier)
