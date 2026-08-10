# Design: OidcCurrentPrincipal @Alternative @Priority(100) + CI Dispatch (#111, #110)

**Date:** 2026-06-23
**Issues:** #111 (OidcCurrentPrincipal), #110 (publish.yml dispatch)
**Follow-up:** #112 (protocol + consumer cleanup after #111 ships), #113 (CI dispatch trigger gap)

---

## #111 — Fix 1: @Alternative @Priority(100) on OidcCurrentPrincipal

### Problem

`OidcCurrentPrincipal` is `@RequestScoped` with no `@Alternative` or `@Priority`. When
the `oidc/` module is on the classpath alongside another non-default `CurrentPrincipal`
implementation (e.g. `QhorusInboundCurrentPrincipal`, `TenantScopedPrincipal`), Quarkus
ArC throws `AmbiguousResolutionException` at build-time augmentation.

### Change

Add `@Alternative @Priority(100)` to `OidcCurrentPrincipal`:

```java
@RequestScoped
@Alternative
@Priority(100)
public class OidcCurrentPrincipal implements CurrentPrincipal {
```

### CDI Resolution Hierarchy (after change)

Unqualified `@Inject CurrentPrincipal` resolution — all beans satisfying the default
injection point, ordered by CDI precedence:

| Bean | Repo | Annotation | Priority | Active When |
|------|------|-----------|----------|-------------|
| MockCurrentPrincipal | platform | @ApplicationScoped @DefaultBean | — | No other impl on classpath |
| QhorusInboundCurrentPrincipal | qhorus | @ApplicationScoped | — | Non-OIDC qhorus harnesses |
| TenantScopedPrincipal | work | @RequestScoped @Unremovable | — | Non-OIDC work harnesses |
| FixedCurrentPrincipal | platform (testing/) | @ApplicationScoped @Alternative @Priority(1) | 1 | Non-OIDC tests |
| OidcCurrentPrincipal | platform (oidc/) | @RequestScoped @Alternative @Priority(100) | 100 | OIDC harness (prod + test) |

FixedCurrentPrincipal@1 is displaced by OidcCurrentPrincipal@100 in OIDC harness tests.
This is correct — OIDC tests use `@TestSecurity` + `@InjectMock SecurityIdentity/JsonWebToken`
per the oidc-harness-wiring-checklist protocol (step 5).

**Qualifier-scoped beans — unaffected:** Three `CurrentPrincipal` implementations use custom
qualifiers: `QhorusSystemCurrentPrincipal @QhorusSystem` (qhorus), `SystemCurrentPrincipal
@EngineSystem` (engine), `SystemCurrentPrincipal @WorkSystem` (work). These do not participate
in unqualified `@Inject CurrentPrincipal` resolution — they are only reachable via their
qualifier and are unaffected by this change.

**Priority collision with casehub-work test beans:** Three `MutableCurrentPrincipal` classes
in casehub-work (runtime/, persistence-mongodb/, queues/ — all `src/test/java`) use
`@Alternative @Priority(100)`, the same priority as OidcCurrentPrincipal. If a casehub-work
test module ever adds `casehub-platform-oidc` to its test classpath, `AmbiguousResolutionException`
would result. This collision is acceptable: test beans are not published, and OIDC +
mutable-test-principal is a contradictory configuration. Priority 100 was chosen to match the
contract already documented in `QhorusInboundCurrentPrincipal`'s Javadoc.

### Files

- `oidc/src/main/java/io/casehub/platform/oidc/OidcCurrentPrincipal.java` — add `@Alternative`,
  `@Priority(100)`, imports, and update Javadoc to reflect broader displacement scope
  (displaces all non-alternative implementations, not just MockCurrentPrincipal)

---

## #111 — Fix 2: MissingTenancyException (replaces IllegalStateException)

### Problem

`OidcCurrentPrincipal.tenancyId()` throws `IllegalStateException` when the JWT
lacks a `tenancyId` claim. `IllegalStateException` is thrown by Quarkus internals
and Hibernate in unrelated places. Consumer `ExceptionMapper<IllegalStateException>`
must string-match on the message — fragile and creates undefined re-throw paths.

Per protocol `casehub-work-illegal-state-exception`: do not throw `IllegalStateException`
in REST-reachable code.

### Design Decision: Exception in platform-api, not oidc/

The issue proposed `MissingTenancyClaimException` in `oidc/`. This spec puts
`MissingTenancyException` in `platform-api` instead:

1. `tenancyId()` is an SPI method on `CurrentPrincipal` in `platform-api`. Consumers
   inject the interface, not the OIDC impl. Catching the exception shouldn't require
   an oidc/ dependency.
2. Any auth-backed implementation could fail to resolve tenancy — not just OIDC.
3. "Claim" is OIDC vocabulary. The SPI-level concept is "missing tenancy."

### Exception Design

```java
package io.casehub.platform.api.identity;

public class MissingTenancyException extends RuntimeException {
    private final String actorId;

    public MissingTenancyException(String actorId) {
        super("No tenancy identifier for authenticated principal: " + actorId);
        this.actorId = actorId;
    }

    public String actorId() { return actorId; }
}
```

- **Package:** `io.casehub.platform.api.identity` — same package as `CurrentPrincipal`
- **Extends:** `RuntimeException` — infrastructure/configuration error, not recoverable
- **Carries:** `actorId` for debuggability (follows `AccessDeniedException` pattern)
- **Unchecked:** no SPI signature change needed

### CurrentPrincipal.tenancyId() Javadoc update

