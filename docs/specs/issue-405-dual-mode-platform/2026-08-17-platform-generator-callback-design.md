# Platform Code Generator + SPI Callback System — Design Spec

**Issues:** casehubio/platform#230, casehubio/platform#231, casehubio/engine#893, casehubio/work#348
**Parent epic:** casehubio/parent#405
**Date:** 2026-08-17

## Problem

CaseHub's GraphQL presentation layer is 2,270 lines of hand-written code across 4 domain modules (engine-graphql, work-graphql, ledger-graphql, qhorus-graphql). Analysis shows 85% is mechanical patterns: pagination boilerplate copied 9 times, thin delegation mutations (11 of 14 are `return XType.from(service.doThing(args))`), and DTO records with `from()` factory methods. This scales linearly with SPIs and carries copy-paste bug risk.

Separately, the dual-mode platform needs a callback mechanism so remote apps can implement platform SPIs over HTTP — enabling the "app-targets-scaffold" deployment model where thin apps register their SPI implementations with a running CaseHub server.

Both problems are solved by the same infrastructure: a build-time code generator that reads annotated SPI interfaces and produces GraphQL resolvers, DTOs, and callback proxies.

## Architecture

```
SPI Interface (api/)                    BUILD TIME                      RUNTIME
┌─────────────────────┐     ┌──────────────────────────┐    ┌─────────────────────┐
│ @McpDomain("engine")│     │  Generator (@BuildStep)  │    │  MCP Scanner        │
│ @PlatformQuery(...) │────▶│  Scans Jandex index      │    │  Scans @GraphQLApi  │
│ @PlatformMutation() │     │  Produces:               │    │  + @McpDomain       │
│ @CallbackEligible   │     │  ├─ GraphQL resolvers ───────▶│  Builds MCP model   │
│                     │     │  ├─ DTOs                  │    │                     │
└─────────────────────┘     │  └─ Callback proxies     │    └─────────────────────┘
                            │                          │
                            │  Also scans:             │    ┌─────────────────────┐
                            │  Hand-written resolvers  │    │  Callback Registry  │
                            │  (skips their methods)   │    │  Lease + heartbeat  │
                            └──────────────────────────┘    │  Credential mgmt    │
                                                            └─────────────────────┘
```

### Key properties

- **Sequential pipeline:** Generator (build time) → scanner (startup). Generator produces `@GraphQLApi @McpDomain` resolvers; the existing MCP scanner discovers them. No overlap.
- **Orthogonal concerns:** `@PlatformQuery`/`@PlatformMutation` → GraphQL generation. `@CallbackEligible` → callback proxy generation. An SPI can have either, both, or neither.
- **Hand-written override:** The generator scans for hand-written `@GraphQLApi` resolver classes at build time and skips methods that already exist. No collision, no special annotation on SPIs.
- **Module-boundary enforcement:** The generator validates via Jandex that every type exposed in a generated DTO belongs to an api/ module. Runtime-internal types produce a build-time error.

## New Artifacts

### In `platform-api` (pure Java, zero dependencies)

#### `@PlatformQuery` annotation

```java
package io.casehub.platform.api.mcp;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PlatformQuery {
    String value() default "";  // description — maps to @Description
}
```

#### `@PlatformMutation` annotation

```java
package io.casehub.platform.api.mcp;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PlatformMutation {
    String value() default "";  // description — maps to @Description
}
```

#### `@CallbackEligible` annotation

```java
package io.casehub.platform.api.mcp;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CallbackEligible {
    String name() default "";    // stable registration key (e.g., "worker-provisioner")
                                 // if empty, derived from simple class name in kebab-case
    boolean fanOut() default true; // true: invoke all registrations; false: single-impl (first registration wins)
}
```

Placed on extension SPI interfaces whose methods can be invoked remotely via callback. Interface-level — all methods on a `@CallbackEligible` interface are part of the callback contract.

All three annotations live in `io.casehub.platform.api.mcp` alongside the existing `@McpDomain` and `ModelEnricher`.

### Annotated SPI example

