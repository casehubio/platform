# ACL Design Specification — casehub-platform

**Date:** 2026-06-01  
**Status:** Draft — pending quarkus-flow and drools worker use cases  
**Author:** Mark Proctor  
**Scope:** casehub-platform ACL SPI, data model, hierarchy, multi-tenancy, worker access

---

## 1. Context and Motivation

casehub-platform currently provides three foundational cross-cutting capabilities:

- **Identity** — `ActorDIDProvider`, `DIDResolver`, `AgentCredentialValidator`, `AgentIdentityVerificationService`. Answers: who is this actor?
- **RBAC** — `GroupMembershipProvider`. Answers: what groups/roles does this actor have?
- **Preferences** — `PreferenceProvider`, scope-aware YAML + JPA backends. Answers: what settings apply to this actor/scope?

**ACL is the major missing piece.** None of the above answers: what data can this actor access?

The immediate driver is admin API design — REST endpoints that return cases, work items, plan items, and event logs need to restrict their responses based on the caller's permissions. The requirement is data-level ACL, not method-level: the permission is a property of the data object itself, not of the code path that retrieves it. A case accessible to an actor should be accessible regardless of whether it is fetched via REST, a CDI bean, a background job, or a reactive stream.

Additionally, quarkus-flow workers and drools workers are arriving imminently (within 24 hours of this document being written). They are the first concrete worker use cases for ACL and their requirements must inform the design before implementation begins.

---

## 2. Research — What Exists for Quarkus

A systematic survey of de-facto ACL and fine-grained authorization options for Quarkus was conducted before committing to a custom implementation.

### 2.1 Keycloak Authorization Services (`quarkus-keycloak-authorization`)

Keycloak provides UMA 2.0 resource-level permissions via its Authorization Services. The `quarkus-keycloak-authorization` extension integrates this with Quarkus OIDC.

**Why not used:**
- Requires Keycloak as a live external service at runtime. casehub-platform cannot take a runtime dependency on Keycloak — it is a foundational library with zero mandatory external service dependencies.
- Policies are defined inside Keycloak's admin UI, not in pluggable backends. The platform pattern (SPI + multiple backend implementations) is not expressible.
- Tightly coupled to Keycloak's policy model. If a deployment uses a different IdP, Authorization Services is unavailable.

Verdict: viable as a provider implementation later, not as the foundation.

### 2.2 Quarkus Built-In `@PermissionsAllowed` + `@PermissionChecker`

Added in Quarkus 3.x. Build-time bound, type-safe, integrates with `SecurityIdentity`. `@PermissionChecker` methods can receive method parameters and implement custom logic.

**Why not used:**
- Fundamentally method-level enforcement. It protects code entry points, not data objects.
- If the same resource is accessible through multiple code paths (REST endpoint, CDI bean, reactive pipeline, background job), every entry point must be independently annotated.
- Provides no mechanism for filtered list APIs ("show me all cases I can access") — that requires data-level joins, which `@PermissionsAllowed` does not touch.
- The framework is about protecting invocations, not governing visibility of data.

Verdict: useful for endpoint-level guards layered on top of a data ACL system, not a replacement for one.

### 2.3 Zanzibar-Style ReBAC — OpenFGA and SpiceDB (`quarkus-zanzibar`)

Google's Zanzibar paper inspired a family of relationship-based access control systems. Two have Quarkus extensions in the Quarkiverse:

- `quarkus-zanzibar-openfga` (v2.2.0, March 2025) — backed by OpenFGA (open source, Apache 2.0)
- `quarkus-zanzibar-authzed` (v2.7.0, Sept 2025) — backed by SpiceDB / Authzed (open source, Apache 2.0)

Both model permissions as relationship tuples: `(actor, relation, resource)`. Relationship traversal enables rich hierarchical policies.

**Clarification on open source status:** OpenFGA and SpiceDB are open source (Apache 2.0). The constraint is not licensing.

**Why not used as the default:**
- Both require an external service at runtime (an OpenFGA or SpiceDB process). casehub-platform needs a pure Java, fully embedded default. Running a separate authorization service is a deployment choice for consumers, not a platform requirement.
- Both Quarkus extensions are `preview` status as of this writing.
- For the ACL model described in this spec (case-level grants with implicit child inheritance), the relationship traversal power of Zanzibar is not required day one.

