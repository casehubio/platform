# Cascade Enforcement in RegistryService

**Issue:** casehubio/platform#571
**Branch:** issue-571-cascade-enforcement
**Date:** 2026-10-11

## Problem

`CascadeRule` and `CascadeAction` types exist in `platform-api`. Both `InMemoryRegistryService` and `JpaRegistryService` store cascade rules via `registerCascadeRule()` but do not enforce them during `deregister()`. Zero cascade behavior tests exist.

## Solution Overview

Wire cascade enforcement into `deregister()` and `HeartbeatScheduler`. When an entry is deregistered (or expires via TTL), query its outbound relationships, match against registered cascade rules, and apply the appropriate action to each target entry. Return a `CascadeResult` describing what happened.

## SPI Changes (platform-api)

### CascadeRule — add `onSourceExpired` field

```java
public record CascadeRule(
    String relationshipType,
    CascadeAction onSourceDeregister,
    CascadeAction onSourceExpired,   // nullable — defaults to onSourceDeregister
    Duration cascadeGracePeriod      // nullable — null means cascade fires immediately on DOWN;
                                     // non-null means entry must remain DOWN for this duration
                                     // before TTL-triggered cascade fires (explicit deregister
                                     // always cascades immediately regardless)
) {
    public CascadeRule(String relationshipType, CascadeAction onSourceDeregister) {
        this(relationshipType, onSourceDeregister, null, null);
    }

    public CascadeAction effectiveOnSourceExpired() {
        return onSourceExpired != null ? onSourceExpired : onSourceDeregister;
    }
}
```

**Rationale (D1):** Crash-without-deregister is the common failure mode. TTL expiry should trigger cascade with the same default behavior as explicit deregister, but allow per-relationship-type differentiation when needed.

### RegistryEntry — add `orphanedAt` field

```java
public record RegistryEntry(
    String id, String type, String namespace, String tenancyId,
    Map<String, String> metadata, Instant registeredAt,
    Instant lastHeartbeat, Duration ttl, HealthStatus health,
    Instant orphanedAt  // nullable — null means not orphaned
) {
    public boolean isOrphaned() { return orphanedAt != null; }

    public RegistryEntry withOrphaned(Instant now) {
        return new RegistryEntry(id, type, namespace, tenancyId,
                metadata, registeredAt, lastHeartbeat, ttl, health, now);
    }

    // Existing withers updated to carry orphanedAt through
    public RegistryEntry withHealth(HealthStatus newHealth) {
        return new RegistryEntry(id, type, namespace, tenancyId,
                metadata, registeredAt, lastHeartbeat, ttl, newHealth, orphanedAt);
    }

    public RegistryEntry withHeartbeat(Instant now) {
        return new RegistryEntry(id, type, namespace, tenancyId,
                metadata, registeredAt, now, ttl, HealthStatus.HEALTHY, orphanedAt);
    }
}
```

**Rationale (D2):** Health and topology are orthogonal dimensions. Every mature registry (Kubernetes, Consul, ZooKeeper) keeps them separate. `orphanedAt` gives boolean status + cleanup timing in one field, following the existing `lastHeartbeat` nullable Instant pattern.

### RegistryEvent — new event kinds

```java
public enum EventKind {
    REGISTERED, DEREGISTERED, HEALTH_CHANGED,
    HEARTBEAT_EXPIRED, LINKED, UNLINKED,
    ORPHANED,          // target was marked orphaned by MARK_ORPHANED cascade
    CASCADE_NOTIFIED   // NOTIFY cascade fired (informational — no state change)
}
```

Factory methods:
```java
public static RegistryEvent orphaned(RegistryEntry entry) { ... }
public static RegistryEvent cascadeNotified(RegistryEntry entry, Relationship rel) { ... }
```

**Rationale (D6):** Cascade-triggered deregistrations reuse `DEREGISTERED` (a deregistration is a deregistration). Orphaning needs a new kind because it's a topology change, not a health change.

### RegistryService — return type change

```java
public interface RegistryService {
    // Changed: void → CascadeResult
    CascadeResult deregister(String id);

    // All other methods unchanged
    // ...
}
```

### New types

