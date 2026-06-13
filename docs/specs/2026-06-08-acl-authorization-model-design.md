# ACL Authorization Model — Design Specification

**Date:** 2026-06-09
**Status:** Draft
**Scope:** Data-level ACL for external actors, identity propagation, worker rights roadmap
**Tracking:** platform#68

---

## 1. Context

casehub-platform provides identity (`CurrentPrincipal`, `GroupMembershipProvider`) and preferences (`PreferenceProvider`), but no authorization model below the tenancy level. The gap:

- No mechanism to answer "what data can this actor access?"
- `CaseInstance` carries no actor identity — the initiating principal is discarded after save
- `PropagationContext.inheritedAttributes` was designed for identity propagation but is always empty in production

This spec defines the ACL model in three phases:
- **Phase 1 — ACL Core**: resource model, permission model, access control enforcement for external actors interacting with cases via API
- **Phase 2 — Identity Propagation**: wire actor identity through the engine's execution hierarchy so ACL enforcement works for internal paths
- **Phase 3 — Worker Rights**: roadmap framing only — not implementation-ready

---

# Phase 1 — ACL Core

Scope: Define the resource model, permission model, and access control enforcement for external actors interacting with cases via API. No identity propagation. No worker grants.

---

## 2. Design Decisions

### 2.1 Flat Grant Model — Direct Actor-to-Resource Grants

Grants are direct entries in an `acl_entry` table: `(actor_id, resource_id, action, expires_at)`. No role-definition tables, no role-permission mapping. An actor either has a grant on a resource or they don't.

The `actor_id` field accepts both individual actor IDs and group-prefixed IDs (`group:<groupName>`). Group-based grants compose with `GroupMembershipProvider` at check time — the SPI resolves an actor's groups internally, not at the call site.

This model is IdP-agnostic. `CurrentPrincipal.roles()` (which defaults to `groups()`) provides the actor's group memberships from whatever identity provider is deployed. The ACL layer does not reference any specific IdP.

### 2.2 Static-First, Dynamic-Ready SPI

Grants are declared at deploy time. At runtime, the engine enforces pre-approved grants. No "permission pending" states are implemented.

The SPI contract does not preclude dynamic approval. A future authorization service could produce grants asynchronously — the `grant()` call is the same regardless of whether it happens at deploy time or after async approval.

### 2.3 SPI in platform-api, Implementation in acl-jpa/

The `AccessControlProvider` SPI lives in `platform-api` (package `io.casehub.platform.api.acl`) — zero dependencies, usable by all consuming modules (engine, work, ledger, memory). The `@DefaultBean` no-op lives in `platform/`. JPA-backed implementation lives in `acl-jpa/`.

This follows the existing platform pattern: SPIs in `platform-api`, default beans in `platform/`, real implementations in dedicated modules.

### 2.4 Instance-Level ACL with Implicit Inheritance

Grants target specific resource instances (`case:abc-123`), not resource types. An actor who can READ `case:abc-123` cannot necessarily READ `case:def-456`. Instance isolation is the default.

Child resources inherit access from their parent via `registerParent()`. Plan items, event log entries, and work items inherit from their case. Sub-cases inherit from their parent case. The inheritance chain is walked at check time.

### 2.5 Programmatic Enforcement

All enforcement is programmatic via `AccessControlProvider.canAccess()`. No annotation-based interceptor. Consumers call the SPI explicitly at their API boundaries. This keeps enforcement transparent, debuggable, and works uniformly across REST endpoints, CDI beans, reactive pipelines, and batch jobs.

### 2.6 Multi-Tenancy

ACL checks use the **tenant of the current resource**, not the actor's home tenant. Tenant isolation is a data layer filter applied before ACL is consulted. ACL operates within already-tenancy-filtered data. The `tenancy_id` on `acl_entry` is for operational filtering and auditing.

Cross-tenant access requires explicit grants in the target tenant. `CurrentPrincipal.isCrossTenantAdmin()` can bypass this for administrative operations.

### 2.7 Sub-Case Cascade

Access to a parent case cascades to all its sub-cases by default. A sub-case may not be accessed unless the actor has access to the root of the case tree (directly or via cascade). Override by exception only — a concrete use case must justify blocking cascade before that mechanism is designed.

Cascade is implemented via `registerParent()` at sub-case creation time.

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

### 3.1 CaseHubRuntime Operation Mapping

