# ACL Authorization Model — Design Specification

**Date:** 2026-06-08
**Status:** Approved
**Scope:** Flat grant ACL SPI, instance-level enforcement, implicit inheritance, identity propagation, engine module structure
**Tracking:** platform#68
**Supersedes:** §6–§8 of `2026-06-01-acl-design.md` (open questions resolved here)

---

## 1. Context

casehub-platform provides identity (`CurrentPrincipal`, `GroupMembershipProvider`) and preferences (`PreferenceProvider`), but no authorization model below the tenancy level. The gap:

- No mechanism to answer "what data can this actor access?"
- Workers run inside the engine's trust boundary with no scoped permissions
- `CaseInstance` carries no actor identity — the initiating principal is discarded after save
- `PropagationContext.inheritedAttributes` was designed for identity propagation but is always empty in production

The prior spec (`2026-06-01-acl-design.md`) established data-level ACL, implicit inheritance, the case as the natural ACL boundary, and custom flat JPA as the default backend. This spec resolves the open questions from §6.9 and defines the complete authorization model.

---

## 2. Design Decisions

### 2.1 Flat Grant Model — Direct Actor-to-Resource Grants

Grants are direct entries in an `acl_entry` table: `(actor_id, resource_id, action, expires_at)`. No role-definition tables, no role-permission mapping. An actor either has a grant on a resource or they don't.

The `actor_id` field accepts both individual actor IDs and group-prefixed IDs (`group:<groupName>`). Group-based grants compose with `GroupMembershipProvider` at check time — the SPI resolves an actor's groups internally, not at the call site.

This model is IdP-agnostic. `CurrentPrincipal.roles()` (which defaults to `groups()`) provides the actor's group memberships from whatever identity provider is deployed. The ACL layer does not reference any specific IdP.

### 2.2 Static-First, Dynamic-Ready SPI

Grants are declared at deploy time. At runtime, the engine enforces pre-approved grants. No "permission pending" states are implemented.

The SPI contract does not preclude dynamic approval. A future authorization service could produce grants asynchronously — the `grant()` call is the same regardless of whether it happens at deploy time or after async approval.

### 2.3 SPI in platform-api, Implementation in Engine

The `AccessControlProvider` SPI lives in `platform-api` (package `io.casehub.platform.api.acl`) — zero dependencies, usable by all consuming modules (engine, work, ledger, memory). The `@DefaultBean` no-op lives in `platform/`. JPA-backed implementations live in `engine/security/`.

This follows the existing platform pattern: SPIs in `platform-api`, default beans in `platform/`, real implementations in dedicated modules.

### 2.4 Instance-Level ACL with Implicit Inheritance

Grants target specific resource instances (`case:abc-123`), not resource types. An actor who can READ `case:abc-123` cannot necessarily READ `case:def-456`. Instance isolation is the default.

Child resources inherit access from their parent via `registerParent()`. Plan items, event log entries, and work items inherit from their case. Sub-cases inherit from their parent case. The inheritance chain is walked at check time.

### 2.5 Programmatic Enforcement

All enforcement is programmatic via `AccessControlProvider.canAccess()`. No annotation-based interceptor. Consumers call the SPI explicitly at their API boundaries. This keeps enforcement transparent, debuggable, and works uniformly across REST endpoints, CDI beans, reactive pipelines, and batch jobs.

---

## 3. Action Model

Four actions cover all case operations:

| AclAction | Maps to | Typical use |
|-----------|---------|-------------|
| `READ` | query case, view plan items, event log, work items | observers, auditors |
| `WRITE` | signal, update context, assign work items | case managers |
| `ADMIN` | start case, close, suspend, resume, dispatch, modify definition | supervisors |
| `CLAIM` | claim work items for execution | workers |

Classification rationale:
- `signal` is `WRITE` — mutates running case state, common case-manager operation
- `start`, `close`, `suspend` are `ADMIN` — lifecycle/structural operations requiring elevated privilege
- `dispatch` is `ADMIN` — creates new execution, structural
- `CLAIM` is distinct — work-item-specific, separates the ability to view work from the ability to claim it

If finer granularity is needed later (e.g., separate START from CLOSE), the enum can be extended without breaking existing code — new values are additive.

