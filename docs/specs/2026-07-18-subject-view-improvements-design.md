# Subject View Toolkit — Improvements Before Work-Queues Migration

**Date:** 2026-07-18
**Issue:** casehubio/platform#184
**Status:** Design
**Refs:** casehubio/platform#175 (original toolkit)

## Summary

Seven improvements to the subject view toolkit (platform-view modules)
identified during migration review from casehub-work's perspective.
Grouped by priority: must-fix (blocks migration), should-fix (saves
every consumer work), nice-to-have (optimization/convenience).

All changes are within casehub-platform. No cross-repo modifications —
engine and work-queues are consumers, not targets.

## §1 — CHANGED Event Type

**Problem:** `ViewEventType` has ADDED/REMOVED only. Work-queues fires
CHANGED when a subject mutates while remaining in a view — dashboards
and SSE consumers need it to know "this item in your view just updated."

**Change:** Add `CHANGED` to `ViewEventType`. Add
`SubjectViewEvaluator.computeEvents()` to replace `diff()` — emits
ADDED, REMOVED, and CHANGED events. CHANGED fires when a viewId appears
in both `before` and `after` maps.

```java
public enum ViewEventType { ADDED, REMOVED, CHANGED }
```

**Method change — `diff()` → `computeEvents()`:**

The existing `diff()` computes ADDED/REMOVED only and returns an empty
list when membership is unchanged. The new `computeEvents()` has a
broader contract: it computes all view events for a subject
re-evaluation, including CHANGED for stable memberships. `diff()` is
removed — callers migrate to `computeEvents()`.

```java
public List<SubjectViewEvent> computeEvents(
        UUID subjectId, String tenancyId,
        Map<UUID, String> before, Map<UUID, String> after) {
    List<SubjectViewEvent> events = new ArrayList<>();

    before.forEach((id, name) -> {
        if (after.containsKey(id)) {
            events.add(new SubjectViewEvent(subjectId, id,
                after.get(id), ViewEventType.CHANGED, tenancyId));
        } else {
            events.add(new SubjectViewEvent(subjectId, id, name,
                ViewEventType.REMOVED, tenancyId));
        }
    });

    after.forEach((id, name) -> {
        if (!before.containsKey(id)) {
            events.add(new SubjectViewEvent(subjectId, id, name,
                ViewEventType.ADDED, tenancyId));
        }
    });

    return events;
}
```

CHANGED uses `after.get(id)` for viewName — if a view was renamed, the
event carries the current name.

**Semantic contract:** CHANGED means "this subject was already in this
view and something triggered a re-evaluation." The domain controls
event volume by choosing which lifecycle events trigger
`computeEvents()` calls. The platform cannot know which subject
mutations are relevant to which views — that is domain knowledge.

**Affected files:**
- `platform-api/.../view/ViewEventType.java`
- `platform-view/.../view/SubjectViewEvaluator.java`
- `platform-view/.../view/SubjectViewEvaluatorTest.java`

## §2 — additionalConditions on SubjectViewSpec

**Problem:** The original design spec included `additionalConditions` but
the implementation dropped it. Work-queues stores JEXL filter expressions
on `QueueView.additionalConditions` — views that select by label pattern
*and* a domain-evaluated condition.

**Change:** Add `String additionalConditions` (nullable) to
`SubjectViewSpec`. Platform stores it, never interprets it. Domains use
it for expression-language filters (JEXL, MVEL, JQ).

```java
public record SubjectViewSpec(
    UUID id,
    String name,
    String tenancyId,
    String labelPattern,
    Path scope,
    String sortField,
    String sortDirection,
    String additionalConditions,
    Instant createdAt
) { ... }
```

**Column:** `TEXT`, nullable. The platform never queries, indexes, or
interprets this field — it is opaque domain data stored and retrieved
as-is. `TEXT` avoids an arbitrary length ceiling; PostgreSQL stores
`TEXT` and `VARCHAR` identically.

