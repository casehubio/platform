# ACL Authorization Model — Design Specification

**Date:** 2026-06-08
**Status:** Approved
**Scope:** Role-based access control SPI, Keycloak integration, operation-level enforcement, engine module structure
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

### 2.1 Role-Based Bindings via Keycloak Groups

Roles are managed in Keycloak. `CurrentPrincipal.roles()` (which defaults to `groups()`) provides the actor's roles from the OIDC token. The platform does not maintain a role-binding table — Keycloak owns who has which role.

The ACL layer maps Keycloak group names to resource-level permissions. This mapping is stored in the engine's database (`role_definition` + `role_permission` tables). The flow:

```
Keycloak groups → OIDC token → CurrentPrincipal.roles()
                                       ↓
                              ACL layer resolves:
                              role → role_definition → role_permission
                                       ↓
                              Permission(resourceType, action) checked
                              against requested (resource, action)
```

### 2.2 Static-First, Dynamic-Ready SPI

Permissions are declared at deploy time and approved before deployment. At runtime, the engine enforces pre-approved grants. No "permission pending" states are implemented.

The SPI contract does not preclude dynamic approval. A future `authorizationService.requestBinding()` could produce role definitions asynchronously — the `RoleManager.defineRole()` call is the same regardless of whether it happens at deploy time or after async approval.

### 2.3 All Code in Engine

All ACL code — SPIs, model types, implementations, interceptor — lives in the `casehub-engine` repository. Other modules (work, ledger, memory) depend on `engine-api` for the ACL contract. `CurrentPrincipal` and `GroupMembershipProvider` remain in `platform-api`.

### 2.4 Programmatic Enforcement

All enforcement is programmatic via `AccessControlProvider.canAccess()`. No annotation-based interceptor. Consumers call the SPI explicitly at their API boundaries. This keeps enforcement transparent, debuggable, and works uniformly across REST endpoints, CDI beans, reactive pipelines, and batch jobs.

---

## 3. Action Model

Four actions cover all case operations:

| AclAction | Maps to | Typical role |
|-----------|---------|--------------|
| `READ` | query case, view plan items, event log, work items | case-viewer, auditor |
| `WRITE` | signal, update context, assign work items | case-manager |
| `ADMIN` | start case, close, suspend, resume, dispatch, modify definition | supervisor |
| `CLAIM` | claim work items for execution | worker |

Classification rationale:
- `signal` is `WRITE` — mutates running case state, common case-manager operation
- `start`, `close`, `suspend` are `ADMIN` — lifecycle/structural operations requiring elevated privilege
- `dispatch` is `ADMIN` — creates new execution, structural
- `CLAIM` is distinct — work-item-specific, separates the ability to view work from the ability to claim it

If finer granularity is needed later (e.g., separate START from CLOSE), the enum can be extended without breaking existing code — new values are additive.

---

## 4. Model Types

All types in `io.casehub.engine.api.acl` (engine-api module). Pure Java, zero framework dependencies.

### 4.1 AclAction

```java
public enum AclAction {
    READ,
    WRITE,
    ADMIN,
    CLAIM
}
```

### 4.2 ResourceType

```java
public enum ResourceType {
    CASE,
    PLAN_ITEM,
    WORK_ITEM,
    EVENT_LOG,
    MEMORY,
    CASE_DEFINITION
}
```

### 4.3 ResourceId

Typed resource identifier. Renders as `case:abc-123`, `workitem:def-456`.

```java
public record ResourceId(ResourceType type, String id) {

    public String value() {
        return type.name().toLowerCase().replace("_", "") + ":" + id;
    }

    public static ResourceId parse(String value) {
        int colon = value.indexOf(':');
        if (colon < 0) throw new IllegalArgumentException("Invalid resource ID: " + value);
        String prefix = value.substring(0, colon).toUpperCase();
        String id = value.substring(colon + 1);
        // map prefix to ResourceType
        ResourceType type = switch (prefix) {
            case "CASE" -> ResourceType.CASE;
            case "PLANITEM" -> ResourceType.PLAN_ITEM;
            case "WORKITEM" -> ResourceType.WORK_ITEM;
            case "EVENTLOG" -> ResourceType.EVENT_LOG;
            case "MEMORY" -> ResourceType.MEMORY;
            case "CASEDEFINITION" -> ResourceType.CASE_DEFINITION;
            default -> throw new IllegalArgumentException("Unknown resource type: " + prefix);
        };
        return new ResourceId(type, id);
    }
}
```

