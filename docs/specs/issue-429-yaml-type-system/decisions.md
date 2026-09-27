# Decisions — yaml-core type system polish

## D1: Unified type vocabulary

**Choice:** Extract `ValueType` enum to `io.casehub.yaml.core.type`, replacing `CsvColumnType`. `ParameterType` delegates scalar ops to `ValueType` and keeps LIST.
**Alternatives:**
- Keep CsvColumnType, add ValueType alongside — three type enums is worse than two
- Merge ParameterType into ValueType with LIST — LIST is a compound type, doesn't belong with scalars
**Rationale:** One scalar type vocabulary used by CSV, variable declarations, and build-time validation. ParameterType stays as the module-parameter-specific type with widening rules.
**Trade-offs:** CsvColumnType deletion is a breaking change for any direct consumer (none found outside yaml-core).
**Sources:** CsvColumnType.java, ParameterType.java, issue #429
**Exploration:** deep-analysis
**Status:** captured

## D2: resolveTyped scalar-only rule

**Choice:** When `resolveTyped()` resolves a root object (via ObjectVariableSource) and there's no field path to drill, return null if the root is a Map or List. Fall through to string resolution.
**Alternatives:**
- Add an `isContainer()` method to ObjectVariableSource — API complexity for a case that has zero production usage
- Modify resolveTyped to try full-name resolution first — adds a second resolution attempt per call, more complex
**Rationale:** Eliminates the dual-purpose alias tension entirely. `${each.region}` (Map root, no field) → null → string fallback → "us-east". `${each.region.tier}` (Map root, drill "tier") → Integer 500. ObjectVariableSource is unused in production so no backward compatibility concern.
**Trade-offs:** A future source that intentionally returns a Map/List as a terminal value would need a different resolution method. Not needed today.
**Sources:** VariableResolver.java:110-135, ForEachExpander.java:255-280, CorpusVariableSource.java (unused)
**Exploration:** deep-analysis
**Status:** captured

## D3: forEach + loop on imports (block-level)

**Choice:** Add `forEach` and `loop` fields to `YamlImport`. Module is the universal block abstraction for both compile-time expansion and runtime iteration.
**Alternatives:**
- Inline block construct on nodes (`block:` wrapper) — adds a new YAML construct; modules already serve as blocks
- forEach inheritance (parent scope propagates) — implicit, fragile, hard to trace
**Rationale:** Fills the 2x2 gap (step vs block × compile-time vs runtime) with no new YAML constructs. Module is already the block abstraction — just wire it to the iteration mechanisms.
**Trade-offs:** Expansion order becomes three-phase (forEach on imports → ModuleExpander → forEach on nodes). Slightly more complex expansion pipeline.
**Sources:** YamlImport.java, ForEachExpander.java, LoopDirective.java, ModuleExpander.java
**Exploration:** quick
**Depends on:** D1 (typed values must flow through block-level expansion too)
**Status:** captured

## D4: Typed variable declarations

**Choice:** `TypedMap` record carrying `Map<String, ValueType>` schema + `Map<String, Object>` typed values. `TypedName.parse()` extracts `name:type` syntax. Variables registered via both VariableSource (string fallback) and ObjectVariableSource (typed resolution).
**Alternatives:**
- TypedVariableSource (new interface combining schema + values) — unnecessary new abstraction when TypedMap + existing interfaces work
- Schema on the resolver itself — makes VariableResolver schema-aware, adds complexity to a core class
**Rationale:** TypedMap is a simple data carrier. The existing VariableSource/ObjectVariableSource pair handles resolution. Schema queryable from TypedMap.schema() for build-time tools.
**Trade-offs:** Schema and resolver are separate — build tools query TypedMap directly rather than asking the resolver.
**Sources:** VariableResolver.java, issue #429
**Exploration:** quick
**Depends on:** D1 (ValueType), D2 (resolveTyped fix)
**Status:** captured

## D5: Final conformance audit

**Choice:** After all changes land, sweep the codebase for violations: type safety gaps, duplicate resolution paths, non-normalised entry points, expansion asymmetries. Findings become follow-up issues.
**Alternatives:**
- Skip audit, trust the implementation — misses emergent gaps
- Audit during implementation — too early, findings change as code lands
**Rationale:** The audit verifies the branch achieved its goals. Running it last means it catches everything, including interactions between changes.
**Trade-offs:** None — it's additive.
**Sources:** User requirement
**Exploration:** quick
**Status:** captured

## D6: Step walker placement

