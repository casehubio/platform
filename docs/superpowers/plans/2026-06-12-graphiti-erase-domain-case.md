# Graphiti erase(EraseRequest) Domain+CaseId Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement domain-scoped group_id partitioning in `GraphitiCaseMemoryStore` so that `erase(EraseRequest)` performs real GDPR Art.17-compliant domain-level deletion and `ERASE_DOMAIN_CASE` is declared in capabilities, while simultaneously changing `CaseMemoryStore.erase()` to return `int` (GDPR Art.5(2) audit parity with `eraseEntity()`).

**Architecture:** Domain becomes the third segment of the Graphiti group_id (`{tenantId}::{entityId}::{domain}`), enabling `DELETE /group/{groupId}` for complete domain-level erasure. Case-level erasure uses episode-by-episode deletion (best-effort, bounded by pagination). `eraseEntity()` is reinstated via an optional `casehub.memory.graphiti.known-domains` config property that iterates a DELETE per known domain group.

**Tech Stack:** Java 21, Quarkus 3.x, MicroProfile REST Client, MicroProfile Config, WireMock (tests), JUnit 5.

---

## File Map

| File | Action |
|------|--------|
| `platform-api/src/main/java/io/casehub/platform/api/memory/CaseMemoryStore.java` | Modify: `void erase()` → `int erase()` |
| `platform-api/src/test/java/io/casehub/platform/api/memory/CaseMemoryStoreSpiTest.java` | Modify: anonymous impl `void erase()` → `int erase()` |
| `platform/src/main/java/io/casehub/platform/memory/ReactiveCaseMemoryStore.java` | Modify: `Uni<Void> erase()` → `Uni<Integer> erase()` |
| `platform/src/main/java/io/casehub/platform/memory/BlockingToReactiveBridge.java` | Modify: wrap `int` return as `Uni<Integer>` |
| `platform/src/main/java/io/casehub/platform/memory/NoOpCaseMemoryStore.java` | Modify: `void erase()` → `int erase() { return 0; }` |
| `memory-inmem/src/main/java/io/casehub/platform/memory/inmem/InMemoryMemoryStore.java` | Modify: `erase()` returns count via AtomicInteger |
| `memory-jpa/src/main/java/io/casehub/platform/memory/jpa/JpaMemoryStore.java` | Modify: `erase()` returns `executeUpdate()` count |
| `memory-sqlite/src/main/java/io/casehub/platform/memory/sqlite/SqliteMemoryStore.java` | Modify: `erase()` returns `executeUpdate()` count |
| `memory-mem0/src/main/java/io/casehub/platform/memory/mem0/Mem0CaseMemoryStore.java` | Modify: `erase()` adds pre-list, returns count |
| `testing/src/main/java/io/casehub/platform/testing/memory/CaseMemoryStoreContractTest.java` | Modify: add `erase_returns_non_negative_count` test |
| `memory-graphiti/src/main/java/io/casehub/platform/memory/graphiti/GraphitiCaseMemoryStore.java` | Modify: full implementation per spec |
| `memory-graphiti/src/test/java/io/casehub/platform/memory/graphiti/GraphitiCaseMemoryStoreTest.java` | Modify: GROUP_ID, inline literals, test removals, new tests |
| `memory-graphiti/src/test/java/io/casehub/platform/memory/graphiti/KnownDomainsTestProfile.java` | Create: `@TestProfile` for known-domains tests |
| `memory-graphiti/src/test/java/io/casehub/platform/memory/graphiti/GraphitiCaseMemoryStoreKnownDomainsTest.java` | Create: `@QuarkusTest` on port 39201 |

---

## Task 1: Break the SPI — change `erase()` to return `int`, fix all non-Graphiti implementations

This is a mechanical compile-break: change the return type in the SPI and update every implementation. All changes must be applied together before the build can pass.

**Files:**
- Modify: `platform-api/src/main/java/io/casehub/platform/api/memory/CaseMemoryStore.java`
- Modify: `platform-api/src/test/java/io/casehub/platform/api/memory/CaseMemoryStoreSpiTest.java`
- Modify: `platform/src/main/java/io/casehub/platform/memory/ReactiveCaseMemoryStore.java`
- Modify: `platform/src/main/java/io/casehub/platform/memory/BlockingToReactiveBridge.java`
- Modify: `platform/src/main/java/io/casehub/platform/memory/NoOpCaseMemoryStore.java`
- Modify: `memory-inmem/src/main/java/io/casehub/platform/memory/inmem/InMemoryMemoryStore.java`
- Modify: `memory-jpa/src/main/java/io/casehub/platform/memory/jpa/JpaMemoryStore.java`
- Modify: `memory-sqlite/src/main/java/io/casehub/platform/memory/sqlite/SqliteMemoryStore.java`
- Modify: `memory-mem0/src/main/java/io/casehub/platform/memory/mem0/Mem0CaseMemoryStore.java`

- [ ] **Step 1: Change `CaseMemoryStore.erase()` return type**

In `platform-api/src/main/java/io/casehub/platform/api/memory/CaseMemoryStore.java`, find the Javadoc block before `void erase(EraseRequest request)` and update the method signature:

```java
    /**
     * Erase memories matching the request. Domain is required — use {@link #eraseEntity}
     * for GDPR Art.17 cross-domain full-entity wipe.
     *
     * <p>Adapters MUST perform hard deletion.
     * Adapters MUST call {@link MemoryPermissions#assertTenant} before delegating to the backend.
     *
     * <p>Adapters that do not declare {@link MemoryCapability#ERASE_DOMAIN_CASE} will throw
     * {@link MemoryCapabilityException}. Check {@link #capabilities()} before calling on
     * adapters that may not support domain+caseId scoped deletion.
     *
     * @return count of memory records erased (for GDPR Art.5(2) audit logging)
     */
    int erase(EraseRequest request);
```

