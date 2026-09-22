# Design: Work Spring Data MongoDB (#498)

## Problem

casehub-work uses MongoDB (`persistence-mongodb`) for data access via Quarkus
Panache MongoDB. Spring deployment needs equivalent persistence. The existing
module has 16 store implementations, 13 document classes, and 1 index
initializer — all tightly coupled to `PanacheMongoEntityBase` and CDI.

## Strategy

Full core extraction (D34). The MongoDB Java driver's `MongoClient` is
framework-neutral — both Quarkus and Spring Boot auto-configure
`com.mongodb.client.MongoClient`. Panache is syntactic sugar over
`MongoCollection`; the stores already build BSON filters manually. Extract
all domain logic to framework-neutral POJOs, leaving thin framework wrappers.

No Spring Data repositories (D35). Core POJOs own all query logic using
`MongoCollection` directly. Spring module is pure wiring.

## Module Structure

| Module | Artifact | Purpose |
|--------|----------|---------|
| `persistence-mongodb-core` | `casehub-work-persistence-mongodb-core` | 16 store POJOs + 13 document POJOs + IndexInitializer + codec config |
| `persistence-mongodb` (refactored) | `casehub-work-persistence-mongodb` | Thin Quarkus CDI — `@Produces` returning core POJOs |
| `persistence-spring-mongodb` | `casehub-work-persistence-spring-mongodb` | Thin Spring `@AutoConfiguration` — `@Bean` returning core POJOs |

### Dependency Graph

```
persistence-mongodb-core
  ├── work-api          (WorkItemStore, CrossTenantWorkItemStore SPIs)
  ├── runtime           (10 store SPIs + domain types)
  ├── core              (RoutingCursorStore SPI)
  ├── issue-tracker     (IssueLinkStore SPI)
  ├── platform-api      (CurrentPrincipal)
  └── mongodb-driver-sync  (MongoClient, MongoDatabase, MongoCollection, BSON)

persistence-mongodb (Quarkus)
  ├── persistence-mongodb-core
  ├── quarkus-arc
  └── quarkus-mongodb-client  (auto-configures MongoClient)

persistence-spring-mongodb (Spring)
  ├── persistence-mongodb-core
  └── spring-boot-starter-data-mongodb  (auto-configures MongoClient)
```

Note: `spring-boot-starter-data-mongodb` is used for its auto-configured
`MongoClient` bean and connection pooling — not for Spring Data repository
infrastructure. The `spring-data-mongodb` transitive is unused.

## persistence-mongodb-core

### Document Classes (13)

Strip `PanacheMongoEntityBase` inheritance. Keep `@BsonId` and `@BsonProperty`
annotations from `org.bson.codecs.pojo.annotations`. Documents become plain
Java classes with public fields (matching Panache convention) or records where
feasible.

Each document retains its `from(domain)` static factory and `toDomain()`
instance method for domain↔document mapping.

| Document | Collection | Notes |
|----------|-----------|-------|
| `MongoWorkItemDocument` | `work_items` | ~50 fields, embedded `MongoLabel` subdocument |
| `MongoAuditEntryDocument` | `audit_entries` | append-only |
| `MongoWorkItemScheduleDocument` | `work_item_schedules` | OCC version field |
| `MongoWorkItemTemplateDocument` | `work_item_templates` | |
| `MongoRoutingCursorDocument` | `routing_cursors` | compound `_id` (poolHash:tenancyId) |
| `MongoWorkItemNoteDocument` | `work_item_notes` | |
| `MongoWorkItemRelationDocument` | `work_item_relations` | |
| `MongoWorkItemLinkDocument` | `work_item_links` | |
| `MongoWorkItemSpawnGroupDocument` | `work_item_spawn_groups` | OCC version field |
| `MongoIssueLinkDocument` | `issue_links` | |
| `MongoLabelDefinitionDocument` | `label_definitions` | |
| `MongoLabelRuleDocument` | `label_rules` | |
| `MongoLabelVocabularyDocument` | `label_vocabularies` | atomic upsert |

### Codec Configuration (D37)

A shared utility configures `PojoCodecProvider` on the `MongoDatabase`:

```java
public final class MongoDatabaseFactory {
    public static MongoDatabase withPojoCodecs(MongoDatabase database) {
        CodecRegistry pojoCodec = fromProviders(
            PojoCodecProvider.builder().automatic(true).build());
        CodecRegistry registry = fromRegistries(
            database.getCodecRegistry(), pojoCodec);
        return database.withCodecRegistry(registry);
    }
}
```

Both framework wrappers call `MongoDatabaseFactory.withPojoCodecs(rawDatabase)`
before passing the database to core store constructors.

### Store POJOs (16)

Each store is a plain Java class implementing its SPI interface. Constructor
injection of `MongoDatabase` and (for tenant-scoped stores) `CurrentPrincipal`.

