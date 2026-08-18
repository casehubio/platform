# POJO Deserialization Cache — engine#926

## Summary

Cache the deserialized POJO in `MvelExpressionEngine` to avoid redundant Jackson
deserialization when multiple MVEL expressions are evaluated against the same
`CaseContext` state within one evaluation cycle.

## Motivation

When a context change triggers re-evaluation of N MVEL expressions (bindings, goals,
milestones), `deserializeToPojo()` runs N times — each creating a new `JacksonPojoBridge<T>`
and deserializing the same `JsonNode` to the same POJO type. For a definition with 10
MVEL expressions, that's 10 Jackson deserializations of identical data per context change.

## Design

### §1 Single-entry volatile cache

Add a `volatile CachedPojo` field to `MvelExpressionEngine`:

```java
private volatile CachedPojo cachedPojo;

private record CachedPojo(CaseContext context, long version,
                           Class<?> contextClass, Object pojo) {}
```

Before calling `deserializeToPojo()`, check the cache:

```java
private Object resolveTypedPojo(CaseContext context, Class<?> contextClass) {
    CachedPojo cached = this.cachedPojo;
    if (cached != null
            && cached.context == context
            && cached.version == context.getVersion()
            && cached.contextClass == contextClass) {
        return cached.pojo;
    }
    Object pojo = deserializeToPojo(context, contextClass);
    this.cachedPojo = new CachedPojo(context, context.getVersion(), contextClass, pojo);
    return pojo;
}
```

Replace both call sites (`evaluate()` line 69 and `extractString()` line 127) with
`resolveTypedPojo(context, typed.contextClass())`.

### §2 Cache key semantics

- **`context ==`** (identity): same CaseContext object — different cases have different
  instances, so no cross-case pollution
- **`version ==`**: `CaseContext.getVersion()` increments on every mutation — if the
  context changed between evaluations, the version differs and we re-deserialize
- **`contextClass ==`**: same POJO type — defensive against theoretical multi-type
  scenarios (one contextClass per definition in practice)

### §3 Thread safety

`volatile` guarantees visibility across threads. The record is immutable. The worst case
under concurrency: two threads overwrite each other's cache entry, causing redundant
deserialization. This is identical to current behavior (no deserialization at all) —
never worse, often better.

No `synchronized` needed. No `AtomicReference` needed. The single-entry pattern is
inherently race-tolerant because a stale read just means a cache miss.

### §4 No GC or lifecycle concerns

- The `CachedPojo` record holds a strong reference to the CaseContext, but only until the
  next cache entry replaces it. Since evaluation cycles are short-lived (milliseconds),
  the previous entry is eligible for GC almost immediately.
- `MvelExpressionEngine` is `@ApplicationScoped` — one instance for the app lifetime.
  The cache field is a single record, not a growing collection. No eviction needed.

## Backward Compatibility

No API changes. No behavioral changes. The optimization is entirely internal to
`MvelExpressionEngine`. All existing tests pass unchanged.

## Affected Code

| Area | Module | What changes |
|------|--------|-------------|
| Cache field + record | `runtime/` | `MvelExpressionEngine` — add `CachedPojo` record, `cachedPojo` field |
| Cache lookup | `runtime/` | `MvelExpressionEngine` — new `resolveTypedPojo()` helper |
| evaluate() | `runtime/` | Replace `deserializeToPojo()` call with `resolveTypedPojo()` |
| extractString() | `runtime/` | Replace `deserializeToPojo()` call with `resolveTypedPojo()` |
| Tests | `runtime/` | Test cache hit (same context+version), miss (version change), miss (different context) |

## References

- `runtime/src/main/java/io/casehub/engine/internal/engine/MvelExpressionEngine.java` — primary file
- `api/src/main/java/io/casehub/api/context/CaseContext.java` — `getVersion()` contract
- #238 spec §3 — caching requirement
- GE-20260818-68c8a3 — TypedEvaluator pattern
