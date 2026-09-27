# Decisions — McpToolSource CDI Wiring

## D1: CDI wiring bean placement

**Choice:** `McpStepCatalogWiring @Startup @ApplicationScoped` in yaml-step-runtime. Compile dependency on mcp-core (lightweight — platform-api + descriptor records). Optional runtime dependency on mcp/ via `Instance<ReflectiveOperationDispatcher>`.
**Alternatives:**
- Wiring bean in mcp/ with yaml-step-runtime as dependency — wrong dependency direction (infra depends on consumer)
- New bridge module — overhead for a single bean
**Rationale:** mcp-core contains all descriptor types needed at compile time (DomainModelRegistry, ModelScanComplete, OperationDescriptor, ParameterDescriptor). mcp-core depends only on platform-api — no heavy transitive deps. Instance<> guards handle runtime optionality.
**Trade-offs:** yaml-step-runtime gains a compile dependency on mcp-core. Acceptable — mcp-core is thin (4 records + 1 registry class).
**Sources:** mcp-core/pom.xml (platform-api only), DynamicToolRegistrar.java (existing ModelScanComplete observer pattern)
**Exploration:** quick
**Status:** captured

## D2: Dispatch via Instance<ReflectiveOperationDispatcher>

**Choice:** Inject `Instance<ReflectiveOperationDispatcher>` from mcp/ as optional runtime dependency. Use `isResolvable()` guard. If resolvable, dispatch step execution through `dispatcher.dispatch(domain, operation, params)`. If not resolvable, the wiring bean does not register any tools.
**Alternatives:**
- Direct Method.invoke from OperationDescriptor — duplicates param validation and error handling
- Move ReflectiveOperationDispatcher to mcp-core — mixes CDI-specific dispatch logic into a POJO module
**Rationale:** Reuses existing dispatch + param validation. Falls back gracefully. No code duplication.
**Trade-offs:** MCP step catalog requires both mcp-core (compile) and mcp/ (runtime) on classpath. Without mcp/, no MCP tools in the catalog — which is the desired "optional activation" behavior.
**Sources:** McpToolSource.java (current toolInvoker function), ReflectiveOperationDispatcher.java (dispatch + validation)
**Exploration:** quick
**Status:** captured

## D3: Parameter schema bridging

**Choice:** Map `ParameterDescriptor.typeName()` to `StepParameterType` for StepDefinition inputs. Mapping: String→STRING, Integer/Long/int/long→INTEGER, Double/Float/BigDecimal/double/float→NUMBER, Boolean/boolean→BOOLEAN, List/Collection→ARRAY, everything else→OBJECT. Required flag from `ParameterDescriptor.required()`. Description from `ParameterDescriptor.description()`.
**Alternatives:**
- Leave inputs empty (current McpToolSource) — no schema validation, no IDE completion
- Parse JSON Schema from MCP tool definitions — over-engineering; ParameterDescriptor already has the metadata
**Rationale:** ParameterDescriptor provides name, type, required, description — exactly what StepParameter needs. Simple string mapping covers all common Java types.
**Trade-offs:** Custom types map to OBJECT, losing structural type information. Acceptable — OBJECT means "any map", which is correct for complex parameters.
**Sources:** ParameterDescriptor.java (typeName, required, description fields), StepParameter.java (type, required, description fields)
**Exploration:** quick
**Status:** captured

## D4: McpToolSource signature change

**Choice:** Change toolInvoker from `Function<String, Map<String, Object>>` to `BiFunction<String, Map<String, Object>, Map<String, Object>>` — accepts (toolName, params) → result. Update the `populate()` method to pass step params through to the invoker.
**Alternatives:**
- Keep Function<String, ...> and ignore params — defeats the purpose of schema-validated steps
- Replace McpToolSource entirely with the CDI wiring bean — McpToolSource is still useful as a unit-testable POJO
**Rationale:** The current signature can't pass step parameters to the MCP tool. BiFunction is the minimal change — one extra argument.
**Trade-offs:** Breaking change to McpToolSource constructor. No external consumers — the class was created in #433 on this branch.
**Sources:** McpToolSource.java:18 (current constructor), populate():31-38 (current toolInvoker usage)
**Exploration:** quick
**Status:** captured
