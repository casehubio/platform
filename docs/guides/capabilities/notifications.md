---
capability: notifications
audience: consumer
repo: casehub-platform
verified-current: 2026-11-09
anchors:
  classes:
    - io.casehub.platform.notification.dispatch.NotificationDispatcher
    - io.casehub.platform.subscription.engine.SubscriptionEngine
    - io.casehub.platform.notification.dispatch.DeliveryRetryProcessor
    - io.casehub.platform.notification.dispatch.DeliveryTracker
    - io.casehub.platform.notification.dispatch.DigestFlushScheduler
    - io.casehub.platform.event.CloudEventTypeDispatcher
  spis:
    - io.casehub.platform.api.delivery.NotificationDeliverer
    - io.casehub.platform.api.delivery.DestinationResolver
    - io.casehub.platform.api.delivery.EngagementCallbackHandler
    - io.casehub.platform.api.subscription.SubscribableEvent
  config-keys:
    - casehub.notification.digest.max-buffer-size
    - casehub.delivery.tracking.inmem.max-size
    - casehub.platform.delivery.engagement-enabled
---

# Notifications, Subscriptions & Delivery

Domain modules produce `SubscribableEvent` objects into the notification DataSource. The subscription engine evaluates them against the alpha network, fires `SubscriptionMatched`, and the dispatch pipeline handles delivery (immediate, digest, or suppressed). REST + SSE endpoints expose notifications to clients.

## Modules

### Notification core

| Artifact | What it provides |
|----------|------------------|
| `casehub-platform-notifications` | REST + SSE presentation layer -- list, mark-read, dismiss, unread-count |
| `casehub-platform-notifications-inmem` | In-memory notification store (test/ephemeral) |
| `casehub-platform-notifications-jpa` | JPA notification store (production) -- keyset pagination, retention scheduler |

### Delivery pipeline

| Artifact | What it provides |
|----------|------------------|
| `casehub-platform-notification-dispatch` | Three-path delivery pipeline (digest/suppress/immediate); `DigestFlushScheduler`; `DeliveryRetryProcessor` |
| `casehub-platform-delivery-channel-inmem` | Channel-to-deliverer registry -- **production implementation** (channels are static) |
| `casehub-platform-delivery-tracking-inmem` | In-memory `DeliveryAttemptStore` |
| `casehub-platform-delivery-tracking-jpa` | JPA `DeliveryAttemptStore` -- `SKIP LOCKED` claims, retention purge |
| `casehub-platform-digest-inmem` | In-memory `DigestBuffer` |
| `casehub-platform-digest-jpa` | JPA `DigestBuffer` -- locked drain and transactional handoff to delivery tracking |
| `casehub-platform-notification-settings-inmem` | In-memory preference/suppression store |
| `casehub-platform-notification-settings-jpa` | JPA preference/suppression store -- JSON TEXT columns, retention scheduler |

### Subscriptions

| Artifact | What it provides |
|----------|------------------|
| `casehub-platform-subscriptions` | Subscription matching engine + REST -- alpha network wiring, expression compilation |
| `casehub-platform-subscriptions-inmem` | In-memory subscription store (test/ephemeral) |
| `casehub-platform-subscriptions-jpa` | JPA subscription store (production) -- OR-disjunction scope queries |

## Key SPIs

**SubscribableEvent interface:** Compile-time contract for subscription POJOs. Must implement `type()` (reverse-DNS event type string, e.g. `"io.casehub.work.workitem.completed"`) and `tenancyId()`. POJOs not implementing this interface are silently rejected by the subscription engine.

**SubscriptionScope:** `USER` (per-user subscriptions) or `SYSTEM` (admin-managed, system-wide subscriptions with admin authorization).

**Event type glob matching:** Subscription `eventType` fields support prefix patterns (e.g. `"io.casehub.work.*"`) for matching groups of event types.

**Delivery channels:** Well-known constants in `DeliveryChannels`: `IN_APP`, `EMAIL`, `SMS`, `PUSH`, `WHATSAPP`.

