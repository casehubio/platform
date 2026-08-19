# Retrofit ResourceId into AccessControlProvider — Design Spec

**Issue:** casehubio/platform#237
**Date:** 2026-08-18
**Status:** Draft
**Depends on:** #221 (ResourceId value type introduced)

## 1. Problem Statement

`AccessControlProvider` uses raw `String resourceId` parameters throughout its API.
`ResourceId(String type, String id)` was introduced in #221 as a structured value type
for resource identification — `WorkerCredential` already uses it. The ACL SPI still
uses unstructured strings, creating a type-safety gap: callers can pass malformed
resource identifiers with no compile-time or construction-time validation.

## 2. Design Decisions

| # | Decision | Choice |
|---|----------|--------|
| D1 | Retrofit scope | Full API surface — all ACL types, not just SPI methods |
| D2 | DB storage format | Keep `type:id` strings — no schema migration |

## 3. Changes

### 3.1 ResourceId — add Jackson annotations

`ResourceId` exists but lacks the `@JsonValue`/`@JsonCreator` annotations that the
#221 spec intended. Without them, Jackson serializes as `{"type":"case","id":"123"}`
instead of `"case:123"`. Add:

```java
@JsonValue
@Override
public String toString() {
    return type + ":" + id;
}

@JsonCreator
public static ResourceId parse(String value) { ... }
```

This preserves the existing REST wire format — `"case:123"` as a flat string.

### 3.2 AccessControlProvider — method signatures

Every `String resourceId` parameter becomes `ResourceId`:

```java
public interface AccessControlProvider {

    default boolean canAccess(String actorId, ResourceId resourceId, AclAction action) {
        return true;
    }

    default void grant(String actorId, ResourceId resourceId, AclAction action, Instant expires) {}

    default void revoke(String actorId, ResourceId resourceId, AclAction action) {}

    default void deny(String actorId, ResourceId resourceId, AclAction action, Instant expires) {}

    default void removeDeny(String actorId, ResourceId resourceId, AclAction action) {}

    default void revokeAll(String actorId, ResourceId resourceId) {}

    default void registerParent(ResourceId childResourceId, ResourceId parentResourceId) {}

    default List<ResourceId> accessibleResources(
        String actorId, String resourceType, AclAction action) {
        return List.of();
    }

    default AclPage accessibleResources(AclQuery query) {
        List<ResourceId> all = accessibleResources(
            query.actorId(), query.resourceType(), query.action());
        List<ResourceId> sorted = all.stream()
            .sorted(java.util.Comparator.comparing(ResourceId::toString))
            .toList();
        List<ResourceId> filtered = query.cursor() == null
            ? sorted
            : sorted.stream()
                .filter(r -> r.toString().compareTo(query.cursor()) > 0)
                .toList();
        int limit = query.limit();
        if (filtered.size() <= limit) {
            return new AclPage(filtered, null);
        }
        List<ResourceId> page = filtered.subList(0, limit);
        return new AclPage(page, page.getLast().toString());
    }

    default List<ResourceId> accessibleResourcesIncludingInherited(
        String actorId, String resourceType, AclAction action) {
        return accessibleResources(actorId, resourceType, action);
    }
}
```

Note: `resourceType` in `accessibleResources` stays as `String` — it's a type prefix
for filtering, not a resource identifier.

Batch methods (`grantBatch`, `revokeBatch`, `denyBatch`, `removeDenyBatch`) delegate
to the single-item methods via `AclEntryRequest`, which itself uses `ResourceId` (§3.3).
No signature change needed on the batch methods themselves.

### 3.3 Record types in platform-api

**AclEntryRequest:**
```java
public record AclEntryRequest(String actorId, ResourceId resourceId, AclAction action, Instant expiresAt) {}
```

**AclEntry:**
```java
public record AclEntry(
    String actorId, ResourceId resourceId, AclAction action,
    AclEntryType entryType, Instant grantedAt, Instant expiresAt, String tenancyId) {
    public boolean isExpired() { ... }
}
```

**AclPage:**
```java
public record AclPage(List<ResourceId> resourceIds, String nextCursor) {
    public AclPage { resourceIds = List.copyOf(resourceIds); }
}
```