```java
// In engine-api (casehub-engine)
@McpDomain("engine")
public interface CaseHubRuntime {

    @PlatformMutation("Start a new case from a registered definition")
    UUID startCase(CaseDefinition def, Object inputData);

    @PlatformQuery("Query case context data at a path")
    Object query(UUID caseId, String path);

    @PlatformMutation("Suspend a running case")
    void suspendCase(UUID caseId);

    @PlatformMutation("Resume a suspended case")
    void resumeCase(UUID caseId);

    @PlatformMutation("Cancel a case")
    void cancelCase(UUID caseId);
}

// In engine-api — extension SPI
@CallbackEligible
public interface WorkerProvisioner {
    void provision(WorkerProvisionRequest request);
}
```

`@McpDomain` groups operations into domains for the MCP model. `@PlatformQuery`/`@PlatformMutation` mark which methods get GraphQL resolvers. `@CallbackEligible` marks which SPIs get callback proxies. These are independent concerns.

## Code Generator

### Module structure

GraphQL generation and callback proxy generation are in separate deployment modules — structurally independent, as the concerns are orthogonal. Shared Jandex scanning utilities live in a common module.

```
platform/
├── graphql/                            # EXISTING — shared GraphQL types
│   └── src/main/java/
│       └── io/casehub/platform/graphql/
│           ├── PageInput.java          # (existing)
│           ├── PageInfo.java           # (existing)
│           ├── PaginationHelper.java   # NEW: paginate(list, page, mapper)
│           └── InputMapper.java        # NEW: reflective input-to-builder
│
├── graphql-generator-deployment/       # deployment — GraphQL @BuildStep
│   ├── pom.xml                         # casehub-platform-graphql-generator-deployment
│   └── src/
│       ├── main/java/
│       │   └── io/casehub/platform/graphql/generator/
│       │       ├── GraphQLServiceProcessor.java  # @BuildStep for resolvers + DTOs
│       │       ├── ResolverGenerator.java
│       │       ├── DtoGenerator.java
│       │       └── ModuleBoundaryValidator.java
│       └── test/java/
│
├── callback-generator-deployment/      # deployment — Callback @BuildStep
│   ├── pom.xml                         # casehub-platform-callback-generator-deployment
│   └── src/
│       ├── main/java/
│       │   └── io/casehub/platform/callback/generator/
│       │       ├── CallbackProxyProcessor.java   # @BuildStep for decorators
│       │       ├── CallbackDecoratorGenerator.java
│       │       └── SerializationValidator.java   # Jackson-safety check
│       └── test/java/
```

`PaginationHelper` and `InputMapper` go in the existing `graphql/` module alongside `PageInput`/`PageInfo` — they're GraphQL presentation utilities, not generator infrastructure.

### Generator flow

**GraphQL generator (GraphQLServiceProcessor — graphql-generator-deployment/):**

```
@BuildStep
1. Scan Jandex for all interfaces with @McpDomain
2. For each interface, collect methods with @PlatformQuery or @PlatformMutation
3. Scan Jandex for hand-written @GraphQLApi @McpDomain classes
   → build a Set<String> of (domain, methodName) pairs to skip
4. Group operations by @McpDomain value (domain)
5. For each domain:
   a. Generate query resolver class (methods not in skip set)
   b. Generate mutation resolver class (methods not in skip set)
   c. Generate DTO records for return types (if not already generated)
   d. Generate input records for complex parameter types
6. Validate: every DTO field type must be from an api/ module (Jandex check)
7. For @PlatformQuery/@PlatformMutation with empty value():
   omit @Description entirely (method name is self-documenting)
8. Write .java source files to build output directory
```

**Callback generator (CallbackProxyProcessor — callback-generator-deployment/):**

```
@BuildStep
1. Scan Jandex for all interfaces with @CallbackEligible
2. For each interface:
   a. Read fanOut attribute (true/false) → determines proxy control flow
   b. Read name attribute (or derive from class name in kebab-case)
   c. Validate: all parameter and return types are serialization-safe
      (records or Jackson-compatible constructors, from api/ modules)
   d. Generate @Decorator class implementing the SPI
   e. Generate dispatch endpoint (POST /casehub/callbacks/{spiName}/{method})
3. Write .java source files to build output directory
```

### Generated resolver example

From `CaseHubRuntime.suspendCase(UUID)`:

```java
// GENERATED — do not edit
@GraphQLApi
@McpDomain("engine")
@ApplicationScoped
public class GeneratedEngineMutationResolver {

    @Inject CaseHubRuntime caseHubRuntime;

    @Mutation
    @Description("Suspend a running case")
    public void suspendCase(UUID caseId) {
        caseHubRuntime.suspendCase(caseId);
    }

    @Mutation
    @Description("Resume a suspended case")
    public void resumeCase(UUID caseId) {
        caseHubRuntime.resumeCase(caseId);
    }

    // cancelCase, etc.
}
```

