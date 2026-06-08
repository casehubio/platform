# ACL Authorization Model Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement role-based access control (RBAC) in the casehub-engine repo — model types, SPI interfaces, a no-op default module, and a JPA-backed implementation module.

**Architecture:** ACL types and SPIs live in `engine-api` (`io.casehub.api.acl`). Two new Maven modules under `engine/security/`: `security-noop` (@DefaultBean allow-all) and `security-impl` (@Alternative @Priority(1) JPA-backed). Keycloak groups serve as roles via `CurrentPrincipal.roles()`; `role_definition` + `role_permission` tables map those group names to resource-level permissions. Enforcement is programmatic — `accessControlProvider.canAccess()`.

**Tech Stack:** Java 17, Jakarta CDI, Quarkus Arc, Hibernate ORM (blocking), Panache, Flyway, JUnit 5, AssertJ

**Spec:** `docs/specs/2026-06-08-acl-authorization-model-design.md` (platform repo)

**Target repo:** `/Users/treblereel/workspace/casehub/engine/`

**Package correction:** The spec uses `io.casehub.engine.api.acl`, but engine-api's actual convention is `io.casehub.api.*` (see `io.casehub.api.spi`, `io.casehub.api.model`, `io.casehub.api.context`). This plan uses `io.casehub.api.acl` to match the existing convention.

---

## File Structure

### engine-api (extension — existing module)

| File | Responsibility |
|------|---------------|
| `api/src/main/java/io/casehub/api/acl/AclAction.java` | Enum: READ, WRITE, ADMIN, CLAIM |
| `api/src/main/java/io/casehub/api/acl/ResourceType.java` | Enum: CASE, PLAN_ITEM, WORK_ITEM, EVENT_LOG, MEMORY, CASE_DEFINITION |
| `api/src/main/java/io/casehub/api/acl/ResourceId.java` | Record: typed resource identifier (type + id) |
| `api/src/main/java/io/casehub/api/acl/Permission.java` | Record: (resourceType, action) pair |
| `api/src/main/java/io/casehub/api/acl/Role.java` | Record: named set of permissions |
| `api/src/main/java/io/casehub/api/acl/AccessDeniedException.java` | SecurityException subclass with actorId, resourceId, action |
| `api/src/main/java/io/casehub/api/acl/AccessControlProvider.java` | SPI: canAccess, hasRole, accessibleResources, registerParent |
| `api/src/main/java/io/casehub/api/acl/RoleManager.java` | SPI: defineRole, removeRole, getRole, listRoles |
| `api/src/test/java/io/casehub/api/acl/AclActionTest.java` | AclAction enum contract tests |
| `api/src/test/java/io/casehub/api/acl/ResourceIdTest.java` | ResourceId parse/value round-trip tests |
| `api/src/test/java/io/casehub/api/acl/PermissionTest.java` | Permission record equality tests |
| `api/src/test/java/io/casehub/api/acl/RoleTest.java` | Role immutability + permissions defensiveness |
| `api/src/test/java/io/casehub/api/acl/AccessDeniedExceptionTest.java` | Exception message format + accessors |
| `api/src/test/java/io/casehub/api/acl/AccessControlProviderContractTest.java` | SPI shape + no-op stub contract |
| `api/src/test/java/io/casehub/api/acl/RoleManagerContractTest.java` | SPI shape + no-op stub contract |

### security-noop (new module)

| File | Responsibility |
|------|---------------|
| `security/security-noop/pom.xml` | Maven module: depends on engine-api, quarkus-arc |
| `security/security-noop/src/main/java/io/casehub/engine/security/NoOpAccessControlProvider.java` | @DefaultBean: always allow, always false for hasRole |
| `security/security-noop/src/main/java/io/casehub/engine/security/NoOpRoleManager.java` | @DefaultBean: all operations are no-ops/empty |
| `security/security-noop/src/test/java/io/casehub/engine/security/NoOpAccessControlProviderTest.java` | Tests: verify allow-all, empty returns |
| `security/security-noop/src/test/java/io/casehub/engine/security/NoOpRoleManagerTest.java` | Tests: verify no-op, empty returns |

### security-impl (new module)

| File | Responsibility |
|------|---------------|
| `security/security-impl/pom.xml` | Maven module: depends on engine-api, platform-api, hibernate-orm-panache, flyway |
| `security/security-impl/src/main/resources/db/security/migration/V1__acl_schema.sql` | Flyway: role_definition, role_permission tables + indexes |
| `security/security-impl/src/main/java/io/casehub/engine/security/jpa/RoleDefinitionEntity.java` | JPA entity: role_definition table |
| `security/security-impl/src/main/java/io/casehub/engine/security/jpa/RolePermissionEntity.java` | JPA entity: role_permission table |
| `security/security-impl/src/main/java/io/casehub/engine/security/jpa/JpaRoleManager.java` | @Alternative @Priority(1): CRUD on role definitions and permissions |
| `security/security-impl/src/main/java/io/casehub/engine/security/jpa/JpaAccessControlProvider.java` | @Alternative @Priority(1): resolves roles via CurrentPrincipal, checks role_permission |
| `security/security-impl/src/test/java/io/casehub/engine/security/jpa/JpaRoleManagerTest.java` | @QuarkusTest with @TestTransaction |
| `security/security-impl/src/test/java/io/casehub/engine/security/jpa/JpaAccessControlProviderTest.java` | @QuarkusTest with @TestTransaction |
| `security/security-impl/src/test/resources/application.properties` | H2 or PostgreSQL testcontainer config |

### security (aggregator)

| File | Responsibility |
|------|---------------|
| `security/pom.xml` | Maven aggregator: `<modules>security-noop, security-impl</modules>` |

---

### Task 1: Model Types — AclAction and ResourceType enums

**Files:**
- Create: `api/src/main/java/io/casehub/api/acl/AclAction.java`
- Create: `api/src/main/java/io/casehub/api/acl/ResourceType.java`
- Test: `api/src/test/java/io/casehub/api/acl/AclActionTest.java`

- [ ] **Step 1: Write AclAction enum test**

```java
package io.casehub.api.acl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AclActionTest {

    @Test
    void hasFourValues() {
        assertThat(AclAction.values()).containsExactly(
            AclAction.READ, AclAction.WRITE, AclAction.ADMIN, AclAction.CLAIM);
    }

    @Test
    void valueOfRoundTrips() {
        for (AclAction action : AclAction.values()) {
            assertThat(AclAction.valueOf(action.name())).isEqualTo(action);
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -C /Users/treblereel/workspace/casehub/engine -pl api test -Dtest=io.casehub.api.acl.AclActionTest -DfailIfNoTests=false --batch-mode`
Expected: FAIL — `AclAction` class not found

- [ ] **Step 3: Write AclAction enum**

