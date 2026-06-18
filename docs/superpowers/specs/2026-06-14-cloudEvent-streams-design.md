# CloudEvent Foundation and Platform Stream Modules

**Date:** 2026-06-14 (brainstormed 2026-06-18)
**Issue:** casehubio/platform#98
**Epic:** casehubio/parent#277
**Architectural context:** `docs/superpowers/specs/2026-06-13-p0-layering-decisions-design.md` in casehubio/parent (Decisions 1 and 2)

---

## What this implements

1. `io.cloudevents.CloudEvent` as the platform's typed CDI event type — added as a `compile` dependency to `casehub-platform-api`.
2. `StreamContext` SPI for tenancy propagation in async processing chains (P0 interface; propagation mechanism is P1.8).
3. `EndpointRegistered` CDI event record fired by non-no-op `EndpointRegistry` implementations.
4. `STREAM_EVENT_TYPE` property key on `EndpointPropertyKeys`.
5. `AMQP` added to `EndpointProtocol` enum.
6. Five classpath-activated stream submodules under `casehub-platform`, each firing `Event<CloudEvent>.fireAsync()`.

---

## CloudEvent SDK version management

`cloudevents-core:4.0.1` is added to the `casehub-platform-parent` `<dependencyManagement>` as a direct entry (not a BOM import). `cloudevents-api:4.0.1` is also pinned there, overriding the Quarkus BOM's `cloudevents-api:3.0.0`. Maven's direct-entry precedence over BOM imports ensures the 4.0.1 versions are used consistently throughout the platform build.

Both `cloudevents-core` and `cloudevents-json-jackson` are pinned at `4.0.1` — same CloudEvents SDK release.

