## D1: MCP server SDK strategy — framework-specific

**Choice:** Framework-specific libraries on each side — keep Quarkiverse for Quarkus, use Spring AI MCP Server Starter for Spring
**Alternatives:**
- Agnostic MCP Java SDK core (`io.modelcontextprotocol:sdk`) on both sides — guaranteed protocol parity, but requires migrating Quarkus off Quarkiverse and custom transport adapters on both sides
- Hand-written SSE endpoints — full control, zero deps beyond framework, but significant protocol implementation work
**Rationale:** Each framework-specific library beats the neutral SDK for its own framework (Quarkiverse: CDI lifecycle, Vert.x, native image; Spring AI: auto-configures from ToolCallbackProvider, actuator integration). Asymmetry is justified when each side gets a genuine advantage. The shared source of truth is `@McpDomain` → `DomainModelRegistry`, which is already framework-neutral — transport is commodity plumbing below that boundary.
**Trade-offs:** Two different MCP protocol implementations — potential behavior drift on edge cases, though both target the same spec. If drift becomes a problem, migration to agnostic core is a clean follow-up since the DomainModelRegistry boundary doesn't change.
**Sources:** mcp/pom.xml (Quarkiverse dep), mcp-spring/ (CaseHubToolCallbackProvider implements ToolCallbackProvider), platform-api McpDomain annotation
**Exploration:** quick
**Status:** captured

## D2: Module structure — extend mcp-spring

**Choice:** Extend existing `mcp-spring/` module with Spring AI MCP Server Starter dependency, resource bridge, and transport configuration
**Alternatives:**
- New `mcp-server-spring/` module — separates server transport from tool registration, lets consumers opt in independently
- New `mcp-session-spring/` module — narrowest scope, just session/transport
**Rationale:** One module owning all MCP-in-Spring concerns matches the consolidated pattern (`agent-spring/` consolidated 10 individual modules). Consumers who want MCP tools also want the server transport — splitting creates a dependency that's always satisfied.
**Trade-offs:** Consumers who only want in-process tool registration (no server endpoint) now pull in the server starter. Mitigated by Spring AI's auto-configuration — the server endpoint can be disabled via `spring.ai.mcp.server.enabled=false`.
**Sources:** mcp-spring/ (existing module), agent-spring/ (consolidated pattern precedent)
**Exploration:** quick
**Status:** captured

## D3: Resource bridging — McpSyncServer dynamic registration

**Choice:** Use the underlying `McpSyncServer` bean (exposed by Spring AI MCP Server Starter) for runtime `addResource()` / `removeResource()` calls, preserving full dynamic registration semantics
**Alternatives:**
- Startup-only `SyncResourceSpecification` beans — simpler wiring but loses dynamic registration; modules can't register resources after startup
**Rationale:** The Quarkus `McpResourceRegistryBridge` supports dynamic registration via Quarkiverse `ResourceManager`. The Spring side must preserve this parity — `McpResourceRegistry` SPI callers register resources at runtime (e.g. `DomainResourceRegistrar` observes `ModelScanComplete`, subscription modules register on demand). Startup-only would break these consumers.
**Trade-offs:** Tighter coupling to `McpSyncServer` internals rather than the higher-level Spring AI auto-configuration. If Spring AI changes the `McpSyncServer` API, the bridge needs updating. Acceptable — the SPI boundary (`McpResourceRegistry`) isolates consumers from this.
**Sources:** McpResourceRegistryBridge.java (dynamic registration pattern), platform-api McpResourceRegistry SPI
**Exploration:** quick
**Status:** captured

## D4: Transport — WebMVC SSE

**Choice:** `spring-ai-starter-mcp-server-webmvc` — blocking SSE transport
**Alternatives:**
- Streamable HTTP — newer MCP transport, more future-proof but less battle-tested
- WebFlux SSE — reactive, doesn't align with CaseHub's blocking model
**Rationale:** CaseHub uses `@RunOnVirtualThread` blocking style on the Quarkus side. Spring Boot apps default to WebMVC. The WebMVC SSE transport matches both conventions. Streamable HTTP can be adopted later when it matures.
**Trade-offs:** SSE transport may be deprecated in favor of Streamable HTTP in future MCP spec versions. Migration path is straightforward — swap starter dependency, transport is below the SPI boundary.
**Sources:** Spring AI MCP Server Boot Starter docs (transport options)
**Exploration:** quick
**Status:** captured