Methods like `startCase` that have a hand-written resolver (because they need definition lookup + repository fetch) are automatically skipped — the generator found `startCase` in `CaseMutationResolver.java` at build time.

### Generated DTO example

From `WorkItem` (returned by `WorkItemService` methods):

```java
// GENERATED — do not edit
@Type("WorkItem")
public record WorkItemType(
    UUID id,
    String tenancyId,
    String title,
    String description,
    String status,
    // ... all public fields/getters from WorkItem
    Instant createdAt,
    Instant updatedAt
) {
    public static WorkItemType from(WorkItem source) {
        return new WorkItemType(
            source.id(),
            source.tenancyId(),
            source.title(),
            source.description(),
            source.status() != null ? source.status().name() : null,
            // ... field mappings
            source.createdAt(),
            source.updatedAt()
        );
    }
}
```

### DTO generation rules

- Records: component names and types
- POJOs: public fields and getter-derived names
- Enum fields: `.name()` → String
- `Map<String, Object>` → `Json` (custom scalar, already in platform-graphql)
- `List<T>` → `List<GeneratedTType>`
- `UUID`, `Instant`, `String`, primitives → direct
- Nested complex types: generate a DTO for them too (one level of recursion; deeper nesting generates further DTOs)
- Module-boundary check: every type must come from an api/ module (Jandex verification at build time)

### Shared utilities (generator/ runtime module)

#### PaginationHelper

Eliminates the 9 copy-pasted pagination blocks:

```java
public final class PaginationHelper {

    public static <S, T> PageResult<T> paginate(
            List<S> all, PageInput page, Function<S, T> mapper) {
        int offset = page != null && page.offset() != null ? page.offset() : 0;
        int limit = page != null && page.limit() != null ? page.limit() : 20;
        int total = all.size();
        int end = Math.min(offset + limit, total);

        List<T> items = offset < total
                ? all.subList(offset, end).stream().map(mapper).toList()
                : List.of();

        return new PageResult<>(items, new PageInfo(
                end < total, offset > 0, total, null));
    }
}
```

Generated query resolvers use this automatically. Hand-written resolvers can use it too to replace their boilerplate.

## Callback Infrastructure

### Callback registration model

```java
package io.casehub.platform.api.callback;

public record CallbackRegistration(
    String id,                    // UUIDv7, server-assigned
    String spiName,               // stable name from @CallbackEligible(name=...)
                                  // or kebab-case of simple class name if not specified
    String callbackUrl,           // HTTP POST endpoint on the remote app
    String credentialRef,         // CredentialResolver reference for auth
    String tenancyId,             // tenant scope
    int timeoutMs,                // per-invocation timeout
    Map<String, String> metadata, // app name, version, etc.
    Instant registeredAt,
    Instant expiresAt,            // lease expiry (registeredAt + TTL)
    Instant lastHeartbeatAt
) {}
```

### CallbackRegistry SPI

```java
package io.casehub.platform.api.callback;

public interface CallbackRegistry {

    CallbackRegistration register(CallbackRegistrationRequest request);

    void deregister(String registrationId);

    void heartbeat(String registrationId);

    List<CallbackRegistration> findBySpi(String spiName, String tenancyId);

    Optional<CallbackRegistration> findById(String registrationId);
}
```

- **Register:** Idempotent upsert by (spiName, callbackUrl, tenancyId). Returns the registration with server-assigned ID and computed expiresAt.
- **Deregister:** Graceful shutdown path. Immediate removal.
- **Heartbeat:** Renews the lease — extends expiresAt by TTL. Called at 1/3 TTL with jitter.
- **findBySpi:** Returns all active (non-expired) registrations for an SPI + tenant.

### Lease lifecycle

```
App startup                                    App shutdown
    │                                              │
    ▼                                              ▼
 register(spi, url, ttl=300s)                deregister(id)
    │                                              │
    ├── heartbeat at ~100s ──┐                     └── immediate removal
    ├── heartbeat at ~200s ──┤
    └── ...                  │
                             └── renews expiresAt each time

If app crashes (no deregister, no heartbeat):
    └── lease expires after 300s
        └── reaper @Scheduled purges expired registrations
```

**TTL defaults:** 5-minute TTL, heartbeat at ~100s (1/3 TTL) with ±15s jitter.

