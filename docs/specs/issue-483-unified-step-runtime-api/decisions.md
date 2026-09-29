## D1: Registration types live in yaml-plugin-api; yaml-core depends on yaml-plugin-api

**Choice:** Move Parameter + ParameterType to yaml-plugin-api. Add Definition (unified, with execute function), PluginRegistry, and Portability to yaml-plugin-api. yaml-core gains a dependency on yaml-plugin-api (which itself is zero-dep). Rename yaml-core's existing StepDefinition to Declaration.

**Alternatives:**
- Evolve CatalogEntry in yaml-step-runtime — simpler change, but locks programmatic registration behind CDI. Plugin authors can't call register() without the full runtime on their classpath.
- Duplicate parameter types in yaml-plugin-api — no dep change to yaml-core, but two near-identical parameter types in different modules. Bridging code in yaml-step-runtime. Confusing for consumers.

**Rationale:** yaml-plugin-api is the plugin author contract module. Action and Result are already there. The registration contract (Definition + PluginRegistry) is a natural extension. yaml-core already has a `step` subpackage that conceptually depends on step plugin types — making the dependency explicit is more honest than maintaining parallel type hierarchies.

**Trade-offs:** yaml-core loses its strict zero-dep status (now depends on yaml-plugin-api). However, yaml-plugin-api itself is zero-dep and J2CL-safe, so the effective transitive dependency graph remains pure Java. yaml-core's CLAUDE.md description needs updating.

**Sources:**
- yaml-plugin-api/src/main/java/io/casehub/yaml/plugin/api/StepAction.java — existing execute contract
- yaml-core/src/main/java/io/casehub/yaml/core/step/StepParameter.java — type to move
- yaml-core/src/main/java/io/casehub/yaml/core/step/StepParameterType.java — type to absorb
- yaml-step-runtime/src/main/java/io/casehub/yaml/step/CatalogEntry.java — existing join type (to be replaced)
- Issue #483 — design rationale for unified API

**Exploration:** deep-analysis
**Status:** captured

## D2: Drop "Step" prefix from all type names

**Choice:** All types lose the "Step" prefix. The module/package path provides namespace context. Aligns Java naming with TS (@casehubio/yaml-core/step).

| New name       | Old name           | Module          |
|----------------|--------------------|-----------------|
| Definition     | StepDefinition     | yaml-plugin-api |
| Declaration    | StepDefinition     | yaml-core       |
| Parameter      | StepParameter      | yaml-plugin-api |
| ParameterType  | StepParameterType  | yaml-plugin-api |
| Result         | StepResult         | yaml-plugin-api |
| Action         | StepAction         | yaml-plugin-api |
| PluginRegistry | StepPluginRegistry | yaml-plugin-api |

**Alternatives:**
- Keep "Step" prefix — more explicit, no rename churn, but redundant with package path and diverges from TS naming convention.

**Rationale:** Feedback from pages session. TS side introduced Step* types as namespace disambiguation, but the module path already does that. Pages #506 will rename TS types to match whatever Java establishes here. Clean names from the start avoid a second rename.

**Trade-offs:** Rename churn across yaml-plugin-api, yaml-core, yaml-step-runtime, and downstream consumers. IntelliJ rename refactor handles this mechanically.

**Depends on:** D1 (types must be in their final location before renaming)

**Sources:**
- Platform #483, Pages #506 — cross-repo naming agreement
- TS codebase: @casehubio/yaml-core/step — module path provides namespace context

**Exploration:** quick (directive from cross-session coordination)
**Status:** captured

## D3: Single unified ParameterType — collapse module and step enums

**Choice:** Unify yaml-core's module `ParameterType` (STRING, LIST, INTEGER, NUMBER, BOOLEAN) and `StepParameterType` (STRING, INTEGER, NUMBER, BOOLEAN, ARRAY, OBJECT) into one `ParameterType` enum in yaml-plugin-api. LIST→ARRAY migration. Inline scalar behavior (no ValueType reference — avoids circular dep with yaml-core). ValueType stays in yaml-core for codegen-specific `javaTypes()`/`accepts()`.

Unified enum: STRING, INTEGER, NUMBER, BOOLEAN, ARRAY, OBJECT

Methods retained:
- `isScalar()` — true for STRING/INTEGER/NUMBER/BOOLEAN (from StepParameterType)
- `validate(Object)` — runtime type checking (from StepParameterType, inlined from ValueType)
- `parseScalar(String)` — scalar string conversion (from StepParameterType)
- `canAccept(ParameterType)` — type compatibility (from module ParameterType)
- `fromString(String)` — string parsing with aliases ("DECIMAL"→NUMBER, "LIST"→ARRAY)

ParsedValue stays in yaml-core as a module-internal type — it's a module parsing concern, not a general parameter type concern.

**Alternatives:**
- Keep two enums with converters — the current state. Converters between two types = one concept that got split. Pages has converter functions (stepParamToParameterType, parameterTypeToStepParam) that prove the split is artificial.
- Merge into yaml-core — keeps ParameterType where it was, but yaml-core can't have the unified type without step concerns leaking into module types (ARRAY, OBJECT aren't module concepts today).

**Rationale:** Feedback from pages session. "A parameter's value type (string, integer, boolean) doesn't change based on where it's used." The Java API defines ONE ParameterType. Pages deletes the converters. This is the critical unification.

**Trade-offs:** Module system migrates LIST→ARRAY. ParsedValue.ListValue remains as module-internal but maps to ARRAY type. Module `canAccept()` semantics need ARRAY handling (ARRAY canAccept ARRAY only, no cross-type acceptance).

