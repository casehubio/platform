# Delivery Engagement Tracking Design

**Issue:** casehubio/platform#170
**Date:** 2026-07-16
**Status:** Approved

## Problem

The delivery tracking infrastructure (platform#154) records whether notifications
were successfully delivered to external channels — but not whether recipients
interacted with them. Email opens, push notification taps, link clicks, and
dismissals are invisible to the platform. Without engagement visibility, there is
no way to measure notification effectiveness, tune delivery strategies, or surface
"customer read the notification" signals to downstream modules.

## Design Decisions

1. **Channel-agnostic engagement vocabulary** — the platform defines `EngagementType`
   (OPENED, CLICKED, DISMISSED, REPLIED, CONVERTED). Channel implementations map
   provider-specific signals to these types. This keeps the platform independent of
   any specific email/push/SMS provider.

2. **Engagement as DeliveryAttempt extension** — engagement events are children of
   delivery attempts, stored in the same `DeliveryAttemptStore` SPI. Summary
   timestamps (`firstOpenedAt`, `firstClickedAt`) on `DeliveryAttempt` enable fast
   queries without scanning event history. This avoids an artificial concern boundary —
   engagement is an attribute of delivery, not an independent entity.

3. **SPI callback handler + generic endpoint + direct recording** — three paths to
   record engagement, supporting different integration patterns:
   - SPI path: generic REST endpoint delegates to channel-specific `EngagementCallbackHandler`
     for provider payload translation and webhook signature verification
   - Direct path: REST endpoint accepts `EngagementType` + metadata for a known attempt ID
   - Programmatic path: any module can call `DeliveryAttemptStore.recordEngagement()` directly

4. **CDI events on engagement** — `EngagementRecorded` fires via `fireAsync()` on every
   engagement event, consistent with `DeliveryExhausted`, `NotificationCreated`, and
   other state-change events throughout the notification pipeline.

5. **In-app engagement bridge** — in-app read/dismiss flows into the engagement system
   via `@ObservesAsync NotificationStatusChanged`. This is not duplication:
   `Notification.readAt` is notification-level UI state; `EngagementEvent(OPENED)` is
   delivery-level analytics. They serve different subsystems. The bridge ensures
   cross-channel engagement queries include in-app without query-time joins.

6. **Deployment-level opt-in** — engagement tracking is gated by
   `casehub.delivery.engagement.enabled` (default `false`), honouring the privacy-aware
   opt-in requirement from platform#154. When disabled, `EngagementRecorder.record()`
   no-ops, `EngagementCallbackResource` returns `404`, and `InAppEngagementBridge`
   skips observation. The store SPI methods remain available for programmatic use by
   modules that manage their own consent — the gate is at the recorder level, not the
   store level. Per-tenant granularity is deferred (requires per-tenant configuration
   infrastructure not yet present in the platform).

7. **Append-only engagement events, idempotent summaries** — `recordEngagement()` is
   always append; it does not deduplicate. Webhook providers retry on transient failures,
   so duplicate events are possible. The summary fields (`firstOpenedAt`,
   `firstClickedAt`) are idempotent — set only if currently NULL, so retries do not
   corrupt summary state. Handlers that need provider-level deduplication include a
   provider-specific message ID in the `metadata` field; consumers that need exact counts
   deduplicate at query time by metadata key. This keeps the store simple and pushes
   dedup responsibility to where provider semantics are known (the handler) and where
   query requirements are known (the consumer).

## Data Model

### EngagementType (platform-api)

```java
package io.casehub.platform.api.delivery;

public enum EngagementType {
    OPENED,      // user viewed/opened the notification
    CLICKED,     // user clicked a link or action
    DISMISSED,   // user explicitly dismissed
    REPLIED,     // user replied (email reply, chat response)
    CONVERTED    // downstream business outcome attributed to notification
}
```

Channel mapping examples:
- Email tracking pixel loaded → OPENED
- Email link clicked → CLICKED
- Push notification tapped → OPENED
- Push action button pressed → CLICKED
- In-app markRead → OPENED
- In-app dismiss → DISMISSED

### EngagementEvent (platform-api)

```java
package io.casehub.platform.api.delivery;

public record EngagementEvent(
    String id,              // UUIDv7
    String attemptId,       // FK to DeliveryAttempt.id
    String notificationId,  // denormalized from attempt for query convenience
    String channelId,       // denormalized from attempt
    String userId,          // denormalized
    String tenancyId,       // denormalized
    EngagementType type,
    Instant recordedAt,
    String metadata         // JSON — channel-specific (click URL, device info, etc.)
) {}
```

Denormalization is deliberate — engagement queries ("all opens for user X in tenant Y")
must not require a join to `delivery_attempt`.

### DeliveryAttempt (platform-api) — two new fields

```java
public record DeliveryAttempt(
    // ... existing 14 fields unchanged ...
    Instant firstOpenedAt,   // set on first OPENED event, never overwritten
    Instant firstClickedAt   // set on first CLICKED event, never overwritten
) {}
```

Summary timestamps are query accelerators — "was this notification opened?" without
scanning engagement events. Set once by `recordEngagement()`, immutable after that.

### EngagementRecorded CDI event (platform-api)

```java
package io.casehub.platform.api.delivery;

public record EngagementRecorded(EngagementEvent event) {}
```

Fired via `fireAsync()` after successful `recordEngagement()`.

## SPI Changes

### DeliveryAttemptStore — three new methods

```java
public interface DeliveryAttemptStore {
    // ... existing: store, update, claimRetryable, find, findByNotificationId ...

    void recordEngagement(EngagementEvent event);

    List<EngagementEvent> findEngagementsByAttemptId(String attemptId);

    List<EngagementEvent> findEngagementsByNotificationId(String notificationId);
}
```

`recordEngagement()` stores the event AND updates `firstOpenedAt`/`firstClickedAt` on
the parent `DeliveryAttempt` if this is the first event of that type. Atomic in JPA
(single transaction), best-effort in the in-memory implementation.

### RawEngagement (platform-api)

```java
package io.casehub.platform.api.delivery;

public record RawEngagement(
    String attemptId,       // resolved by the handler from provider payload
    EngagementType type,
    String metadata         // JSON — channel-specific (click URL, device info, etc.)
) {}
```

Lightweight intermediate type representing what a channel handler can determine from
a provider webhook payload. The handler is responsible for mapping provider-specific
message identifiers to platform attempt IDs — it owns the provider identity mapping.
The `EngagementCallbackResource` or `EngagementRecorder` then handles infrastructure
concerns: looking up the attempt, generating the UUIDv7, denormalizing
`notificationId`/`userId`/`tenancyId`, and constructing the full `EngagementEvent`.

### EngagementCallbackHandler SPI (platform-api)

```java
package io.casehub.platform.api.delivery;

public interface EngagementCallbackHandler {

    String channelId();

    List<RawEngagement> translate(String rawPayload);
}
```

Channel modules implement this to translate provider-specific webhook payloads into
`RawEngagement` records. The handler resolves provider message IDs to platform attempt
IDs internally — the `attemptId` parameter was removed from `translate()` because the
callback endpoint has no attempt ID (webhooks arrive at
`/delivery/engagement/callback/{channelId}` with provider-specific payloads). Returns
a list because one provider callback can contain multiple events (e.g. SendGrid sends
batches). Webhook signature verification happens inside `translate()` — throw on
invalid signature.

Implementations are CDI beans, discovered via `Instance<EngagementCallbackHandler>`.

## Callback Infrastructure

### EngagementCallbackResource (notification-dispatch)

JAX-RS resource at `@Path("/delivery/engagement")`:

**`POST /delivery/engagement/callback/{channelId}`** — SPI path.
`@Consumes({"application/json", "application/x-www-form-urlencoded"})` — webhook
providers vary; accepting both covers SendGrid (JSON), Twilio (form-encoded), and
others. The raw body is captured as `String` and passed to `translate()`. Looks up
`EngagementCallbackHandler` by channelId from CDI `Instance`. Calls `translate()` to
get `RawEngagement` records. For each, looks up the parent `DeliveryAttempt` by
attempt ID. If the attempt does not exist (handler bug, retention-purged, or
never-persisted due to original store failure), skip that event with a debug-level log
— consistent with the bridge's "no attempt found → skip silently" pattern. For valid
attempts, calls `EngagementRecorder.record(attempt, type, metadata)`. Returns `404` if
no handler registered for the channel, `200 OK` with empty body on success (providers
expect acknowledgement, not a response body). Returns `404` if engagement tracking is
disabled.

**`POST /delivery/engagement/{attemptId}`** — direct path. Accepts JSON body with
`type` (EngagementType) and optional `metadata` (JSON string). Looks up the attempt;
returns `404` if not found. Enforces tenancy isolation (`attempt.tenancyId()` must
match `CurrentPrincipal.tenancyId()`). Calls `EngagementRecorder.record(attempt, type,
metadata)`. Returns `404` if engagement tracking is disabled.

### EngagementRecorder (notification-dispatch)

```java
@ApplicationScoped
public class EngagementRecorder {

    void record(DeliveryAttempt attempt, EngagementType type, String metadata);
}
```

True convergence point for all three recording paths. Accepts the minimal inputs that
each caller has after its specific lookup/validation, then handles all construction
internally:
1. Generate UUIDv7 for the event ID
2. Extract denormalized fields from the attempt (`notificationId`, `channelId`,
   `userId`, `tenancyId`)
3. Construct the full `EngagementEvent`
4. Store via `DeliveryAttemptStore.recordEngagement()` (which internally updates
   `firstOpenedAt`/`firstClickedAt` on the parent attempt if this is the first event
   of that type)
5. Fire `EngagementRecorded` via `fireAsync()`

No caller constructs `EngagementEvent` directly — the recorder owns event construction,
ensuring consistent ID generation and field extraction across all paths.

When `casehub.delivery.engagement.enabled` is `false`, `record()` returns immediately
without constructing, storing, or firing events.

Both REST paths and the in-app bridge converge here.

## In-App Engagement Bridge

### InAppEngagementBridge (notification-dispatch)

```java
@ApplicationScoped
public class InAppEngagementBridge {

    void onStatusChanged(@ObservesAsync NotificationStatusChanged event);
}
```

Observes `NotificationStatusChanged` (already fired by `NotificationStore`
implementations). Maps: READ → OPENED, DISMISSED → DISMISSED.

Looks up the in-app delivery attempt via
`DeliveryAttemptStore.findByNotificationId(notificationId)`, filtering for
`channelId == DeliveryChannels.IN_APP`. Calls
`EngagementRecorder.record(attempt, type, null)`.

Edge cases:
- No in-app delivery attempt found → log debug, skip silently
- When `casehub.delivery.engagement.enabled` is `false`, the observer method returns
  immediately without looking up attempts or recording events

**Known limitation — `AllNotificationsRead` gap:** no current `NotificationStore`
implementation fires per-notification `NotificationStatusChanged` events from
`markAllRead()`. Both `InMemoryNotificationStore` and `JpaReactiveNotificationStore`
fire only the bulk `AllNotificationsRead` event, and the `NotificationStore` SPI
javadoc actively recommends bulk UPDATE over per-row processing. The bridge therefore
never fires for mark-all-read operations in any current deployment. Engagement
analytics will undercount OPENED events by the fraction of reads that occur via
mark-all-read. This is acceptable because bulk mark-all-read is housekeeping — the user
is not engaging with individual notifications. Consumers of engagement data should be
aware of this systematic undercount in OPENED metrics.

No new dependencies for the `notifications/` REST module.

## Persistence

### Schema — V3001 (delivery-tracking-jpa)

```sql
ALTER TABLE delivery_attempt ADD COLUMN first_opened_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE delivery_attempt ADD COLUMN first_clicked_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE engagement_event (
    id              VARCHAR(36) NOT NULL PRIMARY KEY,
    attempt_id      VARCHAR(36) NOT NULL REFERENCES delivery_attempt(id) ON DELETE CASCADE,
    notification_id VARCHAR(36),
    channel_id      VARCHAR(255) NOT NULL,
    user_id         VARCHAR(255) NOT NULL,
    tenancy_id      VARCHAR(255) NOT NULL,
    type            VARCHAR(20) NOT NULL,
    recorded_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    metadata        TEXT
);

CREATE INDEX idx_engagement_event_attempt ON engagement_event (attempt_id);
CREATE INDEX idx_engagement_event_notification ON engagement_event (notification_id);
CREATE INDEX idx_engagement_event_user ON engagement_event (user_id, tenancy_id, type, recorded_at);
```

### JPA implementation (delivery-tracking-jpa)

New `EngagementEventEntity` mapped to `engagement_event`. `recordEngagement()` persists
the entity and does a conditional UPDATE on `delivery_attempt` to set
`first_opened_at`/`first_clicked_at` only if currently NULL. Single transaction.

`DeliveryAttemptEntity` gains two new fields: `firstOpenedAt`, `firstClickedAt`.

### InMemory implementation (delivery-tracking-inmem)

Second `ConcurrentHashMap<String, List<EngagementEvent>>` keyed by attempt ID.
`recordEngagement()` appends to the list and updates the parent `DeliveryAttempt`'s
summary fields. `evictIfNeeded()` must cascade: when evicting a `DeliveryAttempt`,
also remove the corresponding entry from the engagement event map.

### NoOp implementation (platform)

`NoOpDeliveryAttemptStore` gains no-op implementations: `recordEngagement()` silently
drops, `findEngagements*()` return empty lists.

## Module Impact

No new modules. All changes in existing modules:

| Module | Changes |
|--------|---------|
| platform-api | EngagementType, EngagementEvent, RawEngagement, EngagementRecorded, EngagementCallbackHandler, DeliveryAttempt +2 fields (breaks all constructor call sites — see below), DeliveryAttemptStore +3 methods |
| platform | NoOpDeliveryAttemptStore no-op implementations |
| delivery-tracking-inmem | InMemoryDeliveryAttemptStore engagement methods, second map, eviction cascade |
| delivery-tracking-jpa | EngagementEventEntity, JpaDeliveryAttemptStore engagement methods, V3001, DeliveryAttemptEntity +2 fields |
| notification-dispatch | EngagementRecorder, EngagementCallbackResource, InAppEngagementBridge, DeliveryTracker (constructor updates), DeliveryRetryProcessor (constructor updates) |

**DeliveryAttempt constructor call site updates:** adding `firstOpenedAt` and
`firstClickedAt` to the record changes the canonical constructor signature. All
existing call sites must add the two new fields (both `null` at construction time —
engagement timestamps are set only by `recordEngagement()`). Known call sites:
`DeliveryTracker` (5 calls), `InMemoryDeliveryAttemptStore.claimRetryable()` (1 call),
`DeliveryAttemptEntity.toDomain()` (1 call), `DeliveryRetryProcessor` (3 calls), plus
test code in `DeliveryTrackerTest`, `DeliveryRetryProcessorTest`,
`InMemoryDeliveryAttemptStoreTest`, `JpaDeliveryAttemptStoreTest`, and
`DeliveryAttemptTest`.

## Out of Scope

- Concrete channel implementations (SendGrid handler, FCM handler) — downstream modules
- Analytics/aggregation queries — consumer concern
- Dedicated REST endpoints for querying engagement data — `findEngagements*()` provides
  the programmatic API; a REST surface is a follow-up if needed

## Test Coverage

- EngagementType, EngagementEvent, and RawEngagement record validation (platform-api)
- `DeliveryAttemptStoreContractTest` — new contract test class in platform-api covering
  ALL 8 methods (existing 5 + new 3), following the `NotificationStoreContractTest`
  pattern. Both `InMemoryDeliveryAttemptStore` and `JpaDeliveryAttemptStore` extend it.
  This replaces the current per-implementation test approach for `DeliveryAttemptStore`
  and ensures behavioural parity across implementations.
- InMemoryDeliveryAttemptStore: engagement recording, summary field first-write-wins,
  eviction cascade to engagement map (delivery-tracking-inmem)
- JpaDeliveryAttemptStore: engagement recording, summary field conditional update,
  ON DELETE CASCADE behaviour, migration (delivery-tracking-jpa)
- EngagementRecorder: store + CDI event sequence, error isolation, disabled-flag no-op
  (notification-dispatch)
- EngagementCallbackResource: SPI path routing, RawEngagement→EngagementEvent
  enrichment, direct path validation, missing handler 404, tenancy check,
  content-type handling (JSON and form-encoded), disabled-flag 404
  (notification-dispatch)
- InAppEngagementBridge: READ→OPENED mapping, DISMISSED→DISMISSED mapping, missing
  attempt graceful skip, disabled-flag skip (notification-dispatch)
