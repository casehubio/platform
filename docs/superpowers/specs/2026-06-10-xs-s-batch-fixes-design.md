# XS/S Correctness Batch — Design

**Issues:** platform#54, #62, #64, #72, #79  
**Branch:** `issue-54-xs-s-batch-fixes`  
**Date:** 2026-06-10

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

The package-private test constructor is documented "use with `http://` WireMock endpoints" but sets `this.requireHttps = true`. The field is dead weight in this path (`@PostConstruct` does not run for test constructor callers), and the value contradicts the documented use case.

Change: `this.requireHttps = true` → `this.requireHttps = false`.

### Fix 2 — Blank authToken at `@PostConstruct`

`validateEndpoint()` checks that the endpoint is non-blank and uses HTTPS. It does not check that `authToken` is non-blank. A blank token sends `Authorization: Bearer ` on every request, receives 401s that are not cached (cache is never populated), and causes a retry storm under load.

Add immediately after the HTTPS check:

```java
if (authToken == null || authToken.isBlank()) {
    throw new IllegalArgumentException(
        "casehub.identity.scim.auth-token must not be blank when endpoint is configured");
}
```

**Hard fail, not LOG.warn.** A blank token with a configured endpoint has no valid use case. Fail fast at startup rather than silently degrade under load.

---

## #64 — Mem0 `eraseById` preflight ownership check

**Files:**
- `memory-mem0/src/main/java/io/casehub/platform/memory/mem0/Mem0Client.java`
- `memory-mem0/src/main/java/io/casehub/platform/memory/mem0/Mem0CaseMemoryStore.java`
- `memory-mem0/src/test/java/io/casehub/platform/memory/mem0/Mem0CaseMemoryStoreTest.java`

### Context

`eraseById` calls `assertTenant` (verifying the caller belongs to `tenantId`) then immediately issues `DELETE /memories/{memoryId}`. This prevents cross-tenant IDOR (a tenant-A caller cannot target a tenant-B memoryId because `assertTenant` would reject it). However, intra-tenant IDOR is not prevented: any entity in tenant-A that knows a `memoryId` can delete any other entity-in-tenant-A's memory, even if that memory was stored under a different `entityId`.

The compound `user_id = "{tenantId}::{entityId}"` encodes entity scope. The preflight GET verifies the target memory's `user_id` starts with `"{tenantId}::"` before deleting — this is already covered. More precisely, it verifies `"{tenantId}::"` is the tenant prefix, which is the cross-tenant guard already in place.

The actual intra-tenant entity isolation gap: memory `M` belongs to `entityId="agent-1"` under `tenantId="t1"`. A caller authenticated as `t1` but acting for `entityId="agent-2"` can delete `M` because `assertTenant` only checks `t1 == t1`. The preflight GET fetches `M`'s `user_id` (`t1::agent-1`) and can verify it starts with the right tenant prefix — but it cannot verify entity ownership without knowing the caller's `entityId`.

`eraseById` does not receive an `entityId` parameter (by SPI design). The preflight GET therefore provides **tenant-level ownership verification only** — confirming the memory belongs to this tenant before deleting. This closes the cross-tenant gap robustly and makes the ownership chain explicit in the code.

### Design

Add to `Mem0Client`:

```java
@GET
@Path("/memories/{memoryId}")
@Produces(MediaType.APPLICATION_JSON)
Mem0Memory getById(@PathParam("memoryId") String memoryId);
```

`Mem0Memory` already has a `userId()` field containing `"{tenantId}::{entityId}"`.

In `Mem0CaseMemoryStore.eraseById()`, before the DELETE:

```java
// Preflight ownership check
final Mem0Memory existing;
try {
    existing = client.getById(memoryId);
} catch (WebApplicationException e) {
    if (e.getResponse() != null && e.getResponse().getStatus() == 404) return;
    throw toStoreException(e);
}
final String tenantPrefix = tenantId + SEP;
if (existing.userId() == null || !existing.userId().startsWith(tenantPrefix)) {
    throw new SecurityException(
        "Memory " + memoryId + " does not belong to tenant: " + tenantId);
}
// Proceed to DELETE (404 guard below handles concurrent deletion)
```

