# SPI Batch Cleanup — Design Spec

**Date:** 2026-07-12
**Branch:** issue-171-spi-batch-cleanup
**Issues:** #171, #139, #172, #164, #165, #167, #168, #169

---

## Overview

Batch of 8 issues: DataSource infrastructure (#171, #139, #172), digest/delivery
improvements (#164, #165, #167), and structural cleanup (#168, #169). All changes
follow established platform patterns.

---

## Group A — DataSource Infrastructure

### A1. Alpha Network Extraction (prerequisite for #171)

The alpha network runtime (AlphaDataSource, TypeNode, FilterNode, FanOutProcessor)
is currently in `datasource-inmem/`. Both in-memory and JPA registries
need these classes to create working DataSource instances. Having `datasource-jpa/`
depend on `datasource-inmem/` creates a CDI conflict — InMemoryDataSourceRegistry
`@Alternative @Priority(100)` would always beat JpaDataSourceRegistry
`@ApplicationScoped`.

**Module:** `datasource-alpha/` (`casehub-platform-datasource-alpha`)

**Extracted classes:**
- `AlphaDataSource<T>` — Rete alpha network DataSource implementation
- `TypeNode<T>` — type-discriminated routing node
- `FilterNode<T>` — predicate evaluation node
- `FanOutProcessor<T>` — multi-subscriber fan-out with exception isolation

`RegistryKey` is NOT extracted — it is a registry implementation detail (`(path, tenancyId)`
composite map key), not an alpha network runtime class. Both `datasource-inmem/` and
`datasource-jpa/` keep their own package-private `RegistryKey` records.

**Package:** `io.casehub.platform.datasource.alpha`

**Dependencies:** `casehub-platform-api`, `org.jboss.logging:jboss-logging`

**Impact:** `datasource-inmem/` adds `datasource-alpha/` as compile dependency and
removes the extracted classes. Import statements update from
`io.casehub.platform.datasource.memory.*` to
`io.casehub.platform.datasource.alpha.*`. Consumers of `AlphaDataSource` in tests
(SubscriptionEngineTest, DataSourceRouterTest) update imports.

No functional change — pure structural refactoring.

### A2. JPA-backed DataSourceRegistry (#171)

**Module:** `datasource-jpa/` (`casehub-platform-datasource-jpa`)

**CDI:** `@ApplicationScoped JpaDataSourceRegistry` — Tier 2, beats `@DefaultBean`
no-op. `InMemoryDataSourceRegistry @Alternative @Priority(100)` beats it in tests
when on the test classpath.

**Entity:** `DataSourceDescriptorEntity`

**Table:** `datasource_descriptor` (Flyway V4000)

```sql
CREATE TABLE IF NOT EXISTS datasource_descriptor (
    path              VARCHAR(1024) NOT NULL,
    tenancy_id        VARCHAR(255)  NOT NULL,
    object_type_key   VARCHAR(255)  NOT NULL,
    endpoint_path     VARCHAR(1024),
    accepted_event_types TEXT,
    properties        TEXT,
    marshaller_keys   TEXT,
    registered_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (path, tenancy_id)
);
```

- `accepted_event_types`: JSON array string
- `properties`: JSON object string
- `marshaller_keys`: JSON object string (eventType → marshallerKey mapping)
- Flyway DDL uses `TEXT` — consistent with ALL existing platform migrations
  (`digest-jpa/V2000` uses `TEXT` for `notification_json`,
  `delivery-tracking-jpa/V3000` uses `TEXT` for `payload`). H2 does not support
  `JSONB`, so PostgreSQL-specific types in DDL would break `@QuarkusTest` with H2.
- JPA entity fields use `@JdbcTypeCode(SqlTypes.JSON)` — Hibernate maps to JSONB on
  PostgreSQL (write-time validation, `@>` containment queries) and VARCHAR on H2 in
  tests. JSON semantics are enforced at the JPA layer, not the DDL layer.
- Natural composite PK `(path, tenancy_id)` — matches the SPI key semantics

**Startup reconciliation:** `@Observes StartupEvent` loads all persisted descriptors,
creates AlphaDataSource for each, fires `DataSourceRegistered` events. This
restores the runtime state after restart. Uses `StartupEvent` (not `@PostConstruct`)
because CDI observers like `DataSourceRouter` and `SubscriptionEngine` may not be
fully initialized during `@PostConstruct` — consistent with `SubscriptionEngine`'s
existing `@Observes StartupEvent` pattern.

**Runtime behavior:** `register()` persists the descriptor AND creates an
AlphaDataSource. `deregister()` removes the DB row AND marks the AlphaDataSource
for removal (same lifecycle as InMem). CDI events fire on all mutations.

**Dependencies:**
- `casehub-platform-api`
- `casehub-platform-datasource-alpha` (AlphaDataSource runtime)
- `quarkus-hibernate-orm-panache`, `quarkus-flyway`, `quarkus-jdbc-postgresql` (optional)
- Test: `casehub-platform-testing`, `quarkus-junit`, `quarkus-jdbc-h2`, `assertj-core`

**Test:** `JpaDataSourceRegistryTest` — mirrors `InMemoryDataSourceRegistryTest`
structure. H2 in-memory database. `@QuarkusTest` with `@TestTransaction`.

### A3. Marshaller Configuration Model (#139)

**SPI change (platform-api):**

Add `marshallerKeys` field to `DataSourceDescriptor`:

```java
public record DataSourceDescriptor(
    Path path,
    String tenancyId,
    ObjectType<?> objectType,
    Path endpointPath,
    Set<String> acceptedEventTypes,
    Map<String, String> properties,
    Map<String, String> marshallerKeys   // eventType → marshallerKey; empty = no marshalling
)
```

Issue #139 requires "multiple marshallers per DataSource (different event types)."
A single `marshallerKey` would only support one marshaller for all event types. The
`marshallerKeys` map keys each accepted event type to its marshaller key, enabling
per-event-type marshalling (e.g., `order.created` → `orderMarshaller`,
`order.cancelled` → `cancellationMarshaller`).

New SPI `MarshallerRegistry`:

```java
public interface MarshallerRegistry {
    void register(String key, Marshaller<?, ?> marshaller);
    Optional<Marshaller<?, ?>> resolve(String key);
}
```

**Default implementation (platform/):** `@DefaultBean NoOpMarshallerRegistry` —
`resolve()` always returns empty.

**Population timing contract:** Marshallers must be registered during bean
initialization (`@PostConstruct`), not during `@Observes StartupEvent`. The JPA
DataSourceRegistry reconciles persisted descriptors at `@Observes StartupEvent` and
calls `marshallerRegistry.resolve()` for each marshallerKey (fail-fast). If a
`MarshallerRegistry` implementation also populates at `StartupEvent`, ordering between
the two handlers is nondeterministic — the JPA registry may reconcile before marshallers
are registered, causing spurious `IllegalArgumentException` on startup. CDI injection
ordering guarantees that `@PostConstruct` on the injected `MarshallerRegistry` bean
completes before the consuming `DataSourceRegistry` bean handles `StartupEvent`.

**Wiring:** When `DataSourceRegistry.register()` gets a descriptor with non-empty
`marshallerKeys`, resolve ALL referenced Marshallers from MarshallerRegistry at
registration time. Wrap the returned DataSource with a decorator that intercepts
`add(input)` — the decorator inspects the CloudEvent type, looks up the appropriate
marshaller from the pre-resolved map, and applies `marshaller.marshal(input)` before
routing into the alpha network. Callers see the same DataSource interface.

**Fail-fast at registration:** If any marshallerKey in the map cannot be resolved by
`MarshallerRegistry`, throw `IllegalArgumentException` from `register()`. A
configured-but-unresolvable marshaller key is a configuration error, not a runtime
degradation — fail-open would cause the DataSource to silently receive raw objects
that fail `ObjectType.matches()` in the TypeNode, resulting in invisible data loss
with no error signal.

**Unmapped event types pass through without marshalling.** If a CloudEvent's type has
no entry in `marshallerKeys`, the decorator passes the raw object to the alpha network
unchanged. `marshallerKeys` is an opt-in binding — only event types with explicit
marshaller entries are transformed. This supports mixed-type DataSources where some
event types need marshalling and others are consumed raw (e.g., `order.created` is
marshalled to `Order`, while `order.audit` passes through as a CloudEvent for
audit-log subscribers using a `CloudEvent` ObjectType).

**Marshaller javadoc:** Update `Marshaller.java` javadoc to remove the reference to
the non-existent `MarshallNode`. The decorator pattern is the chosen integration
approach — marshalling happens before the alpha network, not inside it as a node.

**Impact on callers:** DataSourceDescriptor constructor gains one parameter.
All existing call sites pass `Map.of()` for marshallerKeys (no behavioral change).

### A4. DataSource Descriptor Update (#172)

**SPI change (platform-api):**

```java
void update(DataSourceDescriptor descriptor);
```

Added to `DataSourceRegistry`. Semantics:
- Key match: `(path, tenancyId)` must match an existing registration
- Throws `IllegalStateException` if not found — unlike `deregister()` (where no-op is
  safe because the goal "key is gone" is achieved regardless), `update()` no-op is a
  silent correctness risk because the caller's goal "descriptor is updated" is NOT achieved.
  **NoOp exemption:** the `@DefaultBean` NoOp implementation is exempt — it accepts all
  calls silently, consistent with the existing NoOp displacement contract where register()
  returns a stub and deregister() is no-op. The throw-on-not-found applies only to
  non-no-op implementations (InMem, JPA).
- Immutable field: `objectType` — changing objectType invalidates TypeNode routing for
  existing subscribers (subscribers wired to `TypeNode<Order>` silently stop receiving
  data if type changes to `Transaction`). ObjectType changes require deregister+register.
  **Runtime enforcement:** implementations MUST check
  `descriptor.objectType().getTypeKey().equals(existing.objectType().getTypeKey())` and
  throw `IllegalArgumentException` if they differ. Documentation alone is insufficient —
  without a runtime check, callers can silently change the type and break subscribers.
- Mutable fields: endpointPath, acceptedEventTypes, properties, marshallerKeys
- The DataSource instance survives — active subscriptions are preserved

**New CDI event:**

```java
public record DataSourceUpdated(
    DataSourceDescriptor oldDescriptor,
    DataSourceDescriptor newDescriptor,
    DataSource<?> dataSource
) {}
```

Carries the current `DataSource<?>` instance — necessary because marshalling changes
via `update()` rebuild the decorator, producing a new DataSource instance in the
registry's `sources` map. Without the DataSource reference, `DataSourceRouter` can
replace the cached descriptor but still holds the OLD decorator with OLD marshalling
logic. Follows the `DataSourceDeregistered(descriptor, dataSource)` pattern where the
instance is carried for identity-based operations in observers.

**Implementations:**
- NoOp: silent no-op
- InMem: `descriptors.replace(key, descriptor)` + fire CDI event
- JPA: entity merge + fire CDI event

If marshallerKeys change, the marshalling decorator is rebuilt with the new
Marshaller set (fail-fast: unresolvable keys throw from `update()`). If marshallerKeys
is cleared (empty map), marshalling is removed.

**DataSourceRouter observer:** `DataSourceRouter` must observe `DataSourceUpdated` to
replace the cached descriptor AND DataSource instance in its `WiredDataSource` list,
using `event.dataSource()` for the new DataSource reference. Without this, changes to
`acceptedEventTypes` or `endpointPath` via `update()` leave the routing table stale,
and marshalling changes leave the handler calling `add()` on the old decorator with
old marshalling logic.

```java
void onDataSourceUpdated(@ObservesAsync DataSourceUpdated event) {
    // Replace both descriptor AND dataSource in wiredDataSources for matching (path, tenancyId)
    // Use event.newDescriptor() for the descriptor, event.dataSource() for the DataSource
}
```

---

## Group B — Digest/Delivery Improvements

### B1. BUFFER_FOR_DIGEST Validation (#164)

Validate at preference update time: when `QuietHoursAction.BUFFER_FOR_DIGEST` is
set, at least one channel in the user's channel preferences must have a digest
schedule configured.

**Location:** Standalone `PreferenceValidator` in `notifications/` module — same
module as the REST endpoint. The SPI in `platform-api` must not depend on
`DeliveryChannelRegistry` for validation.

```java
@ApplicationScoped
public class PreferenceValidator {
    @Inject DeliveryChannelRegistry channelRegistry;

    public void validate(NotificationPreferenceUpdate update,
                         NotificationPreferences existing) {
        if (update has BUFFER_FOR_DIGEST quiet hours action) {
            boolean anyDigested = effective channel prefs have at least one
                                  digest schedule (user pref or channel default);
            if (!anyDigested) {
                throw new IllegalArgumentException(
                    "BUFFER_FOR_DIGEST requires at least one channel with a digest schedule");
            }
        }
    }
}
```

**Wiring point:** `NotificationPreferenceResource.update()` in
`notifications/src/main/java/io/casehub/platform/notification/rest/NotificationPreferenceResource.java`.
The resource injects `PreferenceValidator` and calls `validator.validate(update, existing)`
before `store.update()`. `DeliveryChannelRegistry` is in `platform-api`, so `notifications/`
already has the dependency — no new module dependencies needed.

**ChannelRouter runtime warning:** KEEP the existing WARN log in `ChannelRouter`. The
upstream validation prevents the worst case (zero digested channels at preference
update time), but the runtime state can diverge after validation — e.g., a channel's
default digest schedule changes, or the user modifies digest settings on a different
channel. The runtime warning is defense-in-depth that catches state divergence the
upstream validation cannot prevent. Cost: one `LOG.warnf()` per affected channel
routing decision. Benefit: runtime visibility into a configuration inconsistency.

### B2. Secondary Index for pendingKeysForUser (#165)

**JPA (digest-jpa):** The existing composite index
`idx_digest_buffer_key (user_id, tenancy_id, channel_id)` covers the prefix query
`WHERE user_id = ? AND tenancy_id = ?`. No new migration needed.

**InMem (digest-inmem):** Add a secondary reverse-lookup map:

```java
private final ConcurrentHashMap<String, Set<DigestBufferKey>> userIndex =
    new ConcurrentHashMap<>();

private static String userKey(String userId, String tenancyId) {
    return userId + "|" + tenancyId;
}
```

Inner set type: `ConcurrentHashMap.newKeySet()` — the outer `ConcurrentHashMap`
provides per-key atomicity via `compute()`, but `add()` and `drain()` for the same
user can execute concurrently from different threads. A plain `HashSet` would corrupt
under concurrent mutation.

Maintain on `add()` (add to set) and `drain()` (remove from set when buffer
empties). All set mutations occur inside `userIndex.compute()` for per-key atomicity.
`pendingKeysForUser()` becomes O(1) lookup instead of O(n) stream filter.

### B3. Digest Buffer Retention (#167)

**JPA (digest-jpa):** Add `@Scheduled` retention purge targeting orphan rows only:

```java
@Scheduled(cron = "0 0 3 * * ?")
@Transactional
void retentionPurge() {
    Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));
    int purged = entityManager.createQuery(
            "DELETE FROM DigestBufferEntity e WHERE e.bufferedAt < :cutoff " +
            "AND NOT EXISTS (" +
            "  SELECT 1 FROM DigestBufferEntity recent " +
            "  WHERE recent.userId = e.userId " +
            "  AND recent.tenancyId = e.tenancyId " +
            "  AND recent.channelId = e.channelId " +
            "  AND recent.bufferedAt >= :cutoff" +
            ")")
        .setParameter("cutoff", cutoff)
        .executeUpdate();
    if (purged > 0) {
        LOG.infof("Digest retention purge: %d orphan rows removed", purged);
    }
}
```

The `NOT EXISTS` subquery ensures only truly orphaned keys are purged — rows are
deleted only when ALL entries for that `(userId, tenancyId, channelId)` combination
are older than the cutoff. If any entry is recent, the key is still active (receiving
new notifications) and none of its entries are purged. This prevents the scenario
where a monthly digest schedule's pending entries (legitimately buffered for up to 31
days) are deleted before the digest fires.

Config: `casehub.notification.digest.retention-days` (default 90). Default is 90 days
(not 30) to comfortably exceed any reasonable digest schedule period including monthly
and quarterly.

**InMem (digest-inmem):** Add `lastModified` timestamp to `BufferEntry` (updated on
every `add()`). Apply TTL check consistently across ALL read methods — `pendingKeys()`,
`pendingKeysForUser()`, `pendingCount()`, `oldestPendingTimestamp()`, and `drain()` —
to avoid inconsistency where expired keys appear in some methods but not others. A
key is expired when `lastModified + retentionPeriod < now`. Lazy eviction: expired
entries are removed from the map on access.

---

## Group C — Structural Cleanup

### C1. UUIDv7 Relocation (#168)

Move `io.casehub.platform.api.notification.UUIDv7` to
`io.casehub.platform.api.util.UUIDv7`.

Create new package `io.casehub.platform.api.util` in platform-api. Move the class
and its test. Use IntelliJ `ide_move_file` to update all 46 references across
the codebase automatically.

### C2. InMemoryDeliveryChannelRegistry Extraction (#169)

**New module:** `delivery-channel-inmem/` (`casehub-platform-delivery-channel-inmem`)

Extract `InMemoryDeliveryChannelRegistry` from `notification-dispatch/` into the
new module. Stays `@ApplicationScoped` — this is the production implementation
(channels are startup-configured, not dynamic).

`notification-dispatch/` adds `delivery-channel-inmem/` as a compile dependency.

**pom.xml pattern:** Follows existing inmem modules (jandex plugin, platform-api
dependency, quarkus-arc).

No `@Alternative` — there is only one implementation. No @DefaultBean no-op needed
because `DeliveryChannelRegistry` already has method-level contracts that require
a real implementation (channels must be resolvable for delivery to work).

---

## Implementation Order

1. **C1** — UUIDv7 relocation (unblocks nothing, but changes import paths — do first
   to avoid conflicts with later changes)
2. **A1** — Alpha network extraction (prerequisite for A2)
3. **A3** — Marshaller configuration model (changes DataSourceDescriptor — do before
   A2 so the JPA entity includes marshallerKeys from the start)
4. **A4** — DataSource descriptor update (adds update() to SPI — do before A2 so
   JPA implements it from the start)
5. **A2** — JPA-backed DataSourceRegistry (depends on A1, A3, A4)
6. **C2** — InMemoryDeliveryChannelRegistry extraction
7. **B1** — BUFFER_FOR_DIGEST validation
8. **B2** — Secondary index for pendingKeysForUser
9. **B3** — Digest buffer retention

---

## CLAUDE.md Updates Required

After implementation:
- Add `datasource-alpha/` to Modules table
- Add `datasource-jpa/` to Modules table
- Add `delivery-channel-inmem/` to Modules table
- Add `MarshallerRegistry` to Package Structure
- Update `DataSourceDescriptor` entry in Package Structure (marshallerKeys field)
- Add `DataSourceUpdated` to Package Structure
- Update UUIDv7 package location

## ARC42STORIES.MD Updates Required

After implementation, add to ARC42STORIES.MD:

**§1 Module structure paragraph:** Add `datasource-alpha/` (shared alpha network
runtime), `datasource-jpa/` (JPA-backed DataSourceRegistry), `delivery-channel-inmem/`
(InMemoryDeliveryChannelRegistry extraction).

**Layer taxonomy table:** Add new layer for DataSource infrastructure:
- L13: DataSource Infrastructure: `datasource-alpha/` (shared alpha network runtime —
  AlphaDataSource, TypeNode, FilterNode, FanOutProcessor), `datasource-inmem/` (volatile
  InMemoryDataSourceRegistry), `datasource-jpa/` (JPA-backed JpaDataSourceRegistry +
  Flyway V4000)
- L14: Delivery Channel Adapters: `delivery-channel-inmem/` (InMemoryDeliveryChannelRegistry
  extraction from notification-dispatch/)

**§5 Building Block View diagram:** Add container entries for all three new modules
within the appropriate layer boundaries, plus relationship arrows to `platform-api`.

**§5 existing gap:** `datasource-inmem/` is currently missing from the layer taxonomy
and §5 diagram — include it in the L13 update.