**NotificationDeliverer SPI:** Implement to deliver notifications via a specific channel. Methods: `channelId()`, `deliver(NotificationInput)`, `deliverDigest(DigestSummary)`. Self-registers its `DeliveryChannelDescriptor` in the `DeliveryChannelRegistry` at `@PostConstruct`.

**DestinationResolver SPI:** Resolves a user's delivery destination for a specific channel. Methods: `channelId()`, `resolve(userId, tenancyId)`. One implementation per channel type.

**DestinationScope:** `PER_USER` (email, SMS, WhatsApp) or `PER_TENANT` (future -- Slack, Teams).

## Digest System

The scheduler uses the user's channel-specific digest schedule when present, otherwise the delivery channel's default schedule. Missing preferences or a null user schedule therefore retain the channel's default timing. A pending buffer is flushed as an orphan only when neither source provides a schedule.

Before flushing a pending digest, the scheduler checks the current channel enablement: an explicit user preference takes precedence over the channel's `defaultEnabled` setting. Disabled channels retain their buffered notifications, subject to normal retention and buffer limits. Re-enabling a channel makes that buffer eligible for delivery under the current scheduling and suppression rules; disabling also clears any earlier quiet-hours deferral.

Configurable digest schedules via `DigestSchedule` sealed interface:
- `DigestSchedule.Interval(Duration period)` -- fixed period (minimum 1 minute)
- `DigestSchedule.DailyAt(LocalTime time, ZoneId timezone)` -- once per day
- `DigestSchedule.WeeklyAt(DayOfWeek day, LocalTime time, ZoneId timezone)` -- once per week

**Durable handoff:** Pair persistent digest and delivery-tracking adapters on the same database and transaction manager. The scheduler persists a delivery attempt and removes the buffered notifications in one transaction; a tracking failure leaves the notifications buffered. Both JPA adapters lock selected rows with `SKIP LOCKED`, so concurrent schedulers do not drain the same notifications.

**Recovery:** Guaranteed in-flight digests become retryable after `casehub.delivery.retry.claim-timeout` (default five minutes). Set the timeout above normal send duration. Delivery remains at least once: if a process stops after sending but before confirming delivery, recovery can send the digest again.

**Delivery retries:** A failed digest is retried when the channel defines a `guaranteedMinSeverity` and at least one buffered notification meets that threshold. Digests without a delivery guarantee or below the threshold remain `FAILED`, with the failure reason recorded and no retry deadline. Eligible failures remain `RETRYING` and are scheduled using the configured retry delay.

**DigestGroupBy:** `FLAT` (no grouping), `CATEGORY` (by notification category), `ENTITY` (by entity type and ID).

## Engagement Tracking

**EngagementType:** `OPENED`, `CLICKED`, `DISMISSED`, `REPLIED`, `CONVERTED`. `EngagementCallbackHandler` SPI translates provider-specific webhook payloads into platform engagement events (must verify request signatures via provider-specific headers).

## Configuration

| Property | Purpose | Default |
|----------|---------|---------|
| `casehub.notification.digest.max-buffer-size` | Digest buffer size (0 = no eviction in JPA) | 0 (JPA), 500 (in-memory) |
| `casehub.delivery.retry.claim-timeout` | Lease for an in-flight delivery attempt | 5m |
| `casehub.delivery.tracking.inmem.max-size` | In-memory delivery attempt store size | 10000 |
| `casehub.platform.delivery.engagement-enabled` | Platform preference enabling engagement event recording | false |
| `casehub.delivery.retention.attempt-days` | Delivery attempt retention | -- |
| `casehub.delivery.retention.failed-attempt-days` | Failed attempt retention | -- |
| `casehub.delivery.retention.engagement-days` | Engagement event retention | -- |

### Flyway locations

| Module | Flyway location |
|--------|----------------|
| `notifications-jpa` | `classpath:db/notification/migration` |
| `notification-settings-jpa` | `classpath:db/notification-settings/migration` |
| `delivery-tracking-jpa` | `classpath:db/delivery-tracking/migration` |
| `digest-jpa` | `classpath:db/digest/migration` |
| `subscriptions-jpa` | `classpath:db/subscription/migration` |
