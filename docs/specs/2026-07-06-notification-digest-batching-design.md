# Notification Digest and Batching — Design Spec

**Issue:** casehubio/platform#144
**Epic:** #147 (Notification System)
**Date:** 2026-07-06

## Problem

Without digesting, a user subscribed to "all comments on my work items" gets 50 individual
emails when a discussion thread heats up. External channels (email, SMS, push) need
timer-driven aggregation to collapse multiple notifications into periodic summaries.

## Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Interception point | After channel routing, before delivery | NotificationInput is fully resolved (no POJO dependency), channels are known, mute already filtered |
| Digest scope | Per-user-per-channel | One digest email per user, not per subscription. Matches Gmail/GitHub/Slack. Per-subscription would produce N emails — the opposite of reducing noise |
| Schedule model | Sealed interface: `Interval(Duration)` + `DailyAt(LocalTime, ZoneId)` | Covers the two real use cases with type safety. Weekly deferred as third variant |
| Buffer persistence | In-memory v1, SPI enables future JPA | Items are already in the in-app inbox. Buffer loss on restart = missed external digest, not lost notifications |
| URGENT bypass | In ChannelRouter — URGENT always routes as immediate | Routing decision, not dispatch decision. Router is the single source of routing truth |
| Digest preference location | Extend `ChannelPreference` with nullable `digestSchedule` | Same record, same merge pattern with channel descriptor defaults. null = immediate delivery |

## Data Model Changes (platform-api)

### New Types — `io.casehub.platform.api.delivery`

#### `DigestSchedule` (sealed interface)

```java
public sealed interface DigestSchedule {
    boolean isFlushDue(Instant oldestPending, Instant lastFlush, Instant now);

    record Interval(Duration period) implements DigestSchedule {
        private static final Duration MIN_PERIOD = Duration.ofMinutes(1);
        public Interval {
            Objects.requireNonNull(period, "period");
            if (period.compareTo(MIN_PERIOD) < 0)
                throw new IllegalArgumentException("period must be >= " + MIN_PERIOD);
        }
        @Override
        public boolean isFlushDue(Instant oldestPending, Instant lastFlush, Instant now) {
            return !oldestPending.plus(period).isAfter(now);
        }
    }
    record DailyAt(LocalTime time, ZoneId timezone) implements DigestSchedule {
        public DailyAt {
            Objects.requireNonNull(time, "time");
            Objects.requireNonNull(timezone, "timezone");
        }
        @Override
        public boolean isFlushDue(Instant oldestPending, Instant lastFlush, Instant now) {
            Instant todayTarget = now.atZone(timezone).with(time).toInstant();
            return !now.isBefore(todayTarget) && lastFlush.isBefore(todayTarget);
        }
    }
}
```

Jackson serialization via `@JsonTypeInfo(use = Id.NAME, property = "type")` and `@JsonSubTypes`:
```json
{"type": "interval", "period": "PT4H"}
{"type": "daily_at", "time": "09:00", "timezone": "Europe/London"}
```

#### `DigestBufferKey` (record)

```java
public record DigestBufferKey(String userId, String tenancyId, String channelId) {
    public DigestBufferKey {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(tenancyId, "tenancyId");
        Objects.requireNonNull(channelId, "channelId");
    }
}
```

#### `DigestSummary` (record)

```java
public record DigestSummary(
    String userId, String tenancyId, String channelId,
    List<NotificationInput> notifications,
    Instant periodStart, Instant periodEnd
) {
    public DigestSummary {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(tenancyId, "tenancyId");
        Objects.requireNonNull(channelId, "channelId");
        Objects.requireNonNull(notifications, "notifications");
        if (notifications.isEmpty())
            throw new IllegalArgumentException("notifications must not be empty");
        notifications = List.copyOf(notifications);
    }
}
```

#### `DigestBuffer` (SPI)

```java
public interface DigestBuffer {
    void add(DigestBufferKey key, NotificationInput notification);
    List<NotificationInput> drain(DigestBufferKey key);
    Set<DigestBufferKey> pendingKeys();
    Optional<Instant> oldestPendingTimestamp(DigestBufferKey key);
}
```

Buffer is intentionally dumb — stores notifications, reports pending state. The flush
scheduler owns scheduling intelligence (last-flush tracking, next-flush computation).

### Modified Records

#### `ChannelPreference` — add nullable `digestSchedule`

```java
public record ChannelPreference(
    boolean enabled,
    NotificationSeverity minSeverity,
    DigestSchedule digestSchedule
) {
    public ChannelPreference {
        Objects.requireNonNull(minSeverity, "minSeverity");
    }
    public boolean isDigested() { return digestSchedule != null; }
}
```

