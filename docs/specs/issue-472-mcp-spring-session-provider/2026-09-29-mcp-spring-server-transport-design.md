# MCP Spring Server Transport — Design Spec

**Issue:** platform#472
**Date:** 2026-09-29
**Status:** Reviewed

## Problem

The `mcp-spring/` module registers `@McpDomain` tools as Spring AI `ToolCallback` instances via `CaseHubToolCallbackProvider`, but has no MCP server transport — external MCP clients (Claude, other agents) cannot connect to a Spring Boot CaseHub app over SSE to invoke those tools or access resources.

The Quarkus side has full MCP server capability via `quarkus-mcp-server-sse` (tools, resources, resource templates, completions). Spring needs parity.

## Solution

Extend `mcp-spring/` with Spring AI MCP Server Starter (WebMVC SSE transport) for tools and a new `SpringMcpResourceRegistryBridge` for dynamic resource registration, plus a `SpringDomainResourceRegistrar` to register domain resources at startup.

## Architecture

### Tools — zero custom code

Add `spring-ai-starter-mcp-server-webmvc` dependency. Spring AI auto-discovers `ToolCallbackProvider` beans and registers their tools with the MCP server. `CaseHubToolCallbackProvider` already implements `ToolCallbackProvider`, so all `@McpDomain` operations are served automatically.

### Resources — SpringMcpResourceRegistryBridge

New class implementing `McpResourceRegistry` SPI from `platform-api`. Injects `McpSyncServer` (exposed by Spring AI MCP Server Starter as a bean) for dynamic runtime registration.

Responsibilities:
- `newResource(descriptor)` → returns a `BridgeRegistration` builder
- `BridgeRegistration.handler(h).register()` → builds `McpServerFeatures.SyncResourceSpecification` (static) or `McpServerFeatures.SyncResourceTemplateSpecification` (templates) with handler lambda, then calls `McpSyncServer.addResource()` / `addResourceTemplate()`. Completions are bundled into the template specification at registration time (not registered separately).
- `deregister(name)` → maintains a `ConcurrentHashMap<String, Registration>` that maps name → URI/uriTemplate. Calls `McpSyncServer.removeResource(uri)` for static resources or `McpSyncServer.removeResourceTemplate(uriTemplate)` for templates. The SDK remove methods take URI, not name — the bridge translates.
- `resolve(name)` / `list()` → local `ConcurrentHashMap` lookup (mirrors Quarkus bridge pattern)
- Publishes `McpResourceRegistered` event via `ApplicationEventPublisher.publishEvent()` after successful registration (parity with Quarkus CDI `Event.fireAsync()`)
- Observes `McpResourceUpdated` events via `@EventListener` → looks up the affected resource's `SyncResourceSpecification` and calls the SDK's per-resource notification API (not a generic `notifyResourcesChanged()` — that method does not exist)

**`serverName` limitation:** Spring AI MCP Server does not support multi-server scoping. The `serverName` field on `McpResourceRegistration` is accepted but ignored on the Spring side. This matches Spring AI's single-server model. If multi-server support is added to Spring AI later, this can be wired through.

**Subscribable resources:** The Quarkus bridge explicitly rejects `subscribable=true` on template resources (Quarkiverse limitation). The Spring bridge should apply the same restriction initially, relaxing it if the Spring AI SDK supports resource subscriptions.

Structure mirrors `McpResourceRegistryBridge` in the Quarkus `mcp/` module:
- `Registration` record (descriptor, URI/uriTemplate for deregistration lookup, valid flag)
- `BridgeRegistration` inner class (builder pattern: handler, completions, serverName)
- `BridgeHandle` inner class (implements `McpResourceHandle` for update notifications and deregistration)

### Domain Resource Registration — SpringDomainResourceRegistrar

New class mirroring the Quarkus `DomainResourceRegistrar`. Listens for `ModelScanComplete` event (published by `SpringModelScanner`) and registers the `casehub://domain-index` static resource + `casehub://domains/{domain}` resource template with completions via `McpResourceRegistry`.

Without this, the bridge infrastructure exists but no domain resources are registered at startup.

### Data Flow

```
@McpDomain SPI → SpringModelScanner → DomainModelRegistry
                                     │                │
                    CaseHubToolCallbackProvider    ModelScanComplete event
                           (ToolCallbackProvider)         │
                                     │          SpringDomainResourceRegistrar
                    Spring AI auto-config         │
                                     │          McpResourceRegistry.register()
                               McpSyncServer          │
                                ╱         ╲           │
                          tools          resources ←──┘
                            │               │
               (auto-discovered)    SpringMcpResourceRegistryBridge
                                            │
                               WebMVC SSE transport
                                            │
                                  external MCP clients
```

## Components

### Modified files

**`mcp-spring/pom.xml`**
- Add dependency: `spring-ai-starter-mcp-server-webmvc`

**`McpSpringAutoConfiguration.java`**
- Add `@AutoConfiguration(after = McpServerAutoConfiguration.class)` to ensure `McpSyncServer` bean exists before our auto-config runs. Without this, `@ConditionalOnBean(McpSyncServer.class)` may silently fail, and `NoOpMcpResourceRegistry` from the generated platform-spring auto-config would win.
- Add `@Bean @ConditionalOnBean(McpSyncServer.class)` for `SpringMcpResourceRegistryBridge` — implements `McpResourceRegistry`, displacing the `@ConditionalOnMissingBean` no-op.
- Add `@Bean @ConditionalOnBean(McpSyncServer.class)` for `SpringDomainResourceRegistrar`.
- Inject `McpSyncServer` and `ApplicationEventPublisher`.

