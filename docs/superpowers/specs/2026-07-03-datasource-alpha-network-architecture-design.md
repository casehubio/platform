# DataSource + Alpha Network Architecture

**Date:** 2026-07-03
**Status:** Draft — architectural spec with open questions, not implementation-ready
**Scope:** casehub-platform
**Issue:** TBD — to be created when spec is promoted from Draft

---

## 1. Problem

Events arrive at casehub through multiple streams modules (Kafka, AMQP, webhook, poll, Camel) and adapters (QhorusCloudEventAdapter). Today, all producers converge on `Event<CloudEvent>.fireAsync()` — a CDI async broadcast. The sole runtime consumer (`RasEngine.onCloudEvent`) receives every event and performs its own type discrimination and filtering. The engine does not yet consume external events.

This creates three problems:

1. **Redundant filtering** — each consumer independently checks event types and applies predicates. N consumers × M event types = N×M checks per event.
2. **No shared evaluation** — identical type checks across consumers are evaluated independently. No structural sharing.
3. **No declarative routing** — consumers are wired by CDI observer convention, not by a registry of named, typed, filtered subscriptions.

## 2. Concept: DataSource as Boundary

A **DataSource** is a boundary concept from the Sink/Source model. It defines where data enters a system — the edge between outside and inside.

- A **Source** is where data enters your system
- A **Sink** is where data exits your system
- An **EndPoint** is the infrastructure descriptor (WHERE/HOW to connect — URL, protocol, credentials, topic)
- A **DataSource** is the logical boundary (WHERE data enters — named, typed, scoped, registered)

An EndPoint describes the pipe. A DataSource is one end of the pipe — the receiving end. You turn a raw EndPoint into a DataSource to make it available for binding to a target system.

### Relationship to Engine Binding

The engine's `Binding` (in `casehub-engine`) connects a trigger to a target (capability, subCase, humanTask):

```java
Binding.builder()
    .name("siem-handler")
    .on(trigger)           // Trigger — when to fire
    .when(guard)           // ExpressionEvaluator — under what conditions
    .capability(analysis)  // BindingTarget — what to activate
    .build()
```

Today `Trigger` has two implementations: `ContextChangeTrigger` (blackboard changes) and `ScheduleTrigger` (cron/delay). A DataSource-backed Trigger would be the third — enabling the engine to react to external events arriving through the streams pipeline.

`CaseDefinitionYamlMapper` (casehub-engine, line 589) already has a TODO: `"CloudEventTrigger and ScheduleTrigger conversion not yet implemented"`. The new Trigger type will be named `DataSourceTrigger` (not `CloudEventTrigger`) — it references a DataSource, which is the platform concept; CloudEvent is one possible payload type, not the only one. YAML mapper support for `DataSourceTrigger` is out of scope for this spec but tracked as a follow-up.

The layering:

- **Platform** owns the DataSource — type discrimination + filter chain + alpha network routing
- **Engine** owns the Binding — connects a DataSource (via a new `Trigger` impl) to a case capability/worker/humanTask, adding engine-specific guards (`when`), outcome policies, etc.

"A Binding binds a DataSource to a case."

### Scoping

A DataSource is scope-independent. The same type+filter model works at every level — what changes is the boundary it's attached to:

- **Runtime/application scope** — attached to an EndPoint. Events from Kafka, webhooks, AMQP flow through the alpha network. Shared across all cases.
- **Case scope** — attached to a running case's internal state. Fires when objects within the case change.

The DataSource SPI itself doesn't know about scope. The attachment point determines what feeds it.

## 3. Architecture: Alpha Network

The alpha network is a Rete-style optimisation for single-object type discrimination and filtering. It eliminates redundant evaluation when multiple subscribers share type or filter criteria.

### Pipeline

```
EndPoint → [Marshaller (optional)] → DataSource → alpha network → subscribers
```

The marshaller is an optional, configurable transform stage between the endpoint and the DataSource. It converts the raw event format (e.g. CloudEvent) into a typed POJO. Without a marshaller, the DataSource receives raw events.

Two paths:

| Path | Marshaller | DataSource type | Filter language | Speed |
|------|-----------|----------------|-----------------|-------|
| Raw | none | `DataSource<CloudEvent>` | jq (attribute expressions) | Fast — no deserialisation |
| Typed | CloudEvent → POJO | `DataSource<SiemAlert>` | MVEL3 (compiled Java) | Fastest — native bytecode on typed fields |

### CDI Bridge: DataSourceRouter