Mapping of `CaseHubRuntime` API methods to `AclAction`:

| Method | AclAction | Resource | Notes |
|--------|-----------|----------|-------|
| `startCase(definition, ...)` | ADMIN | `casedefinition:<id>` | Case instance does not exist yet — ACL check is against the case definition |
| `signal(caseId, path, value)` | WRITE | `case:<caseId>` | Mutates running case state |
| `cancelCase(caseId)` | ADMIN | `case:<caseId>` | Terminal lifecycle operation |
| `suspendCase(caseId)` | ADMIN | `case:<caseId>` | Stops execution |
| `resumeCase(caseId)` | ADMIN | `case:<caseId>` | Resumes execution |
| `query(caseId, ...)` | READ | `case:<caseId>` | Reads case state |
| `eventLog(caseId, ...)` | READ | `case:<caseId>` | Reads event log — inherits from case |

`CLAIM` does not appear in `CaseHubRuntime` — it is a `casehub-work` operation for work item claiming.

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

### 4.2 AclResourceType

```java
public final class AclResourceType {

    public static final String CASE = "case";
    public static final String PLAN_ITEM = "planitem";
    public static final String WORK_ITEM = "workitem";
    public static final String EVENT_LOG = "eventlog";
    public static final String CASE_DEFINITION = "casedefinition";

    private AclResourceType() {}
}
```

Resource IDs are formed as `type:id` — e.g. `case:abc-123`, `planitem:xyz-456`. The type prefix avoids collisions across resource types and makes the ACL table self-documenting.

### 4.3 AclEntry

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

`expiresAt` enables time-bounded grants for human delegation scenarios — auditor access windows, temporary delegation to a colleague. `NULL` means permanent.

### 4.4 AccessDeniedException

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

In `platform-api`, zero dependencies. Blocking only — reactive callers wrap with `Uni.createFrom().item()`.

```java
package io.casehub.platform.api.acl;

import java.time.Instant;
import java.util.List;

public interface AccessControlProvider {

    boolean canAccess(String actorId, String resourceId, AclAction action);

    void grant(String actorId, String resourceId, AclAction action, Instant expires);

    void revoke(String actorId, String resourceId, AclAction action);

    void revokeAll(String actorId, String resourceId);

    void registerParent(String childResourceId, String parentResourceId);

    List<String> accessibleResources(String actorId, String resourceType, AclAction action);
}
```

Group expansion is internal — the implementation calls `GroupMembershipProvider.groupsOf(actorId)` to resolve the actor's groups, then checks for matching `group:<name>` entries. Call sites never handle group expansion.

### 5.1 GroupMembershipProvider Extension

`GroupMembershipProvider` requires a new method:

```java
List<String> groupsOf(String actorId);
```

Returns the groups that `actorId` belongs to. The existing `membersOf(groupName)` answers "who is in group X?" — the check algorithm needs the inverse: "what groups is actor Y in?"

### 5.2 Contract Test

`AccessControlProviderSpiTest` — abstract contract test in `platform-api` (or `testing/`). Each implementation (`acl-inmem/`, `acl-jpa/`) extends it. Covers:
- grant/revoke/revokeAll lifecycle
- canAccess with direct grants
- canAccess with group-based grants via `group:<name>`
- registerParent and inheritance walk
- expires_at honoured
- accessibleResources filtering

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

### 7.1 platform-api (extension)

Extends the existing `platform-api` module with:
- Package `io.casehub.platform.api.acl`
- Model types: `AclAction`, `AclResourceType`, `AclEntry`, `AccessDeniedException`
- SPI interface: `AccessControlProvider`
- Extension to `GroupMembershipProvider`: `groupsOf(actorId)`
- Contract test: `AccessControlProviderSpiTest`

### 7.2 platform/ (extension)

`NoOpAccessControlProvider` `@DefaultBean` `@ApplicationScoped`:
- `canAccess()` → always `true` (allow-all)
- `grant()`, `revoke()`, `revokeAll()`, `registerParent()` → no-op
- `accessibleResources()` → empty list

Consistent with the existing `@DefaultBean` pattern (`NoOpCaseMemoryStore`).

### 7.3 acl-jpa/ (new module in platform repo)

`casehub-platform-acl-jpa`

`JpaAccessControlProvider` `@ApplicationScoped` — displaces no-op by classpath presence. Queries `acl_entry` and `resource_parent` tables. Composes with `GroupMembershipProvider.groupsOf(actorId)` for group-based grant resolution.

