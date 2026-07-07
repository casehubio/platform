# Notification S/XS Batch Design

**Date:** 2026-07-07
**Branch:** issue-157-notification-s-xs-batch
**Covers:** #157, #163, #159, #161, #162, #156
**Epic:** #147

---

## 1. Code Review Cleanup (#157, #163)

### #157-F1: Redundant requireNonNull in NotificationPreferenceUpdate

Remove `Objects.requireNonNull(channelDefaults, "channelDefaults")` inside the `if (channelDefaults != null)` guard in the compact constructor. Dead code — can never throw.

**File:** `platform-api/.../NotificationPreferenceUpdate.java`

### #157-F2: SuppressionEvaluator triple Instant.now()

Thread `Instant now` as a parameter to both `evaluate()` and `evaluateUserLevel()`. `checkSnoozed` and `checkQuietHours` use that single instant. `LocalTime.now(tz)` becomes `now.atZone(tz).toLocalTime()`.

`checkMuted` drops its `Instant.now()` expiry check — the store layer (F5, F6) is now authoritative for expiry filtering. The evaluator trusts the list returned by `activeMutes()` without re-filtering.

Breaking signature change — two callers update mechanically:
- `NotificationDispatcher.dispatchToUser()` passes `Instant.now()`
- `DigestFlushScheduler.processKey()` passes its local `now`

**Files:** `notification-dispatch/.../SuppressionEvaluator.java`, `NotificationDispatcher.java`, `DigestFlushScheduler.java`, and their tests.

### #157-F3: NotificationSeverity ordinal comparison

Add `isAtLeast(NotificationSeverity threshold)` method to the enum:

```java
public enum NotificationSeverity {
    /** Ordinal order encodes priority: INFO < WARNING < URGENT. */
    INFO,
    WARNING,
    URGENT;

    public boolean isAtLeast(NotificationSeverity threshold) {
        return this.ordinal() >= threshold.ordinal();
    }
}
```

`ChannelRouter` changes from `severity.ordinal() < minSeverity.ordinal()` to `!severity.isAtLeast(minSeverity)`.

**Files:** `platform-api/.../NotificationSeverity.java`, `notification-dispatch/.../ChannelRouter.java`

### #157-F4: TemplateResolver MethodHandle cache

Add a static `ConcurrentHashMap<Class<?>, Map<String, MethodHandle>>` to `TemplateResolver`. `extractField()` checks the cache first, populates on miss. Thread-safe, zero contention on reads after warmup.

```java
private static final ConcurrentHashMap<Class<?>, ConcurrentHashMap<String, Optional<MethodHandle>>> HANDLE_CACHE =
    new ConcurrentHashMap<>();

static String extractField(final Object pojo, final String fieldName) {
    try {
        var classHandles = HANDLE_CACHE.computeIfAbsent(pojo.getClass(), k -> new ConcurrentHashMap<>());
        var handle = classHandles.computeIfAbsent(fieldName, f -> {
            try {
                var method = pojo.getClass().getMethod(f);
                return Optional.of(MethodHandles.lookup().unreflect(method));
            } catch (Exception e) {
                return Optional.empty();
            }
        });
        if (handle.isEmpty()) return null;
        Object value = handle.get().invoke(pojo);
        return value != null ? value.toString() : null;
    } catch (Throwable e) {
        return null;
    }
}
```

**File:** `notification-dispatch/.../TemplateResolver.java`

### #157-F5: InMemorySuppressionStore lazy eviction

Implement actual eviction in `activeMutes()` using `compute()` — filter and replace the list, removing expired entries:

```java
@Override
public List<MuteRule> activeMutes(String userId, String tenancyId) {
    String key = makeKey(userId, tenancyId);
    Instant now = Instant.now();
    List<MuteRule> active = new ArrayList<>();
    muteStore.computeIfPresent(key, (k, rules) -> {
        List<MuteRule> filtered = rules.stream()
            .filter(r -> r.expiresAt() == null || !now.isAfter(r.expiresAt()))
            .toList();
        active.addAll(filtered);
        return filtered.isEmpty() ? null : new ArrayList<>(filtered);
    });
    return active.isEmpty() ? List.of() : List.copyOf(active);
}
```