### New files

**`SpringMcpResourceRegistryBridge.java`**
- Implements `McpResourceRegistry`
- Constructor-injected: `McpSyncServer`, `ApplicationEventPublisher`
- `ConcurrentHashMap<String, Registration>` for local tracking (name → descriptor + URI/uriTemplate)
- `@EventListener` for `McpResourceUpdated` → per-resource notification via SDK API
- Static resource registration: builds `SyncResourceSpecification` with handler lambda translating `McpResourceReadRequest` → `McpResourceContent`. The handler receives the SDK's exchange/args object — extract URI and translate to our `McpResourceReadRequest`.
- Template resource registration: builds `SyncResourceTemplateSpecification` with handler + inline completion values. Template argument extraction from resolved URIs must use the URI template pattern to parse argument values from the concrete URI.
- Deregistration: `McpSyncServer.removeResource(uri)` / `removeResourceTemplate(uriTemplate)` — name→URI translation via local map.
- Publishes `McpResourceRegistered` after successful registration.

**`SpringDomainResourceRegistrar.java`**
- `@EventListener(ModelScanComplete.class)` — registers `casehub://domain-index` + `casehub://domains/{domain}` template with completions via `McpResourceRegistry`. Mirrors `DomainResourceRegistrar` from the Quarkus `mcp/` module.

## Configuration

Spring AI MCP Server auto-configuration properties (defaults are sufficient):
- `spring.ai.mcp.server.enabled=true` (default)
- `spring.ai.mcp.server.capabilities.tool=true` (default)
- `spring.ai.mcp.server.capabilities.resource=true` (default — must be enabled for resources to work; disabled would silently prevent resource registration)

CaseHub-specific:
- `casehub.mcp.server-name` (optional, existing) — accepted but ignored on Spring side (single-server model)

## Implementation Notes

- **MCP SDK version:** The project classpath has MCP SDK 2.0.0. Verify all API names against this version at implementation time. Method signatures may differ from earlier SDK versions referenced in documentation.
- **Dual state consistency:** Both the bridge (`ConcurrentHashMap`) and `McpSyncServer` maintain internal resource state. The bridge is the authoritative source for `resolve()`/`list()` — the SDK's state is the transport-facing truth. Registration/deregistration must update both atomically (bridge first, then SDK; if SDK call fails, roll back bridge entry).

## Testing

### Unit tests

- `SpringMcpResourceRegistryBridgeTest` — register static resource, verify local tracking and `McpResourceRegistered` event published. Register template with completions. Deregister by name, verify URI-based SDK removal called. Verify `McpResourceUpdated` event triggers per-resource notification. Verify `subscribable=true` on templates is rejected. Mock `McpSyncServer`.

### Integration tests

- Extend `spring-integration-test/` or add test in `mcp-spring/`:
  - Boot Spring context with MCP server enabled
  - Verify `CaseHubToolCallbackProvider` tools appear in MCP tool listing
  - Verify `SpringDomainResourceRegistrar` registers domain resources at startup
  - Register a resource via `McpResourceRegistry`, verify it appears in MCP resource listing
  - Deregister, verify removal
  - Verify auto-configuration ordering: `SpringMcpResourceRegistryBridge` displaces `NoOpMcpResourceRegistry`

## Scope Boundaries

**In scope:**
- Spring AI MCP Server Starter dependency + auto-configuration with explicit ordering
- `SpringMcpResourceRegistryBridge` implementing `McpResourceRegistry` SPI
- `SpringDomainResourceRegistrar` for startup domain resource registration
- Static resources, resource templates, completions (bundled at registration)
- Resource update notifications, `McpResourceRegistered` events
- Name→URI translation for deregistration
- Unit and integration tests

**Out of scope:**
- MCP prompts (not used by CaseHub today)
- MCP client capability (connecting to external MCP servers from Spring)
- Streamable HTTP transport (future follow-up when it matures)
- Migrating Quarkus off Quarkiverse (separate concern per D1)
- Multi-server scoping (`serverName` accepted but ignored)

## References

- `mcp/src/main/java/io/casehub/platform/mcp/McpResourceRegistryBridge.java` — Quarkus bridge being mirrored
- `mcp/src/main/java/io/casehub/platform/mcp/DomainResourceRegistrar.java` — Quarkus domain resource registrar being mirrored
- `mcp-spring/src/main/java/io/casehub/platform/mcp/spring/CaseHubToolCallbackProvider.java` — existing Spring AI tool integration
- `mcp-spring/src/main/java/io/casehub/platform/mcp/spring/McpSpringAutoConfiguration.java` — auto-config to extend
- `platform-api` `io.casehub.platform.api.mcp` — McpResourceRegistry SPI, McpResourceDescriptor, McpResourceHandle
- Spring AI MCP Server Boot Starter docs — transport options, resource support, ToolCallbackProvider auto-discovery
- `decisions.md` D1-D4 — framework-specific SDK, extend mcp-spring, McpSyncServer dynamic, WebMVC SSE
