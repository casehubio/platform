# Engine Spring Data persistence-hibernate — Design Spec

**Issue:** casehubio/parent#497
**Epic:** casehubio/parent#501 (Spring Deployment Readiness)
**Scale:** M | **Complexity:** Med

## Problem

casehub-engine uses `persistence-hibernate` for data access — 7 JPA entities,
9 SPI implementations, PostgreSQL row-level security (RLS) via session
variables, and 12 Flyway migrations. Spring deployment needs Spring Data JPA
equivalents for all 9 SPIs, plus a generated Spring MVC controller for
`ActorStateResource`.

## Module Structure

**2 new modules + 2 modified modules + 1 plugin addition:**

| Module | Type | Purpose |
|--------|------|---------|
| `persistence-jpa-common` | New | Shared entities, Flyway migrations, TenantContextManager, RlsPolicySetup |
| `persistence-spring-jpa` | New | Spring Data JPA implementations of 9 SPIs |
| `persistence-hibernate` | Refactored | Delegates to jpa-common entities, uses TenantContextManager |
| `engine-support-spring` | Modified | `@ConditionalOnMissingBean` on in-memory beans, rest-spring-generator plugin |

## persistence-jpa-common

### Entities

Move all 7 entity classes from `persistence-hibernate` to
`persistence-jpa-common`. They are already plain JPA entities (no Panache):

| Entity | Table | PK | Notable |
|--------|-------|----|---------|
| `CaseInstanceEntity` | `case_instance` | Long IDENTITY | `@ManyToOne(EAGER)` to CaseMetaModel, 2 `@ElementCollection`, jsonb exchangeHeaders + pendingActionGate, `@DynamicUpdate` |
| `CaseMetaModelEntity` | `case_meta_model` | Long IDENTITY | jsonb definition as `JsonNode` |
| `EventLogEntity` | `event_log` | Long IDENTITY | `seq` GENERATED ALWAYS AS IDENTITY, 2 jsonb (`JsonNode`) |
| `ExecutionSnapshotEntity` | `execution_snapshot` | UUID caseId | 3 jsonb (stored as String) |
| `PlanItemEntity` | `plan_item` | Long IDENTITY | `@DynamicUpdate`, String planItemId with unique constraint |
| `PlanVersionEntity` | `plan_version` | UUID | 3 jsonb (stored as String) |
| `SubCaseGroupEntity` | `subcase_group` | Long IDENTITY | 1 `@ElementCollection` (childCaseIds) |

Remove Quarkus-specific annotations if any (none expected — entities use
standard Jakarta Persistence only). Add Jandex maven plugin for Spring Data
entity scanning.

Also move `PendingActionGateMapper` (package-private utility for jsonb
serialization) alongside `CaseInstanceEntity`.

### Flyway Migrations

Move all 12 migration files to `src/main/resources/db/engine/migration/`:

```
V1.0.0__Create_Quartz_Tables.sql     (inert in Spring — no harm)
V1.1.0__Create_Application_Tables.sql
V1.2.0__Add_Case_Instance_Label_Table.sql
...through...
V1.11.0__Create_Plan_Version_Table.sql
```

Both `persistence-hibernate` and `persistence-spring-jpa` configure Flyway
to scan `classpath:db/engine/migration/`. The `persistence-hibernate` module
removes its own copy and depends on jpa-common for migrations.

### TenantContextManager (D29, D33)

Framework-neutral POJO extracted from `TenantAwareRepository`:

```java
public class TenantContextManager {
    private final EntityManager em;
    private final boolean rlsEnabled;

    public TenantContextManager(EntityManager em, boolean rlsEnabled) { ... }

    public void setTenantContext(String tenancyId) {
        // validate tenancyId (no quotes, no backslashes)
        // em.createNativeQuery("SET LOCAL \"casehub.tenancy_id\" = '" + tenancyId + "'").executeUpdate()
    }

    public void setCrossTenantContext() {
        // if (rlsEnabled) em.createNativeQuery("SET LOCAL ROLE casehub_crosstenancy").executeUpdate()
    }
}
```