**Lease reaper:** `@Scheduled` every 60 seconds. Purges registrations where `expiresAt < now()`. The reaper is a storage cleanup mechanism, not a correctness mechanism — `findBySpi` always filters by `expiresAt > now()` at query time, so expired registrations are never returned regardless of reaper timing.

### CallbackInvoker

```java
package io.casehub.platform.callback;

@ApplicationScoped
public class CallbackInvoker {

    @Inject PolicyEnforcer policyEnforcer;
    @Inject CredentialResolver credentialResolver;
    @Inject WorkerCredentialStore workerCredentialStore;
    @Inject ObjectMapper objectMapper;

    public <T> T invoke(CallbackRegistration registration,
                        String methodName,
                        Object[] args,
                        Class<T> returnType) {
        // 1. Mint a WorkerCredential for the invocation
        // 2. Resolve outbound credentials via CredentialResolver
        // 3. Serialize args to JSON
        // 4. HTTP POST to registration.callbackUrl()/{methodName}
        //    with WorkerCredential in Authorization header
        // 5. Retry via PolicyEnforcer (3x exponential backoff with jitter)
        // 6. Circuit breaker: after 5 consecutive failures,
        //    mark registration as unhealthy (separate from lease expiry)
        // 7. Deserialize response to returnType
    }
}
```

Uses existing platform infrastructure:
- `PolicyEnforcer` + `ExecutionPolicy` for retry/timeout
- `CredentialResolver` for outbound credentials to the remote app
- `WorkerCredentialStore` for identity propagation (mints scoped credential per invocation)
- `BackoffStrategy.EXPONENTIAL_WITH_JITTER` for retry backoff

**Circuit breaking** belongs in `governance/` as a `CircuitBreakerPolicy` extension of `ExecutionPolicy` — not ad-hoc in `callback/`. The `CallbackInvoker` uses `PolicyEnforcer` for the complete execution envelope (retry + timeout + circuit breaker). Other outbound infrastructure (agent providers, stream processors, endpoint connectors) can reuse the same circuit breaker. Per-registration circuit state: after 5 consecutive failures, the circuit opens and invocations are short-circuited for a configurable recovery window. This is separate from lease expiry — a healthy lease with a broken circuit means the app is reachable but failing.

### Generated callback decorator example

The callback proxy uses CDI `@Decorator` — not `@ApplicationScoped`, not `@Alternative`. This follows the `agent-gate` precedent (decorator wraps whatever implementation wins the CDI priority contest). The decorator checks the callback registry first; if registrations exist, it routes to the remote app. If not, it delegates to the wrapped bean (which could be the `@DefaultBean` no-op or a local `@ApplicationScoped` implementation).

From `@CallbackEligible(name = "worker-provisioner") WorkerProvisioner`:

```java
// GENERATED — do not edit
@Decorator
@Priority(Interceptor.Priority.APPLICATION + 100)
public class CallbackWorkerProvisionerDecorator implements WorkerProvisioner {

    @Inject @Delegate WorkerProvisioner delegate;
    @Inject CallbackRegistry callbackRegistry;
    @Inject CallbackInvoker invoker;
    @Inject CurrentPrincipal currentPrincipal;

    @Override
    public void provision(WorkerProvisionRequest request) {
        String tenancyId = currentPrincipal.tenancyId();
        List<CallbackRegistration> registrations =
            callbackRegistry.findBySpi("worker-provisioner", tenancyId);

        if (registrations.isEmpty()) {
            delegate.provision(request);  // fall through to local impl or no-op
            return;
        }

        for (CallbackRegistration reg : registrations) {
            invoker.invoke(reg, "provision",
                new Object[]{request}, void.class);
        }
    }
}
```

**CDI displacement ladder — three scenarios all work correctly:**

| Scenario | What runs |
|----------|-----------|
| No local impl + no callbacks | Decorator → delegate (= `@DefaultBean` no-op) |
| No local impl + callbacks registered | Decorator → remote invocation via `CallbackInvoker` |
| Local impl exists (aggregate install) | Decorator → delegate (= local `@ApplicationScoped` impl) unless callbacks also registered |
| Local impl + callbacks registered | Decorator → remote invocation (decorator runs first at APPLICATION+100) |