**No config flag.** This is a security invariant. `eraseById` is an infrequent GDPR erasure operation; one extra round-trip is acceptable.

### Tests

Update `Mem0CaseMemoryStoreTest`:
- `eraseById_sends_delete_after_successful_preflight_GET`: stub GET → matching userId → stub DELETE → verify both called
- `eraseById_throws_SecurityException_when_userId_tenant_prefix_mismatch`: stub GET → mismatched userId → verify SecurityException, no DELETE
- `eraseById_skips_when_preflight_returns_404`: stub GET → 404 → verify no DELETE called

---

## #72 — `eraseEntity()` return type `void` → `int`

**Purpose:** GDPR Art.5(2) accountability. Callers need to log how many records were actually erased, not just that erasure was requested.

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

### Implementations

| Adapter | Count source |
|---|---|
| `NoOpCaseMemoryStore` | `return 0` (nothing stored, erasure trivially satisfied) |
| `InMemoryMemoryStore` | Iterate `ConcurrentHashMap` entries matching `(tenantId, entityId)`, sum list sizes; then `removeIf` to delete |
| `JpaMemoryStore` | JPQL `DELETE FROM MemoryEntry WHERE tenantId = :tenantId AND entityId = :entityId` → `int` from `executeUpdate()` |
| `SqliteMemoryStore` | JDBC `DELETE FROM memory_entry WHERE tenant_id = ? AND entity_id = ?` → `int` from `executeUpdate()` |
| `Mem0CaseMemoryStore` | `client.list(userId, null, null)` → `results.size()` → then `client.deleteAll(userId, null, null)` |
| `GraphitiCaseMemoryStore` | `client.getEpisodes(groupId, Integer.MAX_VALUE).size()` → then `client.deleteGroup(groupId)`. Episode count only — derived entity nodes and edges have no independent count API; document this in Javadoc. |

For Mem0 and Graphiti, the count-then-delete adds one extra round-trip. Acceptable for infrequent GDPR erasure.

### Contract test additions

In `CaseMemoryStoreContractTest` (abstract; applies to all concrete adapter tests):

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

Update existing `eraseEntity_removes_all_domains_for_entity` and `eraseEntity_leaves_other_entities_intact` to verify return value is `> 0` and `>= 0` respectively (rather than ignoring it).

### Other files touched

- `platform-api/src/test/java/io/casehub/platform/api/memory/CaseMemoryStoreSpiTest.java` — `eraseEntity_default_throws_MemoryCapabilityException`: return type signature update
- `platform/src/test/java/io/casehub/platform/memory/NoOpCaseMemoryStoreTest.java` — verify `eraseEntity` returns `0`
- `platform/src/test/java/io/casehub/platform/memory/BlockingToReactiveBridgeThreadingTest.java` — update inline stub (`void` → `return 0`) and assertion

---

## #79 — `assertTenant` async-aware overload + all adapters

### Root cause

`MemoryPermissions.assertTenant(tenantId, principal)` checks `principal.tenancyId().equals(tenantId)`. In `@ObservesAsync` handler threads, no CDI request scope is propagated. `CurrentPrincipal` (a `@RequestScoped` or `@ApplicationScoped` mock) returns a sentinel that does not match `input.tenantId()` → `SecurityException` → silent drop.

The check is an **HTTP boundary authentication gate**, not a data filter. In async context the "caller" is trusted application code — the tenantId in `MemoryInput` was set by code that ran in authenticated context. The data-scoping by `tenantId` (which records are touched) is unconditional and always happens via `input.tenantId()` in the adapter. Only the principal comparison is skipped.

### Design decision

`MemoryPermissions` stays CDI-free (pure Java, fully unit-testable). The CDI context check lives in the adapters — they already have Quarkus deps and `Arc` is the right layer for container-awareness. The conditional logic is encapsulated in a named overload to avoid raw inline conditionals.