**Affected files:**
- `platform-api/.../view/SubjectViewSpec.java`
- `platform-api/.../view/SubjectViewSpecTest.java`
- `platform-view-jpa/.../view/jpa/SubjectViewEntity.java` (new field + toSpec/fromSpec)
- `platform-view-jpa/.../resources/db/view/migration/V5001__subject_view_additional_conditions.sql`
- `platform-view-inmem/.../view/inmem/InMemorySubjectViewStore.java` (carry field in save)
- All callers of SubjectViewSpec constructor (pre-release — API break is fine)

## §3 — InMemorySubjectViewQuerySupport

**Problem:** `platform-view-inmem` has store and tracker implementations
but no query implementation. Every domain consumer writing tests hits
this gap.

**Change:** Abstract helper class in `platform-view-inmem`, parallel to
`JpaLabelPatternQuerySupport<E, L>`:

```java
public abstract class InMemorySubjectViewQuerySupport<S>
        implements SubjectViewQuery<S> {

    private final Supplier<Collection<S>> subjectSource;
    private final Function<S, Set<String>> labelExtractor;
    private final Function<S, String> tenancyExtractor;
    private final Function<String, Comparator<S>> sortFieldResolver;

    protected InMemorySubjectViewQuerySupport(
            Supplier<Collection<S>> subjectSource,
            Function<S, Set<String>> labelExtractor,
            Function<S, String> tenancyExtractor,
            Function<String, Comparator<S>> sortFieldResolver) {
        this.subjectSource = subjectSource;
        this.labelExtractor = labelExtractor;
        this.tenancyExtractor = tenancyExtractor;
        this.sortFieldResolver = sortFieldResolver;
    }

    @Override
    public List<S> findByView(SubjectViewSpec view) {
        var stream = subjectSource.get().stream()
            .filter(s -> tenancyExtractor.apply(s).equals(view.tenancyId()))
            .filter(s -> labelExtractor.apply(s).stream()
                .anyMatch(p -> LabelPatternMatcher.matches(
                    view.labelPattern(), p)));
        return sorted(stream, view).toList();
    }

    @Override
    public List<S> findByView(SubjectViewSpec view, int offset, int limit) {
        return findByView(view).stream()
            .skip(offset).limit(limit).toList();
    }

    @Override
    public long countByView(SubjectViewSpec view) {
        return findByView(view).size();
    }

    private Stream<S> sorted(Stream<S> stream, SubjectViewSpec view) {
        if (view.sortField() == null || sortFieldResolver == null) {
            return stream;
        }
        Comparator<S> cmp = sortFieldResolver.apply(view.sortField());
        if (cmp == null) return stream;
        if ("DESC".equalsIgnoreCase(view.sortDirection())) {
            cmp = cmp.reversed();
        }
        return stream.sorted(cmp);
    }
}
```

**Sort field resolver.** Maps `SubjectViewSpec.sortField()` string names
to type-safe comparators. The helper handles direction (`DESC` reverses
the comparator). This matches `JpaLabelPatternQuerySupport`'s Criteria
API `orderBy()` behavior without requiring reflection on entity fields.

**Not a CDI bean.** `SubjectViewQuery<S>` is generic — CDI erases the
type parameter. Domains extend with concrete `@ApplicationScoped` types
that CDI can distinguish. Same pattern as `JpaLabelPatternQuerySupport`.

**Tenancy filtering included** for parity with the JPA helper, which
always filters by `view.tenancyId()`.

**Affected files:**
- `platform-view-inmem/.../view/inmem/InMemorySubjectViewQuerySupport.java` (new)
- `platform-view-inmem/.../view/inmem/InMemorySubjectViewQuerySupportTest.java` (new)

## §4 — Bulk getLastKnownMembership

**Problem:** Current API is per-subject. Cascade scenarios (INFERRED
label engine) touch dozens of subjects in one transaction — N individual
DB lookups for before-state.

**Change:** Default method on `ViewMembershipTracker` that loops over the
single-subject method. JPA overrides with `WHERE subject_id IN (...)`.

