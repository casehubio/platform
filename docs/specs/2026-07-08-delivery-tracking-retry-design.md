# Delivery Tracking and Guaranteed Retry — Design Spec

**Issue:** #154  
**Date:** 2026-07-08  
**Status:** Approved

---

## Problem

The notification dispatch pipeline (`NotificationDispatcher`) delivers to external channels
via `NotificationDeliverer.deliver()` but treats the `DeliveryResult` as fire-and-forget —
failures are logged and discarded. There is no record of what was delivered, when, or whether
it succeeded. Failed deliveries to external channels (email, SMS, push) are permanently lost.

## Design Decisions

### Separation of concerns

Two independent problems, solved independently:

1. **Delivery tracking** — universal. Every `DeliveryResult` from every channel is persisted
   as a `DeliveryAttempt` record. This is observability — channel health, audit trail,
   delivery history per user.

2. **Guaranteed retry** — per-channel policy. Each `DeliveryChannelDescriptor` declares a
   `guaranteedMinSeverity` threshold. Failed deliveries at or above the threshold are retried
   with exponential backoff. Below the threshold, failures are recorded but not retried.

### Why not a transactional outbox

The classic outbox pattern solves the dual-write problem — atomically writing a business
record and an event intent. Our architecture doesn't have this problem:
`NotificationDispatcher.onMatch()` runs on a CDI async thread (`@ObservesAsync`), already
decoupled from any transaction. External channel deliveries have no transactional context
to piggyback on. What we need is a persistent retry queue, not an outbox.

### Why not severity-gated tracking

The issue originally proposed tracking only URGENT notifications. Tracking should be
universal because: (a) channel health metrics require visibility across all severities,
(b) the per-record cost is trivial (one row per delivery), and (c) selective tracking
creates a blind spot that makes debugging harder.

### Engagement tracking deferred

Open/engagement tracking (email opened, push tapped) requires external callbacks from
channel providers — a different concern from delivery tracking. The `DeliveryAttempt`
schema is extensible for future engagement fields, but the callback infrastructure is
out of scope. **Follow-up:** #170.

### Issue #154 acceptance criteria divergence

Issue #154's original acceptance criteria reference a transactional outbox and dead-letter
queue. This spec diverges intentionally:

- **Transactional outbox** → rejected (§ Why Not a Transactional Outbox). The persistent
  retry queue achieves the same guaranteed delivery without requiring transactional context.
- **Dead-letter queue** → `EXPIRED` status serves this role. EXPIRED records are durable,
  queryable via `DeliveryAttemptStore.find()`, and inspectable for manual remediation.
  A programmatic redrive mechanism (resetting EXPIRED → RETRYING) is a future extension.