The Quarkus `TenantAwareRepository` is refactored from an abstract base class
with `@Inject` fields to a thin wrapper that constructs `TenantContextManager`
from CDI-injected `EntityManager` and `@ConfigProperty`. All 9 repository
and store classes change from inheritance to composition.

### RlsPolicySetup (D30)

Framework-neutral POJO extracted from `RlsPolicyApplicator`:

```java
public class RlsPolicySetup {
    private static final List<String> TABLES =
        List.of("case_instance", "case_meta_model", "event_log", "plan_item", "subcase_group");

    private final DataSource dataSource;
    private final boolean rlsEnabled;

    public RlsPolicySetup(DataSource dataSource, boolean rlsEnabled) { ... }

    public void apply() {
        // if (!rlsEnabled) return
        // createBypassRole + applyRls per table (existing SQL logic)
    }
}
```

### Dependencies

```xml
<dependencies>
    <dependency>jakarta.persistence:jakarta.persistence-api (provided)</dependency>
    <dependency>io.casehub:casehub-engine-common-core</dependency>
    <dependency>com.fasterxml.jackson.core:jackson-databind (provided)</dependency>
</dependencies>
<plugins>
    <plugin>io.smallrye:jandex-maven-plugin</plugin>
</plugins>
```

Package: `io.casehub.persistence.jpa`

## persistence-spring-jpa

### SPI Implementations

9 SPI implementations, each as a POJO class with constructor-injected
`EntityManager` + `TenantContextManager`. Wired via `@Bean` in the
auto-configuration.

| SPI | Implementation | Notes |
|-----|---------------|-------|
| `CaseInstanceRepository` | `SpringJpaCaseInstanceRepository` | 9 methods, tenant-scoped, `updateStateAndAppendEvent` transactional |
| `CaseMetaModelRepository` | `SpringJpaCaseMetaModelRepository` | 4 methods, tenant-scoped |
| `EventLogRepository` | `SpringJpaEventLogRepository` | 10 methods, tenant-scoped, native SQL for scheduling queries |
| `SubCaseGroupRepository` | `SpringJpaSubCaseGroupRepository` | 6 methods, tenant-scoped, `incrementCompleted`/`incrementRejected` use UPDATE queries |
| `CrossTenantEventLogRepository` | `SpringJpaCrossTenantEventLogRepository` | 7 methods, cross-tenant context |
| `CrossTenantCaseInstanceRepository` | `SpringJpaCrossTenantCaseInstanceRepository` | 1 method, cross-tenant context |
| `PlanItemStore` | `SpringJpaPlanItemStore` | 7 methods, mixed tenant + cross-tenant |
| `PlanVersionStore` | `SpringJpaPlanVersionStore` | 5 methods, static ObjectMapper for jsonb |
| `ExecutionSnapshotStore` | `SpringJpaExecutionSnapshotStore` | 7 methods, static ObjectMapper for jsonb |

Each implementation mirrors the corresponding `persistence-hibernate` class
but receives `TenantContextManager` via constructor rather than inheriting
from `TenantAwareRepository`. The query logic (JPQL, CriteriaBuilder, native
SQL) is identical — the only changes are constructor injection and
`@Transactional` annotations replacing `@Transactional` from different
packages.

### Auto-Configuration

```java
@AutoConfiguration
@ConditionalOnClass(EntityManager.class)
@EntityScan(basePackages = "io.casehub.persistence.jpa")
public class PersistenceAutoConfiguration {

    @Bean TenantContextManager tenantContextManager(
            EntityManager em,
            @Value("${casehub.rls.enabled:false}") boolean rlsEnabled) { ... }

    @Bean RlsPolicySetup rlsPolicySetup(
            DataSource dataSource,
            @Value("${casehub.rls.enabled:false}") boolean rlsEnabled) { ... }

    @Bean CaseInstanceRepository caseInstanceRepository(
            EntityManager em, TenantContextManager tcm) { ... }

    // ... 8 more SPI beans ...

    @Bean CommandLineRunner rlsPolicyRunner(RlsPolicySetup setup) {
        return args -> setup.apply();
    }
}
```

### Dependencies