**Depends on:** D1 (ParameterType lives in yaml-plugin-api)

**Sources:**
- yaml-core/src/main/java/io/casehub/yaml/core/module/ParameterType.java — module enum to absorb
- yaml-core/src/main/java/io/casehub/yaml/core/step/StepParameterType.java — step enum to absorb
- yaml-core/src/main/java/io/casehub/yaml/core/type/ValueType.java — scalar foundation, stays in yaml-core
- TS converter functions in pages — prove the split is artificial
- Platform #483, Pages #506 — critical unification directive

**Exploration:** quick (directive from cross-session coordination)
**Status:** captured

## D4: PluginRegistry replaces Catalog — one interface

**Choice:** PluginRegistry with register(), resolve(), availableActions(). Replaces both StepCatalog (read interface in yaml-step-runtime) and adds write capability. Lives in yaml-plugin-api as an SPI. CompositeStepCatalog evolves into the CDI-backed implementation.

**Alternatives:**
- PluginRegistry extends Catalog — read/write split. Adds complexity for no practical benefit since all consumers need the same instance.
- Keep separate, unrelated — maximum backward compat but two parallel APIs for the same concept.

**Rationale:** One interface, one concept. The issue is explicit: register(), resolve(), availableActions(). No read/write split. Consumers and producers use the same type.

**Trade-offs:** StepCatalog deleted. All consumers update imports. CatalogEntry replaced by Definition (from D1/D2).

**Depends on:** D1 (PluginRegistry in yaml-plugin-api), D2 (naming)

**Sources:**
- yaml-step-runtime/src/main/java/io/casehub/yaml/step/StepCatalog.java — interface to replace
- Issue #483 — explicit design for unified registry

**Exploration:** quick
**Status:** captured

## D5: CDI scanner is the default; APT codegen is an optional optimization

**Choice:** Two registration paths, both feeding registry.register():

1. **Default — CDI scanner (fully dynamic):** At startup, discovers @Plugin beans via CDI, reflects on record components to build inputs/outputs, captures constructor + @Execute method as a lambda implementing Action. No generated code needed. Plugin authors just write an @Plugin record and it works.

2. **Optional — APT codegen:** For projects wanting build-time verification (compile-time error detection) and slightly faster startup (no reflection scan). APT generates *Action class + schema. CDI scanner picks up the generated artifacts and registers via the same register() call.

Both paths produce a Definition and call PluginRegistry.register(). The registry is path-agnostic.

**Alternatives:**
- APT-only (current state) — no dynamic path, every plugin requires compile-time codegen. Blocks non-Java registration paths.
- APT demoted to validation-only — loses the startup speed benefit and forces everyone to the reflection path. The issue originally said this, but the pages session refined it.

**Rationale:** Dynamic-by-default lowers the barrier for plugin authors (write a record, done). APT codegen is there for teams that want compile-time safety and faster startup. Both paths converge on register() so the registry, catalog browser, and runtime don't know the difference.

**Trade-offs:** Two code paths to maintain (scanner + APT). But the APT path is already written and working — the new work is the scanner. The scanner is the simpler path (no code generation infrastructure).

**Depends on:** D1 (Definition in yaml-plugin-api), D4 (PluginRegistry)

**Sources:**
- yaml-plugin-processor — existing APT codegen (stays, becomes optional)
- yaml-step-runtime AptPluginSource — existing manifest loading (stays, loads APT output)
- Cross-session coordination — "default is fully dynamic, APT is optional"

**Exploration:** quick (directive from cross-session coordination)
**Status:** captured

## D6: Portability defaults are registration-path-dependent

**Choice:** Extend @Plugin with `portability()` attribute: `@Plugin(value = "name", portability = Portability.JAVA)`. Portability defaults vary by registration path:

- **@Plugin records** → default JAVA (requires JVM to run @Execute method)
- **YAML-defined steps** → inferred from invoke binding kind:
  - `rest`, `graphql`, `mcp` → UNIVERSAL (protocol-based, any runtime can make the call)
  - `process`, `script` → UNIVERSAL (any runtime can spawn a subprocess)
  - `agent` → UNIVERSAL (goes through AgentProvider SPI, protocol-level)
- **Programmatic registration** → explicitly set by caller via Definition builder (no inference)

The YAML source sets portability at registration time based on the invoke binding. This is a registration-path concern, not a PluginRegistry concern — the registry stores whatever portability it receives.

**Alternatives:**
- Default everything to JAVA — misrepresents YAML-defined REST/GraphQL steps. A YAML step with `invoke: { rest: ... }` has no Java dependency; showing JAVA in the catalog is wrong.
- Separate @Portability annotation — adds a second annotation for a single concept.

**Rationale:** Portability should follow from the nature of the step, not from which runtime loaded it. A REST call is a URL and params — it's UNIVERSAL regardless of whether a Java or TS runtime loaded the YAML. @Plugin records ARE Java code, so JAVA is correct.

**Trade-offs:** @Plugin annotation gains a new attribute. Backward compatible — default value means existing @Plugin records work unchanged. YAML source must map invoke binding kind → Portability at registration time.

**Depends on:** D2 (naming — @Plugin not @StepPlugin)

**Sources:**
- yaml-plugin-api/src/main/java/io/casehub/yaml/plugin/api/StepPlugin.java — annotation to extend
- yaml-core/src/main/java/io/casehub/yaml/core/step/InvokeBinding.java — invoke kinds for portability inference
- Issue #483 — portability model (UNIVERSAL, JAVA, TS, BOTH)

**Exploration:** quick
**Status:** captured