- [ ] **Step 2: Fix `CaseMemoryStoreSpiTest` anonymous impl**

In `platform-api/src/test/java/io/casehub/platform/api/memory/CaseMemoryStoreSpiTest.java`, find line 18 and change:

```java
        @Override public void erase(EraseRequest r) {}
```
to:
```java
        @Override public int erase(EraseRequest r) { return 0; }
```

- [ ] **Step 3: Update `ReactiveCaseMemoryStore.erase()` to `Uni<Integer>`**

In `platform/src/main/java/io/casehub/platform/memory/ReactiveCaseMemoryStore.java`, change line 17:

```java
    Uni<Void> erase(EraseRequest request);
```
to:
```java
    /**
     * Reactive mirror of {@link io.casehub.platform.api.memory.CaseMemoryStore#erase}.
     * Returns count of records erased (see blocking SPI for semantics).
     */
    Uni<Integer> erase(EraseRequest request);
```

- [ ] **Step 4: Fix `BlockingToReactiveBridge.erase()`**

In `platform/src/main/java/io/casehub/platform/memory/BlockingToReactiveBridge.java`, replace the `erase` method (currently lines 39–43):

```java
    @Override
    public Uni<Integer> erase(EraseRequest request) {
        return Uni.createFrom().item(() -> delegate.erase(request))
            .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }
```

- [ ] **Step 5: Fix `NoOpCaseMemoryStore.erase()`**

In `platform/src/main/java/io/casehub/platform/memory/NoOpCaseMemoryStore.java`, line 29, change:

```java
    @Override public void erase(final EraseRequest request) {}
```
to:
```java
    @Override public int erase(final EraseRequest request) { return 0; }
```

- [ ] **Step 6: Fix `InMemoryMemoryStore.erase()`**

In `memory-inmem/src/main/java/io/casehub/platform/memory/inmem/InMemoryMemoryStore.java`, replace the `erase` method:

```java
    @Override
    public int erase(EraseRequest request) {
        MemoryPermissions.assertTenant(request.tenantId(), principal, requestContextActive());
        final var key = new BucketKey(request.tenantId(), request.entityId(), request.domain());
        final var removed = new AtomicInteger();
        store.computeIfPresent(key, (k, memories) -> {
            final var remaining = new CopyOnWriteArrayList<>(memories.stream()
                .filter(m -> request.caseId() != null && !request.caseId().equals(m.caseId()))
                .toList());
            removed.set(memories.size() - remaining.size());
            return remaining;
        });
        return removed.get();
    }
```

Note: `AtomicInteger` is already imported in this file (used by `eraseEntity()`).

- [ ] **Step 7: Fix `JpaMemoryStore.erase()`**

In `memory-jpa/src/main/java/io/casehub/platform/memory/jpa/JpaMemoryStore.java`, replace the `erase` method:

```java
    @Override
    @Transactional(TxType.REQUIRED)
    public int erase(EraseRequest request) {
        MemoryPermissions.assertTenant(request.tenantId(), principal, requestContextActive());

        var jpql = new StringBuilder(
            "DELETE FROM MemoryEntry WHERE tenantId = :tenantId AND entityId = :entityId AND domain = :domain");
        if (request.caseId() != null) jpql.append(" AND caseId = :caseId");

        var q = em.createQuery(jpql.toString())
            .setParameter("tenantId", request.tenantId())
            .setParameter("entityId", request.entityId())
            .setParameter("domain",   request.domain().name());
        if (request.caseId() != null) q.setParameter("caseId", request.caseId());

        final int count = q.executeUpdate();
        em.clear();
        return count;
    }
```

- [ ] **Step 8: Fix `SqliteMemoryStore.erase()`**

In `memory-sqlite/src/main/java/io/casehub/platform/memory/sqlite/SqliteMemoryStore.java`, replace the `erase` method:

```java
    @Override
    public int erase(EraseRequest request) {
        MemoryPermissions.assertTenant(request.tenantId(), principal, requestContextActive());
        final StringBuilder sql = new StringBuilder(
            "DELETE FROM memory_entry WHERE tenant_id = ? AND entity_id = ? AND domain = ?");
        if (request.caseId() != null) sql.append(" AND case_id = ?");
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            ps.setString(idx++, request.tenantId());
            ps.setString(idx++, request.entityId());
            ps.setString(idx++, request.domain().name());
            if (request.caseId() != null) ps.setString(idx, request.caseId());
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("erase() failed", e);
        }
    }
```

- [ ] **Step 9: Fix `Mem0CaseMemoryStore.erase()`**

In `memory-mem0/src/main/java/io/casehub/platform/memory/mem0/Mem0CaseMemoryStore.java`, replace the `erase` method:

```java
    @Timed(value = "casehub.memory.mem0", histogram = true, extraTags = {"operation", "erase"})
    @Override
    public int erase(EraseRequest request) {
        MemoryPermissions.assertTenant(request.tenantId(), principal, requestContextActive());
        // Pre-list for count — same race caveat as eraseEntity(): writes arriving between
        // list() and deleteAll() may cause the returned count to understate actual deletion.
        try {
            final String userId = compoundUserId(request.tenantId(), request.entityId());
            final Mem0ListResponse listed = client.list(userId, request.domain().name(), request.caseId());
            final int count = listed.results() != null ? listed.results().size() : 0;
            client.deleteAll(userId, request.domain().name(), request.caseId());
            return count;
        } catch (WebApplicationException e) {
            throw toStoreException(e);
        }
    }
```

