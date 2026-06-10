# XS/S Correctness Batch — Design (rev 2)

**Issues:** platform#54, #62, #64, #72, #79  
**Branch:** `issue-54-xs-s-batch-fixes`  
**Date:** 2026-06-10  
**Revised:** 2026-06-10 (post-review)

---

## Scope

Five correctness fixes on the same branch. No migrations. No backward compatibility constraints. Design-quality-first; breaking changes to SPI signatures are intentional and mechanical to migrate.

---

## #54 — Jandex index for platform-api

**Already resolved** in commit `7ed33ce fix(#54): add Jandex index to casehub-platform-api`. The `jandex-maven-plugin` is in `platform-api/pom.xml` and `META-INF/jandex.idx` is generated into `target/classes`. Action: close the GitHub issue. No code changes.

---

## #62 — ScimActorDIDProvider: test constructor + authToken validation

**File:** `identity/src/main/java/io/casehub/platform/identity/ScimActorDIDProvider.java`

### Fix 1 — Test constructor `requireHttps` field

The package-private test constructor sets `this.requireHttps = true` and is documented "use with `http://` WireMock endpoints." The field value is **inert** in this path: `requireHttps` is only read inside `validateEndpoint()`, which is a `@PostConstruct` method that never fires for directly-constructed objects. The value `true` is never read and can never cause a failure — it just creates confusion for future maintainers who might assume it is.

Change: `this.requireHttps = true` → `this.requireHttps = false` to match the documented contract and prevent confusion if the field ever acquires a second read site.

### Fix 2 — Blank authToken at `@PostConstruct`

`validateEndpoint()` checks that the endpoint is non-blank and uses HTTPS. It does not validate `authToken`. A blank token sends `Authorization: Bearer ` on every request, receives 401s that are never cached (the cache is never populated), and causes retry storms under load.

Note on timing: `ScimActorDIDProvider` is `@Alternative`. `@PostConstruct` fires only when the bean is activated via `quarkus.arc.selected-alternatives` — not at general Quarkus boot. This means the startup validation is conditional on activation, not guaranteed universally. Any deployment that activates this bean will hit the check.

Add immediately after the HTTPS check in `validateEndpoint()`:

```java
if (authToken == null || authToken.isBlank()) {
    throw new IllegalArgumentException(
        "casehub.identity.scim.auth-token must not be blank when endpoint is configured");
}
```

**Hard fail, not LOG.warn.** A blank token with a configured endpoint has no valid use case. Fail at activation time, not under load.

---

## #64 — Mem0 `eraseById` preflight ownership check + SPI `entityId` addition

**Files:**
- `platform-api/.../CaseMemoryStore.java` — `eraseById` signature
- `platform-api/.../CaseMemoryStoreSpiTest.java` — signature update
- `platform/.../NoOpCaseMemoryStore.java`, `ReactiveCaseMemoryStore.java`, `BlockingToReactiveBridge.java` — signature updates
- `memory-inmem/.../InMemoryMemoryStore.java` — signature + entity-scoped search
- `memory-jpa/.../JpaMemoryStore.java` — signature + `entity_id` in WHERE
- `memory-sqlite/.../SqliteMemoryStore.java` — signature + `entity_id` in WHERE
- `memory-mem0/.../Mem0Client.java` — add `getById`
- `memory-mem0/.../Mem0CaseMemoryStore.java` — eraseById signature + preflight GET
- `memory-mem0/.../Mem0CaseMemoryStoreTest.java` — updated tests
- `memory-graphiti/.../GraphitiCaseMemoryStore.java` — signature update (still throws `MemoryCapabilityException`)
- `memory-graphiti/.../GraphitiCaseMemoryStoreTest.java` — signature update
- `testing/.../CaseMemoryStoreContractTest.java` — signature updates
- `platform/.../NoOpCaseMemoryStoreTest.java`, `BlockingToReactiveBridgeThreadingTest.java` — signature updates

### Architectural decision: add `entityId` to `eraseById`

The current SPI is inconsistent:

| Method | Has entityId? |
|---|---|
| `store(MemoryInput)` | ✅ yes |
| `query(MemoryQuery)` | ✅ yes |
| `erase(EraseRequest)` | ✅ yes |
| `eraseEntity(entityId, tenantId)` | ✅ yes |
| `eraseById(memoryId, tenantId)` | ❌ no — outlier |

