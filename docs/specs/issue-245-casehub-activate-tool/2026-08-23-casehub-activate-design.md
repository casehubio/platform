# casehub_activate — On-Demand Per-Operation Tool Registration

**Issue:** casehubio/platform#245
**Branch:** issue-245-casehub-activate-tool
**Date:** 2026-08-23

## Problem

`DynamicToolRegistrar` registers a single `casehub_action` meta-tool at
startup. LLM agents use it effectively, but human MCP users lose
auto-completion — they must type `{domain, operation, params}` manually.

Registering all operations as individual tools at startup solves
auto-complete but bloats the tool list for every connected client.

## Solution

Add `casehub_activate` tool registered at startup alongside `casehub_model`
and `casehub_action`. When called with a domain name, it registers that
domain's operations as individual tools via `ToolManager.newTool()`. The MCP
server sends `notifications/tools/list_changed` automatically.

- LLMs use `casehub_action` directly — never activate, never pay token cost
  for individual tools
- Humans activate domains they need — auto-complete for their working set
- Idempotent — calling activate twice is a no-op
- No deactivation needed (tools persist until server restart)

## Architecture

**Modified files:** `DynamicToolRegistrar.java` + `DynamicToolRegistrarTest.java`

**New field:** `Set<String> activatedDomains = ConcurrentHashMap.newKeySet()`

**Registration in `onScanComplete()`:**

Both `casehub_action` and `casehub_activate` use `setHandler(fn, true)` for
virtual thread execution — GraphQL resolvers may perform blocking I/O.

```java
// Update existing casehub_action to use virtual threads:
toolManager.newTool("casehub_action")
    // ... existing description and schema ...
    .setHandler(args -> { /* existing handler */ }, true)  // virtual thread
    .register();

// New: casehub_activate
toolManager.newTool("casehub_activate")
    .setDescription("Activate a domain's operations as individual tools "
            + "for auto-completion. Use casehub_model to discover domain names.")
    .addArgument("domain", "Domain name to activate", true, String.class)
    .setHandler(args -> activateDomain((String) args.args().get("domain")), true)
    .register();
```

**Activation handler (`activateDomain`):**

```java
private ToolResponse activateDomain(String domain) {
    // 1. Validate domain exists
    DomainModel model = registry.getDomain(domain)
            .orElseThrow(() -> new IllegalArgumentException(
                    "Unknown domain: " + domain
                    + ". Use casehub_model to list available domains."));

    // 2. Idempotency check — BEFORE registration attempt
    if (activatedDomains.contains(domain)) {
        return ToolResponse.success("Domain '" + domain + "' already activated.");
    }

    // 3. Register per-operation tools — wrapped in try-catch for partial failure
    List<String> registered = new ArrayList<>();
    try {
        for (OperationDescriptor op : model.operations()) {
            String toolName = domain + "_" + op.name();
            registerOperationTool(domain, op, toolName);
            registered.add(toolName);
        }
    } catch (Exception e) {
        // Partial failure — roll back registered tools
        for (String toolName : registered) {
            toolManager.removeTool(toolName);
        }
        LOG.warnf(e, "Failed to activate domain '%s' — rolled back %d tools",
                domain, registered.size());
        return ToolResponse.error("Failed to activate domain '" + domain
                + "': " + e.getMessage());
    }

    // 4. Mark as activated ONLY after full success
    activatedDomains.add(domain);

    return ToolResponse.success("Activated " + registered.size()
            + " tools for domain '" + domain + "': " + registered);
}
```

**Per-operation tool registration (`registerOperationTool`):**