The existing streams modules (Kafka, AMQP, webhook, poll, Camel) and adapters (QhorusCloudEventAdapter) all fire `Event<CloudEvent>.fireAsync()` on the CDI bus. The **DataSourceRouter** bridges CDI events into the DataSource alpha network.

`DataSourceRouter` — `@ApplicationScoped` CDI observer in `platform/`:

1. `@ObservesAsync CloudEvent` — catches all CDI CloudEvents (coexists with existing observers)
2. Extracts `tenancyid` extension from the CloudEvent
3. Routes to all runtime-scope DataSources matching the tenant (see routing logic)
4. For each matching DataSource, applies the configured marshaller (if any)
5. Pushes the (raw or marshalled) event into the DataSource's alpha network
6. The alpha network does type discrimination and fan-out to subscribers

**Routing logic:** The router does **tenancy-based routing only** — it does not match CloudEvent source URIs to EndPoint paths. CloudEvent source URIs (`/platform/streams/kafka/siem-events`) and EndPoint paths (`streams/siem-kafka`) are in different namespaces with no reliable mapping (especially for webhooks where the source is preserved from the external sender). Instead:

1. Extract `tenancyid` from the CloudEvent extension
2. Route to all runtime-scope DataSources where `descriptor.tenancyId` matches the CloudEvent's tenancyId
3. Also route to all platform-global DataSources (`descriptor.tenancyId == PLATFORM_TENANT_ID`)
4. **Pre-filter:** for each candidate DataSource, check `descriptor.acceptedEventTypes()`. If non-empty and the CloudEvent's `type` is not in the set, skip silently (no log — the event was never a candidate)
5. Apply the configured marshaller (if any) — `MarshalException` drops the event with a WARN log
6. Call `dataSource.add(event)` — the alpha network (ObjectType nodes, filter chain) does the actual discrimination

The `endpointPath` field on `DataSourceDescriptor` is metadata/documentation (which EndPoint feeds this DataSource), not a routing criterion. The alpha network IS the routing mechanism — this is the Rete approach.

**Startup initialization:** The router follows the same pattern as `CamelStreamProcessor` (2026-06-14 spec) to handle DataSources registered at `@PostConstruct` time:

```java
void onStartup(@Observes StartupEvent ev) {
    registry.discover(allRuntimeQuery).forEach(d -> wireRoute(d));
    started.set(true);
}

void onDataSourceRegistered(@ObservesAsync DataSourceRegistered event) {
    if (!started.get()) return; // pre-startup registrations covered by onStartup
    wireRoute(event.descriptor()); // idempotent
}
```

`@Startup @ApplicationScoped` beans complete `@PostConstruct` before `StartupEvent` fires, so `discover()` at startup sees the complete pre-startup registry state. Late-delivered pre-startup events are discarded — the startup handler already covered them.

**Coexistence:** The DataSourceRouter is additive — existing `@ObservesAsync CloudEvent` observers (e.g. `RasEngine.onCloudEvent`) continue to receive events directly from CDI. Consumers migrate incrementally by subscribing to DataSources instead of observing CDI events. The CDI observer can be removed when all consumers have migrated.

**Module placement:** The router lives in `platform/`, not in a backend module. It injects `DataSourceRegistry` (SPI from `platform-api/`) and works with whatever backend is active. When the `NoOpDataSourceRegistry` is active, discover() returns nothing and the router does nothing. This ensures the router survives backend replacement (e.g. `datasource-inmem/` → `datasource-drools/`).

### Alpha network internals

```
Event arrives (raw CloudEvent or typed POJO)
  → Type node (O(1) lookup by ObjectType.getTypeKey())
    → Filter chain (0..N expression evaluations)
      → Fan-out to 1..N subscribers (DataProcessor.add())
```

### Subscription model

A consumer subscribes to a DataSource with type and filter criteria. The alpha network is internal — the consumer doesn't see the network structure.

**Raw path** (DataSource receives CloudEvents, filter on CloudEvent attributes):

```java
DataSource<?> source = registry.resolveSource(Path.parse("siem-events"), tenancyId).orElseThrow();
SubscriptionHandle handle = source.subscribe(
    new CloudEventObjectType("io.casehub.siem.alert"),
    myCloudEventProcessor);
// later: handle.unsubscribe();
```

**Typed path** (DataSource receives marshalled POJOs, filter on POJO fields):

```java
DataSource<?> source = registry.resolveSource(Path.parse("siem-alerts-typed"), tenancyId).orElseThrow();
SubscriptionHandle handle = source.subscribe(
    new ClassObjectType<>(SiemAlert.class),
    alert -> alert.severity() >= HIGH,
    myAlertProcessor);
```