`eraseById` is the sole method without entity scope. The counter-argument — that callers always obtain `memoryId` via `query()`, which already enforces entity isolation — is a runtime-behaviour argument, not a design argument. Correctness should be by construction, not by caller discipline.

**New signature:** `eraseById(String memoryId, String entityId, String tenantId)`

With `entityId`, the Mem0 preflight can perform an exact `user_id` equality check (`compoundUserId(tenantId, entityId)`) instead of prefix matching. All JDBC adapters add `entity_id` to the WHERE clause. The migration is fully mechanical.

### Preflight GET: endpoint verification required

⚠️ **Implementation blocker:** Add `GET /memories/{memoryId}` to `Mem0Client` — but **confirm this endpoint exists in the target Mem0 OSS version before implementing**. The WireMock stubs in `Mem0CaseMemoryStoreTest` cover only `POST /memories`, `GET /memories`, `POST /search`, `DELETE /memories/{id}`, and `DELETE /memories`. The blog entry explicitly documents `POST /memories`, `GET /memories`, `POST /search` as the OSS API surface. No evidence in the codebase that `GET /memories/{id}` exists in Mem0 OSS. If the endpoint is absent, the preflight strategy must be revised (see alternative below).

Add to `Mem0Client` if confirmed:

```java
@GET
@Path("/memories/{memoryId}")
@Produces(MediaType.APPLICATION_JSON)
Mem0Memory getById(@PathParam("memoryId") String memoryId);
```

`Mem0Memory` already has a `userId()` field containing `"{tenantId}::{entityId}"`.

### eraseById implementation in Mem0CaseMemoryStore

```java
// Preflight: verify the memory belongs to this entity within this tenant
final Mem0Memory existing;
try {
    existing = client.getById(memoryId);
} catch (WebApplicationException e) {
    if (e.getResponse() != null && e.getResponse().getStatus() == 404) return;
    throw toStoreException(e);
}
// A null userId on a 200 response is treated as a violation —
// a memory with no owner identity cannot be safely attributed.
final String expectedUserId = compoundUserId(tenantId, entityId);
if (!expectedUserId.equals(existing.userId())) {
    throw new SecurityException(
        "Memory " + memoryId + " does not belong to entity " + entityId
        + " in tenant " + tenantId);
}
// Proceed to DELETE — 404 guard handles concurrent deletion
```

**If `GET /memories/{id}` does not exist in Mem0 OSS:** the preflight must fall back to `GET /memories?user_id={compoundUserId}` (list for this entity), scanning for `memoryId`. This is O(N) in entity history size. Confirm API surface before choosing strategy.

**No config flag.** This is a security invariant. `eraseById` is infrequent GDPR erasure; one extra round-trip is acceptable.

### Other adapters with new entityId parameter

- **`InMemoryMemoryStore.eraseById`**: scope the scan to `BucketKey(tenantId, entityId, *)` buckets. The current implementation scans all tenant entries; adding entity scope makes it precise and faster.
- **`JpaMemoryStore.eraseById`**: add `AND entityId = :entityId` to the JPQL DELETE WHERE clause.
- **`SqliteMemoryStore.eraseById`**: add `AND entity_id = ?` to the SQL DELETE WHERE clause.
- **`GraphitiCaseMemoryStore.eraseById`**: still throws `MemoryCapabilityException` (no `ERASE_BY_ID`); just update the method signature.
- **`NoOpCaseMemoryStore.eraseById`**: still a true no-op; update signature only.

### Tests

Update `Mem0CaseMemoryStoreTest` for new signature and add:
- `eraseById_preflight_GET_verifies_ownership`: stub GET → matching userId → stub DELETE → verify both called
- `eraseById_throws_SecurityException_on_userId_mismatch`: stub GET → mismatched userId → SecurityException; verify no DELETE
- `eraseById_returns_on_preflight_404`: stub GET → 404 → no DELETE called
- `eraseById_tenant_mismatch_throws_before_http`: unchanged semantics, new signature

Update `CaseMemoryStoreContractTest` signature in all `eraseById` call sites. The entityId for the contract test calls is `"entity-1"` (the default input entity).

---

## #72 — `eraseEntity()` return type `void` → `int`

**Purpose:** GDPR Art.5(2) accountability. Callers need to log how many records were actually erased.

### SPI changes

**`CaseMemoryStore`** (`platform-api`):

```java
default int eraseEntity(String entityId, String tenantId) {
    throw new MemoryCapabilityException(MemoryCapability.ERASE_ENTITY, getClass());
}
```

