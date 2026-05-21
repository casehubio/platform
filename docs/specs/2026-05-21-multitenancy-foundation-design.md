# Multi-Tenancy Foundation — Design Spec

**Issue:** casehubio/platform#17  
**Branch:** issue-17-multitenancy-foundation  
**Date:** 2026-05-21

---

## Summary

Add the multi-tenancy primitives to `casehub-platform` that everything downstream will depend on: tenancy identity on `CurrentPrincipal`, constants for sentinel values, and cross-tenant admin capability. This is the prerequisite for engine#299 and claudony#121.

---

## Decisions Made

### Constants location
`TenancyConstants` is a new dedicated class in `platform-api` — not static fields on `CurrentPrincipal`. Constants are imported by data access classes, cache builders, event publishers — none of which should import an identity SPI for an infrastructure reason.

### Method shape
Both `tenancyId()` and `isCrossTenantAdmin()` are **abstract** on `CurrentPrincipal`. No interface defaults. Every implementor must provide them explicitly — compile error is the safety net that forces consideration of tenancy at every new implementation.

### Mock configurability
`isCrossTenantAdmin()` is configurable in `MockCurrentPrincipal` via `@ConfigProperty`, consistent with `actorId` and `groups`. Rationale: developers need to simulate cross-tenant admin access locally without swapping beans. Default is `false` — cross-tenant admin is never accidentally enabled.

### Protocols
- **PP-20260520-439daf** — tenancyId filtering is always unconditional; no `if (multiTenantEnabled)` anywhere
- **PP-20260520-e6a5f0** — `isCrossTenantAdmin()` is checked once at CDI injection time in cross-tenant data access classes, never at call sites

---

## New Types

### `TenancyConstants` — `platform-api`

```
io.casehub.platform.api.identity.TenancyConstants
```

```java
public final class TenancyConstants {
    public static final String DEFAULT_TENANT_ID = "278776f9-e1b0-46fb-9032-8bddebdcf9ce";
    public static final String PLATFORM_TENANT_ID = "platform";
    private TenancyConstants() {}
}
```

- `DEFAULT_TENANT_ID` — single-tenant sentinel. Returned by mock and fixture by default. Configurable via `casehub.tenancy.default-id`.
- `PLATFORM_TENANT_ID` — reserved for future platform-level super-admin operations spanning all tenants.

---

## Interface Changes

### `CurrentPrincipal` — `platform-api`

Two new abstract methods:

```java
/**
 * The tenant this principal belongs to.
 *
 * <p>Single-tenant deployments return {@link TenancyConstants#DEFAULT_TENANT_ID}.
 * Real implementations read from the JWT {@code tenancyId} claim.
 *
 * <p>This value must never be sourced from user-supplied input — always derived
 * from the authenticated security context.
 */
String tenancyId();

/**
 * Whether this principal has cross-tenant admin access.
 *
 * <p>Must return {@code true} only for platform-level super-admin principals.
 * Checked once at CDI injection time in cross-tenant data access classes —
 * never at call sites. See protocol PP-20260520-e6a5f0.
 */
boolean isCrossTenantAdmin();
```

No changes to existing methods.

---

## Implementation Changes

### `MockCurrentPrincipal` — `platform/`

Two new `@ConfigProperty` fields:

```java
@ConfigProperty(name = "casehub.tenancy.default-id",
                defaultValue = "278776f9-e1b0-46fb-9032-8bddebdcf9ce")
String tenancyId;  // matches TenancyConstants.DEFAULT_TENANT_ID

@ConfigProperty(name = "casehub.platform.principal.crossTenantAdmin",
                defaultValue = "false")
boolean crossTenantAdmin;
```

Note: `defaultValue` in `@ConfigProperty` must be a string literal (annotation restriction). The UUID string is a deliberate duplication of `TenancyConstants.DEFAULT_TENANT_ID` — a comment on the field points to the constant.

**Configuration:**
| Property | Default | Purpose |
|---|---|---|
| `casehub.tenancy.default-id` | `278776f9-e1b0-46fb-9032-8bddebdcf9ce` | Tenant ID for this deployment |
| `casehub.platform.principal.crossTenantAdmin` | `false` | Simulate cross-tenant admin in dev |

### `FixedCurrentPrincipal` — `testing/`

Two new mutable fields with setters:

```java
private String tenancyId = TenancyConstants.DEFAULT_TENANT_ID;
private boolean crossTenantAdmin = false;

public void setTenancyId(String tenancyId) { this.tenancyId = tenancyId; }
public void setCrossTenantAdmin(boolean crossTenantAdmin) { this.crossTenantAdmin = crossTenantAdmin; }
```

`reset()` updated to restore both fields alongside `actorId` and `groups`.

---

## Testing

### `CurrentPrincipalSpiTest` (platform-api)
SPI contract tests — verify shape, not specific values:
- `tenancyId()` returns non-null, non-blank string
- `isCrossTenantAdmin()` returns without exception

### `MockBeansTest` (platform/)
Integration tests against the mock:
- Default `tenancyId()` returns `TenancyConstants.DEFAULT_TENANT_ID`
- Default `isCrossTenantAdmin()` returns `false`
- `casehub.tenancy.default-id` property override is respected
- `casehub.platform.principal.crossTenantAdmin=true` override is respected

### `FixedCurrentPrincipalTest` (testing/)
Unit tests:
- Defaults match mock defaults (`DEFAULT_TENANT_ID`, `false`)
- `setTenancyId()` and `setCrossTenantAdmin()` take effect immediately
- `reset()` restores both fields to defaults

---

## Files Changed

| File | Module | Change |
|---|---|---|
| `TenancyConstants.java` | `platform-api` | New |
| `CurrentPrincipal.java` | `platform-api` | Add `tenancyId()`, `isCrossTenantAdmin()` |
| `MockCurrentPrincipal.java` | `platform/` | Implement both methods with `@ConfigProperty` |
| `MockBeansTest.java` | `platform/` | New test cases |
| `FixedCurrentPrincipal.java` | `testing/` | Implement both methods with setters + `reset()` |
| `FixedCurrentPrincipalTest.java` | `testing/` | New test cases |
| `CurrentPrincipalSpiTest.java` | `platform-api` | New contract test cases |

---

## Out of Scope

- `@CrossTenant` CDI qualifier and producer — belongs in the first consumer repo that implements a cross-tenant repository, not in platform
- OIDC `CurrentPrincipal` implementation — casehubio/platform#16, deferred
- `tenancyId` column on entity tables — consumer repos (engine#299, claudony#121), blocked by this issue