```xml
<dependencies>
    <dependency>io.casehub:casehub-engine-persistence-jpa-common</dependency>
    <dependency>io.casehub:casehub-engine-common-core</dependency>
    <dependency>io.casehub:casehub-platform-api</dependency>
    <dependency>org.springframework.boot:spring-boot-starter-data-jpa</dependency>
    <dependency>org.flywaydb:flyway-core</dependency>
    <dependency>org.flywaydb:flyway-database-postgresql</dependency>
    <dependency>com.fasterxml.jackson.datatype:jackson-datatype-jsr310</dependency>
    <!-- test -->
    <dependency>org.springframework.boot:spring-boot-starter-test (test)</dependency>
    <dependency>com.h2database:h2 (test)</dependency>
    <dependency>io.casehub:casehub-platform-spring-testing (test)</dependency>
</dependencies>
```

Package: `io.casehub.persistence.spring`

### Spring Boot Auto-Configuration Registration

`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:
```
io.casehub.persistence.spring.PersistenceAutoConfiguration
```

## persistence-hibernate Refactor

### TenantAwareRepository Elimination

Replace `TenantAwareRepository` abstract base class with
`TenantContextManager` composition:

**Before:**
```java
class JpaCaseInstanceRepository extends TenantAwareRepository
    implements CaseInstanceRepository {
    // uses this.em and this.setTenantContext()
}
```

**After:**
```java
@ApplicationScoped
class JpaCaseInstanceRepository implements CaseInstanceRepository {
    private final EntityManager em;
    private final TenantContextManager tcm;

    @Inject
    JpaCaseInstanceRepository(EntityManager em, TenantContextManager tcm) { ... }
    // uses em and tcm.setTenantContext()
}
```

A `@Produces TenantContextManager` method in a CDI producer bean constructs
the POJO from `@Inject EntityManager` and `@ConfigProperty`:

```java
@ApplicationScoped
class PersistenceProducers {
    @Produces @ApplicationScoped
    TenantContextManager tenantContextManager(
            EntityManager em,
            @ConfigProperty(name = "casehub.rls.enabled", defaultValue = "false") boolean rlsEnabled) {
        return new TenantContextManager(em, rlsEnabled);
    }
}
```

### RlsPolicyApplicator Delegation

```java
@ApplicationScoped
public class RlsPolicyApplicator {
    @Inject RlsPolicySetup setup;

    void onStart(@Observes @Priority(100) StartupEvent ev) {
        setup.apply();
    }
}
```

A `@Produces` method constructs `RlsPolicySetup`:

```java
@Produces @ApplicationScoped
RlsPolicySetup rlsPolicySetup(
        AgroalDataSource dataSource,
        @ConfigProperty(name = "casehub.rls.enabled", defaultValue = "false") boolean rlsEnabled) {
    return new RlsPolicySetup(dataSource, rlsEnabled);
}
```

### Flyway Migration Location

Update `application.properties` to point to the jpa-common migration
location: `quarkus.flyway.locations=classpath:db/engine/migration`

### Dependencies

Add `casehub-engine-persistence-jpa-common` as a compile dependency.
Remove entity classes (moved to jpa-common).

## engine-support-spring Modifications

### D31: @ConditionalOnMissingBean on In-Memory Beans

Add `@ConditionalOnMissingBean` to the 5 in-memory persistence `@Bean`
methods in `EngineSupportAutoConfiguration`:

```java
@Bean @Primary @ConditionalOnMissingBean
InMemoryCaseInstanceRepository caseInstanceRepository(EventLogRepository elr) { ... }

@Bean @Primary @ConditionalOnMissingBean
InMemoryCaseMetaModelRepository caseMetaModelRepository() { ... }

@Bean @Primary @ConditionalOnMissingBean
InMemoryEventLogRepository eventLogRepository() { ... }

@Bean @Primary @ConditionalOnMissingBean
InMemoryPlanItemStore planItemStore() { ... }