**`ReactiveCaseMemoryStore`** (`platform/`):

```java
default Uni<Integer> eraseEntity(String entityId, String tenantId) {
    return Uni.createFrom().failure(
        new UnsupportedOperationException("eraseEntity not supported by this adapter"));
}
```

**`BlockingToReactiveBridge`** (`platform/`):

```java
public Uni<Integer> eraseEntity(String entityId, String tenantId) {
    return Uni.createFrom().item(() -> delegate.eraseEntity(entityId, tenantId))
              .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
}
```

### Implementations — counts

**`NoOpCaseMemoryStore`**: `return 0` — nothing stored, erasure trivially satisfied.

**`InMemoryMemoryStore`**: single-pass with `AtomicInteger` to avoid the two-pass race window:

```java
@Override
public int eraseEntity(String entityId, String tenantId) {
    MemoryPermissions.assertTenant(tenantId, principal, requestContextActive());
    final AtomicInteger count = new AtomicInteger();
    store.entrySet().removeIf(e -> {
        if (e.getKey().tenantId().equals(tenantId) && e.getKey().entityId().equals(entityId)) {
            count.addAndGet(e.getValue().size());
            return true;
        }
        return false;
    });
    return count.get();
}
```

`ConcurrentHashMap.entrySet().removeIf()` is safe under concurrent access. The count reflects list sizes at iteration time; a concurrent add between iteration and removal may undercount by at most that add. Acceptable for a test adapter.

**`JpaMemoryStore`**: JPQL `DELETE FROM MemoryEntry WHERE tenantId = :tenantId AND entityId = :entityId` — `executeUpdate()` returns `int` natively.

**`SqliteMemoryStore`**: JDBC `DELETE FROM memory_entry WHERE tenant_id = ? AND entity_id = ?` — `executeUpdate()` returns `int`.

**`Mem0CaseMemoryStore`**: count-then-delete. `client.list(userId, null, null)` returns all memories for the entity across all domains. Null-safe count:

```java
final Mem0ListResponse listed = client.list(compoundUserId(tenantId, entityId), null, null);
final int count = listed.results() != null ? listed.results().size() : 0;
client.deleteAll(compoundUserId(tenantId, entityId), null, null);
return count;
```

**Javadoc on `Mem0CaseMemoryStore.eraseEntity()`:** "The returned count is a best-effort estimate: new writes can arrive between the `list()` call and the `deleteAll()` call, causing the count to understate the actual deletion. Callers must treat this as a lower bound for logging purposes — it is not an exact audit count."

**`GraphitiCaseMemoryStore`**: count episodes first, then cascade-delete the group:

```java
private static final int MAX_EPISODES_FOR_COUNT = 10_000;

@Override
public int eraseEntity(final String entityId, final String tenantId) {
    MemoryPermissions.assertTenant(tenantId, principal, requestContextActive());
    final String groupId = compoundGroupId(tenantId, entityId);
    final int count = client.getEpisodes(groupId, MAX_EPISODES_FOR_COUNT).size();
    try {
        client.deleteGroup(groupId);
    } catch (final WebApplicationException e) {
        throw GraphitiStoreException.from(e);
    }
    return count;
}
```

**Javadoc on `GraphitiCaseMemoryStore.eraseEntity()`:** "Returns the episode count at the time of the call, capped at `MAX_EPISODES_FOR_COUNT` (10,000). The Graphiti server's `last_n` upper bound is not verified; entities with more than 10,000 episodes will report an understated count. Derived entity nodes and relationship edges extracted by the LLM have no independent count API and are not included in the returned value. The actual deletion via `DELETE /group/{groupId}` is complete — the cap is only a count limitation, not an erasure limitation."

### Contract test additions

In `CaseMemoryStoreContractTest`:

```java
@Test
void eraseEntity_returns_count_of_deleted_records() {
    store().store(input("entity-1", "a"));
    store().store(input("entity-1", "b"));
    store().store(new MemoryInput("entity-1", OTHER_DOMAIN, TENANT, null, "c", Map.of()));
    assertEquals(3, store().eraseEntity("entity-1", TENANT));
}

@Test
void eraseEntity_returns_zero_when_nothing_stored() {
    assertEquals(0, store().eraseEntity("entity-99", TENANT));
}
```