### 4.4 Permission

A `(resourceType, action)` pair granted by a role.

```java
public record Permission(ResourceType resourceType, AclAction action) {}
```

### 4.5 Role

A named set of permissions.

```java
public record Role(String name, Set<Permission> permissions) {
    public Role {
        permissions = Set.copyOf(permissions);
    }
}
```

### 4.6 AccessDeniedException

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

## 5. SPI Interfaces

### 5.1 AccessControlProvider — Enforcement

What most modules inject. Answers "can this actor do this?"

```java
package io.casehub.engine.api.acl;

import java.util.List;

public interface AccessControlProvider {

    boolean canAccess(String actorId, String resourceId, AclAction action);

    boolean hasRole(String actorId, String roleName);

    List<String> accessibleResources(String actorId, ResourceType resourceType, AclAction action);

    /** Reserved for future instance-level ACL. No-op in initial implementation. */
    void registerParent(String childResourceId, String parentResourceId);
}
```

**`canAccess`** — resolves actor's roles via `CurrentPrincipal`, expands permissions via role definitions, checks against (resourceType, action). Type-level check.

**`hasRole`** — checks if any of the actor's Keycloak groups match the named role.

**`accessibleResources`** — capability check: does the actor have any role granting the required action on the given resource type? Returns matching role names; the caller queries the data layer for actual instances.

**`registerParent`** — reserved for future instance-level ACL. No-op in initial implementation. Will register parent→child relationships for implicit inheritance when instance-level grants are added.

### 5.2 RoleManager — Permission Mapping Management

Used by admin tools and the authorization service to manage what each Keycloak group can do.

```java
package io.casehub.engine.api.acl;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface RoleManager {

    void defineRole(String roleName, Set<Permission> permissions);

    void removeRole(String roleName);

    Optional<Role> getRole(String roleName);

    List<Role> listRoles();
}
```

**No `bindRole`/`unbindRole`** — Keycloak handles role assignment to actors.

**`defineRole`** — maps a Keycloak group name to a set of resource-level permissions. Overwrites if the role already exists.

### 5.3 Tenancy Handling

Neither SPI takes `tenancyId` as a parameter. Implementations resolve `tenancyId` internally via `CurrentPrincipal.tenancyId()` and apply it as a data-layer filter. This is consistent with `CaseMemoryStore`.

### 5.4 Group Expansion

`canAccess` takes `actorId`, not groups. The implementation composes with `GroupMembershipProvider` internally to resolve group-based checks. Call sites never handle group expansion.

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

## 7. Module Structure

All modules in the `casehub-engine` repository.

### 7.1 engine/api (extension)

Extends the existing `engine-api` module with:
- Package `io.casehub.engine.api.acl`
- All model types: `AclAction`, `ResourceType`, `ResourceId`, `Permission`, `Role`, `AccessDeniedException`
- SPI interfaces: `AccessControlProvider`, `RoleManager`

### 7.2 engine/security/security-noop (new module)

`casehub-engine-security-noop`

`@DefaultBean` implementations:
- `NoOpAccessControlProvider`: `canAccess()` → always `true` (allow-all). `hasRole()` → always `false`. `accessibleResources()` → empty list. `registerParent()` → no-op.
- `NoOpRoleManager`: `defineRole()` → no-op. `removeRole()` → no-op. `getRole()` → `Optional.empty()`. `listRoles()` → empty list.

### 7.3 engine/security/security-impl (new module)

`casehub-engine-security`

`@Alternative @Priority(1)` JPA-backed implementations:
- `JpaAccessControlProvider` — queries `role_definition`, `role_permission`, `resource_parent` tables. Composes with `CurrentPrincipal` (from platform-api) and `GroupMembershipProvider` (from platform-api).
- `JpaRoleManager` — CRUD on `role_definition` and `role_permission` tables.