**SPI contract:** The returned map contains entries only for subjects
that have at least one view membership. Subjects with no memberships
are absent from the map. Callers use
`result.getOrDefault(subjectId, Map.of())` for safe access.

```java
default Map<UUID, Map<UUID, String>> getLastKnownMembership(
        Set<UUID> subjectIds) {
    Map<UUID, Map<UUID, String>> result = new HashMap<>();
    for (UUID subjectId : subjectIds) {
        Map<UUID, String> membership = getLastKnownMembership(subjectId);
        if (!membership.isEmpty()) {
            result.put(subjectId, membership);
        }
    }
    return result;
}
```

**JPA override** — single query:

```java
if (subjectIds.isEmpty()) return Map.of();
return em.createQuery(
        "SELECT e FROM ViewMembershipEntity e WHERE e.subjectId IN :sids",
        ViewMembershipEntity.class)
    .setParameter("sids", subjectIds)
    .getResultList()
    .stream()
    .collect(Collectors.groupingBy(
        e -> e.subjectId,
        Collectors.toMap(e -> e.viewId, e -> e.viewName)));
```

**Affected files:**
- `platform-api/.../view/ViewMembershipTracker.java` (default method)
- `platform/.../view/NoOpViewMembershipTracker.java` (override)
- `platform-view-inmem/.../view/inmem/InMemoryViewMembershipTracker.java` (override)
- `platform-view-jpa/.../view/jpa/JpaViewMembershipTracker.java` (optimized override)
- Tests for each implementation

## §5 — SubjectViewOrchestrator

**Problem:** Every domain consumer writes the same ~12 lines of
boilerplate: inject evaluator + store + tracker, fetch before-state,
fetch views, evaluate membership, diff, update tracker, fire events.

**Change:** New `@ApplicationScoped` in `platform-view` that composes
evaluator + store + tracker:

```java
@ApplicationScoped
public class SubjectViewOrchestrator {

    @Inject SubjectViewEvaluator evaluator;
    @Inject SubjectViewStore viewStore;
    @Inject ViewMembershipTracker tracker;

    public List<SubjectViewEvent> evaluateAndTrack(
            UUID subjectId, String tenancyId, Set<String> labelPaths) {
        var before = tracker.getLastKnownMembership(subjectId);
        var views = getViews(tenancyId);
        var after = evaluator.evaluateMembership(labelPaths, views);
        var events = evaluator.computeEvents(subjectId, tenancyId, before, after);
        tracker.updateMembership(subjectId, after);
        return events;
    }

    public Map<UUID, List<SubjectViewEvent>> evaluateAndTrackBatch(
            Map<UUID, Set<String>> subjectLabelPaths, String tenancyId) {
        var subjectIds = subjectLabelPaths.keySet();
        var allBefore = tracker.getLastKnownMembership(subjectIds);
        var views = getViews(tenancyId);

        Map<UUID, List<SubjectViewEvent>> result = new LinkedHashMap<>();
        subjectLabelPaths.forEach((subjectId, labelPaths) -> {
            var before = allBefore.getOrDefault(subjectId, Map.of());
            var after = evaluator.evaluateMembership(labelPaths, views);
            var events = evaluator.computeEvents(subjectId, tenancyId, before, after);
            tracker.updateMembership(subjectId, after);
            result.put(subjectId, events);
        });
        return result;
    }

    public SubjectViewSpec saveView(SubjectViewSpec spec) {
        var saved = viewStore.save(spec);
        invalidateViewCache(spec.tenancyId());
        return saved;
    }

    public void deleteView(UUID viewId) {
        var spec = viewStore.findById(viewId);
        viewStore.delete(viewId);
        spec.ifPresent(s -> invalidateViewCache(s.tenancyId()));
    }

    private List<SubjectViewSpec> getViews(String tenancyId) {
        // see §6 for caching layer
        return viewStore.findByTenancy(tenancyId);
    }
}
```

