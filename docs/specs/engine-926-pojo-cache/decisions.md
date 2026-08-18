## D1: Cache strategy — single-entry volatile

**Choice:** Single volatile field holding a `CachedPojo(CaseContext context, long version, Class<?> contextClass, Object pojo)` record. Cache hit when `context ==` (identity) AND `version ==` AND `contextClass ==`. Miss → deserialize, store new entry.
**Alternatives:**
- ConcurrentHashMap keyed by (context, version, class) — handles interleaved cases but adds complexity, size bounds, and eviction logic for marginal gain
- ThreadLocal — clean isolation per thread, but heavier with virtual threads and doesn't help when evaluations cross thread boundaries
**Rationale:** The primary case is N expressions evaluated in sequence for one context change. Single-entry captures this exactly. Interleaved case (two cases' evaluations alternating) falls back to full deserialization — no worse than current behavior. Zero GC pressure (one record allocation per miss, previous is collected). No listener subscription needed — version check is the invalidation signal.
**Trade-offs:** Interleaved multi-case evaluation loses the cache benefit. Acceptable — the optimization targets the N-per-cycle case, not cross-case sharing.
**Sources:** `MvelExpressionEngine.java` (runtime/), `CaseContext.getVersion()`, #238 spec §3
**Exploration:** quick
**Status:** captured