**Tenant-scoped stores (13):**

```java
public class MongoWorkItemStoreCore implements WorkItemStore {
    private final MongoCollection<MongoWorkItemDocument> collection;
    private final CurrentPrincipal principal;

    public MongoWorkItemStoreCore(MongoDatabase database, CurrentPrincipal principal) {
        this.collection = database.getCollection("work_items", MongoWorkItemDocument.class);
        this.principal = principal;
    }

    // All existing query logic migrates here unchanged —
    // replace Panache static methods with collection.find/insertOne/replaceOne
}
```

**Cross-tenant stores (3):**

```java
public class MongoCrossTenantWorkItemStoreCore implements CrossTenantWorkItemStore {
    private final MongoCollection<MongoWorkItemDocument> collection;

    public MongoCrossTenantWorkItemStoreCore(MongoDatabase database) {
        this.collection = database.getCollection("work_items", MongoWorkItemDocument.class);
    }
}
```

No `CurrentPrincipal` — cross-tenant stores run without tenant filtering.

### Panache → MongoCollection Migration Patterns

| Panache Pattern | MongoCollection Equivalent |
|----------------|--------------------------|
| `DocType.find(filter).list()` | `collection.find(filter).into(new ArrayList<>())` |
| `DocType.find(filter).firstResult()` | `collection.find(filter).first()` |
| `DocType.count(filter)` | `collection.countDocuments(filter)` |
| `doc.persist()` | `collection.insertOne(doc)` |
| `doc.persistOrUpdate()` | `collection.replaceOne(eq("_id", id), doc, new ReplaceOptions().upsert(true))` |
| `DocType.delete(filter)` | `collection.deleteOne(filter)` |
| `DocType.mongoCollection()` | `collection` (already a field) |

### OCC (Optimistic Concurrency Control)

3 stores use OCC — `MongoWorkItemStoreCore`, `MongoWorkItemScheduleStoreCore`,
`MongoWorkItemSpawnGroupStoreCore`.

Pattern: include `version` in the query filter, increment on update. If
`replaceOne` or `findOneAndUpdate` returns `modifiedCount == 0`, throw
`OptimisticLockException`. No change from current logic — just uses
`collection.replaceOne()` directly instead of Panache.

### Atomic Operations

2 stores use atomic MongoDB operations:

- `MongoRoutingCursorStoreCore.acquireNext()` — `findOneAndUpdate` with `$inc`
  on cursor position + upsert for first-touch creation
- `MongoLabelVocabularyStoreCore.findOrCreate()` — `findOneAndUpdate` with
  `$setOnInsert` + upsert for atomic insert-if-absent

These translate directly — `collection.findOneAndUpdate()` is the same API
Panache delegates to under the hood.

### IndexInitializer (D38)

```java
public class MongoIndexInitializer {
    private final MongoDatabase database;

    public MongoIndexInitializer(MongoDatabase database) {
        this.database = database;
    }

    public void init() {
        createIndex("work_item_templates", ascending("name", "tenancyId"), unique);
        createIndex("work_item_spawn_groups", ascending("parentId", "idempotencyKey"), unique);
        createIndex("work_item_relations", ascending("sourceId", "targetId", "relationType"), unique);
        createIndex("issue_links", ascending("workItemId", "trackerType", "externalRef"), unique);
        createIndex("label_vocabularies", ascending("scope", "tenancyId"), unique);
    }
}
```

### TenancyMigration

The existing `MongoTenancyMigration` (one-time data migration adding tenancyId
to legacy documents) moves to core. It's pure MongoDB operations — takes
`MongoDatabase`, iterates collections, updates documents lacking `tenancyId`.

## persistence-mongodb (Quarkus — Refactored)

Becomes a thin CDI producer module. One class:

```java
@ApplicationScoped
public class PersistenceMongoProducers {

    @Produces @ApplicationScoped @Alternative @Priority(1)
    public WorkItemStore workItemStore(MongoDatabase database, CurrentPrincipal principal) {
        return new MongoWorkItemStoreCore(database, principal);
    }

    @Produces @ApplicationScoped @Alternative @Priority(1)
    public CrossTenantWorkItemStore crossTenantWorkItemStore(MongoDatabase database) {
        return new MongoCrossTenantWorkItemStoreCore(database);
    }

    // ... 14 more @Produces methods, one per store SPI
}
```

A second producer resolves `MongoDatabase` from Quarkus's auto-configured
`MongoClient`:

```java
@ApplicationScoped
public class MongoDatabaseProducer {

    @Produces @ApplicationScoped
    public MongoDatabase mongoDatabase(MongoClient client,
            @ConfigProperty(name = "quarkus.mongodb.database") String dbName) {
        return MongoDatabaseFactory.withPojoCodecs(client.getDatabase(dbName));
    }
}
```