`nextCursor` stays as `String` — it's an opaque pagination token (the `toString()`
of the last `ResourceId` in the page).

**AccessDeniedException:**
```java
public class AccessDeniedException extends SecurityException {
    private final String actorId;
    private final ResourceId resourceId;
    private final AclAction action;
    // ...
}
```

**AclQuery** — unchanged. `resourceType` is a type prefix string, `cursor` is an
opaque string. Neither is a resource identifier.

### 3.4 Admin REST DTOs (acl-admin/)

**AclEntryInput:**
```java
public record AclEntryInput(String actorId, ResourceId resourceId, AclAction action, Instant expiresAt) {}
```

**ParentInput:**
```java
public record ParentInput(ResourceId childResourceId, ResourceId parentResourceId) {}
```

With `@JsonCreator` on `ResourceId.parse()`, the REST wire format stays unchanged:
`"resourceId": "case:123"` deserializes directly to `ResourceId`.

**AclResource** query parameters (`@QueryParam("resourceId")`): JAX-RS requires a
`fromString(String)` or `valueOf(String)` static method for query param conversion.
Add `fromString` as a delegate to `parse`:

```java
public static ResourceId fromString(String value) { return parse(value); }
```

The revoke and check endpoints accept `resourceId=case:123` as before.

### 3.5 InMemoryAccessControlProvider (acl-inmem/)

Internal `GrantKey` and `ParentKey` records change from `String resourceId` to
`ResourceId resourceId`. The `accessibleResources` method changes its prefix
filtering from `entry.resourceId().startsWith(prefix)` to
`entry.resourceId().type().equals(resourceType)` — cleaner with structured access.

Wildcard resolution in `resolveAt` changes from `resourceId.indexOf(':')` string
manipulation to `new ResourceId(resourceId.type(), "*")`.

### 3.6 JpaAccessControlProvider (acl-jpa/)

Conversion at the JPA boundary:
- **Write path:** `resourceId.toString()` when constructing `AclEntryEntity`,
  `ResourceParentEntity`, and `AclAuditLogEntity`
- **Read path:** `ResourceId.parse()` when projecting from JPQL queries

JPQL queries are unchanged — they operate on the `String` column using existing
LIKE patterns. The `resourceType` prefix for LIKE is still constructed as
`escaped + ":%"`.

`resolveAt` wildcard logic: same simplification as in-memory — use
`new ResourceId(resourceId.type(), "*").toString()` for the wildcard string.

### 3.7 NoOpAccessControlProvider (platform/)

No changes needed — all methods use default implementations from the interface.

## 4. Scope Boundaries

### In scope

- `ResourceId` Jackson annotations (`@JsonValue`, `@JsonCreator`)
- `AccessControlProvider` method signatures
- `AclEntryRequest`, `AclEntry`, `AclPage`, `AccessDeniedException` record changes
- `AclEntryInput`, `ParentInput` DTO changes
- `AclResource` REST endpoint adaptation
- `InMemoryAccessControlProvider` adaptation
- `JpaAccessControlProvider` boundary conversion
- All ACL tests updated
- CLAUDE.md package structure update

### Not in scope

- `AclResourceType` — left unchanged (#238 closed, won't fix)
- DB schema migration — `type:id` strings stay as-is
- Consumer repo updates (engine, etc.) — breaking change absorbed on next version bump
- `AclQuery` / cursor format — unchanged

## 5. Testing Strategy

| Layer | Approach |
|-------|----------|
| `ResourceId` Jackson | Unit — `@JsonValue`/`@JsonCreator` round-trip via ObjectMapper |
| `AccessControlProvider` | Existing tests — adapt to use `ResourceId` constructors |
| `InMemoryAccessControlProvider` | Existing tests — adapt fixtures |
| `JpaAccessControlProvider` | Existing tests — adapt fixtures, verify `type:id` storage unchanged |
| `AclResource` (admin) | Existing tests — verify REST wire format unchanged |
| `AccessDeniedException` | Existing test — adapt to `ResourceId` |
