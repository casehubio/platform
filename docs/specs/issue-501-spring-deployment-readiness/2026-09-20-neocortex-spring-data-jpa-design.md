# Neocortex Spring Data JPA — Design Spec

**Issue:** casehubio/parent#496
**Scale:** S | **Complexity:** Low
**Branch:** issue-501-spring-deployment-readiness (platform workspace)
**Target repo:** casehubio/neocortex

## Goal

Create Spring Data JPA equivalents for neocortex's 2 JPA persistence modules
(`memory-jpa`, `memory-cbr-jpa`), following the platform jpa-common + spring-jpa
pattern established across 10+ platform modules.

## Module Structure

4 new modules in neocortex, 2 refactored:

| Module | Type | Purpose |
|--------|------|---------|
| `memory-jpa-common` | new | Shared `MemoryEntry` entity + Flyway V1–V3 migrations |
| `memory-cbr-jpa-common` | new | Shared `CbrCaseEntity` entity + Flyway V1 migration |
| `memory-spring-jpa` | new | Spring Data JPA `CaseMemoryStore` implementation |
| `memory-cbr-spring-jpa` | new | Spring Data JPA `CbrCaseMemoryStore` implementation |
| `memory-jpa` | refactored | Depends on jpa-common, removes local entity copy |
| `memory-cbr-jpa` | refactored | Depends on jpa-common, removes local entity copy |

Additionally, 1 extraction in `memory-api`:
- `CbrCaseFilterMatcher` — static utility extracted from `JpaCbrCaseMemoryStore` (D27)

## Detailed Design

### 1. Entity Extraction (jpa-common modules)

**memory-jpa-common:**
- Move `MemoryEntry.java` from `memory-jpa` → `memory-jpa-common`
- Remove `extends PanacheEntityBase` — plain JPA entity with `@Entity`/`@Table`/`@Id`
- Move Flyway migrations (`db/memory/migration/V1–V3`) to jpa-common
- Dependencies: `casehub-neocortex-memory-api` (for domain type refs if needed),
  `jakarta.persistence-api` (provided), `jackson-databind` (provided)
- Jandex indexed

**Refactor memory-jpa:**
- Depend on `memory-jpa-common` for entity
- `JpaMemoryStore` uses `em.persist()` / `em.createQuery()` instead of Panache
  static methods (`MemoryEntry.persist()`)
- Remove Panache extends — entity is now a plain POJO
- Flyway migrations come from jpa-common classpath

**memory-cbr-jpa-common:**
- Move `CbrCaseEntity.java` from `memory-cbr-jpa` → `memory-cbr-jpa-common`
- Entity is already a plain JPA entity (no Panache) — no modifications needed
- Move Flyway migration (`db/cbr/migration/V1`) to jpa-common
- Dependencies: `jakarta.persistence-api` (provided)
- Jandex indexed

**Refactor memory-cbr-jpa:**
- Depend on `memory-cbr-jpa-common` for entity
- No code changes needed — already uses `em.persist()` directly
- Flyway migrations come from jpa-common classpath

### 2. Filter Matching Extraction (D27)

Extract from `JpaCbrCaseMemoryStore` to `memory-api`:

```java
package io.casehub.neocortex.memory.cbr;

public final class CbrCaseFilterMatcher {
    public static boolean matchesFilters(
            CbrCase storedCase, Map<String, CbrFilter> filters,
            CbrFeatureSchema schema) { ... }

    // private: matchesSingleFilter, matchesHasMatch, allSubFieldsMatch
}
```

Same pattern as `CbrSimilarityScorer` and `CbrFeatureValidator` — static utility,
pure domain logic, zero external dependencies.

Refactor `JpaCbrCaseMemoryStore` to call `CbrCaseFilterMatcher.matchesFilters()`
instead of its private methods.

### 3. memory-spring-jpa (Spring Data JPA CaseMemoryStore)

**Repository:**
```java
public interface MemoryEntryRepository extends JpaRepository<MemoryEntry, String> {
    // Derived queries for chronological path
    // @Query(nativeQuery=true) for FTS path
}
```

**Implementation: `SpringMemoryStore implements CaseMemoryStore`**
- Constructor-injected: `MemoryEntryRepository`, `CurrentPrincipal`, `ObjectMapper`
- FTS config via `@Value("${casehub.memory.jpa.fts.enabled:true}")` and
  `@Value("${casehub.memory.jpa.fts.language:english}")`
- `store()` / `storeAll()`: map `MemoryInput` → `MemoryEntry`, save via repository
- `query()`: dual-path — FTS (native query) when enabled + question present,
  JPQL otherwise (D28)
- `erase()` / `eraseById()` / `eraseSubject()` / `eraseSubjectAcrossTenants()`:
  `@Modifying @Query` for bulk deletes
- `scan()`: native query with dialect-aware attribute filtering (PostgreSQL
  jsonb vs H2 LIKE)
- `discoverTenants()`: native query for distinct tenant_id
- `purge()`: `@Modifying @Query` with configurable age + confidence filters
- All mutations enforce `MemoryPermissions.assertTenant()`

**AutoConfiguration:**
```java
@AutoConfiguration
@ConditionalOnClass(SpringMemoryStore.class)
@EnableJpaRepositories(basePackageClasses = MemoryEntryRepository.class)
public class MemoryJpaAutoConfiguration {
    @Bean @ConditionalOnMissingBean(CaseMemoryStore.class)
    public SpringMemoryStore springMemoryStore(...) { ... }
}
```

