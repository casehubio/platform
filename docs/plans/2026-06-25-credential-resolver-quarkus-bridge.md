# CredentialResolver Quarkus Bridge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement `QuarkusCredentialResolver` — an `@Alternative @Priority(1)` bridge from `CredentialResolver` to Quarkus `CredentialsProvider`, in a new `credentials-quarkus/` module.

**Architecture:** Single class delegating `resolve(credentialRef)` to `CredentialsProvider.getCredentials(credentialRef)`. Uses `@Any Instance<CredentialsProvider>` with `@PostConstruct` validation for `@Named`-safe injection. Returns `Map.copyOf()` for immutability. Displaces `DefaultCredentialResolver` @DefaultBean when on classpath.

**Tech Stack:** Java 21, Quarkus 3.32.2, `io.quarkus:quarkus-credentials`, CDI (`quarkus-arc`)

**Spec:** `docs/superpowers/specs/2026-06-25-credential-resolver-quarkus-bridge-design.md`

## Global Constraints

- `platform-api/` must remain zero-dependency — no Quarkus, no JPA, no casehubio imports. Pure Java only.
- `credentials-quarkus/` is a library module — `generate-code` + `generate-code-tests` only, no `quarkus:build` goal.
- `@Alternative @Priority(1)` displaces `DefaultCredentialResolver` @DefaultBean.
- `io.quarkus:quarkus-credentials` version managed by the Quarkus BOM — no explicit version.
- Jandex plugin required for ArC to discover `@Alternative` beans in dependency JARs.
- Issue: casehubio/platform#116

---

### Task 1: Module scaffold + QuarkusCredentialResolver + tests

**Files:**
- Create: `credentials-quarkus/pom.xml`
- Create: `credentials-quarkus/src/main/java/io/casehub/platform/credentials/quarkus/QuarkusCredentialResolver.java`
- Create: `credentials-quarkus/src/test/java/io/casehub/platform/credentials/quarkus/MockCredentialsProvider.java`
- Create: `credentials-quarkus/src/test/java/io/casehub/platform/credentials/quarkus/QuarkusCredentialResolverTest.java`
- Modify: `pom.xml` — add `<module>credentials-quarkus</module>`

**Interfaces:**
- Consumes: `io.casehub.platform.api.credentials.CredentialResolver` (from platform-api), `io.quarkus.credentials.CredentialsProvider` (from quarkus-credentials)
- Produces: `QuarkusCredentialResolver` — `@Alternative @Priority(1) @ApplicationScoped`, implements `CredentialResolver`

- [ ] **Step 1: Write the test mock — MockCredentialsProvider**

Create `credentials-quarkus/src/test/java/io/casehub/platform/credentials/quarkus/MockCredentialsProvider.java`:

```java
package io.casehub.platform.credentials.quarkus;

import io.quarkus.credentials.CredentialsProvider;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class MockCredentialsProvider implements CredentialsProvider {

    @Override
    public Map<String, String> getCredentials(final String credentialsProviderName) {
        return switch (credentialsProviderName) {
            case "db-primary" -> {
                final Map<String, String> creds = new HashMap<>();
                creds.put("user", "admin");
                creds.put("password", "s3cret");
                yield creds;
            }
            case "returns-empty" -> Map.of();
            case "returns-null" -> null;
            default -> null;
        };
    }
}
```

- [ ] **Step 2: Write the failing tests**

Create `credentials-quarkus/src/test/java/io/casehub/platform/credentials/quarkus/QuarkusCredentialResolverTest.java`:

```java
package io.casehub.platform.credentials.quarkus;

import io.casehub.platform.api.credentials.CredentialPropertyKeys;
import io.casehub.platform.api.credentials.CredentialResolver;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
class QuarkusCredentialResolverTest {

    @Inject CredentialResolver resolver;

    @Test
    void null_ref_returns_empty_map() {
        assertThat(resolver.resolve(null)).isEmpty();
    }

    @Test
    void blank_ref_returns_empty_map() {
        assertThat(resolver.resolve("   ")).isEmpty();
    }

    @Test
    void known_ref_returns_credentials_via_passthrough() {
        Map<String, String> result = resolver.resolve("db-primary");
        assertThat(result)
                .containsEntry(CredentialPropertyKeys.USER, "admin")
                .containsEntry(CredentialPropertyKeys.PASSWORD, "s3cret")
                .hasSize(2);
    }

    @Test
    void unknown_ref_returns_empty_map_when_provider_returns_null() {
        assertThat(resolver.resolve("nonexistent")).isEmpty();
    }

    @Test
    void empty_map_from_provider_returns_empty_map() {
        assertThat(resolver.resolve("returns-empty")).isEmpty();
    }

    @Test
    void returned_map_is_immutable() {
        Map<String, String> result = resolver.resolve("db-primary");
        assertThatThrownBy(() -> result.put("extra", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl credentials-quarkus -Dtest=QuarkusCredentialResolverTest`
Expected: COMPILATION FAILURE — `QuarkusCredentialResolver` does not exist, module does not exist.

- [ ] **Step 4: Create the module pom.xml**