The raw path uses `CloudEventObjectType` to match on CloudEvent `type` attribute strings. The typed path uses `ClassObjectType` on a DataSource fed by a marshaller that has already converted CloudEvent → SiemAlert. `ClassObjectType<SiemAlert>.matches(siemAlert)` is `true` because the marshaller has already done the conversion — the DataSource contains SiemAlerts, not CloudEvents.

The subscription returns a `SubscriptionHandle` for lifecycle management and unsubscription. The alpha network creates or reuses type nodes and filter nodes as needed.

### Sharing model

- **Type nodes** — always shared. `ObjectType.getTypeKey()` returns a comparable key. When a new subscription arrives for the same type key, it attaches to the existing type node. Hash lookup.
- **Expression-based filter nodes** — shareable via string comparison when subscriptions use `FilterExpression` (see §6). Two `FilterExpression` instances with the same `type()` and `expression()` share a filter node. String equality is a pragmatic approximation — it catches the common case (same expression copied across subscriptions) but does not guarantee optimal node sharing for semantically equivalent but syntactically different expressions (e.g. `a > b` vs `a>b`).
- **Predicate filter nodes** — not shared. Plain `Predicate<T>` instances (Java lambdas) have identity-based equality. Each lambda subscription gets its own filter node. The alpha network distinguishes `FilterExpression` from plain `Predicate` via `instanceof` — `FilterExpression` enables sharing, plain `Predicate` does not.

**Future: compile-time sharing for lambdas.** APT + JavaParser at compile time can normalize lambda expressions, centralise them, and replace inline definitions with references — enabling structural equality. Or Drools vol2 drops in with its own Rete network builder. The SPI does not expose sharing; it is an implementation concern.

### ObjectType key contract

`ObjectType.getTypeKey()` returns `Object` intentionally — different ObjectType implementations use semantically different key types (`Class<?>` for `ClassObjectType`, `String` for `CloudEventObjectType`). The contract:

- Keys are used for HashMap lookup via `equals()`/`hashCode()` — implementations must ensure their keys implement these correctly
- Keys from different ObjectType implementations are naturally disjoint by Java type (`Class` and `String` never falsely compare equal)
- Two ObjectType instances with the same `getTypeKey()` share a type node in the alpha network
- Keys must be immutable and safe for use as HashMap keys

### Error propagation model

Synchronous propagation through the filter chain, with isolated per-subscriber error handling:

- Each `DataProcessor.add()` invocation is wrapped in its own try/catch
- One failing subscriber does **not** prevent delivery to other subscribers in the fan-out
- Subscriber exceptions are logged (WARN level) but **not** propagated to the event source
- The alpha network continues processing the remaining subscribers after a failure
- No retry — failed delivery is final for that event

This matches Drools vol2's approach and preserves the fault isolation that CDI `fireAsync()` provides (each observer completes independently). The RasEngine's existing per-registration try/catch pattern becomes the alpha network's responsibility — subscribers no longer need their own error wrapping.

### Tenancy isolation

Tenancy isolation operates at the DataSource boundary, not inside the alpha network:

- Each DataSource instance is registered with a `(path, tenancyId)` — this is the unique key
- The alpha network is per-DataSource instance — there is no shared cross-tenant network
- Tenant A's events never enter Tenant B's DataSource
- The DataSourceRouter handles tenant routing: it extracts `tenancyid` from the CloudEvent and routes to the matching DataSource(s)
- Platform-global DataSources (registered with `TenancyConstants.PLATFORM_TENANT_ID`) receive events from all tenants — the router pushes to both tenant-specific and platform-global DataSources

This is simpler and more secure than a shared network with tenant-aware filtering — data never crosses the tenant boundary inside the network.

## 4. Pluggable Filter Expressions

Filter predicates in the alpha network are pluggable — the same pattern as the engine's `ExpressionEvaluator` interface (`type()` discriminator, multiple implementations).

### Available filter languages

| Type | Implementation | Input | Sharing | Speed | Serialisable |
|------|---------------|-------|---------|-------|-------------|
| `"jq"` | jackson-jq / jjq | `CloudEvent` attributes, JSON data | Yes (string) | Microseconds | Yes |
| `"mvel"` | MVEL3 transpiler (mock until published) | Typed POJOs | Yes (string) | Nanoseconds (compiled bytecode) | Yes |
| `"lambda"` | Java `Predicate<T>` | Any type | No (identity-based) | Nanoseconds (native) | No |

### MVEL3 as the intended POJO filter language

MVEL3 is not an interpreter — it is a transpiler: MVEL expression → Java source (via JavaParser AST) → compiled bytecode → `Evaluator<C,W,O>` instance. Native JVM speed at runtime.