@Bean @Primary @ConditionalOnMissingBean
InMemorySubCaseGroupRepository subCaseGroupRepository() { ... }
```

When `persistence-spring-jpa` is on the classpath, its unconditional beans
win. When absent, the in-memory defaults still work (E2E test, dev mode).

### D32: rest-spring-generator Plugin

Add the rest-spring-generator plugin to `engine-support-spring/pom.xml`:

```xml
<plugin>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-platform-rest-spring-generator</artifactId>
    <version>${casehub-platform.version}</version>
    <executions>
        <execution>
            <id>generate</id>
            <goals><goal>generate</goal></goals>
            <configuration>
                <quarkusModules>
                    <quarkusModule>${project.basedir}/../engine-support-core</quarkusModule>
                </quarkusModules>
            </configuration>
        </execution>
        <execution>
            <id>verify-rest-drift</id>
            <goals><goal>verify</goal></goals>
            <phase>verify</phase>
            <configuration>
                <quarkusModules>
                    <quarkusModule>${project.basedir}/../engine-support-core</quarkusModule>
                </quarkusModules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

This generates a Spring MVC `@RestController` for `ActorStateResource`
(`@GET /actors/{actorId}/state`). The core POJO is already a `@Bean` in
`EngineSupportAutoConfiguration` — the generated controller injects and
delegates to it.

## Testing Strategy

### persistence-spring-jpa Tests

One test class per SPI implementation, using `@SpringBootTest` + H2.
Pattern matches platform spring-jpa tests:

- `@SpringBootTest(classes = PersistenceAutoConfiguration.class)`
- H2 in-memory database (PostgreSQL-specific RLS tests excluded — RLS
  requires PostgreSQL)
- `@Transactional` test methods with rollback
- Verify all SPI methods produce correct results

For RLS-specific behavior, add `*IT.java` integration tests that require
PostgreSQL (excluded from `mvn test`, run via `mvn verify` with
Testcontainers or external PostgreSQL).

### persistence-hibernate Tests

Existing tests continue to pass — the refactor is structural (inheritance
→ composition), not behavioral. Run full `mvn test` on the module after
refactoring.

### Displacement Tests

Verify that when both `engine-support-spring` and `persistence-spring-jpa`
are on the classpath, the JPA beans displace the in-memory defaults. Add
a test in `persistence-spring-jpa`:

```java
@SpringBootTest(classes = {EngineSupportAutoConfiguration.class, PersistenceAutoConfiguration.class})
class DisplacementTest {
    @Autowired CaseInstanceRepository repo;

    @Test void jpaDisplacesInMemory() {
        assertThat(repo).isInstanceOf(SpringJpaCaseInstanceRepository.class);
    }
}
```

## Build Order

```
1. persistence-jpa-common  (entities, migrations, TenantContextManager, RlsPolicySetup)
2. persistence-hibernate   (refactored — depends on jpa-common)
3. persistence-spring-jpa  (depends on jpa-common)
4. engine-support-spring   (modified — @ConditionalOnMissingBean + rest-spring-generator)
```

Step 2 (persistence-hibernate refactor) and Step 3 (persistence-spring-jpa)
are independent once jpa-common exists.

## Scope Boundaries

**In scope:**
- 2 new Maven modules (persistence-jpa-common, persistence-spring-jpa)
- persistence-hibernate refactor (inheritance → composition)
- engine-support-spring modification (@ConditionalOnMissingBean + REST gen)
- Consumer guide update (Spring Data JPA persistence section)

**Out of scope:**
- Cross-tenant SPI in-memory defaults (4 SPIs without in-memory impl — the
  Spring JPA module provides the first real implementation, same as Quarkus)
- Quartz table migration cleanup (V1.0.0 stays — inert in Spring)
- Spring-native multi-tenancy (@TenantId) — uses PostgreSQL RLS via SET LOCAL

## References

- `persistence-hibernate/` source — 7 entities, 9 SPI implementations, TenantAwareRepository, RlsPolicyApplicator
- `engine-support-spring/EngineSupportAutoConfiguration.java` — 5 in-memory @Primary beans (lines 207-244)
- `engine-support-core/ActorStateResource.java` — @Path("/actors"), 1 endpoint
- Platform `memory-jpa-common` + `memory-spring-jpa` — established jpa-common/spring-jpa pattern
- Platform `rest-spring-generator` plugin configuration (platform-spring/pom.xml)
- D5 (constructor-scanning), D7 (ConfigProperties), D29-D33 (this issue's decisions)
- casehubio/parent#497 issue
