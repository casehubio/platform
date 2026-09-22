# Design: Streams Core Extraction + Spring Cloud Stream Adapters

**Issue:** casehubio/parent#507
**Branch:** issue-501-spring-deployment-readiness
**Date:** 2026-09-19

## Problem

Four streaming modules are Quarkus-only with no core extraction:
- `streams-kafka` — SmallRye Reactive Messaging `@Incoming`
- `streams-amqp` — SmallRye Reactive Messaging `@Incoming`
- `streams-poll` — `java.net.http.HttpClient` + Quarkus `@Scheduled`
- `streams-camel` — Apache Camel route builder + CDI lifecycle events

Only `streams-webhook-core` has been extracted (with its REST endpoint generated into platform-spring).

All four modules duplicate a `buildCloudEvent()` method (~30 lines each) with identical semantics: read `STREAM_EVENT_TYPE` from descriptor → determine tenancyId → construct CloudEvent v1 → optionally set datacontenttype.

## Solution

### Module Architecture

9 new modules + 4 refactored:

```
streams-core                    ← NEW: shared CloudEventFactory + constants
├── streams-poll-core           ← NEW: PollStreamProcessorCore POJO
├── streams-kafka-core          ← NEW: KafkaStreamProcessorCore POJO
├── streams-amqp-core           ← NEW: AmqpStreamProcessorCore POJO
├── streams-camel-core          ← NEW: CamelStreamProcessorCore POJO
├── streams-webhook-core        (existing, unchanged)
│
├── streams-poll                ← REFACTOR: thin Quarkus wrapper → delegates to poll-core
├── streams-kafka               ← REFACTOR: thin Quarkus wrapper → delegates to kafka-core
├── streams-amqp                ← REFACTOR: thin Quarkus wrapper → delegates to amqp-core
├── streams-camel               ← REFACTOR: thin Quarkus wrapper → delegates to camel-core
├── streams-webhook             (existing, unchanged)
│
├── streams-poll-spring         ← NEW: @Scheduled lifecycle
├── streams-kafka-spring        ← NEW: Spring Cloud Stream functional binding
├── streams-amqp-spring         ← NEW: Spring Cloud Stream functional binding
└── streams-camel-spring        ← NEW: @EventListener lifecycle + camel-spring-boot
```

### streams-core — Shared Foundation

**Artifact:** `casehub-platform-streams-core`
**Dependencies:** `casehub-platform-api`, `cloudevents-core`

```java
package io.casehub.platform.streams;

public final class StreamCloudEventFactory {

    public static CloudEvent build(byte[] data,
                                   @Nullable EndpointDescriptor descriptor,
                                   String tenancyIdOverride,
                                   String sourcePrefix) { ... }
}
```

Parameters:
- `data` — raw message bytes
- `descriptor` — endpoint descriptor providing type, content type, tenancyId
- `tenancyIdOverride` — from message header (nullable; falls back to descriptor, then DEFAULT_TENANT_ID)
- `sourcePrefix` — module-specific source URI prefix (e.g. `"kafka"`, `"amqp"`, `"poll"`, `"camel"`, `"webhook"`)

The `descriptor` parameter is nullable — messages arriving on unregistered topics/addresses pass `null`. When null, the factory uses fallback values for all descriptor-derived fields.

Logic (consolidated from 4 copies):
1. Read `STREAM_EVENT_TYPE` from `descriptor.properties()` if non-null (fallback: `"io.casehub.stream.unregistered"`)
2. Determine effective tenancyId: `tenancyIdOverride` → `descriptor.tenancyId()` (if non-null) → `DEFAULT_TENANT_ID`
3. Build `CloudEventBuilder.v1()` with UUID id, type, source URI (`sourcePrefix + "://" + descriptor.path()`), `OffsetDateTime.now()`, raw data, `tenancyid` extension
4. If `STREAM_DATA_CONTENT_TYPE` present on descriptor → set `datacontenttype`

Also includes:
- `StreamConstants` — unregistered type fallback, source URI format