Verdict: viable as optional provider modules (`acl-openfga/`, `acl-spicedb/`) once the SPI is defined. Not the default.

### 2.4 OPA (Open Policy Agent)

Mature policy-as-code engine. Used in production at scale (including Apache Polaris, which is Quarkus-based).

**Why not used:**
- No native Quarkus extension. Requires a custom HTTP client to a sidecar or external OPA service.
- Policy language (Rego) is a separate DSL with its own learning curve.
- Adds network latency on every authorization check.
- Operationally heavier than any embedded option.

Verdict: not suitable for platform default. Could be a provider for deployments already running OPA.

### 2.5 jCasbin

Java port of Apache Casbin. Supports ACL, RBAC, ABAC via configurable model files (`PCONF`). Pluggable storage adapters (JDBC, JPA). Fully embedded — no external service.

**Initial appeal:** pure Java, open source, no external service dependency, handles model evaluation declaratively.

**Why not used:**
- For the ACL model this spec describes — `(actor, resource, action)` with one level of implicit parent inheritance — the evaluation logic is approximately 30 lines of Java. jCasbin solves a harder problem than we have.
- jCasbin's `g` (grouping) function can express resource hierarchies, but the resource parent relationships still have to be written on resource creation — the write-path coupling problem doesn't go away.
- jCasbin does not help with filtered list queries ("show me all cases I can see"). Those SQL queries must be written regardless of whether jCasbin is present.
- GraalVM native compilation requires Aviator interpreter mode (`-J-Daviator.eval.mode=INTERPRETER`). Not a hard blocker for JVM mode, but a gotcha for consumers who want native images.
- The Java adapter ecosystem is thinner than the Go original. JDBC/JPA adapters are community-maintained.

The delta between jCasbin and flat JPA for this use case is smaller than it appears. jCasbin earns its place when wildcard matching, ABAC conditions, or policy inheritance are needed day one. None of those are established requirements.

**Revisit trigger:** if ABAC conditions become a concrete requirement (not speculative), jCasbin becomes more attractive. The SPI design below does not preclude it as a backend.

Verdict: not the default implementation. Potentially a provider module later.

### 2.6 Ory Keto

Fine-grained access control via gRPC/REST. No Quarkus integration found as of 2025-2026.

Verdict: not viable without significant custom integration work.

### 2.7 Summary

| Option | External Service | Open Source | Quarkus Native | List Query Help | Verdict |
|--------|-----------------|-------------|----------------|-----------------|---------|
| Keycloak AuthZ | Required | Yes | Yes | No | Provider only |
| @PermissionsAllowed | No | Yes | Yes (built-in) | No | Complementary, not ACL |
| OpenFGA / SpiceDB | Required | Yes | Preview | No | Provider module |
| OPA | Required | Yes | No | No | Provider module |
| jCasbin | No | Yes | Partial (JVM) | No | Revisit if ABAC needed |
| Custom flat JPA | No | Yes | Yes | Yes (by design) | **Selected** |

---

## 3. Core Design Decisions

### 3.1 Data-Level, Not Method-Level

ACL entries govern access to data objects identified by a stable ID. The enforcement point is at the data retrieval layer, not at method invocation. The same ACL check applies regardless of which code path touches the data.

### 3.2 Build It — Custom Flat JPA with Implicit Inheritance

A custom implementation following the existing platform module pattern:

- Pure Java SPI in `platform-api` — zero dependencies
- JPA-backed default in a new `acl-jpa/` module — same pattern as `persistence-jpa/`, `memory-jpa/`
- In-memory no-op default bean in `platform/` — for test and environments where ACL is not installed
- No jCasbin, no external service, no new framework

### 3.3 Implicit Inheritance, Not Explicit at Every Level

Two approaches were evaluated:

**Explicit at every level:** Every resource (case, plan item, work item, event log entry) gets its own ACL row. Maximum precision. Simple list queries (direct join on ACL table). Write-path heavy — every resource creation must pump ACL rows. Revocation requires cascading deletes across all child rows. Particularly painful for dynamic resources created mid-execution by the engine (plan items spawned during case execution).

**Implicit inheritance:** ACL entries at the parent resource level. Children derive access from their parent. Write path is minimal — one entry when the case is created. Revocation is clean — remove the parent entry, children follow automatically. List queries are more complex (OR across direct entry and parent entry). Domain coupling — the ACL layer must know the parent relationship.

