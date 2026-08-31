---
capability: identity
audience: consumer
repo: casehub-platform
anchors:
  classes:
    - io.casehub.platform.identity.CurrentPrincipal
    - io.casehub.platform.identity.GroupMembershipProvider
    - io.casehub.platform.identity.ActorTypeResolver
  spis:
    - io.casehub.platform.identity.CurrentPrincipal
    - io.casehub.platform.identity.GroupMembershipProvider
  config-keys:
    - casehub.tenancy.default-id
    - casehub.platform.scim.token
---

# Identity & Tenancy

Who is acting, what groups they belong to, what tenant they operate in.

## Modules

| Artifact | What it activates |
|----------|-------------------|
| `casehub-platform-api` | `CurrentPrincipal` and `GroupMembershipProvider` SPIs |
| `casehub-platform` | `@DefaultBean` mock `CurrentPrincipal` (`@ApplicationScoped`, `@ConfigProperty` values) |
| `casehub-platform-oidc` | OIDC-backed `CurrentPrincipal` from JWT (displaces mock) |
| `casehub-platform-scim` | SCIM 2.0 `GroupMembershipProvider` (displaces mock) |
| `casehub-platform-testing` (test scope) | `FixedCurrentPrincipal`, `InMemoryGroupMembershipProvider` |

## Key SPIs

**CurrentPrincipal** — who is acting: `actorId()`, `groups()`, `roles()`, `tenancyId()`, `actorType()`, `isSystem()`, `isAuthenticated()`, `isCrossTenantAdmin()`.

Not `SecurityIdentity`. CaseHub actors include AI agents, system actors, and internal services that operate outside HTTP request context. Real implementations are `@RequestScoped` and delegate to `SecurityIdentity`; the mock is `@ApplicationScoped` (no request context in dev/test).

**GroupMembershipProvider** — inverse membership: "who is in group X?"

`membersOf(groupName, tenancyId)` is tenant-scoped -- every call requires a `tenancyId` parameter for tenant isolation. `groupsOf(actorId, tenancyId)` provides the reverse lookup.

**Tenancy:** `tenancyId()` is abstract -- every implementor must provide it. Single-tenant deployments return `TenancyConstants.DEFAULT_TENANT_ID`. `isCrossTenantAdmin()` controls cross-tenant data access.

**Actor types:** `ActorType` enum with `HUMAN`, `AGENT`, `SYSTEM`. `ActorTypeResolver.resolve(actorId)` derives the type from the actor ID string. `actorType()` and `isSystem()` use this.

## Configuration

| Property | Purpose | Default |
|----------|---------|---------|
| `casehub.tenancy.default-id` | Default tenant ID for single-tenant deployments | (TenancyConstants value) |
| `casehub.platform.scim.token` | Static SCIM auth token | -- |
| `quarkus.oidc-client.scim.*` | SCIM client-credentials auth | -- |
| `casehub.identity.dids."actorId"` | Static actor-to-DID mapping | -- |
| `casehub.identity.credentials."actorId"` | VC JWT file paths | -- |
