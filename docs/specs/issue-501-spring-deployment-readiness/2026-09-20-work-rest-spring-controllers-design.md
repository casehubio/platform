# Design: Work Spring REST Controllers (#494)

Generate 28 Spring MVC REST controllers for the casehub-work repo by extracting
core POJOs from 25 hand-written JAX-RS @Path resources (plus 3 test resources)
and wiring the rest-spring-generator plugin.

## Context

casehub-work has 25 @Path resources across 9 modules (rest, ai, federation,
issue-tracker, ledger, progress-rest, queues, reports, runtime) with no Spring
MVC equivalents. The rest-spring-generator Maven plugin scans @Path resources
via Jandex and generates @RestController classes that delegate to a core POJO.
The generator requires each resource to inject a single core POJO with matching
method names and typed return values.

Currently, no resource follows this pattern. All 25 use field injection with
1-6 CDI dependencies, 68% of methods return `Response` (not typed), and 34
methods use `@Transactional`. Core extraction is the prerequisite.

## Architecture

### Core Extraction Pattern

Each @Path resource is split into two classes in the same module:

1. **Core POJO** (`.core` subpackage) — framework-neutral, constructor-injected,
   typed return values, `@jakarta.transaction.Transactional` where needed. Owns
   all business logic (validation, orchestration, error handling).

2. **JAX-RS resource** (existing class, refactored) — thin adapter. One
   `@Inject` field pointing to the core POJO. Each REST method is a one-liner
   delegating to the core.

```
rest/src/main/java/io/casehub/work/rest/
    WorkItemBulkResource.java          // thin @Path adapter
    core/
        WorkItemBulkCore.java          // business logic POJO
        BulkRequest.java               // extracted inner record
        BulkItemResult.java            // extracted inner record
```

### CDI Wiring

Each module with extracted core POJOs gets a `@Produces @ApplicationScoped`
producer method (or a producer class if multiple POJOs). The spring-generator
picks up these `@Produces` methods and generates corresponding `@Bean` methods
in work-spring automatically.

### Generator Pipeline

```
work modules (9)        rest-spring-generator        work-spring
  @Path resources   -->   scan Jandex indexes    -->   generated
  + core POJOs            find delegate types          @RestController
                          generate controllers         classes
```

The rest-spring-generator is configured in work-spring's pom.xml with
`<quarkusModules>` pointing to all 9 work modules containing @Path resources.
The spring-generator (already configured) handles the core POJO `@Bean` wiring.

## Core POJO Contract

Each core POJO follows these rules:

| Aspect | Rule |
|--------|------|
| **Constructor** | All dependencies via constructor params |
| **Return types** | Typed values (`List<T>`, `Optional<T>`, `T`, `void`) — never `Response` |
| **Transactions** | `@jakarta.transaction.Transactional` on methods (not on class) |
| **Inner types** | Extracted to top-level classes in `.core` subpackage |
| **Package** | `<resource-package>.core` (e.g., `io.casehub.work.rest.core`) |
| **Naming** | `<ResourceName>` with `Resource` → `Core` (e.g., `WorkItemBulkCore`) |
| **Framework deps** | Zero CDI, zero Spring, zero JAX-RS imports |

### SSE Endpoints (2 methods)

`ProgressResource.streamEvents()` and `QueueResource.streamQueueEvents()`
currently return `Multi<T>`. After extraction, core POJOs return
`java.util.concurrent.Flow.Publisher<T>` — the JDK 9+ reactive streams
standard.

- **Quarkus resource:** `Multi.createFrom().publisher(core.streamEvents())`
- **Spring controller:** Generated `SseEmitter`-based method that subscribes
  to the publisher and sends events.

### Excluded Resources

| Resource | Reason |
|----------|--------|
| `AsyncApiResource` | 0 dependencies, reads classpath resource directly. Hand-write in work-spring if needed (5 lines). |

## Generator Enhancements (platform)

### 1. Flow.Publisher → SseEmitter translation

Add to `RestControllerWriter`:
- Detect `Flow.Publisher<T>` return type in `wrapReturnType()`
- Generate `SseEmitter` return type
- In `buildMethodBody()`, generate: create SseEmitter, subscribe to publisher
  on virtual thread, send events, complete on terminal signal

### 2. Null delegate guard in scanner

`RestResourceScanner` currently produces descriptors with null
`delegateTypeName` for resources with 0 `@Inject` fields. Add a skip
(with log warning) to avoid NPE in the writer. Matches the existing
skip patterns (interfaces, @RegisterRestClient, generated packages).

## Resource Inventory

### THIN (5) — trivial extraction

| Resource | Module | Injects | Methods | Notes |
|----------|--------|---------|---------|-------|
| EscalationSummaryResource | ai | 1 | 1 | Single store, typed return |
| ReportResource | reports | 1 | 4 | Single service, all typed returns |
| AuditResource | rest | 1 | 1 | Single store, map return |
| SlaAdminResource | runtime | 1 | 1 | Single loader, Response return |
| AsyncApiResource | rest | 0 | 1 | **Excluded** — no delegate |