```java
public record CascadeResult(
    RegistryEntry deregistered,              // the entry that was explicitly deregistered (null if id not found)
    List<CascadeEffect> cascadeEffects       // what happened to targets
) {
    public static CascadeResult empty() {
        return new CascadeResult(null, List.of());
    }

    public static CascadeResult simple(RegistryEntry entry) {
        return new CascadeResult(entry, List.of());
    }

    public boolean hasFailures() {
        return cascadeEffects.stream().anyMatch(e -> e.failure() != null);
    }
}

public record CascadeEffect(
    String targetId,
    CascadeAction action,
    Relationship relationship,
    RegistryEntry targetEntry,    // state of target after action (null if DEREGISTER succeeded)
    String failure                // null = success; non-null = error message
) {}
```

## Implementation — Cascade Engine

Cascade logic is extracted into a reusable `CascadeEngine` class (framework-neutral, pure Java) shared by both InMemory and JPA implementations. The engine takes callbacks for the actual mutations (deregister, mark orphaned, resolve, etc.) so it remains decoupled from storage.

```java
public class CascadeEngine {

    @FunctionalInterface
    public interface EntryResolver {
        Optional<RegistryEntry> resolve(String id);
    }

    @FunctionalInterface
    public interface RelationshipQuery {
        List<Relationship> outboundFrom(String sourceId);
    }

    public interface EntryMutator {
        void deregister(String id);
        void markOrphaned(String id, Instant now);
    }

    public List<CascadeEffect> applyCascade(
            String sourceId,
            List<CascadeRule> rules,
            Function<CascadeRule, CascadeAction> actionSelector,  // extracts which action to use
            EntryResolver resolver,
            RelationshipQuery relQuery,
            EntryMutator mutator,
            Consumer<RegistryEvent> eventSink,
            Set<String> visited            // cycle detection
    ) { ... }
}
```

### Cascade Algorithm

```
applyCascade(sourceId, rules, actionSelector, resolver, relQuery, mutator, eventSink, visited):
  1. Add sourceId to visited (cycle detection)
  2. Query outbound relationships from sourceId
  3. For each relationship:
     a. Find matching CascadeRule by relationship.type()
     b. If no rule matches → skip (CascadeAction.NONE equivalent)
     c. If target is in visited → skip (cycle detected)
     d. Resolve target entry
     e. Apply action:
        - DEREGISTER: mutator.deregister(targetId), fire DEREGISTERED event,
          recurse: applyCascade(targetId, ...)
        - MARK_ORPHANED: mutator.markOrphaned(targetId, now), fire ORPHANED event
        - NOTIFY: fire CASCADE_NOTIFIED event (no state change)
        - NONE: skip
     f. Catch exceptions per-target → record as CascadeEffect with failure message
  4. Return List<CascadeEffect>
```

**Cycle detection:** The `visited` set prevents infinite recursion when A→B→A.

**Depth limit:** `maxCascadeDepth` (default 10) stops recursion and logs a warning when exceeded. `visited.size()` tracks current depth. Prevents pathological cascade chains from holding transactions open indefinitely.

**Partial failure (D3):** Each target is cascaded independently. Failures on one target don't prevent cascading to others. All results (success and failure) are collected into `CascadeEffect` records.

**JPA transactional coherence:** In `JpaRegistryService`, `deregister()` runs inside a `@Transactional` boundary. Synchronous cascade inside the same transaction means JPA cascade is effectively **atomic** — if any cascade target fails with an unchecked exception, the entire transaction rolls back. This is different from `InMemoryRegistryService` where each `ConcurrentHashMap.remove()` is independent (genuinely best-effort). This divergence is intentional: JPA's atomic semantics are safer for persistent state, while InMemory's best-effort is acceptable for volatile/testing use.

### InMemoryRegistryService Changes

```java
@Override
public CascadeResult deregister(String id) {
    var removed = entries.remove(id);
    if (removed == null) return CascadeResult.empty();

    // Apply cascade BEFORE removing relationships
    var effects = cascadeEngine.applyCascade(
        id, List.copyOf(cascadeRules.values()),
        CascadeRule::onSourceDeregister,  // use deregister action
        this::resolve, this::outboundRelationships,
        new MutatorImpl(), cdiEventSink,
        new HashSet<>(Set.of(id))  // source already visited
    );

    // Remove source's relationships
    relationships.removeIf(r -> r.sourceId().equals(id) || r.targetId().equals(id));

    var event = RegistryEvent.deregistered(removed);
    cdiEventSink.accept(event);
    notifyWatchers(event);

    return new CascadeResult(removed, effects);
}
```