The existing Javadoc says "Real implementations read from the JWT `tenancyId` claim" — factually
wrong for `TenantScopedPrincipal` (reads from `TenantHolder`) and `QhorusInboundCurrentPrincipal`
(reads from `InboundTenancyContext`). This is the same OIDC vocabulary leak that motivated
putting the exception in platform-api. Update in the same change:

```java
/**
 * The tenant this principal belongs to.
 *
 * <p>Single-tenant deployments return {@link TenancyConstants#DEFAULT_TENANT_ID}.
 * Real implementations derive this from the authenticated security context.
 *
 * <p>This value must never be sourced from user-supplied input — always derived
 * from the authenticated security context.
 *
 * @throws MissingTenancyException if the principal is authenticated but tenancy
 *         cannot be resolved from the security context
 */
String tenancyId();
```

### OidcCurrentPrincipal.tenancyId() after change

```java
@Override
public String tenancyId() {
    if (identity.isAnonymous()) return TenancyConstants.DEFAULT_TENANT_ID;
    return jwt.<String>claim("tenancyId")
        .orElseThrow(() -> new MissingTenancyException(identity.getPrincipal().getName()));
}
```

### Behavioral delta: casehub-work's IllegalStateExceptionMapper

casehub-work ships `IllegalStateExceptionMapper` (`@Provider ExceptionMapper<IllegalStateException>`)
that maps **all** `IllegalStateException` to HTTP 409 CONFLICT. Today, when
`OidcCurrentPrincipal.tenancyId()` throws `IllegalStateException`, this mapper catches it
incidentally and returns 409.

After this change, `MissingTenancyException extends RuntimeException` — the work mapper
no longer catches it. The exception escapes to the default handler, producing 500.

**This is a correction, not a regression.** 409 CONFLICT means "conflict with the current
state of the target resource." A missing JWT tenancy claim is not a resource state conflict —
it is a server-side configuration error (OIDC provider not including the `tenancyId` claim
in issued tokens). The client cannot fix this by changing their request. 500 INTERNAL SERVER
ERROR is the correct status for infrastructure errors. The 409 mapping was incidental —
the work mapper catches `IllegalStateException` globally for domain state violations,
not for identity infrastructure failures.

Consumers that want a specific HTTP status for `MissingTenancyException` can add their own
`ExceptionMapper<MissingTenancyException>`. Platform does not ship one — it does not own
REST concerns.

### Files

- `platform-api/src/main/java/io/casehub/platform/api/identity/CurrentPrincipal.java` — update `tenancyId()` Javadoc: replace OIDC-specific language, add `@throws MissingTenancyException`
- `platform-api/src/main/java/io/casehub/platform/api/identity/MissingTenancyException.java` — new
- `platform-api/src/test/java/io/casehub/platform/api/identity/MissingTenancyExceptionTest.java` — new
- `oidc/src/main/java/io/casehub/platform/oidc/OidcCurrentPrincipal.java` — use new exception
- `oidc/src/test/java/io/casehub/platform/oidc/OidcCurrentPrincipalTest.java` — assert MissingTenancyException

---

## #110 — Complete dispatch list for direct platform consumers

### Problem

`casehub-worker` depends on `casehub-platform-api` and `casehub-platform-governance`.
When platform publishes, worker does not rebuild — it is missing from the downstream
dispatch list.

Per protocol `ci-dispatch-covers-direct-consumers`: dispatch `upstream-published`
to every repo that declares platform as a direct Maven compile/runtime dependency.

### Audit

The current dispatch list (`ledger connectors eidos neural-text`) is incomplete. Eight
direct consumers with compile/runtime platform dependencies are missing:

| Repo | GitHub Name | Platform Artifacts (compile/runtime) | Has `repository_dispatch`? |
|------|-----------|-------------------------------------|--------------------------|
| casehub-worker | casehubio/casehub-worker | platform-api, platform-governance | ✅ Yes |
| work | casehubio/work | platform-api, platform-expression, platform | ✅ Yes |
| qhorus | casehubio/qhorus | platform-api, platform | ✅ Yes |
| engine | casehubio/engine | platform, platform-api | ❌ No (maven.yml only) |
| devtown | casehubio/devtown | platform-expression, platform | ❌ No (push/dispatch only) |
| aml | casehubio/aml | platform-expression, platform-config, platform, platform-memory-jpa | ❌ No (build.yml only) |
| clinical | casehubio/clinical | platform-api, platform-config, platform-expression, platform | ❌ No (push/dispatch only) |
| claudony | casehubio/claudony | platform-api, platform-config, platform-expression, platform | ❌ No (ci.yml only) |

3 of 8 are dispatch-ready. 5 lack the `repository_dispatch: types: [upstream-published]`
trigger in their CI workflow — dispatching to them is harmless (API call succeeds, event
created, no workflow fires) but ineffective until their CI is fixed. Tracked as #113.

### Change

Replace the dispatch loop with the complete consumer list:

```bash
for repo in ledger connectors eidos neural-text casehub-worker work qhorus engine devtown aml clinical claudony; do
```

### Files

- `.github/workflows/publish.yml` — expand dispatch list

---

## Test Plan

### #111
- Existing `OidcCurrentPrincipalTest.authenticated_tenancyId_absent_throws()` — update to assert `MissingTenancyException` instead of `IllegalStateException`
- New `MissingTenancyExceptionTest` — verify message format, actorId field
- `mvn --batch-mode install` — full build to verify no CDI resolution issues

### #110
- Visual inspection — CI file change only, no test needed