**Test:** H2-based `@DataJpaTest` verifying store/query/erase/storeAll. FTS
path tested only with PostgreSQL Testcontainers (optional, can be deferred).

### 4. memory-cbr-spring-jpa (Spring Data JPA CbrCaseMemoryStore)

**Repository:**
```java
public interface CbrCaseEntityRepository extends JpaRepository<CbrCaseEntity, String> {
    // Derived queries + @Query for tenant/domain/caseType filtering
}
```

**Implementation: `SpringCbrCaseMemoryStore implements CbrCaseMemoryStore`**
- Constructor-injected: `CbrCaseEntityRepository`, `ObjectMapper`
- Schema registry: `ConcurrentHashMap<String, CbrFeatureSchema>` (per-instance)
- `store()`: validate features, map → entity, save
- `retrieveSimilar()`: query entities, reconstruct → CbrCase (local ~15-line
  `reconstruct()` method), filter via `CbrCaseFilterMatcher.matchesFilters()`,
  score via `CbrSimilarityScorer.scoreDetailed()`, sort + truncate
- `erase()` / `eraseEntity()` / `eraseByScope()`: `@Modifying @Query`
- `recordOutcome()`: load entity, apply confidence adjustment, save
- `supersede()` / `reinstate()`: load, check state, mutate, save
- `purge()`: age-based + count-based + trust-based via `@Modifying @Query`
- `scan()`: paginated query, map → `CbrCaseSummary`
- `discoverTenants()`: distinct query
- `findCaseIds()` / `supersedeMatching()` / `reinstateMatching()` /
  `supersedeAll()` / `reinstateAll()`: combinations of query + filter + mutate
- `getSupersessionStatus()` / `findSupersededCases()`: query + map

**AutoConfiguration:**
```java
@AutoConfiguration
@ConditionalOnClass(SpringCbrCaseMemoryStore.class)
@EnableJpaRepositories(basePackageClasses = CbrCaseEntityRepository.class)
public class CbrJpaAutoConfiguration {
    @Bean @ConditionalOnMissingBean(CbrCaseMemoryStore.class)
    public SpringCbrCaseMemoryStore springCbrCaseMemoryStore(...) { ... }
}
```

**Test:** H2-based `@DataJpaTest` verifying store/retrieveSimilar/erase/
supersede/reinstate/recordOutcome.

### 5. Dependencies

**memory-jpa-common pom.xml:**
```xml
<dependencies>
    <dependency>
        <groupId>jakarta.persistence</groupId>
        <artifactId>jakarta.persistence-api</artifactId>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

**memory-spring-jpa pom.xml:**
```xml
<dependencies>
    <dependency>casehub-neocortex-memory-jpa-common</dependency>
    <dependency>casehub-neocortex-memory-api</dependency>
    <dependency>casehub-platform-api</dependency>  <!-- CurrentPrincipal -->
    <dependency>spring-boot-starter-data-jpa</dependency>
    <dependency>jackson-databind</dependency>
    <!-- test: spring-boot-starter-test, h2 -->
</dependencies>
```

**memory-cbr-spring-jpa pom.xml:**
```xml
<dependencies>
    <dependency>casehub-neocortex-memory-cbr-jpa-common</dependency>
    <dependency>casehub-neocortex-memory-api</dependency>  <!-- CbrCaseMemoryStore SPI + CbrCaseFilterMatcher -->
    <dependency>casehub-platform-api</dependency>  <!-- Path -->
    <dependency>spring-boot-starter-data-jpa</dependency>
    <dependency>jackson-databind</dependency>
    <!-- test: spring-boot-starter-test, h2 -->
</dependencies>
```

### 6. Integration with existing memory-spring

The existing `memory-spring` module (MemoryAutoConfiguration) already injects
`CaseMemoryStore` and `CbrCaseMemoryStore` — it provides runtime wiring (enrichment,
retention, CBR outcome processing). The new spring-jpa modules satisfy those
injections. No changes to `memory-spring` needed.

Classpath composition:
- `memory-spring` + `memory-spring-jpa` + `memory-cbr-spring-jpa` = full Spring
  memory subsystem with JPA persistence
- `memory-spring` + `memory-inmem` = volatile test/dev configuration

### 7. What This Does NOT Include

- No spring-generator usage — these modules use handwritten Spring Data repos
  (no Quarkus annotations to generate from)
- No `@Scheduled` retention — retention scheduling is already in `memory-spring`
  via `MemoryRetentionPurger` and `CbrRetentionPurger` core POJOs
- No Micrometer `@Timed` equivalent — the Quarkus module uses Micrometer
  annotations; Spring Boot auto-configures Micrometer. Can add `@Timed` later
  if needed.

## References

- `neocortex/memory-jpa/src/main/java/io/casehub/neocortex/memory/jpa/` — Quarkus implementation (3 files)
- `neocortex/memory-cbr-jpa/src/main/java/io/casehub/neocortex/memory/cbr/jpa/` — Quarkus CBR implementation (2 files)
- `platform/memory-jpa-common/` — platform entity extraction pattern
- `platform/persistence-spring-jpa/` — platform Spring Data JPA pattern
- `neocortex/memory-spring/` — existing Spring auto-configuration (runtime wiring)
- D27 — CBR filter extraction rationale
- D28 — FTS dual-path rationale
