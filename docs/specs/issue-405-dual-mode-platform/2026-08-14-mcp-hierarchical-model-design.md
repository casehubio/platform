# MCP Hierarchical Model — Design Spec

**Issue:** casehubio/platform#228
**Parent epic:** casehubio/parent#405
**Protocol:** casehubio/parent#407 (hierarchical MCP model)
**Date:** 2026-08-14

## Problem

CaseHub's foundation repos each expose MCP tools independently: qhorus has 37 tools, connectors 6, IoT 3, openclaw 8, drafthouse 10. When composed in a unified CaseHub server (scaffold), an agent faces 60+ tool definitions. This wastes context window on descriptions the agent won't use and degrades tool selection accuracy at scale.

The hierarchical MCP model (protocol #407) solves this: two fixed tools (`casehub_model` + `casehub_action`) replace N per-domain tools with a navigable operation catalog. The agent reads the model to understand what's available, then calls `casehub_action` to execute.

## Architecture

The MCP model is derived from existing MicroProfile GraphQL annotations on hand-written resolver classes. No code generator. No new annotation model beyond one domain-grouping marker. The GraphQL schema and MCP model describe the same remote operations — one source, two presentation protocols.

```
GraphQL resolvers            @McpDomain           ModelEnricher
(@Query, @Mutation,      →   groups resolvers  →  contributes
 @Subscription,              into domains at      live state
 @Description)               scan time            per domain

              ↓ Jandex scan at startup ↓

         ┌──────────────────────────────────┐
         │  GraphQLModelScanner             │
         │  - discovers @GraphQLApi classes  │
         │  - groups by @McpDomain          │
         │  - reads @Query/@Mutation        │
         │  - reads @Subscription (metadata)│
         │  - reflects parameter types      │
         │  - merges ModelEnricher state     │
         └──────────────┬───────────────────┘
                        ↓
         ┌──────────────────────────────────┐
         │  @McpServer("casehub")           │
         │  casehub_model  → navigate       │
         │  casehub_action → execute        │
         │  (ReflectiveOperationDispatcher) │
         └──────────────────────────────────┘
```

### Key properties

- **Zero-drift:** The MCP operation catalog is derived from GraphQL annotations. A new `@Query` method on a resolver automatically appears in both the GraphQL schema and the MCP model.
- **Annotation-filtered dispatch:** Only methods annotated with `@Query` or `@Mutation` are registered at scan time. `casehub_action` cannot invoke arbitrary methods — it dispatches only to registered operations.
- **Complementary surfaces:** Per-domain MCP tools (`@McpServer("qhorus")`, etc.) serve embedded mode. The hierarchical model serves centralized mode. Both coexist.
- **Event discovery, not delivery:** `@Subscription` methods appear in the model as non-callable event metadata pointing to Qhorus channels. Event delivery uses existing Qhorus `check_messages` infrastructure (cursor-based polling with APPEND/COLLECT/BARRIER/EPHEMERAL semantics).

## New Artifacts

### In `platform-api` (pure Java, zero dependencies)

#### `@McpDomain` annotation

```java
package io.casehub.platform.api.mcp;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpDomain {
    String value();
}
```

Placed on GraphQL resolver classes to group them into domains:

```java
@McpDomain("engine")
@GraphQLApi
@ApplicationScoped
public class CaseQueryResolver { ... }

@McpDomain("engine")
@GraphQLApi
@ApplicationScoped
public class CaseMutationResolver { ... }
```

Multiple resolver classes with the same `@McpDomain` value are merged into one domain in the model.

#### `ModelEnricher` interface

```java
package io.casehub.platform.api.mcp;

public interface ModelEnricher {
    default String summary() { return ""; }
    default Map<String, Object> state() { return Map.of(); }
}
```

Optional CDI bean. Annotate with `@McpDomain` to bind to a domain — same annotation used on resolver classes (one identity mechanism, no string mismatch risk):

```java
@McpDomain("engine")
@ApplicationScoped
public class EngineModelEnricher implements ModelEnricher {
    @Inject CaseService caseService;
    public String summary() { return "Case orchestration engine"; }
    public Map<String, Object> state() {
        return Map.of("activeCases", caseService.countActive());
    }
}
```

The scanner matches enrichers to domains by scanning for `ModelEnricher` beans that carry `@McpDomain`. If an enricher's `@McpDomain` doesn't match any resolver domain, the scanner warns at startup. If no enricher exists for a domain, the model shows operations without summary or state.

### New module: `mcp/` in casehub-platform

**Artifact:** `casehub-platform-mcp`

**Dependencies:**
- `casehub-platform-api` (for @McpDomain, ModelEnricher)
- `io.quarkiverse.mcp:quarkus-mcp-server-sse` (for @McpServer, @Tool, @ToolArg, @WrapBusinessError)
- `org.eclipse.microprofile.graphql:microprofile-graphql-api` (provided scope — for scanning @Query, @Mutation, @Subscription, @Description)
- `com.fasterxml.jackson.core:jackson-databind` (for parameter deserialization and result serialization)

**Key classes:**

| Class | Purpose |
|-------|---------|
| `CaseHubMcpTools` | `@McpServer("casehub")` bean with `casehub_model` and `casehub_action` tool methods |
| `GraphQLModelScanner` | `@Startup` CDI bean — Jandex scan of @GraphQLApi + @McpDomain classes, builds operation catalog |
| `ModelRegistry` | Internal catalog: domains → operations + event metadata + enricher state |
| `ReflectiveOperationDispatcher` | Annotation-filtered `Method.invoke()` dispatch with Jackson deserialization |
| `DomainModel` | Internal record: domain name, summary, operations, events, state |
| `OperationDescriptor` | Internal record: name, type (query/mutation), summary, parameters, return type |
| `EventDescriptor` | Internal record: event type, summary, channel hint |
| `ParameterDescriptor` | Internal record: name, type name, required flag, field map for complex types |

## Tool Design

### `casehub_model` — navigate the operation catalog

**Parameters:**
- `domain` (optional String) — if omitted, returns domain list. If provided, returns domain detail.

**Tier 0 response (no domain param):**

```json
{
  "domains": [
    {
      "name": "engine",
      "summary": "Case orchestration engine",
      "operationCount": 10,
      "eventCount": 2,
      "state": { "activeCases": 5, "definitions": 3 }
    },
    {
      "name": "work",
      "summary": "Human task lifecycle",
      "operationCount": 15,
      "eventCount": 2,
      "state": { "pendingItems": 12 }
    },
    {
      "name": "ledger",
      "summary": "Audit ledger and trust scoring",
      "operationCount": 8
    },
    {
      "name": "qhorus",
      "summary": "Agent communication mesh",
      "operationCount": 10,
      "eventCount": 2,
      "state": { "activeChannels": 3 }
    }
  ]
}
```

Domains without a ModelEnricher have no `summary` or `state` fields.

**Tier 1 response (with domain param):**

```json
{
  "domain": "engine",
  "summary": "Case orchestration engine",
  "state": { "activeCases": 5, "definitions": 3 },
  "queries": [
    {
      "name": "caseById",
      "summary": "Retrieve a case by its unique identifier",
      "params": [
        { "name": "caseId", "type": "UUID", "required": true }
      ],
      "returns": "CaseInstance"
    },
    {
      "name": "cases",
      "summary": "List cases with optional filtering",
      "params": [
        { "name": "filter", "type": "CaseFilterInput",
          "fields": { "status": "String", "definitionName": "String", "actorId": "String" } },
        { "name": "page", "type": "PageInput",
          "fields": { "offset": "Integer", "limit": "Integer", "cursor": "String" } }
      ],
      "returns": "CasePage"
    }
  ],
  "mutations": [
    {
      "name": "startCase",
      "summary": "Start a new case instance from a case definition",
      "params": [
        { "name": "input", "type": "StartCaseInput", "required": true,
          "fields": { "namespace": "String", "name": "String", "context": "JSON" } }
      ],
      "returns": "CaseInstance"
    }
  ],
  "events": [
    {
      "name": "caseLifecycle",
      "summary": "Case state changes (created, suspended, resumed, cancelled, completed)",
      "params": [{ "name": "caseId", "type": "UUID", "required": true }],
      "delivery": "qhorus",
      "channel": "engine-case-lifecycle"
    }
  ]
}
```

### `casehub_action` — execute an operation

**Parameters:**
- `domain` (required String) — the domain name
- `operation` (required String) — the operation name
- `params` (optional JSON object) — operation parameters

**Request:**

```json
casehub_action(
  domain = "engine",
  operation = "startCase",
  params = {
    "input": {
      "namespace": "io.casehub.devtown",
      "name": "pr-review",
      "context": { "prId": 123 }
    }
  }
)
```

**Response (success):**

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "ACTIVE",
  "definition": {
    "namespace": "io.casehub.devtown",
    "name": "pr-review"
  }
}
```

**Response (error — via @WrapBusinessError):**

Returned as `isError: true` MCP tool response with the exception message as text content. Same pattern as QhorusMcpTools.

### Dispatch flow

1. `casehub_action` receives domain + operation + params
2. `ModelRegistry.resolve(domain, operation)` → `OperationDescriptor` (includes the target `Method` and resolver bean type)
3. Validate: operation must be registered (annotation-filtered at scan time)
4. Obtain resolver bean as CDI proxy via `CDI.current().select(resolverType).get()` — this ensures CDI interceptors (@Transactional, @RolesAllowed, custom interceptors) fire correctly
5. Deserialize params: `ObjectMapper.convertValue()` maps JSON to method parameter types. ObjectMapper configured with `JavaTimeModule` and ISO-8601 date handling to align with SmallRye GraphQL's deserialization behavior
6. `Method.invoke(cdiProxy, deserializedArgs)` — executes the resolver method through the CDI proxy
6. Serialize result: `ObjectMapper.writeValueAsString()` → JSON response
7. Exceptions: `@WrapBusinessError({IllegalArgumentException.class, IllegalStateException.class})` converts business exceptions to `isError` responses

### Security model

- **Method scope filtering:** Only methods annotated with `@Query` or `@Mutation` are registered at scan time. The dispatcher resolves operations from the registry, never from method name alone. Lifecycle methods, utility methods, and inherited Object methods are never invocable.
- **Authorization:** Resolver beans are obtained as CDI proxies via `CDI.current().select()`, ensuring CDI interceptors (`@Transactional`, `@RolesAllowed`) fire on reflection dispatch. CaseHub resolvers additionally delegate to service interfaces that inject `CurrentPrincipal` and check ACL at the service level.
- **Input validation:** Jackson deserialization rejects malformed input before it reaches the resolver method. Type mismatches, missing required fields, and invalid enum values throw `JsonProcessingException`, caught and returned as `isError`.

## Scanner Implementation

### Startup scan flow

```
@Startup @ApplicationScoped
GraphQLModelScanner:
  1. Jandex: find all classes annotated with @McpDomain
  2. For each class:
     a. Read @McpDomain value → domain name
     b. Verify @GraphQLApi is also present (warn if missing)
     c. Scan methods for @Query, @Mutation, @Subscription
     d. For @Query/@Mutation: build OperationDescriptor
        - Method name → operation name
        - @Description value → summary (empty string if absent)
        - Method parameters → ParameterDescriptor list
          - Parameter name (from -parameters flag)
          - Parameter type → type name
          - Required: true unless Optional<T> or annotated @DefaultValue
          - Complex types: reflect public fields/getters → field map
        - Return type → return type name
     e. For @Subscription: build EventDescriptor (metadata only)
        - Method name → event name
        - @Description value → summary
        - Method parameters → ParameterDescriptor list
        - Channel hint: derived as {domain}-{methodNameToKebabCase}
          per platform channel naming convention (e.g., engine + caseLifecycle → "engine-case-lifecycle").
          Convention-only for v1.
  3. Group OperationDescriptors and EventDescriptors by domain name
  4. CDI Instance<ModelEnricher>: merge summary + state per domain
  5. Register assembled DomainModels in ModelRegistry