Once `casehub-iot`, `casehub-qhorus`, or `casehub-connectors` implement their adapters, version management moves to `casehub-parent` BOM (parent#276) and the platform-parent entries are removed.

---

## Changes to `casehub-platform-api`

### Package: `io.casehub.platform.api.endpoints`

**`EndpointRegistered` record** (new):
```java
public record EndpointRegistered(EndpointDescriptor descriptor) {}
```
CDI event type. Fired by `InMemoryEndpointRegistry.register()` via `Event<EndpointRegistered>.fireAsync()` after every successful `store.put()`. `NoOpEndpointRegistry.register()` is a silent no-op and must NOT fire this event — firing it would trigger stream route creation for phantom endpoints that don't actually exist. Any future non-no-op `EndpointRegistry` implementation must document whether it fires `EndpointRegistered`; the `EndpointRegistry` interface Javadoc states this as a required obligation.

**`EndpointPropertyKeys.STREAM_EVENT_TYPE`** (new constant):
```java
public static final String STREAM_EVENT_TYPE = "stream-event-type";
```
Reverse-DNS CloudEvent `type` string, e.g. `io.casehub.iot.temperature`. All stream modules read this from `EndpointDescriptor.properties()` to set the CloudEvent `type` field.

**`EndpointProtocol.AMQP`** (new enum value):
```java
/** AMQP message broker transport. Use TOPIC property for queue/topic name. URL does not apply — broker connection is Quarkus-managed via standard config (e.g. amqp-host, amqp-port). */
AMQP,
```
Inserted after `KAFKA`, before `MCP`.

### Package: `io.casehub.platform.api.streams` (new)

**`StreamContext` SPI** (new):
```java
public interface StreamContext {
    String tenancyId();
}
```
The async equivalent of `CurrentPrincipal` for stream processing chains where CDI request scope is inactive. `@ObservesAsync` handlers that need tenant-scoped operations must extract `tenancyid` directly from `event.getExtension("tenancyid")` (see PP-20260611-d4e5cf). This SPI defines the contract for future propagation mechanisms; the P0 `@DefaultBean` returns `DEFAULT_TENANT_ID`.

Javadoc must state: "P0 — the multi-tenant propagation mechanism is unresolved (P1.8). Do not rely on this SPI returning a per-message tenancyId in P0. Extract tenancyid from the CloudEvent extension attribute directly."

### `cloudevents-core` compile dependency

```xml
<dependency>
    <groupId>io.cloudevents</groupId>
    <artifactId>cloudevents-core</artifactId>
    <scope>compile</scope>
</dependency>
```

`io.cloudevents.CloudEvent` becomes available to all consumers of `casehub-platform-api` transitively. No wrapper type.

---

## Changes to `casehub-platform` (default/mock module)

### Package: `io.casehub.platform.streams` (new)

**`NoOpStreamContext`** (new):
```java
@DefaultBean
@ApplicationScoped
public class NoOpStreamContext implements StreamContext {
    public String tenancyId() { return TenancyConstants.DEFAULT_TENANT_ID; }
}
```

---

## Changes to `casehub-platform-endpoints-memory`

**`InMemoryEndpointRegistry`** gains `Event<EndpointRegistered>` injection and fires it after every successful registration:

```java
@Inject Event<EndpointRegistered> endpointRegisteredEvent;

@Override
public void register(EndpointDescriptor endpoint) {
    store.put(new RegistryKey(endpoint.path().value(), endpoint.tenancyId()), endpoint);
    endpointRegisteredEvent.fireAsync(new EndpointRegistered(endpoint));
}
```

`InMemoryEndpointRegistryTest` adds a verification that `EndpointRegistered` fires on every `register()` call using a `CountDownLatch` capture bean (`@ApplicationScoped`, not `@ObservesAsync` — GE-20260513-b15933: `@ObservesAsync` is silently not delivered in `@QuarkusTest`; the capture bean observes synchronously or the test calls the handler directly).

---

## New submodules

### Folder naming and Maven coordinates

| Folder | Artifact ID |
|--------|-------------|
| `streams-kafka/` | `casehub-platform-streams-kafka` |
| `streams-amqp/` | `casehub-platform-streams-amqp` |
| `streams-webhook/` | `casehub-platform-streams-webhook` |
| `streams-poll/` | `casehub-platform-streams-poll` |
| `streams-camel/` | `casehub-platform-streams-camel` |

### Common pom pattern

All five are **Jandex library modules** — Quarkus plugin goals `generate-code` and `generate-code-tests` only; no `build` goal. Jandex plugin required for CDI bean discovery when consumed as JAR (PP-20260508-6d1f5c). Pattern follows `scim/`.

Common compile deps:
- `casehub-platform-api` (compile)
- `casehub-platform` (runtime — provides `NoOpEndpointRegistry @DefaultBean` and `NoOpStreamContext @DefaultBean` during augmentation)
- `casehub-platform-endpoints-memory` (test — provides `InMemoryEndpointRegistry` for test isolation)

### Common CloudEvent construction

All stream modules produce `CloudEvent` with:

| Field | Value |
|-------|-------|
| `type` | `EndpointDescriptor.properties().get(STREAM_EVENT_TYPE)` |
| `source` | logical producer URI, module-specific (e.g. `/platform/streams/kafka/{topic}`) |
| `subject` | `null` for raw payloads; preserved if incoming message is already a CloudEvent (detected by `application/cloudevents+json` content type or binary CloudEvents marker) |
| `id` | `UUID.randomUUID().toString()` |
| `time` | message/frame timestamp if available, `OffsetDateTime.now()` otherwise |
| `data` | raw payload bytes or structured payload |
| `tenancyid` extension | source varies by module — see below |

`tenancyid` source by module:

| Module | `tenancyid` source | Rationale |
|--------|-------------------|-----------|
| `streams-kafka` | Kafka header `X-Tenancy-ID`; fallback to `EndpointDescriptor.tenancyId()` | Internal producer; header is operator-controlled |
| `streams-amqp` | AMQP message property `X-Tenancy-ID`; fallback to `EndpointDescriptor.tenancyId()` | Internal producer; property is operator-controlled |
| `streams-webhook` | `EndpointDescriptor.tenancyId()` (set by operator at registration) | External caller must not self-claim tenant; URL path `{tenancyId}` param is IGNORED for tenancyid — using it would be a security hole |
| `streams-poll` | `EndpointDescriptor.tenancyId()` | Operator-configured polling target |
| `streams-camel` | `EndpointDescriptor.tenancyId()` | Camel route is operator-defined |

---

## Module-by-module design

### `streams-kafka/`

**Dependencies:** `quarkus-smallrye-reactive-messaging-kafka`, `cloudevents-json-jackson` (for CloudEvents deserialization from Kafka, both at 4.0.1 from the platform-parent managed versions).

**Channel configuration:** static `@Incoming` channels declared in `application.properties` by the consuming application:
```
mp.messaging.incoming.my-events.connector=smallrye-kafka
mp.messaging.incoming.my-events.topic=iot-temperature
```

**P0 channel constraint:** one `@Incoming("casehub-kafka-stream")` channel per deployment (channel name configurable via `casehub.streams.kafka.channel`, default `casehub-kafka-stream`). Multiple Kafka topics can feed the same channel via `mp.messaging.incoming.casehub-kafka-stream.topics=topic1,topic2` (SmallRye multi-topic). For dynamic topics (unknown at deploy time), use `streams-camel` instead.

**Channel→EndpointDescriptor correlation** at `@Observes StartupEvent`: reads `mp.messaging.incoming.${casehub.streams.kafka.channel}.topic` (or `.topics`) via MicroProfile Config, then calls `EndpointRegistry.discover(KAFKA + RECEIVE)` and matches by `TOPIC` property. If no matching descriptor is found for a topic, the processor logs a warning and fires a CloudEvent with `type = "io.casehub.platform.streams.kafka.unregistered"`.

**Message handling:**
- If message is already a CloudEvent (via SmallRye CloudEvents Kafka deserialization): add `tenancyid` extension if absent, fire as-is.
- If message is raw bytes/String: build a CloudEvent from scratch using the descriptor's `STREAM_EVENT_TYPE` and the message body as `data`.

**`tenancyid`:** Kafka header `X-Tenancy-ID` if present; `EndpointDescriptor.tenancyId()` otherwise.

### `streams-amqp/`

Symmetric with `streams-kafka/`. Uses `quarkus-smallrye-reactive-messaging-amqp`. `EndpointProtocol.AMQP` + `EndpointCapability.RECEIVE` for discovery. `tenancyid` from AMQP message property `X-Tenancy-ID`; fallback to descriptor.

### `streams-webhook/`

**Dependencies:** `quarkus-rest-jackson`, `quarkus-smallrye-reactive-messaging` (for CloudEvents HTTP binding deserialization).

**REST endpoint:** `@POST /streams/webhook/{tenancyId}/{streamId}`
- Accepts CloudEvents HTTP binding (structured: `application/cloudevents+json`; binary: `ce-` prefixed headers + arbitrary content type).
- `tenancyId` path param is the URL segment for routing — used only to look up the logical stream descriptor; NOT used as the `tenancyid` extension value.
- `streamId` → `EndpointRegistry.resolve(Path.of("streams", streamId), EndpointDescriptor.tenancyId())` to get `STREAM_EVENT_TYPE`.

**Self-registration at `@Observes StartupEvent`:**
```
Path.of("platform", "streams", "webhook")
EndpointType.SERVICE
EndpointProtocol.HTTP
EndpointCapability.RECEIVE
URL = casehub.streams.webhook.public-url config (required, no default)
```
This announces that the webhook receiver is running. Logical stream-source descriptors (per-stream paths) are registered by `casehub-ops/StreamEndpointNodeProvisioner`, not by this module.

**Two distinct EndpointRegistry entries:** the module's self-registration at `Path.of("platform", "streams", "webhook")` is the physical receiver. Each logical stream source is at `Path.of("streams", streamId)` registered by ops. Different paths, different semantics — not a conflict.

**`tenancyid`:** `EndpointDescriptor.tenancyId()` from the logical stream descriptor (operator-set at registration). The `tenancyId` URL path parameter is a routing hint, not a trust boundary.

### `streams-poll/`

**Dependencies:** `quarkus-rest-client-jackson`, `quarkus-scheduler`.

**Poll loop:**
```java
@Scheduled(every = "${casehub.streams.poll.interval:60s}")
void poll() {
    endpointRegistry.discover(
        new EndpointQuery(null /* all tenants */, null, HTTP, Set.of(QUERY))
    ).forEach(this::pollAndFire);
}
```

Per endpoint: HTTP GET to `EndpointPropertyKeys.URL`; map response body to CloudEvent `data`; set `STREAM_EVENT_TYPE` from descriptor; fire `Event<CloudEvent>.fireAsync()`.

**Tenancy scope for discovery:** discovers endpoints with `EndpointProtocol.HTTP + EndpointCapability.QUERY` for `TenancyConstants.PLATFORM_TENANT_ID` (platform-global) in P0. Per-tenant poll scheduling (to poll tenant-scoped `HTTP + QUERY` endpoints) is P1+.

**`tenancyid`:** `EndpointDescriptor.tenancyId()`.

**Note:** single global poll interval for all endpoints in P0. Per-endpoint intervals are P1+.

### `streams-camel/`

**Dependencies:** `camel-quarkus-core` + Camel components as needed by the consumer (e.g. `camel-quarkus-kafka` for dynamic Kafka, `camel-quarkus-amqp` for AMQP via Camel). This module only provides the route-building infrastructure; the consumer application adds component deps.

**Startup buffering and observer** (GE-20260522-5685ba: synchronous `@Observes StartupEvent` propagates exceptions and can abort startup — use it only for safe operations):
```java
final AtomicBoolean camelStarted = new AtomicBoolean(false);
final CopyOnWriteArrayList<EndpointDescriptor> pendingDescriptors = new CopyOnWriteArrayList<>();

void onStartup(@Observes io.quarkus.runtime.StartupEvent ev) {
    pendingDescriptors.forEach(this::addRoute);
    pendingDescriptors.clear();
    camelStarted.set(true);
}

void onEndpointRegistered(@ObservesAsync EndpointRegistered event) {
    if (event.descriptor().protocol() != EndpointProtocol.CAMEL) return;
    if (camelStarted.get()) {
        addRoute(event.descriptor());
    } else {
        pendingDescriptors.add(event.descriptor());
    }
}
```
Quarkus guarantees `StartupEvent` fires after `CamelContext` reaches `Started`. Routes added in this handler are safe. Runtime registrations after startup call `addRoutes()` directly.

**Route construction per descriptor:**
```java
void addRoute(EndpointDescriptor d) {
    camelContext.addRoutes(new RouteBuilder() {
        public void configure() {
            from(d.properties().get(URL))
                .process(exchange -> {
                    CloudEvent ce = buildCloudEvent(exchange, d);
                    cloudEventBus.fireAsync(ce);
                });
        }
    });
}
```

**CAMEL vs KAFKA mutual exclusion (deployment constraint, not code):** `streams-camel` observes `EndpointProtocol.CAMEL` endpoints only — not `KAFKA`. Running `streams-kafka` (SmallRye static channel) and `streams-camel` (Camel Kafka component) for the same Kafka topic from the same consumer group causes silent partial message loss — Kafka partition-splits between two consumer groups. Deployment rule: use exactly one for a given topic.

---

## Testing

### Test strategy for `fireAsync()` in `@QuarkusTest`

GE-20260513-b15933: `@ObservesAsync` CDI events are silently not delivered in `@QuarkusTest`. Each stream module test must NOT rely on CDI observation to verify `CloudEvent` fire.

Required pattern: extract the CloudEvent construction logic into a package-private method on the processor bean, test that method directly, verify the constructed `CloudEvent` fields without going through `fireAsync()`.

For integration-level verification that `fireAsync()` is called: inject an `@ApplicationScoped` capture bean that exposes a `CountDownLatch`; the capture bean's `void capture(CloudEvent ce)` is called explicitly by the test (not via CDI observation).

### Per-module test approach

| Module | Message simulation |
|--------|-------------------|
| `streams-kafka` | SmallRye `@InMemoryConnector` (test scope) — in-process channel |
| `streams-amqp` | SmallRye `@InMemoryConnector` — same pattern |
| `streams-webhook` | Quarkus REST Assured — `POST /streams/webhook/{tenancyId}/{streamId}` |
| `streams-poll` | WireMock (`wiremock:3.x`) — simulates polled HTTP endpoint |
| `streams-camel` | `camel-quarkus-mock` direct route; or a `direct:` endpoint URI in test config |

---

## Acceptance criteria

- [ ] `casehub-platform-parent` pom.xml manages `cloudevents-core:4.0.1`, `cloudevents-api:4.0.1`, `cloudevents-json-jackson:4.0.1`
- [ ] `casehub-platform-api` pom.xml has `cloudevents-core` compile dep (no version — managed by parent)
- [ ] `io.cloudevents.CloudEvent` visible to all consumers of `casehub-platform-api` transitively
- [ ] `StreamContext` SPI in `io.casehub.platform.api.streams`; Javadoc states P0 limitation
- [ ] `NoOpStreamContext @DefaultBean @ApplicationScoped` in `io.casehub.platform.streams` inside `casehub-platform`
- [ ] `EndpointRegistered` record in `io.casehub.platform.api.endpoints`
- [ ] `EndpointPropertyKeys.STREAM_EVENT_TYPE` constant added
- [ ] `EndpointProtocol.AMQP` enum value added
- [ ] `InMemoryEndpointRegistry.register()` fires `Event<EndpointRegistered>.fireAsync()` after `store.put()`
- [ ] `NoOpEndpointRegistry.register()` remains silent — no event fired
- [ ] `InMemoryEndpointRegistryTest` verifies `EndpointRegistered` fires on each `register()` call
- [ ] All five stream modules build and pass tests
- [ ] Each stream module activates by classpath presence (adding as dep starts receiving events; removing stops without breaking other consumers)
- [ ] Each stream module fires `Event<CloudEvent>.fireAsync()` with `type`, `source`, `subject`, `id`, `time`, `tenancyid` extension set
- [ ] `streams-camel` buffers `EndpointRegistered` events received before `CamelContext` starts; drains in `@Observes StartupEvent`
- [ ] CAMEL vs KAFKA mutual exclusion documented in `streams-camel` module Javadoc and README-style pom `<description>`
- [ ] `casehub-platform-api` module `CLAUDE.md` entry updated with stream package docs
- [ ] `parent#276` (BOM) and `parent#277` (epic) referenced in commit message

---

## Deferred (captured as issues)

| Concern | Issue |
|---------|-------|
| `StreamContext` multi-tenant propagation mechanism | P1.8 — no issue yet; design requires ThreadLocal or Mutiny context proposal |
| Per-endpoint poll intervals | P1+ in poll module |
| CloudEvent `subject` extraction from raw payload fields | P1+ |
| Stream source credential lookup (`credentialRef` on EndpointDescriptor) | P1+ |
| `cloudevents-core` to casehub-parent BOM | parent#276 |
| `StateChangeEvent → CloudEvent` adapter | iot#19 |
| `MessageReceivedEvent → CloudEvent` adapter | qhorus#279 |
| `InboundMessage → CloudEvent` adapter | connectors#20 |