Pipeline:
```
MVEL expression string
  → Antlr4 parse → MVEL AST
  → MVELTranspiler → Java AST (JavaParser CompilationUnit)
  → KieMemoryCompiler → bytecode (hidden class, no classloader)
  → Evaluator instance (native speed, JIT-inlineable)
```

Benefits for DataSource filters:

1. **Native execution speed** — compiled to bytecode, JIT-inlineable
2. **Structural equality** — `LambdaCatalog` + `LambdaKey` provide content-based identity, enabling alpha network node sharing
3. **Persistence** — `LambdaPersistenceManager` persists compiled classes to disk; no recompilation on restart
4. **Type-safe** — compilation fails at registration time if the expression doesn't match the target type
5. **Drools alignment** — MVEL3 IS what Drools vol2 uses; zero impedance mismatch when Drools drops in
6. **No classloader concerns** — Java 15+ hidden classes (JEP 371) are GC-eligible without classloaders; Java 17+ elastic metaspace (JEP 387) returns memory to the OS

**Availability:** MVEL3 is not yet published to Maven Central. It depends on a forked JavaParser (`org.mvel.javaparser`) and `drools-compiler`. The initial casehub implementation ships a **mock MVEL3 evaluator** — satisfies the `ExpressionEvaluator` interface, accepts expression strings, but does not compile or evaluate them. **Mock behavior:** all filter evaluations return `true` (pass-through). This allows the full subscription and alpha network pipeline to be tested end-to-end — events flow through type nodes and filter nodes to subscribers — without MVEL3. The mock does not validate expression syntax. Swapped for the real implementation when MVEL3 is published. No SPI or consumer changes required.

### jq for raw CloudEvent filtering

When no marshaller is configured and events arrive as raw `CloudEvent`, jq operates on CloudEvent attributes and JSON data without deserialisation. Suitable for attribute-based routing (type, source, subject, extensions) where deserialisation cost is not justified.

### ExpressionEvaluator location

The engine's `ExpressionEvaluator` interface lives in `io.casehub.api.model.evaluator` (casehub-engine). It has a single method: `String type()` — a pure type discriminator with no engine-specific concerns.

**Resolution:** `ExpressionEvaluator` moves to `io.casehub.platform.api.evaluator` in `platform-api/`. The engine module takes a compile dependency on this new location (which it already has via `platform-api`). The existing engine implementations (`JQExpressionEvaluator`, `LambdaExpressionEvaluator`) remain in the engine and implement the platform-api interface. DataSource filter types implement the same interface — no parallel hierarchy needed.

The existing `io.casehub.platform.api.expression` package contains `ConfigManager` and `SecretManager` (JQ scope injection types) — `ExpressionEvaluator` goes in a new `evaluator` package, not the existing `expression` package.

## 5. Consumers

**Runtime-scope producers** (fire `Event<CloudEvent>.fireAsync()`):

| Producer | Module | Mechanism |
|----------|--------|-----------|
| Kafka | `streams-kafka` | `@Incoming` channel, builds CloudEvent from `byte[]` |
| AMQP | `streams-amqp` | `@Incoming` channel, builds CloudEvent from `byte[]` |
| Webhook | `streams-webhook` | JAX-RS POST, structured CloudEvents HTTP binding |
| Poll | `streams-poll` | `@Scheduled` HTTP GET, builds CloudEvent from response |
| Camel | `streams-camel` | Dynamic Camel route, observes `EndpointRegistered` |
| Qhorus | `casehub-qhorus` | `QhorusCloudEventAdapter` — observes `MessageReceivedEvent`, converts to CloudEvent and fires |

**Runtime-scope consumers:**

| Consumer | Today | With DataSource | Migration notes |
|----------|-------|-----------------|-----------------|
| **RAS** (`RasEngine`) | `@ObservesAsync CloudEvent`, `SituationDefinitionRegistry.findByEventType()` for type discrimination | Phase 1: subscribes to a DataSource with catch-all ObjectType; internal `findByEventType()` routing unchanged. Phase 2 (future): subscribes per event type; alpha network does discrimination | Phase 1 is additive — only the delivery mechanism changes. `RasEngine.onCloudEvent` is removed; DataSource subscription replaces it. `SituationDefinitionRegistry` is unaffected |
| **Engine** | No external event ingestion | New `DataSourceTrigger` impl references a DataSource; binding fires on match | Engine gains external event reactivity without direct CDI observer coupling |

At case scope, the engine is also the consumer — DataSources attached to case-internal state, wired through `Binding.on(Trigger)`.

## 6. SPI Types (platform-api/)

