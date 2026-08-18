## D1: Typed context model — Worker<T>, CaseContext internal

**Choice:** Worker API is generic: `Worker<T>`. Workers get `T` directly — their domain POJO. `CaseContext` stays non-generic and internal to the engine. The `ContextBridge<T>` projects the WORKING layer into `T` at dispatch and extracts changes back on completion.
**Alternatives:**
- `CaseContext<T>` generic — adds a type parameter to an interface where most methods (`get(String)→Object`, `set(String, Object)`) are intrinsically untyped. `getData()→T` is useful but `get("key")` has unclear semantics when T is a POJO.
- Separate `JavaBeanCaseFile<T>` wrapper — adds a parallel type hierarchy
**Rationale:** The context IS T. Workers shouldn't need CaseContext — it's engine infrastructure (layers, diffs, change events). Whether/how to expose CaseContext to workers is deferred to engine#912.
**Trade-offs:** Workers cannot access SEMANTIC/EPISODIC layers or change tracking. Acceptable — those are engine concerns, not worker concerns.
**Sources:** engine `api/src/main/java/io/casehub/api/context/CaseContext.java`, `ContextBridge.java`, `JacksonPojoBridge.java`
**Exploration:** quick → revised after adversarial review
**Status:** revised

## D2: CaseContext worker access — DEFERRED

**Choice:** Deferred to engine#912. For #238, CaseContext is not exposed to workers.
**Rationale:** The question of what API CaseContext should expose (generic type parameter, layer access, get() semantics, read-only vs read-write) is a standalone design decision with multiple open tensions. #238 delivers the typed worker model without resolving CaseContext exposure.
**Exploration:** quick
**Status:** deferred (engine#912)

## D3: Expression engine selection — inferred from bridge type

**Choice:** Default expression engine is inferred from the `ContextBridge` output type: POJO → MVEL, `Map`/`JsonNode` → JQ. Per-expression override via YAML map syntax: `when: { mvel: "expr" }` or `when: { jq: "expr" }`.
**Alternatives:**
- Per case definition — `expressionEngine: mvel` in YAML header. Too coarse — can't mix engines within a definition.
- Per expression only — every expression must declare its engine. Verbose, no sensible default.
**Rationale:** Zero config for the 90% case. The context type already implies the natural expression language. Override syntax maps cleanly to existing `ExpressionEngineRegistry` type discriminators.
**Trade-offs:** Implicit default may surprise users who expect JQ everywhere. MVEL allows method invocation on POJOs (security surface) — consider restricting to property access only. Migration path needed when adding `contextType` to definitions with existing JQ expressions.
**Sources:** platform `ExpressionEngineRegistry`, `MvelExpressionEngine`, `JQExpressionEngine`
**Exploration:** quick → clarified after adversarial review
**Status:** captured

## D4: Layer model — T maps to WORKING layer only

**Choice:** T is the typed projection of the WORKING layer. SEMANTIC and EPISODIC layers are engine-internal — workers don't access them (CaseContext not exposed per D1/D2).
**Alternatives:**
- T spans all layers via composition — forces users to model layers they don't care about
- Typed bridges per layer — more wiring for a need that doesn't exist yet
**Rationale:** WORKING is the user's domain data. SEMANTIC/EPISODIC are engine infrastructure. Layer exposure deferred to engine#912.
**Trade-offs:** None for workers — they never needed layer access. Engine-internal code continues using CaseContext with full layer access.
**Sources:** `CaseContextImpl` layered model (WORKING, SEMANTIC, EPISODIC), `WritableLayerImpl`
**Exploration:** quick
**Status:** captured

## D5: YAML declaration of context type — on the case definition

**Choice:** Case definition declares `contextType: com.example.MyCase` in YAML. Engine constructs `JacksonPojoBridge<MyCase>` automatically. One case, one context type. Fail-fast if class not on classpath (not silent fallback to MapBridge).
**Alternatives:**
- Per worker/binding — each binding declares its own context type. Adds complexity, unclear when different workers on the same case would need different context shapes.
- CDI bridge registration — developer registers a `ContextBridge<MyCase>` bean, engine discovers by type. No YAML change but requires CDI wiring for something that's pure configuration.
**Rationale:** The context type is a property of the case, not the worker. All workers operating on the same case share the same domain data shape. YAML is the natural place for case-level configuration.
**Trade-offs:** Can't have different context types per worker on the same case.
**Sources:** `CaseDefinition.getDefaultWorkerBridge()`, `BridgeResolver`
**Exploration:** quick → clarified after adversarial review (fail-fast)
**Status:** captured
