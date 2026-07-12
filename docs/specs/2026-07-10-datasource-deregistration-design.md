# DataSource Deregistration Lifecycle — Design Spec

**Issue:** casehubio/platform#138
**Date:** 2026-07-10
**Status:** approved

---

## Problem

The DataSource system is add-only. Three gaps:

1. **No CDI event for removal** — consumers cannot react when a DataSource is deregistered.
2. **Router accumulates stale routes** — `DataSourceRouter.wiredDataSources` grows forever; deregistered DataSources continue matching CloudEvents.
3. **Register replaces silently** — `register()` creates a new `AlphaDataSource` on every call, orphaning active subscribers on the previous instance.

## Design

### Principle: Rete-style self-pruning

The alpha network already self-prunes: `TypeNode` removes empty `FilterNode`s, `AlphaDataSource` removes empty `TypeNode`s. This design extends that pattern to the registry level via reference counting. Deregistration marks a DataSource for removal; actual cleanup happens when the share count (active subscriber count) reaches zero.

### 1. SPI changes (platform-api)

**`DataSourceRegistry.register()`** — behavioral contract change from upsert to idempotent. If `(path, tenancyId)` already exists **and is not pending removal**, return the existing `DataSource`. No replacement, no new instance. If the existing DataSource is marked for removal (deregistration in progress), treat as a new registration: create a new `AlphaDataSource`, replace the map entries, and fire `DataSourceRegistered`. Method signature unchanged.

This is a breaking contract change — not a javadoc update. The current contract is upsert: re-registering the same key replaces the descriptor and returns a new instance. The new contract is idempotent: re-registering returns the existing instance (first descriptor wins). Callers that relied on upsert to update a DataSource's descriptor will now get back the original instance. Descriptor update requires explicit deregister + register (#172).

**`DataSourceRegistry.deregister()`** — already exists. Tighten contract: fires `DataSourceDeregistered` async, marks the DataSource for removal. Actual map cleanup happens when share count reaches zero (immediately if already zero). No-op if not found (unchanged).

The SPI javadoc must be updated to reflect the asynchronous nature of cleanup effects. The current javadoc says "Deregistering stops further deliveries" and "`isActive()` becomes `false`" — both implying synchronous, immediate effects. The updated javadoc must use eventual language: "Deregistering *eventually* stops further deliveries" and "`isActive()` *eventually* becomes `false`." Between `deregister()` returning and CDI observers completing, deliveries continue and `isActive()` remains `true`.

**`DataSourceDeregistered`** — new CDI event record. Carries both the descriptor and the `DataSource` instance being deregistered:

```java
public record DataSourceDeregistered(DataSourceDescriptor descriptor, DataSource<?> dataSource) {
    public DataSourceDeregistered {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(dataSource, "dataSource");
    }
}
```

The `dataSource` field enables identity-based comparison in CDI observers (e.g., `DataSourceRouter`) — necessary because CDI `@ObservesAsync` does not guarantee event ordering. When `deregister() + register()` fires both `DataSourceDeregistered` and `DataSourceRegistered` for the same `(path, tenancyId)`, observers may process them in either order. The DataSource instance lets the router determine whether a wired entry belongs to the deregistered DataSource or a replacement (see §4).

Same obligation pattern: non-no-op implementations must fire it; no-op `@DefaultBean` must not. The `DataSourceRegistry` class-level javadoc must add a `DataSourceDeregistered` obligation section matching the existing `DataSourceRegistered` one — documenting that non-no-op implementations fire it after every successful `deregister()` call, and the no-op `@DefaultBean` must NOT fire it.

### 2. AlphaDataSource reference counting and pruning

**Subscriber count** — `AtomicInteger` on `AlphaDataSource`. Increments on every `subscribe()` call (before wiring into the network), decrements on every `unsubscribe()` (after unwiring from the network).

**Removal marking** — package-private `markForRemoval(Runnable onEmpty)`. Stores the callback and checks: if share count is already zero, fires immediately.

**Removal query** — package-private `isPendingRemoval()`. Returns `true` after `markForRemoval()` has been called. Used by `InMemoryDataSourceRegistry.register()` to detect re-registration during drain.

**Pruning trigger** — in the `Handle.unsubscribe()` path, after decrementing: if count reaches zero AND `pendingRemoval` is true, fire the `onEmpty` callback (registry removes its map entries).

**Subscribe during drain** — subscriptions after `markForRemoval()` are accepted and count toward the share count. The DataSource is only cleaned up when all subscribers — including any added during drain — have unsubscribed. This is consistent with reference counting semantics: every subscriber counts, regardless of when they subscribed. The `DataSource` SPI does not expose lifecycle state; rejecting subscriptions would require the interface to know about removal marking, which crosses concerns.

**Thread safety** — three operations interact with the lifecycle state:

1. `markForRemoval(Runnable onEmpty)`: `synchronized (this)` — sets `pendingRemoval = true`, stores callback, reads `shareCount`. If count is zero, fires callback. All three steps are atomic within the monitor.
2. `Handle.unsubscribe()`: after unwiring from the network, enters `synchronized (alphaDataSource)` — decrements `shareCount`, checks `pendingRemoval`. If count reaches zero and `pendingRemoval` is true, fires callback. Same monitor as `markForRemoval`.
3. `subscribe()` (all overloads): `shareCount.incrementAndGet()` — NO synchronization needed. `AtomicInteger` provides thread-safe increment. The worst case (subscribe races with `markForRemoval`) is safe: `markForRemoval` sees the incremented count and defers cleanup; the new subscriber will eventually unsubscribe and trigger cleanup then.