---

## 4. Model Types

All types in `io.casehub.platform.api.acl` (platform-api module). Pure Java, zero framework dependencies.

### 4.1 AclAction

```java
public enum AclAction {
    READ,
    WRITE,
    ADMIN,
    CLAIM
}
```

### 4.2 AclEntry

```java
public record AclEntry(
    String actorId,
    String resourceId,
    AclAction action,
    Instant grantedAt,
    Instant expiresAt,
    String tenancyId
) {
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
```

### 4.3 AccessDeniedException

```java
public class AccessDeniedException extends SecurityException {
    private final String actorId;
    private final String resourceId;
    private final AclAction action;

    public AccessDeniedException(String actorId, String resourceId, AclAction action) {
        super("Access denied: actor=" + actorId + " resource=" + resourceId + " action=" + action);
        this.actorId = actorId;
        this.resourceId = resourceId;
        this.action = action;
    }

    public String actorId() { return actorId; }
    public String resourceId() { return resourceId; }
    public AclAction action() { return action; }
}
```

---

## 5. SPI Interface

In `platform-api`, zero dependencies:

```java
package io.casehub.platform.api.acl;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import java.time.Instant;
import java.util.List;

public interface AccessControlProvider {

    /**
     * Returns true if the actor has the given action on the resource,
     * either directly or via inheritance from a registered parent.
     */
    boolean canAccess(String actorId, String resourceId, AclAction action);

    /**
     * Reactive variant.
     */
    Uni<Boolean> canAccessReactive(String actorId, String resourceId, AclAction action);

    /**
     * Grant access. expires = null for permanent; non-null for time-bounded.
     */
    void grant(String actorId, String resourceId, AclAction action, Instant expires);

    /**
     * Revoke a specific grant.
     */
    void revoke(String actorId, String resourceId, AclAction action);

    /**
     * Revoke all grants for an actor on a resource (all actions).
     */
    void revokeAll(String actorId, String resourceId);

    /**
     * Register a parent relationship for implicit inheritance.
     * childResourceId inherits access from parentResourceId.
     */
    void registerParent(String childResourceId, String parentResourceId);

    /**
     * Return all resource IDs of the given type that the actor can access.
     * Used for filtered list APIs.
     */
    List<String> accessibleResources(String actorId, String resourceType, AclAction action);

    /**
     * Reactive variant for filtered lists.
     */
    Multi<String> accessibleResourcesReactive(String actorId, String resourceType, AclAction action);
}
```

This is the same SPI contract as the original spec (§4.4). Group expansion is internal — the implementation calls `GroupMembershipProvider` to resolve `group:*` entries. Call sites never handle group expansion.

---

## 6. Enforcement Pattern

All enforcement is programmatic. Consumers inject `AccessControlProvider` and call `canAccess()` at their API boundary:

```java
if (!accessControlProvider.canAccess(principal.actorId(), resourceId, AclAction.READ)) {
    throw new AccessDeniedException(principal.actorId(), resourceId, AclAction.READ);
}
```

No annotations, no interceptor. Transparent, debuggable, works in all contexts.

---

## 7. Identity Propagation

### 7.1 PropagationContext Carries userId + roles

`PropagationContext.inheritedAttributes` carries `userId` and `roles` as plain strings. No raw tokens, no token references, no expiry concerns.

```java
// At root case creation in CaseHubReactor:
Map<String, String> identity = Map.of(
    "userId", currentPrincipal.actorId(),
    "roles", String.join(",", currentPrincipal.roles())
);
PropagationContext.createRoot(traceId, identity, budget);
```

`createChild()` propagates all attributes from parent to child. Sub-cases and workers inherit the initiating principal's identity automatically.

### 7.2 Dispatch Identity via PropagationContext

`CasehubDispatch` threads caller identity through `PropagationContext`, not through `WorkRequest`. `WorkRequest` remains `record WorkRequest(String capability, Map<String, Object> input)` — no identity fields added.

The dispatched worker receives identity from the `PropagationContext` of its parent execution context. This is consistent with `PropagationContext`'s design intent and avoids coupling `WorkRequest` to identity concerns.

### 7.3 External Token Delegation

