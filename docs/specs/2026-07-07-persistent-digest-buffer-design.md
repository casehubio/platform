# Persistent Digest Buffer — Design Spec

**Issue:** casehubio/platform#158
**Date:** 2026-07-07
**Status:** Approved

## Summary

JPA-backed `DigestBuffer` implementation in a dedicated `digest-jpa/` module, with
extraction of `InMemoryDigestBuffer` from `notification-dispatch/` into a new
`digest-inmem/` module. Follows the Store SPI pattern established by casehub-work's
`persistence-memory/` and the `persistence-backend-cdi-priority` protocol.

## Motivation

The v1 digest pipeline (#144) uses an in-memory `DigestBuffer` (`ConcurrentHashMap`).
Buffer contents are lost on process restart. For deployments where missing an external
digest delivery is unacceptable (regulated environments, auditable notification delivery),
a persistent buffer is required.

## Prerequisite: Module Extraction

`InMemoryDigestBuffer` currently lives inside `notification-dispatch/` as bare
`@ApplicationScoped`. Adding a second `@ApplicationScoped` implementation in another
module creates CDI ambiguity. Per the `cdi-classpath-presence-requires-module-separation`
protocol, the in-memory implementation must live in a separate module.

This extraction is not optional — it is mandated by protocol and is a prerequisite for
adding any alternative DigestBuffer backend.

## Module Structure

Two new modules:

| Module | Artifact | CDI Tier | Annotation | Package |
|--------|----------|----------|------------|---------|
| `digest-inmem/` | `casehub-platform-digest-inmem` | 4 (in-memory) | `@Alternative @Priority(100)` | `io.casehub.platform.delivery.digest.inmem` |
| `digest-jpa/` | `casehub-platform-digest-jpa` | 2 (primary backend) | `@ApplicationScoped` | `io.casehub.platform.delivery.digest.jpa` |

Existing modules affected:

| Module | Change |
|--------|--------|
| `notification-dispatch/` | Remove `InMemoryDigestBuffer` + its test. Add `digest-inmem` as test dep. |
| `platform/` | No change — `NoOpDigestBuffer` stays as `@DefaultBean` (Tier 1). |
| Parent `pom.xml` | Add `<module>digest-inmem</module>` and `<module>digest-jpa</module>`. |

### CDI Resolution Ladder

| Classpath state | Active impl |
|----------------|-------------|
| Nothing extra | `NoOpDigestBuffer` (@DefaultBean) |
| `digest-jpa` | `JpaDigestBuffer` (@ApplicationScoped) |
| `digest-inmem` | `InMemoryDigestBuffer` (@Alternative @Priority(100)) |
| Both | `InMemoryDigestBuffer` wins |

## Database Schema

Migration: `classpath:db/digest/migration/V2000__digest_buffer.sql`

Per the `flyway-version-range-allocation` protocol (PP-20260508-07b9f6), digest-jpa claims
V2000–V2999 in the casehub-platform allocation table. The protocol's table must be updated
to register this range.

```sql
CREATE TABLE IF NOT EXISTS digest_buffer (
    id                UUID NOT NULL PRIMARY KEY,
    user_id           VARCHAR(255) NOT NULL,
    tenancy_id        VARCHAR(255) NOT NULL,
    channel_id        VARCHAR(255) NOT NULL,
    notification_json TEXT NOT NULL,
    buffered_at       TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_digest_buffer_key
    ON digest_buffer (user_id, tenancy_id, channel_id);
```

Consumers must add `classpath:db/digest/migration` to `quarkus.flyway.locations`.

## Entity: DigestBufferEntity

- PK: `id` UUID, application-generated via `UUIDv7` (time-ordered, B-tree friendly).
- `notification_json` TEXT — Jackson serialization of `NotificationInput`.
- `buffered_at` TIMESTAMP WITH TIME ZONE — insertion time.
- `toNotificationInput()` / `fromNotificationInput()` mapping methods on the entity.
- Same ObjectMapper + Jackson pattern as `NotificationPreferencesEntity`.

### JSON Forward Compatibility

`NotificationInput` has `Objects.requireNonNull` on 6 of 8 fields. If a future change adds a
new required field, stored rows missing that field will fail to deserialize. This is accepted
as a design constraint: `NotificationInput` record changes that add required fields must include
a migration plan for the `digest_buffer` table (either a data migration to populate the new field
or a buffer drain before deployment). The digest buffer is a short-lived staging area — rows
typically live for one digest interval (minutes to hours), so coordinating a drain-before-upgrade
is practical for regulated deployments that use this module.

## JpaDigestBuffer Implementation

`@ApplicationScoped`, implements `DigestBuffer`. Injects `EntityManager`.

| SPI method | Implementation |
|-----------|----------------|
| `add(key, notification)` | `@Transactional`. INSERT one row. If `maxBufferSize > 0`, check count for key — if over limit, DELETE oldest by `buffered_at`. |
| `drain(key)` | `@Transactional`. SELECT all rows for key ordered by `buffered_at`, collect IDs, DELETE WHERE id IN (:ids), return mapped list. |
| `pendingKeys()` | `SELECT DISTINCT user_id, tenancy_id, channel_id FROM digest_buffer`. |
| `oldestPendingTimestamp(key)` | `SELECT MIN(buffered_at) FROM digest_buffer WHERE ...`. |
| `pendingCount(key)` | `SELECT COUNT(*) FROM digest_buffer WHERE ...`. |
| `pendingKeysForUser(userId, tenancyId)` | `SELECT DISTINCT ... WHERE user_id = ? AND tenancy_id = ?`. |

### Eviction

Config property: `casehub.notification.digest.max-buffer-size` (same name as InMemoryDigestBuffer).
Default: `0` (unlimited — no eviction). When > 0, `add()` trims oldest rows for the key
after insert.

### Drain Atomicity

The SPI contract: "Concurrent adds during drain must not lose items." Under PostgreSQL READ
COMMITTED, each **statement** sees the latest committed snapshot. A naive `SELECT` + `DELETE
WHERE key=?` pair is unsafe: the DELETE takes a new snapshot and may delete rows committed
between the SELECT and DELETE — rows never returned to the caller.

**Implementation:** `drain()` uses a two-step ID-scoped approach:
1. `SELECT id, notification_json, buffered_at FROM digest_buffer WHERE user_id=? AND tenancy_id=? AND channel_id=? ORDER BY buffered_at`
2. `DELETE FROM digest_buffer WHERE id IN (:ids)` — targets only the rows from step 1.

A concurrent `add()` that commits between the SELECT and DELETE is safe — its row has a different
ID and is not deleted. A concurrent `add()` that commits before the SELECT is included in the
result and deleted. No items are lost.

### No Retention Scheduler

The buffer is self-cleaning — `drain()` removes rows. Orphaned rows (keys never drained)
are pathological and better addressed by operational monitoring. Tracked separately in #167
(digest buffer orphan cleanup/retention).

## DigestFlushScheduler State

`lastFlushTimes` and `quietHoursDeferredKeys` remain in-memory ConcurrentHashMaps inside
`DigestFlushScheduler`. They are NOT part of the DigestBuffer SPI. On restart, `lastFlushTimes`
defaults to `Instant.EPOCH` — all pending items flush on the next tick. This is correct behavior:
if the server missed a scheduled flush during downtime, flush immediately on recovery.

## Dependencies

### digest-inmem/pom.xml
- `casehub-platform-api` (compile)
- `quarkus-arc` (compile)
- `junit-jupiter`, `assertj-core` (test)

### digest-jpa/pom.xml
- `casehub-platform-api` (compile)
- `quarkus-hibernate-orm-panache` (compile)
- `quarkus-flyway` (compile)
- `quarkus-jdbc-postgresql` (optional)
- `quarkus-jackson` (compile)
- `casehub-platform-testing` (test)
- `quarkus-junit` (test)
- `assertj-core` (test)

### notification-dispatch/pom.xml — new test dependency
```xml
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-platform-digest-inmem</artifactId>
    <version>${project.version}</version>
    <scope>test</scope>
</dependency>
```

## Testing

### digest-inmem/
- `InMemoryDigestBufferTest` — moved from `notification-dispatch/`. Same tests, new location.

### digest-jpa/
- `JpaDigestBufferTest` — `@QuarkusTest` with PostgreSQL dev services.
- Full SPI contract: add/drain, drain clears buffer, drain unknown key, pendingKeys,
  oldestPendingTimestamp, pendingCount, pendingKeysForUser, eviction when configured,
  no eviction when maxBufferSize=0.
- `@TestTransaction` (per memory-jpa convention).
- Test dep: `casehub-platform-testing` for `FixedCurrentPrincipal`.

### notification-dispatch/
- `DigestFlushSchedulerTest` — add `digest-inmem` test dependency. Import path changes only.
- `InMemoryDigestBufferTest` — removed (moved to `digest-inmem/`).

## Consumer Migration

After extraction, `notification-dispatch/` no longer ships `InMemoryDigestBuffer`. Consumers
that currently depend on `notification-dispatch/` and rely on digest buffering will fall through
to `NoOpDigestBuffer` (`@DefaultBean`) unless they explicitly add a digest backend.

**Affected consumer:** `notifications/` (the only compile-time consumer of `notification-dispatch`
within casehub-platform). Migration: add `digest-inmem` or `digest-jpa` as a compile dependency.

All consumers are internal casehub applications — the migration is mechanical and there are no
external users.

## Documentation Updates

### CLAUDE.md
- Add `digest-inmem/` and `digest-jpa/` to the module table.
- Update `notification-dispatch/` description to remove InMemoryDigestBuffer reference.

### ARC42STORIES.MD
- §1 Description: add `digest-inmem/` and `digest-jpa/` to module structure listing.
- §4 Solution Strategy: add layer entry for digest adapter modules.
- §5 Building Block View: add containers for both new modules with CDI tier annotations.
- §7 Deployment View: add module scope table entries:
  - `casehub-platform-digest-inmem`: `test` (isolation) or `compile` (ephemeral). Do NOT combine with `digest-jpa` in same scope.
  - `casehub-platform-digest-jpa`: `compile` + Flyway location. `classpath:db/digest/migration` required.

## Out of Scope

- #154 Guaranteed delivery and tracking — broader concern including retry/dead-letter.
- #165 Secondary index for `pendingKeysForUser` — profile first.
- #167 Digest buffer orphan row retention/cleanup — operational monitoring concern.
- #168 UUIDv7 relocation from notification package to shared utility — architectural cleanup.
- #169 InMemoryDeliveryChannelRegistry extraction from `notification-dispatch/` — same CDI
  pattern issue but not gating this work.
