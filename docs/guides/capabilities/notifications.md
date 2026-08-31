---
capability: notifications
audience: consumer
repo: casehub-platform
anchors:
  classes:
    - io.casehub.platform.notification.NotificationBridge
    - io.casehub.platform.notification.SubscriptionEngine
    - io.casehub.platform.notification.dispatch.DeliveryRetryProcessor
    - io.casehub.platform.notification.dispatch.DigestFlushScheduler
    - io.casehub.platform.notification.CloudEventTypeDispatcher
  spis:
    - io.casehub.platform.notification.spi.DeliveryChannel
    - io.casehub.platform.notification.spi.NotificationDeliverer
    - io.casehub.platform.notification.spi.DestinationResolver
    - io.casehub.platform.notification.spi.EngagementCallbackHandler
    - io.casehub.platform.subscription.SubscribableEvent
  config-keys:
    - casehub.notification.digest.max-buffer-size
    - casehub.delivery.tracking.inmem.max-size
    - casehub.delivery.engagement.enabled
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
| `casehub-platform-digest-jpa` | JPA `DigestBuffer` -- drain via SELECT+DELETE in transaction |
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

Configurable digest schedules via `DigestSchedule` sealed interface:
- `DigestSchedule.Interval(Duration period)` -- fixed period (minimum 1 minute)
- `DigestSchedule.DailyAt(LocalTime time, ZoneId timezone)` -- once per day
- `DigestSchedule.WeeklyAt(DayOfWeek day, LocalTime time, ZoneId timezone)` -- once per week

**DigestGroupBy:** `FLAT` (no grouping), `CATEGORY` (by notification category), `ENTITY` (by entity type and ID).

## Engagement Tracking

**EngagementType:** `OPENED`, `CLICKED`, `DISMISSED`, `REPLIED`, `CONVERTED`. `EngagementCallbackHandler` SPI translates provider-specific webhook payloads into platform engagement events (must verify request signatures via provider-specific headers).

## Configuration

| Property | Purpose | Default |
|----------|---------|---------|
| `casehub.notification.digest.max-buffer-size` | Digest buffer size (0 = no eviction) | 0 |
| `casehub.delivery.tracking.inmem.max-size` | In-memory delivery attempt store size | 10000 |
| `casehub.delivery.engagement.enabled` | Enable engagement event recording | false |
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