Dependencies:
- `platform-api` (`AccessControlProvider` SPI, `GroupMembershipProvider`)
- Quarkus Hibernate ORM

Same pattern as `persistence-jpa/`, `memory-jpa/`.

### 7.4 acl-inmem/ (new module in platform repo)

`casehub-platform-acl-inmem`

`InMemoryAccessControlProvider` `@Alternative` `@Priority(10)` — `ConcurrentHashMap`, for `@QuarkusTest` isolation. No `quarkus:build` goal. Pattern mirrors `memory-inmem/`. Do NOT combine with `acl-jpa/` in the same scope.

---

## 8. JPA Schema

Schema for `acl-jpa/`:

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

Key columns:
- **`expires_at`** — time-bounded grants for human delegation (auditor access windows, temporary delegation). Not for worker execution lifetime.
- **`condition`** — reserved for future ABAC; not evaluated initially.

---

## 9. Check Algorithm

### 9.1 canAccess(actorId, resourceId, action)

```
1. Resolve actor's groups via GroupMembershipProvider.groupsOf(actorId)
2. Build candidate set: {actorId, 'group:<g1>', 'group:<g2>', ...}
3. Check acl_entry for a matching row:
   actor_id IN candidate set
   AND resource_id = resourceId
   AND action = action
   AND (expires_at IS NULL OR expires_at > now())
4. If no match, look up resource_parent for resourceId
5. If parent exists, repeat from step 3 with parent_resource_id
6. Walk up until match found or chain exhausted
7. Return result
```

### 9.2 accessibleResources(actorId, resourceType, action)

```sql
SELECT DISTINCT e.resource_id
FROM acl_entry e
WHERE e.action = :action
  AND (e.expires_at IS NULL OR e.expires_at > now())
  AND e.actor_id IN (:candidateSet)
  AND e.resource_id LIKE :resourceTypePrefix
```

Returns directly-granted resource IDs. Sub-resources that inherit access from a parent are not returned directly — the caller traverses hierarchies via the engine's existing relationships.

### 9.3 Instance-Level vs Type-Level

This spec defines **instance-level ACL**: grants target specific resource instances. An actor who can READ `case:abc-123` cannot automatically READ `case:def-456`.

If type-level grants are needed later (e.g., "all case-managers can READ all cases in their tenant"), a wildcard resource ID pattern (`case:*`) or a separate type-grant table can be added. The SPI does not need to change — `canAccess()` would compose instance-level and type-level checks internally.

---

## 10. Case Definition Authorization Schema

The `CaseDefinitionSpec` gains an optional `authorization` property that declares which groups are granted each `AclAction` when a case of this type is created.

### 10.1 YAML Structure

```yaml
spec:
  authorization:
    read:  [case-manager, supervisor, auditor]
    write: [case-manager, supervisor]
    admin: [supervisor]
    claim: [case-worker]

  milestones: [...]
  capabilities: [...]
  workers: [...]
```

All fields are optional. If `authorization` is absent, no ACL entries are created (NoOp default applies — allow-all). If partially specified, grants are created only for the listed actions.

Groups are IdP-agnostic strings — resolved via `CurrentPrincipal.roles()` at runtime.

### 10.2 JSON Schema Addition

Added to `CaseDefinition.yaml` `$defs`:

```yaml
Authorization:
  type: object
  description: >
    Declares which groups are granted each ACL action when a case
    of this type is created. Groups are IdP-agnostic — resolved
    via CurrentPrincipal.roles(). All fields are optional.
  unevaluatedProperties: false
  properties:
    read:
      type: array
      items: { type: string }
      description: "Groups granted READ — query case, view plan items, event log, work items"
    write:
      type: array
      items: { type: string }
      description: "Groups granted WRITE — signal, update context, assign work items"
    admin:
      type: array
      items: { type: string }
      description: "Groups granted ADMIN — start, close, suspend, resume, dispatch"
    claim:
      type: array
      items: { type: string }
      description: "Groups granted CLAIM — claim work items for execution"
```

`CaseDefinitionSpec.properties` adds:

```yaml
authorization:
  $ref: "#/$defs/Authorization"
  description: >
    ACL grants created when a case instance of this type is started.
    Maps AclAction to groups. Absent = no ACL enforcement (NoOp default).
```

### 10.3 Engine Behaviour

When `CaseHubRuntime.startCase(definition, ...)` creates a case instance:

1. Engine reads `definition.spec.authorization`
2. For each action with groups listed, engine calls `AccessControlProvider.grant()` for each group:
   ```java
   // e.g. for read: [case-manager, auditor]
   accessControlProvider.grant("group:case-manager", "case:" + caseId, AclAction.READ, null);
   accessControlProvider.grant("group:auditor", "case:" + caseId, AclAction.READ, null);
   ```
3. The initiating principal receives ADMIN grant automatically (case creator is always admin of their case)

If `authorization` is absent, no grants are created. The `NoOpAccessControlProvider` returns `true` for all `canAccess()` — environments without ACL installed remain unaffected.

---

## 11. Phase 1 — Resolved Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Grant model | Flat grants `(actor, resource, action)` | Simpler than role-based; sufficient for instance-level case ACL |
| Identity source | IdP-agnostic via `CurrentPrincipal` | `CurrentPrincipal.roles()` already wired; no IdP coupling |
| ACL code location | SPI in platform-api, impl in platform acl-jpa/ | Platform concern; follows persistence-jpa/memory-jpa pattern |
| Grant timing | Static-first, dynamic-ready | `grant()`/`revoke()` SPI allows future async approval |
| Action model | READ, WRITE, ADMIN, CLAIM | Covers all case operations; extensible via enum addition |
| Enforcement | Programmatic `canAccess()` | Transparent, debuggable, works in all contexts |
| Default behavior | NoOp allow-all `@DefaultBean` | Consistent with platform pattern |
| Resource hierarchy | Implicit inheritance via `resource_parent` | Write-path minimal; case children inherit |
| Time-bounded grants | `expires_at` on `acl_entry` | Human delegation — auditor windows, temporary access |
| Multi-tenancy | Tenant of current resource | Consistent with data-layer tenant filtering |
| Sub-case cascade | Default yes, override by exception | No concrete use case for blocking cascade |

### Phase 1 — Explicitly Out of Scope

- PropagationContext changes
- Worker grants of any kind
- Authorization service SPI

---

# Phase 2 — Identity Propagation

Scope: Wire actor identity through the engine's execution hierarchy so ACL enforcement works for internal paths, not just the REST boundary. Engine repo work. No new platform SPIs.

---

## 12. PropagationContext Wiring

Populate `PropagationContext.inheritedAttributes` with `userId` and `roles` (plain strings, comma-separated) at root case creation. `createChild()` already propagates all attributes — no changes needed there.

All call sites that must be updated:
- `CaseHubReactor.java` — two `createRoot()` call sites
- `EmptyWorkerContextProvider.java`
- `CaseContextChangedEventHandler.java`

```java
Map<String, String> identity = Map.of(
    "userId", currentPrincipal.actorId(),
    "roles", String.join(",", currentPrincipal.roles())
);
PropagationContext.createRoot(traceId, identity, budget);
```

## 13. Dispatch Identity Threading

`CasehubDispatch` reads identity from the current `PropagationContext` when submitting via `WorkOrchestrator`. `WorkRequest` stays data-only — no identity fields added. Identity is a cross-cutting concern carried by `PropagationContext`, not coupled to `WorkRequest`.

## 14. CaseInstance Identity Gap

`CaseHubReactor.java:147` calls `caseInstanceRepository.save(instance, currentPrincipal.tenancyId())` — the `actorId` from `currentPrincipal` is discarded immediately after save. The case then has no record of who created it.

Resolution: store `actorId` on `CaseInstance` at creation or carry it exclusively via `PropagationContext` — decision to be made in Phase 2 design session.

## 15. Sub-Case rootCaseId

`CaseInstanceEntity` currently has `parentCaseId` (nullable) but no `rootCaseId`. Sub-case cascade traversal for ACL checks requires walking the `parentCaseId` chain upward — a recursive CTE in SQL.

Adding `rootCaseId` to `CaseInstanceEntity` would allow direct cascade checks without recursive traversal. This is an engine-side schema change required before `acl-jpa/` can efficiently enforce sub-case cascade.

## 16. External Token Delegation (Documented, Deferred)

Three existing quarkus-flow patterns for external service token delegation:

| Pattern | Mechanism | Proof Point |
|---------|-----------|-------------|
| JWT bearer (user token) | Token passed as workflow input (`"token": jwt.getRawToken()`); referenced in HTTP step headers via JQ: `.header("Authorization", "${ \"Bearer \" + (.token) }")` | `JwtWithinWorkflowTest`, `SubmissionWorkflow` in `core/integration-tests/` |
| Static credentials | `secret("name")` declares a Quarkus secret dependency; `basic($secret.name.username, $secret.name.password)` injects into HTTP calls | `CustomerProfileFlow` in `examples/http-basic-auth/` |
| Formal CNCF `authRef` | `JWTConverter` + `HttpRequestDecorator` registered via ServiceLoader in `FlowNativeProcessor`. Supports `authRef` on workflow function definitions per Serverless Workflow spec | `core/deployment/.../FlowNativeProcessor.java` — not yet used in casehub |

**The gap:** `CasehubCallableTaskBuilder` dispatches via `WorkRequest.of(capability, Map.of())` — no token reaches the dispatched worker. For on-behalf-of delegation to work, `PropagationContext` would need to carry the raw OIDC token or a token reference.

**Why deferred:** Raw JWT has expiry implications for long-running cases (tokens expire before the case completes). A token reference (looked up from a store) defers expiry management but requires a lookup service. This is an open design problem, not a closed one.

### Phase 2 — Resolved Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Identity carrier | userId + roles as plain strings in `PropagationContext.inheritedAttributes` | No expiry issues, cascades via `createChild()` |
| Dispatch identity | PropagationContext, not WorkRequest | WorkRequest stays data-only |
| Token delegation | Deferred | Raw JWT expires; token reference needs infrastructure |

### Phase 2 — Explicitly Out of Scope

- Worker grants
- External token delegation implementation
- Authorization service SPI

---

# Phase 3 — Worker Rights (Roadmap)

This section is roadmap framing — enough for the next design session to pick up without re-litigating the reasoning, not enough to be mistaken for a design that's ready to implement.

---

## 17. Worker Rights — Design Space

### 16.1 In-Process vs External Workers

**In-process workers** (lambda, workflow step) are sandboxed architecturally by the engine's execution boundary and the `inputSchema`/`outputSchema` data-flow contract in the CaseDefinition schema. They receive only the data explicitly mapped via JQ expressions and write results back through the same contract. No ACL grants required — isolation is architectural.

**External workers** (claudony-style provisioners, agent runners) are API callers. They interact with the case via REST endpoints or engine APIs. They are governed by normal API-boundary ACL enforcement from Phase 1.

### 16.2 Privileged External Workers

A worker that needs permissions its initiating principal doesn't hold — analogous to Kubernetes RBAC service accounts. Design not resolved; options include:
- A distinct worker identity in `PropagationContext`
- Case-definition-declared permission intent (authorization YAML section)
- Offline approval via a future authorization service SPI

### 16.3 Isolation Levels

Different worker types may warrant different trust levels:
- **Trusted internal** — in-process, sandboxed by inputSchema/outputSchema
- **Identity-inheriting external** — API caller running under the initiating principal's identity
- **Privileged external** — API caller with its own service-account identity and elevated grants

Not designed — noted as the frame for future work.

### 16.4 Authorization Service SPI

If the privileged external worker scenario is ever pursued, the offline approval mechanism lives here. The SPI would receive "worker W in case definition D wants action A on resource type R" and produce an approved (or rejected) ACL entry. Not designed in Phase 3 — just named.

---

## 18. References

- Prior spec: `docs/specs/2026-06-01-acl-design.md`
- `CurrentPrincipal`: `platform-api/src/main/java/io/casehub/platform/api/identity/CurrentPrincipal.java`
- `GroupMembershipProvider`: `platform-api/src/main/java/io/casehub/platform/api/identity/GroupMembershipProvider.java`
- `PropagationContext`: `engine/api/src/main/java/io/casehub/api/context/PropagationContext.java`
- `CaseHubReactor`: `engine/runtime/src/main/java/io/casehub/engine/internal/engine/CaseHubReactor.java`
- `CasehubDispatch`: `engine/flow/src/main/java/io/casehub/engine/flow/CasehubDispatch.java`
- `CaseDefinition schema`: `engine/schema/src/main/resources/schema/CaseDefinition.yaml`
- `NoOpCaseMemoryStore`: `platform/src/main/java/io/casehub/platform/memory/NoOpCaseMemoryStore.java`
- Platform module patterns: `persistence-jpa/`, `memory-jpa/`, `memory-inmem/`, `scim/`
- Tracking issue: platform#68