```

### Complex type field expansion

For parameters with non-primitive types (records, POJOs), the scanner reflects fields to provide a `fields` map:

```java
public record StartCaseInput(String namespace, String name, Map<String, Object> context) {}
→ fields: { "namespace": "String", "name": "String", "context": "JSON" }
```

Rules:
- Records: component names and types
- POJOs: public fields and getter-derived names
- `Map<String, Object>` → type "JSON"
- `List<T>` → type "List<T>"
- `UUID`, `Instant`, `String`, primitives → type name directly
- Nested complex types: one level of expansion (no recursive descent)

## Integration Points

### Existing per-domain MCP tools

Per-domain MCP tools (`@McpServer("qhorus")`, `@McpServer("iot")`, etc.) continue to serve embedded mode unchanged. The hierarchical model on `@McpServer("casehub")` serves centralized mode. They coexist as separate named MCP servers.

### Qhorus event delivery

Event metadata in the model includes channel hints. The agent uses Qhorus `check_messages` MCP tool for actual event consumption. The MCP model provides discovery; Qhorus provides delivery.

### Scaffold (unified CaseHub server)

Scaffold adds `casehub-platform-mcp` as a compile dependency. The scanner discovers all GraphQL resolver classes from all domain modules on the classpath. The model reflects exactly the domains deployed in that application.

## Testing Strategy

### Unit tests

- `GraphQLModelScannerTest` — scanner with mock `@GraphQLApi` + `@McpDomain` annotated classes, verifies operation catalog assembly, domain grouping, event metadata extraction
- `ReflectiveOperationDispatcherTest` — dispatch to mock resolver methods, parameter deserialization, error handling
- `ModelRegistryTest` — domain registration, lookup, enricher merging

### Integration tests

- `CaseHubMcpToolsTest` — `@QuarkusTest` with test resolver classes, verifies `casehub_model` tier 0 and tier 1 responses, `casehub_action` dispatch and response serialization
- Security: verify non-annotated methods on resolver classes are not invocable
- Error handling: verify malformed params return `isError`, verify business exceptions are wrapped

### Test resolver (for development before per-domain resolvers exist)

```java
@McpDomain("test")
@GraphQLApi
@ApplicationScoped
public class TestQueryResolver {
    @Query @Description("Echo the input back")
    public String echo(@Name("message") String message) { return message; }
    