Dependencies:
- `engine-api` (SPIs)
- `platform-api` (`CurrentPrincipal`, `GroupMembershipProvider`)
- Quarkus Hibernate ORM, Flyway

Flyway location: `classpath:db/security/migration`

---

## 8. JPA Schema

Flyway migration `V1__acl_schema.sql` in `security-impl`:

```sql
CREATE TABLE role_definition (
    id          BIGSERIAL PRIMARY KEY,
    role_name   VARCHAR(255) NOT NULL UNIQUE,
    tenancy_id  VARCHAR(64)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE role_permission (
    id                 BIGSERIAL PRIMARY KEY,
    role_definition_id BIGINT       NOT NULL REFERENCES role_definition(id) ON DELETE CASCADE,
    resource_type      VARCHAR(50)  NOT NULL,
    action             VARCHAR(50)  NOT NULL,
    CONSTRAINT uq_role_perm UNIQUE (role_definition_id, resource_type, action)
);

-- Reserved for future instance-level ACL (not created in initial migration)
-- CREATE TABLE resource_parent (
--     child_resource_id  VARCHAR(255) NOT NULL,
--     parent_resource_id VARCHAR(255) NOT NULL,
--     tenancy_id         VARCHAR(64)  NOT NULL,
--     PRIMARY KEY (child_resource_id)
-- );

CREATE INDEX idx_rd_tenancy ON role_definition (tenancy_id);
CREATE INDEX idx_rp_role ON role_permission (role_definition_id);
```

No `role_binding` table — Keycloak handles role assignment.

---

## 9. Check Algorithm

### 9.1 canAccess(actorId, resourceId, action)

```
1. Resolve actor's roles via CurrentPrincipal.roles()
2. Parse resourceId → ResourceType (e.g., "case:abc-123" → CASE)
3. For each role:
   a. Look up role_definition by role_name + tenancy_id
   b. Expand role_permission to get Set<Permission>
   c. If any Permission matches (resourceType, action) → GRANT
4. No match → DENY
```

This is a type-level check: "does any of the actor's roles grant this action on this resource type?" The specific resource instance (`abc-123`) is not consulted — if the actor's roles permit READ on CASE, they can read any case in their tenant.

### 9.2 accessibleResources(actorId, resourceType, action)

This is a **capability check**, not an instance enumeration. It answers: "does this actor have any role granting `action` on `resourceType`?" If yes, the caller queries the data layer for actual resource instances (filtered by tenancy and visibility).

```sql
SELECT DISTINCT rd.role_name
FROM role_definition rd
JOIN role_permission rp ON rp.role_definition_id = rd.id
WHERE rd.role_name IN (:actorRoles)
  AND rd.tenancy_id = :tenancyId
  AND rp.resource_type = :resourceType
  AND rp.action = :action
```

The implementation composes this capability check with the engine's data layer: if the actor has the capability, the engine returns all resources of that type within the actor's tenant. Instance-level filtering (which specific cases within the tenant) is done by the data layer, not the ACL layer.

### 9.3 Type-Level vs Instance-Level

This spec defines **type-level RBAC**: roles grant permissions on resource types (e.g., "case-managers can WRITE CASE resources"), not on specific resource instances. Instance-level isolation is provided by:

- **Tenant filtering** — `tenancy_id` on all entities scopes visibility to the actor's tenant
- **Case hierarchy** — the engine's existing `parentCaseId` relationships scope sub-case visibility

If instance-level ACL is needed later (e.g., "actor X can only access case:abc-123, not case:def-456"), the `resource_parent` table and a new resource-grant table can be added without changing the SPI — `canAccess()` would compose type-level capability with instance-level grants. The SPI is shaped to accommodate this.

---

## 10. Relationship to Prior Spec

This spec resolves the open questions from `2026-06-01-acl-design.md` §6.9:

| Question | Resolution |
|----------|------------|
| §6.9.1 — Flat grant vs role-based bindings | **Role-based.** Keycloak groups map to role definitions with permission sets. |
| §6.9.2 — Static vs dynamic permission requests | **Static-first.** Deploy-time role definitions. SPI does not preclude dynamic. |
| §6.9.3 — What PropagationContext carries | **actorId + roles** (from CurrentPrincipal). No raw token. External delegation is a separate concern. |
| §6.9.4 — Where authorization service SPI lives | **engine-api.** Both `AccessControlProvider` and `RoleManager` in engine. |
| §6.9.5 — How casehub:dispatch threads identity | **Engine issue filed.** WorkRequest needs identity carrier; PropagationContext needs wiring. |
| §6.9.6 — Multi-tenancy intersection | **Tenant-scoped role definitions.** `role_definition.tenancy_id` scopes role definitions per tenant. Cross-tenant roles require cross-tenant admin. |

This spec also updates the module structure from the prior spec:
- Prior: SPIs in `platform-api`, implementations in `acl-jpa/` and `acl-inmem/` platform modules
- Current: All ACL code in engine — SPIs in `engine-api`, implementations in `engine/security/`

The prior spec's §1–§5 (motivation, research, core design decisions, resource identity, case as ACL boundary, implicit inheritance, sub-case cascade, multi-tenancy separation) remain valid and are not superseded.

---

## 11. Engine Issues to File

### 11.1 PropagationContext Identity Wiring

Populate `PropagationContext.inheritedAttributes` with `userId` and `roles` at `CaseHubReactor.createRoot()`. Currently all `createRoot()` call sites pass `Map.of()` (empty). Wire `currentPrincipal.actorId()` and `currentPrincipal.roles()` into the attributes map.

### 11.2 WorkRequest Identity Carrier

Add identity fields to `WorkRequest` so that `CasehubDispatch.dispatch()` threads caller identity to dispatched workers. Currently `WorkRequest.of(capability, Map.of())` carries no identity.

### 11.3 engine/security Module Creation

Create `engine/security/security-noop/` and `engine/security/security-impl/` modules with pom.xml, default beans, JPA entities, Flyway migrations, and interceptor implementation.

### 11.4 Case Definition YAML Authorization Extension

Extend case definition YAML schema with an `authorization` section that declares which Keycloak groups are required for each operation (READ, WRITE, ADMIN, CLAIM) on the case and its resources.

---

## 12. Design Decisions Summary

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Role source | Keycloak groups via OIDC | CurrentPrincipal.roles() already wired; no separate binding table |
| Permission mapping storage | Engine DB (role_definition + role_permission) | Maps Keycloak groups to resource-level permissions; manageable at deploy time |
| ACL code location | Engine repo (engine-api, engine/security/) | Authorization is tightly coupled to case execution; other modules depend on engine-api |
| Grant timing | Static-first, dynamic-ready | Deploy-time role definitions; SPI shape allows future async approval |
| Action model | READ, WRITE, ADMIN, CLAIM | Covers all case operations; extensible via enum addition |
| Enforcement | Programmatic canAccess() | Transparent, debuggable, works in all contexts |
| SPI split | AccessControlProvider (check) + RoleManager (manage) | Enforcement consumers never see management; each SPI is focused |
| Default behavior | NoOp allow-all | Consistent with platform @DefaultBean pattern; safe for environments without ACL |
| Resource hierarchy | Implicit inheritance via resource_parent | Write-path minimal; case children inherit; consistent with prior spec §3.3 |

---

## 13. References

- Prior spec: `docs/specs/2026-06-01-acl-design.md`
- `CurrentPrincipal`: `platform-api/src/main/java/io/casehub/platform/api/identity/CurrentPrincipal.java`
- `GroupMembershipProvider`: `platform-api/src/main/java/io/casehub/platform/api/identity/GroupMembershipProvider.java`
- `PropagationContext`: `engine/api/src/main/java/io/casehub/api/context/PropagationContext.java`
- `CaseHubReactor`: `engine/runtime/src/main/java/io/casehub/engine/internal/engine/CaseHubReactor.java`
- `CasehubDispatch`: `engine/flow/src/main/java/io/casehub/engine/flow/CasehubDispatch.java`
- Tracking issue: platform#68