Update existing `eraseEntity_removes_all_domains_for_entity` and `eraseEntity_leaves_other_entities_intact` to assert the return value is positive and zero respectively (rather than discarding it).

### Other files touched

- `platform-api/.../CaseMemoryStoreSpiTest.java` — `eraseEntity_default_throws_MemoryCapabilityException`: return type signature update
- `platform/.../NoOpCaseMemoryStoreTest.java` — assert `eraseEntity` returns `0`
- `platform/.../BlockingToReactiveBridgeThreadingTest.java` — update inline stub (`void` → `return 0`) and assertion
- `memory-jpa/src/test/.../JpaMemoryStoreTest.java` — add count assertions for existing `eraseEntity` calls; `eraseEntity` is also called via `CaseMemoryStoreContractTest` which this extends
- `memory-sqlite/src/test/.../SqliteMemoryStoreTest.java` — update `@AfterEach` calls (lines 31–32) to receive and discard the int, or assert `> 0` as a health check

---

## #79 — `assertTenant` async-aware overload + all adapters

### Root cause

`MemoryPermissions.assertTenant(tenantId, principal)` checks `principal.tenancyId().equals(tenantId)`. In `@ObservesAsync` handler threads, no CDI request scope is propagated. `CurrentPrincipal` (a `@RequestScoped` or `@ApplicationScoped` mock) returns a sentinel that does not match `input.tenantId()` → `SecurityException` → silent drop.

`assertTenant()` is an **HTTP boundary authentication gate**, not a data filter. In async context the "caller" is trusted application code — the tenantId in `MemoryInput` was set by code that ran in authenticated context. Data scoping (which records are touched) always happens unconditionally via `input.tenantId()` in the adapter.

### `MemoryPermissions` — new overload

`MemoryPermissions` stays CDI-free. The CDI context check lives in the adapters.

```java
/**
 * Async-safe form. When requestContextActive=false (e.g. in an {@code @ObservesAsync}
 * handler thread), trusts tenantId directly — the caller is application code running
 * after an authenticated event fire, not an external actor.
 * When requestContextActive=true, delegates to {@link #assertTenant(String, CurrentPrincipal)}.
 */
public static void assertTenant(String tenantId, CurrentPrincipal principal,
                                 boolean requestContextActive) {
    if (requestContextActive) assertTenant(tenantId, principal);
}
```

The 2-arg form is unchanged. It remains correct for callers that know they are in request scope.

### Remove `CaseMemoryStore.assertTenant()` default wrapper

`CaseMemoryStore` has a default wrapper at lines 138–140:
```java
default void assertTenant(String tenantId, CurrentPrincipal principal) {
    MemoryPermissions.assertTenant(tenantId, principal);
}
```

No adapter calls `this.assertTenant()` — all call `MemoryPermissions.assertTenant()` directly. The wrapper serves no function but creates a trap: future adapter code that uses `this.assertTenant()` instead of the 3-arg `MemoryPermissions.assertTenant()` will silently bypass async-awareness. **Remove the wrapper entirely.**

`CaseMemoryStoreSpiTest` has two tests (`assertTenant_throws_on_mismatch`, `assertTenant_passes_on_match`) that test only the wrapper delegation. These tests are redundant with `MemoryPermissionsTest` and must be removed along with the wrapper.

### Adapter changes (5 adapters)

Each adapter (`InMemoryMemoryStore`, `JpaMemoryStore`, `SqliteMemoryStore`, `Mem0CaseMemoryStore`, `GraphitiCaseMemoryStore`) gains one private helper:

```java
private boolean requestContextActive() {
    return Arc.container().requestContext().isActive();
}
```

Every `MemoryPermissions.assertTenant(x, principal)` call in every operation (`store`, `storeAll`, `query`, `erase`, `eraseById`, `eraseEntity`) becomes:

```java
MemoryPermissions.assertTenant(x, principal, requestContextActive());
```

**`SqliteMemoryStore.storeAll()` calls `assertTenant` twice per item** — the pre-flight `forEach` at line 125 (checks all inputs before any JDBC operation) AND the per-item call at line 133 inside the batch loop. Both sites must change to the 3-arg form. The double-check is intentional (pre-flight for atomicity + per-item as defence-in-depth); preserving both is correct.

### `CaseMemoryStore.store()` Javadoc — full replacement

The existing Javadoc has three guidance sections. All three must be updated:

**`@ObservesAsync`** — Previously marked "not safe." Now: `@ObservesAsync` callers are supported. Adapters use the async-aware 3-arg `assertTenant` form, which trusts `MemoryInput.tenantId()` directly when no CDI request scope is active. The data-scoping by `tenantId` is unconditional; only the principal comparison is skipped.

**`@Observes` (synchronous)** — Still valid and unchanged. Synchronous observers preserve request scope and propagate exceptions normally. The transaction-coupling tradeoff remains: a synchronous `store()` call is atomic with the event-firing transaction — desirable for compliance writes, wrong if the caller expects fire-and-forget.

**Batch jobs / startup contexts** — Previously required explicit request scope activation. Now: the 3-arg assertTenant form handles these too — no request scope active → trust the tenantId from `MemoryInput` directly. Explicit `@ActivateRequestContext` is no longer required for memory writes from batch or startup code.

### `MemoryPermissionsTest` — new tests

```java
@Test
void three_arg_skips_check_when_not_in_request_context() {
    CurrentPrincipal p = () -> "other-tenant";
    assertDoesNotThrow(() -> MemoryPermissions.assertTenant("mine", p, false));
}

@Test
void three_arg_enforces_when_in_request_context() {
    CurrentPrincipal p = () -> "other-tenant";
    assertThrows(SecurityException.class,
        () -> MemoryPermissions.assertTenant("mine", p, true));
}
```

(`CurrentPrincipal` is a functional interface — `tenancyId()` is the only method invoked by these tests.)

### Protocol update

`casememorystore-adapter-asserttenant-contract.md` (PP-20260529-57cc3b): add:

> **Async-aware form:** Adapters that support `@ObservesAsync` callers use the 3-arg overload `MemoryPermissions.assertTenant(tenantId, principal, requestContextActive())` where `requestContextActive()` returns `Arc.container().requestContext().isActive()`. When the request context is not active, the principal comparison is skipped and `tenantId` from `MemoryInput` is trusted directly. The security gate before capability gate ordering still applies.

---

## Commit sequence

```
fix(platform#62): ScimActorDIDProvider test constructor + authToken hard validation
fix(platform#79): MemoryPermissions async-aware assertTenant overload + all adapters
fix(platform#72): eraseEntity() returns int count — all adapters + reactive bridge
fix(platform#64): eraseById gains entityId param + Mem0 preflight ownership GET
```

Issue #54 is closed via GitHub (no code commit).

---

## Files changed

| File | Issues |
|---|---|
| `platform-api/.../MemoryPermissions.java` | #79 |
| `platform-api/.../CaseMemoryStore.java` | #64 (eraseById sig), #72, #79 (remove wrapper, Javadoc) |
| `platform-api/.../MemoryPermissionsTest.java` | #79 |
| `platform-api/.../CaseMemoryStoreSpiTest.java` | #64, #72, #79 (remove wrapper tests) |
| `platform/.../NoOpCaseMemoryStore.java` | #64, #72 |
| `platform/.../ReactiveCaseMemoryStore.java` | #64, #72 |
| `platform/.../BlockingToReactiveBridge.java` | #64, #72 |
| `platform/.../NoOpCaseMemoryStoreTest.java` | #64, #72 |
| `platform/.../BlockingToReactiveBridgeThreadingTest.java` | #64, #72 |
| `memory-inmem/.../InMemoryMemoryStore.java` | #64, #72, #79 |
| `memory-jpa/.../JpaMemoryStore.java` | #64, #72, #79 |
| `memory-jpa/src/test/.../JpaMemoryStoreTest.java` | #72 |
| `memory-sqlite/.../SqliteMemoryStore.java` | #64, #72, #79 |
| `memory-sqlite/src/test/.../SqliteMemoryStoreTest.java` | #72 |
| `memory-mem0/.../Mem0Client.java` | #64 |
| `memory-mem0/.../Mem0CaseMemoryStore.java` | #64, #72, #79 |
| `memory-mem0/.../Mem0CaseMemoryStoreTest.java` | #64, #72 |
| `memory-graphiti/.../GraphitiCaseMemoryStore.java` | #64, #72, #79 |
| `memory-graphiti/.../GraphitiCaseMemoryStoreTest.java` | #64, #72 |
| `identity/.../ScimActorDIDProvider.java` | #62 |
| `testing/.../CaseMemoryStoreContractTest.java` | #64, #72 |
| `garden/docs/protocols/casehub/casememorystore-adapter-asserttenant-contract.md` | #79 |