- [ ] **Step 10: Build the full project to confirm all adapters compile**

```bash
mvn --batch-mode install -DskipTests
```

Expected: `BUILD SUCCESS`. If any module fails with a compile error, fix it before continuing.

- [ ] **Step 11: Run the full test suite to confirm no regressions**

```bash
mvn --batch-mode install
```

Expected: `BUILD SUCCESS`. `GraphitiCaseMemoryStoreTest` currently has tests that will still pass (erase throws, eraseEntity works) — they will break in Task 3. That is intentional.

- [ ] **Step 12: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/platform add \
  platform-api/src/main/java/io/casehub/platform/api/memory/CaseMemoryStore.java \
  platform-api/src/test/java/io/casehub/platform/api/memory/CaseMemoryStoreSpiTest.java \
  platform/src/main/java/io/casehub/platform/memory/ReactiveCaseMemoryStore.java \
  platform/src/main/java/io/casehub/platform/memory/BlockingToReactiveBridge.java \
  platform/src/main/java/io/casehub/platform/memory/NoOpCaseMemoryStore.java \
  memory-inmem/src/main/java/io/casehub/platform/memory/inmem/InMemoryMemoryStore.java \
  memory-jpa/src/main/java/io/casehub/platform/memory/jpa/JpaMemoryStore.java \
  memory-sqlite/src/main/java/io/casehub/platform/memory/sqlite/SqliteMemoryStore.java \
  memory-mem0/src/main/java/io/casehub/platform/memory/mem0/Mem0CaseMemoryStore.java
git -C /Users/mdproctor/claude/casehub/platform commit -m "fix(platform#75): erase(EraseRequest) → int across all adapters"
```

---

## Task 2: Update Graphiti tests — RED state (write failing tests before implementing)

Update `GROUP_ID` to the 3-segment format, remove the three stale tests, update the capabilities assertion, and add new tests that will FAIL until the implementation is done.

**Files:**
- Modify: `memory-graphiti/src/test/java/io/casehub/platform/memory/graphiti/GraphitiCaseMemoryStoreTest.java`

- [ ] **Step 1: Update `GROUP_ID` constant**

Find line 28:
```java
    static final String GROUP_ID    = TENANT + "::" + ENTITY;
```
Change to:
```java
    static final String GROUP_ID    = TENANT + "::" + ENTITY + "::" + DOMAIN.name();
```

- [ ] **Step 2: Update two inline literals not covered by the constant**

**Literal 1** — in `query_chronological_last_n_equals_limit_times_entity_count`, find:
```java
        wireMock.verify(getRequestedFor(urlPathEqualTo("/episodes/tenant-1::actor-2"))
            .withQueryParam("last_n", equalTo("10")));
```
Change to:
```java
        wireMock.verify(getRequestedFor(urlPathEqualTo("/episodes/tenant-1::actor-2::investigation"))
            .withQueryParam("last_n", equalTo("10")));
```

**Literal 2** — in `graphQuery_multi_entity_issues_one_search_per_entity_entity_order_concat`, find both occurrences of `"tenant-1::actor-2"` in the `withRequestBody(matchingJsonPath(...))` stubs and change each to `"tenant-1::actor-2::investigation"`.

- [ ] **Step 3: Remove three stale tests**

Delete these three complete test methods from the file:

1. `erase_EraseRequest_throws_MemoryCapabilityException_no_http_call` — this documented the old stub behavior; erase() is now a working operation
2. `erase_tenant_mismatch_throws_before_capability_check` — superseded by the new `erase_domain_only_tenant_mismatch_throws_before_http`
3. `eraseEntity_deletes_group_and_returns_episode_count` — happy-path moves to `KnownDomainsTest`; without known-domains, eraseEntity() now throws

- [ ] **Step 4: Update `eraseEntity_tenant_mismatch_throws_before_http` rename + assertion**

Find `eraseEntity_tenant_mismatch_throws_before_http` and rename it to `eraseEntity_tenant_mismatch_throws_SecurityException`. The body is unchanged — wrong tenant still throws `SecurityException` because the replacement override calls `assertTenant` first:

```java
    @Test
    void eraseEntity_tenant_mismatch_throws_SecurityException() {
        assertThrows(SecurityException.class, () -> store.eraseEntity(ENTITY, "wrong-tenant"));
        wireMock.verify(0, deleteRequestedFor(anyUrl()));
        wireMock.verify(0, getRequestedFor(anyUrl()));
    }
```

- [ ] **Step 5: Add `eraseEntity_without_known_domains_throws_MemoryCapabilityException`**

Add this test in the `// ── eraseEntity` section:

```java
    @Test
    void eraseEntity_without_known_domains_throws_MemoryCapabilityException() {
        // No casehub.memory.graphiti.known-domains configured → capability absent → exception.
        final var ex = assertThrows(MemoryCapabilityException.class,
            () -> store.eraseEntity(ENTITY, TENANT));
        assertEquals(MemoryCapability.ERASE_ENTITY, ex.required());
        wireMock.verify(0, getRequestedFor(anyUrl()));
        wireMock.verify(0, deleteRequestedFor(anyUrl()));
    }
```

- [ ] **Step 6: Update the capabilities test**

Find `capabilities_includes_expected_set` and update assertions:

```java
    @Test
    void capabilities_includes_expected_set() {
        final var caps = store.capabilities();
        assertTrue(caps.contains(MemoryCapability.CHRONOLOGICAL_ORDER));
        assertTrue(caps.contains(MemoryCapability.SEMANTIC_SEARCH));
        assertTrue(caps.contains(MemoryCapability.TEMPORAL_GRAPH));
        assertTrue(caps.contains(MemoryCapability.FACT_SEARCH));
        assertTrue(caps.contains(MemoryCapability.DOMAIN_SCOPED));        // added
        assertTrue(caps.contains(MemoryCapability.ERASE_DOMAIN_CASE));    // added
        assertFalse(caps.contains(MemoryCapability.ERASE_ENTITY));        // removed (no known-domains configured)
        assertFalse(caps.contains(MemoryCapability.CASE_SCOPED));
        assertFalse(caps.contains(MemoryCapability.ENTITY_TYPE_FILTER));
        assertFalse(caps.contains(MemoryCapability.ERASE_BY_ID));
    }
```

- [ ] **Step 7: Add four new `erase(EraseRequest)` tests**

Add in the `// ── erase(EraseRequest)` section:

```java
    // ── erase(EraseRequest) ───────────────────────────────────────────────────

    @Test
    void erase_domain_only_deletes_group_and_returns_count() {
        wireMock.stubFor(get(urlPathEqualTo("/episodes/" + GROUP_ID))
            .willReturn(okJson("""
                [
                  {"uuid":"ep-1","content":"a","created_at":"2026-01-01T00:00:00Z","group_id":"%s"},
                  {"uuid":"ep-2","content":"b","created_at":"2026-01-02T00:00:00Z","group_id":"%s"},
                  {"uuid":"ep-3","content":"c","created_at":"2026-01-03T00:00:00Z","group_id":"%s"}
                ]
                """.formatted(GROUP_ID, GROUP_ID, GROUP_ID))));
        wireMock.stubFor(delete(urlEqualTo("/group/" + GROUP_ID))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"success\":true,\"message\":\"deleted\"}")));

        final int count = store.erase(new EraseRequest(ENTITY, DOMAIN, TENANT, null));
        assertEquals(3, count);
        wireMock.verify(getRequestedFor(urlPathEqualTo("/episodes/" + GROUP_ID)));
        wireMock.verify(deleteRequestedFor(urlEqualTo("/group/" + GROUP_ID)));
    }

    @Test
    void erase_domain_only_tenant_mismatch_throws_before_http() {
        assertThrows(SecurityException.class,
            () -> store.erase(new EraseRequest(ENTITY, DOMAIN, "wrong-tenant", null)));
        wireMock.verify(0, getRequestedFor(anyUrl()));
        wireMock.verify(0, deleteRequestedFor(anyUrl()));
    }

    @Test
    void erase_with_caseId_deletes_only_matching_episodes() {
        wireMock.stubFor(get(urlPathEqualTo("/episodes/" + GROUP_ID))
            .willReturn(okJson("""
                [
                  {"uuid":"ep-1","content":"a","created_at":"2026-01-01T00:00:00Z",
                   "group_id":"%s","source_description":"domain=investigation;caseId=case-99"},
                  {"uuid":"ep-2","content":"b","created_at":"2026-01-02T00:00:00Z",
                   "group_id":"%s","source_description":"domain=investigation;caseId=case-99"},
                  {"uuid":"ep-3","content":"c","created_at":"2026-01-03T00:00:00Z",
                   "group_id":"%s","source_description":"domain=investigation;caseId=other-case"}
                ]
                """.formatted(GROUP_ID, GROUP_ID, GROUP_ID))));
        wireMock.stubFor(delete(urlMatching("/episode/ep-\\d+"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"success\":true}")));

        final int count = store.erase(new EraseRequest(ENTITY, DOMAIN, TENANT, "case-99"));
        assertEquals(2, count);
        wireMock.verify(deleteRequestedFor(urlEqualTo("/episode/ep-1")));
        wireMock.verify(deleteRequestedFor(urlEqualTo("/episode/ep-2")));
        wireMock.verify(0, deleteRequestedFor(urlEqualTo("/episode/ep-3")));
    }

    @Test
    void erase_with_caseId_no_matches_returns_zero() {
        wireMock.stubFor(get(urlPathEqualTo("/episodes/" + GROUP_ID))
            .willReturn(okJson("""
                [
                  {"uuid":"ep-1","content":"a","created_at":"2026-01-01T00:00:00Z",
                   "group_id":"%s","source_description":"domain=investigation;caseId=other-case"}
                ]
                """.formatted(GROUP_ID))));

        final int count = store.erase(new EraseRequest(ENTITY, DOMAIN, TENANT, "case-99"));
        assertEquals(0, count);
        wireMock.verify(0, deleteRequestedFor(anyUrl()));
    }
```

- [ ] **Step 8: Run graphiti tests to confirm RED state**

```bash
mvn --batch-mode -pl memory-graphiti test
```

Expected: multiple test failures. Specifically:
- All group_id-based tests fail (stubs expect 3-segment URL, code still generates 2-segment)
- New `erase_domain_only_*` and `erase_with_caseId_*` tests fail (erase() not yet implemented)
- `eraseEntity_without_known_domains_throws_MemoryCapabilityException` fails (eraseEntity() still works)
- Capabilities test fails (`ERASE_DOMAIN_CASE` and `DOMAIN_SCOPED` not yet declared)

If any stale test PASSES that should have been deleted, stop and re-check Step 3.

---

## Task 3: Implement Graphiti adapter — helpers and write/read path changes