    @Query @Description("Return a fixed greeting")
    public String hello() { return "Hello from CaseHub"; }
    
    public String notExposed() { return "should not be invocable"; }
}
```

## Module Structure

```
platform/
├── platform-api/
│   └── src/main/java/io/casehub/platform/api/mcp/
│       ├── McpDomain.java          (annotation)
│       └── ModelEnricher.java      (interface)
├── mcp/
│   ├── pom.xml
│   └── src/
│       ├── main/java/io/casehub/platform/mcp/
│       │   ├── CaseHubMcpTools.java
│       │   ├── GraphQLModelScanner.java
│       │   ├── ModelRegistry.java
│       │   ├── ReflectiveOperationDispatcher.java
│       │   ├── DomainModel.java
│       │   ├── OperationDescriptor.java
│       │   ├── EventDescriptor.java
│       │   └── ParameterDescriptor.java
│       └── test/java/io/casehub/platform/mcp/
│           ├── CaseHubMcpToolsTest.java
│           ├── GraphQLModelScannerTest.java
│           ├── ReflectiveOperationDispatcherTest.java
│           └── TestQueryResolver.java
└── pom.xml  (add mcp to <modules>)
```

## Decisions

See `decisions.md` in this directory for the full decision log (D1–D7).

## Known Limitations

- **Dual deserialization path:** `casehub_action` uses Jackson `ObjectMapper.convertValue()` for parameter deserialization, while GraphQL uses SmallRye's native deserialization. Edge cases (custom scalar coercion, null vs absent fields) may diverge. Mitigated by configuring Jackson with `JavaTimeModule` and ISO-8601 handling to align with SmallRye. Future option: dispatch through SmallRye GraphQL's programmatic execution API for true pipeline unification.

## Out of Scope

- **Code generator:** No Quarkus deployment extension. No build-time code generation. (D1)
- **Per-domain GraphQL resolvers:** Tracked by separate issues (#892, #347, #190, #394). The scanner activates when resolvers arrive.
- **Generation counter:** Not needed for v1. The model is cheap to regenerate. (D6)
- **Dynamic runtime registration:** Remote apps extending the model. Addressable with an internal registry when callback mechanism (Phase 3) needs it. (D3)
- **Subscription dispatch:** Events are not callable via `casehub_action`. Delivery via Qhorus. (D2)