All types in `platform-api/` — zero-dependency, pure Java. Permanent contract; stays when Drools drops in.

### ObjectType

Pluggable type discriminator. Stripped down from Drools' version — no `Externalizable`, no `ValueType`. Generic type parameter `T` constrains the relationship between the type discriminator and its consumers.

```java
public interface ObjectType<T> {
    /** Returns true if the given object matches this type. */
    boolean matches(Object object);

    /**
     * Returns a key for indexed lookup (e.g. Class, String).
     * Keys are used as HashMap keys — must implement equals()/hashCode() correctly.
     * Keys from different ObjectType implementations are disjoint by Java type.
     */
    Object getTypeKey();
}
```

Initial implementations:

- `ClassObjectType<T>` — `matches()` via `instanceof`, `getTypeKey()` returns the `Class<T>`
- `CloudEventObjectType` implements `ObjectType<CloudEvent>` — matches on CloudEvent `type` attribute string, `getTypeKey()` returns the type string

### DataProcessor

Subscriber contract. Add-only — continuous stream, no update/remove.

```java
public interface DataProcessor<T> {
    void add(T object);
}
```

### SubscriptionHandle

Returned by all `subscribe()` overloads. Provides lifecycle management and unsubscription.

```java
public interface SubscriptionHandle {
    /** Removes this subscription from the alpha network. Idempotent. */
    void unsubscribe();

    /** Returns true if the subscription is still active. */
    boolean isActive();
}
```

### FilterExpression

A `Predicate` that carries its source expression string, enabling the alpha network to share filter nodes. Implements `Predicate<T>` so it works transparently with the existing subscribe API — the alpha network internally checks `filter instanceof FilterExpression` to decide whether to share.

```java
public record FilterExpression<T>(
    String type,
    String expression,
    Predicate<T> predicate
) implements Predicate<T> {

    public FilterExpression {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(expression, "expression");
        Objects.requireNonNull(predicate, "predicate");
    }

    @Override
    public boolean test(T t) {
        return predicate.test(t);
    }
}
```

- `type` — the expression language (`"jq"`, `"mvel"`, etc.), matching `ExpressionEvaluator.type()`
- `expression` — the source expression string (e.g. `".type == \"siem.alert\""`)
- `predicate` — the compiled evaluation function

Two `FilterExpression` instances with the same `type()` and `expression()` share a filter node in the alpha network. Plain `Predicate` instances (lambdas) never share — they lack the expression metadata.

### DataSource

The named, typed event source boundary — both the entry point for producers and the subscription point for consumers. Extends `DataProcessor<T>` to provide the `add(T)` ingestion method.

```java
public interface DataSource<T> extends DataProcessor<T> {
    // --- Ingestion (inherited from DataProcessor) ---
    // void add(T object);

    // --- Subscription ---

    /** Subscribe to all events from this source. */
    SubscriptionHandle subscribe(DataProcessor<? super T> processor);

    /** Subscribe with type discrimination — only events matching objectType are delivered. */
    <U> SubscriptionHandle subscribe(ObjectType<U> objectType, DataProcessor<? super U> processor);

    /** Subscribe with type discrimination and predicate filter (or FilterExpression for sharing). */
    <U> SubscriptionHandle subscribe(ObjectType<U> objectType, Predicate<U> filter, DataProcessor<? super U> processor);

    /** Convenience: subscribe with Class-based type discrimination and predicate filter. */
    <U> SubscriptionHandle subscribe(Class<U> type, Predicate<U> filter, DataProcessor<? super U> processor);
}
```

`DataSource extends DataProcessor` is architecturally correct: a DataSource IS a data entry point, and `DataProcessor.add(T)` is precisely the entry-point contract. The DataSourceRouter in `platform/` calls `dataSource.add(event)` to push CloudEvents into the alpha network. Consumers subscribe via `subscribe()`. One interface, two roles — which is exactly what a boundary concept IS. This also aligns with Drools where `DataStore` has an `add()` method.

The generic parameter `U` on `ObjectType<U>` constrains the processor type — `subscribe(cloudEventObjectType, cloudEventProcessor)` compiles, but `subscribe(cloudEventObjectType, siemAlertProcessor)` does not. The `Class<U>` convenience overload creates a `ClassObjectType<U>` internally.

The `Predicate<U> filter` parameter accepts both plain lambdas (not shareable) and `FilterExpression<U>` (shareable via expression string comparison). No separate overload needed — the alpha network does `instanceof FilterExpression` internally.

The exact subscribe overloads are indicative — the precise API shape will be refined during implementation.

### DataSourceDescriptor

