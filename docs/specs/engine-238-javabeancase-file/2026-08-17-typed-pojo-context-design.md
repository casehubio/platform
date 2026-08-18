# Typed POJO Context — engine#238

## Summary

Enable case definitions to declare a typed POJO as their working context via YAML,
replacing manual `Map<String, Object>` access with type-safe domain objects. When
`contextType` is declared, the engine automatically constructs a `JacksonPojoBridge<T>`,
infers `expressionLang: mvel` for POJO-native expression evaluation, and passes `T`
to workers via the existing `WorkerFunction<T, R>` API.

## Motivation

Workers currently receive context data as `Map<String, Object>` via `MapBridge`. All
access is string-keyed with manual casting — `ctx.get("transaction")`, `(Transaction) ctx.getAs("transaction", Transaction.class)`. This is error-prone and loses compile-time
type safety. JQ expressions evaluate against `JsonNode`, which is correct for untyped
contexts but unnatural for domain models where field access should be `transaction.amount > 1000`, not `.transaction.amount > 1000`.

The engine already has the building blocks:
- `ContextBridge<T>` — SPI for pluggable context conversion
- `JacksonPojoBridge<T>` — bridge implementation that serializes/deserializes POJOs via Jackson
- `WorkerFunction<T, R>` — generic worker API where `T` is the input type
- `ExpressionEvaluatorFactory` — per-definition `expressionLang` selection (ADR 0009)
- Platform `ExpressionEngineRegistry` with MVEL and JQ engines

What's missing is the YAML-driven wiring that connects these pieces.

## Design

### §1 YAML `contextType` declaration

A case definition declares its context type at the top level:

```yaml
name: aml-transaction-screening
version: "1.0"
contextType: com.example.aml.TransactionCase
# expressionLang defaults to "mvel" when contextType is present
```

The `CaseDefinitionYamlMapper` processes `contextType`:
1. Resolves the class via `BridgeResolver.resolveByTypeNameStrict(contextType)` — this
   existing method throws on missing class (**fail-fast**, no silent fallback to MapBridge
   as `resolveByTypeName()` would)
2. Constructs `JacksonPojoBridge<T>` for the resolved class
3. Sets it as the definition's `defaultWorkerBridge`

When `contextType` is absent, the current behavior is preserved — `MapBridge` is used
and `expressionLang` defaults to `"jq"`.

### §2 Expression engine inference

ADR 0009 establishes `expressionLang` as a per-definition field. This design adds
inference based on `contextType`:

| `contextType` | `expressionLang` (explicit) | Effective engine |
|---------------|----------------------------|------------------|
| absent | absent | JQ (current default) |
| absent | `"jq"` | JQ |
| absent | `"mvel"` | MVEL |
| present | absent | **MVEL (inferred)** |
| present | `"jq"` | JQ (explicit override) |
| present | `"mvel"` | MVEL |

The inference is: `contextType` present + `expressionLang` absent → `"mvel"`. Explicit
`expressionLang` always wins. This supports migration — a definition can declare
`contextType` for typed worker access while keeping `expressionLang: jq` during
a transition period.

### §3 MVEL evaluation context

Currently, JQ expressions evaluate against `context.layer(WORKING).asJsonNode()`.
MVEL evaluates against Java objects natively — property access, method calls, operators.

When `expressionLang` resolves to `"mvel"`, the engine must provide the typed POJO as
the evaluation context. The evaluation path:

1. Engine obtains the `ContextBridge<T>` from the `CaseDefinition`
2. Engine calls `bridge.deserialise(workingLayer.asJsonNode())` to produce `T` from the
   full WORKING layer (distinct from `bridge.initialise()` which takes narrowed input
   for worker dispatch)
3. MVEL evaluates expressions against `T` — `transaction.amount > 1000` accesses
   `T.getTransaction().getAmount()` via JavaBean property resolution

**Caching:** Bridge initialisation is not free (Jackson deserialization). The POJO
should be cached per evaluation cycle (one case context change may trigger multiple
expression evaluations — bindings, goals, milestones). Invalidation signal: subscribe
to `CaseContext.onAnyChange()` — any `set()`/`setAll()`/`remove()` call on the WORKING
layer fires `ContextChangeEvent`, which clears the cached POJO. The cache is scoped
to the evaluation cycle, not the case lifetime.