Breaking change. Existing JSON rows deserialize with `digestSchedule = null` (immediate
delivery) — behavior unchanged without explicit user action.

#### `DeliveryChannelDescriptor` — add nullable `defaultDigestSchedule`

```java
public record DeliveryChannelDescriptor(
    String channelId, String displayName, boolean external,
    boolean defaultEnabled, NotificationSeverity defaultMinSeverity,
    DigestSchedule defaultDigestSchedule
)
```

In-app registers with `defaultDigestSchedule = null`. External channels set a default
or null (immediate by default, user can enable digest).

#### `NotificationDeliverer` — add `deliverDigest` default method

```java
default DeliveryResult deliverDigest(DigestSummary summary) {
    NotificationInput collapsed = new NotificationInput(
        summary.userId(), summary.tenancyId(),
        summary.notifications().size() + " new notifications",
        null, "digest", NotificationSeverity.INFO, null,
        summary.notifications().getFirst().source());
    return deliver(collapsed);
}
```

Default collapses to single notification. Source attribution is lossy — uses the first
notification's `NotificationSource`. This is a basic fallback; deliverers wanting rich
digest formatting (grouped HTML email, Slack sections) or accurate per-notification
source tracking must override this method. `DigestSummary` enforces a non-empty
notification list, so `getFirst()` is always safe.

## Pipeline Flow

### Current (immediate delivery)

```
SubscriptionMatched → NotificationDispatcher.onMatch(@ObservesAsync)
  → per user:
    → suppression (mute → drop)
    → template resolution → NotificationInput
    → channel routing → Set<ResolvedChannel>
    → per channel: if suppressed skip, else deliver
```

### New (digest-aware delivery)

```
SubscriptionMatched → NotificationDispatcher.onMatch(@ObservesAsync)
  → per user:
    → suppression (mute → drop)
    → template resolution → NotificationInput
    → channel routing → Set<ResolvedChannel> (now with digested flag)
    → per channel:
        if digested   → digestBuffer.add(key, input)
        if suppressed → skip
        else          → deliver immediately
```

Plus scheduled flush:
```
@Scheduled(every="1m") → DigestFlushScheduler.tick()
  → for each pendingKey in digestBuffer:
    → look up user's digest schedule
    → check if flush is due (schedule.isFlushDue — polymorphic)
    → check user-level suppression (evaluateUserLevel → snooze/quiet hours → defer)
    → drain buffer (skip if empty) → deliverer.deliverDigest(summary)
```

### Digest and Suppression Interaction

**At buffer time (event fires):**
- Muted → drop (all channels) — already filtered before channel routing
- Digested channel → always buffer (snooze/quiet hours irrelevant — not delivering now)
- Suppressed non-digest channel → skip (current behavior)
- Otherwise → deliver immediately

**At flush time (scheduler fires):**
- Snoozed → defer flush (leave in buffer, try next tick)
- Quiet hours → defer flush
- Otherwise → drain and deliver digest

Buffering is unconditional for digest channels. Suppression gates the flush, not the buffer.

### ResolvedChannel — add `digested` flag

```java
public record ResolvedChannel(
    String channelId, NotificationDeliverer deliverer,
    boolean suppressed, boolean digested
)
```

### ChannelRouter — digest routing logic

```java
final DigestSchedule effectiveDigest;
if (userPref != null && userPref.digestSchedule() != null) {
    effectiveDigest = userPref.digestSchedule();
} else {
    effectiveDigest = descriptor.defaultDigestSchedule();
}

final boolean digested = descriptor.external()
    && effectiveDigest != null
    && severity != NotificationSeverity.URGENT;
```

Three conditions: external + has schedule + not URGENT. Router is the single routing authority.

## DigestFlushScheduler

`@ApplicationScoped` in `notification-dispatch/`. Injects `DigestBuffer`,
`NotificationPreferenceStore`, `SuppressionStore`, `SuppressionEvaluator`,
`DeliveryChannelRegistry`.

### SuppressionEvaluator — user-level evaluation

At flush time the scheduler checks only user-level suppression (snooze, quiet hours).
Entity-level mute was already evaluated at buffer time. The existing `evaluate()` method
requires entity parameters that don't exist for a heterogeneous digest. New method:

```java
public SuppressionResult evaluateUserLevel(final Optional<Snooze> activeSnooze,
                                           final QuietHours quietHours) {
    return new SuppressionResult(false, checkSnoozed(activeSnooze), checkQuietHours(quietHours));
}
```

