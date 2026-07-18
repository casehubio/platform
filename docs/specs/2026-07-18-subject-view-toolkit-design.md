# Subject View Toolkit — casehub-platform #175

**Date:** 2026-07-18
**Issue:** casehubio/platform#175
**Status:** Design

## Summary

Generic toolkit for creating filtered views of any labeled subject — cases,
WorkItems, devices, or any future entity type. Generalizes the pattern
established by casehub-work-queues (filtered views of WorkItems by label
patterns) into a reusable platform capability.

A "queue" is not a container you put things in. A queue is a **view** defined
by a label pattern that selects subjects based on their metadata. Subjects
appear in views when their labels match. When labels change, lifecycle events
fire (ADDED, REMOVED). Application surfaces — inboxes, triage panels,
workbenches — consume these views and events.

## Design Roots

casehub-work-queues models queues as filtered views over WorkItems:

- `QueueView` defines a queue by `labelPattern` (supports `*`, `**`, exact match)
- WorkItems carry `WorkItemLabel` (Path-based, MANUAL or INFERRED persistence)
- `QueueMembershipContext` diffs before/after membership on each lifecycle event,
  emitting ADDED/REMOVED/CHANGED events via a DB-backed `QueueMembershipTracker`
- `FilterEvaluationObserver` wires lifecycle events → filter evaluation → membership
  diff → queue events

This toolkit extracts the generic pattern. casehub-work-queues migrates to it
separately (future issue).

### Why Not WorkItem?

WorkItem is a rich human task lifecycle entity — 12 statuses, SLA deadlines,
delegation chains, exclusion policies, capability validation, forms, schemas,
M-of-N spawn groups. The view infrastructure is the reusable part. Domains that
need operational lifecycle (claim/release/complete) use WorkItem or build their
own. The toolkit owns which subjects are visible where, not what happens to them.

### Why Not a Container Model?

The original issue described explicit `queueName` fields and `enqueue`/`escalate`
operations. Investigation of casehub-work revealed that setting a label is
functionally equivalent to setting a `queueName` — the item appears in whatever
views match. The label model adds: hierarchy via Path (`iot/**` shows all IoT
subjects), multi-view visibility (a subject can appear in several views
simultaneously), and consistency with the established work-queues pattern.

## Module Structure

| Module | Artifact | Responsibility |
|--------|----------|----------------|
| `platform-api` | `casehub-platform-api` | Pure Java types — pattern matcher, view spec, SPIs, event types. Package: `io.casehub.platform.api.view` |
| `platform-view/` | `casehub-platform-view` | Runtime orchestration — membership evaluation, diffing. No persistence dependency. |
| `platform-view-inmem/` | `casehub-platform-view-inmem` | `@Alternative @Priority(100)` — ConcurrentHashMap implementations. Lightweight production (single-node, startup-configured) and test isolation. |
| `platform-view-jpa/` | `casehub-platform-view-jpa` | `@ApplicationScoped` — JPA implementations + Criteria API query helper. PostgreSQL + Flyway. |
| `platform-view-mongodb/` | `casehub-platform-view-mongodb` | `@Alternative @Priority(1)` — MongoDB implementations. |

### ARC42STORIES Layer & Chapter Assignment

**Layer:** L13: Subject View Adapters

| Module | Layer |
|--------|-------|
| `platform-api` (view types) | L1: API Tier |
| `platform/` (NoOp defaults) | L2: Default Implementations |
| `platform-view/` | L13: Subject View |
| `platform-view-inmem/` | L13: Subject View |
| `platform-view-jpa/` | L13: Subject View |
| `platform-view-mongodb/` | L13: Subject View |

**Chapter:** C22: Subject View Toolkit
**Journey:** J7: Subject Views
**Issues:** casehubio/platform#175

## §1 — platform-api Types

All pure Java. Zero dependencies. Package: `io.casehub.platform.api.view`.

### LabelPatternMatcher

Static utility. Extracted from `LabelVocabularyService.matchesPattern()` in
casehub-work. Three matching modes:

| Pattern | Semantics | SQL translation |
|---------|-----------|----------------|
| `legal` | Exact match | `WHERE path = 'legal'` |
| `legal/*` | One segment below | `WHERE path LIKE 'legal/%' AND path NOT LIKE 'legal/%/%'` |
| `legal/**` | Any depth below | `WHERE path LIKE 'legal/%'` |

```java
public final class LabelPatternMatcher {
    public static boolean matches(String pattern, String path) { ... }
}
```

Semantically identical to `LabelVocabularyService.matchesPattern()`. Same
behaviour, same edge cases. When work-queues migrates, it delegates here.

### SubjectViewSpec

What defines a view:

```java
public record SubjectViewSpec(
    UUID id,
    String name,
    String tenancyId,
    String labelPattern,
    Path scope,
    String sortField,
    String sortDirection,
    Instant createdAt
) {}
```

- `labelPattern` — the label pattern that selects subjects (e.g., `iot/ai-resolution/**`)
- `scope` — visibility scope for the view definition itself. Uses the platform
  `Path` type (`io.casehub.platform.api.path.Path`) for type-safe hierarchy
  operations (`isAncestorOf()`, `parent()`, `depth()`).
- `sortField` — optional (nullable). Entity field name for default ordering of
  view results. Domain query implementations interpret this; the platform does
  not validate it against entity metadata.
- `sortDirection` — optional (nullable). `"ASC"` or `"DESC"`. Defaults to `"ASC"`
  when `sortField` is present but direction is null.
- `createdAt` — set by the persistence layer (`@PrePersist`). Null for unpersisted
  specs. Useful for audit and display ordering of view definitions.

### SubjectViewStore SPI

CRUD for view definitions:

```java
public interface SubjectViewStore {
    SubjectViewSpec save(SubjectViewSpec spec);
    Optional<SubjectViewSpec> findById(UUID id);
    List<SubjectViewSpec> findByTenancy(String tenancyId);
    void delete(UUID id);
}
```

**View deletion and membership state:** Deleting a view via `delete()` does
not trigger REMOVED events for currently-matched subjects. Membership state
self-corrects lazily — the next label change for each subject triggers
`evaluateMembership()`, which excludes the deleted view from the `after` set,
producing a REMOVED event via `diff()`. Domains that need immediate cleanup
on view deletion should trigger a bulk re-evaluation for all subjects in the
view before calling `delete()`.

### ViewMembershipTracker SPI

Persistent before/after state for event diffing. Same role as
`QueueMembershipTracker` in work-queues:

```java
public interface ViewMembershipTracker {
    Map<UUID, String> getLastKnownMembership(UUID subjectId);
    void updateMembership(UUID subjectId, Map<UUID, String> viewIdToName);
    void removeMembership(UUID subjectId);
}
```

### SubjectViewQuery SPI

The listing query — "show me all subjects in this view." Each backend
implements this with native database queries (SQL joins, MongoDB `$elemMatch`,
in-memory iteration). The platform does NOT load subjects into memory and
filter in Java when a database query exists.

```java
public interface SubjectViewQuery<S> {
    List<S> findByView(SubjectViewSpec view);
    List<S> findByView(SubjectViewSpec view, int offset, int limit);
    long countByView(SubjectViewSpec view);
}
```

The unpaginated `findByView()` is for internal use (membership evaluation,
batch processing). The paginated overload is the primary access pattern —
every UI consumer needs it. `countByView()` pairs with the paginated query
for total-count display.

Domain consumers provide entity-specific implementations, using per-backend
query helpers the platform provides.

**CDI type erasure:** Like event types (see below), `SubjectViewQuery<S>` is
a generic interface. CDI erases the type parameter at injection time. If two
domains provide `SubjectViewQuery` implementations in the same deployment,
inject by the concrete type (`CaseViewQueryStore`), not the generic interface.
This is identical to the established CDI event wrapping convention — domains
define concrete types that CDI can distinguish.

### @DefaultBean Implementations (platform/)

ARC42STORIES §4 invariant: every SPI in `platform-api/` gets a `@DefaultBean`
implementation in `platform/`.

