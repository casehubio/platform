# memory-mem0 Design Spec

**Issue:** casehubio/platform#33
**Date:** 2026-06-04 (revised v4)
**Branch:** issue-033-memory-mem0

---

## Overview

A new `memory-mem0/` submodule providing a `CaseMemoryStore` adapter backed by the
[Mem0](https://mem0.ai) REST API. Mem0 stores memories as vector embeddings, enabling
semantic (`MemoryOrder.RELEVANCE`) recall alongside chronological retrieval.

**CDI tier:** `@Alternative @Priority(1) @ApplicationScoped` — beats `JpaMemoryStore
@ApplicationScoped` and `NoOpCaseMemoryStore @DefaultBean` when on the classpath.
Target: Mem0 OSS (Docker + pgvector).

### Why Mem0 rather than extending memory-jpa with pgvector?

Adding `MemoryOrder.RELEVANCE` to `memory-jpa` would require:

1. A new embedding-model SPI + a new adapter module (calling OpenAI, Ollama, etc.)
2. A `vector` column in the `memory_entry` schema — a breaking Flyway migration
3. Cosine-similarity SQL queries

Mem0 externalises all of that: it manages the embedding model, computes embeddings at
write time, stores them in pgvector, and returns ranked results. The CaseHub adapter only
speaks Mem0's REST API. Secondary benefit: the same adapter works against Mem0 cloud by
changing `quarkus.rest-client.mem0.url`.

### Key design decision — `infer: false`

Mem0's default (`infer: true`) sends stored text through an LLM, extracts atomic facts,
and may produce N Mem0 memories from one `store()` call. This breaks the CaseMemoryStore
contract at three levels: 1:1 `store()`/memoryId mapping, count semantics of
`query(limit)`, and reliable `eraseById()`.

Setting `infer: false` stores the text verbatim as a single Mem0 memory. Mem0 still
computes a vector embedding for the original text, so `MemoryOrder.RELEVANCE` semantic
search works via cosine similarity. The caller controls memory granularity.

---

## Mem0 OSS REST API — Source-Level Facts

**No `/v1/` prefix.** Routes are bare: `/memories`, `/search`. All `/v1/` references in
Mem0 docs refer to the cloud API. This adapter targets OSS.

**`app_id` does not exist in Mem0 OSS.** The three native filter dimensions are
`user_id`, `agent_id`, `run_id`. Tenant isolation is via compound `user_id` (see Field
Mapping).

**GET /memories has no `limit` or pagination parameter.** With at least one filter
provided, it returns ALL matching records unbounded. Limit is always applied client-side.

**POST /search supports `top_k`.** The search endpoint accepts a `top_k` integer that
caps the result set returned by Mem0. Used for RELEVANCE queries.

**Bulk DELETE uses query params, not a request body.**
`DELETE /memories?user_id=X&agent_id=Y` — at least one param required (HTTP 400
otherwise).

**DELETE without `agent_id` deletes all agent_id values.** Source-confirmed from
`mem0/memory/main.py`: `delete_all()` builds a filter dict excluding None values only, so
`user_id=X` alone lists and deletes every vector with that user_id regardless of stored
agent_id. `eraseEntity()` is correct.

**`infer` is supported in Mem0 OSS.** Source-confirmed from `server/main.py`: the
`MemoryCreate` Pydantic model includes `infer` as an explicit field alongside `user_id`,
`agent_id`, `run_id`, `metadata`, `memory_type`, and `prompt`. Setting `infer: false` in
the POST body is a first-class OSS capability, not a cloud-only feature. The entire
`infer: false` design decision rests on this; it is verified.

**Scores are NOT reliably comparable across separate `/search` calls.** The scoring
function in `mem0/utils/scoring.py` computes `min(raw / max_possible, 1.0)` where
`max_possible` is 1.0, 2.0, or 2.5 depending on whether BM25 results and entity boosts
are present for that specific call. Two calls to different entities can have different
`max_possible` values, making their scores non-comparable. Scores ARE valid for ordering
results within a single call.

---

## Field Mapping

| CaseHub | Mem0 API field | Notes |
|---|---|---|
| `"{tenantId}::{entityId}"` | `user_id` | Compound key — `::` is safe (UUIDs are hex+hyphen only). Embeds tenantId for storage-level isolation since `app_id` does not exist in Mem0 OSS. |
| `domain.name()` | `agent_id` | **Intentional semantic mismatch:** Mem0's `agent_id` means "which AI agent owns this memory." We use it as an opaque domain filter key. Harmless with `infer: false` — Mem0 treats it as a plain filter string. If `infer: true` is ever enabled, Mem0's deduplication logic scopes by `agent_id` — this would produce unexpected cross-domain memory consolidation. |
| `caseId` | `run_id` | Omitted from request when null |
| `text` | `messages[0].content` | role = "user" |
| `attributes` | `metadata` | Flat `Map<String,String>`. No reserved platform keys. |
| Mem0's `id` | `memoryId` | Mem0's UUID; 1:1 with `infer: false` |
| Mem0's `memory` field | `Memory.text()` | Verbatim original text with `infer: false` |
| Mem0's `created_at` | `Memory.createdAt()` | ISO-8601 string from list and search responses, parsed to `Instant`. Not present in `store()` response — set by Mem0 server clock at write time. |
| Mem0's `score` | (adapter-internal) | Present in search responses (`float`). Used for within-call ordering only. Not surfaced in `Memory` — the SPI is adapter-neutral; other adapters have no scores. |

No `casehub_created_at` metadata injection. No changes to `MemoryAttributeKeys`.

---

## Module Structure

```
memory-mem0/
  pom.xml
  src/main/java/io/casehub/platform/memory/mem0/
    Mem0CaseMemoryStore.java     # CaseMemoryStore @Alternative @Priority(1) @ApplicationScoped
    Mem0Client.java              # @RegisterRestClient(configKey="mem0")
                                 # @RegisterProvider(Mem0AuthFilter.class)
    Mem0AuthFilter.java          # @ApplicationScoped ClientRequestFilter — bearer token
    Mem0Config.java              # @ConfigMapping(prefix="casehub.memory.mem0")
    Mem0StoreException.java      # RuntimeException wrapping Mem0 HTTP errors
    dto/
      Mem0AddRequest.java        # POST /memories body
      Mem0AddResponse.java       # POST /memories response
      Mem0MemoryResult.java      # element of results in add response (id, memory, event)
      Mem0Memory.java            # memory in list/search responses
                                 # (id, memory, metadata, created_at, updated_at, score — Float nullable)
      Mem0SearchRequest.java     # POST /search body (query, user_id, agent_id, run_id, top_k, threshold)
                                 # threshold is always populated from Mem0Config.searchThreshold()
                                 # (default 0.1 — Mem0's built-in default). Mem0's scoring pre-filters
                                 # results below this value before returning. Tunable per deployment.
      Mem0ListResponse.java      # GET /memories response (results list)
  src/test/java/io/casehub/platform/memory/mem0/
    Mem0CaseMemoryStoreTest.java # @QuarkusTest + @QuarkusTestResource(Mem0WireMockResource)
    Mem0WireMockResource.java    # QuarkusTestResourceLifecycleManager — dynamic port
  src/test/resources/
    application.properties       # api-key=test-key, infer=false (url set by WireMock)
```

**Artifact:** `casehub-platform-memory-mem0`

---

## Dependencies

| Dependency | Scope | Notes |
|---|---|---|
| `casehub-platform-api` | compile | SPI, value types |
| `quarkus-rest-client-jackson` | compile | MicroProfile REST Client — same as scim/ |
| `io.micrometer:micrometer-core` | compile | Provides `@Timed` annotation. Interception requires `quarkus-micrometer` in the consuming application — not in this library module. `@Timed` is silently ignored if the consuming app has no Micrometer registry, making the dependency zero-cost for uninterested consumers. |
| `casehub-platform` | test | MockCurrentPrincipal, MockPreferenceProvider |
| `casehub-platform-testing` | test | FixedCurrentPrincipal |
| `quarkus-junit5` | test | @QuarkusTest |
| `wiremock` 3.13.0 | test | Simulated Mem0 server |
| `jandex-maven-plugin` | build | CDI discovery when consumed as JAR |

**No `quarkus:build` goal** — `CurrentPrincipal` is test-scope only; production CDI
validation would fail. `generate-code` and `generate-code-tests` kept for `@ConfigMapping`
and `@RegisterRestClient` annotation processing.

**Note on `micrometer-core`:** `quarkus-micrometer` (a Quarkus extension) must NOT be
added to a library module that skips `quarkus:build`. Extensions carry deployment
metadata that confuses consumers' augmentation phase, and ARC interceptors for `@Timed`
are registered by the consuming application's Quarkus augmentation anyway.

---

## Configuration

```properties
# REST client URL (MicroProfile standard — outside Mem0Config)
quarkus.rest-client.mem0.url=http://mem0:8000      # required

# Mem0-specific (prefix: casehub.memory.mem0)
casehub.memory.mem0.api-key=<required>             # no @WithDefault — startup fails if absent
casehub.memory.mem0.infer=false                    # default false
casehub.memory.mem0.since-search-top-k=500         # top_k for POST /search when since != null
casehub.memory.mem0.search-threshold=0.1           # pre-filter: results below this score dropped by Mem0
```

`Mem0Config`:

```java
@ConfigMapping(prefix = "casehub.memory.mem0")
public interface Mem0Config {
    String apiKey();            // required — SmallRye Config throws DeploymentException at startup
                                // if absent; no @WithDefault intentionally
    @WithDefault("false")
    boolean infer();

    @WithDefault("500")
    int sinceSearchTopK();      // top_k passed to POST /search when query.since() != null

    @WithDefault("0.1")
    double searchThreshold();   // Mem0 drops results below this score before returning.
                                // Always included in Mem0SearchRequest. Raise to reduce noise;
                                // lower to increase recall. Mem0's own default is 0.1.
}
```

`Mem0StartupValidator` is not needed. SmallRye Config validates `@ConfigMapping` methods
with no `@WithDefault` during the configuration phase (before any bean instantiation) and
throws `DeploymentException` with a clear property-missing message. A validator that calls
`config.apiKey()` eagerly adds no earlier failure.

**Timeouts** — via standard Quarkus REST client properties:
`quarkus.rest-client.mem0.connect-timeout`, `quarkus.rest-client.mem0.read-timeout`.

---

## SPI Implementation

### Observability

All five public SPI methods carry:
```java
@Timed(value = "casehub.memory.mem0", histogram = true,
       extraTags = {"operation", "<method-name>"})
```
The consuming application provides `quarkus-micrometer` and a registry; the annotation is
ignored without it (zero overhead on unconfigured consumers).

### `Mem0AuthFilter`

`@ApplicationScoped ClientRequestFilter`. Adds `Authorization: Bearer <api-key>` to every
request. Registered via `@RegisterProvider(Mem0AuthFilter.class)` on `Mem0Client`. **Not**
annotated `@Provider` — per GE-20260530-385dbb, `@Provider` on a REST client filter causes
Quarkus to instantiate it directly, bypassing CDI and leaving `@Inject` fields null.

### `store(MemoryInput)`

1. `MemoryPermissions.assertTenant(input.tenantId(), principal)` — first statement.
2. POST `/memories`:
   ```json
   {
     "messages": [{"role": "user", "content": "<text>"}],
     "user_id":  "{tenantId}::{entityId}",
     "agent_id": "<domain.name()>",
     "run_id":   "<caseId>",            // omitted when null
     "infer":    false,
     "metadata": { ...caller attributes... }
   }
   ```
3. Return `response.results().get(0).id()`. With `infer: false`, Mem0 produces exactly one
   result per message.
4. If `results` is empty (should not occur): throw `Mem0StoreException("store produced no result")`.
5. On non-2xx: catch `WebApplicationException` (per GE-20260526-a08a81), wrap in
   `Mem0StoreException` with HTTP status and Mem0 error body.

**`storeAll` partial failure.** *(Updated by platform#69.)* `Mem0CaseMemoryStore` now
overrides `storeAll()` with pre-flight `assertTenant` for all inputs before any HTTP call.
A `SecurityException` from `storeAll()` is therefore always clean — the pre-flight caught
the tenant violation before any `POST /memories` was issued, so nothing is in Mem0.
A `Mem0StoreException` from a mid-batch HTTP failure may still indicate a partial write —
items sent before the failing call are already persisted in Mem0 with no rollback path.
The IDs stored before failure are not surfaced. Tracked in platform#67.

### `query(MemoryQuery)`

1. `MemoryPermissions.assertTenant(query.tenantId(), principal)`.

2. **Path selection:**

   **RELEVANCE + non-null question → POST `/search` per entity.**
   Params per entity: `user_id`, `agent_id`, `run_id` (if caseId set),
   `top_k = query.since() == null ? query.limit() : config.sinceSearchTopK()`,
   `threshold = config.searchThreshold()`, `query = question`.
   Each entity's results arrive ranked by Mem0's blended score (valid within that call).
   Mem0 drops results below `threshold` before returning — callers relying on `query.limit()`
   being fully satisfied should lower the threshold if they see truncated results.

   **CHRONOLOGICAL or RELEVANCE + null question → GET `/memories` per entity.**
   `RELEVANCE` with no question is valid-by-design and degrades to chronological — consistent
   with `JpaMemoryStore` and `SqliteMemoryStore`. Callers expecting semantic ranking must
   supply a question. This is the platform convention; `MemoryOrder.RELEVANCE` documentation
   should be updated to note the fallback (out of scope for this module — see deferred items).
   Params per entity: `user_id`, `agent_id`, `run_id` (if caseId set). No `limit` param —
   GET `/memories` is unbounded; all matching records are returned.

3. **Multi-entity score handling.** For RELEVANCE + question, scores from separate
   `/search` calls are **not reliably comparable**. Mem0's scoring normalises by
   `max_possible` (1.0, 2.0, or 2.5 depending on BM25 signal presence per call). Entity A's
   score-0.9 and entity B's score-0.9 may reflect different `max_possible` denominators.
   Merging by interleaved score sort would give a false impression of global ranking.
   Correct behaviour: each entity's results are individually score-ranked (valid within one
   call); the merged list is entity-order concatenation (entity A's ranked results, then
   entity B's, etc.). Documented as a known limitation.

4. **`since` filtering (client-side):** if `query.since() != null`, parse
   `Mem0Memory.createdAt()` as `Instant` and exclude entries where `createdAt < since`.
   Entries with null or unparseable `created_at` are kept (defensive — treated as in-window).

5. **Final ordering:**
   - RELEVANCE + question: results are already in entity-order with per-entity score ranking.
     Apply `query.limit()` to the concatenated list.
   - CHRONOLOGICAL (or RELEVANCE + null question): sort merged list by `createdAt`
     **descending** (newest-first — the platform convention confirmed by
     `CaseMemoryStoreContractTest.chronological_order_is_default()`). Apply `query.limit()`.

6. Map each `Mem0Memory` to `Memory`. `Memory.attributes()` = `Mem0Memory.metadata()` as-is.
   `Memory.createdAt()` = parsed `Mem0Memory.createdAt()`.

### `erase(EraseRequest)`

1. `MemoryPermissions.assertTenant(request.tenantId(), principal)`.
2. `DELETE /memories?user_id={tenantId}::{entityId}&agent_id={domain}[&run_id={caseId}]`
   Omitting `run_id` deletes all case-scoped memories for that entity+domain+tenant —
   correct semantics for `erase(caseId=null)`.

### `eraseById(String memoryId, String tenantId)`

1. `MemoryPermissions.assertTenant(tenantId, principal)`.
2. `DELETE /memories/{memoryId}`.
3. HTTP 404 is swallowed — memory already absent.
4. Any other non-2xx: wrap in `Mem0StoreException`.

**Intra-tenant IDOR:** `assertTenant` verifies the caller belongs to `tenantId` but not
that `memoryId` belongs to an entity within that tenant. Any entity in the same tenant that
obtains a memoryId can delete another entity's memory. Cross-tenant IDOR is prevented by
the compound `user_id` encoding + `assertTenant`. Intra-tenant IDOR is acceptable in v1
(memoryIds are internal values returned only by `store()`, not guessable). A preflight
`GET /memories/{memoryId}` to verify ownership is tracked in platform#64.

### `eraseEntity(String entityId, String tenantId)`

1. `MemoryPermissions.assertTenant(tenantId, principal)`.
2. `DELETE /memories?user_id={tenantId}::{entityId}`
   No `agent_id` → all domains (source-confirmed: `delete_all()` filters by user_id alone,
   returns and deletes all agent_id values). No `run_id` → all cases. GDPR Art.17 wipe.

---

## Testing

### `Mem0WireMockResource`

Implements `QuarkusTestResourceLifecycleManager` (per GE-20260526-286ac7). `start()` returns:
```
quarkus.rest-client.mem0.url   → http://localhost:<dynamic>
casehub.memory.mem0.api-key    → test-key
casehub.memory.mem0.infer      → false
```

### `Mem0CaseMemoryStoreTest`

`@QuarkusTest @QuarkusTestResource(Mem0WireMockResource.class)`. Does **not** extend
`CaseMemoryStoreContractTest` — WireMock cannot maintain stateful round-trip semantics.

| Test | Asserts |
|---|---|
| `store_sends_infer_false_in_request_body` | Body contains `"infer": false` |
| `store_compound_user_id_encodes_tenant_and_entity` | `user_id = "tenant-1::entity-1"` |
| `store_field_mapping_all_fields` | agent_id, run_id, content, metadata all correct |
| `store_absent_caseId_omits_run_id` | No `run_id` key when caseId is null |
| `store_returns_mem0_memory_id` | Returns `results[0].id` |
| `store_bearer_token_on_every_request` | `Authorization: Bearer test-key` header |
| `store_tenant_mismatch_throws_before_http` | SecurityException; WireMock receives 0 calls |
| `store_non_2xx_throws_Mem0StoreException` | 500 → Mem0StoreException |
| `query_chronological_sends_get_without_limit_param` | GET /memories?user_id=... with no limit param |
| `query_relevance_with_question_sends_search_with_top_k` | POST /search with top_k = query.limit() |
| `query_relevance_with_question_and_since_uses_since_search_top_k` | POST /search top_k = sinceSearchTopK |
| `query_relevance_without_question_falls_back_to_get` | GET /memories called (not POST /search) |
| `query_multi_entity_relevance_concatenates_in_entity_order` | Entity A results precede entity B; no cross-entity score sort |
| `query_single_entity_relevance_ordered_by_score` | Results in descending score order for one entity |
| `query_maps_mem0_response_to_memory_records` | All Memory fields populated correctly |
| `query_since_filters_using_mem0_native_created_at` | Entry with older created_at excluded |
| `query_null_created_at_entry_kept_defensive` | Null created_at → included (not excluded) |
| `query_unparseable_created_at_entry_kept_defensive` | Malformed ISO-8601 string → included (DateTimeParseException caught, entry treated as in-window) |
| `query_multi_entity_fans_out_one_request_per_entity` | WireMock receives N requests for N entityIds |
| `query_limit_applied_to_merged_result_set` | Combined result truncated to limit |
| `query_tenant_mismatch_throws_before_http` | SecurityException; 0 HTTP calls |
| `erase_sends_delete_with_query_params` | `DELETE /memories?user_id=tenant::entity&agent_id=domain` |
| `erase_null_caseId_omits_run_id` | No run_id query param |
| `erase_tenant_mismatch_throws_before_http` | SecurityException; 0 HTTP calls |
| `eraseById_sends_delete_by_id_path` | `DELETE /memories/{id}` path |
| `eraseById_404_is_swallowed` | No exception on 404 |
| `eraseById_non_2xx_non_404_throws_Mem0StoreException` | 403 and 500 → Mem0StoreException |
| `eraseById_tenant_mismatch_throws_before_http` | SecurityException; 0 HTTP calls |
| `eraseEntity_sends_delete_with_compound_user_id_no_agent_id` | `DELETE /memories?user_id=tenant::entity` — no agent_id param |
| `eraseEntity_tenant_mismatch_throws_before_http` | SecurityException; 0 HTTP calls |
| `eraseEntity_sends_compound_key_for_correct_tenant_only` | user_id encodes t1, not t2; WireMock verifies URL construction only (behavioral isolation verified in future Testcontainers IT) |
| `storeAll_returns_all_memory_ids_in_order` | WireMock stubs two sequential POST /memories responses; storeAll(2 inputs) returns both IDs in input order |

**Future:** `Mem0CaseMemoryStoreContractIT extends CaseMemoryStoreContractTest` —
Testcontainers with Mem0 OSS + Ollama, tagged `@Tag("integration")`, excluded from
default surefire (platform#65).

---

## Consumer Guide

```xml
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-platform-memory-mem0</artifactId>
    <version>${project.version}</version>
</dependency>
```

```properties
quarkus.rest-client.mem0.url=http://mem0:8000
casehub.memory.mem0.api-key=${MEM0_API_KEY}
```

For `@Timed` observability: add `quarkus-micrometer` and a registry (e.g.
`quarkus-micrometer-registry-prometheus`) to the consuming application.

Add to consuming module's test `application.properties`:
```properties
quarkus.index-dependency.mem0.group-id=io.casehub
quarkus.index-dependency.mem0.artifact-id=casehub-platform-memory-mem0
```

**CDI displacement:** `@Alternative @Priority(1)` is globally activated by Quarkus ARC and
beats `@ApplicationScoped` beans deterministically (no ambiguity). Co-deploying `memory-mem0`
and `memory-jpa` is technically safe (mem0 always wins) but pointless. Co-deploying with
`memory-inmem` or `memory-sqlite` (both `@Alternative @Priority(1)`) causes ARC to throw
an ambiguous resolution error — do NOT combine those.

**Multi-entity RELEVANCE ordering:** for `MemoryQuery.forEntities(...)` with RELEVANCE order,
results from each entity are individually relevance-ranked, but results from different entities
are concatenated in the order `entityIds` is supplied — not by cross-entity score. The first
entity in the list is fully represented before the second. Callers should supply `entityIds`
in decreasing order of importance. Collections with no natural priority order (e.g., a `Set`)
should be sorted explicitly before calling `forEntities`.

**`since` and the Mem0 server clock:** `store()` does not return a `created_at` timestamp —
it is set by the Mem0 server at write time. Do not compute `since = Instant.now()` before
calling `store()` and then immediately query with that barrier: if the Mem0 server clock lags
the application clock, the freshly stored memory may fall below the `since` threshold and be
excluded from results. If you need to query immediately after storing, either omit `since` or
add a small buffer (e.g., `since = Instant.now().minusSeconds(5)` before `store()`).

**`infer=true` caveat:** the 1:1 `store()`/memoryId contract and predictable `query(limit)`
counts require `infer=false` (the default). With `infer=true`, Mem0's LLM may produce N
memories per `store()` call; `eraseById()` removes only the first; `query(limit=3)` may
return fewer than 3 of your application's "stored" memories.

---

## Known Limitations

| Concern | Notes |
|---|---|
| Tenant isolation | Application-level only: `assertTenant` + compound `user_id = "{tenantId}::{entityId}"`. Mem0 OSS has no native `app_id`; cross-tenant leakage is prevented by the encoding, but relies on application enforcement. |
| GET /memories is unbounded | No `limit` param on the list endpoint. Every chronological query fetches all matching records per entity, regardless of `query.limit()`. Limit is applied client-side. May be expensive for entities with hundreds of memories. |
| `since` + RELEVANCE ceiling | When `query.since() != null` and order is RELEVANCE + question, `top_k = sinceSearchTopK` (default 500). If an entity has more than 500 recent memories, some in the time window may be missed. Configurable via `casehub.memory.mem0.since-search-top-k`. |
| CHRONOLOGICAL + since is worse than RELEVANCE + since | RELEVANCE + since fetches at most `sinceSearchTopK` from Mem0 before filtering. CHRONOLOGICAL + since uses GET /memories which is unbounded — it fetches the entire entity history before filtering to the time window. A caller choosing CHRONOLOGICAL to be "safer" is actually fetching more data. If both orderings are viable, prefer RELEVANCE + question when using `since`. |
| Multi-entity RELEVANCE ordering | Scores from separate `/search` calls are not reliably comparable — Mem0's `max_possible` denominator (1.0/2.0/2.5) varies by call depending on BM25 signal. Cross-entity interleaving by score would be misleading. Results are concatenated in entity fan-out order, with per-entity relevance ordering preserved. |
| eraseById intra-tenant IDOR | Any entity within the same tenant holding a memoryId can delete another entity's memory. Cross-tenant IDOR is prevented by compound user_id + `assertTenant`. Preflight GET mitigation tracked in platform#64. |
| Multi-entity fan-out | Sequential — up to 25 HTTP requests for `MAX_ENTITY_IDS`. |
| `storeAll` partial failure | `SecurityException` from `storeAll()` is always clean — pre-flight (platform#69) ensures zero HTTP before any tenant violation throws. `Mem0StoreException` from a mid-batch HTTP failure may still leave prior items in Mem0 with no rollback. Caller cannot recover stored IDs. Tracked in platform#67. |
| `agent_id` mismatch | Mem0's `agent_id` semantically means "which AI agent owns this." We use it for `domain.name()`. Harmless with `infer: false`. Could produce unexpected deduplication if `infer: true` is enabled. |
| Score not surfaced in `Memory` | Scores are adapter-specific; the `CaseMemoryStore` SPI is adapter-neutral (other adapters have no scores). Callers receive relevance-ordered results but cannot inspect individual scores post-query. |
| RELEVANCE + null question | Valid-by-design — degrades to CHRONOLOGICAL, consistent with JPA and SQLite adapters. Callers expecting semantic ranking must supply a question. `MemoryOrder.RELEVANCE` javadoc should be updated to document the fallback (tracked as minor doc task). |
| Testcontainers CI | Real Mem0 integration test deferred — requires Ollama or equivalent in CI (platform#65). |

---

## Deferred Issues

| Issue | Ref |
|---|---|
| eraseById preflight GET for intra-tenant IDOR | platform#64 |
| Testcontainers Mem0 integration test | platform#65 |
| Observability (@Timed) for all memory adapters platform-wide | platform#66 |
| `storeAll` API design — partial failure IDs not surfaced | platform#67 |
