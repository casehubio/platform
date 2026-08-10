# CaseMemoryStore Consumer Feedback — Design Spec

**Date:** 2026-05-30
**Issue:** platform#48
**References:** platform#27 (original SPI), platform#36 (contract tests)

## Context

platform#48 is consumer feedback from devtown on gaps in the surrounding design of
`CaseMemoryStore`. The core SPI is correct; the gaps are in conventions and API
expressiveness around it. This spec covers the resolution.

## Scope

Five gaps identified in #48. Resolution:

| Gap | Description | Resolution |
|-----|-------------|------------|
| 1 | No standard emission pattern | Document three approaches; defer to app feedback |
| 2 | No attribute key conventions | Add `MemoryAttributeKeys` to platform-api |
| 3 | Single-entity query only | Change `entityId: String` → `entityIds: List<String>` + fluent API |
| 4 | `since` is a hard cutoff, no recency signal | Replace `recentFirst: boolean` with `MemoryOrder` enum |
| 5 | `text` field ergonomics undocumented | Javadoc on `MemoryInput.text` |

SPI changes (Gaps 2–5) land in a single changeset before any new adapter work.
Gap 1 is documented but not resolved — a child issue off #48 tracks app feedback.

## Delivery order

1. This changeset: platform-api + memory-inmem + memory-jpa + contract tests (#36)
2. #37, #33, #34, #40 — all build against the updated SPI

No PRs — direct push to main.

---

## Gap 2 — `MemoryAttributeKeys`

New class `io.casehub.platform.api.memory.MemoryAttributeKeys` in `platform-api`.

### Key naming convention

Platform-reserved attribute keys use **kebab-case**. Consumer applications should
follow the same convention for their domain-specific keys to avoid naming collisions.

### Reserved keys

```java
public final class MemoryAttributeKeys {

    /** Identity of the actor who produced this memory fact. Use the OIDC subject
     *  (same value as CurrentPrincipal.actorId()) when available. */
    public static final String ACTOR_ID   = "actor-id";

    /** Role of the actor within the domain (e.g. "reviewer", "investigator", "clinician").
     *  Supplementary to ACTOR_ID — tooling that needs actor identity should use ACTOR_ID. */
    public static final String ACTOR_ROLE = "actor-role";

    /** Outcome of the action or case from which this memory was emitted.
     *  The key is reserved so tooling can locate outcome facts across domains;
     *  values are domain-specific and defined by the consumer application
     *  (e.g. "DONE"/"DECLINE" in devtown; something else in clinical or aml). */
    public static final String OUTCOME    = "outcome";

    /** Confidence score as a decimal string formatted to 4 decimal places.
     *  Always use {@link #formatConfidence} to write and {@link #parseConfidence}
     *  to read — do not format manually. */
    public static final String CONFIDENCE = "confidence";

    private MemoryAttributeKeys() {}

    /** Formats a confidence value [0.0, 1.0] to the canonical 4-decimal-place string. */
    public static String formatConfidence(double v) {
        if (v < 0 || v > 1) throw new IllegalArgumentException("confidence must be in [0,1], got: " + v);
        return String.format("%.4f", v);
    }

    /** Parses a confidence string previously written by {@link #formatConfidence}. */
    public static double parseConfidence(String s) {
        return Double.parseDouble(s);
    }
}
```

`CONFIDENCE` requires formatting helpers because `"0.85"`, `"0.850"`, and `"0.8500"` are
semantically equal but not string-equal; cross-domain comparison breaks without a canonical form.

`SOURCE_CASE_ID` is explicitly not included: `MemoryInput.caseId` is the first-class field
for the originating case; a duplicate attribute adds no value.

---

## Gap 3 — Multi-entity recall

### `MemoryQuery` shape

`entityId: String` → `entityIds: List<String>`. Maximum 25 entities per query.

The limit of 25 is derived from the realistic party-to-a-case model: real case party counts
are 2–15; 25 provides generous headroom while keeping `IN (:entityIds)` trivial for JPA and
the in-mem fan-out firmly O(n) over a small n. Callers approaching 25 should evaluate whether
they are doing a contextual recall or a batch lookup — the latter requires a different API.

### Constructor policy

Java records expose a public canonical constructor by spec; the canonical constructor is
retained but **not the intended construction path**. Use the factory methods.

### Factory methods (required fields only)

```java
public static MemoryQuery forEntity(String entityId, MemoryDomain domain, String tenantId)
public static MemoryQuery forEntities(List<String> entityIds, MemoryDomain domain, String tenantId)
```

Default limit: 20. Default order: `MemoryOrder.CHRONOLOGICAL`.

Both factory method Javadocs must state these defaults explicitly — callers who omit
`withLimit()` get 20 silently, which is intentional but must be discoverable from the API
rather than inferred from the prose. The original `MemoryQuery` required an explicit limit
(compact constructor validated `limit >= 1`); this is a deliberate ergonomics change.

### Fluent with* modifiers

```java
public MemoryQuery withCaseId(String caseId)
public MemoryQuery withQuestion(String question)
public MemoryQuery withLimit(int limit)
public MemoryQuery withSince(Instant since)
public MemoryQuery withOrder(MemoryOrder order)
```

Each returns a new `MemoryQuery` with the single field replaced. Required fields
(`entityIds`, `domain`, `tenantId`) are not modifiable after construction.

### Limit semantics

`limit` applies to the **combined result set**, not per-entity. A query for entities A and B
with `limit=10` returns the 10 most relevant results across both, not 10 from each.
Callers needing per-entity limits should issue separate queries.

### Returned `Memory` records

Each returned `Memory` carries its own `entityId: String` identifying which entity the fact
belongs to. Multi-entity query results are mixed in the result list; callers distinguish by
inspecting `Memory.entityId()`.

### `eraseEntity` asymmetry

`EraseRequest` retains a single `entityId`. GDPR Art.17 erasure is intentionally entity-scoped:
a right-to-erasure request names one data subject. The multi-entity extension is a query
optimisation, not an erasure model. This asymmetry is by design.

---

## Gap 4 — `MemoryOrder` enum

Replace the proposed `recentFirst: boolean` with an enum:

```java
public enum MemoryOrder {
    /**
     * Results ordered by creation time, newest first.
     * All adapters honour this mode.
     */
    CHRONOLOGICAL,

    /**
     * Results ordered by relevance to {@code MemoryQuery.question}.
     * Adapters with relevance ranking (JPA FTS via ts_rank, Mem0 vector search,
     * Graphiti temporal graph) honour this. Non-semantic adapters silently fall
     * back to CHRONOLOGICAL.
     * If {@code question} is null, all adapters fall back to CHRONOLOGICAL.
     */
    RELEVANCE
}
```

Default in `MemoryQuery`: `CHRONOLOGICAL`. This preserves existing behaviour for all callers.

### JPA adapter behaviour

The JPA adapter already supports relevance ranking via `ts_rank` in its FTS path.
`MemoryOrder` makes the intent explicit and lets the adapter honour it correctly:

| `order` | `question` | `fts.enabled` | Path used |
|---------|-----------|----------------|-----------|
| `CHRONOLOGICAL` | any | any | `queryChronological` (createdAt DESC) |
| `RELEVANCE` | non-null | true | `queryFts` (ts_rank DESC) |
| `RELEVANCE` | null | any | fall back to `queryChronological` |
| `RELEVANCE` | non-null | false | fall back to `queryChronological` |

The spec previously stated "relevance ranking is not in scope for JPA" — this was wrong.
JPA already does `ORDER BY ts_rank(...) DESC` in the FTS path.

### In-mem adapter

Always `CHRONOLOGICAL`; `RELEVANCE` is accepted without error and falls back silently.
This is documented in the adapter and in `MemoryOrder.RELEVANCE` Javadoc.

### Interaction with `since`

`since` narrows the search space in both modes. In `RELEVANCE` mode with a `since` window,
the adapter ranks results by relevance within the time window — not across all history.
Callers doing semantic search with a tight `since` window should expect reduced recall
if relevant facts fall outside the window.

---

## Gap 5 — `MemoryInput.text` Javadoc + blank validation

### Blank validation

Add to `MemoryInput` compact constructor:

```java
if (text.isBlank()) throw new IllegalArgumentException("text must not be blank");
```

`MemoryDomain` already rejects blank; `MemoryInput.text` should be consistent.

### Javadoc on `text`

> `text` must be human-readable natural language when using semantic adapters (Mem0, Graphiti).
> This field is embedded for vector search — both accuracy and completeness matter:
> truncating or abbreviating text degrades retrieval quality. Serialised JSON or structured
> data will produce degraded semantic results. Use `attributes` for structured metadata.

---

## Gap 1 — Emission pattern (deferred)

Three approaches to document in `CaseMemoryStore.store()` Javadoc and in the child issue:

**Option A — Application-layer CDI observer**
Application emits a domain event from its case outcome handler; an observer in the same
application calls `store()`. Keeps messaging and memory decoupled at the cost of
per-application boilerplate.

**Option B — Optional `memory-cdi/` platform module**
Platform provides CDI infrastructure wiring domain events to `store()`. Keeps applications
clean at the cost of coupling domain event types to the platform.

**Option C — Platform-defined narrow event type**
Platform defines a `MemoryStoreRequest` event type that applications emit. A thin platform CDI
module listens and calls `store()`. Applications map domain events → `MemoryStoreRequest`
(preserving separation); platform handles wire-up boilerplate. Avoids Option B's domain-type
coupling while reducing Option A's per-application boilerplate.

All three are under investigation. Consumer apps (devtown, clinical, aml) should evaluate
each and provide feedback. A child issue off #48 tracks the outcome.

The child issue should also note the `storeAll` N-transaction risk: `JpaMemoryStore` does
not override `storeAll()`, so the default issues N individual `@Transactional(REQUIRED)`
calls. Emission paths that channel bulk facts through `storeAll()` will pay N database
round-trips. The child issue should include a `storeAll` batch-insert override for JPA
as part of the emission pattern work.

---

## Adapter updates

### `memory-inmem`

- `query()`: fan out across all `BucketKey` entries matching any `entityId` in `entityIds`;
  merge and apply `limit` to combined stream
- `MemoryOrder.RELEVANCE`: silently falls back to `CHRONOLOGICAL` (in-mem has no
  relevance ranking)
- No change to `BucketKey` — stays per-entity

### `memory-jpa`

- `queryChronological` and `queryFts`: update `WHERE` clause to `entity_id IN (:entityIds)`
- `MemoryOrder` routing: see table in Gap 4 section above
- JPA handles single-element `IN` lists identically to multi-element — no branching needed

---

## Contract tests (#36)

Additions to the existing adapter contract test suite:

1. **Multi-entity query:** store facts for two entities, query with `forEntities(...)`, assert both returned within limit
2. **Limit is combined:** store 5 facts for entity A and 5 for entity B, query with `limit=6`, assert exactly 6 returned
3. **`MemoryOrder.RELEVANCE` accepted:** verify both adapters accept it without error (behavioural correctness deferred to semantic adapters)
4. **`MemoryAttributeKeys` round-trip:** store with reserved keys using `formatConfidence`, recall and assert keys preserved, `parseConfidence` round-trips correctly
5. **Blank `text` rejected:** verify `MemoryInput` constructor throws on blank text

Closes #36 as part of this changeset.

---

## Issues

| Action | Issue |
|--------|-------|
| This changeset | references #48, closes #36 |
| CDI emission investigation (Options A/B/C + storeAll batch) | new child issue off #48 |
| Adapter work builds against updated SPI | #37, #33, #34, #40 |
