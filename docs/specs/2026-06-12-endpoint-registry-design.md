# Endpoint Registry Design

**Issue:** platform#73
**Date:** 2026-06-12
**Status:** Draft (revised — awaiting approval)

---

## Problem

Workers in `casehub-workers` (HTTP, Camel, etc.) and the planned `casehub-deployment`
desired-state domain need to reference external systems by name rather than hardcoding
connection details. Currently workers embed URLs and protocol config inline — making
configuration fragile, environment-specific, and unable to support multi-tenant deployments
where each tenant connects to their own instance of an external system.

---

## Design

### Module Structure

`EndpointRegistry` and all its value types live in `platform-api` — the same module as
`CaseMemoryStore`, `CurrentPrincipal`, and `PreferenceProvider`. No new SPI module is created.

The only new submodule added to `casehub-platform` is:

```
casehub-platform/
  endpoints-memory/   artifact: casehub-platform-endpoints-memory
                      depends on: casehub-platform-api
                      jakarta.enterprise.cdi-api as provided
                      contains: InMemoryEndpointRegistry @Alternative @Priority(100)

  platform-api/       gains: EndpointRegistry SPI + all value types
                      package: io.casehub.platform.api.endpoints

  platform/           gains: NoOpEndpointRegistry @DefaultBean
```

