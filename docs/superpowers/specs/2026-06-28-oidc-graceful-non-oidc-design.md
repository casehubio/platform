# OidcCurrentPrincipal — Graceful Non-OIDC SecurityIdentity Handling

**Issue:** casehubio/platform#121
**Date:** 2026-06-28
**Status:** Approved

## Problem

`OidcCurrentPrincipal.tenancyId()` and `isCrossTenantAdmin()` inject `JsonWebToken` via CDI
and read claims from it. When a non-OIDC `HttpAuthenticationMechanism` creates a non-anonymous
`SecurityIdentity`, the Quarkus `OidcJsonWebTokenProducer` returns a `NullJsonWebToken` — a
stub where every `getClaim()` returns `null`. This causes `tenancyId()` to throw
`MissingTenancyException` and `isCrossTenantAdmin()` to silently return `false`.

**Root cause:** `@Inject JsonWebToken jwt` is unreliable in mixed-auth deployments.
Quarkus [discussion #49272](https://github.com/quarkusio/quarkus/discussions/49272) confirms
this: `@Inject JsonWebToken` returns `NullJsonWebToken` for non-OIDC auth, while
`SecurityIdentity.getPrincipal()` correctly returns the actual principal.

## Design

### Approach: Principal instanceof + JWT-first with attribute fallback

Remove the `@Inject JsonWebToken jwt` field. Use `identity.getPrincipal() instanceof JsonWebToken`
to detect JWT-bearing principals. This is deliberately broader than Quarkus's internal
`instanceof OidcJwtCallerPrincipal` check in `OidcJsonWebTokenProducer` — our check reads
claims from any `JsonWebToken` principal, including SmallRye JWT direct authentication
(`DefaultJWTCallerPrincipal`), not just OIDC-issued tokens. Narrowing to
`OidcJwtCallerPrincipal` would exclude valid JWT auth paths.

Resolution order for `tenancyId()` and `isCrossTenantAdmin()`:

1. **Anonymous** — return sentinel (DEFAULT_TENANT_ID / false)
2. **JWT claim** — if principal is a `JsonWebToken`, try the claim. Authoritative when present.
3. **SecurityIdentity attribute** — fallback for non-OIDC mechanisms or OIDC with augmentor-provided attributes
4. **Neither** — throw `MissingTenancyException` / return `false`

### Evidence

- **Type hierarchy verified:** `OidcJwtCallerPrincipal → DefaultJWTCallerPrincipal → JWTCallerPrincipal → JsonWebToken → Principal`. OIDC principals ARE `JsonWebToken` instances.
- **Non-OIDC verified:** `QuarkusPrincipal` implements only `Principal`, not `JsonWebToken`. The `instanceof` check is a definitive discriminator.
- **Quarkus source verified:** `OidcUtils.validateAndCreateIdentity()` line 366 calls `builder.setPrincipal(jwtPrincipal)` where `jwtPrincipal` is `OidcJwtCallerPrincipal`.
- **NullJsonWebToken verified:** `getClaim()` returns `null`, `claim()` returns `Optional.empty()`.

### Why JWT-first, not attributes-first

JWT claims are cryptographically verified by the OIDC infrastructure. SecurityIdentity
attributes are arbitrary key-value pairs. When both exist (unlikely but possible), JWT
should be authoritative. The resolution order (JWT → attribute → throw) reflects this.

### Why not strict branching (JWT-or-throw)

A legitimate scenario exists where OIDC provides identity but a `SecurityIdentityAugmentor`
adds `tenancyId` as an attribute — e.g., corporate IdPs that cannot add custom claims to
JWTs. Strict branching would throw `MissingTenancyException` even though the data is
available via the attribute.

## Changes

### OidcCurrentPrincipal.java

**Remove:** `@Inject JsonWebToken jwt` field

**`tenancyId()`:**
```java
public String tenancyId() {
    if (identity.isAnonymous()) return TenancyConstants.DEFAULT_TENANT_ID;

    if (identity.getPrincipal() instanceof JsonWebToken jwt) {
        Optional<String> claim = jwt.claim("tenancyId");
        if (claim.isPresent()) return claim.get();
    }

    Object attr = identity.getAttribute("tenancyId");
    if (attr instanceof String s) return s;
    if (attr != null) throw new IllegalStateException(
        "SecurityIdentity attribute 'tenancyId' must be String, got: " + attr.getClass().getName());

    throw new MissingTenancyException(identity.getPrincipal().getName());
}
```

**`isCrossTenantAdmin()`:**
```java
public boolean isCrossTenantAdmin() {
    if (identity.isAnonymous()) return false;

    if (identity.getPrincipal() instanceof JsonWebToken jwt) {
        Optional<Boolean> claim = jwt.claim("crossTenantAdmin");
        if (claim.isPresent()) return claim.get();
    }

    Object attr = identity.getAttribute("crossTenantAdmin");
    if (attr instanceof Boolean b) return b;
    if (attr != null) throw new IllegalStateException(
        "SecurityIdentity attribute 'crossTenantAdmin' must be Boolean, got: " + attr.getClass().getName());

    return false;
}
```

**`actorId()` and `groups()`:** unchanged — `identity.getPrincipal().getName()` and
`identity.getRoles()` work for all SecurityIdentity types.

**Javadoc:** update class-level javadoc to document the three-tier resolution order
and the attribute key convention (attribute names match CurrentPrincipal method names:
`tenancyId`, `crossTenantAdmin`).

### OidcCurrentPrincipalTest.java

Replace `@InjectMock JsonWebToken jwt` with mock principal setup on `SecurityIdentity`.

Test scenarios:

| Scenario | Principal type | Claim | Attribute | Expected |
|----------|---------------|-------|-----------|----------|
| OIDC, all claims present | mock JsonWebToken | present | — | read from claim |
| OIDC, crossTenantAdmin absent | mock JsonWebToken | empty | — | false |
| OIDC, tenancyId absent, throws | mock JsonWebToken | empty | null | MissingTenancyException |
| OIDC, claim absent, attribute present (augmentor) | mock JsonWebToken | empty | present | read from attribute |
| OIDC, both claim and attribute present (tenancyId) | mock JsonWebToken | `"tenant-jwt"` | `"tenant-attr"` | `"tenant-jwt"` (claim wins) |
| OIDC, both claim and attribute present (crossTenantAdmin) | mock JsonWebToken | `true` | `false` | `true` (claim wins) |
| Non-OIDC, attribute present | plain Principal | — | present | read from attribute |
| Non-OIDC, no attribute, throws | plain Principal | — | null | MissingTenancyException |
| Non-OIDC, tenancyId wrong type | plain Principal | — | Integer `42` | IllegalStateException |
| Non-OIDC, crossTenantAdmin wrong type | plain Principal | — | String `"true"` | IllegalStateException |
| Anonymous | — | — | — | sentinels |
| System actorId via OIDC | mock JsonWebToken | present | — | isSystem() true |

### No changes to

- `CurrentPrincipal` interface (platform-api) — SPI unchanged
- `MockCurrentPrincipal` (platform/) — config-driven, no SecurityIdentity
- `FixedCurrentPrincipal` (testing/) — test fixture, no SecurityIdentity
- `MissingTenancyException` — semantics unchanged
- Module name / class name — `oidc/OidcCurrentPrincipal` remains accurate

## Attribute key convention

Non-OIDC `HttpAuthenticationMechanism` implementations stamp these attributes on the
`SecurityIdentity` via `QuarkusSecurityIdentity.Builder.addAttribute()`:

| Attribute key | Type | Required | Notes |
|---------------|------|----------|-------|
| `tenancyId` | String | Yes | Tenant identifier. Matches `CurrentPrincipal.tenancyId()` |
| `crossTenantAdmin` | Boolean | No | Defaults to false. Matches `CurrentPrincipal.isCrossTenantAdmin()` |

Convention: attribute key names match `CurrentPrincipal` method names. No constants class
needed — the convention is self-documenting.

### Principal naming convention

`actorId()` delegates to `identity.getPrincipal().getName()`. For OIDC, this returns
the JWT `upn` or `preferred_username` claim (per MicroProfile JWT spec). For non-OIDC
mechanisms, this returns whatever name was passed to `QuarkusPrincipal(name)` or the
custom `Principal` implementation.

The principal name feeds directly into `ActorTypeResolver.resolve()`, which classifies
the actor type. Non-OIDC mechanisms must set the principal name to match these patterns:

| Intent | Principal name | ActorTypeResolver result |
|--------|---------------|-------------------------|
| System service | `"system"` or `"system:<qualifier>"` | SYSTEM |
| Agent persona | `"<name>:<persona>@<version>"` | AGENT |
| Human user | any other string | HUMAN |

**Pitfall:** email addresses (e.g., `"system@internal.corp.com"`) resolve to HUMAN,
not SYSTEM. Use `"system"` or `"system:scheduler"` for system-level service accounts.

## Deferred items (GitHub issues to create)

1. **Update `oidc-harness-wiring-checklist` protocol** — add a step documenting the
   SecurityIdentity attribute convention for non-OIDC mechanisms
2. **Remove `OpenClawCurrentPrincipal` workaround** — casehub-openclaw#42 introduced
   `@Alternative @Priority(150)` CurrentPrincipal for bridge auth. Once #121 ships,
   that workaround can be removed. Filed against casehub-openclaw, blocked by #121.

## Protocol coherence

- **platform-spi-contract.md** — Rule 1 (real CurrentPrincipal must be @RequestScoped): OidcCurrentPrincipal remains @RequestScoped. Compliant.
- **auth-retrofit-readiness.md** — No auth logic in domain/service. SPI signatures free of auth types. Not affected.
- **current-principal-boolean-delegates-to-actor-type.md** — Boolean classifiers delegate to actorType(). Not affected.
- **oidc-harness-wiring-checklist.md** — Wiring steps unchanged. Protocol update deferred (item 1 above).
