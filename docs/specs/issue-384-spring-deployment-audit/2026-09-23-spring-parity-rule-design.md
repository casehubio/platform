# SpringParityRule — Build-Time Drift Detection for Spring Module Coverage

**Issue:** casehubio/platform#396
**Date:** 2026-09-23
**Branch:** issue-384-spring-deployment-audit

## Problem

When a new Quarkus module with `@Produces` beans is added, no build-time mechanism detects the absence of a Spring counterpart. The gap is only discovered at Spring Boot deployment time. Bean-level parity (every `@Produces` → corresponding `@Bean`) is already enforced by the generator `verify` goals in each `*-spring` module — the uncovered gap is **module-level**: detecting when a Quarkus CDI module has no Spring generation path at all.

## Solution

A new `SpringParityRule` Maven Enforcer rule in the `drift-detection/` module. Uses the Enforcer 3.x API (`AbstractEnforcerRule` with `@Named` + `@Inject`) to inject `MavenSession` for reactor-level access. Configured to run in `spring-integration-test` (the existing Spring composition gate).

Two checks:

1. **Module coverage** — every Quarkus CDI module has a Spring path
2. **Generator completeness** — every `*-spring` module with generators has both `generate` and `verify` goals

## Architecture

### Class: `SpringParityRule`

```
drift-detection/
  src/main/java/io/casehub/platform/drift/
    SpringParityRule.java      # new — extends AbstractEnforcerRule
    DriftDetectionRule.java    # existing — unchanged
  src/main/resources/
    spring-parity-exceptions.txt  # new — module exception list
  src/test/java/io/casehub/platform/drift/
    SpringParityRuleTest.java  # new
```

**Constructor injection:**
- `MavenSession session` — reactor project list
- `MavenProject project` — current project context

**Configuration parameters (set via enforcer plugin XML):**
- `String exceptionsFile` — path to exceptions file

### Check 1: Module Coverage

**Step 1 — Build the "covered" set:**

Scan all reactor modules whose artifactId ends with `-spring`. For each:

a. Parse the module's POM for generator plugin executions (`casehub-platform-spring-generator`, `casehub-platform-graphql-spring-generator`, `casehub-platform-rest-spring-generator`, `casehub-platform-mcp-spring-generator`).

b. Extract `<quarkusModule>` values from each execution's configuration. These are filesystem paths like `${project.basedir}/../governance`. Resolve to the corresponding reactor module by matching the directory name against reactor module base directories.

c. Add all resolved modules to the covered set.

d. For hand-written `*-spring` modules (no generator plugins), infer coverage: if artifactId is `foo-spring`, add `foo` to the covered set. This handles `callback-spring`, `agent-gate-spring`, `mcp-spring`, etc.

**Step 2 — Build the "candidate" set:**

All reactor modules NOT matching auto-exclude patterns:

| Suffix pattern | Reason excluded |
|----------------|-----------------|
| `-core` | Framework-neutral POJOs |
| `-api` | Pure Java SPIs |
| `-spring`, `-spring-jpa` | IS the Spring coverage |
| `-jpa-common` | Shared JPA entities |
| `-jpa` | Quarkus-specific JPA (Spring uses `-spring-jpa`) |
| `-testing` | Test fixtures |
| `-generator` | Build-time code generators |
| `-starter` | Aggregate POMs |
| `-alpha` | Runtime libraries without CDI |

Additional exact-match exclusions: the root aggregator POM itself (packaging=pom modules with no `src/main/java`).

**Step 3 — Compute violations:**

```
violations = candidates - covered - exceptions
```

Each violation reports: `"Module 'foo' has no Spring coverage — add a *-spring module, a <quarkusModule> reference, or list it in the exceptions file with justification."`

### Check 2: Generator Completeness

For each `*-spring` module that has at least one generator plugin configured:

- Collect all execution elements per plugin
- Verify that for each `generate` execution, a corresponding `verify` execution exists (matched by plugin, not necessarily by execution ID)
- A plugin with `generate` but no `verify` goal → error: `"Module 'foo-spring' has spring-generator with 'generate' goal but no 'verify' goal — drift will go undetected."`

Modules with no generator plugins (hand-written `*-spring`) are not checked.

### Exceptions File

Located at `drift-detection/src/main/resources/spring-parity-exceptions.txt`. Reuses the `AllowList` format from `DriftDetectionRule`:

```
# Quarkus-only credentials bridge — Spring uses EnvironmentCredentialResolver
credentials-quarkus

# In-memory dev/test modules — Spring uses JPA counterparts (*-spring-jpa)
acl-inmem
callback-inmem
datasource-inmem
delivery-channel-inmem
delivery-tracking-inmem
digest-inmem
endpoints-memory
notification-settings-inmem
notifications-inmem
platform-view-inmem
subscriptions-inmem

# Simulation framework — Quarkus-only by design
simulation-inmem
event-simulation

# YAML config reader — displaces mock, Quarkus-specific ConfigMapping
config

# Optional modules — activated by classpath, no CDI beans requiring Spring translation
platform-pdf
platform-signing

# Test fixtures
testing

# GraphQL infrastructure — Quarkus SmallRye-specific
graphql
graphql-client

# Quarkus-specific infrastructure (REST/filters/NoSQL)
acl-admin
acl-worker
persistence-mongodb

# LLM config — Quarkus-specific SmallRye config
llm-config
llm-config-bedrock
llm-config-vertex
```

**Note:** Several modules that appear uncovered are actually covered:
- `callback`, `mcp`, `oidc`, `streams-amqp/camel/kafka/poll` → hand-written `*-spring` modules
- `callback-client`, `notification-dispatch`, `streams-webhook` → rest-spring-generator in platform-spring
- `agent-*` backends → generated `agent-*-spring` modules

The exact exceptions list is finalized during implementation by running the rule against the baseline. Modules like `endpoints-config`, `preferences-editor`, `notifications`, `subscriptions`, `notification-dispatch` may be covered via graphql-spring-generator scanning their `-core` counterparts — to be verified.

Each entry preceded by a justification comment. Unjustified entries produce build warnings (not errors — gives migration path).

**Note:** The exceptions file is populated with the current baseline. As more modules get Spring counterparts (tracked by other issues in the queue: #394, #395, #397, #398, #399), entries will be removed.

### POM Changes

**drift-detection/pom.xml** — add dependencies:

```xml
<dependency>
    <groupId>org.apache.maven</groupId>
    <artifactId>maven-core</artifactId>
    <version>3.9.9</version>
    <scope>provided</scope>
</dependency>
<dependency>
    <groupId>javax.inject</groupId>
    <artifactId>javax.inject</artifactId>
    <version>1</version>
    <scope>provided</scope>
</dependency>
```

**spring-integration-test/pom.xml** — add enforcer plugin execution:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-enforcer-plugin</artifactId>
    <dependencies>
        <dependency>
            <groupId>io.casehub</groupId>
            <artifactId>casehub-platform-drift-detection</artifactId>
            <version>${project.version}</version>
        </dependency>
    </dependencies>
    <executions>
        <execution>
            <id>spring-parity</id>
            <goals><goal>enforce</goal></goals>
            <configuration>
                <rules>
                    <springParityRule>
                        <exceptionsFile>
                            ${project.basedir}/../drift-detection/src/main/resources/spring-parity-exceptions.txt
                        </exceptionsFile>
                    </springParityRule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

## Test Strategy

Unit tests in `SpringParityRuleTest` using mock `MavenSession` and `MavenProject`:

| Scenario | Expected |
|----------|----------|
| Module with no Spring counterpart, not in exceptions | Fail |
| Module covered by `<quarkusModule>` reference | Pass |
| Module with hand-written `*-spring` counterpart | Pass |
| Module in exceptions file | Pass |
| Auto-excluded module (`*-core`, `*-api`, etc.) | Not checked |
| `*-spring` with generator missing verify goal | Fail |
| `*-spring` with no generators (hand-written) | Pass |
| Unjustified exception entry | Warning |
| Current codebase baseline | Clean (all pass) |

Integration: `mvn verify` on the current codebase must pass with zero violations.

## Acceptance Criteria Mapping

| Criterion | How satisfied |
|-----------|---------------|
| Detects new @Produces without @Bean across reactor | Module-level coverage check ensures every Quarkus module has a Spring generation path; generator verify goals handle bean-level checking |
| Module-level check detects new Quarkus module without Spring counterpart | Check 1 — candidates minus covered minus exceptions |
| Exceptions file for intentionally Quarkus-only modules | `spring-parity-exceptions.txt` with AllowList format |
| Meta-verify confirms all generators are running | Check 2 — generator completeness for *-spring modules |
| Rule passes on current codebase | Exceptions file populated with current baseline |

## References

- `drift-detection/src/main/java/.../DriftDetectionRule.java` — existing AllowList pattern and Enforcer rule structure
- `spring-generator/src/main/java/.../JandexProducerScanner.java` — how bean scanning works (not reused here, but validates that bean-level checking is already handled)
- `platform-spring/pom.xml` — consolidated generator config covering multiple modules
- `governance-spring/pom.xml` — standard direct generator config with generate + verify
- `callback-spring/src/` — hand-written *-spring module (no generators)
- [Maven Enforcer 3.x Custom Rule API](https://maven.apache.org/enforcer/enforcer-api/writing-a-custom-rule.html) — AbstractEnforcerRule, @Named, @Inject pattern
- [ReactorModuleConvergence](https://maven.apache.org/enforcer/enforcer-rules/reactorModuleConvergence.html) — built-in reactor-level rule as reference
- casehubio/platform#384 — audit that identified this gap (dim 5)