Named declaration paralleling `EndpointDescriptor`. The unique key is `(path, tenancyId)`.

```java
public record DataSourceDescriptor(
    Path path,
    String tenancyId,
    ObjectType<?> objectType,
    Path endpointPath,
    Set<String> acceptedEventTypes,
    Map<String, String> properties
) {
    public DataSourceDescriptor {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(tenancyId, "tenancyId");
        Objects.requireNonNull(objectType, "objectType");
        Objects.requireNonNull(acceptedEventTypes, "acceptedEventTypes");
        Objects.requireNonNull(properties, "properties");
        acceptedEventTypes = Set.copyOf(acceptedEventTypes);
        properties = Map.copyOf(properties);
    }

    public boolean isPlatformGlobal() {
        return TenancyConstants.PLATFORM_TENANT_ID.equals(tenancyId);
    }
}
```

- `path` — hierarchical name using platform `Path` (e.g. `siem/high-severity`). Enables prefix-based discovery and hierarchical namespacing consistent with `EndpointDescriptor`
- `tenancyId` — tenant scope, following `EndpointDescriptor` conventions
- `objectType` — the type discriminator for the alpha network's root type node
- `endpointPath` — nullable; references the `EndpointDescriptor.path` that feeds this DataSource. Null for case-scoped DataSources (which have no EndPoint)
- `acceptedEventTypes` — CloudEvent `type` pre-filter for the DataSourceRouter. If non-empty, the router only pushes CloudEvents whose `type` attribute is in this set. Unmatched events are silently skipped — they were never candidates, so no log. If empty, the DataSource accepts all CloudEvents (broadcast). This prevents the marshalling-failure flood that tenancy-broadcast routing would otherwise produce (see §3 CDI Bridge)
- `properties` — non-secret configuration, same pattern as `EndpointDescriptor.properties()`

### DataSourceQuery

Criteria for `DataSourceRegistry.discover()`, paralleling `EndpointQuery`.

```java
public record DataSourceQuery(
    String tenancyId,
    ObjectType<?> objectType
) {
    public DataSourceQuery {
        Objects.requireNonNull(tenancyId, "tenancyId");
    }
}
```

A descriptor matches when all conditions hold:
```
(descriptor.tenancyId == tenancyId  OR  descriptor.tenancyId == PLATFORM_TENANT_ID)
AND (objectType == null  OR  descriptor.objectType.getTypeKey().equals(objectType.getTypeKey()))
```

### DataSourceRegistered

CDI event fired after successful `DataSourceRegistry.register()`. Parallels `EndpointRegistered`.

```java
public record DataSourceRegistered(DataSourceDescriptor descriptor) {
    public DataSourceRegistered {
        Objects.requireNonNull(descriptor, "descriptor");
    }
}
```

Non-no-op `DataSourceRegistry` implementations have an obligation to fire `Event<DataSourceRegistered>.fireAsync()` after every successful `register()` call. `NoOpDataSourceRegistry` must NOT fire this event. The `DataSourceRouter` observes this event to wire up CDI-to-DataSource routing for newly registered DataSources.

### DataSourceRegistry

Named DataSource registration, discovery, and runtime instance management. Parallels `EndpointRegistry` for descriptors, extends it with runtime `DataSource` instance lifecycle.

```java
public interface DataSourceRegistry {
    /** Register a DataSource and return its runtime instance. Creates the backing alpha network. */
    DataSource<?> register(DataSourceDescriptor descriptor);

    /** Resolve a descriptor by path for the given tenant (two-step priority lookup). */
    Optional<DataSourceDescriptor> resolve(Path path, String tenancyId);

    /** Resolve the runtime DataSource instance for subscription. */
    Optional<DataSource<?>> resolveSource(Path path, String tenancyId);

    /** Discover descriptors matching the query criteria. */
    List<DataSourceDescriptor> discover(DataSourceQuery query);

    /** Deregister — removes both descriptor and runtime DataSource instance. */
    void deregister(Path path, String tenancyId);
}
```

The registry manages both **descriptor metadata** and **runtime DataSource instances**. `register()` creates the backing `DataSource` instance (with its alpha network) and returns it. `resolveSource()` retrieves the runtime instance for subscription. `resolve()` retrieves the descriptor for metadata queries. `deregister()` destroys both.

**Tenant isolation contract** (parallels `EndpointRegistry`):