Returns `isMuted = false` (mute is not applicable at flush time). `checkSnoozed` and
`checkQuietHours` are existing private methods promoted to package-private visibility.

### Tick processing

```java
@Scheduled(every = "${casehub.notification.digest.tick-interval:1m}")
void tick() {
    for (DigestBufferKey key : digestBuffer.pendingKeys()) {
        try {
            processKey(key);
        } catch (Exception e) {
            LOG.warnf(e, "Digest flush failed for key %s", key);
        }
    }
}
```

Per-key error isolation prevents one broken delivery from blocking all subsequent keys.
Matches the per-channel try/catch pattern in `NotificationDispatcher.dispatchToUser()`.

Per-key processing:

1. **Look up schedule** from `preferenceStore.get(userId, tenancyId)` →
   `channelDefaults.get(channelId).digestSchedule()`. If null (user disabled digest since
   buffering), flush immediately — no orphaned items.

2. **Check flush-due** — polymorphic on the schedule variant:
   ```java
   Instant oldest = digestBuffer.oldestPendingTimestamp(key).orElse(now);
   Instant lastFlush = lastFlushTimes.getOrDefault(key, Instant.EPOCH);
   if (!schedule.isFlushDue(oldest, lastFlush, now)) return;
   ```
   Each `DigestSchedule` variant implements its own flush-due logic. Adding `WeeklyAt`
   (#160) requires only a new variant with its `isFlushDue` implementation — no scheduler
   changes.

3. **Check suppression** — snooze and quiet hours defer the flush:
   ```java
   var result = suppressionEvaluator.evaluateUserLevel(activeSnooze, quietHours);
   if (result.isSnoozed() || result.quietHoursActive()) return;
   ```

4. **Drain and deliver:**
   ```java
   Instant periodStart = lastFlushTimes.getOrDefault(key,
       digestBuffer.oldestPendingTimestamp(key).orElse(now));
   List<NotificationInput> items = digestBuffer.drain(key);
   if (items.isEmpty()) return;
   var summary = new DigestSummary(userId, tenancyId, channelId,
       items, periodStart, Instant.now());
   channelRegistry.resolveDeliverer(channelId)
       .ifPresent(d -> d.deliverDigest(summary));
   lastFlushTimes.put(key, Instant.now());
   ```

   `periodStart` uses the last flush time for contiguous digest periods, falling back to
   the oldest pending notification timestamp on first flush or post-restart. Captured before
   `drain()` since drain clears the buffer. Empty drain is guarded — possible if items were
   consumed between `pendingKeys()` and `drain()` (future multi-instance deployments).

### Scheduler state

`lastFlushTimes: ConcurrentHashMap<DigestBufferKey, Instant>` — in-memory, resets on restart.
Post-restart behavior: `Interval` computes from `oldestPendingTimestamp` (still correct);
`DailyAt` flushes on next tick if target time has passed today (acceptable).

### Failed delivery

Items are drained before delivery. If delivery fails, they're lost from the buffer. They
are still in the in-app inbox. This is a v1 tradeoff — #154 (guaranteed delivery) tracks
the production-grade solution with retry/dead-letter.

### Observability

The scheduler is a timer-driven background process — logging is essential for operators
diagnosing "why didn't my digest arrive?"

**Flush decisions** — `LOG.infof` per key when flushing: user, channel, item count, period.
**Suppression deferrals** — `LOG.debugf` when flush deferred due to snooze or quiet hours.
**Delivery failures** — `LOG.warnf` matching the existing `NotificationDispatcher` pattern.
**Edge cases** — `LOG.debugf` for orphan drain (schedule removed since buffering), empty
drain (items consumed between `pendingKeys()` and `drain()`), buffer eviction (max size
exceeded).

Structured metrics (buffer depth gauges, flush latency histograms) are deferred to the
persistent buffer implementation (#158) where they have operational value.

## Module Structure

| Module | Change |
|--------|--------|
| `platform-api/` | New: `DigestSchedule`, `DigestBufferKey`, `DigestSummary`, `DigestBuffer`. Modified: `ChannelPreference`, `DeliveryChannelDescriptor`, `NotificationDeliverer` |
| `platform/` | New: `NoOpDigestBuffer @DefaultBean` |
| `notification-dispatch/` | New: `InMemoryDigestBuffer @ApplicationScoped`, `DigestFlushScheduler`. Modified: `NotificationDispatcher`, `ChannelRouter`, `ResolvedChannel`. New dep: `quarkus-scheduler` |
| `notification-settings-inmem/` | Handle new `ChannelPreference` field (pass null digestSchedule) |
| `notification-settings-jpa/` | Handle new `ChannelPreference` field in JSON column; Jackson config for `DigestSchedule` sealed interface |
| `notifications/` (REST) | Expose `digestSchedule` in existing preference endpoints |

No new modules. `InMemoryDigestBuffer` lives in `notification-dispatch/` following the
`InMemoryDeliveryChannelRegistry` precedent.

### NoOpDigestBuffer in `platform/`

```java
@DefaultBean
@ApplicationScoped
public class NoOpDigestBuffer implements DigestBuffer {
    public void add(DigestBufferKey key, NotificationInput n) { }
    public List<NotificationInput> drain(DigestBufferKey key) { return List.of(); }
    public Set<DigestBufferKey> pendingKeys() { return Set.of(); }
    public Optional<Instant> oldestPendingTimestamp(DigestBufferKey key) { return Optional.empty(); }
}
```

Per `noop-registry-must-not-fire-cdi-events` protocol: completely silent.

### InMemoryDigestBuffer in `notification-dispatch/`

`@ApplicationScoped` (Tier 2 in CDI priority ladder, beats `@DefaultBean` automatically).
`ConcurrentHashMap<DigestBufferKey, BufferEntry>` with `compute()` for per-key atomicity.
Data lost on restart — acceptable (in-app inbox has the items).

Buffer size capped per key at `casehub.notification.digest.max-buffer-size` (default: 500).
When exceeded, oldest items are evicted and a warning is logged. Eviction is silent to
downstream — `drain()` returns only surviving items and `DigestSummary` carries no eviction
metadata. Evicted items remain in the in-app inbox. Eviction metadata (`DrainResult` with
`evictedCount`) deferred to the persistent buffer (#158) where it has operational value.

### Breaking Change Migration

Constructor call sites for `ChannelPreference` and `DeliveryChannelDescriptor` across:
- `notification-dispatch/` (ChannelRouter tests, InAppNotificationDeliverer)
- `notification-settings-inmem/` (InMemoryNotificationPreferenceStore)
- `notification-settings-jpa/` (entity mapper)
- `notifications/` (REST endpoint mappers)
- Test classes

All within platform repo. Mechanical: add `null` for digestSchedule/defaultDigestSchedule.

## Testing Strategy

### Unit tests (no Quarkus)

- `DigestSchedule` — construction validation: null rejection, below-minimum period
  rejection; `isFlushDue` for Interval and DailyAt with controlled `Instant` inputs
- `ChannelRouter` — digest flag: external+schedule+non-URGENT → true; internal → false;
  URGENT → false; no schedule → false; user pref overrides channel default
- `DigestFlushScheduler` — flush-due via polymorphic `isFlushDue`; suppression deferral
  via `evaluateUserLevel`; orphan drain when schedule removed; empty drain skip
- `InMemoryDigestBuffer` — add/drain/pendingKeys/oldestTimestamp; concurrent add safety;
  eviction when max buffer size exceeded
- `NotificationDeliverer.deliverDigest()` — SPI default method contract test per
  `spi-default-method-contract-test` protocol: anonymous implementation providing only
  `channelId()` and `deliver()`, verify digest collapses to single notification with
  count-based title and delegates to `deliver()`
- `SuppressionEvaluator.evaluateUserLevel()` — snoozed returns `isSnoozed = true`,
  quiet hours returns `quietHoursActive = true`, always returns `isMuted = false`

### Integration tests (@QuarkusTest)

- End-to-end: SubscriptionMatched with digest user → in-app immediate, external buffered →
  advance clock → digest delivered
- URGENT bypass: URGENT + digest enabled → immediate to all channels
- Snooze deferral: buffer items, activate snooze → flush deferred; deactivate → flush fires
- Preference change: buffer with digest, switch to immediate → next tick flushes immediately

## Deferred Concerns

Filed as GitHub issues — not in scope for this branch:

| # | Concern | Why deferred |
|---|---------|-------------|
| #158 | Persistent digest buffer (`digest-jpa/`) | v1 accepts buffer loss on restart; in-app inbox has the items |
| #159 | Digest groupBy preference | Deliverer-decided grouping sufficient for v1 |
| #160 | Weekly schedule variant (`WeeklyAt`) | Rare for notification digests |
| #161 | Digest status REST endpoint | UI concern, not pipeline |
| #162 | Quiet hours → digest integration | Buffer suppressed items instead of dropping — behavior change |