The decorator priority `APPLICATION + 100` is above the `agent-gate` decorator (`APPLICATION = 2000`... wait, that's the same value). Specifically: `APPLICATION + 100 = 2100`, which runs after `agent-gate` at `APPLICATION = 2000`. The callback decorator is closer to the bean than the rate limiter.

### Fan-out vs single-impl semantics

The `@CallbackEligible` annotation's `fanOut` attribute determines the generated proxy's control flow:

**`fanOut = true` (default) — invoke all registrations:**

```java
// void methods: invoke all, ignore results
for (CallbackRegistration reg : registrations) {
    invoker.invoke(reg, "provision", args, void.class);
}

// return-value methods: invoke all, return first non-null result
for (CallbackRegistration reg : registrations) {
    T result = invoker.invoke(reg, "selectWorker", args, returnType);
    if (result != null) return result;
}
return delegate.selectWorker(request); // fallback to local
```

**`fanOut = false` — single implementation, first registration wins:**

```java
// Use first active registration only
CallbackRegistration reg = registrations.get(0);
return invoker.invoke(reg, methodName, args, returnType);
```

When `fanOut = false` and multiple apps register for the same SPI+tenant, `CallbackRegistry.register()` logs a warning but accepts the registration. `findBySpi` returns registrations ordered by `registeredAt` — first registered wins. This is explicit and predictable.

**SPI-specific guidance:**

| SPI | fanOut | Rationale |
|-----|--------|-----------|
| `WorkerProvisioner` | true | Multiple apps may provision different worker types |
| `ActionRiskClassifier` | true | Most-restrictive-wins across remote classifiers |
| `SlaBreachPolicy` | false | Single policy per tenant |
| `WorkerSelectionStrategy` | false | Single selection strategy per tenant |

### Identity propagation

When the platform invokes a callback (platform → remote app):

1. `CallbackInvoker` mints a `WorkerCredential` via `WorkerCredentialStore`:
   - `actorId` = original caller's identity from `CurrentPrincipal`
   - `resourceId` = SPI being invoked (e.g., `ResourceId.of("spi", "WorkerProvisioner")`)
   - `tenancyId` = caller's tenant
   - `actions` = the method being called
   - `expiresAt` = short-lived (30s)

2. The credential token is sent in the `Authorization` header of the callback HTTP request.

3. The remote app validates via `acl-worker`'s `WorkerCredentialFilter` — same infrastructure used for worker-to-platform requests.

### Callback module structure

```
platform/
├── callback-api/                       # pure Java — records + SPI
│   ├── pom.xml                         # casehub-platform-callback-api
│   └── src/main/java/
│       └── io/casehub/platform/api/callback/
│           ├── CallbackRegistration.java
│           ├── CallbackRegistrationRequest.java
│           ├── CallbackRegistry.java
│           ├── CallbackRegistered.java         # CDI event
│           └── CallbackDeregistered.java       # CDI event
│
├── callback-inmem/                     # @Alternative @Priority(100) volatile
│   ├── pom.xml                         # casehub-platform-callback-inmem
│   └── src/main/java/
│       └── InMemoryCallbackRegistry.java
│
├── callback-jpa/                       # @ApplicationScoped JPA-backed
│   ├── pom.xml                         # casehub-platform-callback-jpa
│   └── src/main/java/
│       └── JpaCallbackRegistry.java
│
├── callback/                           # runtime — invoker + lease reaper
│   ├── pom.xml                         # casehub-platform-callback
│   └── src/main/java/
│       └── io/casehub/platform/callback/
│           ├── CallbackInvoker.java
│           └── LeaseReaper.java               # @Scheduled — purges expired
```

This follows existing platform module patterns:
- SPI in `callback-api/` (pure Java, zero deps)
- `@DefaultBean` no-op in `platform/` (the core module)
- In-memory `@Alternative @Priority(100)` for tests
- JPA `@ApplicationScoped` for production
- Runtime logic in `callback/`

## Integration with existing infrastructure

### MCP model

No changes to the `mcp/` module. Generated resolvers carry `@GraphQLApi @McpDomain` — the scanner discovers them at startup. The MCP model automatically reflects generated operations alongside hand-written ones.

### Endpoint registry

Callback registrations are independent of `EndpointRegistry`. Endpoints describe external systems the platform connects TO (Kafka, HTTP, Camel). Callbacks describe remote apps that implement platform SPIs. Different lifecycle, different semantics.

### Existing GraphQL modules

The 4 hand-written GraphQL modules (engine-graphql, work-graphql, ledger-graphql, qhorus-graphql) continue to work. The generator produces additional resolver classes for methods not covered by hand-written resolvers. Over time, hand-written resolvers can be replaced by generated ones as the bespoke 15% is addressed.

The existing hand-written resolvers can also adopt the shared utilities (PaginationHelper) to reduce their boilerplate independently of the generator.

## Deployment models

### Aggregate install (current — devtown, clinical, aml)

Apps bundle everything as compile deps. Generated resolvers and callback proxies are on the classpath. If no remote callbacks are registered, callback proxies behave as no-ops (same as @DefaultBean). No change to existing behaviour.

### App-targets-scaffold (new)

A thin app runs as a separate service:

1. App includes `casehub-platform-callback-api` + its SPI implementations
2. At startup, `CallbackAutoRegistrar` (@Startup bean) discovers local `@CallbackEligible` SPI implementations via CDI
3. Auto-registrar calls scaffold's callback registration endpoint for each SPI
4. Scaffold's generated callback proxies now route to the app
5. App heartbeats at 1/3 TTL with jitter
6. On shutdown, app deregisters

The auto-registrar (casehubio/platform#231) is a separate issue in the queue.

## Security model

- **Registration endpoint:** Protected by standard OIDC/JWT authentication. Only authenticated principals can register callbacks.
- **Callback invocation:** Platform mints a scoped `WorkerCredential` per invocation. Remote app validates via `WorkerCredentialFilter`. The credential carries actorId, resourceId, tenancyId, permitted actions, and a short expiry (30s).
- **Outbound auth:** `credentialRef` on the registration resolves via `CredentialResolver` to obtain the remote app's API key or bearer token. This authenticates the platform to the remote app.
- **Idempotency:** Every callback invocation includes a unique invocation ID (UUIDv7) in the `X-CaseHub-Invocation-Id` header. Remote apps should deduplicate.
- **Payload signing:** Not in v1. HMAC-SHA256 signing can be added when the callback protocol matures — requires specifying secret provisioning, header format, and verification on the remote side. WorkerCredential + TLS provides sufficient security for the initial deployment model.

## Testing strategy

### Generator tests (generator-deployment/)

- Sample SPI interfaces with `@PlatformQuery`/`@PlatformMutation` → verify generated source
- Sample SPI with `@CallbackEligible` → verify generated callback proxy
- Hand-written resolver exists → verify generator skips those methods
- Return type from runtime/ module → verify build-time error
- Generated resolver + MCP scanner → verify operations appear in model

### Callback tests

- `CallbackRegistryTest` — register, heartbeat, deregister, lease expiry
- `CallbackInvokerTest` — HTTP POST, retry, circuit breaker (WireMock)
- `LeaseReaperTest` — expired registrations are purged
- Integration test: register callback → invoke SPI → callback received → response returned

### Existing test preservation

All 14 existing MCP integration tests continue to pass — the generator and scanner are additive, not replacing.

## Decisions

See `decisions.md` in this directory for the full decision log (D8–D16).

## Known limitations

- **Subscription generation:** `@Subscription` methods are not generated in v1. They involve CDI event bridges and SSE infrastructure that requires hand-written wiring. The generator handles `@PlatformQuery` and `@PlatformMutation` only.
- **REST generation:** The generator does not produce REST endpoints. GraphQL + MCP are the two presentation surfaces. REST can be added as a future generator output if needed.
- **Dual deserialization:** Generated resolvers use SmallRye's native GraphQL deserialization for incoming parameters. Callback proxies use Jackson for HTTP serialization. Edge cases (custom scalar coercion, null vs absent) may diverge. Same limitation as the MCP dispatcher (see MCP spec, Known Limitations).

## Out of scope

- **CallbackAutoRegistrar** — client-side auto-registration (@Startup CDI bean in remote apps). Tracked by casehubio/platform#231, separate issue in the queue.
- **Engine callback adapters** — WorkerProvisioner + ActionRiskClassifier callback adapters in casehub-engine. Tracked by casehubio/engine#893.
- **Work callback adapters** — SlaBreachPolicy + WorkerSelectionStrategy callback adapters in casehub-work. Tracked by casehubio/work#348.
- **Retiring hand-written REST modules** — the generator enables this but it's a separate phase.
- **Scaffold deployment of apps** — scaffold managing app lifecycle. Future work beyond the callback mechanism.
