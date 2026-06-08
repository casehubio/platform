# Graphiti Memory Adapter + Capability Model

**Date:** 2026-06-07 (revised 2026-06-08)
**Issue:** casehubio/platform#34
**Branch:** issue-34-graphiti-memory-adapter

---

## Overview

Three coupled changes:

1. **Capability model** (`platform-api`) — `MemoryCapability` enum, `MemoryCapabilityException`, `CaseMemoryStore.capabilities()` + `requireCapability()`. Applies to all adapters. `MemoryQuery` stays at its current shape.

2. **`GraphCaseMemoryStore` SPI** (`platform-api`) — extends `CaseMemoryStore` with `graphQuery(GraphMemoryQuery)`. Designed for all graph adapters (Graphiti now; Neo4j-direct, FalkorDB-direct, Kuzu-direct later). `NoOpCaseMemoryStore` in `platform/` is updated to implement `GraphCaseMemoryStore`, satisfying both injection types when no real adapter is deployed.

3. **`memory-graphiti/` module** — `@Alternative @Priority(2)` `GraphCaseMemoryStore` (and therefore `CaseMemoryStore`) backed by the [Graphiti](https://github.com/getzep/graphiti) temporal knowledge graph service (Zep OSS). Supports Neo4j, FalkorDB, Kuzu as graph backends.

### Why Graphiti

Each memory backend serves a distinct capability tier:

| Tier | Backend | Search | What it answers |
|------|---------|--------|-----------------|
| 0 | inmem | none | test isolation |
| 1 | jpa / sqlite | FTS keyword | "find text containing these words" |
| 2 | mem0 | vector similarity | "find text meaning this" |
| 3 | graphiti | hybrid + temporal graph | "what entities/facts were true, and when?" |

Graphiti's bi-temporal model tracks `valid_at`/`invalid_at` on every LLM-extracted relationship edge. For compliance reconstruction (EU AI Act Art.12, GDPR Art.22), this surfaces what was factually valid at a specific instant — **when `limit` is set large enough to capture all relevant facts** (see §5 Known Limitations).

### Graphiti text contract — mandatory reading before use

**Graphiti does not store text verbatim.** Unlike every other adapter, `Memory.text()` from Graphiti is never the original `MemoryInput.text()`:

- **RELEVANCE path** (`graphQuery()` → `POST /search`): `Memory.text` = LLM-generated natural language description of a relationship edge (e.g. "Alice approved the clinical trial protocol"). This is the extracted fact, not the source sentence.
- **CHRONOLOGICAL path** (`query()` → `GET /episodes/{group_id}`): `Memory.text` = `"(user): {original text}"` — Graphiti's ingest layer formats the episode body as `f'{m.role or ""}({m.role_type}): {m.content}'`. Since this adapter does not set `role` in `AddMessage`, the role slot is empty: `"" + "(user)" + ": " + content`.

**Bi-temporal value lives in facts, not episodes.** `FactResult.valid_at`/`invalid_at` carries LLM-extracted temporal validity (when a fact became true in the world). `EpisodicNode.valid_at` is the store timestamp (≈ `created_at`). Only the RELEVANCE/`graphQuery()` path (facts) provides meaningful bi-temporal data.

Callers that require verbatim `Memory.text()` round-trip MUST NOT use Graphiti. Use mem0 (infer:false) for verbatim storage.

### `query(MemoryQuery)` vs `graphQuery(GraphMemoryQuery)`

| | `query()` | `graphQuery()` |
|-|-----------|----------------|
| Path | CHRONOLOGICAL → episodes; RELEVANCE → facts | Always facts (POST /search) |
| Question | Optional | Required |
| Domain | Required (from `MemoryQuery`) | Required (from `GraphMemoryQuery`) |
| Temporal filter | None | `validAt` client-side filter on `valid_at`/`invalid_at` |
| Use case | Simple retrieval, chronological audit trail | Semantic search with temporal graph awareness |

---

## 1 — Capability Model (`platform-api`)

### 1.1 `MemoryCapability` enum

```java
package io.casehub.platform.api.memory;

public enum MemoryCapability {
    // Universal
    CHRONOLOGICAL_ORDER,
    DOMAIN_SCOPED,
    CASE_SCOPED,
    SINCE_FILTER,
    BATCH_STORE,

    // Semantic search tier
    SEMANTIC_SEARCH,     // vector similarity — mem0, graphiti
    FULL_TEXT_SEARCH,    // BM25/FTS keyword — jpa, sqlite

    // Graph tier (graphiti + future direct-Bolt adapters)
    TEMPORAL_GRAPH,      // valid_at/invalid_at available on results; client-side temporal filtering
    ENTITY_TYPE_FILTER,  // filter results by graph entity type (future — no REST support yet)
    ENTITY_TRAVERSAL,    // graph traversal with configurable depth (future — no REST support yet)
    FACT_SEARCH,         // edge / relationship search via POST /search
    NODE_SEARCH,         // entity node summary search (future — no REST endpoint yet)

    // Erasure granularity
    ERASE_BY_ID,         // eraseById() — per-episode deletion (note: derived facts persist)
    ERASE_ENTITY,        // eraseEntity() — GDPR full-entity wipe
    ERASE_DOMAIN_CASE,   // erase(EraseRequest) — domain+caseId scoped
}
```

### 1.2 `MemoryCapabilityException`

```java
package io.casehub.platform.api.memory;

public class MemoryCapabilityException extends RuntimeException {
    private final MemoryCapability required;

    public MemoryCapabilityException(MemoryCapability required, Class<?> adapter) {
        super(adapter.getSimpleName() + " does not support " + required.name()
              + " — check CaseMemoryStore.capabilities() before calling");
        this.required = required;
    }

    public MemoryCapability required() { return required; }
}
```

### 1.3 `CaseMemoryStore` changes

New default methods; existing `eraseById()` and `eraseEntity()` defaults change from
`UnsupportedOperationException` to `MemoryCapabilityException` — uniform capability
signalling replaces the parallel exception mechanism. Javadoc on `erase()` updated to note
that adapters not declaring `ERASE_DOMAIN_CASE` will throw `MemoryCapabilityException`.

```java
default Set<MemoryCapability> capabilities() {
    return Set.of();
}

default void requireCapability(MemoryCapability c) {
    if (!capabilities().contains(c))
        throw new MemoryCapabilityException(c, getClass());
}

// Changed: was UnsupportedOperationException
default void eraseEntity(String entityId, String tenantId) {
    throw new MemoryCapabilityException(ERASE_ENTITY, getClass());
}

// Changed: was UnsupportedOperationException
default void eraseById(String memoryId, String tenantId) {
    throw new MemoryCapabilityException(ERASE_BY_ID, getClass());
}
```

**Breaking change:** callers catching `UnsupportedOperationException` for these methods must update to catch `MemoryCapabilityException`. Migration is mechanical.

**Test update required:** `CaseMemoryStoreSpiTest` in `platform-api/src/test/` has `eraseById_default_throws()` and `eraseEntity_default_throws()` asserting `UnsupportedOperationException`. Both must be updated to assert `MemoryCapabilityException` after this change.

### 1.4 `MemoryQuery` — NO CHANGES

`MemoryQuery` stays at its current 8-field record shape. Graph-native parameters belong in `GraphMemoryQuery` (§2), not here.

### 1.5 `NoOpCaseMemoryStore` updated to implement `GraphCaseMemoryStore`

`NoOpCaseMemoryStore` in `platform/` is updated to implement `GraphCaseMemoryStore` (which extends `CaseMemoryStore`). This satisfies both `CaseMemoryStore` and `GraphCaseMemoryStore` injection points when no real adapter is deployed — one bean, one class, no ambiguity.

```java
@DefaultBean @ApplicationScoped
public class NoOpCaseMemoryStore implements GraphCaseMemoryStore {
    // ... existing no-op overrides unchanged ...
    @Override public List<Memory> graphQuery(GraphMemoryQuery query) { return List.of(); }
    @Override public Set<MemoryCapability> capabilities() { return Set.of(); }
    // eraseById() and eraseEntity() remain explicit no-ops (not throws)
    // NoOp is special: "nothing stored → erasure trivially satisfied"
}
```

`NoOpCaseMemoryStore` is the only adapter where `capabilities()` does NOT declare `ERASE_BY_ID`/`ERASE_ENTITY` yet `eraseById()`/`eraseEntity()` do not throw — it is exempt from the "not declared → must throw" contract test rule (see §4).

**Implementation note for NoOp:** The current `NoOpCaseMemoryStore` in `platform/` implements `CaseMemoryStore` and has no `capabilities()` override. This spec requires three changes: (1) change `implements CaseMemoryStore` → `implements GraphCaseMemoryStore`, (2) add `graphQuery()` returning `List.of()`, (3) add `capabilities()` returning `Set.of()`. All three must be applied together.

### 1.6 Existing adapter capability declarations

| Adapter | Class (verified) | Capabilities |
|---------|-----------------|-------------|
| `NoOpCaseMemoryStore` | `platform/…/memory/NoOpCaseMemoryStore` | `Set.of()` — special: eraseById/eraseEntity are no-ops despite not declared |
| `InMemoryMemoryStore` | `memory-inmem/…/inmem/InMemoryMemoryStore` | `CHRONOLOGICAL_ORDER, DOMAIN_SCOPED, CASE_SCOPED, SINCE_FILTER, BATCH_STORE, ERASE_BY_ID, ERASE_ENTITY, ERASE_DOMAIN_CASE` |
| `JpaMemoryStore` | `memory-jpa/…/jpa/JpaMemoryStore` | same as inmem + `FULL_TEXT_SEARCH` |
| `SqliteMemoryStore` | `memory-sqlite/…/sqlite/SqliteMemoryStore` | same as inmem + `FULL_TEXT_SEARCH` |
| `Mem0CaseMemoryStore` | `memory-mem0/…/mem0/Mem0CaseMemoryStore` | `CHRONOLOGICAL_ORDER, DOMAIN_SCOPED, CASE_SCOPED, SINCE_FILTER, BATCH_STORE, SEMANTIC_SEARCH, ERASE_BY_ID, ERASE_ENTITY, ERASE_DOMAIN_CASE` |
| `GraphitiCaseMemoryStore` | `memory-graphiti/…/graphiti/GraphitiCaseMemoryStore` | see §3.4 |

### 1.7 `MemoryAttributeKeys` additions

```java
/** ISO-8601 Instant string — when this fact became valid (LLM-extracted). */
public static final String VALID_FROM = "valid-from";

/** ISO-8601 Instant string — when this fact was invalidated; absent if still valid. */
public static final String VALID_UNTIL = "valid-until";
```

---

## 2 — `GraphCaseMemoryStore` SPI (`platform-api`)

### 2.1 Interface

```java
package io.casehub.platform.api.memory;

import java.util.List;

/**
 * Graph-native extension of CaseMemoryStore. Implemented by adapters backed by
 * temporal knowledge graph engines (Graphiti, Neo4j-direct, FalkorDB-direct, etc.).
 *
 * <p>Callers needing temporal graph queries inject GraphCaseMemoryStore directly.
 * Callers needing only basic storage inject CaseMemoryStore — unaffected by graph params.
 *
 * <p>NoOpCaseMemoryStore implements this interface; UnsatisfiedResolutionException
 * will not occur when no graph adapter is deployed.
 */
public interface GraphCaseMemoryStore extends CaseMemoryStore {

    /**
     * Semantic graph query. Uses the adapter's native search endpoint.
     * Memory.text() carries LLM-extracted fact descriptions — not original stored text.
     *
     * <p>For chronological (non-semantic) retrieval, use the base
     * {@link CaseMemoryStore#query(MemoryQuery)} with {@link MemoryOrder#CHRONOLOGICAL}.
     */
    List<Memory> graphQuery(GraphMemoryQuery query);
}
```

### 2.2 `GraphMemoryQuery`

`question` is **required** — `graphQuery()` is purely semantic; `POST /search` requires a query string. For non-semantic chronological retrieval, use the base `query(MemoryQuery)` method.

`domain` is **required** — mirrors `MemoryQuery` and ensures `Memory.domain` is always constructible (non-blank `MemoryDomain`).

```java
package io.casehub.platform.api.memory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record GraphMemoryQuery(
    String tenantId,           // required
    List<String> entityIds,    // required, 1–MAX_ENTITY_IDS
    MemoryDomain domain,       // required — used to populate Memory.domain in results
    String question,           // required — search query for POST /search
    int limit,                 // default 10
    Instant since,             // null → no time lower bound (client-side filter on createdAt)
    Instant validAt,           // null → no temporal filter; requires TEMPORAL_GRAPH if non-null
    Set<String> entityTypes,   // null → all types; requires ENTITY_TYPE_FILTER if non-null
    MemoryResultType resultType // default DEFAULT; FACTS also valid; NODES removed
) {
    public static final int MAX_ENTITY_IDS = 25;

    public GraphMemoryQuery {
        Objects.requireNonNull(tenantId,  "tenantId required");
        Objects.requireNonNull(entityIds, "entityIds required");
        Objects.requireNonNull(domain,    "domain required");
        Objects.requireNonNull(question,  "question required — for chronological retrieval use query(MemoryQuery)");
        if (question.isBlank()) throw new IllegalArgumentException("question must not be blank");
        if (entityIds.isEmpty()) throw new IllegalArgumentException("entityIds must not be empty");
        if (entityIds.size() > MAX_ENTITY_IDS)
            throw new IllegalArgumentException("entityIds must not exceed " + MAX_ENTITY_IDS);
        if (limit < 1) throw new IllegalArgumentException("limit must be >= 1");
        entityIds = List.copyOf(entityIds);
        entityTypes = entityTypes == null ? null : Set.copyOf(entityTypes);
        resultType = resultType == null ? MemoryResultType.DEFAULT : resultType; // normalize, not requireNonNull
    }

    public static GraphMemoryQuery forEntity(String entityId, MemoryDomain domain, String tenantId, String question) {
        return new GraphMemoryQuery(tenantId, List.of(entityId), domain, question, 10, null, null, null, MemoryResultType.DEFAULT);
    }

    public GraphMemoryQuery withLimit(int limit) { ... }
    public GraphMemoryQuery withSince(Instant since) { ... }
    public GraphMemoryQuery withValidAt(Instant validAt) { ... }
    public GraphMemoryQuery withEntityTypes(Set<String> entityTypes) { ... }
    public GraphMemoryQuery withResultType(MemoryResultType resultType) { ... }
}
```

### 2.3 `MemoryResultType`

```java
package io.casehub.platform.api.memory;

public enum MemoryResultType {
    DEFAULT,  // adapter-defined (FACTS for graphiti; episodes for graphiti CHRONOLOGICAL via query())
    FACTS     // edge / relationship results — requires FACT_SEARCH
    // NODES removed: requires NODE_SEARCH which has no current REST endpoint — add when implemented
    // ALL removed: requires both FACT_SEARCH + NODE_SEARCH
}
```

---

## 3 — `memory-graphiti/` Module

### 3.1 Module structure

```
memory-graphiti/
  pom.xml
  src/main/java/io/casehub/platform/memory/graphiti/
    GraphitiCaseMemoryStore.java   @Alternative @Priority(2) @ApplicationScoped
    GraphitiClient.java            @RegisterRestClient(configKey = "graphiti")
    GraphitiConfig.java            @ConfigMapping(prefix = "casehub.memory.graphiti")
    GraphitiAuthFilter.java        optional Bearer token (see §3.3)
    GraphitiStoreException.java    wraps WebApplicationException
    dto/
      AddMessagesRequest.java      {group_id, messages: list<AddMessage>}
      AddMessage.java              {content, uuid, name, role_type, role, timestamp, source_description}
      GraphitiSearchRequest.java   {group_ids, query, max_facts}
      GraphitiSearchResponse.java  {facts: list<FactResult>}  ← SearchResults wrapper shape
      FactResult.java              {uuid, name, fact, valid_at, invalid_at, expired_at, created_at}
      GraphitiEpisodicNode.java    {uuid, name, group_id, source, source_description, content,
                                    valid_at, entity_edges, episode_metadata, created_at}
  src/test/java/io/casehub/platform/memory/graphiti/
    GraphitiCaseMemoryStoreTest.java
```

**pom.xml:** no `quarkus:build` goal; `generate-code` + `generate-code-tests` only; Jandex plugin. Dependencies: `casehub-platform-api` (compile), `quarkus-rest-client-jackson` (compile), `micrometer-core` (compile), `casehub-platform` + `casehub-platform-testing` + `quarkus-junit5` + `wiremock` (test).

**DTO serialization contract:** All Graphiti REST endpoints use `snake_case` JSON keys. This is the Mem0 precedent (see `Mem0Memory.java` and `Mem0AddRequest.java`). Two distinct rules apply:

1. **All DTOs (request and response):** every field whose JSON name differs from the Java camelCase field name requires `@JsonProperty("snake_case")`. Without it Jackson silently maps to `null` on deserialization (no error). Affected fields: `valid_at`, `invalid_at`, `expired_at`, `created_at`, `group_id`, `source_description`, `entity_edges`, `episode_metadata`, `role_type`, `group_ids`, `max_facts`.

2. **Request DTOs only** (`AddMessagesRequest`, `AddMessage`, `GraphitiSearchRequest`): additionally annotate with `@JsonInclude(JsonInclude.Include.NON_NULL)` so optional null fields are omitted from the request body. This annotation is meaningless on response DTOs (deserialization, not serialization) — do **not** add it to `FactResult`, `GraphitiEpisodicNode`, or `GraphitiSearchResponse`.

3. **`AddMessage.timestamp` must serialize as ISO-8601 string.** Jackson's default for `Instant` is epoch-milliseconds (`1749390600000`), but Graphiti's Pydantic `datetime` parser expects an ISO-8601 string (`"2026-06-08T14:30:00Z"`). This is a library module — relying on the consuming application's Jackson global config is not safe. Use `@JsonFormat(shape = JsonFormat.Shape.STRING)` on the `timestamp` field of `AddMessage` to force ISO-8601 output regardless of global config.

**`CLAUDE.md` Modules table** — new row:
> `memory-graphiti/` | `casehub-platform-memory-graphiti` | `@Alternative @Priority(2)` Graphiti REST `GraphCaseMemoryStore` — temporal knowledge graph (Neo4j/FalkorDB/Kuzu via Graphiti OSS). LLM entity extraction (async). `group_id = {tenantId}::{entityId}`. `query()` RELEVANCE → `POST /search`; CHRONOLOGICAL → `GET /episodes/{group_id}`. `graphQuery()` → per-entity `POST /search` with optional temporal filtering. No Flyway. Configure: `quarkus.rest-client.graphiti.url`, `casehub.memory.graphiti.api-key`. Do NOT combine with other `@Priority(2)` adapters.

### 3.2 Verified Graphiti REST API routes

Source-verified against `getzep/graphiti` `server/graph_service/routers/`:

| Method | Path | Used for |
|--------|------|---------|
| `POST` | `/messages` | `store()` — episode ingest; returns 202 (async LLM extraction) |
| `POST` | `/search` | `query(RELEVANCE)`, `graphQuery()` |
| `GET` | `/episodes/{group_id}` | `query(CHRONOLOGICAL)` — returns `List<EpisodicNode>` directly |
| `DELETE` | `/group/{group_id}` | `eraseEntity()` |
| `DELETE` | `/episode/{uuid}` | `eraseById()` |

**`SearchQuery`** fields (source-verified): `group_ids: list[str] | None`, `query: str` (required), `max_facts: int` (default 10). No `valid_at`, no `entity_types` — temporal and type filtering is applied client-side on the `FactResult` response.

**`FactResult`** fields: `uuid`, `name`, `fact`, `valid_at`, `invalid_at`, `expired_at`, `created_at`. No `group_id` — entity attribution requires the calling context.

**`EpisodicNode`** fields (from `graphiti_core/nodes.py`): `uuid`, `name`, `group_id`, `source`, `source_description`, `content`, `valid_at`, `entity_edges`, `episode_metadata`, `created_at`. `GET /episodes/{group_id}` returns a direct `List<EpisodicNode>` (no wrapper DTO).

### 3.3 Configuration + auth filter

```java
@ConfigMapping(prefix = "casehub.memory.graphiti")
public interface GraphitiConfig {
    Optional<String> apiKey();  // bearer token if Graphiti auth is enabled
}
// REST URL: quarkus.rest-client.graphiti.url
```

`GraphitiAuthFilter` is `@ApplicationScoped` (one instance per deployment, `apiKey` injected via `@Inject GraphitiConfig`). It adds `Authorization: Bearer {apiKey}` when present. **Must NOT be annotated `@Provider`** — causes Quarkus to treat it as a global JAX-RS provider, bypassing CDI injection (see garden GE-20260530-385dbb). Register via `@RegisterProvider(GraphitiAuthFilter.class)` on `GraphitiClient` interface instead. Follows `Mem0AuthFilter` pattern exactly.

### 3.4 `GraphitiCaseMemoryStore`

#### CDI + capabilities

```java
@Alternative @Priority(2) @ApplicationScoped
public class GraphitiCaseMemoryStore implements GraphCaseMemoryStore {

    @Override
    public Set<MemoryCapability> capabilities() {
        return Set.of(
            CHRONOLOGICAL_ORDER, SINCE_FILTER, BATCH_STORE,
            SEMANTIC_SEARCH,
            TEMPORAL_GRAPH,  // client-side filter on FactResult.valid_at / invalid_at (see §5)
            FACT_SEARCH,
            ERASE_BY_ID,     // DELETE /episode/{uuid} — episode only; derived facts persist (#74)
            ERASE_ENTITY     // DELETE /group/{group_id}
            // NOT declared: DOMAIN_SCOPED, CASE_SCOPED (see §3.4 rationale),
            //               ENTITY_TYPE_FILTER, ENTITY_TRAVERSAL (no REST support — #76),
            //               NODE_SEARCH (no REST endpoint),
            //               ERASE_DOMAIN_CASE (no sub-group deletion)
        );
    }
}
```

#### Why DOMAIN_SCOPED and CASE_SCOPED are not declared

`SearchQuery` has no domain/caseId parameter; `FactResult` has no metadata. Domain filtering is impossible for RELEVANCE results. For CHRONOLOGICAL results, `source_description` carries `{"domain":"x","caseId":"y"}` JSON (stored at ingest), but parsing it back for filtering would produce inconsistent behaviour across the two paths.

The consistent and honest design: domain and caseId are not filterable in Graphiti. `Memory.domain` is populated from the caller's query context (not from the Graphiti response). For CHRONOLOGICAL results, `source_description` carries `domain=<domain>` or `domain=<domain>;caseId=<caseId>` in key=value format (stored at ingest) — zero cost, enables debugging, positions for `episode_metadata` REST support if Graphiti exposes it in the future.

#### `group_id` strategy

`group_id = {tenantId}::{entityId}` — identical separator `::` to Mem0. Chosen because:
- `eraseEntity()` is a single `DELETE /group/...` call
- `query(CHRONOLOGICAL)` is O(entityIds) GET calls with direct entityId attribution
- Alternative (`{tenantId}::{entityId}::{domain}::{caseId}`) makes `eraseEntity()` impossible (no group enumeration endpoint)

#### `store(MemoryInput input)`

1. `MemoryPermissions.assertTenant(input.tenantId(), principal)` — before any I/O
2. `UUID episodeUuid = UUID.randomUUID()`
3. Build `AddMessage`: `content = input.text()`, `uuid = episodeUuid.toString()`, `role_type = "user"`, `timestamp = Instant.now()`, `source_description = "domain=" + input.domain().name() + (input.caseId() != null ? ";caseId=" + input.caseId() : "")` (stored for debugging/future extensibility; never parsed back), `name = input.entityId()`
4. Build `AddMessagesRequest`: `group_id = compoundGroupId(tenantId, entityId)`, `messages = [message]`
5. `client.addMessages(request)` → 202 (async LLM extraction in Graphiti). On non-2xx: catch `WebApplicationException`, wrap in `GraphitiStoreException` with HTTP status and response body (same pattern as `Mem0StoreException`).
6. Return `episodeUuid.toString()`

**Eventual consistency:** the returned memoryId is valid immediately but graph entities and facts are not queryable until Graphiti's async LLM extraction completes (seconds to low minutes). Immediate `query()` after `store()` returns empty.

#### `storeAll(List<MemoryInput> inputs)`

Pre-flight: `MemoryPermissions.assertTenant` for every input before any REST call. Sequential `addMessages` calls. Returns episode UUIDs in input order. Follows `memory-storeall-transactional-contract.md`. Note: a 202 response indicates the episode is queued for async LLM extraction; extraction failures are not signalled back to the adapter. The storeAll contract covers HTTP-level failures; async extraction failures are logged internally by Graphiti and are not recoverable from this adapter.

#### `query(MemoryQuery query)` — base path, per-entity calls

**entityId attribution rule:** issue one REST call per entityId. This ensures each `Memory` record carries the correct `entityId` from the call context. Batching all entityIds into one `/search` call is NOT used — `FactResult` has no `group_id`, so entityId is irrecoverable from batched results.

| Condition | Per-entity call |
|-----------|----------------|
| `RELEVANCE` + `question != null` | `POST /search` with `group_ids=[{tenantId}::{entityId}]`, `query=question`, `max_facts=limit` per entityId |
| `CHRONOLOGICAL` (or RELEVANCE without question) | `GET /episodes/{group_id}?last_n={last_n}` per entityId |

After all per-entity calls, merge results and apply:
- `since` filter (client-side on `created_at`)
- sort: CHRONOLOGICAL → `created_at` desc across all entities; RELEVANCE → entity-order concatenation (all of entity-1's score-ranked results, then all of entity-2's, etc.). Cross-entity score interleaving is intentionally avoided — scores from separate `POST /search` calls are not comparable (different normalization denominators per call, same issue as Mem0's multi-entity merge).
- `limit` cap applied to combined result

**`last_n` for CHRONOLOGICAL:** `last_n = query.limit() * entityIds.size()`. After merge and sort, the combined result is capped at `query.limit()`. This ensures enough candidates across entities before filtering.

**`Memory.domain`:** always `query.domain()` — domain comes from the caller's query context, not the Graphiti response (which carries no domain metadata in either `FactResult` or the needed fields of `EpisodicNode` without source_description parsing).

#### `graphQuery(GraphMemoryQuery query)` — graph-native path

Per-entity calls: one `POST /search` per entityId in `query.entityIds()`.

1. `MemoryPermissions.assertTenant(query.tenantId(), principal)` — captured before any I/O
2. Capability checks (in order):
   - always → `requireCapability(FACT_SEARCH)` (graphQuery always uses fact search; guards future adapters that may not declare it)
   - `validAt != null` → `requireCapability(TEMPORAL_GRAPH)`
   - `entityTypes != null` → `requireCapability(ENTITY_TYPE_FILTER)`
3. Per-entityId: `POST /search` with `group_ids=[compoundGroupId(tenantId, entityId)]`, `query=question`, `max_facts=limit`
4. Merge all results using entity-order concatenation (entity-1 results first, then entity-2, etc. — same reasoning as `query()` RELEVANCE merge); apply client-side filters:
   - `since` on `created_at`
   - `validAt` on `valid_at ≤ validAt AND (invalid_at == null OR invalid_at > validAt)`
5. Apply `limit` cap to combined result
6. Map to `Memory` with `domain = query.domain()`

#### Erase methods

All erase methods call `MemoryPermissions.assertTenant` as the **first** statement, before any capability check or HTTP call. Tenant check before capability check prevents capability-probing by unauthorized callers.

| Method | Step 1 | Step 2 |
|--------|--------|--------|
| `eraseEntity(entityId, tenantId)` | `assertTenant(tenantId, principal)` | `DELETE /group/{tenantId}::{entityId}` — cascading delete |
| `erase(EraseRequest)` | `assertTenant(request.tenantId(), principal)` | `requireCapability(ERASE_DOMAIN_CASE)` → throws; no HTTP call |
| `eraseById(memoryId, tenantId)` | `assertTenant(tenantId, principal)` | `DELETE /episode/{uuid}` — source episode only; derived facts persist (#74) |

#### `Memory` mapping

`Memory.domain` is always sourced from the calling query's domain, not from the Graphiti response. This ensures `MemoryDomain` is never constructed with a blank string.

| `Memory` field | RELEVANCE / graphQuery (FactResult) | CHRONOLOGICAL (EpisodicNode) |
|----------------|-------------------------------------|------------------------------|
| `memoryId` | `uuid` | `uuid` |
| `entityId` | `entityId` from per-entity call context | strip `{tenantId}::` from `group_id` |
| `domain` | `query.domain()` or `graphQuery.domain()` | `query.domain()` |
| `tenantId` | from query | from query |
| `caseId` | `null` | `null` (source_description carried but not parsed) |
| `text` | `fact` (LLM-generated fact description) | `content` = `"(user): {original text}"` (empty role slot; Graphiti's ingest format) |
| `attributes` | `VALID_FROM` = `valid_at`; `VALID_UNTIL` = `invalid_at` if non-null | `VALID_FROM` = `valid_at` (≈ store timestamp) |
| `createdAt` | `created_at` | `created_at` |

#### Metrics

All public SPI methods: `@Timed(value = "casehub.memory.graphiti", histogram = true, extraTags = {"operation", "<method>"})` — observability parity with mem0 (platform#66).

### 3.5 `GraphitiClient`

```java
@RegisterRestClient(configKey = "graphiti")
@RegisterProvider(GraphitiAuthFilter.class)
@Path("/")
public interface GraphitiClient {

    @POST @Path("/messages")
    @Consumes(MediaType.APPLICATION_JSON)
    Response addMessages(AddMessagesRequest request);  // 202

    @POST @Path("/search")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    GraphitiSearchResponse search(GraphitiSearchRequest request);

    @GET @Path("/episodes/{groupId}")
    @Produces(MediaType.APPLICATION_JSON)
    List<GraphitiEpisodicNode> getEpisodes(
        @PathParam("groupId") String groupId,
        @QueryParam("last_n") int lastN);  // returns list directly, no wrapper

    @DELETE @Path("/group/{groupId}")
    void deleteGroup(@PathParam("groupId") String groupId);

    @DELETE @Path("/episode/{uuid}")
    void deleteEpisode(@PathParam("uuid") String uuid);
}
```

---

## 4 — Testing

### `GraphitiCaseMemoryStoreTest` (WireMock)

All WireMock stubs use source-verified route paths.

**`store()` tests:**
- Happy path: `POST /messages → 202`; UUID returned; group_id = `{tenantId}::{entityId}`; `AddMessage.uuid` = returned UUID; `source_description` = key=value format (`domain=<domain>` when no caseId; `domain=<domain>;caseId=<caseId>` when caseId present); `role_type` = "user"; `timestamp` serialized as ISO-8601 string (not epoch-millis)
- Tenant mismatch: `SecurityException` before any HTTP call
- HTTP error from Graphiti: `GraphitiStoreException`

**`storeAll()` tests:**
- Sequential POSTs; IDs returned in input order
- Pre-flight all-bad: all inputs have wrong tenant → `SecurityException`; zero HTTP calls
- Pre-flight [good, bad]: first input passes tenant check, second fails → `SecurityException`; pre-flight checks ALL inputs before starting any POST, so zero HTTP calls (this is the critical mixed-tenant case)

**`query(RELEVANCE + question)` tests:**
- Per-entity: one `POST /search` per entityId; `group_ids` contains single compound id per call
- `Memory.entityId` correctly attributed from per-entity call context
- `Memory.domain` = `query.domain()`; NOT parsed from Graphiti response
- `Memory.text` = `FactResult.fact` (not original text)
- `VALID_FROM`/`VALID_UNTIL` in `Memory.attributes` from `valid_at`/`invalid_at`
- Since filter: `created_at` before barrier excluded client-side

**`query(CHRONOLOGICAL)` tests:**
- Per-entity: one `GET /episodes/{groupId}` per entityId; `last_n = limit * entityIds.size()`
- `Memory.entityId` extracted from `EpisodicNode.group_id` (strip tenantId:: prefix)
- `Memory.text` = `EpisodicNode.content` = `"(user): {original text}"` (empty role slot in Graphiti's episode body format)
- Multi-entity: two GET calls; merged and sorted newest-first; combined capped at limit

**`graphQuery()` tests:**
- Per-entity POST /search calls; `Memory.entityId` from call context; `Memory.domain = graphQuery.domain()`
- `validAt` filter: stubs return mix of valid/invalid facts; only those `valid_at ≤ validAt AND (invalid_at == null OR invalid_at > validAt)` returned
- `since` filter: applied client-side on `created_at`
- Missing capability: `graphQuery` with `entityTypes` set → `MemoryCapabilityException(ENTITY_TYPE_FILTER)`; no HTTP call

**Erase tests:**
- `eraseEntity()`: stubs `DELETE /group/{groupId} → 200`; verify group_id; tenant mismatch throws first
- `eraseById()`: stubs `DELETE /episode/{uuid} → 200`; verify uuid; tenant mismatch throws first
- `erase(EraseRequest)`: `MemoryCapabilityException(ERASE_DOMAIN_CASE)`; zero HTTP calls

**Eventual consistency test (documented asymmetry):**
- `store()` returns UUID; immediate `query()` stubs empty Graphiti response → test documents that empty is the expected immediate result

### `MemoryCapabilityExceptionTest` (`platform-api`)

- Message contains capability name and adapter class name
- Default `eraseById()` on `CaseMemoryStore` throws `MemoryCapabilityException(ERASE_BY_ID)` — not `UnsupportedOperationException`
- Default `eraseEntity()` throws `MemoryCapabilityException(ERASE_ENTITY)`

### `GraphMemoryQueryTest` (`platform-api`)

- `forEntity()` requires question (null question → NPE)
- `forEntity()` requires domain (null domain → NPE)
- `withValidAt()` + compact constructor: `resultType` normalized to DEFAULT when null input
- `withEntityTypes()`: defensive copy; null input → null field (not empty set)
- `question = ""` → `IllegalArgumentException`

### `NoOpCaseMemoryStoreTest` — updated

- `graphQuery()` returns empty list ✓
- `eraseById()` and `eraseEntity()` do NOT throw (confirmed special exemption) ✓
- `capabilities()` returns `Set.of()` ✓

### Contract test extension (`CaseMemoryStoreContractTest`)

New capability-check contract:

```
If adapter.capabilities().contains(ERASE_BY_ID)  → eraseById() must not throw MemoryCapabilityException
If adapter is NOT NoOpCaseMemoryStore
   AND adapter.capabilities() does NOT contain ERASE_BY_ID
   → eraseById() must throw MemoryCapabilityException (not UnsupportedOperationException)
```

Same rule applies for `ERASE_ENTITY`/`eraseEntity()`.

`NoOpCaseMemoryStore` is explicitly exempted — its no-op behaviour is semantically correct regardless of capability declaration.

### Contract test participation — Graphiti

`GraphitiCaseMemoryStoreTest` does NOT extend `CaseMemoryStoreContractTest` (WireMock-based; no durable state). A Testcontainers integration test `GraphitiCaseMemoryStoreContractIT` (tracked as follow-up) will run against a real Graphiti service with appropriate delays between `store()` and `query()` to account for LLM extraction latency.

**Blanket rule:** Every test that calls `store()` followed by `query()` in sequence will fail due to eventual consistency — `store()` returns 202 (episode queued) but graph facts are not queryable until async LLM extraction completes.

**Tests expected to pass** (security boundaries + synchronous operations only):

| Test | Why it passes |
|------|--------------|
| `store_assigns_non_empty_memory_id` | UUID returned synchronously |
| `store_assigns_unique_ids` | two different UUIDs returned synchronously |
| `store_tenant_mismatch_throws` | `SecurityException` before any HTTP call |
| `query_empty_when_nothing_stored` | no prior state; empty result correct |
| `query_does_not_leak_across_tenants` | `SecurityException` from `assertTenant` |
| `storeAll_empty_returns_empty_list` | no-op, no HTTP calls |
| `storeAll_all_wrong_tenant_throws_security_exception` | pre-flight `SecurityException` |
| `eraseById_does_not_cross_tenant_boundary` | `SecurityException` before HTTP call |
| `eraseEntity_does_not_cross_tenant_boundary` | `SecurityException` before HTTP call |
| `erase_tenant_mismatch_throws` | `SecurityException` before `requireCapability` |

**Note on `query_does_not_leak_across_domains`:** this test will appear to pass under eventual consistency (store returns 202, query immediately returns empty → test asserts empty → green). It will fail once the Testcontainers IT adds extraction wait time, revealing the true domain isolation gap. The failure is masked, not absent.

**All other tests fail** — root causes summarised:

| Root cause | Affected tests |
|-----------|----------------|
| Eventual consistency (store() not immediately queryable) | All tests calling store() then query() or erase() in sequence |
| DOMAIN_SCOPED not supported | `query_does_not_leak_across_domains` (once extraction delay added), `query_with_caseId_filters_correctly` |
| `erase(EraseRequest)` throws `MemoryCapabilityException` | All `erase_*` tests except `erase_tenant_mismatch_throws` |
| Text verbatim round-trip broken by design | `attribute_keys_round_trip_correctly` |

---

## 5 — Known Limitations

| Limitation | Notes | Tracking |
|-----------|-------|---------|
| `erase(EraseRequest)` domain+caseId scoped deletion not supported | group_id is entity-level; no sub-group deletion | #75 |
| `eraseById()` deletes source episode only; derived entity/relationship facts persist | `DELETE /episode/{uuid}` removes EpisodicNode, not EntityEdge | #74 |
| DOMAIN_SCOPED/CASE_SCOPED not declared | FactResult carries no domain; no server-side domain filter | — |
| ENTITY_TYPE_FILTER/ENTITY_TRAVERSAL not declared | SearchQuery has no type/depth params | #76 |
| **TEMPORAL_GRAPH is client-side after server truncation** | Graphiti returns up to `max_facts` by relevance score BEFORE `validAt` filtering. For compliance reconstruction (EU AI Act Art.12), set `limit` large enough to capture all relevant facts for the queried entities. With default `limit=10`, facts ranked below position 10 are never examined and may be missed in temporal reconstruction. | — |
| `Memory.text` is NOT the original stored text | RELEVANCE/graphQuery: LLM fact description; CHRONOLOGICAL: `role(role_type): text` prefix | — |
| Eventual consistency: `store()` → `query()` immediate read returns empty | LLM extraction is async; seconds to minutes | — |
| Orphaned entity nodes on `eraseById()` | getzep/graphiti#1083 — entities without MENTIONS not cleaned | — |
| `SINCE_FILTER` with RELEVANCE may miss post-since facts | Top `max_facts` selected by relevance score before `since` filter applied; post-since facts ranked below that cutoff are missed. Prefer RELEVANCE + larger `limit` or CHRONOLOGICAL for exhaustive since-bounded retrieval. | — |
| `SINCE_FILTER` with CHRONOLOGICAL may miss older post-since episodes | `last_n = limit * entityIds.size()` fetches only the N most-recent episodes. If post-since episodes exist beyond position `last_n` in recency order, they are missed. Increase `limit` to raise `last_n` for wider coverage. | — |
| Bi-temporal value is in facts only | `EpisodicNode.valid_at` ≈ store timestamp (not LLM-extracted temporal). Only `FactResult.valid_at`/`invalid_at` carry genuine temporal validity. | — |
| `BlockingToReactiveBridge` does not cover `graphQuery()` | `ReactiveCaseMemoryStore` and `BlockingToReactiveBridge` predate `GraphCaseMemoryStore` and have no knowledge of `graphQuery()`. Reactive callers wanting graph queries must inject `GraphCaseMemoryStore` directly and schedule on a worker thread (`Uni.createFrom().item(() -> store.graphQuery(q)).runSubscriptionOn(executor)`). | — |

---

## 6 — Configuration Reference

| Property | Required | Description |
|----------|----------|-------------|
| `quarkus.rest-client.graphiti.url` | Yes | Base URL of Graphiti service (e.g. `http://localhost:8000`) |
| `casehub.memory.graphiti.api-key` | No | Bearer token for Graphiti auth |

LLM provider, API key, and graph DB connection are configured in the Graphiti Docker service itself.

---

## 7 — Documentation Updates (implementation-doc-sync)

### PLATFORM.md / CLAUDE.md
- Capability ownership table: note `GraphCaseMemoryStore` as the graph-native SPI extension; document that `capabilities()` is the self-description mechanism for all adapters
- Boundary rules: add "Do not use Graphiti for domain-scoped or case-scoped memory isolation — use jpa/sqlite/mem0"
- `memory-graphiti/` entry in CLAUDE.md Modules table (see §3.1)
- No new cross-repo dependencies

### ARC42STORIES.MD
The following sections require updating to reflect this chapter (C16):

| Section | Change |
|---------|--------|
| §1 Description — Module structure | Add `memory-graphiti/` (Graphiti REST + temporal graph) |
| §1 Description — Core capabilities | Add `GraphCaseMemoryStore` (graph-native SPI extension) |
| §4 Quality Goals — Adapter exclusivity | Update: "Exactly one memory adapter active — combining two Priority(2) adapters causes AmbiguousResolutionException" |
| §5 Building Block View — L6 container | Add `memory-graphiti/` container: `@Alternative @Priority(2) GraphitiCaseMemoryStore` |
| §7 Deployment View table | Add row: `casehub-platform-memory-graphiti` | `compile` | Configure `quarkus.rest-client.graphiti.url` |
| §9.1 Journey Overview | J2 "Semantic Memory": mark C8–C11 complete; add C16 as new chapter |
| §9.2 Chapter Index | Add row: `16 | Graphiti Temporal Memory | J2 | L1, L2, L6 | 🔲 pending (#34)` |
| §9.2 Layer × Chapter matrix | Add C16 column: L1=Med (GraphCaseMemoryStore, GraphMemoryQuery, MemoryCapability), L2=Med (NoOpCaseMemoryStore update), L6=High |
| §9.3 Chapter Entries | Add C16 entry |