External service token delegation (§6.8 in the original spec) is a separate concern from internal identity propagation. The `PropagationContext` carries identity for internal ACL checks. External delegation (OAuth 2.0 on-behalf-of, static credentials, `authRef`) uses quarkus-flow's existing token patterns and is out of scope for this spec.

---

## 8. Multi-Tenancy

ACL checks use the **tenant of the current resource**, not the actor's home tenant. When a worker running under `userId=alice` (from tenant A) accesses a resource in tenant B (e.g., a cross-tenant sub-case), the ACL check queries `acl_entry` rows where `tenancy_id` matches the resource's tenant.

This is consistent with the original spec's §3.6: tenant isolation is a data layer filter applied before ACL is consulted. ACL operates within already-tenancy-filtered data. The `tenancy_id` on `acl_entry` is for operational filtering and auditing.

Cross-tenant access requires explicit grants in the target tenant. `CurrentPrincipal.isCrossTenantAdmin()` can bypass this for administrative operations.

---

## 9. Module Structure

### 9.1 platform-api (extension)

Extends the existing `platform-api` module with:
- Package `io.casehub.platform.api.acl`
- Model types: `AclAction`, `AclEntry`, `AccessDeniedException`
- SPI interface: `AccessControlProvider`

### 9.2 platform/ (extension)

`@DefaultBean` no-op `AccessControlProvider`:
- `canAccess()` → always `true` (allow-all)
- `canAccessReactive()` → `Uni.createFrom().item(true)`
- `grant()`, `revoke()`, `revokeAll()`, `registerParent()` → no-op
- `accessibleResources()` → empty list
- `accessibleResourcesReactive()` → `Multi.createFrom().empty()`

Consistent with the existing `@DefaultBean` pattern (`NoOpCaseMemoryStore`, `NoOpWorkerProvisioner`).

### 9.3 engine/security/security-noop (new module in engine repo)

`casehub-engine-security-noop` — reserved for engine-specific no-op extensions if needed. May not be required if the platform-level `@DefaultBean` suffices.

### 9.4 engine/security/security-impl (new module in engine repo)

`casehub-engine-security`

`@Alternative @Priority(1)` JPA-backed implementation:
- `JpaAccessControlProvider` — queries `acl_entry` and `resource_parent` tables. Composes with `GroupMembershipProvider` (from platform-api) for group-based grant resolution.

Dependencies:
- `platform-api` (`AccessControlProvider` SPI, `CurrentPrincipal`, `GroupMembershipProvider`)
- Quarkus Hibernate ORM, Flyway

Flyway location: `classpath:db/security/migration`

---

## 10. JPA Schema

Flyway migration `V1__acl_schema.sql` in `security-impl`:

```sql
CREATE TABLE acl_entry (
    id          BIGSERIAL PRIMARY KEY,
    actor_id    VARCHAR(255) NOT NULL,
    resource_id VARCHAR(255) NOT NULL,
    action      VARCHAR(50)  NOT NULL,
    condition   TEXT         NULL,
    granted_at  TIMESTAMP    NOT NULL DEFAULT now(),
    expires_at  TIMESTAMP    NULL,
    tenancy_id  VARCHAR(64)  NOT NULL,

    CONSTRAINT uq_acl_entry UNIQUE (actor_id, resource_id, action)
);

CREATE TABLE resource_parent (
    child_resource_id  VARCHAR(255) NOT NULL,
    parent_resource_id VARCHAR(255) NOT NULL,
    tenancy_id         VARCHAR(64)  NOT NULL,
    PRIMARY KEY (child_resource_id)
);

CREATE INDEX idx_acl_actor_resource ON acl_entry (actor_id, resource_id);
CREATE INDEX idx_acl_resource       ON acl_entry (resource_id);
CREATE INDEX idx_acl_tenancy        ON acl_entry (tenancy_id);
CREATE INDEX idx_rp_parent          ON resource_parent (parent_resource_id);
```

Key differences from the old spec:
- **`acl_entry`** replaces `role_definition` + `role_permission` — flat grants, not role mappings
- **`resource_parent`** is created immediately (not reserved/commented out) — instance-level inheritance is active from day one
- **`expires_at`** enables time-bounded grants for worker access
- **`condition`** is reserved for future ABAC; not evaluated initially
- **No `role_binding` table** — grants are direct, not mediated through roles