### HeartbeatScheduler Changes

Two responsibilities: (1) mark entries DOWN on TTL expiry and fire `HEARTBEAT_EXPIRED` event, (2) trigger cascade for entries that have been DOWN beyond their grace period.

The existing `HEARTBEAT_EXPIRED` event kind (dormant in Stage 1) is now fired on TTL expiry. `HEALTH_CHANGED` is fired for other health transitions. This resolves the design drift where HeartbeatScheduler was firing `HEALTH_CHANGED` for TTL expiry.

**Grace period:** Cascade does not fire immediately on DOWN transition. The `cascadeGracePeriod` on each `CascadeRule` determines how long an entry must remain DOWN before cascade fires. If no grace period is configured (null), cascade fires on the first tick after DOWN. This prevents cascading on transient network partitions (Kubernetes uses `tolerationSeconds`, Consul uses `deregister_critical_service_after`).

**Transition tracking:** HeartbeatScheduler must detect the DOWN transition (not just check "is this entry past TTL?") to avoid firing cascade on every tick. The entry's health state provides this: cascade only considers entries with `health == DOWN`.

```java
public void checkHeartbeats() {
    var now = Instant.now();
    var rules = registry.cascadeRules();

    for (var entry : registry.allEntries()) {
        if (entry.health() == HealthStatus.DOWN) {
            // Already DOWN — check if grace period elapsed for cascade
            if (!rules.isEmpty()) {
                applyCascadeIfGraceElapsed(entry, rules, now);
            }
            continue;
        }
        var lastSeen = entry.lastHeartbeat() != null ? entry.lastHeartbeat() : entry.registeredAt();
        if (now.isAfter(lastSeen.plus(entry.ttl()))) {
            var downEntry = entry.withHealth(HealthStatus.DOWN);
            registry.updateEntry(downEntry);
            // Fire HEARTBEAT_EXPIRED (not HEALTH_CHANGED) for TTL expiry
            var event = RegistryEvent.heartbeatExpired(downEntry);
            eventSink.accept(event);
            // Cascade with zero grace period fires immediately
            if (!rules.isEmpty()) {
                applyCascadeIfGraceElapsed(downEntry, rules, now);
            }
        }
    }
}

private void applyCascadeIfGraceElapsed(RegistryEntry entry, List<CascadeRule> rules, Instant now) {
    // Entry must have been DOWN for at least the rule's grace period
    // Use lastHeartbeat (or registeredAt) + ttl as the DOWN-since timestamp
    var downSince = (entry.lastHeartbeat() != null ? entry.lastHeartbeat() : entry.registeredAt())
                    .plus(entry.ttl());
    for (var rule : rules) {
        if (rule.cascadeGracePeriod() != null && now.isBefore(downSince.plus(rule.cascadeGracePeriod()))) {
            continue;  // grace period not yet elapsed for this rule
        }
        // Apply cascade for this rule's relationship type
        cascadeEngine.applyCascadeForRule(
            entry.id(), rule, CascadeRule::effectiveOnSourceExpired, ...);
    }
}
```

### JpaRegistryService Changes

Same cascade logic via `CascadeEngine`, with JPA-specific mutator implementations that use `EntityManager` operations. The `CascadeRuleEntity` gains an `onSourceExpired` column (nullable). Flyway migration V2 adds the column to `cascade_rule` table and adds `orphaned_at` column to `registry_entry` table.

### JPA Migration (V2)

```sql
ALTER TABLE registry_entry ADD COLUMN orphaned_at TIMESTAMP;
ALTER TABLE cascade_rule ADD COLUMN on_source_expired VARCHAR(20);
ALTER TABLE cascade_rule ADD COLUMN cascade_grace_period_seconds BIGINT;
```

## Design Constraints

### Cascade rules are global per relationship type

`CascadeRule` is keyed by `relationshipType` — one rule per relationship type across the entire registry. You cannot have different cascade behavior for two relationships of the same type (e.g., `app-1 --owns--> pool-1` cannot cascade differently from `app-2 --owns--> pool-2`).

**Workaround:** Use specific relationship types when different cascade behaviors are needed: `"owns-pool"` vs `"owns-channel"` instead of a generic `"owns"`. This means cascade rules are coupled to relationship naming conventions — consumers should plan their relationship type vocabulary accordingly.

## Module Impact