Startup observer triggers index initialization:

```java
@ApplicationScoped
public class MongoStartup {
    @Inject MongoDatabase database;

    void onStartup(@Observes StartupEvent event) {
        new MongoIndexInitializer(database).init();
    }
}
```

**Dependency changes:** Remove `quarkus-mongodb-panache`. Add
`quarkus-mongodb-client` (provides `MongoClient` CDI bean without Panache).
Add `persistence-mongodb-core`.

## persistence-spring-mongodb (Spring — New)

Single auto-configuration class:

```java
@AutoConfiguration
@ConditionalOnClass(MongoClient.class)
public class WorkPersistenceMongoAutoConfiguration {

    @Bean
    public MongoDatabase workMongoDatabase(MongoClient client,
            @Value("${spring.data.mongodb.database}") String dbName) {
        return MongoDatabaseFactory.withPojoCodecs(client.getDatabase(dbName));
    }

    @Bean @ConditionalOnMissingBean(WorkItemStore.class)
    public WorkItemStore workItemStore(MongoDatabase database, CurrentPrincipal principal) {
        return new MongoWorkItemStoreCore(database, principal);
    }

    @Bean @ConditionalOnMissingBean(CrossTenantWorkItemStore.class)
    public CrossTenantWorkItemStore crossTenantWorkItemStore(MongoDatabase database) {
        return new MongoCrossTenantWorkItemStoreCore(database);
    }

    // ... 14 more @Bean methods

    @Bean
    public MongoIndexInitializer mongoIndexInitializer(MongoDatabase database) {
        return new MongoIndexInitializer(database);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initIndexes(ApplicationReadyEvent event) {
        event.getApplicationContext().getBean(MongoIndexInitializer.class).init();
    }
}
```

Registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

`@ConditionalOnMissingBean` on each store bean — consumers can override
individual stores if needed. Consistent with platform's Spring displacement
pattern (D31).

## Testing

### persistence-mongodb-core

Unit tests with embedded MongoDB (de.flapdoodle.embed.mongo) or Testcontainers.
Each store test instantiates the core POJO directly with a real `MongoDatabase`:

```java
@Testcontainers
class MongoWorkItemStoreCoreTest {
    @Container static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    MongoWorkItemStoreCore store;

    @BeforeEach
    void setUp() {
        MongoClient client = MongoClients.create(mongo.getConnectionString());
        MongoDatabase db = MongoDatabaseFactory.withPojoCodecs(
            client.getDatabase("test"));
        store = new MongoWorkItemStoreCore(db, new FixedCurrentPrincipal("tenant1", "actor1"));
    }
}
```

Tests cover: CRUD operations, tenant isolation (verify tenant filter applied),
OCC conflict detection, atomic operations (concurrent acquireNext), query
builder (WorkItemQuery filter combinations), index uniqueness constraints.

### persistence-mongodb (Quarkus)

Existing `@QuarkusTest` integration tests stay — they exercise the full CDI
stack. Verify that refactored producers wire correctly and all existing tests
pass unchanged.

### persistence-spring-mongodb

Displacement test verifying auto-configuration produces the correct bean types:

```java
@SpringBootTest
class WorkPersistenceMongoDisplacementTest {
    @Autowired WorkItemStore workItemStore;

    @Test
    void autoConfiguredStoreIsMongoCore() {
        assertThat(workItemStore).isInstanceOf(MongoWorkItemStoreCore.class);
    }
}
```

## Consumer Guide Update

Add a Spring Data MongoDB section to `docs/guides/consumer-guide.md`:

```yaml
# application.yml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/casehub-work
      database: casehub-work
```

```xml
<dependency>
  <groupId>io.casehub</groupId>
  <artifactId>casehub-work-persistence-spring-mongodb</artifactId>
</dependency>
```

## Scope Summary

| Item | Count |
|------|-------|
| New modules | 2 (`persistence-mongodb-core`, `persistence-spring-mongodb`) |
| Refactored modules | 1 (`persistence-mongodb`) |
| Store POJOs to migrate | 16 |
| Document POJOs to migrate | 13 |
| Index initializer | 1 |
| Quarkus dep change | Remove `quarkus-mongodb-panache`, add `quarkus-mongodb-client` |
| Spring auto-config class | 1 |
| Test classes | ~3 (core unit tests, Quarkus integration verification, Spring displacement) |

## References

- D34-D38 — decisions for this issue (decisions.md)
- D27-D28 — JPA common extraction precedent
- D19-D26 — streams core extraction precedent
- D30 — RlsPolicySetup extraction pattern (index initialization parallel)
- persistence-mongodb source (16 stores, 13 documents, MongoIndexInitializer)
- work-api, runtime, core, issue-tracker — SPI interface definitions
- casehubio/parent#498 — issue
- casehubio/parent#501 — parent epic