---

## 11. Check Algorithm

### 11.1 canAccess(actorId, resourceId, action)

```
1. Resolve actor's groups via GroupMembershipProvider
2. Check acl_entry for a matching row:
   (actor_id = actorId OR actor_id IN actor's groups as 'group:<name>')
   AND resource_id = resourceId
   AND action = action
   AND (expires_at IS NULL OR expires_at > now())
3. If no match, look up resource_parent for resourceId
4. If parent exists, repeat from step 2 with parent_resource_id
5. Walk up until match found or chain exhausted
6. Return result
```

This is an **instance-level check**: "does this actor have a grant for this specific resource?" The grant is on `case:abc-123`, not on "all cases." Instance isolation is the default.

### 11.2 accessibleResources(actorId, resourceType, action)

```sql
SELECT DISTINCT e.resource_id
FROM acl_entry e
WHERE e.action = :action
  AND (e.expires_at IS NULL OR e.expires_at > now())
  AND (
    e.actor_id = :actorId
    OR e.actor_id IN (:actorGroupPrefixed)
  )
  AND e.resource_id LIKE :resourceTypePrefix
```

Returns directly-granted resource IDs. Sub-resources that inherit access from a parent are not returned directly — the caller traverses hierarchies via the engine's existing relationships.

### 11.3 Instance-Level vs Type-Level

This spec defines **instance-level ACL**: grants target specific resource instances. An actor who can READ `case:abc-123` cannot automatically READ `case:def-456`.

If type-level grants are needed later (e.g., "all case-managers can READ all cases in their tenant"), a wildcard resource ID pattern (`case:*`) or a separate type-grant table can be added. The SPI does not need to change — `canAccess()` would compose instance-level and type-level checks internally.

---

## 12. Resolved Open Questions

This spec resolves all 6 open questions from `2026-06-01-acl-design.md` §6.9:

| # | Question | Resolution |
|---|----------|------------|
| §6.9.1 | Flat grant vs role-based bindings | **Flat grants.** `acl_entry(actor_id, resource_id, action, expires_at)`. No role-definition tables. Direct grants on specific resource instances. |
| §6.9.2 | Static vs dynamic permission requests | **Static-first, dynamic-ready.** Deploy-time grants. SPI shape (`grant()`/`revoke()`) allows future dynamic approval without changes. |
| §6.9.3 | What PropagationContext carries | **userId + roles as strings.** No raw tokens, no token references. External delegation is a separate concern using quarkus-flow's existing patterns. |
| §6.9.4 | Where authorization service SPI lives | **platform-api** (SPI). Implementations in engine. All consuming modules (work, ledger, memory) depend on platform-api for the `AccessControlProvider` contract. |
| §6.9.5 | How dispatch threads identity | **PropagationContext.** `CasehubDispatch` threads identity via `PropagationContext.inheritedAttributes`, not by adding fields to `WorkRequest`. |
| §6.9.6 | Multi-tenancy intersection | **Tenant of current resource.** ACL checks query grants in the resource's tenant, not the actor's home tenant. Cross-tenant access requires explicit grants. |

---

## 13. Engine Issues

### 13.1 PropagationContext Identity Wiring

Populate `PropagationContext.inheritedAttributes` with `userId` and `roles` at `CaseHubReactor.createRoot()`. Currently all `createRoot()` call sites pass `Map.of()` (empty). Wire `currentPrincipal.actorId()` and `currentPrincipal.roles()` into the attributes map.

### 13.2 CasehubDispatch Identity Threading

Update `CasehubDispatch` to thread identity from the current `PropagationContext` when dispatching workers. Currently `WorkRequest.of(capability, Map.of())` carries no identity. The fix is in PropagationContext propagation, not in WorkRequest.

### 13.3 engine/security Module Creation

Create `engine/security/security-impl/` module with pom.xml, JPA entities, Flyway migrations, and `JpaAccessControlProvider` implementation. Depends on `platform-api` for the `AccessControlProvider` SPI and `GroupMembershipProvider`.

