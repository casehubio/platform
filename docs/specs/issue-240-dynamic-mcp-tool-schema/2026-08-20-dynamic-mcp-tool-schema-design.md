# Dynamic MCP Tool Schema — Design Spec

**Issue:** casehubio/platform#240
**Date:** 2026-08-20
**Status:** Draft
**Depends on:** #228 (MCP hierarchical model)

## 1. Problem Statement

The `casehub_action` MCP tool accepts `domain`, `operation`, and `params` as
untyped string arguments. LLMs must first call `casehub_model` to learn valid
domains, operations, and parameter shapes. In long conversations this context
scrolls away, causing the LLM to guess parameters and rely on validation errors
to self-correct. This works (one-retry recovery) but is reactive.

Flat MCP tools avoid this because their JSON Schema lives in the tool definition,
which is always in the system prompt. This issue makes `casehub_action` self-describing
by dynamically building its JSON Schema from the scanned operation catalog.

Additionally, `casehub_model` serves dual purposes: operation catalog navigation
AND runtime state (enricher summaries, live metrics). Once the schema handles
the catalog, the runtime state is better exposed as MCP Resources — the protocol
primitive designed for declarative, read-only data.

## 2. Design Decisions

| # | Decision | Choice |
|---|----------|--------|
| D1 | Tool coexistence | Keep both `casehub_model` and `casehub_action` |
| D2 | Schema representation | Config-driven mode: `simple` (description catalog) or `rich` (if-then per-domain enums) |
| D3 | Registration approach | Replace `@Tool casehub_action` with `ToolManager` programmatic registration |

## 3. Changes

### 3.1 ModelScanComplete CDI event

`GraphQLModelScanner` fires a `ModelScanComplete` CDI event after scan completes.
`DynamicToolRegistrar` observes this event to build and register the dynamic tool.

```java
public record ModelScanComplete() {}
```

### 3.2 McpSchemaBuilder — schema generation

Builds the `casehub_action` JSON Schema from discovered `DomainModel` records.
Two modes selectable via `casehub.mcp.schema-mode` config (default: `simple`):

**Simple mode** — domain enum + description-based operation catalog:
- `domain`: `{"type": "string", "enum": ["cases", "work", "ledger"]}`
- `operation`: `{"type": "string", "description": "cases:\n  Queries: getCases, getCase\n  Mutations: startCase, suspendCase\nwork:\n  Queries: ..."}`
- `params`: `{"type": "string", "description": "Operation parameters as JSON object"}`

**Rich mode** — domain enum + `allOf` with `if-then` per-domain operation enums:
- Same `domain` enum
- `operation`: `{"type": "string"}` (no description — schema constrains it)
- `allOf`: one `if-then` block per domain, constraining `operation` to that domain's enum
- Enables client-side validation of domain/operation combinations

### 3.3 DynamicToolRegistrar — programmatic tool registration

`@ApplicationScoped` bean that observes `ModelScanComplete`, builds the schema
via `McpSchemaBuilder`, and registers `casehub_action` via `ToolManager.newTool()`.

**Implementation requirements** (from decision review):

1. **Error handling:** The handler must catch `IllegalArgumentException` and
   `IllegalStateException` and return `ToolResponse.error(message)` — reproducing
   the `@WrapBusinessError` behavior that was on the static `@Tool` class.
   Unexpected exceptions are also caught, logged, and returned as error responses.

2. **Server scoping:** If the deployment uses a named MCP server (e.g.,
   `@McpServer("casehub")`), the programmatic tool must register on the same
   server. In single-server deployments, the framework defaults correctly
   (verified by tests). For multi-server deployments, `.setServerName("casehub")`
   would be required — note as a P0 if multi-server is ever used.

3. **Handler argument extraction:** The `ToolManager` handler receives
   `ToolArguments` with `Map<String, Object> args()`. Arguments are extracted
   by key (`domain`, `operation`, `params`) rather than by method parameter position.

4. **Notifications:** `notifications/tools/list_changed` fires automatically on
   `ToolManager.register()` — no manual notification needed.

### 3.4 CaseHubMcpTools — casehub_action removed

The static `@Tool casehub_action` method is removed from `CaseHubMcpTools`.
`casehub_model` remains as `@Tool` with `@McpServer("casehub")` — it serves
runtime state and enricher summaries that don't belong in a static schema.

### 3.5 MCP Resources for domain metadata (Batch 2)

Expose domain catalog as MCP Resources using `ResourceTemplateManager`:

**Resource template:** `casehub://domains/{domain}`

Each registered domain becomes a readable resource containing:
- Domain summary (from `ModelEnricher.summary()`)
- Runtime state (from `ModelEnricher.state()`)
- Operation details (names, params, return types, summaries)
- Event metadata (channels, delivery)

**Registration:** A new `DomainResourceRegistrar` observes `ModelScanComplete`
and registers one resource per domain via `ResourceManager.newResource()`.
Also registers a resource template for `casehub://domains/{domain}` via
`ResourceTemplateManager`.

**Index resource:** `casehub://domains` — lists all domains with summary and
operation counts (same content as `casehub_model` tier 0).

This provides an alternative to `casehub_model` for clients that support MCP
Resources. LLMs can browse domain state in the resource layer; `casehub_model`
remains for clients that prefer tool-based navigation.

## 4. Scope Boundaries

### In scope

**Batch 1 — Dynamic tool schema** (partially implemented):
- `ModelScanComplete` CDI event
- `McpSchemaBuilder` with `simple` and `rich` modes
- `DynamicToolRegistrar` with `ToolManager` registration
- `casehub_action` removal from `CaseHubMcpTools`
- `SchemaMode` enum and config property
- Tests for schema builder and dynamic registration

**Batch 2 — MCP Resources:**
- `DomainResourceRegistrar` — registers per-domain resources
- Resource template for `casehub://domains/{domain}`
- Index resource at `casehub://domains`
- Tests for resource registration and content

### Not in scope

- Runtime domain re-registration (debounced re-register on hot deploy) — future work
- Schema size budgeting — monitor after deployment, address if system prompt grows too large
- Structured per-operation params in schema (params stays as opaque JSON string)
- `casehub_model` migration to Resources — it stays as a tool; Resources are additive
- Multi-server deployment support (`.setServerName()` — document as P0)

## 5. Testing Strategy

| Layer | Approach |
|-------|----------|
| `McpSchemaBuilder` | Unit — verify simple/rich schema shape for known domain models |
| `DynamicToolRegistrar` | Integration — verify tool registered after scan, dispatch works end-to-end |
| `CaseHubMcpTools` | Existing tests — verify casehub_model still works, casehub_action removed |
| `DomainResourceRegistrar` | Integration — verify resources registered, content readable |
| `SchemaMode` config | Unit — verify config selects correct mode |

## References

- [CaseHubMcpTools.java] — current two-tool MCP implementation
- [GraphQLModelScanner.java] — startup CDI scanner for @McpDomain resolvers
- [ModelRegistry.java] — domain model storage
- [ReflectiveOperationDispatcher.java] — reflection-based operation dispatch
- [quarkus-mcp-server ToolManager API] — programmatic tool registration (v1.11.1)
- [quarkus-mcp-server ResourceManager API] — programmatic resource registration (v1.11.1)
- [GitHub #228] — MCP hierarchical model (parent issue)
- [GitHub #240] — this issue
- [issue-240-decision review] — R1-09 (server scoping), R1-10 (error handling), R1-03 (MCP Resources)