**Batch method** uses the bulk `getLastKnownMembership(Set<UUID>)` from
§4 and fetches views once for the whole batch. Per-subject evaluation is
sequential — `updateMembership` writes must be ordered for correctness
in cascade scenarios.

**Domain observer after this change (5 lines, down from 12):**

```java
@ApplicationScoped
public class CaseViewObserver {
    @Inject SubjectViewOrchestrator views;
    @Inject Event<CaseViewEvent> eventBus;

    @Transactional
    void onCaseLabelsChanged(@Observes CaseLabelsChangedEvent e) {
        views.evaluateAndTrack(e.caseId(), e.tenancyId(), e.labelPaths())
            .forEach(evt -> eventBus.fire(new CaseViewEvent(evt)));
    }
}
```

**Composition over inheritance.** The orchestrator can be unit tested
independently, used by non-CDI code, mocked in domain tests, and
extended with caching (§6) and scope filtering (§7) without changing
domain observer code.

**No @DefaultBean.** The orchestrator is in `platform-view` (runtime
module), not `platform-api` (SPI). If the module isn't on the classpath,
domains use the building blocks directly.

**Relationship to additionalConditions (§2).** The orchestrator evaluates
membership by label-pattern matching only. Views with
`additionalConditions` carry an opaque condition string that the platform
does not interpret — condition evaluation is domain logic. This means the
orchestrator may record membership for a subject that matches a view's
label pattern but fails its additional condition.

Domains with `additionalConditions` have two approaches:
1. Use the orchestrator and post-filter events — accept that the tracker
   holds label-match memberships, evaluate conditions on received events,
   and discard false positives. Simple but the tracker has broader state.
2. Use the building blocks directly (`evaluator` + `tracker` + `store`) —
   interleave condition evaluation with membership tracking for precise
   state. More code but exact memberships.

The orchestrator is designed for the common case: views defined by label
patterns alone. This is not a limitation — it is a scope boundary.

**Affected files:**
- `platform-view/.../view/SubjectViewOrchestrator.java` (new)
- `platform-view/.../view/SubjectViewOrchestratorTest.java` (new)

## §6 — View Definition Caching

**Problem:** `findByTenancy()` is called on every subject mutation. For
high-throughput domains, that's a hot query.

**Change:** TTL-based ConcurrentHashMap cache inside the orchestrator's
`getViews()` method. Default off — opt-in via config.

```java
@ConfigProperty(name = "casehub.view.cache.ttl-seconds", defaultValue = "0")
int cacheTtlSeconds;

private final ConcurrentHashMap<String, CachedViews> viewCache
    = new ConcurrentHashMap<>();

private List<SubjectViewSpec> getViews(String tenancyId) {
    if (cacheTtlSeconds <= 0) {
        return viewStore.findByTenancy(tenancyId);
    }
    var cached = viewCache.get(tenancyId);
    if (cached != null && !cached.isExpired(cacheTtlSeconds)) {
        return cached.views();
    }
    var views = viewStore.findByTenancy(tenancyId);
    viewCache.put(tenancyId, new CachedViews(views, Instant.now()));
    return views;
}

private void invalidateViewCache(String tenancyId) {
    viewCache.remove(tenancyId);
}

private record CachedViews(List<SubjectViewSpec> views, Instant fetchedAt) {
    boolean isExpired(int ttlSeconds) {
        return Instant.now().isAfter(fetchedAt.plusSeconds(ttlSeconds));
    }
}
```

**Default off** (`ttl-seconds = 0`). High-throughput domains opt in with
e.g. `casehub.view.cache.ttl-seconds=30`. View definitions are
admin-managed, so 30s staleness is typically acceptable.

**Automatic invalidation.** `saveView()` and `deleteView()` (§5)
invalidate the affected tenancy's cache entry. Domains that manage
views through the orchestrator get transparent cache consistency. TTL
handles staleness for any mutations through the store directly.

**Entry count.** The cache holds one entry per tenancy ID — typically
O(10-100) in most deployments. Expired entries are replaced on next
access but never proactively evicted. For deployments with dynamic
tenancy creation, keep TTL short to limit memory from unused entries.