- `NoOpSubjectViewStore @DefaultBean @ApplicationScoped` — `save()` returns
  the input unchanged, `findById()` returns empty, `findByTenancy()` returns
  empty list, `delete()` is a no-op.
- `NoOpViewMembershipTracker @DefaultBean @ApplicationScoped` —
  `getLastKnownMembership()` returns empty map, `updateMembership()` and
  `removeMembership()` are no-ops.

Consumers who don't add an adapter module get safe no-op behavior instead of
`UnsatisfiedResolutionException` at boot.

### Event Types

```java
public enum ViewEventType { ADDED, REMOVED }

public record SubjectViewEvent(
    UUID subjectId,
    UUID viewId,
    String viewName,
    ViewEventType type,
    String tenancyId
) {}
```

Domains define concrete CDI event types wrapping `SubjectViewEvent` to avoid
generic type erasure:

```java
// Domain defines — e.g., in casehub-engine
public record CaseViewEvent(SubjectViewEvent event) {}
```

## §2 — Runtime Orchestration (platform-view)

No persistence dependency. Pure Java + CDI.

### SubjectViewEvaluator

Given a subject's label paths and all view specs, computes which views the
subject belongs to. Used in the lifecycle event path — single subject already
in the transaction context:

```java
@ApplicationScoped
public class SubjectViewEvaluator {

    public Map<UUID, String> evaluateMembership(
            Set<String> subjectLabelPaths,
            List<SubjectViewSpec> views) {
        return views.stream()
            .filter(v -> subjectLabelPaths.stream()
                .anyMatch(p -> LabelPatternMatcher.matches(v.labelPattern(), p)))
            .collect(Collectors.toMap(SubjectViewSpec::id, SubjectViewSpec::name));
    }

    public List<SubjectViewEvent> diff(
            UUID subjectId,
            String tenancyId,
            Map<UUID, String> before,
            Map<UUID, String> after) {
        List<SubjectViewEvent> events = new ArrayList<>();

        // REMOVED: in before, not in after
        before.forEach((id, name) -> {
            if (!after.containsKey(id)) {
                events.add(new SubjectViewEvent(subjectId, id, name,
                    ViewEventType.REMOVED, tenancyId));
            }
        });

        // ADDED: not in before, in after
        after.forEach((id, name) -> {
            if (!before.containsKey(id)) {
                events.add(new SubjectViewEvent(subjectId, id, name,
                    ViewEventType.ADDED, tenancyId));
            }
        });

        return events;
    }
}
```

O(views × labels) per subject mutation. View definitions are O(10-100) —
this is not a scalability concern.

### Domain Wiring Pattern

A domain plugs in with one CDI observer:

```java
@ApplicationScoped
public class CaseViewObserver {
    @Inject SubjectViewEvaluator evaluator;
    @Inject SubjectViewStore viewStore;
    @Inject ViewMembershipTracker tracker;
    @Inject Event<CaseViewEvent> eventBus;

    @Transactional
    void onCaseLabelsChanged(@Observes CaseLabelsChangedEvent e) {
        var before = tracker.getLastKnownMembership(e.caseId());
        var views = viewStore.findByTenancy(e.tenancyId());
        var after = evaluator.evaluateMembership(e.labelPaths(), views);
        var events = evaluator.diff(e.caseId(), e.tenancyId(), before, after);
        tracker.updateMembership(e.caseId(), after);
        events.forEach(evt -> eventBus.fire(new CaseViewEvent(evt)));
    }
}
```

~12 lines. The domain provides: the CDI observer method signature (its
lifecycle event type), the concrete CDI event type, and any domain-specific
enrichment.

**Transaction semantics:** `@Observes` (synchronous) ensures the membership
evaluation runs within the originating transaction. The `@Transactional`
annotation on the observer method participates in the existing transaction
context. If the membership update fails, the entire transaction rolls back —
labels don't change and membership state stays consistent. This matches the
existing `FilterEvaluationObserver` pattern in casehub-work-queues.