| Module | Changes |
|--------|---------|
| `platform-api` | CascadeRule (new field), RegistryEntry (new field + withers), RegistryEvent (2 new EventKinds), RegistryService (deregister return type), CascadeResult + CascadeEffect (new types) |
| `platform-core` | NoOpRegistryService.deregister() returns CascadeResult.empty() |
| `registry-inmem-core` | InMemoryRegistryService uses CascadeEngine, HeartbeatScheduler triggers cascade |
| `registry-jpa-common` | JpaRegistryService uses CascadeEngine, entities updated, Flyway V2 |
| `registry-jpa` | RegistryHeartbeatScheduler triggers cascade |
| `registry-spring-jpa` | RegistryHeartbeatSchedulerConfig triggers cascade |

## Consumer Impact

Claudony's `PoolMeshRegistrar` explicitly manages lifecycle via `unlink()` — it does not currently register cascade rules. No existing consumer will be affected by cascade enforcement because no cascade rules are registered yet. The `deregister()` return type change from `void` to `CascadeResult` requires a recompile of direct callers (claudony, etc.) but existing code that calls `deregister()` as a statement (ignoring the return value) will work without changes after recompile.

## Testing Strategy

Tests belong in `registry-inmem-core` (unit tests on InMemoryRegistryService + CascadeEngine):

1. **Simple cascade:** A→B with DEREGISTER rule. Deregister A → B is deregistered.
2. **MARK_ORPHANED:** A→B with MARK_ORPHANED rule. Deregister A → B has orphanedAt set.
3. **NOTIFY:** A→B with NOTIFY rule. Deregister A → B unchanged, CASCADE_NOTIFIED event fired.
4. **NONE:** A→B with NONE rule. Deregister A → B unchanged, no event.
5. **Recursive cascade:** A→B→C with DEREGISTER rules. Deregister A → B and C deregistered.
6. **Cycle detection:** A→B→A with DEREGISTER rules. Deregister A → B deregistered, no infinite loop.
7. **Partial failure:** A→B and A→C where C fails. B is still deregistered, CascadeResult reports failure on C.
8. **No matching rule:** A→B with relationship type "owns" but cascade rule for "contains". No cascade applied.
9. **TTL cascade:** Entry expires via HeartbeatScheduler, cascade fires using `effectiveOnSourceExpired()`.
10. **Different actions for deregister vs expiry:** CascadeRule with DEREGISTER on deregister, NOTIFY on expiry. Verify correct action used per trigger.
11. **CascadeResult structure:** Verify deregistered entry, effects list, hasFailures().
12. **Events:** Verify correct EventKinds fired for each cascade action.
13. **No cascade rules registered:** Deregister works as before, returns simple CascadeResult.
14. **Grace period:** Entry expires (DOWN), cascade rule has 30s grace. Next tick within grace → no cascade. Tick after grace → cascade fires.
15. **Depth limit:** Chain of depth 15 with maxCascadeDepth=10. Cascade stops at depth 10 and logs warning.
16. **HEARTBEAT_EXPIRED event:** TTL expiry fires HEARTBEAT_EXPIRED (not HEALTH_CHANGED).

## References

- [Kubernetes OwnerReferences](https://kubernetes.io/docs/concepts/overview/working-with-objects/owners-dependents/) — separation of health and topology
- [Kubernetes Garbage Collection](https://kubernetes.io/docs/concepts/architecture/garbage-collection/) — cascading deletion architecture
- [Consul Health Checks](https://developer.hashicorp.com/consul/docs/register/health-check/vm) — three-state health model
- [Health Checks in Distributed Systems: Beyond Liveness](https://geekworkbench.com/blog/technical/health-checks-distributed-systems/) — multi-dimensional health
- [ZooKeeper Programmer's Guide](https://zookeeper.apache.org/doc/current/zookeeperProgrammers.html) — ephemeral node lifecycle
- `platform-api/RegistryService.java` — existing SPI
- `registry-inmem-core/InMemoryRegistryService.java` — current deregister implementation
- `registry-inmem-core/HeartbeatScheduler.java` — current TTL expiry logic
- `registry-jpa-common/JpaRegistryService.java` — JPA implementation
- `claudony/PoolMeshRegistrar.java` — primary consumer
- `specs/issue-571-cascade-enforcement/decisions.md` — D1-D6 decisions
- `specs/issue-571-cascade-enforcement/explorations/D2-exploration.md` — orphan representation deep analysis