```java
package io.casehub.api.acl;

public enum AclAction {
    READ,
    WRITE,
    ADMIN,
    CLAIM
}
```

- [ ] **Step 4: Write ResourceType enum**

```java
package io.casehub.api.acl;

public enum ResourceType {
    CASE,
    PLAN_ITEM,
    WORK_ITEM,
    EVENT_LOG,
    MEMORY,
    CASE_DEFINITION
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -f /Users/treblereel/workspace/casehub/engine/api/pom.xml test -Dtest=io.casehub.api.acl.AclActionTest --batch-mode`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git -C /Users/treblereel/workspace/casehub/engine add api/src/main/java/io/casehub/api/acl/AclAction.java api/src/main/java/io/casehub/api/acl/ResourceType.java api/src/test/java/io/casehub/api/acl/AclActionTest.java
git -C /Users/treblereel/workspace/casehub/engine commit -m "feat(acl): add AclAction and ResourceType enums"
```

---

### Task 2: Model Types — ResourceId record

**Files:**
- Create: `api/src/main/java/io/casehub/api/acl/ResourceId.java`
- Test: `api/src/test/java/io/casehub/api/acl/ResourceIdTest.java`

- [ ] **Step 1: Write ResourceId test**

```java
package io.casehub.api.acl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ResourceIdTest {

    @Test
    void value_formatsTypeAndId() {
        ResourceId rid = new ResourceId(ResourceType.CASE, "abc-123");
        assertThat(rid.value()).isEqualTo("case:abc-123");
    }

    @Test
    void value_underscoreTypes_stripUnderscore() {
        ResourceId rid = new ResourceId(ResourceType.PLAN_ITEM, "pi-1");
        assertThat(rid.value()).isEqualTo("planitem:pi-1");
    }

    @Test
    void value_caseDefinition() {
        ResourceId rid = new ResourceId(ResourceType.CASE_DEFINITION, "cd-1");
        assertThat(rid.value()).isEqualTo("casedefinition:cd-1");
    }

    @Test
    void parse_roundTripsAllTypes() {
        for (ResourceType type : ResourceType.values()) {
            ResourceId original = new ResourceId(type, "id-" + type.name());
            ResourceId parsed = ResourceId.parse(original.value());
            assertThat(parsed).isEqualTo(original);
        }
    }

    @Test
    void parse_noColon_throws() {
        assertThatThrownBy(() -> ResourceId.parse("invalidformat"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid resource ID");
    }

    @Test
    void parse_unknownPrefix_throws() {
        assertThatThrownBy(() -> ResourceId.parse("bogus:id-1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown resource type");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -f /Users/treblereel/workspace/casehub/engine/api/pom.xml test -Dtest=io.casehub.api.acl.ResourceIdTest --batch-mode`
Expected: FAIL — `ResourceId` class not found

- [ ] **Step 3: Write ResourceId record**

```java
package io.casehub.api.acl;

public record ResourceId(ResourceType type, String id) {

    public String value() {
        return type.name().toLowerCase().replace("_", "") + ":" + id;
    }

    public static ResourceId parse(String value) {
        int colon = value.indexOf(':');
        if (colon < 0) throw new IllegalArgumentException("Invalid resource ID: " + value);
        String prefix = value.substring(0, colon).toUpperCase();
        String id = value.substring(colon + 1);
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

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -f /Users/treblereel/workspace/casehub/engine/api/pom.xml test -Dtest=io.casehub.api.acl.ResourceIdTest --batch-mode`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git -C /Users/treblereel/workspace/casehub/engine add api/src/main/java/io/casehub/api/acl/ResourceId.java api/src/test/java/io/casehub/api/acl/ResourceIdTest.java
git -C /Users/treblereel/workspace/casehub/engine commit -m "feat(acl): add ResourceId record with parse/value round-trip"
```

---

### Task 3: Model Types — Permission, Role, AccessDeniedException

**Files:**
- Create: `api/src/main/java/io/casehub/api/acl/Permission.java`
- Create: `api/src/main/java/io/casehub/api/acl/Role.java`
- Create: `api/src/main/java/io/casehub/api/acl/AccessDeniedException.java`
- Test: `api/src/test/java/io/casehub/api/acl/PermissionTest.java`
- Test: `api/src/test/java/io/casehub/api/acl/RoleTest.java`
- Test: `api/src/test/java/io/casehub/api/acl/AccessDeniedExceptionTest.java`

- [ ] **Step 1: Write Permission test**

```java
package io.casehub.api.acl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PermissionTest {

    @Test
    void equality_sameTypeAndAction() {
        Permission a = new Permission(ResourceType.CASE, AclAction.READ);
        Permission b = new Permission(ResourceType.CASE, AclAction.READ);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void inequality_differentAction() {
        Permission a = new Permission(ResourceType.CASE, AclAction.READ);
        Permission b = new Permission(ResourceType.CASE, AclAction.WRITE);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void inequality_differentType() {
        Permission a = new Permission(ResourceType.CASE, AclAction.READ);
        Permission b = new Permission(ResourceType.WORK_ITEM, AclAction.READ);
        assertThat(a).isNotEqualTo(b);
    }
}
```

- [ ] **Step 2: Write Role test**

```java
package io.casehub.api.acl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoleTest {

    @Test
    void permissions_areDefensivelyCopied() {
        Set<Permission> perms = new HashSet<>();
        perms.add(new Permission(ResourceType.CASE, AclAction.READ));
        Role role = new Role("viewer", perms);

        perms.add(new Permission(ResourceType.CASE, AclAction.WRITE));
        assertThat(role.permissions()).hasSize(1);
    }

    @Test
    void permissions_areUnmodifiable() {
        Role role = new Role("viewer", Set.of(new Permission(ResourceType.CASE, AclAction.READ)));
        assertThatThrownBy(() -> role.permissions().add(new Permission(ResourceType.CASE, AclAction.WRITE)))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void name_returnsRoleName() {
        Role role = new Role("admin", Set.of());
        assertThat(role.name()).isEqualTo("admin");
    }
}
```

- [ ] **Step 3: Write AccessDeniedException test**

```java
package io.casehub.api.acl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AccessDeniedExceptionTest {

    @Test
    void message_containsAllFields() {
        AccessDeniedException ex = new AccessDeniedException("user-1", "case:abc-123", AclAction.READ);
        assertThat(ex.getMessage()).contains("user-1", "case:abc-123", "READ");
    }

    @Test
    void accessors_returnConstructorArgs() {
        AccessDeniedException ex = new AccessDeniedException("user-1", "case:abc-123", AclAction.WRITE);
        assertThat(ex.actorId()).isEqualTo("user-1");
        assertThat(ex.resourceId()).isEqualTo("case:abc-123");
        assertThat(ex.action()).isEqualTo(AclAction.WRITE);
    }

    @Test
    void extendsSecurityException() {
        AccessDeniedException ex = new AccessDeniedException("u", "r", AclAction.ADMIN);
        assertThat(ex).isInstanceOf(SecurityException.class);
    }
}
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `mvn -f /Users/treblereel/workspace/casehub/engine/api/pom.xml test -Dtest="io.casehub.api.acl.PermissionTest,io.casehub.api.acl.RoleTest,io.casehub.api.acl.AccessDeniedExceptionTest" --batch-mode`
Expected: FAIL — classes not found

- [ ] **Step 5: Write Permission record**

```java
package io.casehub.api.acl;

public record Permission(ResourceType resourceType, AclAction action) {}
```

- [ ] **Step 6: Write Role record**

```java
package io.casehub.api.acl;

import java.util.Set;

public record Role(String name, Set<Permission> permissions) {

    public Role {
        permissions = Set.copyOf(permissions);
    }
}
```

- [ ] **Step 7: Write AccessDeniedException**

```java
package io.casehub.api.acl;

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

- [ ] **Step 8: Run tests to verify they pass**

Run: `mvn -f /Users/treblereel/workspace/casehub/engine/api/pom.xml test -Dtest="io.casehub.api.acl.PermissionTest,io.casehub.api.acl.RoleTest,io.casehub.api.acl.AccessDeniedExceptionTest" --batch-mode`
Expected: PASS (3 test classes, all green)

- [ ] **Step 9: Commit**

```bash
git -C /Users/treblereel/workspace/casehub/engine add api/src/main/java/io/casehub/api/acl/Permission.java api/src/main/java/io/casehub/api/acl/Role.java api/src/main/java/io/casehub/api/acl/AccessDeniedException.java api/src/test/java/io/casehub/api/acl/PermissionTest.java api/src/test/java/io/casehub/api/acl/RoleTest.java api/src/test/java/io/casehub/api/acl/AccessDeniedExceptionTest.java
git -C /Users/treblereel/workspace/casehub/engine commit -m "feat(acl): add Permission, Role records and AccessDeniedException"
```

---

### Task 4: SPI — AccessControlProvider interface

**Files:**
- Create: `api/src/main/java/io/casehub/api/acl/AccessControlProvider.java`
- Test: `api/src/test/java/io/casehub/api/acl/AccessControlProviderContractTest.java`

- [ ] **Step 1: Write contract test**

Follow the existing pattern from `WorkerContextProviderContractTest.java`: test interface shape with reflection, and test a no-op stub's behaviour.

```java
package io.casehub.api.acl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.List;
import org.junit.jupiter.api.Test;

class AccessControlProviderContractTest {

    @Test
    void interface_hasCanAccessMethod() throws Exception {
        assertThat(AccessControlProvider.class.getMethod(
            "canAccess", String.class, String.class, AclAction.class))
            .isNotNull();
    }

    @Test
    void interface_hasHasRoleMethod() throws Exception {
        assertThat(AccessControlProvider.class.getMethod(
            "hasRole", String.class, String.class))
            .isNotNull();
    }

    @Test
    void interface_hasAccessibleResourcesMethod() throws Exception {
        assertThat(AccessControlProvider.class.getMethod(
            "accessibleResources", String.class, ResourceType.class, AclAction.class))
            .isNotNull();
    }

    @Test
    void interface_hasRegisterParentMethod() throws Exception {
        assertThat(AccessControlProvider.class.getMethod(
            "registerParent", String.class, String.class))
            .isNotNull();
    }

    @Test
    void allowAllStub_canAccess_alwaysTrue() {
        AccessControlProvider stub = new AllowAllStub();
        assertThat(stub.canAccess("actor-1", "case:abc", AclAction.READ)).isTrue();
        assertThat(stub.canAccess("actor-1", "case:abc", AclAction.ADMIN)).isTrue();
    }

    @Test
    void allowAllStub_hasRole_alwaysFalse() {
        AccessControlProvider stub = new AllowAllStub();
        assertThat(stub.hasRole("actor-1", "some-role")).isFalse();
    }

    @Test
    void allowAllStub_accessibleResources_emptyList() {
        AccessControlProvider stub = new AllowAllStub();
        assertThat(stub.accessibleResources("actor-1", ResourceType.CASE, AclAction.READ)).isEmpty();
    }

    @Test
    void allowAllStub_registerParent_noException() {
        AccessControlProvider stub = new AllowAllStub();
        assertThatNoException().isThrownBy(() -> stub.registerParent("child:1", "parent:1"));
    }

    static class AllowAllStub implements AccessControlProvider {
        @Override public boolean canAccess(String actorId, String resourceId, AclAction action) { return true; }
        @Override public boolean hasRole(String actorId, String roleName) { return false; }
        @Override public List<String> accessibleResources(String actorId, ResourceType resourceType, AclAction action) { return List.of(); }
        @Override public void registerParent(String childResourceId, String parentResourceId) {}
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -f /Users/treblereel/workspace/casehub/engine/api/pom.xml test -Dtest=io.casehub.api.acl.AccessControlProviderContractTest --batch-mode`
Expected: FAIL — `AccessControlProvider` not found

- [ ] **Step 3: Write AccessControlProvider interface**

```java
package io.casehub.api.acl;

import java.util.List;

public interface AccessControlProvider {

    boolean canAccess(String actorId, String resourceId, AclAction action);

    boolean hasRole(String actorId, String roleName);

    List<String> accessibleResources(String actorId, ResourceType resourceType, AclAction action);

    void registerParent(String childResourceId, String parentResourceId);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -f /Users/treblereel/workspace/casehub/engine/api/pom.xml test -Dtest=io.casehub.api.acl.AccessControlProviderContractTest --batch-mode`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git -C /Users/treblereel/workspace/casehub/engine add api/src/main/java/io/casehub/api/acl/AccessControlProvider.java api/src/test/java/io/casehub/api/acl/AccessControlProviderContractTest.java
git -C /Users/treblereel/workspace/casehub/engine commit -m "feat(acl): add AccessControlProvider SPI with contract test"
```

---

### Task 5: SPI — RoleManager interface

**Files:**
- Create: `api/src/main/java/io/casehub/api/acl/RoleManager.java`
- Test: `api/src/test/java/io/casehub/api/acl/RoleManagerContractTest.java`

- [ ] **Step 1: Write contract test**

```java
package io.casehub.api.acl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoleManagerContractTest {

    @Test
    void interface_hasDefineRoleMethod() throws Exception {
        assertThat(RoleManager.class.getMethod("defineRole", String.class, Set.class)).isNotNull();
    }

    @Test
    void interface_hasRemoveRoleMethod() throws Exception {
        assertThat(RoleManager.class.getMethod("removeRole", String.class)).isNotNull();
    }

    @Test
    void interface_hasGetRoleMethod() throws Exception {
        assertThat(RoleManager.class.getMethod("getRole", String.class)).isNotNull();
    }

    @Test
    void interface_hasListRolesMethod() throws Exception {
        assertThat(RoleManager.class.getMethod("listRoles")).isNotNull();
    }

    @Test
    void noOpStub_defineRole_noException() {
        RoleManager stub = new NoOpStub();
        assertThatNoException().isThrownBy(
            () -> stub.defineRole("viewer", Set.of(new Permission(ResourceType.CASE, AclAction.READ))));
    }

    @Test
    void noOpStub_removeRole_noException() {
        RoleManager stub = new NoOpStub();
        assertThatNoException().isThrownBy(() -> stub.removeRole("viewer"));
    }

    @Test
    void noOpStub_getRole_returnsEmpty() {
        RoleManager stub = new NoOpStub();
        assertThat(stub.getRole("viewer")).isEmpty();
    }

    @Test
    void noOpStub_listRoles_returnsEmptyList() {
        RoleManager stub = new NoOpStub();
        assertThat(stub.listRoles()).isEmpty();
    }

    static class NoOpStub implements RoleManager {
        @Override public void defineRole(String roleName, Set<Permission> permissions) {}
        @Override public void removeRole(String roleName) {}
        @Override public Optional<Role> getRole(String roleName) { return Optional.empty(); }
        @Override public List<Role> listRoles() { return List.of(); }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -f /Users/treblereel/workspace/casehub/engine/api/pom.xml test -Dtest=io.casehub.api.acl.RoleManagerContractTest --batch-mode`
Expected: FAIL — `RoleManager` not found

- [ ] **Step 3: Write RoleManager interface**

```java
package io.casehub.api.acl;

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

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -f /Users/treblereel/workspace/casehub/engine/api/pom.xml test -Dtest=io.casehub.api.acl.RoleManagerContractTest --batch-mode`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git -C /Users/treblereel/workspace/casehub/engine add api/src/main/java/io/casehub/api/acl/RoleManager.java api/src/test/java/io/casehub/api/acl/RoleManagerContractTest.java
git -C /Users/treblereel/workspace/casehub/engine commit -m "feat(acl): add RoleManager SPI with contract test"
```

---

### Task 6: security-noop module — Maven scaffold + @DefaultBean implementations

**Files:**
- Create: `security/pom.xml` (aggregator)
- Create: `security/security-noop/pom.xml`
- Create: `security/security-noop/src/main/java/io/casehub/engine/security/NoOpAccessControlProvider.java`
- Create: `security/security-noop/src/main/java/io/casehub/engine/security/NoOpRoleManager.java`
- Modify: `pom.xml` (engine parent — add `security/security-noop` module)

- [ ] **Step 1: Write test for NoOpAccessControlProvider**

Create `security/security-noop/src/test/java/io/casehub/engine/security/NoOpAccessControlProviderTest.java`:

```java
package io.casehub.engine.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.casehub.api.acl.AclAction;
import io.casehub.api.acl.ResourceType;
import org.junit.jupiter.api.Test;

class NoOpAccessControlProviderTest {

    private final NoOpAccessControlProvider provider = new NoOpAccessControlProvider();

    @Test
    void canAccess_alwaysReturnsTrue() {
        assertThat(provider.canAccess("actor-1", "case:abc-123", AclAction.READ)).isTrue();
        assertThat(provider.canAccess("actor-1", "case:abc-123", AclAction.ADMIN)).isTrue();
        assertThat(provider.canAccess("actor-2", "workitem:w-1", AclAction.CLAIM)).isTrue();
    }

    @Test
    void hasRole_alwaysReturnsFalse() {
        assertThat(provider.hasRole("actor-1", "admin")).isFalse();
        assertThat(provider.hasRole("actor-1", "viewer")).isFalse();
    }

    @Test
    void accessibleResources_alwaysReturnsEmptyList() {
        assertThat(provider.accessibleResources("actor-1", ResourceType.CASE, AclAction.READ)).isEmpty();
    }

    @Test
    void registerParent_doesNotThrow() {
        assertThatNoException().isThrownBy(() -> provider.registerParent("child:1", "parent:1"));
    }
}
```

- [ ] **Step 2: Write test for NoOpRoleManager**

Create `security/security-noop/src/test/java/io/casehub/engine/security/NoOpRoleManagerTest.java`:

```java
package io.casehub.engine.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.casehub.api.acl.AclAction;
import io.casehub.api.acl.Permission;
import io.casehub.api.acl.ResourceType;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NoOpRoleManagerTest {

    private final NoOpRoleManager manager = new NoOpRoleManager();

    @Test
    void defineRole_doesNotThrow() {
        assertThatNoException().isThrownBy(
            () -> manager.defineRole("viewer", Set.of(new Permission(ResourceType.CASE, AclAction.READ))));
    }

    @Test
    void removeRole_doesNotThrow() {
        assertThatNoException().isThrownBy(() -> manager.removeRole("viewer"));
    }

    @Test
    void getRole_returnsEmpty() {
        manager.defineRole("viewer", Set.of(new Permission(ResourceType.CASE, AclAction.READ)));
        assertThat(manager.getRole("viewer")).isEmpty();
    }

    @Test
    void listRoles_returnsEmptyList() {
        assertThat(manager.listRoles()).isEmpty();
    }
}
```

- [ ] **Step 3: Write security aggregator pom.xml**

Create `security/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>io.casehub</groupId>
        <artifactId>casehub-engine-parent</artifactId>
        <version>0.2-SNAPSHOT</version>
    </parent>

    <artifactId>casehub-engine-security-parent</artifactId>
    <packaging>pom</packaging>
    <name>Case Hub :: Security</name>
    <description>ACL authorization modules</description>

    <modules>
        <module>security-noop</module>
    </modules>
</project>
```

- [ ] **Step 4: Write security-noop pom.xml**

Create `security/security-noop/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>io.casehub</groupId>
        <artifactId>casehub-engine-security-parent</artifactId>
        <version>0.2-SNAPSHOT</version>
    </parent>

    <artifactId>casehub-engine-security-noop</artifactId>
    <name>Case Hub :: Security :: NoOp</name>
    <description>@DefaultBean allow-all ACL implementations — no authorization enforcement</description>

    <dependencies>
        <dependency>
            <groupId>io.casehub</groupId>
            <artifactId>casehub-engine-api</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-arc</artifactId>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-api</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-engine</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>io.smallrye</groupId>
                <artifactId>jandex-maven-plugin</artifactId>
                <executions>
                    <execution>
                        <id>make-index</id>
                        <goals><goal>jandex</goal></goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 5: Write NoOpAccessControlProvider**

```java
package io.casehub.engine.security;

import io.casehub.api.acl.AccessControlProvider;
import io.casehub.api.acl.AclAction;
import io.casehub.api.acl.ResourceType;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@DefaultBean
@ApplicationScoped
public class NoOpAccessControlProvider implements AccessControlProvider {

    @Override
    public boolean canAccess(String actorId, String resourceId, AclAction action) {
        return true;
    }

    @Override
    public boolean hasRole(String actorId, String roleName) {
        return false;
    }

    @Override
    public List<String> accessibleResources(String actorId, ResourceType resourceType, AclAction action) {
        return List.of();
    }

    @Override
    public void registerParent(String childResourceId, String parentResourceId) {}
}
```

- [ ] **Step 6: Write NoOpRoleManager**

```java
package io.casehub.engine.security;

import io.casehub.api.acl.Permission;
import io.casehub.api.acl.Role;
import io.casehub.api.acl.RoleManager;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@DefaultBean
@ApplicationScoped
public class NoOpRoleManager implements RoleManager {

    @Override
    public void defineRole(String roleName, Set<Permission> permissions) {}

    @Override
    public void removeRole(String roleName) {}

    @Override
    public Optional<Role> getRole(String roleName) {
        return Optional.empty();
    }

    @Override
    public List<Role> listRoles() {
        return List.of();
    }
}
```

- [ ] **Step 7: Add security module to engine parent pom.xml**

In `/Users/treblereel/workspace/casehub/engine/pom.xml`, add `<module>security</module>` after the `flow` module (line ~107):

```xml
        <module>flow</module>
        <module>security</module>
    </modules>
```

- [ ] **Step 8: Run tests**

Run: `mvn -f /Users/treblereel/workspace/casehub/engine/security/security-noop/pom.xml test --batch-mode`
Expected: PASS — both test classes green

- [ ] **Step 9: Commit**

```bash
git -C /Users/treblereel/workspace/casehub/engine add security/ pom.xml
git -C /Users/treblereel/workspace/casehub/engine commit -m "feat(acl): add security-noop module with @DefaultBean allow-all implementations"
```

---

### Task 7: security-impl module — Maven scaffold + Flyway migration

**Files:**
- Create: `security/security-impl/pom.xml`
- Create: `security/security-impl/src/main/resources/db/security/migration/V1__acl_schema.sql`
- Modify: `security/pom.xml` (add security-impl to modules)

- [ ] **Step 1: Write security-impl pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>io.casehub</groupId>
        <artifactId>casehub-engine-security-parent</artifactId>
        <version>0.2-SNAPSHOT</version>
    </parent>

    <artifactId>casehub-engine-security</artifactId>
    <name>Case Hub :: Security :: JPA</name>
    <description>JPA-backed ACL authorization — @Alternative @Priority(1), displaces security-noop</description>

    <dependencies>
        <dependency>
            <groupId>io.casehub</groupId>
            <artifactId>casehub-engine-api</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>io.casehub</groupId>
            <artifactId>casehub-platform-api</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-hibernate-orm-panache</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-arc</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-flyway</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-jdbc-postgresql</artifactId>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-junit5</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-test-h2</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-jdbc-h2</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.casehub</groupId>
            <artifactId>casehub-platform-testing</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>io.smallrye</groupId>
                <artifactId>jandex-maven-plugin</artifactId>
                <executions>
                    <execution>
                        <id>make-index</id>
                        <goals><goal>jandex</goal></goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

**Note:** `casehub-platform-testing` provides `@Alternative @Priority(1)` identity fixtures for tests (e.g., a fixed `CurrentPrincipal`). Check the actual artifact name in the engine's dependency management section — it may need a version or import.

- [ ] **Step 2: Write Flyway migration**

Create `security/security-impl/src/main/resources/db/security/migration/V1__acl_schema.sql`:

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

CREATE INDEX idx_rd_tenancy ON role_definition (tenancy_id);
CREATE INDEX idx_rp_role ON role_permission (role_definition_id);
```

- [ ] **Step 3: Add security-impl to aggregator**

In `security/pom.xml`, add the module:

```xml
    <modules>
        <module>security-noop</module>
        <module>security-impl</module>
    </modules>
```

- [ ] **Step 4: Verify build compiles**

Run: `mvn -f /Users/treblereel/workspace/casehub/engine/security/security-impl/pom.xml compile --batch-mode`
Expected: BUILD SUCCESS (no source files yet, but pom + migration should resolve)

- [ ] **Step 5: Commit**

```bash
git -C /Users/treblereel/workspace/casehub/engine add security/security-impl/ security/pom.xml
git -C /Users/treblereel/workspace/casehub/engine commit -m "feat(acl): scaffold security-impl module with Flyway migration"
```

---

### Task 8: security-impl — JPA entities

**Files:**
- Create: `security/security-impl/src/main/java/io/casehub/engine/security/jpa/RoleDefinitionEntity.java`
- Create: `security/security-impl/src/main/java/io/casehub/engine/security/jpa/RolePermissionEntity.java`

- [ ] **Step 1: Write RoleDefinitionEntity**

```java
package io.casehub.engine.security.jpa;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "role_definition",
       indexes = {@Index(name = "idx_rd_tenancy", columnList = "tenancy_id")})
public class RoleDefinitionEntity extends PanacheEntity {

    @Column(name = "role_name", nullable = false, unique = true)
    public String roleName;

    @Column(name = "tenancy_id", nullable = false, length = 64)
    public String tenancyId;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt;
}
```

- [ ] **Step 2: Write RolePermissionEntity**

```java
package io.casehub.engine.security.jpa;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "role_permission",
       uniqueConstraints = @UniqueConstraint(
           name = "uq_role_perm",
           columnNames = {"role_definition_id", "resource_type", "action"}),
       indexes = @Index(name = "idx_rp_role", columnList = "role_definition_id"))
public class RolePermissionEntity extends PanacheEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_definition_id", nullable = false)
    public RoleDefinitionEntity roleDefinition;

    @Column(name = "resource_type", nullable = false, length = 50)
    public String resourceType;

    @Column(name = "action", nullable = false, length = 50)
    public String action;
}
```

- [ ] **Step 3: Verify compile**

Run: `mvn -f /Users/treblereel/workspace/casehub/engine/security/security-impl/pom.xml compile --batch-mode`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git -C /Users/treblereel/workspace/casehub/engine add security/security-impl/src/main/java/io/casehub/engine/security/jpa/
git -C /Users/treblereel/workspace/casehub/engine commit -m "feat(acl): add RoleDefinitionEntity and RolePermissionEntity JPA entities"
```

---

### Task 9: security-impl — JpaRoleManager

**Files:**
- Create: `security/security-impl/src/main/java/io/casehub/engine/security/jpa/JpaRoleManager.java`
- Test: `security/security-impl/src/test/java/io/casehub/engine/security/jpa/JpaRoleManagerTest.java`
- Create: `security/security-impl/src/test/resources/application.properties`

- [ ] **Step 1: Write test application.properties**

```properties
quarkus.datasource.db-kind=h2
quarkus.datasource.jdbc.url=jdbc:h2:mem:security-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1
quarkus.hibernate-orm.database.generation=drop-and-create
quarkus.flyway.migrate-at-start=false

# Fixed CurrentPrincipal for tests (from casehub-platform-testing or configurable mock)
casehub.principal.actor-id=test-actor
casehub.principal.tenancy-id=test-tenant
casehub.principal.groups=viewer,editor
```

- [ ] **Step 2: Write JpaRoleManager test**

```java
package io.casehub.engine.security.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.acl.AclAction;
import io.casehub.api.acl.Permission;
import io.casehub.api.acl.ResourceType;
import io.casehub.api.acl.Role;
import io.casehub.api.acl.RoleManager;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

@QuarkusTest
class JpaRoleManagerTest {

    @Inject
    RoleManager roleManager;

    @Test
    @TestTransaction
    void defineRole_createsRoleWithPermissions() {
        Set<Permission> perms = Set.of(
            new Permission(ResourceType.CASE, AclAction.READ),
            new Permission(ResourceType.CASE, AclAction.WRITE));
        roleManager.defineRole("case-manager", perms);

        Optional<Role> role = roleManager.getRole("case-manager");
        assertThat(role).isPresent();
        assertThat(role.get().name()).isEqualTo("case-manager");
        assertThat(role.get().permissions()).containsExactlyInAnyOrderElementsOf(perms);
    }

    @Test
    @TestTransaction
    void defineRole_overwritesExistingRole() {
        roleManager.defineRole("viewer",
            Set.of(new Permission(ResourceType.CASE, AclAction.READ)));
        roleManager.defineRole("viewer",
            Set.of(new Permission(ResourceType.CASE, AclAction.READ),
                   new Permission(ResourceType.WORK_ITEM, AclAction.READ)));

        Role role = roleManager.getRole("viewer").orElseThrow();
        assertThat(role.permissions()).hasSize(2);
    }

    @Test
    @TestTransaction
    void removeRole_deletesRoleAndPermissions() {
        roleManager.defineRole("temp-role",
            Set.of(new Permission(ResourceType.CASE, AclAction.ADMIN)));
        roleManager.removeRole("temp-role");

        assertThat(roleManager.getRole("temp-role")).isEmpty();
    }

    @Test
    @TestTransaction
    void removeRole_nonExistent_noException() {
        roleManager.removeRole("does-not-exist");
    }

    @Test
    @TestTransaction
    void listRoles_returnsAllDefinedRoles() {
        roleManager.defineRole("role-a",
            Set.of(new Permission(ResourceType.CASE, AclAction.READ)));
        roleManager.defineRole("role-b",
            Set.of(new Permission(ResourceType.WORK_ITEM, AclAction.CLAIM)));

        assertThat(roleManager.listRoles())
            .extracting(Role::name)
            .containsExactlyInAnyOrder("role-a", "role-b");
    }

    @Test
    @TestTransaction
    void getRole_nonExistent_returnsEmpty() {
        assertThat(roleManager.getRole("ghost")).isEmpty();
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn -f /Users/treblereel/workspace/casehub/engine/security/security-impl/pom.xml test -Dtest=io.casehub.engine.security.jpa.JpaRoleManagerTest --batch-mode`
Expected: FAIL — `JpaRoleManager` not found

- [ ] **Step 4: Write JpaRoleManager**

```java
package io.casehub.engine.security.jpa;

import io.casehub.api.acl.AclAction;
import io.casehub.api.acl.Permission;
import io.casehub.api.acl.ResourceType;
import io.casehub.api.acl.Role;
import io.casehub.api.acl.RoleManager;
import io.casehub.platform.api.identity.CurrentPrincipal;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Alternative
@jakarta.annotation.Priority(1)
@ApplicationScoped
public class JpaRoleManager implements RoleManager {

    @Inject
    CurrentPrincipal currentPrincipal;

    @Override
    @Transactional
    public void defineRole(String roleName, Set<Permission> permissions) {
        String tenancyId = currentPrincipal.tenancyId();

        RoleDefinitionEntity existing = RoleDefinitionEntity
            .find("roleName = ?1 and tenancyId = ?2", roleName, tenancyId)
            .firstResult();

        if (existing != null) {
            RolePermissionEntity.delete("roleDefinition", existing);
        } else {
            existing = new RoleDefinitionEntity();
            existing.roleName = roleName;
            existing.tenancyId = tenancyId;
            existing.createdAt = LocalDateTime.now();
            existing.persist();
        }

        for (Permission perm : permissions) {
            RolePermissionEntity rpe = new RolePermissionEntity();
            rpe.roleDefinition = existing;
            rpe.resourceType = perm.resourceType().name();
            rpe.action = perm.action().name();
            rpe.persist();
        }
    }

    @Override
    @Transactional
    public void removeRole(String roleName) {
        String tenancyId = currentPrincipal.tenancyId();
        RoleDefinitionEntity.delete("roleName = ?1 and tenancyId = ?2", roleName, tenancyId);
    }

    @Override
    public Optional<Role> getRole(String roleName) {
        String tenancyId = currentPrincipal.tenancyId();

        RoleDefinitionEntity entity = RoleDefinitionEntity
            .find("roleName = ?1 and tenancyId = ?2", roleName, tenancyId)
            .firstResult();

        if (entity == null) {
            return Optional.empty();
        }

        List<RolePermissionEntity> perms = RolePermissionEntity
            .find("roleDefinition", entity)
            .list();

        Set<Permission> permissions = perms.stream()
            .map(rpe -> new Permission(
                ResourceType.valueOf(rpe.resourceType),
                AclAction.valueOf(rpe.action)))
            .collect(Collectors.toSet());

        return Optional.of(new Role(entity.roleName, permissions));
    }

    @Override
    public List<Role> listRoles() {
        String tenancyId = currentPrincipal.tenancyId();

        List<RoleDefinitionEntity> definitions = RoleDefinitionEntity
            .find("tenancyId", tenancyId)
            .list();

        return definitions.stream()
            .map(def -> {
                List<RolePermissionEntity> perms = RolePermissionEntity
                    .find("roleDefinition", def)
                    .list();
                Set<Permission> permissions = perms.stream()
                    .map(rpe -> new Permission(
                        ResourceType.valueOf(rpe.resourceType),
                        AclAction.valueOf(rpe.action)))
                    .collect(Collectors.toSet());
                return new Role(def.roleName, permissions);
            })
            .toList();
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -f /Users/treblereel/workspace/casehub/engine/security/security-impl/pom.xml test -Dtest=io.casehub.engine.security.jpa.JpaRoleManagerTest --batch-mode`
Expected: PASS (6 tests green)

- [ ] **Step 6: Commit**

```bash
git -C /Users/treblereel/workspace/casehub/engine add security/security-impl/src/
git -C /Users/treblereel/workspace/casehub/engine commit -m "feat(acl): add JpaRoleManager with @TestTransaction tests"
```

---

### Task 10: security-impl — JpaAccessControlProvider

**Files:**
- Create: `security/security-impl/src/main/java/io/casehub/engine/security/jpa/JpaAccessControlProvider.java`
- Test: `security/security-impl/src/test/java/io/casehub/engine/security/jpa/JpaAccessControlProviderTest.java`

- [ ] **Step 1: Write JpaAccessControlProvider test**

```java
package io.casehub.engine.security.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.acl.AccessControlProvider;
import io.casehub.api.acl.AclAction;
import io.casehub.api.acl.Permission;
import io.casehub.api.acl.ResourceType;
import io.casehub.api.acl.RoleManager;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

@QuarkusTest
class JpaAccessControlProviderTest {

    @Inject
    AccessControlProvider accessControlProvider;

    @Inject
    RoleManager roleManager;

    @Test
    @TestTransaction
    void canAccess_grantedPermission_returnsTrue() {
        roleManager.defineRole("viewer",
            Set.of(new Permission(ResourceType.CASE, AclAction.READ)));

        assertThat(accessControlProvider.canAccess("test-actor", "case:abc-123", AclAction.READ))
            .isTrue();
    }

    @Test
    @TestTransaction
    void canAccess_noMatchingPermission_returnsFalse() {
        roleManager.defineRole("viewer",
            Set.of(new Permission(ResourceType.CASE, AclAction.READ)));

        assertThat(accessControlProvider.canAccess("test-actor", "case:abc-123", AclAction.ADMIN))
            .isFalse();
    }

    @Test
    @TestTransaction
    void canAccess_noRolesDefined_returnsFalse() {
        assertThat(accessControlProvider.canAccess("test-actor", "case:abc-123", AclAction.READ))
            .isFalse();
    }

    @Test
    @TestTransaction
    void canAccess_roleNotInActorGroups_returnsFalse() {
        roleManager.defineRole("superadmin",
            Set.of(new Permission(ResourceType.CASE, AclAction.ADMIN)));

        assertThat(accessControlProvider.canAccess("test-actor", "case:abc-123", AclAction.ADMIN))
            .isFalse();
    }

    @Test
    @TestTransaction
    void hasRole_actorHasRole_returnsTrue() {
        roleManager.defineRole("viewer", Set.of());
        assertThat(accessControlProvider.hasRole("test-actor", "viewer")).isTrue();
    }

    @Test
    @TestTransaction
    void hasRole_actorDoesNotHaveRole_returnsFalse() {
        roleManager.defineRole("superadmin", Set.of());
        assertThat(accessControlProvider.hasRole("test-actor", "superadmin")).isFalse();
    }

    @Test
    @TestTransaction
    void accessibleResources_matchingCapability_returnsRoleNames() {
        roleManager.defineRole("viewer",
            Set.of(new Permission(ResourceType.CASE, AclAction.READ)));
        roleManager.defineRole("editor",
            Set.of(new Permission(ResourceType.CASE, AclAction.READ),
                   new Permission(ResourceType.CASE, AclAction.WRITE)));

        List<String> roles = accessControlProvider.accessibleResources(
            "test-actor", ResourceType.CASE, AclAction.READ);
        assertThat(roles).containsExactlyInAnyOrder("viewer", "editor");
    }

    @Test
    @TestTransaction
    void accessibleResources_noCapability_returnsEmptyList() {
        List<String> roles = accessControlProvider.accessibleResources(
            "test-actor", ResourceType.CASE, AclAction.ADMIN);
        assertThat(roles).isEmpty();
    }

    @Test
    @TestTransaction
    void registerParent_noOp_doesNotThrow() {
        accessControlProvider.registerParent("child:1", "parent:1");
    }
}
```

**Note:** The test relies on `casehub.principal.groups=viewer,editor` in `application.properties` (from Task 9, Step 1). The test actor's groups are `viewer` and `editor`, so:
- `canAccess` with `viewer` role + READ permission → true
- `canAccess` with `superadmin` role → false (not in actor's groups)
- `hasRole("viewer")` → true (viewer group defined as role)
- `hasRole("superadmin")` → false (not in actor's groups)

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -f /Users/treblereel/workspace/casehub/engine/security/security-impl/pom.xml test -Dtest=io.casehub.engine.security.jpa.JpaAccessControlProviderTest --batch-mode`
Expected: FAIL — `JpaAccessControlProvider` not found

- [ ] **Step 3: Write JpaAccessControlProvider**

```java
package io.casehub.engine.security.jpa;

import io.casehub.api.acl.AccessControlProvider;
import io.casehub.api.acl.AclAction;
import io.casehub.api.acl.ResourceId;
import io.casehub.api.acl.ResourceType;
import io.casehub.platform.api.identity.CurrentPrincipal;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Set;

@Alternative
@jakarta.annotation.Priority(1)
@ApplicationScoped
public class JpaAccessControlProvider implements AccessControlProvider {

    @Inject
    CurrentPrincipal currentPrincipal;

    @Override
    public boolean canAccess(String actorId, String resourceId, AclAction action) {
        Set<String> actorRoles = currentPrincipal.roles();
        if (actorRoles.isEmpty()) {
            return false;
        }

        String tenancyId = currentPrincipal.tenancyId();
        ResourceId rid = ResourceId.parse(resourceId);
        String resourceTypeName = rid.type().name();

        long count = RolePermissionEntity.count(
            "action = ?1 and resourceType = ?2 and roleDefinition.roleName in ?3 and roleDefinition.tenancyId = ?4",
            action.name(), resourceTypeName, actorRoles, tenancyId);

        return count > 0;
    }

    @Override
    public boolean hasRole(String actorId, String roleName) {
        Set<String> actorRoles = currentPrincipal.roles();
        if (!actorRoles.contains(roleName)) {
            return false;
        }

        String tenancyId = currentPrincipal.tenancyId();
        return RoleDefinitionEntity.count(
            "roleName = ?1 and tenancyId = ?2", roleName, tenancyId) > 0;
    }

    @Override
    public List<String> accessibleResources(String actorId, ResourceType resourceType, AclAction action) {
        Set<String> actorRoles = currentPrincipal.roles();
        if (actorRoles.isEmpty()) {
            return List.of();
        }

        String tenancyId = currentPrincipal.tenancyId();

        List<RolePermissionEntity> matches = RolePermissionEntity.find(
            "action = ?1 and resourceType = ?2 and roleDefinition.roleName in ?3 and roleDefinition.tenancyId = ?4",
            action.name(), resourceType.name(), actorRoles, tenancyId)
            .list();

        return matches.stream()
            .map(rpe -> rpe.roleDefinition.roleName)
            .distinct()
            .toList();
    }

    @Override
    public void registerParent(String childResourceId, String parentResourceId) {}
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -f /Users/treblereel/workspace/casehub/engine/security/security-impl/pom.xml test -Dtest=io.casehub.engine.security.jpa.JpaAccessControlProviderTest --batch-mode`
Expected: PASS (9 tests green)

- [ ] **Step 5: Commit**

```bash
git -C /Users/treblereel/workspace/casehub/engine add security/security-impl/src/main/java/io/casehub/engine/security/jpa/JpaAccessControlProvider.java security/security-impl/src/test/java/io/casehub/engine/security/jpa/JpaAccessControlProviderTest.java
git -C /Users/treblereel/workspace/casehub/engine commit -m "feat(acl): add JpaAccessControlProvider with tenant-scoped permission checks"
```

---

### Task 11: Full build verification

- [ ] **Step 1: Run full engine build**

Run: `mvn -f /Users/treblereel/workspace/casehub/engine/pom.xml --batch-mode install -DskipTests`
Expected: BUILD SUCCESS — all modules compile, jandex indexes generated

- [ ] **Step 2: Run all security tests**

Run: `mvn -f /Users/treblereel/workspace/casehub/engine/security/pom.xml test --batch-mode`
Expected: All tests pass across both security-noop and security-impl

- [ ] **Step 3: Run engine-api ACL tests**

Run: `mvn -f /Users/treblereel/workspace/casehub/engine/api/pom.xml test -Dtest="io.casehub.api.acl.*" --batch-mode`
Expected: All ACL contract and model tests pass

---

### Task 12: File engine GitHub issues

These issues are follow-up work that is NOT implemented in this plan. They are documented in the design spec §11.

- [ ] **Step 1: File PropagationContext identity wiring issue**

```bash
gh issue create --repo casehubio/engine \
  --title "Wire CurrentPrincipal identity into PropagationContext.inheritedAttributes" \
  --body "$(cat <<'EOF'
## Context

`PropagationContext.inheritedAttributes` was designed for identity propagation (userId, roles) through the case hierarchy. Currently all `createRoot()` call sites pass `Map.of()` (empty).

## Task

At every `PropagationContext.createRoot()` call site in `CaseHubReactor`, inject `currentPrincipal.actorId()` and `currentPrincipal.roles()` into the attributes map:

```java
Map<String, String> attrs = Map.of(
    "userId", currentPrincipal.actorId(),
    "roles", String.join(",", currentPrincipal.roles())
);
PropagationContext.createRoot(attrs);
```

## Motivation

The ACL authorization model (platform#68) requires identity to flow through the case hierarchy so that authorization checks in nested cases know which actor initiated the work.

Ref: casehubio/platform#68, spec §11.1
EOF
)" \
  --label "enhancement"
```

- [ ] **Step 2: File WorkRequest identity carrier issue**

```bash
gh issue create --repo casehubio/engine \
  --title "Add identity fields to WorkRequest for dispatch identity propagation" \
  --body "$(cat <<'EOF'
## Context

`WorkRequest` currently carries only `capability` and `input` (Map). When `CasehubDispatch.dispatch()` submits a work request, the caller's identity is lost.

## Task

Add identity fields to `WorkRequest` so dispatched workers can know who initiated the dispatch:

```java
public record WorkRequest(String capability, Map<String, Object> input,
                          String actorId, Set<String> roles) {
    // ...
}
```

Or use `PropagationContext` to carry identity alongside the work request.

## Motivation

Without identity on the dispatch, workers cannot enforce ACL checks on behalf of the original caller.

Ref: casehubio/platform#68, spec §11.2
EOF
)" \
  --label "enhancement"
```

- [ ] **Step 3: File Case Definition YAML authorization extension issue**

```bash
gh issue create --repo casehubio/engine \
  --title "Extend case definition YAML schema with authorization section" \
  --body "$(cat <<'EOF'
## Context

The ACL authorization model defines four actions (READ, WRITE, ADMIN, CLAIM) and maps them to Keycloak groups via role definitions. Currently role definitions are created programmatically via `RoleManager.defineRole()`.

## Task

Extend the case definition YAML schema to declare authorization requirements:

```yaml
authorization:
  roles:
    case-manager:
      - CASE:WRITE
      - WORK_ITEM:READ
    supervisor:
      - CASE:ADMIN
      - CASE:READ
```

At case definition load time, the engine should call `RoleManager.defineRole()` for each declared role, creating the role-permission mappings automatically.

## Motivation

Declarative authorization in YAML eliminates the need for manual `defineRole()` calls and keeps authorization configuration co-located with the case definition.

Ref: casehubio/platform#68, spec §11.4
EOF
)" \
  --label "enhancement"
```

- [ ] **Step 4: Commit (no code changes — issue numbers recorded in design journal)**

Update the workspace design journal with the filed issue numbers.

---

## Implementation Notes

### Package naming
The spec uses `io.casehub.engine.api.acl` but the existing engine-api convention is `io.casehub.api.*`. This plan uses `io.casehub.api.acl` for model types and SPIs in engine-api. Implementation modules use `io.casehub.engine.security` and `io.casehub.engine.security.jpa`.

### Blocking vs Reactive
The spec defines blocking SPIs. The engine's existing `persistence-hibernate` uses Hibernate Reactive (Panache Reactive). The `security-impl` module uses **blocking** Hibernate ORM Panache (`quarkus-hibernate-orm-panache`), which is consistent with the platform's `memory-jpa` module pattern. If the engine later needs reactive ACL checks, a `ReactivAccessControlProvider` can be added alongside, following the `BlockingToReactiveBridge` pattern in platform.

### Test configuration
`security-impl` tests use H2 in PostgreSQL compatibility mode (`MODE=PostgreSQL`) with `database.generation=drop-and-create` instead of Flyway. This avoids test-time Flyway configuration complexity. The Flyway migration is verified by the full integration build.

### CurrentPrincipal in tests
`casehub-platform-testing` provides `@Alternative @Priority(1)` identity fixtures. The test `application.properties` configures a fixed principal with `actorId=test-actor`, `tenancyId=test-tenant`, `groups=viewer,editor`. Tests rely on these groups being present when checking ACL.

### Tenant isolation
`JpaRoleManager` and `JpaAccessControlProvider` both filter by `currentPrincipal.tenancyId()`. Role definitions are tenant-scoped — the same role name in different tenants can have different permissions.