```java
private void registerOperationTool(String domain, OperationDescriptor op,
        String toolName) {
    boolean isQuery = op.type() == OperationDescriptor.OperationType.QUERY;

    ToolDefinition def = toolManager.newTool(toolName)
            .setDescription(op.summary() != null && !op.summary().isBlank()
                    ? op.summary()
                    : "[" + domain + "] " + op.name());

    // Add typed arguments from method signature
    Parameter[] methodParams = op.method().getParameters();
    List<ParameterDescriptor> paramDescs = op.params();
    for (int i = 0; i < paramDescs.size(); i++) {
        ParameterDescriptor pd = paramDescs.get(i);
        def.addArgument(pd.name(), pd.description(),
                pd.required(), methodParams[i].getParameterizedType());
    }

    // MCP annotations — queries are read-only + idempotent; mutations are neither
    def.setAnnotations(new ToolManager.ToolAnnotations(
            null,
            isQuery,   // readOnlyHint
            false,     // destructiveHint — no finer-grained data; safe default
            isQuery,   // idempotentHint — queries are idempotent, mutations are not
            false      // openWorldHint
    ));

    def.setHandler(args -> {
        try {
            Object result = dispatcher.dispatch(domain, op.name(), args.args());
            return ToolResponse.success(mapper.writeValueAsString(result));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ToolResponse.error(e.getMessage());
        } catch (Exception e) {
            LOG.errorf(e, "%s dispatch failed", toolName);
            return ToolResponse.error(e.getMessage());
        }
    }, true);  // virtual thread — resolvers may block

    def.register();
}
```

**Key design choices:**

- **`addArgument()` with real Java types** — `methodParams[i].getParameterizedType()`
  passes the actual method parameter type. The MCP server generates the JSON
  Schema automatically. No manual type mapping.
- **ToolAnnotations** — `readOnlyHint` + `idempotentHint` for QUERY only.
  `destructiveHint=false` for all — no finer-grained mutation classification
  exists in `OperationType`. Most mutations are creates/updates, not deletes.
- **Virtual threads** — `setHandler(fn, true)` for all handlers. GraphQL
  resolvers may perform blocking I/O (JDBC, REST, subprocess).
- **Partial failure rollback** — tools registered before a failure are removed
  via `toolManager.removeTool()`. Domain is NOT added to `activatedDomains`
  until all tools are registered successfully.
- **Error handling** — per-operation handlers match `casehub_action` pattern.
  Activation handler wraps the loop in try-catch and rolls back on failure.
- **Tool naming constraint** — domain and operation names are Java identifiers
  (from `@McpDomain` annotations and method names) and don't contain
  underscores in the current codebase. The `_` separator is unambiguous
  under this constraint.

## Testing

**Unit tests for `DynamicToolRegistrar`** (extend existing `DynamicToolRegistrarTest`):

- `activateDomain_shouldRegisterPerOperationTools` — activate a domain with
  2 operations, verify 2 tools registered via `ToolManager.getTool()`
- `activateDomain_shouldBeIdempotent` — activate same domain twice, second
  returns "already activated", no duplicate tool registration
- `activateDomain_unknownDomain_shouldReturnError` — activate non-existent
  domain, verify error response
- `activateDomain_toolShouldDelegateToDispatcher` — invoke a registered
  per-operation tool, verify `dispatcher.dispatch()` called with correct
  domain/operation/params
- `activateDomain_queryShouldHaveReadOnlyHint` — verify QUERY operations
  get `readOnlyHint=true` + `idempotentHint=true`
- `activateDomain_mutationShouldNotBeIdempotent` — verify MUTATION operations
  get `idempotentHint=false` + `destructiveHint=false`
- `activateDomain_partialFailure_shouldRollback` — pre-register a conflicting
  tool name, activate a domain containing that name, verify error response and
  other tools cleaned up, verify domain not in `activatedDomains` (can retry)
- `activateDomain_emptyDomain_shouldSucceed` — domain with zero operations,
  verify success with empty tool list

## References

- `DynamicToolRegistrar.java` (mcp/) — modified file
- `DynamicToolRegistrarTest.java` (mcp/) — test file
- `ToolManager` (quarkus-mcp-server-core) — `newTool()`, `getTool()`,
  `removeTool()`, `addArgument()`, `ToolAnnotations`
- `ModelRegistry.java` (mcp/) — domain lookup
- `OperationDescriptor.java` (mcp/) — operation metadata
- `ReflectiveOperationDispatcher.java` (mcp/) — dispatch target
- `CaseHubMcpTools.java` (mcp/) — `casehub_model` static tool pattern
- `McpResourceRegistryBridge.java` (mcp/) — virtual thread handler precedent
- casehubio/platform#245 — parent issue
- Review R1-02, R1-03, R1-04, R1-07 — accepted findings