**Justification for placement in `platform-api`:** A separate `endpoints/` module would
produce no isolation benefit. `platform/` must carry `NoOpEndpointRegistry @DefaultBean`
to follow the platform invariant ("every SPI in platform-api gets a @DefaultBean
implementation in platform"). Since `platform/` already depends on `platform-api`, the
SPI types are available with no new dependency edges. A separate module would become a
universal transitive dependency of every consumer (via `platform/`) — identical reach to
`platform-api`, with one extra level of indirection. The only justified exception in this
codebase is `agent-api/`, which carries Mutiny — a dependency `platform-api` cannot have.
`EndpointRegistry` is pure Java; there is no such constraint.

**CDI ladder:**

| Tier | Bean | Annotation | Module |
|------|------|-----------|--------|
| 1 — No-op default | `NoOpEndpointRegistry` | `@DefaultBean` | `platform/` |
| 4 — In-memory | `InMemoryEndpointRegistry` | `@Alternative @Priority(100)` | `endpoints-memory/` |

Tier 2 (JPA) and Tier 3 (NoSQL) are deferred. Priority ladder follows the documented
`persistence-backend-cdi-priority.md` (PP-20260522-0cfa30) convention. Priority 100 for
in-memory leaves headroom for future production backends at Tier 2 and Tier 3.

### Package

`io.casehub.platform.api.endpoints`

### Value Types

**`EndpointProtocol` enum**

```java
public enum EndpointProtocol { HTTP, GRPC, KAFKA, MCP, CAMEL, QHORUS }
```

Closed set — adding a protocol requires an enum change. That is intentional: it forces every
caller to name the protocol explicitly and eliminates silent inconsistencies that break
`discover()` filters (e.g. "http" vs "HTTP"). HTTP subsumes HTTPS — the scheme
(`http://` vs `https://`) is carried in the URL inside `properties`, not in the protocol
type.

**Not all values are wire protocols.** `HTTP`, `GRPC`, and `KAFKA` name the underlying
transport. `CAMEL` and `QHORUS` name the invocation mechanism: `CAMEL` means the endpoint
is invoked via an Apache Camel route (the route's own transport is an implementation
detail); `QHORUS` means the endpoint is invoked via the Qhorus channel dispatch API. The
distinction matters for workers choosing which dispatcher to use — a `CAMEL` endpoint
routes through `casehub-workers-camel` regardless of what transport the Camel route itself
uses. `MCP` is the Anthropic Model Context Protocol, a protocol specification defining tool
invocation over a defined wire format; it is treated as a protocol alongside `HTTP` and
`GRPC`.

**`EndpointType` enum**

```java
public enum EndpointType { SYSTEM, SERVICE, WORKER, AGENT }
```

| Value | Example path | What it identifies |
|-------|-------------|-------------------|
| SYSTEM | `external/salesforce/prod` | An external third-party system |
| SERVICE | `casehubio/qhorus/api` | An internal platform service |
| WORKER | `workers/camel/lead-enrichment` | A data processing worker |
| AGENT | `agents/claude:analyst@v1` | An AI agent |

**`EndpointType` and `tenancyId` are orthogonal.** `EndpointType` classifies *what* the
endpoint is; `tenancyId` controls *who can see it*. A `SERVICE` endpoint may be
platform-global (`TenancyConstants.PLATFORM_TENANT_ID`) or tenant-specific — e.g., a
tenant with a private Qhorus deployment registers `type=SERVICE` with their own
`tenancyId`. By convention most `SERVICE` endpoints are platform-global; this is not a
constraint enforced by the type.

`CASE` and `HUMAN` are excluded. An endpoint registry describes how to connect to a
system via a protocol. A case instance is an internal domain concept, not a connection
target. A human is an actor identity reachable via connectors (Slack, email) or work SPIs
— not a protocol endpoint. Including them conflates connection config with entity
addressing.

**`EndpointCapability` enum**

```java
public enum EndpointCapability { SEND, RECEIVE, QUERY, DISPATCH }
```

| Value | Meaning |
|-------|---------|
| `SEND` | Caller can push data to this endpoint (Kafka produce, HTTP POST, Qhorus dispatch) — fire-and-forget; no response expected |
| `RECEIVE` | Endpoint can push data to the caller (webhook delivery, Kafka consume, SSE) |
| `QUERY` | Caller can issue a read request and receive a synchronous response (HTTP GET, MCP tool call returning data) |
| `DISPATCH` | Caller can invoke an operation and receive a response or acknowledgement (MCP tool call, Camel request-reply, agent invocation) |

**Declared capabilities reflect the endpoint's nominal contract, not the calling pattern.**
Whether a Camel route is one-way or request-reply depends on the route configuration;
whether an HTTP POST is asynchronous (202 Accepted) or synchronous (200 with body) depends
on the service. A Camel route that returns a result declares `DISPATCH`; a route that
accepts and processes asynchronously declares `SEND`. The descriptor declares intent — the
caller must honour it.

An endpoint may declare multiple capabilities (e.g. a Kafka topic is `SEND + RECEIVE`; an
MCP server is `QUERY + DISPATCH`).

**`EndpointDescriptor` record**

```java
public record EndpointDescriptor(
    Path path,
    String tenancyId,                   // TenancyConstants.PLATFORM_TENANT_ID for platform-global
    EndpointType type,
    EndpointProtocol protocol,
    Map<String, String> properties,     // protocol-specific config — URLs, topic names. No secrets inline.
    String credentialRef,               // nullable — names the credential in the secrets backend
    Set<EndpointCapability> capabilities
) {
    public EndpointDescriptor {
        Objects.requireNonNull(path,         "path");
        Objects.requireNonNull(tenancyId,    "tenancyId");
        Objects.requireNonNull(type,         "type");
        Objects.requireNonNull(protocol,     "protocol");
        Objects.requireNonNull(properties,   "properties");
        Objects.requireNonNull(capabilities, "capabilities");
        properties   = Map.copyOf(properties);
        capabilities = Set.copyOf(capabilities);
    }
}
```

Field order: key components (`path`, `tenancyId`) lead, followed by descriptor fields, then
payload and metadata. Matches the `MemoryInput` pattern (`entityId, domain, tenantId` before
`caseId, text, attributes`). All fields except `credentialRef` are required. `properties`
and `capabilities` follow the `MemoryInput.attributes` pattern: require non-null, then
defensively copy. Callers pass `Map.of()` and `Set.of()` explicitly for empty collections.

**`EndpointQuery` record**

```java
public record EndpointQuery(
    String tenancyId,                           // required — always first
    EndpointType type,                          // null = all types
    EndpointProtocol protocol,                  // null = all protocols
    Set<EndpointCapability> requiredCapabilities  // empty = no capability filter
) {
    public EndpointQuery {
        Objects.requireNonNull(tenancyId,             "tenancyId");
        Objects.requireNonNull(requiredCapabilities,  "requiredCapabilities");
        requiredCapabilities = Set.copyOf(requiredCapabilities);
    }
}
```

**`EndpointPropertyKeys` constants**

```java
/**
 * Reserved cross-protocol property keys for {@link EndpointDescriptor#properties()}.
 *
 * <p>Platform-reserved keys use <b>kebab-case</b>. Consumer modules should follow the
 * same convention for protocol-specific extensions to avoid collisions.
 *
 * <p>These keys are conventions, not enforced constraints. Their purpose is to allow
 * workers and callers from different modules to read endpoint properties registered by
 * another module without independent key negotiation. The <em>values</em> are
 * endpoint-specific and defined by each registrar.
 */
public final class EndpointPropertyKeys {

    /**
     * The base URL of the endpoint.
     * Applies to: HTTP (service root), GRPC (host:port or grpc://... URI), MCP (server
     * base URL), CAMEL (Camel endpoint URI — may be any valid Camel endpoint expression),
     * QHORUS (REST base URL).
     */
    public static final String URL = "url";

    /**
     * The Kafka topic name.
     * Applies to: KAFKA only.
     * A producer registering SEND and a consumer registering RECEIVE for the same logical
     * endpoint must use this key to interoperate.
     */
    public static final String TOPIC = "topic";

    private EndpointPropertyKeys() {}
}
```

These are the two keys whose values must cross module boundaries: a worker connecting to a
`KAFKA` endpoint registered by another module needs the topic name; a worker invoking an
`HTTP` or `MCP` endpoint needs the URL. Bootstrap servers, TLS config, route IDs, and
other protocol-specific properties are deployment-local and use module-defined keys.

### `EndpointRegistry` SPI

```java
public interface EndpointRegistry {

    /**
     * Register or update an endpoint. (path, tenancyId) is the unique key — upsert semantics.
     * A single descriptor per (path, tenancyId) declares the complete capability set.
     * Multiple registrations for the same key are resolved by last write — there is no
     * merge semantics. Two beans registering the same path and tenancyId with different
     * capability subsets will silently overwrite each other.
     */
    void register(EndpointDescriptor endpoint);

    /**
     * Resolve an endpoint by path for the given tenant.
     * Returns the tenant-specific endpoint if one exists, otherwise falls back to any
     * platform-global endpoint (tenancyId == TenancyConstants.PLATFORM_TENANT_ID).
     * Tenant-specific takes precedence — allows tenants to override platform defaults.
     *
     * Cross-tenant admin resolution is the caller's responsibility — pass the target
     * tenancyId directly. The SPI has no principal awareness on reads.
     */
    Optional<EndpointDescriptor> resolve(Path path, String tenancyId);

    /**
     * Discover endpoints matching the query criteria.
     * Always includes platform-global endpoints alongside the caller's tenant endpoints.
     * Returns both tenant-specific and platform-global matches — no override semantics;
     * use resolve() when a single authoritative result is required.
     * See "discover() predicate" below for the complete filter conjunction.
     *
     * The result list is unordered. Implementations must not guarantee a specific ordering,
     * and callers must not depend on one.
     */
    List<EndpointDescriptor> discover(EndpointQuery query);

    /** Deregister by (path, tenancyId). No-op if not found. */
    void deregister(Path path, String tenancyId);
}
```

### Tenant Isolation Rules

Enforced by every implementation:

- **`resolve(path, tenancyId)` — priority lookup:** first check for a descriptor with
  `descriptor.tenancyId().equals(tenancyId)`; if found, return it. If not found, check for
  a descriptor with `descriptor.tenancyId().equals(PLATFORM_TENANT_ID)`; return it if
  found. Otherwise return empty. Tenant-specific takes precedence over platform-global —
  this allows tenants to override platform defaults for the same path.
- **`discover(query)` — both returned:** includes descriptors matching either
  `descriptor.tenancyId == query.tenancyId` OR `descriptor.tenancyId == PLATFORM_TENANT_ID`.
  Override semantics (tenant wins over platform-global) apply only to the single-result
  `resolve()` path. `discover()` is intentionally exhaustive — callers choose among results.
- Platform-global endpoints are registered with `TenancyConstants.PLATFORM_TENANT_ID`
- Tenant A's endpoints are never returned for tenant B's queries

**Note on `PLATFORM_TENANT_ID`:** this sentinel was introduced in C3 for "cross-tenant
super-admin principals" (identity context). Using it as `tenancyId` for platform-global
endpoints is semantically consistent — both uses mean "owned by the platform, not any
specific tenant." The `TenancyConstants` Javadoc must be updated to document both uses.

### `discover()` Complete Predicate

The authoritative match condition for every `EndpointRegistry` implementation. A
descriptor is included in the result of `discover(query)` if and only if all four
conditions hold:

```
(descriptor.tenancyId == query.tenancyId  OR  descriptor.tenancyId == PLATFORM_TENANT_ID)
AND (query.type     == null  OR  descriptor.type     == query.type)
AND (query.protocol == null  OR  descriptor.protocol == query.protocol)
AND  descriptor.capabilities.containsAll(query.requiredCapabilities)
```

`requiredCapabilities` being empty satisfies the last condition for all descriptors (every
set `containsAll` of the empty set). Both JPA backends and the in-memory adapter must
implement exactly this predicate — deviations are bugs, not implementation choices.

### Write Authorization Model

`EndpointRegistry` enforces no write authorization. `register()` and `deregister()` are
neutral operations — the SPI does not inject `CurrentPrincipal` and performs no tenant
assertion on writes.

**Rationale:** The initial population model (see below) is application-level `@Startup
@PostConstruct` code — no request-scoped principal is active at startup, and startup code
is implicitly trusted. An `EndpointPermissions.assertTenant()` utility is the right pattern
for dynamic runtime registration (e.g., from `casehub-deployment` where a request context
exists), but is premature here.

**Contract:** Callers are accountable for ensuring `descriptor.tenancyId()` matches their
authority. A tenant-aware authorization utility follows when dynamic runtime registration
is introduced. **File a GitHub issue before closing platform#73.**

### Population Model

The registry is populated by `@Startup @ApplicationScoped` CDI beans via `@PostConstruct`.
This is the canonical platform startup pattern, established by `PathParserConfigurator` in
`platform/`. Each module that owns endpoints declares its own registrar bean:

```java
@Startup
@ApplicationScoped
public class SalesforceEndpointRegistrar {

    @Inject EndpointRegistry registry;

    @ConfigProperty(name = "salesforce.tenancy-id")
    String tenancyId;

    @PostConstruct
    void register() {
        registry.register(new EndpointDescriptor(
            Path.of("external", "salesforce", "prod"),
            tenancyId,
            EndpointType.SYSTEM,
            EndpointProtocol.HTTP,
            Map.of(EndpointPropertyKeys.URL, "https://login.salesforce.com"),
            "salesforce-prod-creds",
            Set.of(EndpointCapability.SEND, EndpointCapability.QUERY)
        ));
    }
}
```

`tenancyId` comes from `@ConfigProperty`, not from `CurrentPrincipal`. The OIDC
`CurrentPrincipal` implementation is `@RequestScoped` — there is no request context at
`@PostConstruct` time.

**Limitation — single registration per bean.** The `@PostConstruct` pattern works for
platform-global endpoints and single-tenant deployments. It does not compose for the
multi-tenant use case where 50 tenants each have their own Salesforce org at different
URLs, different `credentialRef` values, and different `tenancyId` values. Registering one
`@ConfigProperty`-per-tenant does not scale. This case requires the config-backed registrar
module — see Deferred. The in-memory adapter stores whatever the registrar writes; it has
no opinion on how many tenants are registered.

### `InMemoryEndpointRegistry` (in `endpoints-memory/`)

**Internal key type:**

```java
record RegistryKey(String pathValue, String tenancyId) {}
```

`path.value()` (the raw string) is used as the key component rather than the `Path` record
itself. `Path` equality depends on both `value` and `segments`, and `segments` derive from
a configurable parser separator. Using the string `value` directly gives stable identity
independent of parser configuration.

```java
@Alternative
@Priority(100)
@ApplicationScoped
public class InMemoryEndpointRegistry implements EndpointRegistry {
    // ConcurrentHashMap<RegistryKey, EndpointDescriptor>
}
```

- Thread-safe; data is ephemeral (lost on restart)
- `register()` — upsert: re-registering the same `(path.value(), tenancyId)` replaces the descriptor
- `resolve()` — two-step priority lookup:
  1. Try `RegistryKey(path.value(), tenancyId)` — return if present
  2. Try `RegistryKey(path.value(), PLATFORM_TENANT_ID)` — return if present
  3. Return `Optional.empty()`
- `discover()` — applies the complete predicate defined in "discover() Complete Predicate"
- `deregister()` — remove if present, silent no-op if absent

### `NoOpEndpointRegistry @DefaultBean` (in `platform/`)

```java
@DefaultBean
@ApplicationScoped
public class NoOpEndpointRegistry implements EndpointRegistry {
    public void register(EndpointDescriptor e) {}
    public Optional<EndpointDescriptor> resolve(Path p, String t) { return Optional.empty(); }
    public List<EndpointDescriptor> discover(EndpointQuery q) { return List.of(); }
    public void deregister(Path p, String t) {}
}
```

Active when no backend module is on the classpath.

### Relationship to SmallRye Stork

Stork is the Quarkus ecosystem solution for dynamic service-instance discovery with load
balancing (Consul, Kubernetes, Eureka backends). It operates on host:port pairs for
microservice-to-microservice routing.

`EndpointRegistry` is a tenant-scoped, multi-protocol configuration registry for named
external systems. The two are complementary, not substitutes. If a Consul-backed
`EndpointRegistry` implementation is ever needed, Stork is the natural integration point
(implement `EndpointRegistry` over Stork rather than rebuilding Consul integration).

---

## Tests

### `endpoints-memory/` — plain JUnit5, no Quarkus

Instantiates `InMemoryEndpointRegistry` directly (no CDI). Consistent with
`InMemoryMemoryStoreTest` pattern. Covers:

- register + resolve round-trip
- Tenant isolation: tenant A's endpoints invisible to tenant B
- Platform-global visibility: `PLATFORM_TENANT_ID` endpoints visible to all tenants
- resolve() priority: tenant-specific wins over platform-global when both exist for same path
- discover() returns both when tenant-specific and platform-global exist for same path
- deregister removes; subsequent resolve returns empty
- discover: type filter (null = all types match)
- discover: protocol filter (null = all protocols match)
- discover: requiredCapabilities subset check (empty set = all descriptors match)
- discover: platform-global endpoints included for any querying tenant
- discover: `query.tenancyId = PLATFORM_TENANT_ID` returns only platform-global endpoints (the predicate's two OR clauses both reduce to the same condition — no tenant-specific endpoints leak through)
- Upsert: re-registering same `(path, tenancyId)` replaces the descriptor — last write wins, no capability merge
- Constructor null-rejection: `properties=null` and `capabilities=null` both throw `NullPointerException`
- `RegistryKey` equality: two paths with the same `path.value()` and same `tenancyId` resolve to same slot

### `platform/` — `@QuarkusTest`

Confirms `NoOpEndpointRegistry` satisfies `EndpointRegistry` injection when no backend is
present. Follows existing `MockBeansTest` pattern — add `@Inject EndpointRegistry` and
assert `resolve()` returns empty and `discover()` returns empty list.

---

## Maven Changes

### Root `pom.xml`

Add one module entry:
```xml
<module>endpoints-memory</module>
```

No `endpoints` module — the SPI lives in `platform-api` which is already a declared module.

### `platform-api/pom.xml`

No dependency changes. `EndpointRegistry` and all value types are pure Java; `Path` is
already in `platform-api`.

### `platform/pom.xml`

No dependency changes. `platform/` already depends on `casehub-platform-api`. `NoOpEndpointRegistry`
is added as a new source file only.

### `endpoints-memory/pom.xml`

```xml
<artifactId>casehub-platform-endpoints-memory</artifactId>
<dependencies>
  <dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-platform-api</artifactId>
  </dependency>
  <dependency>
    <groupId>jakarta.enterprise</groupId>
    <artifactId>jakarta.enterprise.cdi-api</artifactId>
    <scope>provided</scope>
  </dependency>
  <!-- test only -->
  <dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.assertj</groupId>
    <artifactId>assertj-core</artifactId>
    <scope>test</scope>
  </dependency>
</dependencies>
<!-- jandex plugin -->
```

No `quarkus-junit5` and no `casehub-platform` test dependency — tests instantiate
`InMemoryEndpointRegistry` directly without CDI. The CDI displacement test (`NoOpEndpointRegistry`)
lives in `platform/` tests, which already has `quarkus-junit5`.

---

## Document Updates

### `PLATFORM.md` Capability Ownership table

Add:

| Named endpoint registry | `casehub-platform-api` | `EndpointRegistry` SPI — register/resolve/discover/deregister named endpoints by `(Path, tenancyId)`. `EndpointProtocol` enum (HTTP, GRPC, KAFKA, MCP, CAMEL, QHORUS). Protocol-specific properties in `Map<String,String>`; shared keys in `EndpointPropertyKeys` (`URL`, `TOPIC`). `credentialRef` for secrets backend integration (resolution deferred). Platform-global endpoints use `TenancyConstants.PLATFORM_TENANT_ID`. `NoOpEndpointRegistry @DefaultBean` in `casehub-platform`; `InMemoryEndpointRegistry @Alternative @Priority(100)` in `casehub-platform-endpoints-memory`. JPA backend deferred. |

### `TenancyConstants` Javadoc

Update `PLATFORM_TENANT_ID` Javadoc to cover both uses:

```java
/**
 * Reserved for two platform-level uses:
 * (1) Cross-tenant super-admin principals — a principal whose {@code tenancyId()} returns
 *     this value has platform-wide access bypassing per-tenant filters.
 * (2) Platform-global endpoint registration — an {@code EndpointDescriptor} with this
 *     {@code tenancyId} is visible to all tenants via {@code EndpointRegistry.resolve()}
 *     and {@code EndpointRegistry.discover()}.
 *
 * <p>Both uses share the same semantic root: owned by the platform, not any specific tenant.
 */
public static final String PLATFORM_TENANT_ID = "platform";
```

### `ARC42STORIES.MD`

- **§1 Description:** add `EndpointRegistry` (named endpoint config SPI) to the core capabilities list
- **§4 Layer taxonomy:** add **L9: Endpoint Registry Adapters** — `casehub-platform-endpoints-memory` and future JPA/NoSQL backends. L6 remains Memory Adapters exclusively (CaseMemoryStore / GraphCaseMemoryStore). Endpoint adapters implement a different SPI and must not be conflated into L6.
- **§5 Building Block View:** add `casehub-platform-endpoints-memory` to a new L9 boundary container (`@Alternative @Priority(100) InMemoryEndpointRegistry`)
- **§7 Deployment View:** add `casehub-platform-endpoints-memory` row (scope: `test` for isolation or `compile` for ephemeral installs; note: data lost on restart)
- **§9 Chapter Index:** add C17 to the chapter table (J4: Endpoint Registry)

---

## Deferred

- JPA persistence backend (`endpoints-jpa/`)
- Config-backed registrar module (`casehub-platform-endpoints-config`) — load endpoints
  from YAML at startup, analogous to `casehub-platform-config`. Required for multi-tenant
  deployments where per-tenant endpoint config (different URLs, different `credentialRef`
  per tenant) cannot be expressed as a single `@ConfigProperty`. **File a GitHub issue
  before closing platform#73.**
- Dynamic runtime registration authorization utility (`EndpointPermissions.assertTenant()`)
  — required when `casehub-deployment` registers endpoints at runtime from request context.
  **File a GitHub issue before closing platform#73.**
- Secrets resolution for `credentialRef`
- Path-prefix `discover` queries (`Path.isAncestorOf()` is available when needed)
- CDI events on register/deregister
- Consul/Stork-backed `EndpointRegistry` implementation
- Contract test in `testing/` module (analogous to `CaseMemoryStoreContractTest`) — needed
  when a second adapter implementation exists