**Concurrency note:** Two concurrent label changes for the same subject are
serialized by the database transaction — the second writer blocks until the
first commits. This is correct behavior: the second writer reads the
first writer's committed "before" state and produces accurate events.

## §3 — Backend Implementations

### platform-view-inmem (`@Alternative @Priority(100)`)

ConcurrentHashMap implementations. Lightweight production (single-node,
startup-configured views) and test isolation. Same role as
`InMemoryEndpointRegistry` (`@Priority(100)`) and `InMemoryDigestBuffer`
(`@Priority(100)`) — production-volatile tier, not test-only.

- `InMemorySubjectViewStore` — ConcurrentHashMap keyed by view ID
- `InMemoryViewMembershipTracker` — ConcurrentHashMap keyed by subject ID

For `SubjectViewQuery<S>`, the in-memory backend iterates subjects and matches
with `LabelPatternMatcher`. This is in-memory filtering — correct here since
there is no database. Domains extend with their subject collection source.

Do NOT combine with platform-view-jpa or platform-view-mongodb in the same
scope.

### platform-view-jpa (`@ApplicationScoped`)

JPA implementations. PostgreSQL. Flyway migrations at
`classpath:db/view/migration`.

- `JpaSubjectViewStore` — Panache entity for view definitions
- `JpaViewMembershipTracker` — Panache entity for membership state

**JpaLabelPatternQuerySupport** — the Criteria API helper that makes domain
consumers thin:

```java
public abstract class JpaLabelPatternQuerySupport<E, L> {

    private final Class<E> entityClass;
    private final ListAttribute<E, L> labelsAttr;
    private final SingularAttribute<L, String> pathAttr;
    private final SingularAttribute<E, String> tenancyAttr;

    protected JpaLabelPatternQuerySupport(
            Class<E> entityClass,
            ListAttribute<E, L> labelsAttr,
            SingularAttribute<L, String> pathAttr,
            SingularAttribute<E, String> tenancyAttr) {
        this.entityClass = entityClass;
        this.labelsAttr = labelsAttr;
        this.pathAttr = pathAttr;
        this.tenancyAttr = tenancyAttr;
    }

    protected List<E> findByView(EntityManager em, SubjectViewSpec view) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<E> cq = cb.createQuery(entityClass);
        Root<E> root = cq.from(entityClass);
        Join<E, L> labelJoin = root.join(labelsAttr);

        cq.where(cb.and(
            LabelPatternPredicates.toPredicate(cb, labelJoin.get(pathAttr),
                view.labelPattern()),
            cb.equal(root.get(tenancyAttr), view.tenancyId())
        )).distinct(true);

        if (view.sortField() != null) {
            cq.orderBy("DESC".equalsIgnoreCase(view.sortDirection())
                ? cb.desc(root.get(view.sortField()))
                : cb.asc(root.get(view.sortField())));
        }

        return em.createQuery(cq).getResultList();
    }

    protected List<E> findByView(EntityManager em, SubjectViewSpec view,
            int offset, int limit) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<E> cq = cb.createQuery(entityClass);
        Root<E> root = cq.from(entityClass);
        Join<E, L> labelJoin = root.join(labelsAttr);

        cq.where(cb.and(
            LabelPatternPredicates.toPredicate(cb, labelJoin.get(pathAttr),
                view.labelPattern()),
            cb.equal(root.get(tenancyAttr), view.tenancyId())
        )).distinct(true);

        if (view.sortField() != null) {
            cq.orderBy("DESC".equalsIgnoreCase(view.sortDirection())
                ? cb.desc(root.get(view.sortField()))
                : cb.asc(root.get(view.sortField())));
        } else {
            cq.orderBy(cb.asc(root.get("id")));
        }

        TypedQuery<E> query = em.createQuery(cq);
        query.setFirstResult(offset);
        query.setMaxResults(limit);
        return query.getResultList();
    }

    protected long countByView(EntityManager em, SubjectViewSpec view) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<E> root = cq.from(entityClass);
        Join<E, L> labelJoin = root.join(labelsAttr);

        cq.select(cb.countDistinct(root));
        cq.where(cb.and(
            LabelPatternPredicates.toPredicate(cb, labelJoin.get(pathAttr),
                view.labelPattern()),
            cb.equal(root.get(tenancyAttr), view.tenancyId())
        ));

        return em.createQuery(cq).getSingleResult();
    }
}
```

