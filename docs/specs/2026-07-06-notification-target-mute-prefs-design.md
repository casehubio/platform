# Notification Target Resolution, Mute/Snooze, Channel Preferences — #148 + #143 + #145

**Date:** 2026-07-06
**Issues:** casehubio/platform#148 (target resolution), casehubio/platform#143 (channel preferences), casehubio/platform#145 (mute/snooze)
**Epic:** casehubio/platform#147 (Phases 3, 4, 5)

---

## Overview

Three features that compose into a notification evaluation pipeline between subscription matching (#142) and delivery:

1. **Target resolution** (#148) — expand abstract targets (groups, roles, dynamic POJO fields) to concrete user sets
2. **Channel preferences** (#143) — per-user configuration of which delivery channels to use, with severity thresholds and quiet hours
3. **Mute/snooze** (#145) — per-user temporary suppression: mute drops notifications entirely, snooze defers external delivery

These build on the subscription management system (#142) and notification store (#135). The subscription model gains explicit targets (replacing the userId-is-recipient conflation), and a new NotificationDispatcher bean extracts the delivery pipeline from the SubscriptionEngine.

---

## Design Decisions

### Explicit targets on all subscriptions

The previous subscription model conflated subscription owner and notification recipient — `userId` was both. Target resolution requires separating these. Every subscription now has an explicit `List<NotificationTarget>` specifying who gets notified. `userId` is renamed to `ownerId` (who manages the subscription). The REST layer defaults targets to `[NotificationTarget(USER, ownerId)]` for convenience — the SPI always requires explicit targets. This is a breaking SPI change; migration is mechanical.

### NotificationDispatcher — separate from SubscriptionEngine

SubscriptionEngine's concern is event matching via the alpha network. The delivery pipeline (target resolution → suppression → channel routing → delivery) is a separate concern with its own complexity. The SubscriptionEngine's DataProcessor now fires `SubscriptionMatched` via CDI `fireAsync`. The `NotificationDispatcher` observes it and orchestrates delivery. Clean separation, independently testable.

### Async dispatch via CDI fireAsync

The alpha network must not block on external calls. Target resolution may invoke `GroupMembershipProvider.membersOf()` which can be a SCIM network call. CDI `fireAsync(SubscriptionMatched)` naturally decouples matching from dispatch — the dispatcher runs on the managed executor pool, not the alpha network's thread.

### Delivery channel registry

Consistent with `DataSourceRegistry` and `EndpointRegistry` — the platform's third registry. Each `NotificationDeliverer` self-registers its `DeliveryChannelDescriptor` at startup. Enables preference validation (user can't configure a channel that isn't deployed) and UI discovery (preferences UI shows only available channels).

### In-app is a registered channel

In-app delivery (NotificationStore.store()) is not a special case — it's a `NotificationDeliverer` implementation registered in the `DeliveryChannelRegistry` like any external channel. This makes the pipeline uniform: mute suppresses ALL channels (including in-app), snooze/quiet hours suppress only external channels — both expressed as channel-level decisions, not special-case branching.

### Unified SuppressionStore for mute + snooze

Mute rules and snooze state are both per-user notification suppression, queried together in the same pipeline step. Different data shapes (multiple mutes vs single snooze) but same domain. One SPI, one module pair, one Flyway migration set. Channel preferences are a separate SPI — different domain (configuration vs suppression).

### Best-effort delivery, seam for guaranteed delivery

Async CDI dispatch is best-effort — process crash between match and dispatch loses the notification. This is acceptable: in-app is the reliable fallback, external channels are additive. The `NotificationDeliverer` SPI returns `DeliveryResult` (success/failure), providing the architectural seam for future transactional outbox and delivery tracking (#154).

---

## Section 1: Subscription Model Changes (platform-api)

All types in `io.casehub.platform.api.subscription`.

### Subscription record — modified

```java
public record Subscription(
    String id,                              // UUIDv7
    String ownerId,                         // renamed from userId — who manages this subscription
    String tenancyId,
    String name,
    String eventType,
    List<Constraint> constraints,           // unchanged
    List<NotificationTarget> targets,       // NEW — who gets notified
    boolean includeActor,                   // NEW — include triggering actor in recipients (default false)
    NotificationTemplate template,          // unchanged
    boolean enabled,
    Instant createdAt,
    Instant updatedAt
) {}
```

### NotificationTarget — new

```java
public record NotificationTarget(
    TargetType type,
    String id
) {}

public enum TargetType {
    USER,           // literal userId — direct delivery, no expansion
    GROUP,          // group name — expanded via GroupMembershipProvider.membersOf()
    EVENT_FIELD     // POJO field name — resolved at dispatch time via MethodHandle
}
```

- `USER` — `id` is a literal userId. No expansion.
- `GROUP` — `id` is a group name. Expanded via `GroupMembershipProvider.membersOf(groupName)` at dispatch time. Covers roles, teams, and application-level groups.
- `EVENT_FIELD` — `id` is a POJO property name (e.g., `"assigneeId"`). Resolved at dispatch time via MethodHandle. Solves "notify the assignee."

### includeActor

When true, the actor who triggered the event is included in the resolved recipient set. Default is false — the actor is excluded, which is the common case (no "you commented on your own work item" noise). Actor identified via `template.actorIdField()` + MethodHandle — same mechanism already used for template resolution. Per-subscription so confirmation-style subscriptions can set it to true.

The false-by-default polarity ensures Java's primitive `boolean` default aligns with the intended behaviour — Jackson deserialization of a missing field produces `false` (exclude actor), which is correct without any special handling.

### SubscriptionInput — modified

```java
public record SubscriptionInput(
    String ownerId,                         // renamed from userId
    String tenancyId,
    String name,
    String eventType,
    List<Constraint> constraints,
    List<NotificationTarget> targets,       // NEW — required, non-empty
    boolean includeActor,                   // NEW — default false (actor excluded)
    NotificationTemplate template,
    boolean enabled
) {}
```

### SubscriptionUpdate — modified

```java
public record SubscriptionUpdate(
    String name,
    String eventType,
    List<Constraint> constraints,
    List<NotificationTarget> targets,       // nullable = don't change
    Boolean includeActor,                   // nullable = don't change
    NotificationTemplate template,
    Boolean enabled
) {}
```

### SubscriptionStore SPI — userId → ownerId

All method signatures change: `findById(id, ownerId, tenancyId)`, `update(id, ownerId, tenancyId, update)`, `delete(id, ownerId, tenancyId)`. Same for ReactiveSubscriptionStore. Breaking change — every caller updates mechanically.

### SubscriptionMatched — new CDI event

```java
public record SubscriptionMatched(
    Subscription subscription,
    Object pojo
) {}
```

Fired by SubscriptionEngine's DataProcessor via `event.fireAsync()`. Observed by NotificationDispatcher.

### REST layer convenience

When `POST /subscriptions` is called without `targets`, the REST resource defaults to `[NotificationTarget(USER, currentPrincipal.actorId())]`. The SPI always requires explicit targets.

---

## Section 2: Dispatch Pipeline (notification-dispatch/)

`@ApplicationScoped` in the `notification-dispatch/` module. Observes `SubscriptionMatched` via `@ObservesAsync`. Orchestrates three stage beans.

### Pipeline flow

```
@ObservesAsync SubscriptionMatched
    │
    ▼
TargetResolver.resolve(subscription, pojo)
    → Set<String> recipientUserIds (deduplicated, actor-excluded)
    │
    ▼
Per recipientUserId:
    // Pre-fetch per-user data (one query each — no redundant lookups)
    preferences = NotificationPreferenceStore.get(userId, tenancyId)
    activeMutes = SuppressionStore.activeMutes(userId, tenancyId)
    activeSnooze = SuppressionStore.activeSnooze(userId, tenancyId)
    │
    // Extract suppression metadata cheaply (static template fields + one MethodHandle)
    entityType = template.entityType()
    entityId = extractField(pojo, template.entityIdField())
    category = template.category()
    │
    SuppressionEvaluator.evaluate(activeMutes, activeSnooze, preferences.quietHours(),
                                  entityType, entityId, category)
        → SuppressionResult { isMuted, isSnoozed, quietHoursActive }
    │
    if muted → DROP (skip entirely)
    │
    ▼
    // Full template resolution only when not muted
    TemplateResolver.resolve(template, pojo, userId, tenancyId)
        → NotificationInput (nullable — null if entityIdField or actorIdField missing on POJO)
    if null → skip this recipient, continue to next (WARN already logged by TemplateResolver)
    │
    ChannelRouter.route(preferences.channelDefaults(), suppressionResult, severity)
        → Set<ResolvedChannel> { channelId, deliverer, suppressed }
    │
    ▼
    Per ResolvedChannel (independent — failure in one channel does not prevent delivery to others):
        if suppressed → skip
        try:
            result = deliverer.deliver(notificationInput)
            if !result.success() → log WARN (channelId, failureReason)
        catch exception → log WARN (channelId, exception), continue to next channel
```

Data is pre-fetched once per user, then passed to each pipeline stage as arguments. The SuppressionEvaluator is a pure function over pre-fetched data — no internal queries, independently testable. The ChannelRouter receives pre-fetched user preferences and suppression result; its only dependency is the startup-populated `DeliveryChannelRegistry` (ConcurrentHashMap, no I/O).

### TargetResolver @ApplicationScoped

- Iterates `subscription.targets()`
- `USER` → add userId directly
- `GROUP` → call `GroupMembershipProvider.membersOf(groupName)`, add each `GroupMember.actorId()`
- `EVENT_FIELD` → extract userId from POJO via MethodHandle; if null → skip (log WARN with field name and subscription ID — distinguishes unset field from misconfiguration, same pattern as GROUP empty warning)
- Deduplicates across all targets
- Unless `subscription.includeActor()` is true, removes the actor (extracted via `template.actorIdField()` + MethodHandle)
- GROUP target resolving to empty membership → log WARN with group name and subscription ID (distinguishes misconfiguration from normal self-exclusion)
- Empty result after dedup/exclusion → no dispatch, no error, no log (normal for self-actions)

### SuppressionEvaluator @ApplicationScoped

Pure function — receives pre-fetched data, performs no queries.

`evaluate(activeMutes, activeSnooze, quietHours, entityType, entityId, category) → SuppressionResult`

- Checks mute rules against notification metadata (entityType, entityId, category)
- Checks active snooze
- Checks quiet hours
- Returns `SuppressionResult(isMuted, isSnoozed, quietHoursActive)`
- Muted → entire notification dropped (all channels)
- Snoozed/quiet hours → external channels suppressed, in-app proceeds

Mute matching logic (in evaluator, not store):
- `ENTITY` scope: `rule.entityType().equals(entityType) && rule.scopeId().equals(entityId)`
- `CATEGORY` scope: `rule.scopeId().equals(category)` — if `rule.entityType()` is non-null, also requires `rule.entityType().equals(entityType)` (optional refinement: "mute comments on work-items only")
- Expired rules filtered: `rule.expiresAt() != null && Instant.now().isAfter(rule.expiresAt())`

Quiet hours evaluation handles midnight crossing:
- Same-day (start < end): `start <= now && now < end` (e.g., 09:00–17:00)
- Cross-midnight (start >= end): `now >= start || now < end` (e.g., 22:00–07:00)

The evaluator converts `QuietHours` (LocalTime + ZoneId) to the user's current local time for comparison.

### ChannelRouter @ApplicationScoped

Receives pre-fetched user preferences and suppression result. Its only injected dependency is the startup-populated `DeliveryChannelRegistry` (ConcurrentHashMap, no I/O) — distinct from the old design where it queried per-user stores.

`route(channelDefaults, suppressionResult, severity) → Set<ResolvedChannel>`

- Reads `DeliveryChannelRegistry` for available channels (via `discover()`) and deliverer resolution (via `resolveDeliverer()`)
- For each available channel:
  - Check user preference from `channelDefaults`: is channel enabled? Does severity meet `minSeverity`?
  - If user has no stored preference for a channel, fall back to the channel's self-declared defaults from `DeliveryChannelDescriptor.defaultEnabled()` and `defaultMinSeverity()`
  - Check suppression: is channel external AND (snoozed OR quiet hours active)?
  - Resolve `NotificationDeliverer` via `channelRegistry.resolveDeliverer(channelId)`
- Returns `Set<ResolvedChannel>` — channelId, deliverer reference, suppressed flag

Channel defaults are self-declared by each `NotificationDeliverer` via `DeliveryChannelDescriptor` at registration time. New channels self-describe — no ChannelRouter modifications needed.

### SubscriptionEngine changes

- DataProcessor becomes: `pojo → event.fireAsync(new SubscriptionMatched(subscription, pojo))`
- ConstraintCompiler, EventTypeObjectType — unchanged
- TemplateResolver moves to notification-dispatch/ (delivery concern)

---

## Section 3: Delivery Channel Model (platform-api)

All types in `io.casehub.platform.api.delivery`.

### DeliveryChannelDescriptor

```java
public record DeliveryChannelDescriptor(
    String channelId,
    String displayName,
    boolean external,
    boolean defaultEnabled,
    NotificationSeverity defaultMinSeverity
) {}
```

`external` flag drives suppression: snooze and quiet hours suppress channels where `external=true`.

### Well-known channel constants

```java
public final class DeliveryChannels {
    public static final String IN_APP = "in_app";
    public static final String EMAIL = "email";
    public static final String SMS = "sms";
    public static final String PUSH = "push";
    private DeliveryChannels() {}
}
```

### DeliveryChannelRegistry SPI

```java
public interface DeliveryChannelRegistry {
    void register(DeliveryChannelDescriptor descriptor, NotificationDeliverer deliverer);
    Optional<DeliveryChannelDescriptor> resolve(String channelId);
    Optional<NotificationDeliverer> resolveDeliverer(String channelId);
    Set<DeliveryChannelDescriptor> discover();
}
```

Registration is atomic: descriptor and deliverer are stored together. Follows the `DataSourceRegistry` precedent where `register(DataSourceDescriptor)` returns the instance and `resolveSource()` retrieves it — combined metadata + instance registry. Each deliverer's `@PostConstruct` passes both its descriptor and `this`.

@DefaultBean no-op returns empty. Must NOT fire CDI events (per noop-registry-must-not-fire-cdi-events protocol).

### NotificationDeliverer SPI

```java
public interface NotificationDeliverer {
    String channelId();
    DeliveryResult deliver(NotificationInput notification);
}

public record DeliveryResult(
    boolean success,
    String failureReason
) {}
```

Each deliverer self-registers its `DeliveryChannelDescriptor` in `DeliveryChannelRegistry` at `@PostConstruct`. Returns `DeliveryResult` for future #154 seam.

### InAppNotificationDeliverer — in notification-dispatch/

```java
@ApplicationScoped
public class InAppNotificationDeliverer implements NotificationDeliverer {
    @Inject NotificationStore notificationStore;
    @Inject DeliveryChannelRegistry channelRegistry;

    @PostConstruct void register() {
        channelRegistry.register(new DeliveryChannelDescriptor(
            DeliveryChannels.IN_APP, "In-App Inbox", false,
            true, NotificationSeverity.INFO), this);
    }

    @Override public String channelId() { return DeliveryChannels.IN_APP; }

    @Override public DeliveryResult deliver(NotificationInput notification) {
        notificationStore.store(notification);
        return new DeliveryResult(true, null);
    }
}
```

Only in-app is implemented in this phase. External deliverers are future modules.

### REST endpoint

`GET /notifications/channels` — returns all registered `DeliveryChannelDescriptor` records.

---

## Section 4: User Notification Preferences (platform-api)

All types in `io.casehub.platform.api.notification.settings`.

### NotificationPreferences

```java
public record NotificationPreferences(
    String userId,
    String tenancyId,
    Map<String, ChannelPreference> channelDefaults,
    QuietHours quietHours,
    Instant updatedAt
) {}

public record ChannelPreference(
    boolean enabled,
    NotificationSeverity minSeverity
) {}

public record QuietHours(
    LocalTime start,
    LocalTime end,
    ZoneId timezone
) {}
```

- `channelDefaults` keyed by channelId string. Absence = use platform default.
- `QuietHours` uses `LocalTime` + `ZoneId`. SuppressionEvaluator converts to server instant for evaluation. Recurring daily.
- `minSeverity` gates delivery: severity comparison uses enum ordinal `INFO < WARNING < URGENT`.

### NotificationPreferenceStore SPI

```java
public interface NotificationPreferenceStore {
    Optional<NotificationPreferences> get(String userId, String tenancyId);
    NotificationPreferences update(String userId, String tenancyId, NotificationPreferenceUpdate update);
}

public record NotificationPreferenceUpdate(
    Map<String, ChannelPreference> channelDefaults,
    QuietHours quietHours,
    boolean clearQuietHours
) {}
```

- `update()` is upsert — creates if absent, updates if present.
- `clearQuietHours=true` removes quiet hours (since `quietHours=null` means "don't change").
- No reactive variant — blocking on managed executor is fine. Future reactive addition via default methods per `spi-evolution-default-methods` protocol.

### REST endpoints

| Method | Path | Operation |
|--------|------|-----------|
| `GET` | `/notifications/preferences` | Current user's preferences |
| `PUT` | `/notifications/preferences` | Update (upsert) |

---

## Section 5: Suppression — Mute and Snooze (platform-api)

All types in `io.casehub.platform.api.notification.settings`.

### MuteRule

```java
public record MuteRule(
    String id,
    String userId,
    String tenancyId,
    MuteScope scope,
    String scopeId,
    String entityType,             // required for ENTITY scope; nullable for CATEGORY (optional refinement)
    Instant createdAt,
    Instant expiresAt
) {}

public enum MuteScope {
    ENTITY,
    CATEGORY
}
```

- `ENTITY`: mutes notifications where `source.entityType` matches `entityType` AND `source.entityId` matches `scopeId`. `entityType` is required (validated by store on add).
- `CATEGORY`: mutes notifications where `category` matches `scopeId`. If `entityType` is non-null, also requires matching `entityType` (optional refinement — "mute comments on work-items only"). If `entityType` is null, matches the category for all entity types.
- `expiresAt` nullable — permanent until manually removed.

### MuteRuleInput

```java
public record MuteRuleInput(
    String userId,
    String tenancyId,
    MuteScope scope,
    String scopeId,
    String entityType,             // required for ENTITY scope; nullable for CATEGORY
    Instant expiresAt
) {}
```

### Snooze

```java
public record Snooze(
    String userId,
    String tenancyId,
    Instant until,
    Instant createdAt
) {}
```

At most one active snooze per user. Primary key is `(userId, tenancyId)`. Activating replaces existing.

### SnoozeInput

```java
public record SnoozeInput(
    String userId,
    String tenancyId,
    Instant until
) {}
```

### SuppressionStore SPI

```java
public interface SuppressionStore {
    MuteRule addMute(MuteRuleInput input);
    List<MuteRule> activeMutes(String userId, String tenancyId);
    boolean removeMute(String muteId, String userId, String tenancyId);

    Snooze activateSnooze(SnoozeInput input);
    Optional<Snooze> activeSnooze(String userId, String tenancyId);
    boolean cancelSnooze(String userId, String tenancyId);
}
```

No reactive variant. Same reasoning as NotificationPreferenceStore.

### Expiry cleanup

Per `store-owned-retention-mechanism` protocol:
- JPA: `@Scheduled` purge of expired mute rules and snooze records
- In-memory: lazy eviction — filter out expired entries on read

### REST endpoints

| Method | Path | Operation |
|--------|------|-----------|
| `POST` | `/notifications/mute` | Add mute rule |
| `GET` | `/notifications/mute` | List active mutes |
| `DELETE` | `/notifications/mute/{id}` | Remove mute rule |
| `POST` | `/notifications/snooze` | Activate snooze |
| `GET` | `/notifications/snooze` | Get active snooze (or 404) |
| `DELETE` | `/notifications/snooze` | Cancel snooze |

All mute, snooze, and preference REST endpoints enforce `CurrentPrincipal` — `userId` and `tenancyId` from the request body are overridden from `principal.actorId()` and `principal.tenancyId()`, following the same pattern as `SubscriptionResource`. This prevents users from creating mute rules or activating snooze for other users.

---

## Section 6: Module Structure

### New modules

| Module | Artifact | Purpose |
|--------|----------|---------|
| `notification-settings-inmem/` | `casehub-platform-notification-settings-inmem` | @Alternative @Priority(100) InMemoryNotificationPreferenceStore + InMemorySuppressionStore. ConcurrentHashMap. No quarkus:build goal |
| `notification-settings-jpa/` | `casehub-platform-notification-settings-jpa` | @ApplicationScoped JpaNotificationPreferenceStore + JpaSuppressionStore. Hibernate ORM Panache (blocking-only — no reactive SPI, so no Hibernate Reactive overhead). Flyway at `classpath:db/notification-settings/migration`. No quarkus:build goal |
| `notification-dispatch/` | `casehub-platform-notification-dispatch` | NotificationDispatcher + TargetResolver + SuppressionEvaluator + ChannelRouter + InAppNotificationDeliverer + InMemoryDeliveryChannelRegistry. No quarkus:build goal |

### Changes to existing modules

| Module | Change |
|--------|--------|
| `platform-api/` | New packages: `io.casehub.platform.api.notification.settings`, `io.casehub.platform.api.delivery`. Modified: `io.casehub.platform.api.subscription` (userId→ownerId, targets, includeActor, SubscriptionMatched) |
| `platform/` | Add @DefaultBean no-ops: NoOpNotificationPreferenceStore, NoOpSuppressionStore, NoOpDeliveryChannelRegistry |
| `subscriptions/` | SubscriptionEngine fires SubscriptionMatched via fireAsync. TemplateResolver moves to notification-dispatch/. SubscriptionResource: userId→ownerId, targets default |
| `subscriptions-inmem/` | Subscription entity: userId→ownerId, add targets, includeActor |
| `subscriptions-jpa/` | Subscription entity: userId→ownerId, add targets, includeActor. Flyway V2: add columns, rename |
| `notifications/` | Add REST endpoints for preferences, mute, snooze, channels |

### CDI priority ladder — notification-settings stores

| Tier | Annotation | Module |
|------|-----------|--------|
| @DefaultBean | NoOpNotificationPreferenceStore, NoOpSuppressionStore | platform/ |
| @Alternative @Priority(100) | InMemoryNotificationPreferenceStore, InMemorySuppressionStore | notification-settings-inmem/ |
| @ApplicationScoped | JpaNotificationPreferenceStore, JpaSuppressionStore | notification-settings-jpa/ |

### DeliveryChannelRegistry

The `InMemoryDeliveryChannelRegistry` is the production implementation — stores both descriptors and deliverer instances, populated from deliverer `@PostConstruct` registrations at startup. ConcurrentHashMap keyed by channelId. No JPA needed. Lives in `notification-dispatch/` as `@ApplicationScoped` (beats the @DefaultBean no-op in platform/).

### Flyway

- `notification-settings-jpa/`: `classpath:db/notification-settings/migration`, V1: notification_preferences + mute_rules + snooze tables
- `subscriptions-jpa/`: V2: ALTER TABLE subscription — rename user_id → owner_id, add targets_json (TEXT), add include_actor (BOOLEAN NOT NULL DEFAULT FALSE). Backfill existing rows: `UPDATE subscription SET targets_json = '[{"type":"USER","id":"' || owner_id || '"}]' WHERE targets_json IS NULL;` — converts the old implicit "userId is the recipient" to the explicit `[NotificationTarget(USER, ownerId)]` default. Without this backfill, existing subscriptions resolve zero targets and silently stop delivering.

### Dependency graph

```
platform-api  (SPIs, records, CDI events)
    │
    ├── platform/  (no-op defaults)
    │
    ├── notification-settings-inmem/  (platform-api)
    ├── notification-settings-jpa/   (platform-api, quarkus-hibernate-orm-panache)
    │
    ├── notification-dispatch/       (platform-api)
    │   ├── injects: SuppressionStore, NotificationPreferenceStore
    │   ├── injects: DeliveryChannelRegistry (descriptors + deliverer instances)
    │   ├── injects: GroupMembershipProvider
    │   ├── injects: NotificationStore (via InAppNotificationDeliverer)
    │   └── observes: @ObservesAsync SubscriptionMatched
    │
    ├── subscriptions/  (fires: SubscriptionMatched via fireAsync)
    └── notifications/  (REST — injects stores + registry)
```

---

## Deferred

Each deferred item is filed as a GitHub issue under epic #147:

- **#154 — Guaranteed delivery + tracking** — transactional outbox for URGENT notifications, per-channel delivery status tracking, open/engagement tracking. Architecture provides seam via `DeliveryResult`.
- **#155 — EventTypeRegistry** — discoverable event types for subscription UI and validation. Domain bridges register event type metadata.
- **Digest/batching** (#144) — timer-driven aggregation. Sits in the pipeline between suppression and delivery.
- **Notification center frontend** (#146) — consumes all APIs defined here.
- **External deliverers** — email, SMS, push implementations as separate modules implementing `NotificationDeliverer`.
- **#156 — Channel subscriber target type** — "Notify everyone watching this project." Requires entity-watch subscription infrastructure not present in platform. Explicitly deferred from #148.
- **Subscription-level channel overrides** — per-subscription delivery preferences that override user defaults. The current model extends naturally with an optional `Map<String, ChannelPreference>` on Subscription; migration is mechanical (record constructor change breaks call sites at compile time, all callers update).
- **Post-snooze external delivery** — issue #145 uses "deferred" for snoozed external delivery. Current design suppresses (drops) external delivery during snooze — in-app delivery continues, serving as the durable record. True deferral (queue external delivery and send post-snooze) is a natural extension of #154's transactional outbox. The current "suppress + in-app fallback" is intentional and consistent with the best-effort delivery philosophy.

---

## Documentation Updates

Implementation requires updates to:
- **ARC42STORIES.MD** — notification dispatch pipeline, delivery channel model, suppression system
- **CLAUDE.md** — new modules in module table, package structure updates