Existing `CopyOnWriteArrayList` and `ConcurrentHashMap` in network nodes remain unchanged.

**No new public API on `DataSource`** — `markForRemoval` and `isPendingRemoval` are package-private. Lifecycle management is an implementation detail of the in-memory backend, not exposed on the SPI.

### 3. InMemoryDataSourceRegistry changes

**`register()` — idempotent with drain-awareness:** uses `compute()` (not `computeIfAbsent()`) on both maps. If the key exists and the existing `AlphaDataSource` is not pending removal, returns the existing instance (idempotent). If the key exists but the existing `AlphaDataSource` is pending removal, creates a new `AlphaDataSource` and replaces the map entry. Only fires `DataSourceRegistered` for genuinely new registrations (including replacements of draining instances).

**`deregister()` — lifecycle-aware:**

1. Look up `AlphaDataSource` by key
2. If not found → no-op
3. If found → call `markForRemoval(callback)` where callback gates map cleanup: first attempt `sources.remove(key, oldSource)` (conditional removal using identity equality on `AlphaDataSource`). Only if that succeeds, remove `descriptors.remove(key)`. The identity-based guard on `sources` is authoritative — if it fails (a replacement `AlphaDataSource` now occupies the entry), the `descriptors` entry must not be touched either. Without this gating, `descriptors.remove(key, oldDescriptor)` would use record structural equality and incorrectly match a replacement entry with identical descriptor fields.
4. Fire `DataSourceDeregistered(descriptor, alphaDataSource)` async — both the descriptor and the DataSource instance are included (null-guard for unit test path where the CDI event handle is null)
5. If share count is already zero, callback fires immediately and maps are cleaned. If not, maps stay populated until last subscriber leaves.

**Descriptor stays resolvable during drain** — between `deregister()` and actual map removal, `resolve()`/`resolveSource()` still return the DataSource. Active subscribers are still receiving events. Once callback fires and maps clear, resolution returns empty.

### 4. DataSourceRouter cleanup

CDI `@ObservesAsync` does not guarantee event ordering. When `deregister() + register()` fires both `DataSourceDeregistered` and `DataSourceRegistered` for the same `(path, tenancyId)`, the router may process them in either order. Both handlers must be convergent — producing the correct wired state regardless of processing order.

**`wireRoute` (DataSourceRegistered handler) — convergent:**

1. Resolve `DataSource` from registry by `(path, tenancyId)` from the event's descriptor
2. If empty → DataSource was deregistered between event fire and processing; skip
3. Find existing `WiredDataSource` entry for the same `(path, tenancyId)`
4. If exists AND wired entry's `dataSource` is the same instance as the resolved DataSource → skip (genuine duplicate event)
5. If exists AND different instance → replace the entry (drain-aware re-registration created a new DataSource)
6. If not exists → add new entry

This replaces the current key-only idempotency guard (which would skip a re-registration because the old entry's key matches).

**`unwireRoute` (DataSourceDeregistered handler) — identity-aware:**

1. Find `WiredDataSource` entry for `(path, tenancyId)`
2. If not found → no-op
3. If wired entry's `dataSource` is the same instance as `event.dataSource()` → remove (this IS the DataSource being deregistered)
4. If different instance → skip (the wired entry was already replaced for a new DataSource by `wireRoute`)

This identity comparison is why `DataSourceDeregistered` carries the `DataSource<?>` instance (§1).

**Pre-startup queue** — same pattern as `DataSourceRegistered`: if not yet started, queue and replay at startup.

### 5. SubscriptionEngine reaction

**Observe `DataSourceDeregistered`** — new `@ObservesAsync` method:

1. Check if deregistered path matches `NOTIFICATION_DATASOURCE_PATH`
2. If yes: iterate `handles` map, `unsubscribe()` each, clear map, null out `notificationDataSource`
3. If no: ignore

**Guard against stale state** — `SubscriptionCreated`/`SubscriptionUpdated` handlers check `notificationDataSource != null` before wiring. Log warning and skip if null.

**No re-registration** — deregistering the notification DataSource is a deliberate system action. Recovery is outside scope.

### 6. NoOpDataSourceRegistry

`deregister()` remains a silent no-op. Must NOT fire `DataSourceDeregistered` (same reasoning as not firing `DataSourceRegistered`).

## Modules touched

| Module | Change |
|--------|--------|
| `platform-api/` | `DataSourceDeregistered` record, `register()` contract change from upsert to idempotent, `deregister()` javadoc update to reflect async semantics, `DataSourceRegistry` class-level javadoc: add `DataSourceDeregistered` obligation section, `DataSourceDescriptor` javadoc: remove upsert language |
| `datasource-inmem/` | `AlphaDataSource` ref counting + `markForRemoval`, `InMemoryDataSourceRegistry` idempotent register + lifecycle-aware deregister |
| `platform/` | `DataSourceRouter` convergent `wireRoute` (resolve + instance comparison + replace), identity-aware `unwireRoute` via `DataSourceDeregistered.dataSource()` |
| `subscriptions/` | `SubscriptionEngine` observes `DataSourceDeregistered`, unwires handles for its DataSource |

## Not in scope

- Persistent DataSourceRegistry (JPA-backed) (#171) — no persistent backend exists yet
- Descriptor update/mutation (#172) — descriptors are immutable records; change requires deregister + register
- Marshaller configuration (#139) — independent concern