- `resolve()` — two-step priority lookup: tenant-specific first (`descriptor.tenancyId().equals(tenancyId)`), then platform-global fallback (`descriptor.tenancyId().equals(PLATFORM_TENANT_ID)`). Tenant-specific takes precedence — allows tenants to override platform defaults.
- `discover()` — returns both tenant-specific and platform-global matches. No override semantics; use `resolve()` when a single authoritative result is required.
- Platform-global DataSources are registered with `TenancyConstants.PLATFORM_TENANT_ID`
- Cross-tenant visibility: Tenant A's DataSources are never returned for Tenant B's queries

**CDI event obligation:** Non-no-op implementations must fire `DataSourceRegistered` via `Event<DataSourceRegistered>.fireAsync()` after every successful `register()` call. `NoOpDataSourceRegistry @DefaultBean` must NOT fire the event.

### Marshaller

Optional transform between EndPoint event format and DataSource type. Lives in `platform-api/`.

```java
@FunctionalInterface
public interface Marshaller<I, O> {
    O marshal(I input) throws MarshalException;
}
```

- Associated with a DataSource at registration time (mechanism TBD — see §10.4)
- Applied by the `DataSourceRouter` before pushing events into the DataSource
- **Failure behavior:** `MarshalException` causes the event to be dropped for that DataSource with a WARN log. Other DataSources referencing the same EndPoint are unaffected

## 7. Module Structure

Follows the established platform pattern (same as endpoints, memory, acl, preferences):

| Module | Artifact | Purpose |
|--------|----------|---------|
| `platform-api/` | `casehub-platform-api` | SPI types — ObjectType, DataProcessor, SubscriptionHandle, FilterExpression, DataSource, DataSourceDescriptor, DataSourceQuery, DataSourceRegistered, DataSourceRegistry, Marshaller. Zero deps. |
| `platform/` | `casehub-platform` | `NoOpDataSourceRegistry @DefaultBean` — does nothing, stores nothing. `DataSourceRouter @ApplicationScoped` — bridges CDI CloudEvents into DataSources; discovers existing registrations at startup, wires new ones via `@ObservesAsync DataSourceRegistered`. Does nothing when NoOp registry is active (no registered DataSources). |
| `datasource-inmem/` | `casehub-platform-datasource-inmem` | `InMemoryDataSourceRegistry @Alternative @Priority(100)` — ConcurrentHashMap-backed, volatile. Contains the alpha network implementation (TypeNode, FilterNode, FanOutProcessor). No Flyway, no persistence, no quarkus:build goal. |

When Drools is ready, a new `datasource-drools/` module replaces `datasource-inmem/` with the real engine behind the same `DataSourceRegistry` SPI. Higher `@Priority` displaces the in-memory implementation by classpath presence.

## 8. Drools Vol2 Alignment

The SPI is designed for Drools vol2 drop-in replacement. Mapping:

| casehub SPI | Drools vol2 | Notes |
|-------------|-------------|-------|
| `ObjectType<T>` | `org.drools.base.base.ObjectType` | Same concept, stripped-down interface; casehub adds type parameter for compile-time safety |
| `DataProcessor<T>` | `DataProcessor<CTX, T>` | casehub drops the CTX type parameter (no unit instance context) |
| `DataSource<T> extends DataProcessor<T>` | `DataStore<T>` | casehub is add-only (no update/remove); extends DataProcessor for ingestion |
| `DataSourceRegistry` | No direct equivalent | casehub adds named registration; Drools uses `RuleBaseModifier` |
| Alpha network (TypeNode, FilterNode) | `Filter1Type`, `Filter1AlphaProcessor`, `Filter1TypeIndex`, `TypeIndexer` | Same architecture, casehub-specific class names |
| Fan-out | `Router` + `FanOutBetaProcessor` | Same pattern |
| Filter expressions | MVEL3 `Evaluator<C,W,O>` | Same transpiler; casehub uses MVEL3 directly for dynamic filters |
| `SubscriptionHandle` | No direct equivalent | Drools manages subscriptions via `RuleBaseModifier` lifecycle |

The `datasource-drools/` module would implement `DataSourceRegistry` using Drools' `RuleBase`, `RuleBaseModifier`, and `UnitInstantiator` internally, exposing the same casehub SPI to consumers.

## 9. Resolved Decisions

Decisions made during design that are no longer open:

| Decision | Resolution | Rationale |
|----------|-----------|-----------|
| Thread model | Synchronous propagation through the filter chain | Matches Drools vol2; simpler; no ordering/backpressure concerns |
| Error propagation | Per-subscriber isolation; exceptions logged, not propagated | Preserves CDI fireAsync fault isolation; matches Drools vol2 |
| Filter sharing (initial impl) | Type nodes shared; string-based filter nodes shareable; lambda filter nodes not shared | Lambda equality is identity-based; string-based evaluators (MVEL3, jq) enable sharing via string comparison. Drools drop-in brings full sharing |
| CDI event migration | Additive — DataSourceRouter coexists with existing `@ObservesAsync CloudEvent` consumers | Non-breaking; consumers migrate incrementally |
| CDI-to-DataSource bridge | DataSourceRouter `@ObservesAsync CloudEvent` in `platform/`; tenancy-based routing, alpha network does type discrimination | Router survives backend replacement; tenancy routing avoids CloudEvent source URI ↔ EndPoint path mismatch |
| DataSourceRouter startup | Discover-at-startup + idempotent post-startup handler (same pattern as `CamelStreamProcessor`) | Pre-`@PostConstruct` registrations are covered by startup discovery; `AtomicBoolean` gates late async events |
| DataSourceRegistry manages runtime instances | `register()` returns `DataSource<?>`, `resolveSource()` retrieves runtime instance | Consumers need runtime DataSource objects to subscribe, not just descriptors |
| Filter sharing via FilterExpression | `FilterExpression<T> implements Predicate<T>` carries expression metadata; alpha network checks `instanceof` | No separate subscribe overload needed; sharing is transparent to consumers |
| Named views | Subscriptions carry filter criteria; named entries in the registry are sources, not filtered views | DataSource is a boundary concept; filtered views are subscriptions, not new sources |
| Dynamic filter language | jq (for raw CloudEvents), MVEL3 (mock until published — intended for POJOs), Java lambdas (for programmatic use) | jq for undeserialised CloudEvent attribute routing. MVEL3 mocked initially; transpiles to bytecode when available — native speed, structural equality, Drools alignment |
| Module structure | SPI in `platform-api/`, `@DefaultBean` no-op in `platform/`, `@Alternative` impl in `datasource-inmem/` | Same pattern as endpoints, memory, acl, preferences |
| Marshaller stage | Optional, configurable transform between EndPoint and DataSource | Enables two paths: raw CloudEvent (jq filters, no deserialisation) and typed POJO (MVEL3/lambda filters, native speed) |
| ExpressionEvaluator location | Move to `io.casehub.platform.api.evaluator` in `platform-api/` | Single method (`type()`), no engine-specific concerns; both platform (DataSource filters) and engine (Binding guards) use the same interface |
| EndPoint to DataSource relationship | Declared via `DataSourceDescriptor.endpointPath()`; one-to-many (one EndPoint can feed multiple DataSources) | Multiple DataSources per EndPoint enables raw and typed paths from the same source. Wiring is automatic via DataSourceRouter |
| DataSourceDescriptor key type | `Path` (hierarchical, consistent with EndpointDescriptor) | Enables prefix-based discovery, hierarchical namespacing, and API surface consistency |
| Tenancy isolation | Per-DataSource instance; alpha network is not shared across tenants | Simpler, more secure than shared network with tenant-aware filtering |
| Subscription lifecycle | Handle-based (`SubscriptionHandle`) — O(1) unsubscription, type-safe | Callers unsubscribe via handle, not processor identity lookup |
| MVEL3 mock behavior | Pass-through (all evaluations return `true`) | Full pipeline testable end-to-end without MVEL3; events flow through to subscribers |
| DataSource ingestion | `DataSource<T> extends DataProcessor<T>` — `add(T)` is the entry point | A DataSource IS a data entry point; one interface, two roles (producer ingestion + consumer subscription). Aligns with Drools DataStore |
| Marshalling pre-filter | `DataSourceDescriptor.acceptedEventTypes` — CloudEvent type pre-filter checked before marshalling | Tenancy-broadcast routing makes marshalling failure the normal case for typed DataSources; pre-filter avoids the work entirely |

## 10. Open Questions

### 10.1 Scoping Lifecycle

- Who creates case-scoped DataSources? The engine at case start? The case definition YAML?
- What is the lifecycle when a case closes? Automatic deregistration?
- Can a case-scoped DataSource outlive its case (e.g. for post-mortem analysis)?

### 10.2 DataSink

The outbound boundary (`DataSink`) complements DataSource. In scope for the overall architecture but deferred for implementation. The same alpha network model may apply in reverse (filtering what exits the system).

### 10.3 Marshaller Configuration Model

The `Marshaller` interface is defined (§6), but the configuration mechanism is open:

- Is the marshaller declared as a property in `DataSourceDescriptor` (class name string → CDI lookup)?
- Or a separate registration API parameter (`registry.register(descriptor, marshaller)`)?
- How are marshallers discovered — CDI `Instance<Marshaller>` with type discriminator?
- Error handling beyond log-and-drop: should there be a dead-letter mechanism for failed marshalling?