**LabelPatternPredicates** — translates `LabelPatternMatcher` syntax to JPA
`Predicate`:

```java
public final class LabelPatternPredicates {

    private static String escapeLikePrefix(String prefix) {
        return prefix.replace("\\", "\\\\")
                     .replace("%", "\\%")
                     .replace("_", "\\_");
    }

    public static Predicate toPredicate(
            CriteriaBuilder cb, Path<String> pathExpr, String pattern) {
        if (pattern.endsWith("/**")) {
            String prefix = escapeLikePrefix(
                pattern.substring(0, pattern.length() - 3));
            return cb.like(pathExpr, prefix + "/%", '\\');
        }
        if (pattern.endsWith("/*")) {
            String prefix = escapeLikePrefix(
                pattern.substring(0, pattern.length() - 2));
            return cb.and(
                cb.like(pathExpr, prefix + "/%", '\\'),
                cb.notLike(pathExpr, prefix + "/%/%", '\\')
            );
        }
        return cb.equal(pathExpr, pattern);
    }
}
```

**Domain consumer — thin layer:**

```java
@ApplicationScoped
public class CaseViewQueryStore
        extends JpaLabelPatternQuerySupport<CaseQueueEntry, CaseLabel>
        implements SubjectViewQuery<CaseQueueEntry> {

    @Inject EntityManager em;

    public CaseViewQueryStore() {
        super(CaseQueueEntry.class,
              CaseQueueEntry_.labels,
              CaseLabel_.path,
              CaseQueueEntry_.tenancyId);
    }

    @Override
    public List<CaseQueueEntry> findByView(SubjectViewSpec view) {
        return findByView(em, view);
    }

    @Override
    public List<CaseQueueEntry> findByView(SubjectViewSpec view,
            int offset, int limit) {
        return findByView(em, view, offset, limit);
    }

    @Override
    public long countByView(SubjectViewSpec view) {
        return countByView(em, view);
    }
}
```

4 metamodel attributes. Three delegation methods. SQL join, pattern
translation, and tenant filtering handled by the platform.

### platform-view-mongodb (`@Alternative @Priority(1)`)

MongoDB implementations. Labels stored as embedded array. Pattern matching via
`$elemMatch` with `$regex`:

- `/**` → `{ "labels.path": { $regex: "^<escaped-prefix>/" } }`
- `/*` → `{ "labels.path": { $regex: "^<escaped-prefix>/[^/]+$" } }`
- exact → `{ "labels.path": "pattern" }`

The prefix portion is regex-escaped before interpolation using
`java.util.regex.Pattern.quote(prefix)`. Without escaping, label patterns
containing regex metacharacters (`.`, `+`, `(`, `*`, etc.) produce
malformed or overly-broad matches — e.g., `legal.special/**` would match
`legalXspecial/foo` because `.` matches any character.

Domain consumer provides collection name and entity class.

## §4 — Relationship to Existing Systems

### casehub-work-queues

Continues unchanged. The compatibility guarantee: platform-view's types are
designed so work-queues can migrate later without API breaks:

| platform-view | work-queues equivalent |
|----------------|----------------------|
| `SubjectViewSpec` | `QueueView` |
| `ViewEventType.ADDED/REMOVED` | `QueueEventType` |
| `LabelPatternMatcher.matches()` | `LabelVocabularyService.matchesPattern()` |
| `ViewMembershipTracker` | `QueueMembershipTracker` |
| `SubjectViewEvaluator.diff()` | `QueueMembershipContext.resolve()` |

Migration is a separate issue against casehub-work. When it happens,
work-queues adopts platform-view's SPIs internally. The INFERRED label engine
and JEXL condition evaluation remain work-queues-specific — those are WorkItem
concerns, not generic view concerns.

### casehub-engine (first consumer)