**Decision: implicit inheritance.** The engine's dynamic nature (plan items created mid-execution, sub-cases spawned at runtime) makes write amplification from explicit-at-every-level a serious operational problem. The query complexity of implicit inheritance is manageable and well-understood.

### 3.4 The Case Is the Natural ACL Boundary

Code review of the engine's JPA entities (via IntelliJ MCP) revealed:

- `PlanItemEntity.caseId` — direct reference to `CaseInstance.uuid`
- `EventLogEntity.caseId` — direct reference
- `CaseLedgerEntry.caseId` — direct reference
- `SubCaseGroupEntity.parentCaseId` — direct reference

Every engine resource traces directly to a `caseId` in a single hop. There is no intermediate resource type between the case and its children that needs its own ACL boundary. **ACL entries live at the `CaseInstance` level.** Plan items, event log entries, and ledger entries inherit from their case.

### 3.5 Sub-Case Cascade by Default

`CaseInstanceEntity` has `parentCaseId` (nullable UUID) and `parentPlanItemId` (nullable UUID). Sub-cases are themselves `CaseInstance` rows. The hierarchy is recursive and unbounded in depth.

**Default policy: access to a parent case cascades to all its sub-cases.** A sub-case may not be accessed unless the actor has access to the root of the case tree (directly or via cascade).

**Override by exception only.** A concrete use case must justify blocking cascade for a specific sub-case before that mechanism is designed. No speculative override mechanism will be built.

There is currently no `rootCaseId` column in the schema. Cascade traversal for the check "does actor X have access to sub-case Y?" requires walking the `parentCaseId` chain upward. This is a recursive CTE in SQL. The performance profile of this traversal is an implementation concern for `acl-jpa/` — it is not a design concern for the SPI.

### 3.6 Multi-Tenancy Is Not an ACL Concern

ABAC (Attribute-Based Access Control) was evaluated for the tenant isolation requirement: "this actor can only access cases in their tenant."

**Decision: tenant isolation is a data layer filter, not an ACL policy.**

Code review confirmed that `tenancyId` is already present on every engine entity:
- `CaseInstanceEntity.tenancyId`
- `PlanItemEntity.tenancyId`
- `SubCaseGroupEntity.tenancyId`
- `WorkAdapterPlanItemEntity.tenancyId`
- `CaseLedgerEntry.tenancyId`

Tenant isolation is a universal invariant — it applies to every actor, every resource, every action, always. ABAC is the right model when conditions vary per policy. When the condition is universal, it belongs in the data access layer as a query filter (`WHERE tenancy_id = :currentTenant`), applied before ACL is consulted. ACL then operates within already-tenancy-filtered data. The two layers compose cleanly without knowledge of each other.

ABAC is not required for this design. A nullable `condition` column is reserved in the ACL table schema for future use but will not be evaluated until a concrete ABAC requirement is established.

---

## 4. The ACL Model

### 4.1 Resource Identity

Resources are identified by a typed string ID:

```
case:{uuid}
planitem:{planItemId}
workitem:{workItemId}
eventlog:{caseId}
casedefinition:{id}
```

The type prefix is part of the resource ID — it avoids collisions across resource types and makes the ACL table self-documenting.

### 4.2 Actions

Initial action set (to be extended as concrete use cases are identified):

| Action | Meaning |
|--------|---------|
| `READ` | View the resource and its directly-inherited children |
| `WRITE` | Mutate the resource (update context, post to channel) |
| `ADMIN` | Structural operations (cancel case, modify definition) |
| `CLAIM` | Specific to work items — claim for execution |

### 4.3 ACL Table Schema

```sql
CREATE TABLE acl_entry (
    id          BIGSERIAL PRIMARY KEY,
    actor_id    VARCHAR(255) NOT NULL,   -- actorId or group:groupId
    resource_id VARCHAR(255) NOT NULL,   -- e.g. case:abc-123
    action      VARCHAR(50)  NOT NULL,   -- READ, WRITE, ADMIN, CLAIM
    condition   TEXT         NULL,       -- reserved, not evaluated
    granted_at  TIMESTAMP    NOT NULL,
    expires_at  TIMESTAMP    NULL,       -- NULL = permanent; non-NULL = time-bounded
    tenancy_id  VARCHAR(64)  NOT NULL,

    CONSTRAINT uq_acl_entry UNIQUE (actor_id, resource_id, action)
);

CREATE INDEX idx_acl_actor_resource ON acl_entry (actor_id, resource_id);
CREATE INDEX idx_acl_resource       ON acl_entry (resource_id);
CREATE INDEX idx_acl_tenancy        ON acl_entry (tenancy_id);
```