### 13.4 AccessControlProvider SPI + Default Bean

Add `AccessControlProvider`, `AclAction`, `AclEntry`, `AccessDeniedException` to `platform-api`. Add `@DefaultBean` no-op to `platform/`.

### 13.5 Case Definition YAML Authorization Extension

Extend case definition YAML schema with an `authorization` section that declares which actor groups are required for each operation (READ, WRITE, ADMIN, CLAIM) on the case and its resources. IdP-agnostic — references group names, not IdP-specific constructs.

---

## 14. Design Decisions Summary

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Grant model | Flat grants `(actor, resource, action)` | Simpler than role-based; sufficient for instance-level case ACL; no role-mapping tables |
| Identity source | IdP-agnostic via `CurrentPrincipal` | `CurrentPrincipal.roles()` already wired; no IdP coupling |
| ACL code location | SPI in platform-api, impl in engine | Authorization is a platform concern; all modules need the SPI; engine provides JPA backend |
| Grant timing | Static-first, dynamic-ready | Deploy-time grants; `grant()`/`revoke()` SPI allows future async approval |
| Action model | READ, WRITE, ADMIN, CLAIM | Covers all case operations; extensible via enum addition |
| Enforcement | Programmatic `canAccess()` | Transparent, debuggable, works in all contexts |
| Default behavior | NoOp allow-all `@DefaultBean` | Consistent with platform pattern; safe for environments without ACL |
| Resource hierarchy | Implicit inheritance via `resource_parent` | Write-path minimal; case children inherit; consistent with prior spec §3.3 |
| Time-bounded grants | `expires_at` on `acl_entry` | Worker access naturally scoped to execution lifetime |
| Identity propagation | userId + roles in PropagationContext | Strings, no expiry issues, cascades via `createChild()` |
| Dispatch identity | PropagationContext, not WorkRequest | WorkRequest stays data-only; identity is a cross-cutting concern |
| Multi-tenancy | Tenant of current resource | Consistent with data-layer tenant filtering; cross-tenant needs explicit grants |

---

## 15. Relationship to Prior Spec

The prior spec's §1–§5 (motivation, research, core design decisions, resource identity, platform module design) remain valid and are not superseded. Specifically:

- §3.1 Data-level enforcement — unchanged
- §3.2 Custom flat JPA — unchanged
- §3.3 Implicit inheritance — unchanged, now active from day one
- §3.4 Case as ACL boundary — unchanged
- §3.5 Sub-case cascade — unchanged
- §3.6 Multi-tenancy as data layer filter — unchanged
- §4.1 Resource identity format — unchanged
- §4.2 Actions — unchanged
- §4.3 ACL table schema — unchanged (adopted as-is)
- §4.4 SPI interface — unchanged (adopted as-is)
- §5 Module design — SPI in platform-api (as original spec specified)

This spec updates the module structure from the prior spec:
- Prior: `acl-jpa/` and `acl-inmem/` as platform modules
- Current: JPA implementation in `engine/security/security-impl/` (engine repo, not platform repo)

The `acl-inmem/` module for `@QuarkusTest` isolation remains valid and will follow the `memory-inmem/` pattern. It can live in either platform or engine depending on test infrastructure needs.

---

## 16. References

- Prior spec: `docs/specs/2026-06-01-acl-design.md`
- `CurrentPrincipal`: `platform-api/src/main/java/io/casehub/platform/api/identity/CurrentPrincipal.java`
- `GroupMembershipProvider`: `platform-api/src/main/java/io/casehub/platform/api/identity/GroupMembershipProvider.java`
- `PropagationContext`: `engine/api/src/main/java/io/casehub/api/context/PropagationContext.java`
- `CaseHubReactor`: `engine/runtime/src/main/java/io/casehub/engine/internal/engine/CaseHubReactor.java`
- `CasehubDispatch`: `engine/flow/src/main/java/io/casehub/engine/flow/CasehubDispatch.java`
- `NoOpCaseMemoryStore`: `platform/src/main/java/io/casehub/platform/memory/NoOpCaseMemoryStore.java`
- Platform module patterns: `persistence-jpa/`, `memory-jpa/`, `memory-inmem/`, `scim/`
- Tracking issue: platform#68
