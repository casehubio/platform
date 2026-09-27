# McpToolSource CDI Wiring

**Covers:** #444
**Repo:** casehubio/platform
**Depends on:** #433 (McpToolSource POJO — landed), #443 (AptPluginSource — landed)

## Problem

`McpToolSource` in yaml-step-runtime is a POJO that takes `Set<String>` tool names and a `Function<String, Map<String, Object>>` tool invoker. It works but requires manual construction. The CDI wiring to auto-discover MCP tools from the platform's domain registry at startup is missing. Additionally, the current tool invoker ignores step parameters — it invokes the tool by name only, discarding inputs.

## Scope

**In scope:**
- CDI wiring bean that observes `ModelScanComplete` and populates McpToolSource
- McpToolSource signature change to pass params through
- Parameter schema bridging (ParameterDescriptor → StepParameter)
- Optional activation when MCP is on classpath

**Out of scope:**
- MCP tool parameter JSON Schema generation (follow-on to #437)
- Runtime MCP tool hot-reload (tools discovered at startup only)

## Design

### Dependencies

yaml-step-runtime gains a **compile** dependency on `mcp-core` (`casehub-platform-mcp-core`). mcp-core is thin — depends only on platform-api, contains:
- `DomainModelRegistry` — tool/operation registry
- `ModelScanComplete` — CDI event fired after scanning
- `OperationDescriptor` — operation metadata (name, type, params, method)
- `ParameterDescriptor` — parameter metadata (name, typeName, required, description)

`ReflectiveOperationDispatcher` (in mcp/, not mcp-core) is injected via `Instance<>` as an optional runtime dependency.

### McpToolSource Signature Change

```java
public class McpToolSource implements CatalogSource {

    private final Set<String> toolNames;
    private final BiFunction<String, Map<String, Object>, Map<String, Object>> toolInvoker;
    private final Map<String, StepDefinition> definitions;

    public McpToolSource(
            Map<String, StepDefinition> definitions,
            BiFunction<String, Map<String, Object>, Map<String, Object>> toolInvoker) {
        this.definitions = Map.copyOf(definitions);
        this.toolNames = definitions.keySet();
        this.toolInvoker = toolInvoker;
    }
```

Changes:
- Constructor takes `Map<String, StepDefinition>` (definitions with parameter schemas) instead of bare `Set<String>` tool names
- Tool invoker becomes `BiFunction<String, Map<String, Object>, Map<String, Object>>` — `(toolName, params) → result`
- `populate()` passes step params through to the invoker

### McpStepCatalogWiring

```java
@ApplicationScoped
public class McpStepCatalogWiring implements CatalogSource {

    @Inject
    Instance<DomainModelRegistry> registryInstance;

    @Inject
    Instance<ReflectiveOperationDispatcher> dispatcherInstance;

    private McpToolSource delegate;

    void onScanComplete(@Observes ModelScanComplete event) {
        if (!registryInstance.isResolvable() || !dispatcherInstance.isResolvable()) {
            return;
        }

        DomainModelRegistry registry = registryInstance.get();
        ReflectiveOperationDispatcher dispatcher = dispatcherInstance.get();

        Map<String, StepDefinition> definitions = new LinkedHashMap<>();
        for (DomainModel domain : registry.getDomains()) {
            for (OperationDescriptor op : domain.operations()) {
                if (op.type() == OperationDescriptor.OperationType.STREAM) continue;
                String toolName = domain.name() + "_" + op.name();
                definitions.put(toolName, buildDefinition(toolName, op));
            }
        }

        delegate = new McpToolSource(definitions,
                (toolName, params) -> {
                    String[] parts = toolName.split("_", 2);
                    Object result = dispatcher.dispatch(parts[0], parts[1], params);
                    return result instanceof Map ? (Map<String, Object>) result
                            : Map.of("result", result);
                });
    }

    @Override
    public void populate(Map<String, CatalogEntry> entries) {
        if (delegate != null) {
            delegate.populate(entries);
        }
    }

    @Override
    public int priority() {
        return 300;
    }
}
```

**Lifecycle:** `McpStepCatalogWiring` implements `CatalogSource` directly (not via `McpToolSource`). It observes `ModelScanComplete` to populate its internal `McpToolSource` delegate, then exposes entries when `CompositeStepCatalog` calls `populate()`. Since `ModelScanComplete` fires during `@Startup` of `GraphQLModelScanner`, the delegate is populated before `CompositeStepCatalog.initialize()` runs (which is triggered by consumer modules' startup).

**Optional activation:** If `DomainModelRegistry` or `ReflectiveOperationDispatcher` is not resolvable (mcp/ not on classpath), `onScanComplete` returns immediately. `populate()` returns with no entries. The bean is inert.

### Parameter Schema Bridging

`buildDefinition()` maps `ParameterDescriptor` fields to `StepParameter`:

```java
private StepDefinition buildDefinition(String toolName, OperationDescriptor op) {
    Map<String, StepParameter> inputs = new LinkedHashMap<>();
    for (ParameterDescriptor pd : op.params()) {
        inputs.put(pd.name(), new StepParameter(
                mapType(pd.typeName()),
                pd.required(),
                null,
                List.of(),
                null,
                pd.description()));
    }
    return new StepDefinition(toolName, op.summary(),
            inputs, Map.of(), new InvokeBinding.Mcp(toolName));
}

private static StepParameterType mapType(String typeName) {
    return switch (typeName) {
        case "String" -> StepParameterType.STRING;
        case "Integer", "Long", "int", "long" -> StepParameterType.INTEGER;
        case "Double", "Float", "BigDecimal", "double", "float" -> StepParameterType.NUMBER;
        case "Boolean", "boolean" -> StepParameterType.BOOLEAN;
        default -> {
            if (typeName.startsWith("List") || typeName.startsWith("Collection"))
                yield StepParameterType.ARRAY;
            yield StepParameterType.OBJECT;
        }
    };
}
```

### What changes where

| Module | Change |
|--------|--------|
| `yaml-step-runtime/pom.xml` | Add `casehub-platform-mcp-core` compile dependency |
| `yaml-step-runtime/` | `McpToolSource` — constructor takes `Map<String, StepDefinition>` + `BiFunction`, passes params through |
| `yaml-step-runtime/` | `McpStepCatalogWiring @ApplicationScoped` — CDI wiring, observes ModelScanComplete, implements CatalogSource |
| `yaml-step-runtime/` | Tests for McpStepCatalogWiring (mocked registry + dispatcher) |
| `yaml-step-runtime/` | Update existing McpToolSource tests for new constructor |

### What does NOT change

- `mcp/` — no changes to DynamicToolRegistrar or GraphQLModelScanner
- `mcp-core/` — no changes to descriptor types
- `CompositeStepCatalog` — no initialization changes needed (CatalogSource discovery is unchanged)
- Other CatalogSource implementations (AptPluginSource, YamlStepDefinitionSource) — unchanged

## References

- `yaml-step-runtime/src/main/java/io/casehub/yaml/step/catalog/McpToolSource.java` — current POJO
- `mcp-core/src/main/java/io/casehub/platform/mcp/DomainModelRegistry.java` — tool/operation registry
- `mcp-core/src/main/java/io/casehub/platform/mcp/ModelScanComplete.java` — CDI event
- `mcp-core/src/main/java/io/casehub/platform/mcp/OperationDescriptor.java` — operation metadata
- `mcp-core/src/main/java/io/casehub/platform/mcp/ParameterDescriptor.java` — parameter metadata
- `mcp/src/main/java/io/casehub/platform/mcp/DynamicToolRegistrar.java:47-87` — existing ModelScanComplete observer pattern
- `mcp/src/main/java/io/casehub/platform/mcp/ReflectiveOperationDispatcher.java` — dispatch + validation
- GitHub #444, #433, #443