The store's `Instant.now()` serves eviction (housekeeping), not evaluation. F2's evaluator no longer re-filters by expiry — the store is the single authority for mute expiry.

**File:** `notification-settings-inmem/.../InMemorySuppressionStore.java`

### #157-F6: JPA activeMutes() server-side filtering

Add expiry filter to the JPQL WHERE clause:

```sql
SELECT m FROM MuteRuleEntity m
WHERE m.userId = :userId AND m.tenancyId = :tenancyId
  AND (m.expiresAt IS NULL OR m.expiresAt > :now)
```

Pass `Instant.now()` as `:now` parameter. Remove Java-side filtering.

**File:** `notification-settings-jpa/.../JpaSuppressionStore.java`

### #157-F7: Flyway V2 SQL concatenation

Replace string concatenation with `jsonb_build_array`/`jsonb_build_object`:

```sql
UPDATE subscription SET targets_json =
    jsonb_build_array(jsonb_build_object('type', 'USER', 'id', owner_id))::text
WHERE targets_json IS NULL;
```

Safe regardless of `owner_id` content.

**File:** `subscriptions-jpa/.../V2__subscription_targets.sql`

### #157-F8: Instant.now() as updatedAt in preferences GET

Replace `Instant.now()` with `Instant.EPOCH` in the fallback `NotificationPreferences`:

```java
.orElseGet(() -> new NotificationPreferences(
    principal.actorId(), principal.tenancyId(),
    Map.of(), null, Instant.EPOCH));
```

Two consecutive GETs now return the same timestamp. Clients can use `EPOCH` as a sentinel for "never saved."

**File:** `notifications/.../NotificationPreferenceResource.java`

### #163-F4: Misleading test name

Rename `tick_flushesWhenIntervalElapsed` → `tick_doesNotFlush_whenIntervalNotElapsed`.

### #163-F5: Missing positive flush test

Thread `Instant now` through `DigestFlushScheduler.processKey()` (same pattern as SuppressionEvaluator F2). `tick()` passes `Instant.now()`. New test calls `processKey(key, futureTimestamp)` to verify items are delivered when the interval has elapsed.

### #163-F6: Missing quiet hours deferral test

Add a test with `QuietHours` configured. Set up the stub preference store with quiet hours active, verify deferral. Uses the threaded `Instant now` for deterministic time control.

### #163-F7: DigestBuffer SPI Javadoc

Add contract documentation:

```java
public interface DigestBuffer {
    /** Add a notification to the buffer. Thread-safe. */
    void add(DigestBufferKey key, NotificationInput notification);

    /**
     * Atomically drain all buffered notifications for the key.
     * Returns an immutable list; the buffer for this key is empty after the call.
     * Concurrent adds during drain must not lose items — implementations must
     * use atomic swap (e.g. ConcurrentHashMap.remove()) not clear().
     */
    List<NotificationInput> drain(DigestBufferKey key);

    /** Returns a snapshot of keys with pending items. Thread-safe. */
    Set<DigestBufferKey> pendingKeys();

    /** Timestamp of the oldest buffered item for the key, or empty if no items. */
    Optional<Instant> oldestPendingTimestamp(DigestBufferKey key);

    /** Count of pending items for the key without draining. */
    int pendingCount(DigestBufferKey key);

    /** Pending keys for a specific user. Avoids full-scan in per-user REST endpoints. */
    Set<DigestBufferKey> pendingKeysForUser(String userId, String tenancyId);
}
```

Note: `pendingCount` is also required by #161 (digest status endpoint).

### #163-F8: ChannelPreference.isDigested()