### MODERATE (11) — some inline logic, 1-2 injects

| Resource | Module | Injects | Methods | Key extraction |
|----------|--------|---------|---------|----------------|
| WorkerSkillProfileResource | ai | 1 | 4 | 2 Response → typed |
| ResolutionSuggestionResource | ai | 2 | 1 | Merge 2 deps into core ctor |
| FederationSubscriptionResource | federation | 1 | 3 | 3 Response, 2 inner records |
| IssueLinkResource | issue-tracker | 1 | 5 | 4 Response, 2 inner records |
| ActorTrustResource | ledger | 2 | 1 | Response → typed |
| SpawnGroupResource | rest | 2 | 1 | Response → typed |
| VocabularyResource | rest | 1 | 2 | 1 inner record |
| WorkItemBulkResource | rest | 1 | 1 | 2 inner records, Response → typed |
| WorkItemInstancesResource | rest | 2 | 1 | 1 inner record |
| WorkItemScheduleResource | rest | 2 | 5 | 2 inner records |
| WorkItemSpawnResource | rest | 2 | 3 | 2 inner records |

### COMPLEX (9) — significant logic, 3+ injects

| Resource | Module | Injects | Methods | Key extraction |
|----------|--------|---------|---------|----------------|
| FederationEventResource | federation | 1 | 1 | Panache static call |
| GitHubWebhookResource | issue-tracker | 3 | 1 | Config + handler + tenant |
| JiraWebhookResource | issue-tracker | 3 | 1 | Config + handler + tenant |
| LedgerResource | ledger | 4 | 3 | 4 deps, @Transactional |
| ProgressResource | progress-rest | 5 | 17 | 5 deps, SSE endpoint |
| QueueResource | queues | 6 | 9 | 6 deps, SSE endpoint |
| QueueStateResource | queues | 4 | 2 | 4 deps |
| LabelRuleResource | rest | 5 | 5 | 5 deps, CDI Instance<> |
| WorkItemTemplateResource | rest | 2 | 7 | 3 inner records |

## Module Configuration

### work-spring pom.xml additions

```xml
<plugin>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-platform-rest-spring-generator</artifactId>
    <version>${version.io.casehub}</version>
    <executions>
        <execution>
            <id>generate</id>
            <goals><goal>generate</goal></goals>
            <configuration>
                <quarkusModules>
                    <quarkusModule>${project.basedir}/../rest</quarkusModule>
                    <quarkusModule>${project.basedir}/../ai</quarkusModule>
                    <quarkusModule>${project.basedir}/../federation</quarkusModule>
                    <quarkusModule>${project.basedir}/../issue-tracker</quarkusModule>
                    <quarkusModule>${project.basedir}/../ledger</quarkusModule>
                    <quarkusModule>${project.basedir}/../progress-rest</quarkusModule>
                    <quarkusModule>${project.basedir}/../queues</quarkusModule>
                    <quarkusModule>${project.basedir}/../reports</quarkusModule>
                    <quarkusModule>${project.basedir}/../runtime</quarkusModule>
                </quarkusModules>
            </configuration>
        </execution>
        <execution>
            <id>verify-drift</id>
            <goals><goal>verify</goal></goals>
            <phase>verify</phase>
            <configuration>
                <!-- same quarkusModules -->
            </configuration>
        </execution>
    </executions>
</plugin>
```

### Jandex indexes

Each of the 9 work modules needs `jandex-maven-plugin` configured to produce
`target/classes/META-INF/jandex.idx`. Modules that already have Quarkus
extension deployment (`deployment/`) likely have Jandex. Others need it added.

### Dependencies

work-spring needs compile dependencies on all core POJO types' transitive
dependencies. Most are already present via `casehub-work-runtime-core` and
`casehub-work-support-core`. Additional deps for modules not currently in
work-spring's dependency tree (federation, issue-tracker, ledger, queues,
progress-rest, reports) need to be assessed — some may need their own
`-spring` auto-configuration modules.

## Testing Strategy

1. **Unit tests for core POJOs** — each extracted POJO gets tests verifying
   business logic with mocked dependencies. Tests migrate from resource-level
   tests where they exist.
2. **Generator output verification** — `mvn install` on work-spring produces
   generated controllers; compilation verifies type correctness.
3. **Drift detection** — rest-spring-generator's `verify` goal catches drift
   between resources and generated controllers at build time.

## References

- `rest-spring-generator/src/test/` — SampleResource/SampleCore delegation pattern
- `RestControllerWriter.java` — generation logic (ResponseEntity wrapping, parameter mapping)
- `RestResourceScanner.java` — Jandex scanning (delegate detection, method extraction)
- `AbstractGeneratorMojo.java` — multi-module Jandex loading via `<quarkusModules>`
- D5 (constructor-scanning strategy), D7 (config binding), D39-D42 (this issue's decisions)
- Resource audit (25 resources, 81 methods, 19 inner records, 34 @Transactional)
