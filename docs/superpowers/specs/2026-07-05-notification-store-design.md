# Notification Store Design — #135

**Date:** 2026-07-05
**Issue:** casehubio/platform#135
**Epic:** casehubio/platform#147 (Phase 1)

---

## Overview

In-app notification store for casehub-platform — per-user notification persistence with real-time read path. The terminal sink in the notification routing pipeline: the routing layer (#142) writes notifications, users read them via REST, and SSE pushes real-time updates.

## Data Model

All types in `platform-api` under `io.casehub.platform.api.notification`. Pure Java, zero dependencies.

### Enums

```java
public enum NotificationSeverity { INFO, WARNING, URGENT }

public enum NotificationStatus { UNREAD, READ, DISMISSED }
```

### Source Reference

Typed coordinates back to the originating event. Not a map — fixed structure, compile-time type safety at every boundary.

```java
public record NotificationSource(
    String eventId,       // CloudEvent id — audit correlation
    String entityType,    // domain entity kind (open set: "work-item", "case", etc.)
    String entityId,      // domain entity instance
    String actorId        // who performed the action (originator, not recipient)
) {
    public NotificationSource {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(actorId, "actorId");
    }
}
```

### Input Record

What the routing layer passes to the store. No id, no status, no timestamps — the store owns identity generation and lifecycle.

```java
public record NotificationInput(
    String userId,
    String tenancyId,
    String title,
    String body,                    // nullable
    String category,                // event type: "work-item.created", "sla.breached"
    NotificationSeverity severity,
    String actionUrl,               // nullable — deep link
    NotificationSource source
) {
    public NotificationInput {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(tenancyId, "tenancyId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(source, "source");
    }
}
```

### Domain Record

What the store persists and returns. Immutable — status transitions produce new database state, not mutated instances.

```java
public record Notification(
    String id,                      // UUID v7 — time-ordered for cursor stability
    String userId,
    String tenancyId,
    String title,
    String body,                    // nullable
    String category,
    NotificationSeverity severity,
    String actionUrl,               // nullable
    NotificationSource source,
    NotificationStatus status,
    Instant createdAt,              // store-generated
    Instant readAt,                 // nullable — set on markRead
    Instant dismissedAt             // nullable — set on dismiss
) {
    public Notification {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(tenancyId, "tenancyId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
```

### Status Lifecycle

```
UNREAD ──→ READ ──→ DISMISSED
  │                      ▲
  └──────────────────────┘
       (direct dismiss)
```

- **UNREAD → READ** — `markRead` / `markAllRead`
- **READ → DISMISSED** — `dismiss`
- **UNREAD → DISMISSED** — `dismiss` (direct — user dismisses without reading)
- No reverse transitions. DISMISSED is terminal.

### Query and Result Types

```java
public record NotificationQuery(
    String userId,
    String tenancyId,
    NotificationStatus status,      // nullable = all statuses
    String category,                // nullable = all categories
    String cursor,                  // nullable = start from beginning
    int limit
) {
    public NotificationQuery {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(tenancyId, "tenancyId");
        if (limit <= 0) throw new IllegalArgumentException("limit must be positive");
    }
}

public record NotificationPage(
    List<Notification> notifications,
    String nextCursor               // null = no more pages
) {
    public NotificationPage {
        Objects.requireNonNull(notifications, "notifications");
        notifications = List.copyOf(notifications);
    }
}
```

### Design Rationale

- **`NotificationInput` / `Notification` split** — follows `MemoryInput` / `Memory` precedent. Store owns identity and lifecycle. Prevents callers from setting id or status to wrong values.
- **`NotificationSource` as typed record, not `Map<String, String>`** — fixed structure, every notification has the same source shape. Maps are for heterogeneous property bags (endpoint config); source references don't vary. Type safety flows from CloudEvent through routing to store to REST to frontend.
- **`entityType` is `String` (open set)** — new entity types are added as the platform grows. Follows `AclResourceType` pattern (string constants), not a closed enum.
- **`tenancyId` on everything** — platform tenant isolation is mandatory. Every SPI enforces it.
- **`id` is UUID v7 (time-ordered)** — same-timestamp notifications have deterministic ordering. Cursor pagination uses `(created_at DESC, id DESC)` — random UUID v4 would produce non-deterministic ordering for same-timestamp records, causing cursor skips or duplicates.
- **`dismissedAt` for lifecycle symmetry** — if `readAt` tracks when READ occurred, `dismissedAt` tracks when DISMISSED occurred. Symmetric lifecycle timestamps for all status transitions.
- **Cursor is opaque `String`** — encoding is implementation-owned. JPA encodes `(createdAt, id)` for keyset pagination; in-memory may use a different scheme.

---

## SPI Interfaces

Both in `platform-api`. **Peer contracts** — neither is derived from or subordinate to the other. Both are mandatory for every backend per the dual-variant rule (`module-tier-structure.md`): the store is consumed from both blocking (routing layer writes) and reactive (RESTEasy Reactive read path) contexts.

### Blocking SPI

```java
public interface NotificationStore {
    Notification store(NotificationInput input);
    List<Notification> storeAll(List<NotificationInput> inputs);
    NotificationPage find(NotificationQuery query);
    long unreadCount(String userId, String tenancyId);
    Optional<Notification> markRead(String id, String userId, String tenancyId);
    Optional<Notification> dismiss(String id, String userId, String tenancyId);
    int markAllRead(String userId, String tenancyId);
}
```

### Reactive SPI

```java
public interface ReactiveNotificationStore {
    Uni<Notification> store(NotificationInput input);
    Uni<List<Notification>> storeAll(List<NotificationInput> inputs);
    Uni<NotificationPage> find(NotificationQuery query);
    Uni<Long> unreadCount(String userId, String tenancyId);
    Uni<Optional<Notification>> markRead(String id, String userId, String tenancyId);
    Uni<Optional<Notification>> dismiss(String id, String userId, String tenancyId);
    Uni<Integer> markAllRead(String userId, String tenancyId);
}
```

### SPI Design Notes

- **`markRead` / `dismiss` return `Optional<Notification>`** — caller gets the updated record (for SSE push) or empty if not found / wrong tenant / wrong user. Enforcement is structural: the implementation's WHERE clause includes `user_id = ? AND tenancy_id = ?`, so authorization is SPI-enforced, not caller-dependent.
- **`userId` on mutation methods** — the SPI is injectable by any CDI bean, not only the REST layer. Without `userId` in the signature, a caller with any notification ID and the correct tenancyId could mutate another user's notification. `userId` makes user-level ownership enforcement structural at the SPI boundary.
- **`storeAll` returns `List<Notification>`** — no partial-failure semantics. A notification that failed to store is a lost notification; failures propagate immediately.
- **No `delete` operation** — retention is store-owned per `store-owned-retention-mechanism.md`.
- **Deduplication is routing-layer responsibility** — the same CloudEvent can legitimately produce multiple notifications for the same user through different subscription matches (e.g., "all work-item changes" AND "urgent items"). A store-level unique constraint on `(userId, source.eventId)` would incorrectly suppress these. The routing layer (#142) owns deduplication because it understands subscription matching semantics.
- **Mutiny `Uni<>` for reactive SPI** — `provided` scope dependency on `io.smallrye.reactive:mutiny` in platform-api (acceptable per `module-tier-structure.md` — "Mutiny is acceptable as `provided` scope — it is a thin reactive-types library with no infrastructure footprint").

---

## CDI Events

Fired by store implementations on mutations. NoOp `@DefaultBean` must NOT fire events per `noop-registry-must-not-fire-cdi-events.md`.

```java
public record NotificationCreated(Notification notification) {
    public NotificationCreated {
        Objects.requireNonNull(notification, "notification");
    }
}

public record NotificationStatusChanged(
    Notification notification,
    NotificationStatus previousStatus
) {
    public NotificationStatusChanged {
        Objects.requireNonNull(notification, "notification");
        Objects.requireNonNull(previousStatus, "previousStatus");
    }
}

public record AllNotificationsRead(
    String userId,
    String tenancyId,
    int count
) {
    public AllNotificationsRead {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(tenancyId, "tenancyId");
    }
}
```

- **Two mutation events, not one** — `NotificationCreated` pushes new notifications; `NotificationStatusChanged` pushes badge count updates. Observers subscribe to exactly what they care about.
- **`AllNotificationsRead` for bulk operations** — `markAllRead` fires one event with count, not per-notification events. Avoids N CDI events for a single bulk operation.
- **`storeAll` fires per-notification** — each notification targets a different user; SSE handler needs per-user granularity.
- **All events fired via `Event<>.fireAsync()`** — non-blocking, observer exceptions are logged but don't fail the store operation.

---

## Module Structure

### New Modules

| Folder | ArtifactId | CDI Tier | Purpose |
|--------|-----------|----------|---------|
| `notifications-inmem/` | `casehub-platform-notifications-inmem` | CDI 4 | `@Alternative @Priority(100)` — both SPIs natively. ConcurrentHashMap. Test + ephemeral installs |
| `notifications-jpa/` | `casehub-platform-notifications-jpa` | CDI 2 | `@ApplicationScoped` — both SPIs natively via Hibernate ORM + Hibernate Reactive. Flyway migrations. H2 reactive emulation for tests |
| `notifications/` | `casehub-platform-notifications` | — | REST resources + SSE push endpoint. Depends on store SPI, observes CDI events. No persistence logic (not a backend — no CDI tier) |

Tier numbers reference the CDI priority ladder (`persistence-backend-cdi-priority.md`): CDI 1 = `@DefaultBean`, CDI 2 = `@ApplicationScoped`, CDI 4 = `@Alternative @Priority(100)`. The REST module is a consumer, not a backend implementation — it has no CDI tier.

### Existing Modules (additions only)

| Module | Addition |
|--------|---------|
| `platform-api/` | `io.casehub.platform.api.notification` package — all SPI interfaces, records, enums, CDI events |
| `platform/` | `io.casehub.platform.notification` — `NoOpNotificationStore`, `NoOpReactiveNotificationStore` |

### Package Structure

```
io.casehub.platform.api.notification          ← platform-api (SPI + records)
io.casehub.platform.notification              ← platform/ (no-ops)
io.casehub.platform.notification.inmem        ← notifications-inmem/
io.casehub.platform.notification.jpa          ← notifications-jpa/
io.casehub.platform.notification.rest         ← notifications/
```

### Implementation Matrix

| Module | `NotificationStore` | `ReactiveNotificationStore` | Fires CDI events? |
|--------|--------------------|-----------------------------|-------------------|
| `platform/` (NoOp) | `@DefaultBean` no-op | `@DefaultBean` no-op | **No** |
| `notifications-inmem/` | **Native** `@Alternative @Priority(100)` | **Native** `@Alternative @Priority(100)` | Yes |
| `notifications-jpa/` | **Native** `@ApplicationScoped` | **Native** `@ApplicationScoped` | Yes |

Both SPIs are mandatory for every backend — native implementations, no bridges. This avoids the bridge anti-pattern documented in `casehubio/neocortex#101`.

### Dependency Flow

```
notifications/  ─────┐
notifications-jpa/  ──┼──► platform-api/  (SPI + records)
notifications-inmem/ ─┘
platform/  ───────────┘
```

`notifications/` depends only on the SPI — gets whichever store is on the classpath.

---

## NoOp Implementations (`platform/`)

`@DefaultBean` — active when no backend module is on the classpath. Does NOT fire CDI events.

### Blocking NoOp

Returns structurally valid records from `store()` / `storeAll()` (generated UUID, UNREAD, Instant.now()) so callers get valid return values. All queries return empty. All mutations return empty/zero.

### Reactive NoOp

Delegates to the blocking no-op. The no-op does no I/O, so delegation is free (no thread hop, no `runSubscriptionOn`). Avoids duplicating `toNotification` logic.

---

## In-Memory Implementation (`notifications-inmem/`)

`@Alternative @Priority(100)` — both SPIs implemented natively.

### Storage

`ConcurrentHashMap<String, Notification>` — thread-safe, non-blocking. Operations complete in nanoseconds, safe on the event loop.

### Reactive Implementation

Separate bean delegating to the blocking implementation. ConcurrentHashMap is non-blocking, so `Uni.createFrom().item()` without `runSubscriptionOn()` is correct — operations run on the caller's thread (event-loop safe). This is NOT the bridge anti-pattern (which uses `runSubscriptionOn(workerPool)` to offload blocking I/O).

Both interfaces cannot be on the same class — `store(NotificationInput)` has the same name and parameter but different return types (`Notification` vs `Uni<Notification>`). Two beans with delegation is the correct Java solution.

### Retention

Bounded size eviction via `casehub.notification.inmem.max-size` (default 10000). Evicts oldest by `createdAt` on insert when full. In-memory idiom per `store-owned-retention-mechanism.md`.

### No `quarkus:build` goal

Follows convention for alternative modules.

---

## JPA Implementation (`notifications-jpa/`)

`@ApplicationScoped` — both SPIs implemented natively. Hibernate ORM for blocking, Hibernate Reactive for reactive.

### Entity

`NotificationEntity` with `@Table(name = "notification")`. `NotificationSource` fields flattened to columns (`source_event_id`, `source_entity_type`, `source_entity_id`, `source_actor_id`) — simpler JPA mapping, individually indexable.

### Indexes

| Index | Columns | Purpose |
|-------|---------|---------|
| `idx_notification_user_status_created` | `(user_id, tenancy_id, status, created_at DESC)` | Primary query: unread notifications newest first |
| `idx_notification_user_category_created` | `(user_id, tenancy_id, category, created_at DESC)` | Filtered views by category |
| `idx_notification_user_created` | `(user_id, tenancy_id, created_at)` | Pagination and retention cleanup |

### Flyway Migration

`V1__notification.sql` at `classpath:db/notification/migration`. Consumers add this path to their Flyway locations.

### Cursor Pagination

Keyset pagination using `(created_at DESC, id DESC)`. Cursor encodes the last-seen `(createdAt, id)` pair. Query uses `WHERE (created_at, id) < (?, ?)`. Fetches `limit + 1` rows to detect `hasMore` and set `nextCursor`.

### Bulk Operations

`markAllRead` uses a single `UPDATE ... SET status = 'READ', read_at = NOW() WHERE user_id = ? AND tenancy_id = ? AND status = 'UNREAD'` — no per-row entity load.

`dismiss` sets `dismissed_at = NOW()` alongside `status = 'DISMISSED'`.

### Test Configuration

```properties
# DevServices auto-starts PostgreSQL testcontainer
quarkus.datasource.db-kind=postgresql
quarkus.datasource.devservices.enabled=true
quarkus.flyway.locations=classpath:db/notification/migration
quarkus.flyway.migrate-at-start=true
quarkus.flyway.clean-at-start=true
quarkus.hibernate-orm.schema-management.strategy=none
```

Uses PostgreSQL DevServices (Testcontainers), matching the `acl-jpa` pattern. H2 reactive emulation was attempted but fails with Hibernate Reactive Panache on `Instant`/`TIMESTAMP` fields (see GE-20260705-2aa4c8).

### Retention

`@Scheduled` purge with two retention windows:

- `casehub.notification.jpa.retention-days` (default 90) — deletes `READ`/`DISMISSED` notifications older than this window.
- `casehub.notification.jpa.unread-retention-days` (default 365) — deletes `UNREAD` notifications older than this window. Prevents unbounded accumulation for inactive or deprovisioned users.

JPA idiom per `store-owned-retention-mechanism.md`.

### No `quarkus:build` goal

Follows convention for non-runtime modules.

---

## REST + SSE (`notifications/`)

JAX-RS resources + SSE push. Depends only on the store SPI — no persistence awareness. Uses `ReactiveNotificationStore` (RESTEasy Reactive runs on the event loop).

### REST Endpoints

| Method | Path | Operation |
|--------|------|-----------|
| `GET` | `/notifications` | List for current user — filtered by status/category, cursor-paginated |
| `GET` | `/notifications/unread-count` | Badge count |
| `PATCH` | `/notifications/{id}/read` | Mark single notification as read |
| `PATCH` | `/notifications/{id}/dismiss` | Dismiss single notification |
| `POST` | `/notifications/mark-all-read` | Bulk mark all as read |
| `GET` | `/notifications/stream` | SSE stream for real-time push |

### REST Design Notes

- `CurrentPrincipal` provides `actorId()` and `tenancyId()` — the user never passes their own userId. Tenant isolation is enforced by the principal, not the query string.
- `markRead` / `dismiss` pass `CurrentPrincipal.actorId()` as `userId` to the SPI — user ownership is enforced at the SPI boundary, not just the REST layer.
- `markRead` / `dismiss` return 200 with updated `Notification` on success, 404 if not found, wrong tenant, or wrong user. No information leak — 404 is the same regardless of reason.
- No `DELETE` endpoint — retention is store-owned.

### Design Decision: SSE over WebSocket

Issue #135 specifies "WebSocket real-time push." This spec uses SSE (Server-Sent Events) instead.

**Rationale:** Notification push is unidirectional server-to-client. SSE provides this natively with: (1) automatic browser reconnection via the `EventSource` API — zero client-side reconnection logic, (2) standard HTTP — no protocol upgrade, works through all proxies and load balancers. WebSocket's bidirectional channel adds complexity (connection lifecycle management, heartbeat pings, custom reconnection) with no benefit — the client never sends data over the push channel. All client-to-server mutations use the REST endpoints. SSE's `Last-Event-ID` replay capability is available for future use but not implemented in Phase 1 — the reconnection strategy is "push current unread count, client re-fetches the notification list."

### SSE Push

Server-Sent Events endpoint at `/notifications/stream`. Unidirectional server-to-client push.

**Connection management:** `ConcurrentHashMap<String, Set<Emitter>>` keyed by `tenancyId::userId`. The `Set<Emitter>` is a `ConcurrentHashMap`-backed set (`Collections.newSetFromMap(new ConcurrentHashMap<>())`) — adds, removes, and iteration happen from different threads (stream establishment, CDI observers, disconnect callbacks). Supports multiple tabs/devices per user.

**Emitter lifecycle:** Each emitter registers `onClose` and `onError` callbacks during stream establishment that remove it from the connection map. When the last emitter for a `tenancyId::userId` key is removed, the key itself is removed from the outer map. This prevents unbounded growth from disconnected browsers, closed tabs, and network failures. Push failures (closed emitter) are caught per-emitter — a single dead connection does not prevent delivery to the user's other active connections.

**Principal capture:** `userId` and `tenancyId` are captured from `CurrentPrincipal` during SSE stream establishment (initial HTTP request) and stored alongside the emitter in the connection map. The `@ObservesAsync` CDI event handlers run on managed executor threads where no request context is active — they match against the stored userId/tenancyId, never against `CurrentPrincipal`. This follows the established platform pattern (see `MemoryPermissions` 3-arg `assertTenant` and `CaseLedgerEventCapture` for precedent).

**Event types pushed to client:**

| SSE event | Payload | Trigger |
|-----------|---------|---------|
| `unread-count` | `{"count": N}` | On connect, after status change, after mark-all-read |
| `notification` | Full `Notification` JSON | `@ObservesAsync NotificationCreated` |
| `notification-updated` | Full `Notification` JSON (new status) | `@ObservesAsync NotificationStatusChanged` |

**CDI event observers:**

- `@ObservesAsync NotificationCreated` → push `notification` event + `unread-count` to the user's SSE streams
- `@ObservesAsync NotificationStatusChanged` → push `notification-updated` event (full updated `Notification` record) + `unread-count` to the user's SSE streams. Pushing the updated notification eliminates multi-device staleness — when User A marks a notification as read on their phone, their desktop tab receives both the updated notification status and the new badge count.
- `@ObservesAsync AllNotificationsRead` → query `unreadCount(userId, tenancyId)` from the store and push the actual count. Does NOT assume zero — a new notification may arrive between `markAllRead` execution and the CDI event dispatch.

**Browser reconnection:** Built into the `EventSource` API — no client-side reconnection logic. On reconnect, server pushes current unread count immediately.

---

## Protocol Compliance

| Protocol | Status |
|----------|--------|
| `platform-spi-contract.md` | ✓ CDI tier ladder: NoOp=@DefaultBean, JPA=@ApplicationScoped, InMem=@Alternative @Priority(100) |
| `module-tier-structure.md` | ✓ SPI in Tier 1, Store SPI pattern, dual blocking+reactive, persistence-memory mandatory |
| `platform-api-scope.md` | ✓ Cross-module types in platform-api |
| `persistence-backend-cdi-priority.md` | ✓ Three-tier ladder |
| `store-owned-retention-mechanism.md` | ✓ InMem=bounded size, JPA=@Scheduled purge |
| `store-owned-ttl-vs-spi-ttl.md` | ✓ Retention config in implementations, not SPI signature |
| `maven-submodule-folder-naming.md` | ✓ Short names: notifications-inmem/, notifications-jpa/, notifications/ |
| `noop-registry-must-not-fire-cdi-events.md` | ✓ NoOp does NOT fire events |
| `quarkus-test-database.md` | ✓ H2 PostgreSQL mode, both JDBC + reactive URLs |
| `platform-module-progression.md` | ✓ Progressive classpath-driven adoption |
| `platform-ownership-check.md` | ✓ Platform infrastructure, no domain entity types in API |

---

## Deferred Decisions

- **Notification grouping / collapsing** — "5 comments on Work Item #42" instead of 5 separate records. Raised in #135 and #147 as an open question. Explicitly deferred to a later phase — Phase 1 stores individual notifications; grouping is a display-layer concern that can be layered on top without schema changes (the `source.entityId` + `category` fields provide the grouping key). Tracked as an open question in the epic (#147).

---

## Related Issues

- **casehubio/platform#147** — parent epic (notification system)
- **casehubio/platform#142** — subscription management (writes to this store)
- **casehubio/platform#148** — target resolution (expands groups before writing)
- **casehubio/platform#137** — DataSource SPI follow-up (deregistration needed for #142)
- **casehubio/neocortex#101** — bridge-only reactive implementations (discovered during this design)