The engine adds labels to case entities, defines case views via
`SubjectViewStore`, wires a `CaseViewObserver` to case lifecycle events, and
implements `SubjectViewQuery<CaseQueueEntry>` using
`JpaLabelPatternQuerySupport`. Cases appear in views based on their labels.
When a case enters the `iot/ai-resolution/**` view, a `CaseViewEvent(ADDED)`
fires and downstream observers act on it.

### IoT CBR spec coordination

The IoT CBR spec (`2026-07-14-cbr-situation-resolution-design.md`) §4-5
describes a container model (`QueueSubject`, `QueueEntry`,
`AbstractQueueEntity`, `AbstractQueueService`) for the same platform#175 slot.
That design was the original plan; this spec replaces it with the view model.

**The IoT CBR spec §4-5 must be updated** to use the view toolkit for case
routing (which cases appear in which queue). The view toolkit handles
visibility — when a case's labels match `iot/ai-resolution/**`, it appears in
that view and a `CaseViewEvent(ADDED)` fires.

**Operational lifecycle** (claim, release, complete, escalate) is a domain
concern. The IoT domain layers its own operational semantics on top of view
events:
- View ADDED event → domain creates a `CaseQueueEntry` with status/assignment
  fields (domain entity, not a platform type)
- Claim, release, complete → domain-specific service methods on the
  domain's queue entry entity

This separation is correct: the platform owns *visibility* (which subjects
appear in which views). Domains own *operations* (what happens to subjects
in a view). A GitHub issue should be filed against `casehubio/iot` to update
§4-5 of the CBR spec to use the view model.

### Original issue #175

The issue described `AbstractQueueEntity`, `AbstractQueueService`,
`QueueSubject` with explicit `queueName` fields and `enqueue`/`escalate`
operations. This design replaces that with the view model — labels instead of
queue names, views instead of containers. The lifecycle events on view
entry/exit provide the same downstream triggering power.

A new GitHub issue should be filed specifically for the subject view toolkit
as designed here. Issue #175 should be updated to note the design pivot and
either closed (with a "design replaced" note referencing the new issue) or
retained for container operations if the IoT domain still needs them as a
separate platform capability.

## §5 — Out of Scope

Each deferred item has a GitHub issue for tracking and discoverability.

| Concern | Reason | Where it lives | Issue |
|---------|--------|----------------|-------|
| Claim/release/complete | Domain-specific operational lifecycle. Use WorkItem when needed. | casehub-work or domain | TBD |
| INFERRED labels | WorkItem-specific auto-labeling. Generic subjects don't need this. | casehub-work-queues | TBD |
| JEXL/MVEL condition evaluation | Requires entity-specific field access. Domains add on top. | Domain concern | TBD |
| View REST API | Domains expose endpoints shaped for their application surfaces. | Domain concern | TBD |
| SSE/WebSocket push | Application surface concern. Domains observe events and push. | Domain concern (Pages, blocks-ui) | TBD |
| Work-queues migration | Compatibility designed in, refactoring is separate. | Future issue, casehub-work | TBD |
| Redis backend | No current consumer. Sorted set `ZRANGEBYLEX` viable if needed. | Future | TBD |
| Snapshot/trend infrastructure | work-queues' `QueueSnapshot` + `QueueSnapshotJob`. Useful but not core. | Future enhancement | TBD |

**Note:** GitHub issues to be filed at spec approval — after the design is
finalized, before implementation begins. Each deferred item will receive its
own issue in the appropriate repository (platform, work, or domain repo).

## References

- casehub-work-queues: `QueueView`, `QueueMembershipContext`,
  `FilterEvaluationObserver`, `LabelVocabularyService`
- IoT CBR spec: `casehubio/iot — docs/superpowers/specs/2026-07-14-cbr-situation-resolution-design.md` §4
- Platform docs: `casehubio/parent — docs/platform/capability-ownership.md`,
  `boundary-rules.md`, `overview.md`
- Hibernate Data Repositories (Jakarta Data): compile-time repository generation
  — informed the APT evaluation but JPA Criteria API + Metamodel chosen instead
