# Design: OIDC-backed CurrentPrincipal

**Date:** 2026-05-21  
**Issue:** casehubio/platform#3, casehubio/platform#16  
**Branch:** issue-3-current-principal-request-scoped

---

## Summary

Ship `casehub-platform-oidc` — a new optional module that provides a `@RequestScoped`
`CurrentPrincipal` implementation backed by Quarkus `SecurityIdentity` and
`JsonWebToken`. Consumers declare the dependency to activate OIDC identity; the
`MockCurrentPrincipal @DefaultBean` remains active for everyone else.

Closes #3 (implements the `@RequestScoped` contract) and #16 (implements
the Keycloak/quarkus-oidc integration).

---

## Module Structure

New `oidc/` directory → artifact `casehub-platform-oidc`. Follows the `config/`
module pattern exactly:

- Depends on `casehub-platform-api` + `quarkus-oidc`
- Jandex plugin for CDI library discovery
- Quarkus maven plugin
- Test scope: `quarkus-junit` + `quarkus-junit-mockito` (for `@InjectMock`)
- Registered in root `pom.xml` `<modules>`

No `@DefaultBean` anywhere in this module. `OidcCurrentPrincipal` is a plain
`@RequestScoped` bean — CDI selects it over `MockCurrentPrincipal @DefaultBean`
automatically when present. Consumers who do not declare `casehub-platform-oidc`
are unaffected; `quarkus-oidc` is never on their classpath transitively.

**Package:** `io.casehub.platform.oidc`

---

## OidcCurrentPrincipal

Single class implementing `CurrentPrincipal`:

```java
@RequestScoped
public class OidcCurrentPrincipal implements CurrentPrincipal {
    @Inject SecurityIdentity identity;
    @Inject JsonWebToken jwt;

    @Override public String actorId() {
        return identity.isAnonymous() ? "anonymous" : identity.getPrincipal().getName();
    }
    @Override public Set<String> groups() {
        return identity.isAnonymous() ? Set.of() : identity.getRoles();
    }
    @Override public String tenancyId() {
        if (identity.isAnonymous()) return TenancyConstants.DEFAULT_TENANT_ID;
        return jwt.<String>claim("tenancyId")
            .orElseThrow(() -> new IllegalStateException("JWT missing required claim: tenancyId"));
    }
    @Override public boolean isCrossTenantAdmin() {
        return !identity.isAnonymous() && jwt.<Boolean>claim("crossTenantAdmin").orElse(false);
    }
}
```

### Claim mapping

| `CurrentPrincipal` method | Source |
|---------------------------|--------|
| `actorId()` | `SecurityIdentity.getPrincipal().getName()` |
| `groups()` | `SecurityIdentity.getRoles()` |
| `tenancyId()` | JWT claim `tenancyId` (String, required) |
| `isCrossTenantAdmin()` | JWT claim `crossTenantAdmin` (Boolean, optional — defaults `false`) |

Claim names are fixed — they are part of the platform JWT schema, not deployment config.

### Anonymous handling

When `identity.isAnonymous()` is true (unauthenticated request to a public endpoint),
returns sentinel values consistent with `CurrentPrincipal.isAuthenticated()` semantics.
Note: `actorId` intentionally differs from the mock's default (`"system"`) — the mock
represents a dev/test authenticated actor; anonymous means no authenticated context.

- `actorId()` → `"anonymous"`
- `groups()` → empty set
- `tenancyId()` → `TenancyConstants.DEFAULT_TENANT_ID`
- `isCrossTenantAdmin()` → `false`

The JWT is never accessed in the anonymous path.

### Missing claim behaviour

- `tenancyId` absent → `IllegalStateException` — a misconfigured token issuer is a
  deployment error, not a runtime edge case
- `crossTenantAdmin` absent → `false` — the claim is optional; absence means no
  elevated privilege (safe default)

---

## Testing

`OidcCurrentPrincipalTest` — `@QuarkusTest` with `@InjectMock` on both
`SecurityIdentity` and `JsonWebToken`. No real OIDC server required; both are CDI
beans and are replaced cleanly via `@InjectMock`.

### Test cases

| Case | Setup | Assertion |
|------|-------|-----------|
| Authenticated, all claims | principal + roles + `tenancyId` + `crossTenantAdmin=true` | all methods return injected values |
| Authenticated, `crossTenantAdmin` absent | claim absent | `isCrossTenantAdmin()` returns `false` |
| Authenticated, `tenancyId` absent | claim absent | `tenancyId()` throws `IllegalStateException` |
| Anonymous identity | `identity.isAnonymous() = true` | sentinel values returned; JWT not accessed |
| Inherited defaults | actorId `"anonymous"` / `"system"` | `isAuthenticated()` / `isSystem()` correct |

No integration test against Keycloak — consumer repos own the full OIDC wiring.

---

## Issues closed

- **#3** — `CurrentPrincipal` mock becomes `@RequestScoped` at auth time: implemented
- **#16** — OIDC `CurrentPrincipal` implementation: implemented

---

## Out of scope

- `GroupMembershipProvider` OIDC / `SecurityIdentityAugmentor` — needs a directory
  (Keycloak Admin API, LDAP); separate module and issue
- Keycloak Admin integration
- Integration tests against a live auth server (consumer responsibility)