**Key columns:**

- `actor_id` — the actor or group being granted access. Group-keyed entries require composition with `GroupMembershipProvider` at check time.
- `resource_id` — the typed resource identifier.
- `expires_at` — nullable. `NULL` = permanent grant. Non-null = time-bounded grant (designed for worker access — see §6).
- `condition` — reserved for future ABAC; not evaluated in initial implementation.
- `tenancy_id` — carried for operational filtering and auditing; not used for isolation (that is the repository's job).

### 4.4 SPI Interface

In `platform-api`, zero dependencies:

```java
package io.casehub.platform.api.acl;

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

**Note on group-based grants:** The SPI takes `actorId`, not `Set<String> groups`. Group expansion is the responsibility of the implementation — it calls `GroupMembershipProvider.membersOf()` internally, not at the call site. This keeps call sites clean and centralises group resolution.

### 4.5 The Check Algorithm

For `canAccess(actorId, resourceId, action)`:

1. Resolve the actor's groups via `GroupMembershipProvider`.
2. Check the ACL table for a direct matching row: `(actorId OR any group, resourceId, action, expires_at IS NULL OR expires_at > now())`.
3. If no direct match, look up the registered parent of `resourceId`. If a parent exists, repeat from step 2 with the parent resource ID.
4. Continue walking up the parent chain until a match is found or the chain is exhausted.
5. Return the result.

For sub-case cascade: the parent chain for `case:sub-abc` is `case:parent-xyz` (the `parentCaseId`). The parent chain must be pre-registered at sub-case creation time via `registerParent()`.

### 4.6 The List Algorithm

For `accessibleResources(actorId, "case", READ)`:

```sql
SELECT DISTINCT resource_id
FROM acl_entry e
WHERE e.action = 'READ'
  AND (e.expires_at IS NULL OR e.expires_at > now())
  AND (
    e.actor_id = :actorId
    OR e.actor_id IN (:actorGroups)
  )
  AND e.resource_id LIKE 'case:%'
```

This returns directly-granted case IDs. Sub-cases that inherit access from a parent are not returned directly — the caller receives the root case IDs and traverses sub-cases via the engine's existing `parentCaseId` relationships. This keeps the ACL list query simple.

---

## 5. Platform Module Design

Following the existing casehub-platform module pattern exactly.

### 5.1 New Modules

| Module | Artifact | Purpose |
|--------|----------|---------|
| `platform-api` (extension) | `casehub-platform-api` | `AccessControlProvider` SPI, `AclAction` enum, `AclEntry` model — zero deps |
| `platform/` (extension) | `casehub-platform` | `@DefaultBean` no-op `AccessControlProvider` — silent allow-all (safe default for environments without ACL installed; consumers add the real backend) |
| `acl-jpa/` (new) | `casehub-platform-acl-jpa` | `@Alternative @Priority(1)` JPA-backed `AccessControlProvider`. Flyway at `classpath:db/acl/migration`. Composes with `GroupMembershipProvider`. |
| `acl-inmem/` (new) | `casehub-platform-acl-inmem` | `@Alternative @Priority(1)` in-memory `AccessControlProvider` — `ConcurrentHashMap`, suitable for `@QuarkusTest` isolation. Pattern mirrors `memory-inmem/`. |

### 5.2 Default Bean Behaviour

The `@DefaultBean` no-op in `platform/` returns `true` for all `canAccess()` calls (allow-all). This is the correct safe default for the platform's role: foundational SPIs have no-op defaults that yield to real implementations without configuration. Consumers that install `acl-jpa/` get real enforcement automatically.

This is consistent with the existing `@DefaultBean` pattern:
- `NoOpWorkerProvisioner` — does nothing
- `BlockingToReactiveBridge` — delegates
- `acl-jpa/` displaces the no-op when on the classpath

### 5.3 Flyway Location Convention

`acl-jpa/` ships migrations at `classpath:db/acl/migration`. Consumers must add this to their Flyway locations:

```properties
quarkus.flyway.locations=classpath:db/migration,classpath:db/acl/migration
```

This follows the same pattern as `persistence-jpa/` (`classpath:db/platform/migration`) and `memory-jpa/` (`classpath:db/memory/migration`).

---

## 6. Worker Access — Open Design Area

### 6.1 Context

quarkus-flow workers and drools workers are arriving within 24 hours of this document being written. They represent the first concrete worker use cases for ACL and must inform the design before implementation.

This section captures what is known and what must be resolved once those use cases land.

### 6.2 What Is Known

Workers are provisioned *to* a specific case via the `WorkerProvisioner` SPI. Provisioning is already an explicit act — the engine knows which worker is being assigned to which case at the moment it calls `WorkerProvisioner.provision()`.

This means provisioning is the natural ACL write event:

- When a worker is provisioned to `case:abc-123`, the provisioning call should write:
  `grant(workerDid, "case:abc-123", READ, expiresAt)`  
  `grant(workerDid, "case:abc-123", WRITE, expiresAt)`
- The `expiresAt` should be bounded to the worker's expected execution window (or the case deadline).
- When the worker completes or is terminated, the engine calls `revoke(workerDid, "case:abc-123", ...)`.

The `WorkerProvisioner` SPI (or the engine's provisioning handling code) becomes an ACL write path.

The `expires_at` column in the ACL table was designed specifically for this use case — worker grants are time-bounded and should not outlive their provisioning window.

### 6.3 Worker Isolation Requirement

A worker provisioned to case A must not be able to access case B, even if both cases are in the same tenant and both are active concurrently. This is a fundamental isolation requirement.

Current engine behaviour: workers operate via `WorkerContext` (injected at execution time), which carries channels and context for their assigned case. There is no mechanism preventing a malicious or buggy worker from querying the case repository for other cases. ACL is the mechanism that would enforce this boundary.

### 6.4 Sub-Case Access for Workers

When a worker spawns a sub-case (via a `subCase` binding in the case definition), should the worker automatically receive ACL access to that sub-case?

Arguments for: the worker needs to observe sub-case outcomes (completion, context changes) to continue its own execution. Without access, it cannot do its job.

Arguments against: automatic cascade from parent case already handles this — if the worker has access to the parent case, and sub-cases cascade, the worker can access sub-cases transitively.

This question is unresolved. It depends on whether sub-case access is granted via parent cascade (the current default) or requires explicit worker grants. The answer should come from the quarkus-flow and drools use cases.

### 6.5 Open Questions for Worker ACL

These must be resolved before implementation:

1. **Does provisioning automatically write ACL entries, or is that an opt-in?** Requiring all `WorkerProvisioner` implementations to call `AccessControlProvider.grant()` couples them to the ACL system. An alternative: the engine's provisioning handling code writes the grant after `provision()` returns.

2. **What is the worker's `actorId`?** For DID-bearing agents (covered by `ActorDIDProvider`), the DID is the natural actor ID. For quarkus-flow workers and drools workers — what identity do they carry?

3. **What is the `expiresAt` value for a worker grant?** Case deadline? A configurable window? Infinite (revoked explicitly on completion)?

4. **Does worker termination automatically revoke ACL grants?** If so, which lifecycle event triggers revocation — `WorkerStatusListener.onWorkerCompleted`, `onWorkerStalled`?

5. **Should workers get `READ` only, or `READ + WRITE`?** Workers update case context — that's a write. But should they be able to cancel the case (`ADMIN`)? Probably not.

---

## 7. Additional Open Areas

### 7.1 Case Definition Access

`CaseMetaModelEntity` (case templates/definitions) are not currently ACL-controlled. In a multi-tenant deployment, tenant A should not be able to instantiate case definitions that belong to tenant B.

`tenancyId` is not present on `CaseMetaModelEntity` (not confirmed — requires code review when casehub-engine is the working context).

This is a separate but related concern. Filed as a known gap; out of scope for the initial ACL implementation.

### 7.2 Work Item Hierarchy

Work items live in the `casehub-work` repository, which was not open during this design session. The engine's `work-adapter/` module bridges between the two:

- `WorkAdapterPlanItemEntity` maps to the same `plan_item` table as `PlanItemEntity`
- `planItemId` and `caseId` are both present

The assumption is: work items inherit ACL from their `caseId` (same as plan items). This must be verified against the work item entity in `casehub-work` before the JPA implementation is written. Specifically:

- Does the work item entity carry `caseId` directly?
- Is `tenancyId` present?
- Are there work item types (templates, groups) that need independent ACL entries?

### 7.3 Event Log and Ledger Visibility

`EventLogEntity` (engine) and `CaseLedgerEntry` (engine ledger) both carry `caseId`. Both are read-only from a consumer perspective.

The assumption is: READ access on a case confers READ access to its event log and ledger entries. No separate `eventlog:` or `ledger:` action is required initially.

If a use case emerges where an actor should be able to see a case but not its audit trail (e.g., a restricted observer role), a dedicated action type would be introduced. This is not a current requirement.

### 7.4 Filtered List APIs — Performance

The list query in §4.6 returns directly-granted resources. For large tenants with many cases, this query will need to be efficient. Index design is an implementation concern for `acl-jpa/`, but the constraint is noted:

- The `(actor_id, resource_id)` index supports point checks.
- The `resource_id LIKE 'case:%'` predicate for list queries does not use a prefix index efficiently. An alternative is a `resource_type` column alongside `resource_id`.
- Group expansion in list queries (actor OR any group) requires the actor's groups to be resolved before the query. For actors in many groups this is a `WHERE actor_id IN (...)` with a potentially large list. A join against an inline group table may be more efficient.

These are implementation details for `acl-jpa/`, not SPI concerns.

### 7.5 ACL Administration API

Admin APIs for managing ACL entries (grant, revoke, list grants for a resource) are not in scope for the initial platform SPI. They are a consumer concern. A future `casehub-platform-acl-admin` module could expose REST endpoints for ACL management — but only once the SPI is stable and real use cases are driving the API shape.

---

## 8. What Remains Before Implementation

In priority order:

1. **quarkus-flow worker use cases** — arriving within 24 hours. Must answer questions in §6.5 before implementation begins.

2. **drools worker use cases** — same timing. Same questions.

3. **Worker actor identity** — what `actorId` do quarkus-flow and drools workers carry? This determines what goes in the `actor_id` column for worker grants.

4. **casehub-work entity review** — open the casehub-work repo and confirm work item entity carries `caseId` and `tenancyId`. Verify the work item → case hierarchy assumption.

5. **GitHub issue** — file a platform issue capturing this design once questions 1-3 are resolved. The issue should reference this spec.

6. **Engine integration points** — identify exactly where in the engine's provisioning code ACL grants are written. Likely `CaseContextChangedEventHandler.tryProvision()` after `provision()` returns a `ProvisionResult`.

---

## 9. Design Decisions Summary

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Build vs buy | Custom flat JPA | Evaluation logic is trivial; existing options either require external services or solve harder problems |
| Enforcement model | Data-level, not method-level | Resources must be protected regardless of access path |
| Inheritance | Implicit from parent | Engine creates resources dynamically; write amplification of explicit-at-every-level is unacceptable |
| ACL boundary | CaseInstance | All engine resources trace to caseId in one hop |
| Sub-case cascade | Default yes, override by exception | No concrete use case for blocking cascade yet |
| Multi-tenancy | Data layer filter, not ABAC | tenancyId already on all entities; universal invariants belong below ACL |
| ABAC | Reserved, not implemented | No concrete requirement; condition column reserved for future |
| Worker access | Time-bounded grants via provisioning | Provisioning is the authorization act; grants expire with the worker |
| jCasbin | Not used | Delta too small; list queries unsolved anyway |
| OpenFGA / SpiceDB | Optional provider module later | External service; pure Java default required |

---

## 10. References

- casehub-engine entities reviewed: `CaseInstanceEntity`, `PlanItemEntity`, `SubCaseGroupEntity`, `WorkAdapterPlanItemEntity`, `CaseLedgerEntry` — all via IntelliJ MCP
- Platform module patterns: `persistence-jpa/`, `memory-jpa/`, `memory-inmem/`, `scim/`
- Related platform SPIs: `GroupMembershipProvider`, `ActorDIDProvider`, `CurrentPrincipal`
- Quarkus extension research: `quarkus-keycloak-authorization`, `quarkus-zanzibar-openfga`, `quarkus-zanzibar-authzed`
- jCasbin: https://github.com/casbin/jcasbin
- OpenFGA Quarkus extension: `io.quarkiverse.openfga:quarkus-openfga-client`
