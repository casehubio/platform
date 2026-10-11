# Decisions — #571 Cascade Enforcement

## D1: Cascade trigger — deregister vs TTL expiry

**Choice:** Both `deregister()` and HeartbeatScheduler TTL expiry trigger cascade. Add `onSourceExpired` field to `CascadeRule` (nullable), defaulting to `onSourceDeregister` when null. Existing 2-arg constructor preserved for backward compatibility.
**Alternatives:**
- Deregister only — simpler but leaves orphaned targets when sources crash without deregistering
- Both triggers, single action — no way to differentiate behavior (e.g. NOTIFY on expiry but DEREGISTER on explicit removal)
**Rationale:** Crash-without-deregister is the common failure mode in distributed systems. Defaulting `onSourceExpired` to `onSourceDeregister` keeps the simple case simple while allowing per-relationship-type differentiation when needed.
**Trade-offs:** Slightly larger CascadeRule record; HeartbeatScheduler gains cascade responsibility (previously fire-and-forget health update)
**Sources:** InMemoryRegistryService.java, HeartbeatScheduler.java, CascadeRule.java, PoolMeshRegistrar.java (claudony)
**Exploration:** quick
**Status:** captured

## D2: Orphan representation — how MARK_ORPHANED is modeled on RegistryEntry

**Choice:** Add nullable `Instant orphanedAt` field to RegistryEntry. Null = not orphaned. Non-null = timestamp of when the entry was orphaned. Convenience method `isOrphaned()`. Wither method `withOrphaned(Instant)` mirrors existing `withHealth()`/`withHeartbeat()`.
**Alternatives:**
- HealthStatus.ORPHANED — loses liveness information (can't be HEALTHY and ORPHANED simultaneously); every mature registry system keeps health and topology separate (Kubernetes, Consul, ZooKeeper)
- Boolean orphaned field — simpler but no timestamp for time-based cleanup policies
- Derived from relationships (no field) — Kubernetes approach but can't distinguish "never had parent" from "parent removed"; requires relationship joins for queries
- Metadata flag — opaque, not queryable via discover(), convention-dependent
**Rationale:** Health and topology are orthogonal dimensions (industry consensus). `orphanedAt` gives boolean status + cleanup timing in one field. Nullable Instant follows existing `lastHeartbeat` pattern. Enables Consul-style timed auto-deregistration (`deregister if orphanedAt + threshold < now`).
**Trade-offs:** One additional field on a foundational record (acceptable for pre-release); RegistryEntry constructor changes (pre-release, no backward compat needed)
**Sources:** Kubernetes OwnerReferences, Consul health model, ZooKeeper ephemeral nodes, industry health-check literature, D2-exploration.md
**Exploration:** deep-analysis
**Status:** captured

## D3: Partial failure handling

**Choice:** Best-effort with result reporting. Each cascade target is applied independently. Failures on one target do not prevent cascading to other targets. A `CascadeResult` reports what succeeded and what failed.
**Alternatives:**
- Atomic (all-or-nothing) — requires transaction support, compensation logic for InMemory, more complex for questionable benefit
- Best-effort silent — simplest but caller has no visibility into partial failures
**Rationale:** Distributed systems are inherently partial-failure environments. Kubernetes GC uses best-effort cascading. Atomicity across independent entries is expensive and often unnecessary — the source is already gone, rolling back a successful cascade on B because C failed doesn't help.
**Trade-offs:** Caller must handle partial results. An entry might be orphaned if its cascade fails.
**Sources:** Kubernetes GC background deletion, Consul deregistration model
**Exploration:** quick
**Status:** captured

## D4: Synchronous cascade execution

**Choice:** Cascade executes synchronously inside `deregister()`, completing before the method returns. Recursive cascade (A→B→C) with cycle detection via a `Set<String> visited` parameter.
**Alternatives:**
- Asynchronous via CDI events — non-blocking but harder to report results, cycle prevention is complex, eventual consistency
- Configurable per-rule — adds complexity without clear need yet
**Rationale:** Kubernetes foreground deletion completes dependents before owner. Synchronous cascade is simpler to reason about, enables result reporting (D3), and makes cycle detection straightforward (pass a visited set through recursion).
**Trade-offs:** `deregister()` call takes longer when cascade chains are deep. Acceptable because cascade depth is typically 1-2 levels, and the operation is already a write.
**Depends on:** D3 (result reporting requires synchronous execution to aggregate results)
**Sources:** Kubernetes foreground cascading deletion
**Exploration:** quick
**Status:** captured

## D5: deregister() return type — CascadeResult

**Choice:** Change `deregister(String id)` to return `CascadeResult`. The result lists: the deregistered entry, all cascaded entries with their actions (DEREGISTER/MARK_ORPHANED/NOTIFY), and any failures. When no cascade rules apply, returns a simple result with just the deregistered entry.
**Alternatives:**
- Remain void with enriched events — less intrusive but caller must set up a watcher to see cascade results
**Rationale:** Pre-release, so breaking the SPI is free. Caller gets full visibility into what happened without needing to register a watcher. The result type is also useful for logging/auditing cascade chains.
**Trade-offs:** Breaking SPI change (`void` → `CascadeResult`). Callers that don't care about cascade results can ignore the return value.
**Depends on:** D3 (CascadeResult carries the partial-failure information), D4 (synchronous execution makes result aggregation possible)
**Sources:** N/A — novel for this registry
**Exploration:** quick
**Status:** captured

## D6: Event kinds for cascade actions

**Choice:** Cascade-triggered deregistrations fire standard `DEREGISTERED` events (same as explicit deregister). Orphaning fires a new `ORPHANED` event kind (added to `EventKind` enum) — distinct from `HEALTH_CHANGED` because orphaning is a topology change, not a health change. `NOTIFY` cascade action fires a new `CASCADE_NOTIFIED` event kind (carries the relationship and source context).
**Alternatives:**
- New cascade-specific event kinds for everything — CASCADE_DEREGISTERED, CASCADE_ORPHANED, CASCADE_NOTIFIED; more granular but forces watchers to handle two event kinds for what is semantically the same outcome (an entry being deregistered)
- Enrich RegistryEvent with cascade metadata — clean but heavier change to the event record
**Rationale:** A deregistration is a deregistration regardless of trigger. Watchers that care about "entry went away" should see one event kind. But orphaning IS semantically new — it's not a health change, it's a topology state change. New ORPHANED and CASCADE_NOTIFIED event kinds fill the gap without splitting existing semantics.
**Trade-offs:** Two new EventKind values. RegistryEvent factory methods needed for the new kinds.
**Depends on:** D2 (ORPHANED event carries the entry with populated orphanedAt)
**Sources:** RegistryEvent.java existing EventKind enum
**Exploration:** quick
**Status:** captured