Add the three private helpers, update `compoundGroupId` to 3-arg, update `extractEntityId`, and propagate the domain segment through all existing read/write operations.

**Files:**
- Modify: `memory-graphiti/src/main/java/io/casehub/platform/memory/graphiti/GraphitiCaseMemoryStore.java`

- [ ] **Step 1: Add `@ConfigProperty` import and field**

At the top of the import list, add:
```java
import org.eclipse.microprofile.config.inject.ConfigProperty;
```

After the existing `@Inject @RestClient GraphitiClient client;` and `@Inject CurrentPrincipal principal;` fields, add:

```java
    @ConfigProperty(name = "casehub.memory.graphiti.known-domains")
    Optional<List<String>> knownDomains;
```

- [ ] **Step 2: Update `compoundGroupId` to 3-arg**

Find the existing:
```java
    static String compoundGroupId(final String tenantId, final String entityId) {
        return tenantId + SEP + entityId;
    }
```

Replace with:
```java
    static String compoundGroupId(final String tenantId, final String entityId, final String domain) {
        return tenantId + SEP + entityId + SEP + domain;
    }
```

- [ ] **Step 3: Update `extractEntityId` to handle 3-segment group_id**

Find the existing:
```java
    static String extractEntityId(final String groupId, final String tenantId) {
        if (groupId == null) return "";
        final String prefix = tenantId + SEP;
        return groupId.startsWith(prefix) ? groupId.substring(prefix.length()) : groupId;
    }
```

Replace with:
```java
    static String extractEntityId(final String groupId, final String tenantId) {
        if (groupId == null) return "";
        final String prefix = tenantId + SEP;
        if (!groupId.startsWith(prefix)) return groupId;
        final String afterTenant = groupId.substring(prefix.length()); // "{entityId}::{domain}"
        final int sepIdx = afterTenant.indexOf(SEP);
        return sepIdx < 0 ? afterTenant : afterTenant.substring(0, sepIdx);
    }
```

- [ ] **Step 4: Update `sendAdd()` to use 3-arg `compoundGroupId`**

In `sendAdd()`, find:
```java
        final var request = new AddMessagesRequest(
            compoundGroupId(input.tenantId(), input.entityId()),
            List.of(message)
        );
```

Replace with:
```java
        final var request = new AddMessagesRequest(
            compoundGroupId(input.tenantId(), input.entityId(), input.domain().name()),
            List.of(message)
        );
```

- [ ] **Step 5: Update `searchForEntity()` to use 3-arg `compoundGroupId`**

In `searchForEntity()`, find:
```java
        final var req = new GraphitiSearchRequest(
            List.of(compoundGroupId(query.tenantId(), entityId)),
            query.question(),
            query.limit()
        );
```

Replace with:
```java
        final var req = new GraphitiSearchRequest(
            List.of(compoundGroupId(query.tenantId(), entityId, query.domain().name())),
            query.question(),
            query.limit()
        );
```

- [ ] **Step 6: Update `episodesForEntity()` to use 3-arg `compoundGroupId`**

In `episodesForEntity()`, find:
```java
        final String groupId = compoundGroupId(query.tenantId(), entityId);
```

Replace with:
```java
        final String groupId = compoundGroupId(query.tenantId(), entityId, query.domain().name());
```

- [ ] **Step 7: Update `graphQuery()` to use 3-arg `compoundGroupId`**

In `graphQuery()`, find:
```java
            final var req = new GraphitiSearchRequest(
                List.of(compoundGroupId(query.tenantId(), entityId)),
                query.question(),
                query.limit()
            );
```

Replace with:
```java
            final var req = new GraphitiSearchRequest(
                List.of(compoundGroupId(query.tenantId(), entityId, query.domain().name())),
                query.question(),
                query.limit()
            );
```

- [ ] **Step 8: Add `eraseGroup()` private helper**

Add this method in the `// ── erase` section before `erase()`:

```java
    /**
     * Fetches episode count then performs cascading DELETE of all episodes, entities,
     * and facts for a group. Returns 0 if the group has never been created (404).
     * Non-404 errors propagate as {@link GraphitiStoreException}.
     */
    private int eraseGroup(final String groupId) {
        try {
            final List<GraphitiEpisodicNode> episodes = client.getEpisodes(groupId, MAX_EPISODES_FOR_COUNT);
            final int count = episodes != null ? episodes.size() : 0;
            client.deleteGroup(groupId);
            return count;
        } catch (final WebApplicationException e) {
            if (e.getResponse() != null && e.getResponse().getStatus() == 404) {
                return 0; // group never existed — erasure satisfied
            }
            throw GraphitiStoreException.from(e);
        }
    }
```

- [ ] **Step 9: Add `getEpisodesOrEmpty()` private helper**

Add this method after `eraseGroup()`:

```java
    /**
     * Returns episodes for a group, or an empty list if the group does not exist (404).
     * Unlike {@link #eraseGroup}, this helper does NOT call deleteGroup — it cannot
     * distinguish "group absent" from "group exists with 0 episodes" and must not skip
     * deletion of empty-but-existing groups.
     */
    private List<GraphitiEpisodicNode> getEpisodesOrEmpty(final String groupId) {
        try {
            final List<GraphitiEpisodicNode> eps = client.getEpisodes(groupId, MAX_EPISODES_FOR_COUNT);
            return eps != null ? eps : List.of();
        } catch (final WebApplicationException e) {
            if (e.getResponse() != null && e.getResponse().getStatus() == 404) {
                return List.of(); // group never existed — no episodes to filter
            }
            throw GraphitiStoreException.from(e);
        }
    }
```