Create `credentials-quarkus/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>io.casehub</groupId>
        <artifactId>casehub-platform-parent</artifactId>
        <version>0.2-SNAPSHOT</version>
    </parent>

    <artifactId>casehub-platform-credentials-quarkus</artifactId>
    <packaging>jar</packaging>
    <name>CaseHub Platform Credentials Quarkus Bridge</name>
    <description>Bridge from CredentialResolver SPI to Quarkus CredentialsProvider.
        @Alternative @Priority(1) — displaces DefaultCredentialResolver @DefaultBean
        when on the classpath. Enables Vault, AWS Secrets Manager, GCP Secret Manager,
        and any other Quarkus credential extension without application code changes.</description>

    <dependencies>
        <dependency>
            <groupId>io.casehub</groupId>
            <artifactId>casehub-platform-api</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-credentials</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-arc</artifactId>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-junit5</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
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
                <groupId>io.quarkus</groupId>
                <artifactId>quarkus-maven-plugin</artifactId>
                <version>${quarkus.platform.version}</version>
                <extensions>true</extensions>
                <executions>
                    <execution>
                        <goals>
                            <goal>generate-code</goal>
                            <goal>generate-code-tests</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <groupId>io.smallrye</groupId>
                <artifactId>jandex-maven-plugin</artifactId>
                <version>${jandex-maven-plugin.version}</version>
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

- [ ] **Step 5: Add module to parent POM**

In `pom.xml`, add `<module>credentials-quarkus</module>` after the `<module>governance</module>` entry:

```xml
        <module>governance</module>
        <module>credentials-quarkus</module>
    </modules>
```

- [ ] **Step 6: Create QuarkusCredentialResolver**

Create `credentials-quarkus/src/main/java/io/casehub/platform/credentials/quarkus/QuarkusCredentialResolver.java`:

```java
package io.casehub.platform.credentials.quarkus;

import io.casehub.platform.api.credentials.CredentialResolver;
import io.quarkus.credentials.CredentialsProvider;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.Map;

/**
 * Bridge from {@link CredentialResolver} to Quarkus {@link CredentialsProvider}.
 *
 * <p>{@code @Alternative @Priority(1)} displaces {@code DefaultCredentialResolver}
 * {@code @DefaultBean} when this module is on the classpath.
 *
 * <p>Uses {@code @Any Instance<CredentialsProvider>} to handle both {@code @Named}
 * and unqualified providers — direct injection would fail for {@code @Named}-only
 * beans because {@code @Named} does not carry {@code @Default} in CDI.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class QuarkusCredentialResolver implements CredentialResolver {

    @Inject
    @Any
    Instance<CredentialsProvider> providers;

    private CredentialsProvider delegate;

    @PostConstruct
    void init() {
        if (providers.isUnsatisfied()) {
            throw new IllegalStateException(
                    "No CredentialsProvider found. Add a Quarkus credential extension " +
                    "(vault, aws-secrets-manager, etc.) or remove credentials-quarkus/ from the classpath.");
        }
        if (providers.isAmbiguous()) {
            throw new IllegalStateException(
                    "Multiple CredentialsProvider beans found. This bridge requires exactly one. " +
                    "Remove extras or use CredentialsProviderFinder for multi-provider deployments.");
        }
        delegate = providers.get();
    }

    @Override
    public Map<String, String> resolve(final String credentialRef) {
        if (credentialRef == null || credentialRef.isBlank()) {
            return Map.of();
        }
        final Map<String, String> result = delegate.getCredentials(credentialRef);
        if (result == null || result.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(result);
    }
}
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl credentials-quarkus -Dtest=QuarkusCredentialResolverTest`
Expected: 6 tests PASS.

- [ ] **Step 8: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/platform add credentials-quarkus/ pom.xml
git -C /Users/mdproctor/claude/casehub/platform commit -m "feat(platform#116): QuarkusCredentialResolver — CredentialsProvider bridge with Instance injection"
```

---

### Task 2: CLAUDE.md + full build verification

**Files:**
- Modify: `CLAUDE.md` — add `credentials-quarkus/` to modules table

**Interfaces:**
- Consumes: `QuarkusCredentialResolver` (from Task 1)
- Produces: updated CLAUDE.md; verified green build across all modules

- [ ] **Step 1: Update CLAUDE.md modules table**

In the `## Modules` table in `CLAUDE.md`, add after the `identity/` row:

```markdown
| `credentials-quarkus/` | `casehub-platform-credentials-quarkus` | @Alternative @Priority(1) CredentialResolver bridge to Quarkus CredentialsProvider. @Any Instance injection with @PostConstruct fail-fast. Enables Vault/AWS/GCP secret backends by classpath presence. No quarkus:build goal |
```

- [ ] **Step 2: Run full project build**

Run: `mvn --batch-mode install`
Expected: BUILD SUCCESS across all modules including the new `credentials-quarkus`.

- [ ] **Step 3: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/platform add CLAUDE.md
git -C /Users/mdproctor/claude/casehub/platform commit -m "docs(platform#116): add credentials-quarkus module to CLAUDE.md"
```