### `MemoryPermissions` — new overload

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

The 2-arg form is unchanged. It remains the correct call for callers that know they are in request scope.

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

### `MemoryPermissionsTest` — new tests

```java
@Test
void three_arg_skips_check_when_not_in_request_context() {
    // requestContextActive=false → no exception even with mismatched tenantId
    CurrentPrincipal p = () -> "other-tenant"; // anonymous impl
    assertDoesNotThrow(() -> MemoryPermissions.assertTenant("mine", p, false));
}

@Test
void three_arg_enforces_when_in_request_context() {
    CurrentPrincipal p = () -> "other-tenant";
    assertThrows(SecurityException.class,
        () -> MemoryPermissions.assertTenant("mine", p, true));
}
```

(Where `CurrentPrincipal` lambda works because `tenancyId()` is the only method needed for these tests.)

### `CaseMemoryStore` Javadoc update

Remove the `@ObservesAsync is not safe` warning on `store()`. Replace with:

> `@ObservesAsync` callers are supported. Adapters use the async-aware 3-arg `assertTenant` form, which trusts `MemoryInput.tenantId()` directly when no CDI request scope is active. The data-scoping by `tenantId` is unconditional; only the principal comparison is skipped in async context.

### Protocol update

`casememorystore-adapter-asserttenant-contract.md` (PP-20260529-57cc3b): add to the body:

> **Async-aware form:** Adapters that support `@ObservesAsync` callers call the 3-arg overload `MemoryPermissions.assertTenant(tenantId, principal, requestContextActive())` where `requestContextActive()` returns `Arc.container().requestContext().isActive()`. When the request context is not active, the principal comparison is skipped and `tenantId` from `MemoryInput` is trusted directly. The security gate before capability gate ordering still applies.

---

## Commit sequence

```
fix(platform#62): ScimActorDIDProvider test constructor + authToken hard validation
fix(platform#79): MemoryPermissions async-aware assertTenant overload + all adapters
fix(platform#72): eraseEntity() returns int count — all adapters + reactive bridge
fix(platform#64): Mem0 eraseById preflight ownership GET before DELETE
```

Issue #54 is closed via GitHub (no code commit).

---

## Files changed

| File | Issues |
|---|---|
| `platform-api/.../MemoryPermissions.java` | #79 |
| `platform-api/.../CaseMemoryStore.java` | #72, #79 (Javadoc) |
| `platform-api/.../MemoryPermissionsTest.java` | #79 |
| `platform-api/.../CaseMemoryStoreSpiTest.java` | #72 |
| `platform/.../NoOpCaseMemoryStore.java` | #72 |
| `platform/.../ReactiveCaseMemoryStore.java` | #72 |
| `platform/.../BlockingToReactiveBridge.java` | #72 |
| `platform/.../NoOpCaseMemoryStoreTest.java` | #72 |
| `platform/.../BlockingToReactiveBridgeThreadingTest.java` | #72 |
| `memory-inmem/.../InMemoryMemoryStore.java` | #72, #79 |
| `memory-jpa/.../JpaMemoryStore.java` | #72, #79 |
| `memory-sqlite/.../SqliteMemoryStore.java` | #72, #79 |
| `memory-mem0/.../Mem0Client.java` | #64 |
| `memory-mem0/.../Mem0CaseMemoryStore.java` | #64, #72, #79 |
| `memory-mem0/.../Mem0CaseMemoryStoreTest.java` | #64, #72 |
| `memory-graphiti/.../GraphitiCaseMemoryStore.java` | #72, #79 |
| `memory-graphiti/.../GraphitiCaseMemoryStoreTest.java` | #72 |
| `identity/.../ScimActorDIDProvider.java` | #62 |
| `testing/.../CaseMemoryStoreContractTest.java` | #72 |
| `garden/docs/protocols/casehub/casememorystore-adapter-asserttenant-contract.md` | #79 |