- [ ] **Step 10: Add `matchesCaseId()` private static helper**

Add this method after `sourceDescription()`:

```java
    private static boolean matchesCaseId(final String sourceDescription, final String caseId) {
        if (sourceDescription == null) return false;
        for (final String part : sourceDescription.split(";")) {
            if (part.equals("caseId=" + caseId)) return true;
        }
        return false;
    }
```

- [ ] **Step 11: Run graphiti tests to confirm store/query tests now pass**

```bash
mvn --batch-mode -pl memory-graphiti test
```

Expected: store and query tests pass (GROUP_ID now matches the 3-segment format produced by the updated code). Erase and eraseEntity tests still fail — that is correct, they haven't been implemented yet.

---

## Task 4: Implement `erase(EraseRequest)` — both branches

**Files:**
- Modify: `memory-graphiti/src/main/java/io/casehub/platform/memory/graphiti/GraphitiCaseMemoryStore.java`

- [ ] **Step 1: Replace the `erase()` method**

Find the current `erase()` method:
```java
    @Timed(value = "casehub.memory.graphiti", histogram = true, extraTags = {"operation", "erase"})
    @Override
    public void erase(final EraseRequest request) {
        MemoryPermissions.assertTenant(request.tenantId(), principal, requestContextActive());
        requireCapability(MemoryCapability.ERASE_DOMAIN_CASE); // throws
    }
```

Replace with:
```java
    /**
     * Erases memories for an entity within a specific domain (and optionally a specific case).
     *
     * <p><b>Branch 1 — domain-level ({@code caseId == null}):</b> calls
     * {@link #eraseGroup} which performs a cascading {@code DELETE /group/{groupId}}.
     * Complete: removes episodes, extracted entities, and relationship facts. Count is
     * capped at {@link #MAX_EPISODES_FOR_COUNT} — this is a count-only limitation;
     * the deletion itself is always complete.
     *
     * <p><b>Branch 2 — case-level ({@code caseId != null}):</b> fetches episodes via
     * {@link #getEpisodesOrEmpty}, filters by {@code source_description}, and issues a
     * {@code DELETE /episode/{uuid}} per match. <b>Best-effort only:</b> episode nodes
     * are removed but LLM-extracted entity/relationship facts may persist pending
     * getzep/graphiti#1083. Bounded to {@link #MAX_EPISODES_FOR_COUNT} — domain groups
     * exceeding this cap may have unmatched case episodes. For strict GDPR Art.17
     * compliance at case granularity, prefer Branch 1 ({@code caseId=null}).
     *
     * @return count of episodes erased (for GDPR Art.5(2) audit logging)
     */
    @Timed(value = "casehub.memory.graphiti", histogram = true, extraTags = {"operation", "erase"})
    @Override
    public int erase(final EraseRequest request) {
        MemoryPermissions.assertTenant(request.tenantId(), principal, requestContextActive());
        final String groupId = compoundGroupId(
            request.tenantId(), request.entityId(), request.domain().name());

        if (request.caseId() == null) {
            // Branch 1: domain-level — cascading complete deletion
            return eraseGroup(groupId);
        }

        // Branch 2: case-level — episode-by-episode (best-effort; see Javadoc)
        final List<GraphitiEpisodicNode> episodes = getEpisodesOrEmpty(groupId);
        int count = 0;
        for (final GraphitiEpisodicNode ep : episodes) {
            if (!matchesCaseId(ep.sourceDescription(), request.caseId())) continue;
            try {
                client.deleteEpisode(ep.uuid());
                count++;
            } catch (final WebApplicationException e) {
                if (e.getResponse() != null && e.getResponse().getStatus() == 404) {
                    count++; // concurrently deleted between GET and DELETE — erasure satisfied
                } else {
                    throw GraphitiStoreException.from(e);
                }
            }
        }
        return count;
    }
```

- [ ] **Step 2: Run graphiti tests**

```bash
mvn --batch-mode -pl memory-graphiti test
```

Expected: all four new `erase_*` tests pass. `eraseEntity` and capabilities tests still fail.

---

## Task 5: Implement `eraseEntity()` replacement and `capabilities()`

**Files:**
- Modify: `memory-graphiti/src/main/java/io/casehub/platform/memory/graphiti/GraphitiCaseMemoryStore.java`

- [ ] **Step 1: Replace `eraseEntity()` override**

Find the current `eraseEntity()` method (the one that does `client.getEpisodes` + `client.deleteGroup`) and replace it entirely:

```java
    /**
     * Erases all data for an entity across all configured domains.
     *
     * <p>Requires {@code casehub.memory.graphiti.known-domains} to be set. Without it,
     * throws {@link MemoryCapabilityException} — Graphiti REST has no group enumeration
     * endpoint, making cross-domain entity wipes impossible without a declared domain list.
     *
     * <p>Each domain group is deleted via cascading {@code DELETE /group/{groupId}}.
     * 404 responses (entity never had data in that domain) are treated as no-ops returning 0.
     *
     * <p><b>Mid-loop failure:</b> if {@code eraseGroup()} throws a {@link GraphitiStoreException}
     * for domain N, the loop aborts — domains 0..N-1 are erased, N..end are not.
     * Retry is safe: already-erased groups return 0 via 404 handling and the loop
     * continues from where it failed.
     *
     * @return sum of episode counts across all deleted domain groups
     */
    @Timed(value = "casehub.memory.graphiti", histogram = true, extraTags = {"operation", "eraseEntity"})
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

- [ ] **Step 2: Replace `capabilities()` with instance-level implementation**

Find the current `capabilities()` method and replace it:

```java
    @Override
    public Set<MemoryCapability> capabilities() {
        // TEMPORAL_GRAPH: client-side filtering on validAt/invalidAt returned per fact.
        // ERASE_BY_ID absent: DELETE /episode/{uuid} only removes EpisodicNode;
        //   derived EntityNode/EntityEdge persist (getzep/graphiti#1083, platform#74).
        // ERASE_ENTITY: declared only when casehub.memory.graphiti.known-domains is configured.
        //   Without it, cross-domain entity wipes are unsupported (no Graphiti group listing endpoint).
        // ERASE_DOMAIN_CASE: domain-level (caseId=null) is cascading and complete;
        //   case-level (caseId!=null) bounded to MAX_EPISODES_FOR_COUNT (see erase() Javadoc).
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

Note: `HashSet` is covered by the existing `import java.util.*;`.

- [ ] **Step 3: Run graphiti tests — expect full GREEN**

```bash
mvn --batch-mode -pl memory-graphiti test
```

Expected: all tests in `GraphitiCaseMemoryStoreTest` pass.

- [ ] **Step 4: Full build to confirm no regressions**

```bash
mvn --batch-mode install
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/platform add \
  memory-graphiti/src/main/java/io/casehub/platform/memory/graphiti/GraphitiCaseMemoryStore.java \
  memory-graphiti/src/test/java/io/casehub/platform/memory/graphiti/GraphitiCaseMemoryStoreTest.java
git -C /Users/mdproctor/claude/casehub/platform commit -m "fix(platform#75): implement GraphitiCaseMemoryStore domain-scoped erase + ERASE_DOMAIN_CASE"
```

---

## Task 6: Add `KnownDomainsTestProfile` and `GraphitiCaseMemoryStoreKnownDomainsTest`

These test the `eraseEntity()` happy path and the 404-handling paths in `eraseGroup()`.

**Files:**
- Create: `memory-graphiti/src/test/java/io/casehub/platform/memory/graphiti/KnownDomainsTestProfile.java`
- Create: `memory-graphiti/src/test/java/io/casehub/platform/memory/graphiti/GraphitiCaseMemoryStoreKnownDomainsTest.java`

- [ ] **Step 1: Create `KnownDomainsTestProfile`**

Create `memory-graphiti/src/test/java/io/casehub/platform/memory/graphiti/KnownDomainsTestProfile.java`:

```java
package io.casehub.platform.memory.graphiti;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

public class KnownDomainsTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "casehub.memory.graphiti.known-domains", "investigation",
            "quarkus.rest-client.graphiti.url", "http://localhost:39201"
        );
    }
}
```

- [ ] **Step 2: Create `GraphitiCaseMemoryStoreKnownDomainsTest`**

Create `memory-graphiti/src/test/java/io/casehub/platform/memory/graphiti/GraphitiCaseMemoryStoreKnownDomainsTest.java`:

```java
package io.casehub.platform.memory.graphiti;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.casehub.platform.api.memory.*;
import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@ActivateRequestContext
@TestProfile(KnownDomainsTestProfile.class)
class GraphitiCaseMemoryStoreKnownDomainsTest {

    static WireMockServer wireMock;

    static final String TENANT   = "tenant-1";
    static final String ENTITY   = "actor-1";
    static final MemoryDomain DOMAIN = new MemoryDomain("investigation");
    static final String GROUP_ID = TENANT + "::" + ENTITY + "::" + DOMAIN.name();

    @Inject GraphCaseMemoryStore store;
    @Inject FixedCurrentPrincipal principal;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(wireMockConfig().port(39201));
        wireMock.start();
        WireMock.configureFor("localhost", 39201);
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        principal.setTenancyId(TENANT);
    }

    @Test
    void eraseEntity_with_known_domains_deletes_each_domain_group() {
        wireMock.stubFor(get(urlPathEqualTo("/episodes/" + GROUP_ID))
            .willReturn(okJson("""
                [
                  {"uuid":"ep-1","content":"a","created_at":"2026-01-01T00:00:00Z","group_id":"%s"},
                  {"uuid":"ep-2","content":"b","created_at":"2026-01-02T00:00:00Z","group_id":"%s"},
                  {"uuid":"ep-3","content":"c","created_at":"2026-01-03T00:00:00Z","group_id":"%s"}
                ]
                """.formatted(GROUP_ID, GROUP_ID, GROUP_ID))));
        wireMock.stubFor(delete(urlEqualTo("/group/" + GROUP_ID))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"success\":true,\"message\":\"deleted\"}")));

        final int count = store.eraseEntity(ENTITY, TENANT);

        assertEquals(3, count);
        wireMock.verify(getRequestedFor(urlPathEqualTo("/episodes/" + GROUP_ID)));
        wireMock.verify(deleteRequestedFor(urlEqualTo("/group/" + GROUP_ID)));
    }

    @Test
    void eraseEntity_with_known_domains_getEpisodes_404_returns_zero() {
        // No stubs — WireMock returns 404 by default for all requests.
        // eraseGroup() catches GET 404 → returns 0; deleteGroup() is never called.
        final int count = store.eraseEntity(ENTITY, TENANT);
        assertEquals(0, count);
        wireMock.verify(0, deleteRequestedFor(anyUrl()));
    }

    @Test
    void eraseEntity_with_known_domains_deleteGroup_404_returns_zero() {
        // Group exists (GET returns 200 with empty list) but DELETE /group returns 404.
        // eraseGroup() catches DELETE 404 → returns 0.
        wireMock.stubFor(get(urlPathEqualTo("/episodes/" + GROUP_ID))
            .willReturn(okJson("[]")));
        wireMock.stubFor(delete(urlEqualTo("/group/" + GROUP_ID))
            .willReturn(aResponse().withStatus(404)));

        final int count = store.eraseEntity(ENTITY, TENANT);

        assertEquals(0, count);
        wireMock.verify(deleteRequestedFor(urlEqualTo("/group/" + GROUP_ID)));
    }

    @Test
    void capabilities_includes_ERASE_ENTITY_when_known_domains_configured() {
        assertTrue(store.capabilities().contains(MemoryCapability.ERASE_ENTITY));
    }
}
```

- [ ] **Step 3: Run the new test class**

```bash
mvn --batch-mode -pl memory-graphiti test
```

Expected: all tests in both `GraphitiCaseMemoryStoreTest` and `GraphitiCaseMemoryStoreKnownDomainsTest` pass.

- [ ] **Step 4: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/platform add \
  memory-graphiti/src/test/java/io/casehub/platform/memory/graphiti/KnownDomainsTestProfile.java \
  memory-graphiti/src/test/java/io/casehub/platform/memory/graphiti/GraphitiCaseMemoryStoreKnownDomainsTest.java
git -C /Users/mdproctor/claude/casehub/platform commit -m "test(platform#75): add KnownDomains test profile and eraseEntity happy-path tests"
```

---

## Task 7: Add contract test for `erase()` return value

**Files:**
- Modify: `testing/src/main/java/io/casehub/platform/testing/memory/CaseMemoryStoreContractTest.java`

- [ ] **Step 1: Add `erase_returns_non_negative_count` to the contract test**

In `CaseMemoryStoreContractTest`, find the `// --- erase ---` section and add one test after the existing `erase_removes_matching_memories` test:

```java
    @Test
    void erase_returns_non_negative_count() {
        store().store(input("to erase"));
        final int result = store().erase(eraseRequest()); // null caseId — domain-level
        assertTrue(result >= 0, "erase must return non-negative count");
    }
```

- [ ] **Step 2: Full build**

```bash
mvn --batch-mode install
```

Expected: `BUILD SUCCESS`. The contract test is abstract — it runs only when a concrete test class extends it. No new test execution happens here unless adapters already extend it.

- [ ] **Step 3: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/platform add \
  testing/src/main/java/io/casehub/platform/testing/memory/CaseMemoryStoreContractTest.java
git -C /Users/mdproctor/claude/casehub/platform commit -m "test(platform#75): add erase() return value assertion to contract test"
```

---

## Self-Review

### Spec coverage check

| Spec requirement | Task |
|-----------------|------|
| `erase()` → `int` SPI break | Task 1 |
| `ReactiveCaseMemoryStore.erase()` → `Uni<Integer>` | Task 1, Step 3 |
| `BlockingToReactiveBridge.erase()` wraps int | Task 1, Step 4 |
| `NoOpCaseMemoryStore.erase()` → `return 0` | Task 1, Step 5 |
| `InMemoryMemoryStore.erase()` returns count | Task 1, Step 6 |
| `JpaMemoryStore.erase()` returns count | Task 1, Step 7 |
| `SqliteMemoryStore.erase()` returns count | Task 1, Step 8 |
| `Mem0CaseMemoryStore.erase()` pre-list + count | Task 1, Step 9 |
| `CaseMemoryStoreSpiTest` anonymous impl fix | Task 1, Step 2 |
| 3-arg `compoundGroupId` | Task 3, Step 2 |
| `extractEntityId` updated for 3-segment | Task 3, Step 3 |
| `sendAdd()` passes domain to compoundGroupId | Task 3, Step 4 |
| `searchForEntity()` passes domain | Task 3, Step 5 |
| `episodesForEntity()` passes domain | Task 3, Step 6 |
| `graphQuery()` passes domain | Task 3, Step 7 |
| `eraseGroup()` helper with 404 handling | Task 3, Step 8 |
| `getEpisodesOrEmpty()` helper with 404 → empty list | Task 3, Step 9 |
| `matchesCaseId()` helper | Task 3, Step 10 |
| `erase()` Branch 1 (domain-level) → `eraseGroup()` | Task 4, Step 1 |
| `erase()` Branch 2 (case-level) with per-episode 404 handling | Task 4, Step 1 |
| `eraseEntity()` replacement with assertTenant + known-domains loop | Task 5, Step 1 |
| `@ConfigProperty knownDomains` | Task 5, Step 1 / Task 3, Step 1 |
| `capabilities()` instance-level with conditional ERASE_ENTITY | Task 5, Step 2 |
| `GROUP_ID` constant updated | Task 2, Step 1 |
| 2 inline literals updated | Task 2, Step 2 |
| 3 stale tests removed | Task 2, Step 3 |
| `eraseEntity_tenant_mismatch_throws_SecurityException` | Task 2, Step 4 |
| `eraseEntity_without_known_domains_throws_MemoryCapabilityException` | Task 2, Step 5 |
| Capabilities test updated (DOMAIN_SCOPED, ERASE_DOMAIN_CASE, ERASE_ENTITY=false) | Task 2, Step 6 |
| 4 new `erase_*` tests | Task 2, Step 7 |
| `KnownDomainsTestProfile` (port 39201) | Task 6, Step 1 |
| `GraphitiCaseMemoryStoreKnownDomainsTest` (4 tests) | Task 6, Step 2 |
| `CaseMemoryStoreContractTest` `erase_returns_non_negative_count` | Task 7, Step 1 |

All spec requirements covered.
