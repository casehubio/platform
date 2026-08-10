# OidcCurrentPrincipal @Alternative + CI Dispatch Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `@Alternative @Priority(100)` to OidcCurrentPrincipal, replace `IllegalStateException` with typed `MissingTenancyException` in platform-api, fix SPI Javadoc, and complete the publish.yml dispatch list.

**Architecture:** Three independent changes (#111 Fix 1, #111 Fix 2, #110) executed sequentially on one branch. Fix 2 creates a new exception in platform-api and wires it through oidc/. Fix 1 adds CDI annotations to oidc/. #110 is a CI config change.

**Tech Stack:** Java 21, Quarkus 3.32.2, Maven, JUnit 5, Mockito, GitHub Actions

## Global Constraints

- `platform-api/` must remain zero-dependency — no Quarkus, no JPA, no casehubio imports. Pure Java only.
- Every commit references an issue.
- TDD: write failing test first, then implement.
- Use `mvn --batch-mode install` for full build verification.

---

### Task 1: MissingTenancyException + CurrentPrincipal Javadoc (#111 Fix 2)

**Files:**
- Create: `platform-api/src/main/java/io/casehub/platform/api/identity/MissingTenancyException.java`
- Create: `platform-api/src/test/java/io/casehub/platform/api/identity/MissingTenancyExceptionTest.java`
- Modify: `platform-api/src/main/java/io/casehub/platform/api/identity/CurrentPrincipal.java:49-58`

**Interfaces:**
- Consumes: nothing (new code in platform-api)
- Produces: `MissingTenancyException(String actorId)`, `actorId()` — used by Task 2

- [ ] **Step 1: Write the failing test for MissingTenancyException**

```java
package io.casehub.platform.api.identity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MissingTenancyExceptionTest {

    @Test
    void message_includes_actorId() {
        var ex = new MissingTenancyException("alice");
        assertEquals("No tenancy identifier for authenticated principal: alice", ex.getMessage());
    }

    @Test
    void actorId_returns_constructor_arg() {
        var ex = new MissingTenancyException("bob");
        assertEquals("bob", ex.actorId());
    }

    @Test
    void extends_RuntimeException() {
        assertInstanceOf(RuntimeException.class, new MissingTenancyException("x"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl platform-api -Dtest=MissingTenancyExceptionTest`
Expected: FAIL — `MissingTenancyException` does not exist yet.

- [ ] **Step 3: Implement MissingTenancyException**

```java
package io.casehub.platform.api.identity;

public class MissingTenancyException extends RuntimeException {

    private final String actorId;

    public MissingTenancyException(String actorId) {
        super("No tenancy identifier for authenticated principal: " + actorId);
        this.actorId = actorId;
    }

    public String actorId() {
        return actorId;
    }
}
```

- [ ] **Step 4: Update CurrentPrincipal.tenancyId() Javadoc**

Replace the existing `tenancyId()` Javadoc block (lines 49-58 of `CurrentPrincipal.java`) with:

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

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn --batch-mode test -pl platform-api -Dtest=MissingTenancyExceptionTest`
Expected: PASS (3 tests)

- [ ] **Step 6: Run full platform-api build**

Run: `mvn --batch-mode test -pl platform-api`
Expected: All existing tests pass. The Javadoc change is source-only; no behavioral change in platform-api.

- [ ] **Step 7: Commit**

```
git add platform-api/src/main/java/io/casehub/platform/api/identity/MissingTenancyException.java \
       platform-api/src/test/java/io/casehub/platform/api/identity/MissingTenancyExceptionTest.java \
       platform-api/src/main/java/io/casehub/platform/api/identity/CurrentPrincipal.java
git commit -m "feat(platform#111): add MissingTenancyException + fix tenancyId() Javadoc

New SPI-level exception for authenticated principals that cannot resolve
tenancy. Replaces IllegalStateException in OidcCurrentPrincipal (next commit).

CurrentPrincipal.tenancyId() Javadoc updated: removed OIDC-specific 'JWT
tenancyId claim' language — factually wrong for TenantScopedPrincipal and
QhorusInboundCurrentPrincipal. Added @throws MissingTenancyException.

Refs: #111"
```

---

### Task 2: OidcCurrentPrincipal @Alternative @Priority(100) + MissingTenancyException wiring (#111 Fix 1 + Fix 2 wiring)

**Files:**
- Modify: `oidc/src/main/java/io/casehub/platform/oidc/OidcCurrentPrincipal.java`
- Modify: `oidc/src/test/java/io/casehub/platform/oidc/OidcCurrentPrincipalTest.java`

**Interfaces:**
- Consumes: `MissingTenancyException(String actorId)` from Task 1
- Produces: nothing (leaf change)

- [ ] **Step 1: Update the existing test for the new exception type**

In `OidcCurrentPrincipalTest.java`, change the `authenticated_tenancyId_absent_throws` test.

Replace:

```java
    @Test
    void authenticated_tenancyId_absent_throws() {
        when(identity.isAnonymous()).thenReturn(false);
        doReturn(Optional.empty()).when(jwt).claim("tenancyId");

        assertThrows(IllegalStateException.class, () -> principal.tenancyId());
    }
```

With:

```java
    @Test
    void authenticated_tenancyId_absent_throws_MissingTenancyException() {
        when(identity.isAnonymous()).thenReturn(false);
        when(identity.getPrincipal()).thenReturn(() -> "alice");
        doReturn(Optional.empty()).when(jwt).claim("tenancyId");

        var ex = assertThrows(MissingTenancyException.class, () -> principal.tenancyId());
        assertEquals("alice", ex.actorId());
    }
```

Add the import at the top of the test file:

```java
import io.casehub.platform.api.identity.MissingTenancyException;
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl oidc -Dtest=OidcCurrentPrincipalTest#authenticated_tenancyId_absent_throws_MissingTenancyException`
Expected: FAIL — `OidcCurrentPrincipal` still throws `IllegalStateException`.

- [ ] **Step 3: Add @Alternative @Priority(100) and wire MissingTenancyException**

In `OidcCurrentPrincipal.java`, make these changes:

Add imports:

```java
import io.casehub.platform.api.identity.MissingTenancyException;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Alternative;
```

Add annotations to the class declaration:

```java
@RequestScoped
@Alternative
@Priority(100)
public class OidcCurrentPrincipal implements CurrentPrincipal {
```

Replace the Javadoc with:

```java
/**
 * OIDC-backed {@link CurrentPrincipal}, {@code @RequestScoped}.
 *
 * <p>{@code @Alternative @Priority(100)} — when this module is on the classpath,
 * this bean automatically displaces all non-alternative {@code CurrentPrincipal}
 * implementations: {@code MockCurrentPrincipal @DefaultBean} (platform),
 * {@code QhorusInboundCurrentPrincipal @ApplicationScoped} (qhorus),
 * {@code TenantScopedPrincipal @RequestScoped} (work). No {@code exclude-types}
 * configuration required.
 *
 * <p>Claim names are fixed platform contract: {@code tenancyId} (required String),
 * {@code crossTenantAdmin} (optional Boolean, defaults false).
 *
 * <p>When the identity is anonymous (unauthenticated request to a public endpoint),
 * returns sentinel values matching MockCurrentPrincipal defaults. The JWT is never
 * accessed in the anonymous path.
 */
```

Replace the `tenancyId()` method body:

```java
    @Override
    public String tenancyId() {
        if (identity.isAnonymous()) return TenancyConstants.DEFAULT_TENANT_ID;
        return jwt.<String>claim("tenancyId")
            .orElseThrow(() -> new MissingTenancyException(identity.getPrincipal().getName()));
    }
```

- [ ] **Step 4: Run the updated test to verify it passes**

Run: `mvn --batch-mode test -pl oidc -Dtest=OidcCurrentPrincipalTest#authenticated_tenancyId_absent_throws_MissingTenancyException`
Expected: PASS

- [ ] **Step 5: Run full oidc module tests**

Run: `mvn --batch-mode test -pl oidc`
Expected: All 6 tests pass (the renamed test + 5 unchanged tests).

- [ ] **Step 6: Run full build**

Run: `mvn --batch-mode install`
Expected: BUILD SUCCESS across all modules. No CDI resolution issues.

- [ ] **Step 7: Commit**

```
git add oidc/src/main/java/io/casehub/platform/oidc/OidcCurrentPrincipal.java \
       oidc/src/test/java/io/casehub/platform/oidc/OidcCurrentPrincipalTest.java
git commit -m "feat(platform#111): @Alternative @Priority(100) on OidcCurrentPrincipal

OidcCurrentPrincipal now automatically displaces all non-alternative
CurrentPrincipal implementations when casehub-platform-oidc is on the
classpath. Consumers no longer need exclude-types configuration.

Also wires MissingTenancyException (from previous commit) replacing
IllegalStateException in tenancyId(). Test updated to assert new type.

Closes: #111"
```

---

### Task 3: Expand publish.yml dispatch list (#110)

**Files:**
- Modify: `.github/workflows/publish.yml:44`

**Interfaces:**
- Consumes: nothing
- Produces: nothing (CI config only)

- [ ] **Step 1: Update the dispatch loop**

In `.github/workflows/publish.yml`, replace line 44:

```yaml
          for repo in ledger connectors eidos neural-text; do
```

With:

```yaml
          for repo in ledger connectors eidos neural-text casehub-worker work qhorus engine devtown aml clinical claudony; do
```

- [ ] **Step 2: Visual review**

Verify the `for repo in ...` line is the only change. Confirm no trailing whitespace or YAML formatting issues.

- [ ] **Step 3: Commit**

```
git add .github/workflows/publish.yml
git commit -m "ci(platform#110): add 8 missing repos to publish.yml downstream dispatch

Adds casehub-worker, work, qhorus, engine, devtown, aml, clinical,
claudony — all direct consumers of platform artifacts per protocol
ci-dispatch-covers-direct-consumers.

5 of 8 repos lack repository_dispatch triggers in their CI (tracked
as #113). Dispatching to them is harmless until their CI is fixed.

Closes: #110"
```

---

### Task 4: Full build verification + code review

- [ ] **Step 1: Run full build**

Run: `mvn --batch-mode install`
Expected: BUILD SUCCESS. All modules compile and all tests pass.

- [ ] **Step 2: Review all changes**

Run: `git log --oneline origin/main..HEAD` and `git diff origin/main..HEAD`
Verify:
1. MissingTenancyException in platform-api with test
2. CurrentPrincipal.tenancyId() Javadoc updated (no OIDC vocabulary)
3. OidcCurrentPrincipal has @Alternative @Priority(100) + MissingTenancyException
4. OidcCurrentPrincipal Javadoc reflects broader displacement
5. OidcCurrentPrincipalTest asserts MissingTenancyException with actorId
6. publish.yml dispatch list has all 12 repos

- [ ] **Step 3: Request code review**

Invoke `superpowers:requesting-code-review`. Any finding Minor or above that isn't fixed this session must be captured as a GitHub issue.

- [ ] **Step 4: Run implementation-doc-sync**

Invoke `implementation-doc-sync` to update any docs that need syncing after the changes.