- **Engagement tracking** → deferred (#170).

Issue #154's acceptance criteria should be updated to reflect the design decision. The
persistent retry queue satisfies the underlying requirement (no permanent loss of
retry-eligible failed deliveries) via a different mechanism than originally proposed.

### Residual failure mode — simultaneous channel + DB failure

If an external delivery fails AND the `DeliveryAttemptStore.store()` call also fails
(e.g., DB outage), the failure is logged but never persisted. The retry processor will
never see it. This is an accepted residual risk:

- The failure window is very narrow (simultaneous channel failure + DB outage)
- DB outages are operationally monitored and will trigger alerts independently
- The log warning provides a reconciliation trail for manual recovery
- Eliminating this window would require a local write-ahead log, adding significant
  complexity for a failure mode that requires two independent systems to be down
  simultaneously

For digest deliveries, this risk is further mitigated by pre-persisting the
`DeliveryAttempt` before delivery (§ DigestFlushScheduler Changes).

---

## SPI Layer (`platform-api/`)

New types in `io.casehub.platform.api.delivery`:

### `DeliveryType` — enum

`IMMEDIATE` | `DIGEST`

Explicit type discriminator for `DeliveryAttempt` payload. Determines deserialization
target: `IMMEDIATE` → `NotificationInput`, `DIGEST` → `DigestSummary`.

### `DeliveryAttempt` — record

| Field | Type | Purpose |
|-------|------|---------|
| `id` | `String` | UUIDv7 — time-ordered |
| `notificationId` | `String` | Link to Notification (nullable — null for DIGEST deliveries) |
| `channelId` | `String` | Which channel was used |
| `userId` | `String` | Recipient |
| `tenancyId` | `String` | Tenant isolation |
| `deliveryType` | `DeliveryType` | IMMEDIATE or DIGEST — determines payload deserialization |
| `status` | `DeliveryStatus` | Lifecycle state |
| `attemptCount` | `int` | How many times delivery was attempted |
| `createdAt` | `Instant` | When the attempt was first created |
| `lastAttemptedAt` | `Instant` | Most recent attempt timestamp (nullable — null before first delivery attempt) |
| `deliveredAt` | `Instant` | Successful delivery timestamp (nullable) |
| `nextRetryAt` | `Instant` | When retry processor should next attempt (nullable) |
| `failureReason` | `String` | Last failure message (nullable) |
| `payload` | `String` | Serialized delivery payload (Jackson JSON) — `NotificationInput` for IMMEDIATE, `DigestSummary` for DIGEST. Self-contained for retry without joins |

### `DeliveryStatus` — enum

`DELIVERED` | `FAILED` | `RETRYING` | `EXPIRED`

- `DELIVERED` — successfully delivered
- `FAILED` — delivery failed, severity below retry threshold (permanent)
- `RETRYING` — delivery failed, queued for retry
- `EXPIRED` — retries exhausted

### `DeliveryAttemptStore` — SPI

```java
public interface DeliveryAttemptStore {
    void store(DeliveryAttempt attempt);
    void update(DeliveryAttempt attempt);
    List<DeliveryAttempt> claimRetryable(Instant now, int batchSize);
    DeliveryAttemptPage find(DeliveryAttemptQuery query);
    List<DeliveryAttempt> findByNotificationId(String notificationId);
}
```

- `store()` — persist a new attempt
- `update()` — update status, attemptCount, timestamps after retry
- `claimRetryable(now, batchSize)` — atomically claim up to `batchSize` attempts where
  `status = RETRYING AND nextRetryAt <= now`. As part of the claim transaction, advances
  each claimed record's `nextRetryAt` by the configured claim timeout (default: 5m). This
  serves two purposes: (1) concurrent callers on other nodes skip these records via
  `SKIP LOCKED`, and (2) if processing exceeds the tick interval, subsequent ticks on any
  node see `nextRetryAt` in the future and skip them. If the claimant crashes before
  updating the record, it becomes re-claimable after the claim timeout expires. JPA
  implementation: `SELECT ... FOR UPDATE SKIP LOCKED` + `UPDATE nextRetryAt = now + claimTimeout`
  in a single transaction.
- `find(query)` — query by userId/tenancyId/channelId/status with cursor pagination
- `findByNotificationId()` — per-notification delivery history across channels

### `DeliveryAttemptQuery` — record

| Field | Type | Purpose |
|-------|------|---------|
| `userId` | `String` | Filter by recipient (nullable) |
| `tenancyId` | `String` | Tenant isolation (required) |
| `channelId` | `String` | Filter by channel (nullable) |
| `status` | `DeliveryStatus` | Filter by status (nullable) |
| `cursor` | `String` | Opaque pagination cursor (nullable) |
| `limit` | `int` | Page size |

### `DeliveryAttemptPage` — record

| Field | Type |
|-------|------|
| `attempts` | `List<DeliveryAttempt>` |
| `nextCursor` | `String` (nullable) |

### `DeliveryExhausted` — CDI event record

```java
public record DeliveryExhausted(DeliveryAttempt attempt) {}
```

Fired when retries are exhausted (status transitions to EXPIRED). No observer required
in this issue — the event is the extension point for future channel fallback or admin
alerting.

### `DeliveryChannelDescriptor` modification

Add one field:

```java
NotificationSeverity guaranteedMinSeverity  // nullable
```

- `null` → no retry (fire-and-forget). Default for in-app.
- `WARNING` → retry WARNING and URGENT failures. Typical for email.
- `URGENT` → retry only URGENT failures. Typical for SMS.

Existing constructor gains the new parameter. All existing call sites
(`InAppNotificationDeliverer.register()`) pass `null`.

---

## Dispatcher Integration (`notification-dispatch/`)

### `ResolvedChannel` modification

Add one field to carry the retry threshold from the routing phase:

```java
NotificationSeverity guaranteedMinSeverity  // nullable
```

Populated by `ChannelRouter` from the `DeliveryChannelDescriptor`. Avoids a second
registry lookup in the dispatcher.

### `NotificationDispatcher` changes

The delivery loop (currently fire-and-forget) gains a tracking write after every
`deliver()` call:

**Current:**
```
deliver() → log result → continue
```

**New:**
```
deliver() → write DeliveryAttempt → continue
```

Logic per channel delivery:

1. Call `channel.deliverer().deliver(notificationInput)`
2. If success → store `DeliveryAttempt(deliveryType=IMMEDIATE, status=DELIVERED, deliveredAt=now)`
3. If failure:
   a. Read `guaranteedMinSeverity` from `ResolvedChannel`
   b. If notification severity >= threshold → store with `status=RETRYING`,
      `nextRetryAt = now + baseDelay`, `attemptCount=1`
   c. If below threshold or threshold is null → store with `status=FAILED`

**Non-transactional:** The `DeliveryAttemptStore.store()` write is independent from
`NotificationStore.store()`. If the tracking write fails (DB down), the delivery still
happened — a logged warning covers this edge case. The pipeline is never blocked by
tracking failures. See § Residual Failure Mode for analysis of the reverse scenario
(delivery fails + DB down).

### `DigestFlushScheduler` changes

After `digestBuffer.drain(key)`, the scheduler writes a `DeliveryAttempt` **before**
attempting delivery. This pre-persist ensures the serialized payload survives even if
the subsequent delivery fails — the retry processor can recover it.

Flow:

1. Drain buffer → get items
2. Build `DigestSummary` and serialize payload (Jackson)
3. Determine retry eligibility: max severity across all notifications in the digest
   vs. `guaranteedMinSeverity` from the channel descriptor
4. Store `DeliveryAttempt` with `deliveryType=DIGEST`, `notificationId=null`,
   serialized digest payload, and:
   - If retry-eligible → `status=RETRYING`, `attemptCount=0`,
     `nextRetryAt = null`
   - If not retry-eligible → `status=FAILED`
5. Attempt delivery via `deliverer.deliverDigest(summary)`
6. If success → update `DeliveryAttempt` to `status=DELIVERED`, `deliveredAt=now`
7. If failure → set `nextRetryAt = now + baseDelay`, `attemptCount=1`
   (makes the record visible to the retry processor after baseDelay)

**Why `nextRetryAt = null` on pre-persist:** The `claimRetryable()` query selects
`WHERE nextRetryAt <= now` — null does not satisfy this comparison in SQL (`NULL <= x`
evaluates to NULL, not TRUE). The record exists (payload is safe) but is invisible to the
retry processor during the delivery attempt. This prevents a timing race: if the initial
delivery takes longer than `baseDelay`, the retry processor cannot claim and redeliver
the record while the scheduler is still delivering it.

**Digest severity rule:** A digest's effective severity for retry eligibility is the
maximum `NotificationSeverity` of any notification in the `DigestSummary.notifications()`
list. This preserves the guaranteed delivery contract for URGENT items that happen to be
in a digest during quiet hours buffering.

If the initial `DeliveryAttemptStore.store()` call fails (DB down), the items are lost
from both the buffer (drained) and the tracking store. This is the same residual risk
described in § Residual Failure Mode.

---

## Retry Processor (`notification-dispatch/`)

### `DeliveryRetryProcessor` — `@ApplicationScoped`

```
@Scheduled(every = "${casehub.delivery.retry.tick-interval:30s}")
void tick():
    batch = deliveryAttemptStore.claimRetryable(now, batchSize)
    for attempt in batch:
        try:
            deliverer = channelRegistry.resolveDeliverer(attempt.channelId())
            if deliverer is absent:
                update(attempt, status=EXPIRED, failureReason="channel not registered")
                event.fireAsync(new DeliveryExhausted(attempt))
                continue
            if attempt.deliveryType() == IMMEDIATE:
                input = deserialize(attempt.payload(), NotificationInput.class)
                result = deliverer.deliver(input)
            else:
                summary = deserialize(attempt.payload(), DigestSummary.class)
                result = deliverer.deliverDigest(summary)

            if result.success():
                update(attempt, status=DELIVERED, deliveredAt=now)
            else:
                advanceOrExpire(attempt, result.failureReason())
        catch Exception e:
            log.warn("Retry failed for attempt {}", attempt.id(), e)
            advanceOrExpire(attempt, e.getMessage())

void advanceOrExpire(attempt, failureReason):
    newCount = attempt.attemptCount() + 1
    if newCount > maxRetries:
        update(attempt, status=EXPIRED, attemptCount=newCount)
        event.fireAsync(new DeliveryExhausted(attempt))
    else:
        nextRetry = computeBackoff(newCount)
        update(attempt, status=RETRYING, attemptCount=newCount,
               nextRetryAt=nextRetry, failureReason=failureReason)
```

### Backoff configuration

| Property | Default | Purpose |
|----------|---------|---------|
| `casehub.delivery.retry.tick-interval` | `30s` | Polling frequency |
| `casehub.delivery.retry.max-retries` | `5` | Retries before EXPIRED (total attempts = max-retries + 1) |
| `casehub.delivery.retry.base-delay` | `30s` | First retry delay |
| `casehub.delivery.retry.max-delay` | `30m` | Caps exponential growth |
| `casehub.delivery.retry.jitter-ms` | `5000` | Random jitter per retry |
| `casehub.delivery.retry.batch-size` | `50` | Records claimed per tick |
| `casehub.delivery.retry.claim-timeout` | `5m` | Duration to advance `nextRetryAt` when claiming — prevents re-claiming during processing; crash recovery after expiry |

**Formula:** `nextRetryAt = now + min(baseDelay * 2^(attemptCount-1), maxDelay) + random(0, jitterMs)`

Retry sequence with defaults: ~30s, ~60s, ~2m, ~4m, ~8m (+ jitter each).

### Payload serialization

Jackson (Quarkus default). Both `NotificationInput` and `DigestSummary` are Java records —
Jackson handles records natively since 2.12+. The `DeliveryType` field determines the
deserialization target class, eliminating the need for type metadata in the JSON.

Payload schema must be backward-compatible across deployments: in-flight `DeliveryAttempt`
records may span a rolling update. Adding fields with defaults is safe; removing or
renaming fields requires draining the retry queue first.

### Idempotency

External channels that support idempotency keys can use `NotificationInput.source().eventId()`.
The `NotificationDeliverer` SPI is unchanged — idempotency is a channel implementation concern.

---

## Module Structure

### New modules

| Module | Artifact | Purpose | Flyway |
|--------|----------|---------|--------|
| `delivery-tracking-inmem/` | `casehub-platform-delivery-tracking-inmem` | `@Alternative @Priority(100)` volatile `InMemoryDeliveryAttemptStore` | — |
| `delivery-tracking-jpa/` | `casehub-platform-delivery-tracking-jpa` | `@ApplicationScoped` JPA `DeliveryAttemptStore` | V3000 (`classpath:db/delivery-tracking/migration`) |

### `NoOpDeliveryAttemptStore` (`platform/`)

`@DefaultBean` no-op implementation that silently discards writes and returns empty results.
Per `persistence-backend-cdi-priority` protocol (Tier 1), ensures the platform boots without
a delivery tracking module on the classpath.

### `delivery-tracking-inmem/`

- `ConcurrentHashMap` storage
- Size-based eviction via `casehub.delivery.tracking.inmem.max-size` (default: 10000),
  oldest-first eviction per `store-owned-retention-mechanism` protocol
- Test scope for `@QuarkusTest` isolation; compile scope for ephemeral installs
- No quarkus:build goal
- Do NOT combine with delivery-tracking-jpa in same scope

### `delivery-tracking-jpa/`

- Hibernate ORM Panache (blocking)
- PostgreSQL, Flyway V3000
- `claimRetryable()` implemented via `SELECT FOR UPDATE SKIP LOCKED` +
  `UPDATE nextRetryAt = now + claimTimeout` in a single transaction
- `@Scheduled` retention purge:
  - DELIVERED/EXPIRED: `casehub.delivery.tracking.retention-days` (default: 90)
  - FAILED: `casehub.delivery.tracking.failed-retention-days` (default: 365)
  - RETRYING where `nextRetryAt < now - retention-days`: treated as stale, transitioned
    to EXPIRED (catches orphaned records from crashed processors or disabled schedulers)
  - RETRYING where `nextRetryAt IS NULL` and `createdAt < now - claim-timeout`: orphaned
    pre-persist records from crashed digest schedulers, transitioned to EXPIRED
- Store-owned retention per protocol — no shared retention SPI
- No quarkus:build goal
- Indexes: `(status, nextRetryAt)` for retry polling, `(notificationId)` for per-notification
  history, `(userId, tenancyId, createdAt)` for user queries

### Modified modules

| Module | Change |
|--------|--------|
| `platform-api/` | New types: `DeliveryAttempt`, `DeliveryType`, `DeliveryStatus`, `DeliveryAttemptStore`, `DeliveryAttemptQuery`, `DeliveryAttemptPage`, `DeliveryExhausted`. Modified: `DeliveryChannelDescriptor` (+`guaranteedMinSeverity`) |
| `platform/` | New: `NoOpDeliveryAttemptStore` |
| `notification-dispatch/` | Modified: `NotificationDispatcher` (tracking writes), `DigestFlushScheduler` (tracking writes + pre-persist), `ResolvedChannel` (+`guaranteedMinSeverity`), `ChannelRouter` (populates `guaranteedMinSeverity`). New: `DeliveryRetryProcessor` |

---

## What This Is NOT

- Not a transactional outbox — no atomicity requirement with notification storage
- Not engagement tracking — no email open/push tap callbacks (deferred to #170)
- Not a change to the existing best-effort pipeline — INFO/WARNING with no retry threshold
  are tracked but not retried
- Not a REST API for delivery status — queryable via SPI, but no endpoints in this issue
- Not zero-loss delivery — a narrow residual failure window exists when both the external
  channel and DB are simultaneously unavailable (§ Residual Failure Mode)

## Future Extension Points

- **Engagement tracking (#170):** `DeliveryAttempt` can gain `openedAt`, `clickedAt` fields.
  Channel-specific callback handlers observe delivery events and update the record.
- **Channel fallback:** A `@ObservesAsync DeliveryExhausted` observer could escalate
  to a fallback channel (e.g., push failed → try SMS).
- **Admin alerting:** Another `DeliveryExhausted` observer could fire admin notifications
  when a channel's failure rate exceeds a threshold.
- **EXPIRED redrive:** A management operation to reset selected EXPIRED records to RETRYING
  for manual retry. The data model already supports this — only the trigger mechanism is needed.
- **REST endpoints:** A future `delivery-tracking/` API module could expose delivery
  history per notification or per user.
