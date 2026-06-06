# Spec: Mem0 storeAll() — sequential batch with pre-flight tenant guard

**Issue:** platform#69  
**Branch:** issue-69-mem0-storeall-batch  
**Date:** 2026-06-06

---

## Problem

`Mem0CaseMemoryStore` has no `storeAll()` override. It falls through to the SPI default:

```java
default List<String> storeAll(List<MemoryInput> inputs) {
    return inputs.stream().map(this::store).toList();
}
```

`store()` calls `assertTenant` then fires a `POST /memories`. For a mixed-tenant batch `[good, bad]`:
1. `store(good)` — assertTenant passes → REST call sent → memory persisted in Mem0
2. `store(bad)` — assertTenant throws SecurityException

Item 0 is already in Mem0 when item 1 fails. The `memory-storeall-transactional-contract` protocol requires no partial writes on tenant violation. The SPI default violates this for REST adapters.

The same bug exists in `InMemoryMemoryStore`, which also relies on the SPI default. `CaseMemoryStoreContractTest` does not test the `[good, bad]` mixed-tenant case, which masked both bugs.

---

## Decision: sequential execution

Parallel execution (Uni.join / ThreadPoolExecutor) was considered and deferred:
- Upstream Mem0 OSS PRs [#4804](https://github.com/mem0ai/mem0/pull/4804) and [#5194](https://github.com/mem0ai/mem0/pull/5194) add client-side parallel batch but are not merged
- N concurrent REST calls saturate the Mem0 embedding pipeline (CPU-bound) and the MicroProfile connection pool
- Partial-failure rollback is impossible over REST regardless of concurrency model
- Tracking issue: platform#70

Sequential execution with pre-flight assertTenant gives REST-adapter atomicity: security violations abort before any HTTP; REST failures stop the batch at the first error.

---

## Design

### 1. `Mem0CaseMemoryStore` — extract `sendAdd()`, add `storeAll()`

Extract the HTTP logic from `store()` into a private `sendAdd(MemoryInput)`:

```java
private String sendAdd(MemoryInput input) {
    final var request = new Mem0AddRequest(
        List.of(new Mem0AddRequest.Mem0Message("user", input.text())),
        compoundUserId(input.tenantId(), input.entityId()),
        input.domain().name(),
        input.caseId(),
        config.infer(),
        new HashMap<>(input.attributes())
    );
    final Mem0AddResponse response;
    try {
        response = client.add(request);
    } catch (WebApplicationException e) {
        throw toStoreException(e);
    }
    if (response.results() == null || response.results().isEmpty()) {
        throw new Mem0StoreException("store produced no result for: " + input.entityId());
    }
    return response.results().get(0).id();
}
```

`store()` becomes:
```java
@Override
public String store(MemoryInput input) {
    MemoryPermissions.assertTenant(input.tenantId(), principal);
    return sendAdd(input);
}
```

`storeAll()`:
```java
@Timed(value = "casehub.memory.mem0", histogram = true, extraTags = {"operation", "storeAll"})
@Override
public List<String> storeAll(List<MemoryInput> inputs) {
    if (inputs.isEmpty()) return List.of();
    inputs.forEach(i -> MemoryPermissions.assertTenant(i.tenantId(), principal));
    final var ids = new ArrayList<String>(inputs.size());
    for (final var input : inputs) {
        ids.add(sendAdd(input));
    }
    return List.copyOf(ids); // unmodifiable, consistent with SPI default and JPA
}
```

**Guarantees:**
- Empty input → immediate `List.of()`, zero HTTP
- Any tenant mismatch → `SecurityException` before any `POST /memories`
- First REST failure → `Mem0StoreException`, remaining items not sent
- IDs returned in input order

**Not guaranteed:** rollback of already-sent items on mid-batch REST failure. This is a documented REST adapter limitation — no compensation mechanism exists without a server-side transaction.

### 2. `InMemoryMemoryStore` — add `storeAll()` override

InMemory has the same SPI-default partial-write bug. Fix with explicit override:

```java
@Override
public List<String> storeAll(List<MemoryInput> inputs) {
    if (inputs.isEmpty()) return List.of();
    inputs.forEach(i -> MemoryPermissions.assertTenant(i.tenantId(), principal));
    return inputs.stream().map(this::store).toList();
}
```

`store()` re-checks assertTenant (double-check, negligible cost for in-memory). This is intentional — `store()` is a public SPI method and must remain self-defending.

### 3. `CaseMemoryStore` — clarify `storeAll()` Javadoc

Add explicit override contract to the default method Javadoc — outcome-based, not mechanism-based (mechanism differs by adapter type):

> Adapters that override this method must: (1) check `MemoryPermissions.assertTenant` for every input; (2) return IDs in input order; (3) ensure no items are durably written if any tenant check fails — via pre-flight for REST-backed adapters, or single-transaction rollback for JDBC-backed adapters. See `memory-storeall-transactional-contract.md` for the full contract.

### 4. `CaseMemoryStoreContractTest` — add mixed-tenant storeAll test

```java
@Test
void storeAll_second_item_tenant_mismatch_no_entries_stored() {
    var good = input("entity-1", "fact");
    var bad  = new MemoryInput("entity-2", DOMAIN, OTHER_TENANT, null, "x", Map.of());
    assertThrows(SecurityException.class, () -> store().storeAll(List.of(good, bad)));
    assertTrue(store().query(query()).isEmpty(),
        "first item must not be persisted when second item fails tenant check");
}
```

All existing adapters (JPA, SQLite, InMemory-after-fix) must pass this test. Mem0 is WireMock-backed and has its own equivalent test.

### 5. `Mem0CaseMemoryStoreTest` — new storeAll tests

Three additions to the `// ── storeAll ──` section:

```java
@Test
void storeAll_empty_returns_empty_no_http() {
    assertEquals(List.of(), store.storeAll(List.of()));
    wireMock().verify(0, postRequestedFor(urlEqualTo("/memories")));
}

@Test
void storeAll_any_tenant_mismatch_fires_zero_http_calls() {
    // second item has wrong tenant — pre-flight must catch it before any REST call
    assertThrows(SecurityException.class, () ->
        store.storeAll(List.of(
            new MemoryInput("entity-1", DOMAIN, TENANT,       null, "ok",  Map.of()),
            new MemoryInput("entity-2", DOMAIN, OTHER_TENANT, null, "bad", Map.of())
        )));
    wireMock().verify(0, postRequestedFor(urlEqualTo("/memories")));
}

@Test
void storeAll_http_failure_stops_remaining_items() {
    wireMock().stubFor(post(urlEqualTo("/memories")).willReturn(serverError()));
    assertThrows(Mem0StoreException.class, () ->
        store.storeAll(List.of(
            new MemoryInput("entity-1", DOMAIN, TENANT, null, "a", Map.of()),
            new MemoryInput("entity-1", DOMAIN, TENANT, null, "b", Map.of())
        )));
    wireMock().verify(1, postRequestedFor(urlEqualTo("/memories")));
}
```

### 6. Protocol update — `memory-storeall-transactional-contract.md`

Add REST adapter clause:

> **REST-backed adapters** (Mem0, Graphiti, and any future HTTP-delegating adapters) have no JPA transaction. The equivalent guarantee is: pre-flight `MemoryPermissions.assertTenant()` for **all** inputs before issuing any HTTP request, then fail-fast on the first HTTP error. Items already persisted before a mid-batch HTTP failure cannot be rolled back — this is a documented REST adapter limitation and must be noted in the adapter's Javadoc.

---

## Files changed

| File | Change |
|------|--------|
| `memory-mem0/src/main/java/.../Mem0CaseMemoryStore.java` | Extract `sendAdd()`, add `storeAll()` |
| `memory-inmem/src/main/java/.../InMemoryMemoryStore.java` | Add `storeAll()` override |
| `platform-api/src/main/java/.../CaseMemoryStore.java` | Clarify `storeAll()` Javadoc |
| `testing/src/main/.../CaseMemoryStoreContractTest.java` | Add mixed-tenant storeAll test |
| `memory-mem0/src/test/.../Mem0CaseMemoryStoreTest.java` | Add 3 storeAll tests |
| `casehub/garden/docs/protocols/casehub/memory-storeall-transactional-contract.md` | Add REST adapter clause |
| `docs/superpowers/specs/2026-06-04-memory-mem0-design.md` | Update stale storeAll partial-failure known limitation |

## Out of scope

- Parallel execution — tracked in platform#70 (revisit when Mem0 PRs #4804/#5194 merge)
- SQLite pre-flight cleanup — tracked in platform#71 (not broken, cosmetic)