Keep. Semantically correct derived method, useful as a guard when `groupBy` is added (#159). `@JsonIgnore` is correct for derived properties. No action.

### #163-F9: jackson-annotations in platform-api

Accepted precedent. `DigestSchedule` already uses `@JsonTypeInfo`/`@JsonSubTypes`. jackson-annotations as `provided` scope is inert without a runtime — same logic as CDI annotations and Mutiny. No action.

---

## 2. DigestGroupBy Preference (#159)

### New type: DigestGroupBy enum

```java
package io.casehub.platform.api.delivery;

public enum DigestGroupBy {
    FLAT,       // No grouping — chronological list
    CATEGORY,   // Group by notification category
    ENTITY      // Group by source entity (entityType + entityId)
}
```

**Location:** `platform-api/src/main/java/io/casehub/platform/api/delivery/DigestGroupBy.java`

### ChannelPreference field addition

```java
public record ChannelPreference(
    boolean enabled,
    NotificationSeverity minSeverity,
    DigestSchedule digestSchedule,
    DigestGroupBy groupBy
) {
    public ChannelPreference {
        Objects.requireNonNull(minSeverity, "minSeverity");
    }

    @JsonIgnore
    public boolean isDigested() {
        return digestSchedule != null;
    }
}
```

`groupBy` is nullable — `null` means `FLAT`. Existing serialized preferences deserialize without migration.

### DigestSummary field addition

```java
public record DigestSummary(
    String userId, String tenancyId, String channelId,
    List<NotificationInput> notifications,
    Instant periodStart, Instant periodEnd,
    DigestGroupBy groupBy
) { ... }
```

`DigestFlushScheduler.flushKey()` resolves `groupBy` from the user's `ChannelPreference` and passes it into the summary. Deliverers use it in `deliverDigest()` to decide presentation. No platform-level grouping logic — the platform passes the preference through; deliverers decide how to render.

---

## 3. Digest Status REST Endpoint (#161)

### New SPI methods: DigestBuffer.pendingCount() and pendingKeysForUser()

```java
int pendingCount(DigestBufferKey key);
Set<DigestBufferKey> pendingKeysForUser(String userId, String tenancyId);
```

`InMemoryDigestBuffer` — uses `CopyOnWriteArrayList` for thread-safe concurrent reads (the REST endpoint reads `size()` while dispatch threads call `add()`):

```java
// BufferEntry uses CopyOnWriteArrayList, not ArrayList
record BufferEntry(CopyOnWriteArrayList<NotificationInput> notifications, Instant addedAt) {}

@Override
public int pendingCount(DigestBufferKey key) {
    var entry = buffers.get(key);
    return entry != null ? entry.notifications().size() : 0;
}

@Override
public Set<DigestBufferKey> pendingKeysForUser(String userId, String tenancyId) {
    return buffers.keySet().stream()
        .filter(k -> k.userId().equals(userId) && k.tenancyId().equals(tenancyId))
        .collect(Collectors.toUnmodifiableSet());
}
```

### Buffer size limit (InMemoryDigestBuffer)

`InMemoryDigestBuffer` enforces a per-key buffer size limit to prevent unbounded memory growth:

- **Config:** `casehub.notification.digest.max-buffer-size` (default: 500)
- **Eviction:** FIFO — when the buffer exceeds `maxBufferSize`, the oldest notification is removed
- **Logging:** DEBUG-level log on eviction (`Buffer eviction for key %s — max size %d exceeded`)

This is an OOM safety net for long digest intervals or quiet hours deferral. Under normal operation (4-hour digest interval, moderate notification volume) the limit is never reached. Silent eviction is acceptable because the in-memory buffer is inherently lossy (process restart loses all buffered items); a JPA-backed buffer would use a different strategy.

### REST endpoint

In the existing `notifications/` module, new resource or method on an existing resource:

```java
@GET
@Path("/notifications/digest/status")
public Map<String, Integer> digestStatus() {
    String userId = principal.actorId();
    String tenancyId = principal.tenancyId();

    Map<String, Integer> result = new LinkedHashMap<>();
    for (DigestBufferKey key : digestBuffer.pendingKeysForUser(userId, tenancyId)) {
        int count = digestBuffer.pendingCount(key);
        if (count > 0) {
            result.put(key.channelId(), count);
        }
    }
    return result;
}
```

Response: `{"email": 12, "sms": 3}`. Empty map when no pending digests.

Injects `DigestBuffer` and `CurrentPrincipal`. Lives in the `notifications/` REST module.

---

## 4. Quiet Hours → Digest Integration (#162)

**Principle: "deferred, not lost."**

### New type: QuietHoursAction enum

```java
package io.casehub.platform.api.notification.settings;

public enum QuietHoursAction {
    SUPPRESS,           // Drop external notifications during quiet hours
    BUFFER_FOR_DIGEST   // Buffer for next digest flush after quiet hours end
}
```

**Location:** `platform-api/src/main/java/io/casehub/platform/api/notification/settings/QuietHoursAction.java`

### QuietHours field addition

```java
public record QuietHours(
    LocalTime start,
    LocalTime end,
    ZoneId timezone,
    QuietHoursAction action
) {
    public QuietHours {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(timezone, "timezone");
    }
}
```

`action` is nullable — `null` means `SUPPRESS` (current behavior). Existing serialized `QuietHours` work without migration.

### ChannelRouter.route() signature and logic change

New parameter: `QuietHoursAction quietHoursAction`.

```java
public Set<ResolvedChannel> route(Map<String, ChannelPreference> channelDefaults,
                                  SuppressionResult suppressionResult,
                                  NotificationSeverity severity,
                                  QuietHoursAction quietHoursAction) {
    // ... per channel:

    boolean quietHoursBuffering = suppressionResult.quietHoursActive()
        && quietHoursAction == QuietHoursAction.BUFFER_FOR_DIGEST
        && effectiveDigest != null;

    if (suppressionResult.quietHoursActive()
        && quietHoursAction == QuietHoursAction.BUFFER_FOR_DIGEST
        && effectiveDigest == null) {
        LOG.warnf("BUFFER_FOR_DIGEST active on channel %s but no digest schedule configured"
            + " — notification suppressed", channelId);
    }

    boolean suppressed = descriptor.external()
        && (suppressionResult.isSnoozed()
            || (suppressionResult.quietHoursActive() && !quietHoursBuffering));

    boolean digested = descriptor.external()
        && effectiveDigest != null
        && (!severity.isAtLeast(NotificationSeverity.URGENT) || quietHoursBuffering);
}
```

Identical to current logic except: `|| quietHoursBuffering` allows URGENT into digest during quiet hours when the user has opted in.

### NotificationDispatcher change

Resolves `QuietHoursAction` from user preferences and passes to `ChannelRouter`:

```java
QuietHoursAction quietHoursAction = preferences
    .map(NotificationPreferences::quietHours)
    .map(QuietHours::action)
    .orElse(null);

Set<ResolvedChannel> channels = channelRouter.route(
    channelDefaults, suppressionResult, notificationInput.severity(), quietHoursAction);
```

Null-safe — `null` action means `SUPPRESS` (current behavior).

### DigestFlushScheduler: quiet hours transition flush

When `BUFFER_FOR_DIGEST` defers a flush during quiet hours, the scheduler tracks the key. On the next tick where quiet hours are no longer active for a previously-deferred key, it flushes immediately regardless of the normal digest schedule.

Complete `processKey` guard chain (replaces the current combined snooze/quiet-hours check):

```java
private final Set<DigestBufferKey> quietHoursDeferredKeys = ConcurrentHashMap.newKeySet();

void processKey(DigestBufferKey key, Instant now) {
    // ... resolve prefs, schedule ...

    if (schedule == null) {
        // Orphan: user disabled digest since buffering — flush immediately
        quietHoursDeferredKeys.remove(key);  // clean up phantom deferred state
        flushKey(key, now, null);
        return;
    }

    // ... resolve oldest, lastFlush ...

    // 1. Quiet hours tracking — pure time comparison, no DB hit
    var quietHours = prefs.map(NotificationPreferences::quietHours).orElse(null);
    if (quietHours != null) {
        var qhResult = suppressionEvaluator.evaluateUserLevel(Optional.empty(), quietHours, now);
        if (qhResult.quietHoursActive()) {
            quietHoursDeferredKeys.add(key);
            return;
        }
    }

    // 2. Schedule gate — is a flush due (deferred OR scheduled)?
    boolean deferredFlush = quietHoursDeferredKeys.contains(key);
    if (!deferredFlush && !schedule.isFlushDue(oldest, lastFlush, now)) {
        return;
    }

    // 3. Snooze check — DB hit, only when flush is imminent
    var activeSnooze = suppressionStore.activeSnooze(key.userId(), key.tenancyId());
    var suppression = suppressionEvaluator.evaluateUserLevel(activeSnooze, quietHours, now);
    if (suppression.isSnoozed()) {
        return;  // deferred key preserved — re-attempted next tick
    }

    // 4. Consume deferred key and flush
    quietHoursDeferredKeys.remove(key);
    flushKey(key, now);
}
```

Guard chain design:

- **Step 1** (quiet hours) runs every tick but is a pure time comparison — no DB, no store lookup. Adds key to the deferred set when quiet hours are active.
- **Step 2** (schedule gate) uses `contains()`, not `remove()` — the deferred key is preserved if snooze blocks the flush in step 3. The key is only consumed in step 4 when the flush actually proceeds.
- **Step 3** (snooze) hits the DB via `suppressionStore.activeSnooze()` but only when a flush would actually happen (deferred or scheduled). This preserves the current performance characteristic: ~1 DB query/hour/key when the schedule is hourly, not 60 queries/hour/key on every minute tick.
- **Step 4** atomically consumes the deferred key and flushes. After this, subsequent ticks follow the normal schedule.

### Behavioral summary

| Quiet hours active | Snoozed | Action | Channel has digest | Severity | Result |
|---|---|---|---|---|---|
| yes | no | SUPPRESS (default) | any | any | suppressed (dropped) — current |
| yes | no | BUFFER_FOR_DIGEST | yes | INFO/WARNING | digested (buffered) |
| yes | no | BUFFER_FOR_DIGEST | yes | URGENT | digested (buffered) — URGENT deferred, flushed when quiet hours end |
| yes | no | BUFFER_FOR_DIGEST | no | any | suppressed (dropped) — WARN logged (configuration mismatch) |
| yes | yes | BUFFER_FOR_DIGEST | yes | any | digested (buffered) — digested takes priority over snooze in dispatcher |
| no | yes | any | yes | any | digested (buffered) — snooze does not prevent digest buffering |
| no | yes | any | no | any | suppressed (dropped) — snooze suppresses immediate delivery |
| no | no | any | yes | INFO/WARNING | digested (normal digest) |
| no | no | any | yes | URGENT | immediate delivery (URGENT bypasses digest) |
| no | no | any | no | any | immediate delivery |

**Note:** When both `suppressed` and `digested` are true, the dispatcher checks `digested` first — the notification is buffered, not dropped. This is pre-existing behavior in `NotificationDispatcher.dispatchToUser()`.

---

## 5. Channel Subscriber Target Type (#156)

### New TargetType enum value

```java
public enum TargetType {
    USER,
    GROUP,
    EVENT_FIELD,
    ENTITY_WATCHERS
}
```

### New SPI: EntityWatcherProvider

```java
package io.casehub.platform.api.subscription;

import java.util.Set;

/**
 * Resolves users watching a specific entity. Application-tier implementations
 * provide the actual watch/follow tracking.
 *
 * <p>Consumed by {@link io.casehub.platform.notification.dispatch.TargetResolver}
 * when expanding {@link TargetType#ENTITY_WATCHERS} targets.
 */
public interface EntityWatcherProvider {
    /**
     * @param entityType the type of entity being watched (e.g. "work-item", "case")
     * @param entityId   the specific entity ID
     * @param tenancyId  tenant context
     * @return user IDs of all watchers; empty set if none or if watching is not implemented
     */
    Set<String> watchersOf(String entityType, String entityId, String tenancyId);
}
```

**Location:** `platform-api/src/main/java/io/casehub/platform/api/subscription/EntityWatcherProvider.java`

### @DefaultBean no-op in platform/

```java
package io.casehub.platform.subscription;

@DefaultBean
@ApplicationScoped
public class NoOpEntityWatcherProvider implements EntityWatcherProvider {
    private static final Logger LOG = Logger.getLogger(NoOpEntityWatcherProvider.class);

    @Override
    public Set<String> watchersOf(String entityType, String entityId, String tenancyId) {
        LOG.warnf("ENTITY_WATCHERS target used but no EntityWatcherProvider is registered"
            + " — notifications to %s/%s will not be delivered", entityType, entityId);
        return Set.of();
    }
}
```

### TargetResolver integration

`TargetResolver` injects `EntityWatcherProvider` via constructor:

```java
private final EntityWatcherProvider entityWatcherProvider;

@Inject
public TargetResolver(GroupMembershipProvider groupMembershipProvider,
                      EntityWatcherProvider entityWatcherProvider) {
    this.groupMembershipProvider = groupMembershipProvider;
    this.entityWatcherProvider = entityWatcherProvider;
}
```

New case in the switch:

```java
case ENTITY_WATCHERS -> {
    String entityType = target.id().isBlank() ? template.entityType() : target.id();
    String entityId = TemplateResolver.extractField(pojo, template.entityIdField());
    if (entityId != null) {
        Set<String> watchers = entityWatcherProvider.watchersOf(
            entityType, entityId, subscription.tenancyId());
        if (watchers.isEmpty()) {
            LOG.debugf("ENTITY_WATCHERS for %s/%s resolved to no watchers", entityType, entityId);
        }
        recipients.addAll(watchers);
    } else {
        LOG.warnf("ENTITY_WATCHERS target: entityIdField '%s' resolved to null on %s",
            template.entityIdField(), pojo.getClass().getSimpleName());
    }
}
```

`target.id()` = entity type override. If blank, falls back to `template.entityType()`.

**ENTITY_WATCHERS target convention:** For ENTITY_WATCHERS, `target.id()` is an entity-type override, not an entity identifier. Pass `""` (blank string) to use the template's default `entityType()`. This differs from USER/GROUP/EVENT_FIELD where `id()` is a required, meaningful identifier. The blank-string convention avoids making `id()` nullable (which would weaken the API contract for the three types that require it) or adding a field that only one type uses.

---

## 6. Deferred Concerns — Issues to File

- **Preference validation for BUFFER_FOR_DIGEST:** When a user selects `BUFFER_FOR_DIGEST` as their quiet hours action, validate that at least one channel has a digest schedule configured. Without this validation, the user opts into buffering but notifications are suppressed (with WARN log). This is a settings-layer concern outside the dispatch batch — file as a separate issue.
- **Secondary index for `pendingKeysForUser`:** The `InMemoryDigestBuffer.pendingKeysForUser()` implementation scans all pending keys with a stream filter. For deployments with many concurrent users, a `ConcurrentHashMap<UserKey, Set<DigestBufferKey>>` secondary index would make this O(channels per user). Profile before optimizing — file as a performance issue if measurements warrant it.

---

## 7. PLATFORM.md Update

Add notification pipeline entry to the Capability Ownership table:

```
| Notification pipeline (subscriptions, dispatch, digest, preferences, suppression) | `casehub-platform` | SubscriptionEngine + NotificationDispatcher + DigestFlushScheduler + ChannelRouter + TargetResolver + SuppressionEvaluator. SPIs in platform-api: NotificationStore, SubscriptionStore, NotificationPreferenceStore, SuppressionStore, DigestBuffer, DeliveryChannelRegistry, EntityWatcherProvider, EventTypeRegistry. Submodules: notifications/ (REST+SSE), subscriptions/ (engine+REST), notification-dispatch/ (pipeline), notification-settings-inmem/, notification-settings-jpa/, notifications-inmem/, notifications-jpa/, subscriptions-inmem/, subscriptions-jpa/. |
```
