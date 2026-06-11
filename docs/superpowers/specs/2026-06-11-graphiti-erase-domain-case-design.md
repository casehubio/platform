# Design: Graphiti `erase(EraseRequest)` — Domain+CaseId Scoped Deletion

**Issue:** platform#75  
**Branch:** `issue-75-graphiti-erase-domain-case`  
**Date:** 2026-06-11 (rev 5)

---

## Problem

`GraphitiCaseMemoryStore.erase(EraseRequest)` throws `MemoryCapabilityException(ERASE_DOMAIN_CASE)` unconditionally. Graphiti's only complete deletion primitive is `DELETE /group/{group_id}` (cascading). The current group_id scheme (`{tenantId}::{entityId}`) is entity-level only — domain and caseId are stored in `source_description` as episode metadata and are invisible to the deletion layer. There is no way to delete a domain+caseId subset without deleting the entire entity group.

This makes Graphiti unsuitable for GDPR Art.17 flows at domain granularity.

---

## Constraint Context (from Garden)

- **GE-20260608-4a1057**: `DELETE /episode/{uuid}` removes only the `EpisodicNode`. LLM-extracted `EntityNode`/`EntityEdge` records persist (upstream: getzep/graphiti#1083). Not GDPR-complete.
- **GE-20260608-8e85f6**: `episode_metadata` exists in graphiti-core but is not exposed by the REST `Message` DTO. `source_description` is the only usable metadata carrier per episode.
- **GE-20260609-616994**: `POST /search` exposes only `group_ids`, `query`, `max_facts`. No server-side group enumeration endpoint exists; `GET /episodes/{groupId}` has no pagination.

---

## Decision: Domain-level group_id

**Chosen scheme:** `{tenantId}::{entityId}::{domain.name()}`

Domain is the correct semantic boundary for a knowledge graph. Within a domain, all cases share entity context (cross-case relationships are Graphiti's primary value over flat vector stores). Partitioning at caseId level would break cross-case entity relationship extraction.

**Rejected alternatives:**
- `{tenantId}::{entityId}::{domain}::{caseId}` — breaks the query model (can't enumerate group_ids by entity without a listing endpoint) and severs cross-case entity relationships
- Episode-level deletion only (no group_id change) — never meets "real" GDPR erasure (EpisodicNode only, derived facts persist)

**Migration:** Existing data stored under `{tenantId}::{entityId}` becomes unreachable. Acceptable — no external users.

---

## SPI Change: `erase(EraseRequest)` → `int`

`CaseMemoryStore.erase(EraseRequest)` changes return type from `void` to `int` for GDPR Art.5(2) audit parity with `eraseEntity()`. The same audit rationale applies at domain granularity. This is a breaking SPI change — all implementations must be updated.

The reactive mirror follows: `ReactiveCaseMemoryStore.erase()` → `Uni<Integer>`. `BlockingToReactiveBridge.erase()` wraps the `int` return as `Uni<Integer>`.

---

## Section 1 — Data Model

```
Before:  group_id = {tenantId}::{entityId}
After:   group_id = {tenantId}::{entityId}::{domain.name()}
```

`compoundGroupId()` becomes a 3-arg static method: `(tenantId, entityId, domain)`.

`extractEntityId(groupId, tenantId)` is updated: strips `{tenantId}::` prefix, then takes the segment up to the next `::` (returns entityId, discards domain suffix).

---

## Section 2 — Write Path

`store()` and `storeAll()` pass `input.domain().name()` to `compoundGroupId()`. The `source_description` format (`"domain=X"` or `"domain=X;caseId=Y"`) is unchanged — it remains the caseId carrier for episode-level filtering in erase.

`sendAdd()` signature, `AddMessage`, `AddMessagesRequest`, and the `/messages` REST call are structurally unchanged. The consequential change is that `sendAdd()` now calls `compoundGroupId(tenantId, entityId, domain.name())` — the group_id value written changes from the 2-segment to the 3-segment form.

---

## Section 3 — Read Path

Both `query()` and `graphQuery()` already carry `domain`. Pass `domain.name()` to `compoundGroupId()`. The group_id is always fully constructible from known query fields.

`episodeToMemory()` uses the updated `extractEntityId()` to parse the 3-segment group_id.

No other mapping logic changes.

---

## Section 4 — Erase Path

### Private helper: `eraseGroup(String groupId) → int`

Shared by both `erase()` Branch 1 and `eraseEntity()`. Handles the pre-fetch, delete, and error semantics in one place:

```
eraseGroup(groupId):
  try:
    episodes = client.getEpisodes(groupId, MAX_EPISODES_FOR_COUNT)
    count = (episodes != null) ? episodes.size() : 0
    client.deleteGroup(groupId)
    return count
  catch WebApplicationException where status == 404:
    return 0   // group never existed — erasure satisfied, no error
  catch WebApplicationException (other):
    throw GraphitiStoreException.from(e)
```

Both `getEpisodes()` and `deleteGroup()` can return 404 (group never existed for that entity+domain combination). 404 is treated as no-op: erasure is trivially satisfied. All other errors propagate as `GraphitiStoreException`.

### Private helper: `getEpisodesOrEmpty(String groupId) → List<GraphitiEpisodicNode>`

Used by Branch 2 only. Returns the episode list for a group, or an empty list if the group has never been written to (404). Other errors propagate as `GraphitiStoreException`. `eraseGroup()` cannot use this helper because it must distinguish 404 (group absent — skip deleteGroup) from empty-but-existing (group present — still call deleteGroup).

```
getEpisodesOrEmpty(groupId):
  try:
    eps = client.getEpisodes(groupId, MAX_EPISODES_FOR_COUNT)
    return eps != null ? eps : List.of()
  catch WebApplicationException where status == 404:
    return List.of()  // group never existed — no episodes to match
  catch WebApplicationException (other):
    throw GraphitiStoreException.from(e)
```

### Private helper: `matchesCaseId(String sourceDescription, String caseId) → boolean`

```java
private static boolean matchesCaseId(String sourceDescription, String caseId) {
    if (sourceDescription == null) return false;
    for (String part : sourceDescription.split(";")) {
        if (part.equals("caseId=" + caseId)) return true;
    }
    return false;
}
```

Splits on `;` for exact key=value matching — prevents `"caseId=123"` matching `"caseId=1234"`.

### `erase(EraseRequest)` → `int` — new implementation

**Branch 1 — domain-level (`caseId == null`):**
1. `MemoryPermissions.assertTenant(request.tenantId(), principal, requestContextActive())`
2. `return eraseGroup(compoundGroupId(tenantId, entityId, domain.name()))`

Deletion is cascading and complete (episodes + EntityNodes + EntityEdges). Count is capped at `MAX_EPISODES_FOR_COUNT`; this is a count limitation only — the deletion itself is always complete.

**Branch 2 — case-level (`caseId != null`):**
1. `assertTenant`
2. `episodes = getEpisodesOrEmpty(groupId)` — empty list if group doesn't exist (404); other errors propagate
3. Filter episodes where `source_description` matches `matchesCaseId(desc, caseId)`
4. For each matching episode:
   ```
   try: DELETE /episode/{uuid}; count++
   catch WebApplicationException where status == 404: count++  // concurrently deleted — erasure satisfied
   catch WebApplicationException (other): throw GraphitiStoreException.from(e)
   ```
5. Return count

**Correctness limitation for Branch 2:** `GET /episodes` has no pagination — only the most recent `MAX_EPISODES_FOR_COUNT` (10,000) episodes are fetched. Domain groups exceeding this cap may have matching case episodes that are silently not erased. For GDPR-complete erasure at case granularity, callers should use domain-level erase (Branch 1, `caseId=null`), which delegates to `eraseGroup()` and is always complete. Additionally, `DELETE /episode/{uuid}` removes only the `EpisodicNode`; LLM-extracted `EntityNode`/`EntityEdge` records may persist pending getzep/graphiti#1083.

### `eraseEntity()` — replace override (tenant-check + known-domains support)

The override is **replaced**, not removed. Removing it would let the SPI default fire without `assertTenant`, violating `casememorystore-adapter-asserttenant-contract.md`. The new override:

```java
@Override
public int eraseEntity(final String entityId, final String tenantId) {
    MemoryPermissions.assertTenant(tenantId, principal, requestContextActive());
    final List<String> domains = knownDomains.orElse(List.of());
    if (domains.isEmpty()) {
        throw new MemoryCapabilityException(MemoryCapability.ERASE_ENTITY, getClass());
    }
    int total = 0;
    for (final String domain : domains) {
        total += eraseGroup(compoundGroupId(tenantId, entityId, domain));
    }
    return total;
}
```

Each domain group is deleted via `eraseGroup()`. A 404 for any domain (entity never had data in that domain) is a no-op returning 0 — safe for any configured domain that was never written.

**Configuration:** `casehub.memory.graphiti.known-domains` — comma-separated domain names declared at deployment (e.g., `investigation,compliance,audit`). Injected as:

```java
@ConfigProperty(name = "casehub.memory.graphiti.known-domains")
Optional<List<String>> knownDomains;
```

**Operational constraint (Javadoc):** domains added after deployment without updating this property will be missed on entity wipe. Operators must update this property before using new domains.

**Mid-loop failure note (Javadoc on the override):** If `eraseGroup()` throws `GraphitiStoreException` for domain N, the loop aborts — domains 0..N-1 are erased, domains N..end are not. Retry is safe: already-erased domain groups return 404 → caught → return 0, so the loop continues past them and picks up the remaining domains.

### `eraseById()` — unchanged

Keeps its override: `assertTenant` then throws `MemoryCapabilityException(ERASE_BY_ID)` — `DELETE /episode/{uuid}` is not GDPR-complete.

---

## Section 5 — Capabilities

`capabilities()` is instance-level (reads `knownDomains`):

```java
@Override
public Set<MemoryCapability> capabilities() {
    // TEMPORAL_GRAPH: client-side filtering on validAt/invalidAt returned per fact.
    // ERASE_BY_ID absent: DELETE /episode/{uuid} only removes EpisodicNode;
    //   derived EntityNode/EntityEdge persist (getzep/graphiti#1083, platform#74).
    // ERASE_ENTITY: declared only when casehub.memory.graphiti.known-domains is configured.
    //   Without it, cross-domain entity wipes are unsupported (no Graphiti group listing endpoint).
    // ERASE_DOMAIN_CASE: domain-level (caseId=null) is cascading and complete;
    //   case-level (caseId!=null) bounded to MAX_EPISODES_FOR_COUNT (see Javadoc).
    final var caps = new HashSet<>(Set.of(
        MemoryCapability.CHRONOLOGICAL_ORDER,
        MemoryCapability.SINCE_FILTER,
        MemoryCapability.BATCH_STORE,
        MemoryCapability.SEMANTIC_SEARCH,
        MemoryCapability.TEMPORAL_GRAPH,
        MemoryCapability.FACT_SEARCH,
        MemoryCapability.DOMAIN_SCOPED,
        MemoryCapability.ERASE_DOMAIN_CASE
    ));
    if (!knownDomains.orElse(List.of()).isEmpty()) {
        caps.add(MemoryCapability.ERASE_ENTITY);
    }
    return Set.copyOf(caps);
}
```

---

## Section 6 — Tests

### `GraphitiCaseMemoryStoreTest` (default config — no known-domains)

**Constant update:** `GROUP_ID = TENANT + "::" + ENTITY + "::" + DOMAIN.name()` → `"tenant-1::actor-1::investigation"`.

**Two inline literals also require domain appended:**
- `query_chronological_last_n_equals_limit_times_entity_count`: `"/episodes/tenant-1::actor-2"` → `"/episodes/tenant-1::actor-2::investigation"`
- `graphQuery_multi_entity_issues_one_search_per_entity_entity_order_concat`: `"tenant-1::actor-2"` → `"tenant-1::actor-2::investigation"` in both stub matchers

**Tests to remove** (behavior changed — these will fail after implementation):
- `erase_EraseRequest_throws_MemoryCapabilityException_no_http_call` — documented the old stub behavior; erase() is now a working operation
- `erase_tenant_mismatch_throws_before_capability_check` — superseded by `erase_domain_only_tenant_mismatch_throws_before_http`
- `eraseEntity_deletes_group_and_returns_episode_count` — happy-path moves to `KnownDomainsTest.eraseEntity_with_known_domains_deletes_each_domain_group`; without known-domains, eraseEntity() now throws

**New `erase(EraseRequest)` tests:**
- `erase_domain_only_deletes_group_and_returns_count` — GET episodes returns 3, DELETE /group called, returns 3
- `erase_domain_only_tenant_mismatch_throws_before_http` — SecurityException, no HTTP
- `erase_with_caseId_deletes_only_matching_episodes` — 3 episodes (2 matching `caseId=case-99`, 1 not), only matching UUIDs deleted, returns 2
- `erase_with_caseId_no_matches_returns_zero` — no matching episodes, returns 0, no DELETE /episode calls

**Updated `eraseEntity` tests:**
- `eraseEntity_tenant_mismatch_throws_SecurityException` — renamed from `eraseEntity_tenant_mismatch_throws_before_http` (assertTenant still runs in replacement override)
- `eraseEntity_without_known_domains_throws_MemoryCapabilityException` — correct tenant, no known-domains property → `MemoryCapabilityException(ERASE_ENTITY)`

**Updated capability test:**
- `assertFalse(caps.contains(MemoryCapability.DOMAIN_SCOPED))` → `assertTrue(...)` (was assertFalse — must invert)
- `assertFalse(caps.contains(MemoryCapability.ERASE_DOMAIN_CASE))` → `assertTrue(...)`
- `assertTrue(caps.contains(MemoryCapability.ERASE_ENTITY))` → `assertFalse(...)` (no known-domains configured)

### `GraphitiCaseMemoryStoreKnownDomainsTest` (separate class, separate port)

A new `@QuarkusTest` class annotated with `@io.quarkus.test.junit.TestProfile(KnownDomainsTestProfile.class)`.

`KnownDomainsTestProfile` implements `QuarkusTestProfile` and overrides `getConfigOverrides()` to set:
- `casehub.memory.graphiti.known-domains=investigation`
- `quarkus.rest-client.graphiti.url=http://localhost:39201`

The class uses its own `WireMockServer` on port 39201 to avoid conflict with the 39200 WireMock instance in `GraphitiCaseMemoryStoreTest`.

Tests in this class:
- `eraseEntity_with_known_domains_deletes_each_domain_group` — stubs GET episodes (3 results) and DELETE /group for `tenant-1::actor-1::investigation`; asserts both called, returns 3
- `eraseEntity_with_known_domains_getEpisodes_404_returns_zero` — no WireMock stubs; WireMock returns 404 by default for GET /episodes; eraseGroup() catches 404 → returns 0; asserts no exception, no DELETE /group call, returns 0. Tests the GET-404 path in eraseGroup().
- `eraseEntity_with_known_domains_deleteGroup_404_returns_zero` — stubs GET /episodes → 200 with empty list (group exists, 0 episodes); stubs DELETE /group → 404; eraseGroup() catches DELETE 404 → returns 0; asserts no exception, returns 0. Tests the DELETE-404 path in eraseGroup().
- `capabilities_includes_ERASE_ENTITY_when_known_domains_configured` — asserts `caps.contains(MemoryCapability.ERASE_ENTITY)`

`KnownDomainsTestProfile` is its own file added to `Files Changed`.

### `CaseMemoryStoreContractTest` — new test

Add one new test to verify `erase()` return type contract:

```java
@Test
void erase_returns_non_negative_count() {
    store().store(input("to erase"));
    final int result = store().erase(eraseRequest());  // null caseId — domain-level
    assertTrue(result >= 0, "erase must return non-negative count");
}
```

### `CaseMemoryStoreSpiTest` — anonymous impl change

```java
// before:
@Override public void erase(EraseRequest r) {}

// after:
@Override public int erase(EraseRequest r) { return 0; }
```

---

## Mem0 `erase()` Implementation Note

`Mem0CaseMemoryStore.erase()` currently calls `client.deleteAll(userId, domain, caseId)` which returns void. To return `int`, apply the same pre-list pattern as `eraseEntity()`:

```java
final Mem0ListResponse listed = client.list(userId, request.domain().name(), request.caseId());
final int count = listed.results() != null ? listed.results().size() : 0;
client.deleteAll(userId, request.domain().name(), request.caseId());
return count;
```

This adds one REST round-trip per `erase()` call. The race caveat is identical to `eraseEntity()`: new writes arriving between `list()` and `deleteAll()` may cause the returned count to understate the actual deletion.

---

## Files Changed

### SPI + reactive layer (breaking change)
| File | Change |
|------|--------|
| `platform-api/.../CaseMemoryStore.java` | `erase(EraseRequest)` → `int` |
| `platform-api/.../CaseMemoryStoreSpiTest.java` | `void erase(EraseRequest r) {}` → `int erase(EraseRequest r) { return 0; }` |
| `platform/.../ReactiveCaseMemoryStore.java` | `erase()` → `Uni<Integer>` |
| `platform/.../BlockingToReactiveBridge.java` | `erase()` wraps `int` return as `Uni<Integer>` |
| `platform/.../NoOpCaseMemoryStore.java` | `erase()` returns 0 |
| `testing/.../CaseMemoryStoreContractTest.java` | Add `erase_returns_non_negative_count` test |

### Adapters (return type)
| File | Change |
|------|--------|
| `memory-inmem/.../InMemoryMemoryStore.java` | `erase()` returns count |
| `memory-jpa/.../JpaMemoryStore.java` | `erase()` returns affected-row count |
| `memory-sqlite/.../SqliteMemoryStore.java` | `erase()` returns affected-row count |
| `memory-mem0/.../Mem0CaseMemoryStore.java` | `erase()` pre-list + deleteAll, returns count |

### Graphiti adapter (primary change)
| File | Change |
|------|--------|
| `memory-graphiti/.../GraphitiCaseMemoryStore.java` | 3-arg `compoundGroupId`; `extractEntityId` updated; `erase()` → `int` with 2-branch impl; `eraseGroup()` private helper; `getEpisodesOrEmpty()` private helper; `matchesCaseId()` helper; `eraseEntity()` replaced with assertTenant + known-domains loop; `capabilities()` instance-level with conditional `ERASE_ENTITY`; `@ConfigProperty knownDomains` |
| `memory-graphiti/.../GraphitiCaseMemoryStoreTest.java` | `GROUP_ID` constant; 2 inline literals; remove 3 stale tests; new erase tests; updated eraseEntity and capabilities tests |
| `memory-graphiti/.../GraphitiCaseMemoryStoreKnownDomainsTest.java` | New @QuarkusTest class, port 39201 |
| `memory-graphiti/.../KnownDomainsTestProfile.java` | New @TestProfile: known-domains=investigation, url=port 39201 |

No Flyway migrations. No changes outside the files listed above.

---

## Acceptance Criteria (from issue)

- `erase(EraseRequest)` executes a real domain+caseId scoped deletion ✅
- `ERASE_DOMAIN_CASE` added to `GraphitiCaseMemoryStore.capabilities()` ✅
- `erase(EraseRequest)` returns `int` count (GDPR Art.5(2) audit parity) ✅
- `eraseEntity()` reinstated via `casehub.memory.graphiti.known-domains` config ✅