**Security:** MVEL allows arbitrary method invocation on the evaluation context. For
user-authored YAML expressions, this means calling any method on `T`. Pre-release,
this is acceptable. For production, consider restricting MVEL to property access only
(MVEL's `PropertyAccessor` sandbox) — tracked as a future concern, not a gate.

### §4 Worker dispatch — no API change needed

`WorkerFunction<T, R>` already receives `T` as input. The engine's worker dispatch
pipeline already:
1. Resolves the bridge via `BridgeResolver`
2. Calls `bridge.initialise(context, narrowedInput)` to produce `T`
3. Passes `T` to `WorkerFunction.Sync.fn.apply(t, scope)`
4. On completion, calls `bridge.extractOutput(result)` to write back

When `contextType` is declared, the bridge is `JacksonPojoBridge<T>` instead of
`MapBridge`. The dispatch pipeline doesn't change — the bridge abstraction handles
the difference.

### §5 CaseContext — stays internal

`CaseContext` remains non-generic and internal to the engine. Workers interact with
`T` (their domain POJO) and `WorkerScope` (execution context — caseId, taskId,
data channels, worker composition).

Workers that need engine-level access (layer writes, sub-case coordination) use
`WorkerRuntime.context()` → `WorkerContext` (task metadata, channels, prior workers).
Whether to expose `CaseContext` directly is deferred to engine#912.

### §6 Backward compatibility

| Existing behavior | After #238 |
|-------------------|-----------|
| No `contextType` in YAML | Unchanged — `MapBridge`, JQ, `Map<String, Object>` |
| `expressionLang: jq` explicit | Unchanged |
| Workers using `WorkerFunction<Map<String, Object>, R>` | Still works — `MapBridge` is the default |
| `MapCaseFile` (migration shim) | Unchanged but no longer needed for new typed cases |
| `BridgeResolver` resolution order | Unchanged: definition bridge (if type matches worker inputType) → CDI-discovered bridges → MapBridge (if inputType is Map) → auto-constructed JacksonPojoBridge |

No existing callers break. `contextType` is purely additive.

### §7 `MapCaseFile` deprecation

`MapCaseFile` was a migration shim from casehub-poc, explicitly documented as
"intended as a stepping-stone." With typed POJO contexts available, `MapCaseFile`
is superseded. Mark it `@Deprecated(forRemoval = true)` with a javadoc pointing to
`contextType` as the replacement.

## Out of Scope

- **CaseContext worker exposure** — whether/how workers access CaseContext directly
  (layers, change tracking, get/set) is deferred to engine#912
- **Per-expression language mixing** — ADR 0009 chose per-definition. Per-expression
  YAML syntax is a future schema concern
- **MVEL sandboxing** — restricting MVEL to property-only access for production.
  Pre-release, full MVEL is acceptable
- **Typed SEMANTIC/EPISODIC layers** — T maps to WORKING only (D4). Layer typing
  deferred until a concrete need emerges
- **Classloader isolation** — `resolveByTypeNameStrict` uses `Class.forName` at
  parse time, which may not see runtime classpath in modular deployments. Acceptable
  for pre-release monolith; revisit if modular deployment materialises

## Affected Code

| Area | Module | What changes |
|------|--------|-------------|
| YAML parsing | `api/` | `CaseDefinitionYamlMapper` — parse `contextType`, construct bridge |
| Expression inference | `api/` | `CaseDefinitionYamlMapper` — infer `expressionLang` from bridge type |
| MVEL evaluation | `runtime/` | Expression evaluation points — produce POJO context for MVEL |
| Bridge caching | `runtime/` | Cache bridge-produced POJO per evaluation cycle |
| JacksonPojoBridge fix | `api/` | Register `JavaTimeModule` on ObjectMapper — temporal fields (Instant, LocalDate, Duration) currently throw (GE-20260730-41c406) |
| JSON schema | `schema/` | Add `contextType` field to case definition schema |
| MapCaseFile | `runtime/` | `@Deprecated(forRemoval = true)` |

## Cross-Repo Impact

- **Consumer apps** (AML, clinical, life, devtown) — can adopt `contextType` when ready.
  No forced migration. Existing YAML continues to work unchanged.
- **casehub-worker** — `WorkerFunction<T, R>` is already generic. No change.
- **casehub-platform** — `ExpressionEngineRegistry`, `MvelExpressionEngine` already exist.
  No platform changes needed.

## References

- `api/src/main/java/io/casehub/api/context/ContextBridge.java` — bridge SPI
- `api/src/main/java/io/casehub/api/context/JacksonPojoBridge.java` — existing POJO bridge
- `api/src/main/java/io/casehub/api/context/MapBridge.java` — default untyped bridge
- `api/src/main/java/io/casehub/api/context/CaseContext.java` — engine internal context
- `api/src/main/java/io/casehub/api/model/converter/CaseDefinitionYamlMapper.java` — YAML parser
- `docs/adr/0009-expression-lang-granularity.md` — ADR: per-definition expressionLang
- `worker/api/src/main/java/io/casehub/worker/api/WorkerFunction.java` — generic worker API
- GE-20260615-35f52f — panels refactor broke JQ bindings (asJsonNode shape change)
- GE-20260730-41c406 — WritableLayerImpl.asJsonNode() fails on Instant fields
- GE-20260512-59a501 — snapshot() loses subclass type
- engine#912 — deferred: CaseContext worker API surface