**Choice:** yaml-step-runtime. StepWalker is a static utility taking StepCatalog, returning resolved steps. Not in yaml-core.
**Alternatives:**
- yaml-core with `Set<String>` knownNames parameter — pure, zero-dep, J2CL-safe. But every caller would immediately need `catalog.resolve()` on the result. No current consumer needs the structural parse without the catalog.
**Rationale:** The walker is a catalog-integrated operation. The language-definition argument for yaml-core is real but theoretical — if a future consumer needs a catalog-free parse, extracting the key-classification logic is a clean refactor.
**Trade-offs:** Walker can't be used without yaml-step-runtime on the classpath. Acceptable — step execution inherently requires the runtime.
**Sources:** StepCatalog.java, StepDefinitionParser.java (yaml-core pattern), ForEachExpander.java (yaml-core pattern), issue #447
**Exploration:** deep-analysis
**Status:** captured

## D7: YAML surface — plugin-name-as-key + invoke escape hatch

**Choice:** Plugin-name-as-key is the primary surface. `invoke:` remains as escape hatch. Legacy `action:` + `data:` from #151 spec is dropped entirely.
**Alternatives:**
- Keep action: + data: alongside plugin-name-as-key — two surfaces for the same concept, confusing vocabulary
- Drop invoke: too, require plugins for everything — too rigid, ops teams need ad-hoc endpoint calls
**Rationale:** action: + data: was never implemented (pre-implementation design from #151 that #447 explicitly supersedes). Plugin-name-as-key is the schema-validated, IDE-completable primary path. invoke: is the flexible escape hatch.
**Trade-offs:** No migration path from action: + data: needed — it was never built.
**Sources:** Issue #447 (explicit target syntax), issue #151 spec §YAML surface syntax, dynamic step catalog spec §Playbook integration
**Exploration:** deep-analysis
**Status:** captured

## D8: P1 plugin module placement

**Choice:** P1 plugins (process, rest-call, assert) in yaml-step-runtime, package `io.casehub.yaml.step.plugin`. Add yaml-plugin-processor as annotation processor in the POM.
**Alternatives:**
- New `yaml-step-plugins/` module — cleaner separation but adds a module for 3 records. Warranted for domain-specific plugins, not foundational vocabulary.
**Rationale:** P1 plugins are foundational vocabulary — they belong with the catalog infrastructure. yaml-step-runtime already has the right dependencies (platform-api, yaml-plugin-api). APT processor is one `<annotationProcessorPaths>` entry.
**Trade-offs:** yaml-step-runtime gains APT processing. Acceptable — the module is the integration layer.
**Sources:** yaml-step-runtime/pom.xml, yaml-plugin-processor APT (BinderEmitter.java, SchemaEmitter.java)
**Exploration:** deep-analysis
**Status:** captured

## D9: CdiServiceRegistry

**Choice:** `CdiServiceRegistry @ApplicationScoped` in yaml-step-runtime using `Arc.container().instance(serviceType)`.
**Alternatives:**
- Standard CDI BeanManager.getReference() — more portable but more verbose
- Separate module (yaml-step-cdi/) — unnecessary, yaml-step-runtime already has quarkus-arc as provided scope
**Rationale:** Arc API is simpler, module already depends on quarkus-arc. Spring counterpart follows the established `-spring` module pattern when needed (not in #447 scope).
**Trade-offs:** Quarkus-specific rather than CDI-standard. Acceptable — the project is Quarkus-native with Spring as a generated layer.
**Sources:** MapServiceRegistry.java (test/standalone impl), quarkus-arc dependency in yaml-step-runtime/pom.xml
**Exploration:** quick
**Status:** captured

## D10: Reserved keys — fixed set

**Choice:** Fixed set from issue-386 decorator evaluation order (when, on-success, on-failure, forEach, loop, retry, timeout, delay, on-error, trigger, transform, signal, publish, transition, parallel, semaphore, barrier, quorum, race) plus structural keys (step, invoke).
**Alternatives:**
- Extensible reserved key set — unnecessary complexity. Decorators are a closed set by design (issue-386 §decorator evaluation order).
**Rationale:** Decorator keys are the engine's fixed vocabulary. Plugin names are the open set. The walker checks reserved keys first, then matches against the catalog.
**Trade-offs:** Adding a new decorator key requires a code change. By design — new decorators are language changes, not plugin extensions.
**Sources:** Issue-386 §Decorator Evaluation Order (13-position stack)
**Exploration:** quick
**Status:** captured

## D11: Schema composition

**Choice:** `StepSchemaComposer` utility in yaml-step-runtime. Reads from catalog, produces composed JSON Schema with oneOf per plugin + decorator properties. File output is #437's concern.
**Alternatives:**
- Method on CompositeStepCatalog — mixes resolution and schema concerns
- Separate module — overkill for a utility class
**Rationale:** Keeps catalog focused on resolution. StepSchemaComposer is a pure function: catalog → schema JSON. The Maven plugin (#437) calls it and writes to disk.
**Trade-offs:** Requires Jackson for schema JSON construction. yaml-step-runtime already has jackson-databind.
**Sources:** SchemaEmitter.java (APT schema generation), META-INF/yaml-plugins/<name>.schema.json format
**Exploration:** quick
**Status:** captured