**No Quarkus cache dependency.** Plain ConcurrentHashMap + Instant.
platform-view stays CDI-only.

## §7 — Scope-Aware Evaluation

**Problem:** `evaluateMembership()` matches all views against all label
paths with no scope filtering. A subject at `eu/germany` is evaluated
against views scoped to `us/california` — wasted work.

**Change:** Overload on `SubjectViewEvaluator`:

```java
public Map<UUID, String> evaluateMembership(
        Set<String> subjectLabelPaths,
        List<SubjectViewSpec> views,
        Path subjectScope) {
    if (subjectScope == null) {
        return evaluateMembership(subjectLabelPaths, views);
    }
    var filtered = views.stream()
        .filter(v -> v.scope() == null
            || v.scope().equals(subjectScope)
            || v.scope().isAncestorOf(subjectScope))
        .toList();
    return evaluateMembership(subjectLabelPaths, filtered);
}
```

**Scope compatibility:** A view matches a subject's scope if the view
has no scope (global), the view's scope equals the subject's scope, or
the view's scope is an ancestor of the subject's scope.

**Orchestrator overloads:**

```java
public List<SubjectViewEvent> evaluateAndTrack(
        UUID subjectId, String tenancyId,
        Set<String> labelPaths, Path subjectScope) {
    var before = tracker.getLastKnownMembership(subjectId);
    var views = getViews(tenancyId);
    var after = evaluator.evaluateMembership(labelPaths, views, subjectScope);
    var events = evaluator.computeEvents(subjectId, tenancyId, before, after);
    tracker.updateMembership(subjectId, after);
    return events;
}

public Map<UUID, List<SubjectViewEvent>> evaluateAndTrackBatch(
        Map<UUID, Set<String>> subjectLabelPaths, String tenancyId,
        Function<UUID, Path> scopeResolver) {
    var subjectIds = subjectLabelPaths.keySet();
    var allBefore = tracker.getLastKnownMembership(subjectIds);
    var views = getViews(tenancyId);

    Map<UUID, List<SubjectViewEvent>> result = new LinkedHashMap<>();
    subjectLabelPaths.forEach((subjectId, labelPaths) -> {
        var before = allBefore.getOrDefault(subjectId, Map.of());
        Path scope = scopeResolver != null ? scopeResolver.apply(subjectId) : null;
        var after = evaluator.evaluateMembership(labelPaths, views, scope);
        var events = evaluator.computeEvents(subjectId, tenancyId, before, after);
        tracker.updateMembership(subjectId, after);
        result.put(subjectId, events);
    });
    return result;
}
```

Batch variant takes `Function<UUID, Path>` — different subjects may have
different scopes within a batch. Null scopeResolver means no filtering.

**Affected files:**
- `platform-view/.../view/SubjectViewEvaluator.java` (overload)
- `platform-view/.../view/SubjectViewEvaluatorTest.java` (scope tests)
- `platform-view/.../view/SubjectViewOrchestrator.java` (overloads)
- `platform-view/.../view/SubjectViewOrchestratorTest.java` (scope tests)

## Module Impact Summary

| Module | Changes |
|--------|---------|
| `platform-api` | ViewEventType +CHANGED, SubjectViewSpec +additionalConditions, ViewMembershipTracker +bulk default method |
| `platform` | NoOpViewMembershipTracker bulk override |
| `platform-view` | SubjectViewEvaluator diff() → computeEvents() + scope overload, SubjectViewOrchestrator (new) |
| `platform-view-inmem` | InMemoryViewMembershipTracker bulk override, InMemorySubjectViewQuerySupport (new), InMemorySubjectViewStore save() update |
| `platform-view-jpa` | JpaViewMembershipTracker bulk override, SubjectViewEntity +additionalConditions, V5001 migration |
| `ARC42STORIES.MD` | L13 description: ADDED/REMOVED → ADDED/REMOVED/CHANGED lifecycle events |

No cross-repo changes. No new modules. No new dependencies.