Each core module handles its own endpoint discovery (protocol-specific `EndpointQuery` + module-specific map structure). Not abstracted — the queries are 2-3 lines and the result shapes differ.

### streams-poll-core

**Artifact:** `casehub-platform-streams-poll-core`
**Dependencies:** `casehub-platform-streams-core`

```java
public class PollStreamProcessorCore {

    public PollStreamProcessorCore(EndpointRegistry endpointRegistry,
                                   Consumer<CloudEvent> eventCallback) { ... }

    public void poll() { ... }  // discover endpoints → fetch → build → fire
}
```

- Owns `java.net.http.HttpClient` (JDK, created internally)
- `fetchBytes(String url)` — HTTP GET with explicit status code check, InterruptedException handling
- `poll()` — discovers HTTP/QUERY endpoints, iterates, fetches, builds CloudEvent via `StreamCloudEventFactory`, fires callback
- No tenancyId header override (HTTP GET responses don't carry tenancy headers)

### streams-kafka-core

**Artifact:** `casehub-platform-streams-kafka-core`
**Dependencies:** `casehub-platform-streams-core`

```java
public class KafkaStreamProcessorCore {

    public KafkaStreamProcessorCore(EndpointRegistry endpointRegistry,
                                    Consumer<CloudEvent> eventCallback,
                                    Map<String, String> channelTopicConfig) { ... }

    public void init() { ... }  // correlate topics → descriptors
    public void processMessage(byte[] body, String topic, String tenancyId) { ... }
}
```

- `channelTopicConfig` — map of channel name → topic list (passed as plain strings, not MicroProfile Config)
- `init()` — discovers KAFKA/RECEIVE endpoints, correlates topics to descriptors
- `processMessage()` — looks up descriptor by topic, builds CloudEvent via factory (with tenancyId override from Kafka header), fires callback
- SmallRye `Message<byte[]>` and `IncomingKafkaRecordMetadata` extraction stays in Quarkus wrapper

### streams-amqp-core

**Artifact:** `casehub-platform-streams-amqp-core`
**Dependencies:** `casehub-platform-streams-core`

```java
public class AmqpStreamProcessorCore {

    public AmqpStreamProcessorCore(EndpointRegistry endpointRegistry,
                                   Consumer<CloudEvent> eventCallback,
                                   Map<String, String> channelAddressConfig) { ... }

    public void init() { ... }  // correlate addresses → descriptors
    public void processMessage(byte[] body, String address, String tenancyId) { ... }
}
```

- Same pattern as kafka-core. Address-to-descriptor correlation instead of topic-to-descriptor.
- tenancyId from AMQP application properties (X-Tenancy-ID)

### streams-camel-core

**Artifact:** `casehub-platform-streams-camel-core`
**Dependencies:** `casehub-platform-streams-core`, `camel-core-model` (Apache Camel route DSL — provides `RouteBuilder`, transitively includes `camel-api` for `CamelContext`/`Exchange`)

```java
public class CamelStreamProcessorCore {

    public CamelStreamProcessorCore(CamelContext camelContext,
                                    EndpointRegistry endpointRegistry,
                                    Consumer<CloudEvent> eventCallback) { ... }

    public void init() { ... }
    public void onEndpointRegistered(EndpointDescriptor descriptor) { ... }
}
```

- `CamelContext` taken directly as constructor param (D21: no wrapping abstraction)
- Owns route-building loop: iterates descriptors, creates `RouteBuilder` with processor lambda
- Idempotency tracking: `Set<String>` (ConcurrentHashMap.newKeySet()) keyed by URI
- Startup-window logic: `AtomicBoolean` controls startup vs runtime registration ordering
- `init()` — discovers CAMEL/RECEIVE endpoints, builds routes for all
- `onEndpointRegistered(descriptor)` — handles runtime endpoint additions (filters for CAMEL protocol, checks idempotency, adds route)
- Route processor: extracts `byte[]` body from Camel `Exchange`, builds CloudEvent via factory, fires callback

### Spring Adapters

#### streams-poll-spring

**Artifact:** `casehub-platform-streams-poll-spring`
**Dependencies:** `casehub-platform-streams-poll-core`, `spring-boot-starter` (for @Scheduled)

Auto-configuration:
- `@Bean PollStreamProcessorCore` — wires `EndpointRegistry` + `ApplicationEventPublisher` (as `Consumer<CloudEvent>`)
- `@Scheduled(fixedDelayString = "${casehub.streams.poll.interval:60000}")` method calls `core.poll()`
- Note: uses JDK HttpClient via core (D22) — no Spring RestClient observability integration. Acceptable for a background poller.

#### streams-kafka-spring

**Artifact:** `casehub-platform-streams-kafka-spring`
**Dependencies:** `casehub-platform-streams-kafka-core`, `spring-kafka` (already in `spring-boot-starter`)

Auto-configuration:
- `@Bean KafkaStreamProcessorCore` — wires `EndpointRegistry` + `ApplicationEventPublisher` + channel config from `spring.kafka.consumer.*` properties
- `@KafkaListener(topics = "${casehub.streams.kafka.topic}")` handler extracts Kafka headers (`KafkaHeaders.RECEIVED_TOPIC`, custom `X-Tenancy-ID`), delegates to `core.processMessage()`
- `@EventListener(ApplicationStartedEvent.class)` calls `core.init()`
- Config: standard `spring.kafka.consumer.*` (well-known Spring configuration)

#### streams-amqp-spring

**Artifact:** `casehub-platform-streams-amqp-spring`
**Dependencies:** `casehub-platform-streams-amqp-core`, `spring-amqp` (already in `spring-boot-starter`)

Auto-configuration:
- `@Bean AmqpStreamProcessorCore` — wires `EndpointRegistry` + `ApplicationEventPublisher` + channel config from `spring.rabbitmq.*` properties
- `@RabbitListener(queues = "${casehub.streams.amqp.queue}")` handler extracts AMQP headers for address and tenancyId, delegates to `core.processMessage()`
- `@EventListener(ApplicationStartedEvent.class)` calls `core.init()`
- Config: standard `spring.rabbitmq.*` (well-known Spring configuration)

#### streams-camel-spring

**Artifact:** `casehub-platform-streams-camel-spring`
**Dependencies:** `casehub-platform-streams-camel-core`, `camel-spring-boot-starter`

Auto-configuration:
- `@Bean CamelStreamProcessorCore` — wires `CamelContext` (auto-configured by camel-spring-boot) + `EndpointRegistry` + `ApplicationEventPublisher`
- `@EventListener(ApplicationStartedEvent.class)` calls `core.init()`
- `@EventListener` on `EndpointRegistered` calls `core.onEndpointRegistered(event.descriptor())`

### Quarkus Module Refactoring

Each existing Quarkus module becomes a thin wrapper:

**Pattern (all 4 modules):**
```java
@ApplicationScoped
public class <Module>Beans {
    @Produces @ApplicationScoped
    <Module>Core processor(EndpointRegistry registry, Event<CloudEvent> bus, ...) {
        var core = new <Module>Core(registry, e -> bus.fireAsync(e), ...);
        core.init();
        return core;
    }
}
```

Plus framework-specific lifecycle where needed:
- **kafka/amqp:** `@Incoming` handler extracts SmallRye metadata, delegates to `core.processMessage()`
- **poll:** `@Scheduled` method calls `core.poll()`
- **camel:** `@ObservesAsync EndpointRegistered` calls `core.onEndpointRegistered()`

### Dependency Graph

```
platform-api
    └── streams-core (CloudEventFactory, constants)
            ├── streams-poll-core
            │       ├── streams-poll (Quarkus)
            │       └── streams-poll-spring
            ├── streams-kafka-core
            │       ├── streams-kafka (Quarkus)
            │       └── streams-kafka-spring (+ spring-kafka)
            ├── streams-amqp-core
            │       ├── streams-amqp (Quarkus)
            │       └── streams-amqp-spring (+ spring-amqp)
            ├── streams-camel-core (+ camel-api)
            │       ├── streams-camel (Quarkus, + camel-quarkus)
            │       └── streams-camel-spring (+ camel-spring-boot)
            └── streams-webhook-core (existing, unchanged)
                    └── streams-webhook (Quarkus, existing)
```

### Testing Strategy

**Core modules:** Pure JUnit 5 + AssertJ. Stub `EndpointRegistry` + capturing `Consumer<CloudEvent>`. For camel-core: standalone `DefaultCamelContext` (no container). For poll-core: WireMock for HTTP.

**Quarkus modules:** Existing tests continue to work — they verify the same behavior through the thin wrapper. May simplify (less logic in the module under test).

**Spring modules:** `@SpringBootTest` with embedded broker or mock bindings. Verify lifecycle hooks fire correctly and events reach `ApplicationEventPublisher`. Spring Cloud Stream test binder (`spring-cloud-stream-test-binder`) for kafka/amqp.

### Event Dispatch Semantics

Spring adapters use synchronous `ApplicationEventPublisher.publishEvent()` for the `Consumer<CloudEvent>` callback. All `@EventListener` beans finish before the handler returns. This is semantically equivalent to Quarkus CDI `fireAsync()` (ack after all observers complete), but the execution model differs: Spring runs listeners on the message consumer thread, while Quarkus dispatches to an async executor pool with a barrier.

A slow `@EventListener` blocks the Kafka/AMQP consumer thread in Spring, reducing throughput. This is a known divergence (D25). If it becomes a problem, consumers can add `@Async` to individual listeners.

### Why streams-webhook-core Is Excluded from StreamCloudEventFactory

`WebhookReceiver` constructs CloudEvents differently: it deserializes incoming *structured* CloudEvents (the sender already built the CloudEvent) and enriches them with tenancyId. The other 4 modules build CloudEvents from *raw bytes* (the message is just data, the module constructs the CloudEvent envelope). `StreamCloudEventFactory` handles the raw-bytes-to-CloudEvent case. Webhook's deserialization logic is unrelated.

### Consumer Guide Impact

Document in `docs/guides/consumer-guide.md`:
- Kafka configuration mapping (SmallRye `mp.messaging.incoming.*` → `spring.kafka.consumer.*`)
- AMQP configuration mapping (SmallRye `mp.messaging.incoming.*` → `spring.rabbitmq.*`)
- Camel Spring Boot auto-configuration (camel-spring-boot-starter)
- Poll interval configuration (`casehub.streams.poll.interval`)
- Event dispatch model: synchronous `publishEvent()` vs Quarkus async `fireAsync()` (D25)

## Decisions

- D19: Shared streams-core for CloudEventFactory (eliminates 4x duplication)
- D20: Native @KafkaListener/@RabbitListener for Spring adapters (revised from Spring Cloud Stream after decision review R1-02)
- D21: Full core extraction for camel (consistency + testability over ceremony savings)
- D22: JDK HttpClient in poll-core (framework-neutral, no Spring RestClient)
- D23: Refactor Quarkus modules to delegate to cores (single source of truth)
- D24: 9 new + 4 refactored module structure
- D25: Synchronous event dispatch in Spring adapters (known divergence from Quarkus async)
- D26: Explicit Category C → A reclassification for streams (overrides D3 from issue #469)

## References

- KafkaStreamProcessor.java — buildCloudEvent duplication analysis
- AmqpStreamProcessor.java — buildCloudEvent duplication analysis
- PollStreamProcessor.java — buildCloudEvent duplication analysis, fetchBytes() extraction candidate
- CamelStreamProcessor.java — route-building orchestration, idempotency tracking, startup-window logic
- WebhookReceiver.java (streams-webhook-core) — reference extraction pattern (Consumer<CloudEvent> callback)
- WebhookBeans.java (streams-webhook) — reference Quarkus wrapper pattern
- Spring Cloud Stream reference docs — functional binding model
- D21 deep analysis — steelman/devil's advocate/first-principles for Camel extraction depth
