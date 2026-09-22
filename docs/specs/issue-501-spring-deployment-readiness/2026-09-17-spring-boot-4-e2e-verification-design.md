# Spring Boot 4 E2E Verification — Design Spec

**Issue:** casehubio/parent#499
**Epic:** casehubio/parent#501 (Spring deployment readiness)
**Date:** 2026-09-17

## Goal

Prove that all platform Spring modules compose into a working Spring Boot 4.1 application. This is the "does it actually start?" gate before any real Spring deployment work.

## Scope

Three work items on one branch:

1. **Version alignment** — remove local version overrides from platform pom.xml, inherit from parent BOM (Quarkus 3.39.3, Spring Boot 4.1.1)
2. **Jackson 2 bridge** — add `spring-boot-jackson2` to ensure both frameworks inject Jackson 2 `ObjectMapper`
3. **E2E test module** — new `spring-integration-test/` module that boots the full app and verifies composition

### Out of scope

- Jackson 3 migration (deferred to Quarkus 4 GA, ~Nov 2026)
- Business logic testing through Spring stack (per-module tests cover this)
- Spring Boot Actuator beyond health check
- Agent module Spring auto-configurations (#504 — separate issue)

## Architecture

### Version Alignment

Remove from platform `pom.xml` `<properties>`:
- `<spring-boot.version>3.4.5</spring-boot.version>` — inherit 4.1.1 from parent BOM
- `<quarkus.platform.version>3.32.2</quarkus.platform.version>` — inherit 3.39.3 from parent BOM

**Parent BOM** (already committed to main):
- `quarkus.platform.version`: 3.39.3
- `spring-boot.version`: 4.1.1

### Jackson 2 Bridge

Add `spring-boot-jackson2` to `spring-boot-starter/pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-jackson2</artifactId>
    <version>${spring-boot.version}</version>
</dependency>
```

This replaces Spring Boot 4's default Jackson 3 auto-configuration with Jackson 2, ensuring the `ObjectMapper` bean injected into core modules (`DeliveryTracker`, `DeliveryRetryProcessor`, `CallbackDispatcher`) remains `com.fasterxml.jackson.databind.ObjectMapper`.

**Lifecycle:** `spring-boot-jackson2` is deprecated. When Quarkus 4 GA ships (~Nov 2026), both frameworks move to Jackson 3 and this bridge is removed. See D1 in decisions.md.

### spring-ai Compatibility Check

Platform pom.xml declares `<spring-ai.version>2.0.0</spring-ai.version>`. Spring AI 2.x may not be compatible with Spring Boot 4.1. Verify during implementation — if incompatible, bump or exclude.

### Flyway Compatibility Check

D8 (from #493) established that Flyway 12 (Quarkus BOM) is incompatible with Spring Boot 3.4's `FlywayAutoConfiguration`. Spring Boot 4.1 may resolve this by shipping Flyway 12+ natively. Check during implementation — if resolved, the `spring.flyway.enabled=false` workaround in `-spring-jpa` test configs may be removable.

## New Module: `spring-integration-test/`

### POM

```xml
<artifactId>casehub-platform-spring-integration-test</artifactId>
<packaging>jar</packaging>

<dependencies>
    <!-- The full starter — all 13 Spring runtime modules -->
    <dependency>
        <groupId>io.casehub</groupId>
        <artifactId>casehub-spring-boot-starter</artifactId>
        <version>${project.version}</version>
    </dependency>

    <!-- Spring Boot web + actuator -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>

    <!-- JPA + H2 for persistence stores -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- Test fixtures -->
    <dependency>
        <groupId>io.casehub</groupId>
        <artifactId>casehub-platform-spring-testing</artifactId>
        <version>${project.version}</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

No `quarkus:build` goal. No Flyway (`spring.flyway.enabled=false`). H2 with `MODE=PostgreSQL` for JPA stores.

### Application Class

`src/main/java/io/casehub/platform/spring/integration/TestApplication.java`:

```java
@SpringBootApplication
public class TestApplication {
    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}
```

Anchors component scanning and auto-configuration discovery.

### application.properties (test)

```properties
spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.hibernate.ddl-auto=create-drop
spring.flyway.enabled=false
management.endpoints.web.exposure.include=health
```

### Test Class

`src/test/java/io/casehub/platform/spring/integration/SpringBootCompositionTest.java`:

Four verification layers:

1. **Context loads** — `@SpringBootTest` with `webEnvironment = RANDOM_PORT`. If context fails to load (missing beans, circular deps), the test fails before any assertion.

2. **Generated controllers register** — inject `RequestMappingHandlerMapping`, assert known endpoints exist (e.g., `/preferences`, `/acl`, `/subscriptions`, `/callbacks`).

3. **Health check** — `TestRestTemplate.getForEntity("/actuator/health")` returns 200 with `status: UP`.

4. **Jackson bridge active** — inject `ObjectMapper` bean, assert `instanceof com.fasterxml.jackson.databind.ObjectMapper` and NOT `instanceof tools.jackson.databind.ObjectMapper`.

## Breakage Surface

Spring Boot 3.4 → 4.1 is a major version jump. Expected breakage areas:

| Area | Risk | Mitigation |
|------|------|------------|
| Auto-configuration class renames | Medium | Spring Boot 4 modularization renamed some classes. Fix import errors. |
| Property renames | Low | `spring-boot-properties-migrator` identifies these at startup. |
| `@JsonComponent` → `@JacksonComponent` | Low | Only relevant if we use `@JsonComponent` in Spring modules (unlikely with jackson2 bridge). |
| `FlywayAutoConfiguration` | Already handled | D8 workaround (`spring.flyway.enabled=false`) stays until verified. |
| spring-ai compatibility | Medium | Check 2.x against Boot 4.1 — bump if needed. |
| Quarkus 3.32→3.39 transitive changes | Low | Quarkus BOM change is internal to Quarkus modules. Spring modules don't depend on Quarkus BOM transitively. |

## Implementation Order

1. Remove version overrides from platform pom.xml
2. `mvn compile` — fix any compilation errors from the bumps
3. Add `spring-boot-jackson2` to starter POM
4. Check spring-ai and Flyway compatibility
5. Create `spring-integration-test/` module
6. Write TestApplication + SpringBootCompositionTest
7. `mvn test` — fix any test failures
8. `mvn install` — full build green

## References

- [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- [Spring Boot 4.0 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Release-Notes)
- [Jackson 3 Migration Guide](https://github.com/FasterXML/jackson/blob/main/jackson3/MIGRATING_TO_JACKSON_3.md)
- [Introducing Jackson 3 support in Spring](https://spring.io/blog/2025/10/07/introducing-jackson-3-support-in-spring/)
- [Quarkus Jackson 3 Epic #52036](https://github.com/quarkusio/quarkus/issues/52036)
- [Road to Quarkus 4](https://github.com/quarkusio/quarkus/discussions/52020)
- casehubio/parent#499 — issue scope
- casehubio/parent#501 — parent epic
- `specs/issue-493-spring-data-jpa/decisions.md` — D8 (Flyway), D9 (@DataJpaTest config)
- `spring-boot-starter/pom.xml` — starter dependency list
- `platform-spring/pom.xml` — generator plugin configuration
- Core module constructor analysis: `CallbackDispatcher`, `DeliveryTracker`, `DeliveryRetryProcessor`
