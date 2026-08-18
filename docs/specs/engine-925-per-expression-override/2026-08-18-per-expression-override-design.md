# Per-Expression Language Override — engine#925

## Summary

Enable individual expressions within a case definition to override the definition-level
`expressionLang` via YAML map syntax: `when: { jq: ".expr" }` or `when: { mvel: "expr" }`.
Plain-string expressions continue using the definition-level language. All expression sites
support the override uniformly.

## Motivation

ADR-0009 chose per-definition `expressionLang` for simplicity and CNCF SW 1.0 alignment.
Issue #238 added `contextType` inference: when a case definition declares a typed POJO,
the engine infers `expressionLang: mvel` for POJO-native property access. This leaves
a gap: a typed-POJO definition that needs one JQ expression for JSON projection (e.g., a
data transformation in a trigger filter) must currently set `expressionLang: jq` globally,
losing MVEL inference for everything else.

Per-expression override closes this gap without changing the default model. The definition-level
language remains the default; individual expressions opt into a different language when needed.

## Design

### §1 YAML syntax — map key discriminator

Every expression field accepts two forms:

```yaml
# Plain string — uses definition-level expressionLang (unchanged)
when: ".amount > 1000"

# Map override — language as key, expression as value
when: { jq: ".amount > 1000" }
when: { mvel: "transaction.amount > 1000" }
```

The map must have exactly one key (the language). A map with zero or multiple keys is
rejected at parse time with a descriptive error. The language must be registered with
`ExpressionEngineRegistry` — `assertLanguageSupported(lang)` runs before evaluator creation.

Existing plain-string expressions are entirely unchanged. The map syntax is purely additive.

### §2 Expression sites

Registry-mediated expression sites in `CaseDefinitionYamlMapper` support per-expression
override:

| Site | Current accessor | Location |
|------|-----------------|----------|
| Binding `when` | `schemaBinding.getWhen()` | `convertBinding()` |
| Trigger `filter` | `schemaTrigger.getContextChange().getFilter()` | `convertTrigger()` |
| Milestone `condition` | `sm.getCondition()` | milestone loop in `convertToApiModel()` |
| Milestone `entryCriteria` | `sm.getEntryCriteria()` | milestone loop in `convertToApiModel()` |
| Goal `condition` | `sg.getCondition()` | goal loop in `convertToApiModel()` |
| `doneWhen` | `completionNode.get("doneWhen")` | completion handling in `convertToApiModel()` |

`doneWhen` currently bypasses the registry entirely (`new JQExpressionEvaluator(doneWhen)`
directly). This must be refactored to route through the shared helper. To preserve backward
compatibility, `doneWhen`'s default language is always `"jq"` (not the definition-level
`expressionLang`). Users who want MVEL `doneWhen` use the override: `doneWhen: { mvel: "expr" }`.

**Excluded:** Label rule conditions use `CompiledExpression<Map<String, Object>, Boolean>` —
a different type system from `ExpressionEvaluator`. They use `compileJqBoolean()` with
hardcoded JQ compilation and `validateJqSyntax()`. Making them registry-aware requires
redesigning `LabelRule`'s constructor and evaluation path — a separate refactoring tracked
as a follow-on issue.

### §3 Shared resolution helper

A single static method handles both plain-string and map-syntax expressions:

```java
private static ExpressionEvaluator resolveExpression(
    JsonNode rawValue,
    ExpressionEngineRegistry registry,
    String defaultLang) {
  if (rawValue == null || rawValue.isNull()) return null;
  if (rawValue.isTextual()) {
    return registry.create(rawValue.asText(), defaultLang);
  }
  if (rawValue.isObject() && rawValue.size() == 1) {
    var entry = rawValue.fields().next();
    String lang = entry.getKey();
    String expr = entry.getValue().asText();
    registry.assertLanguageSupported(lang);
    return registry.create(expr, lang);
  }
  throw new IllegalArgumentException(
      "Expression must be a string or single-key map {lang: expr}, got: " + rawValue);
}
```

All expression sites call `resolveExpression(rawNode, registry, expressionLang)` instead of
`registry.create(schemaField, expressionLang)`. The helper returns `ExpressionEvaluator`
directly — the caller doesn't need to know whether the expression was overridden.

### §4 Raw JsonNode threading

The helper operates on `JsonNode`, not schema model strings. The JSON Schema change (§6)
causes jsonschema2pojo to generate `Object` fields for expression sites (since `oneOf:
[string, object]` cannot be represented as a single Java type). This means `getWhen()`,
`getCondition()`, etc. return `Object` instead of `String`. Jackson accepts both strings
and maps into `Object` fields without deserialization errors. The mapper reads expression
values exclusively from the raw `JsonNode` — the schema model's `Object` return type is
not used directly.

The raw node is already available in `convertToApiModel()` and threaded to `convertBinding()`
via `rawBindingNode`. Sites that don't currently receive a raw node (milestones, goals,
trigger filter within `convertTrigger()`) need the corresponding raw node threaded from
the parent:

- **Milestones:** iterate `rawNode.get("spec").get("milestones")` alongside `schema.getSpec().getMilestones()`
- **Goals:** iterate `rawNode.get("spec").get("goals")` alongside `schema.getSpec().getGoals()`
- **Trigger filter:** `convertTrigger()` receives the raw trigger node; read `contextChange.filter` from it
- **doneWhen:** already read from `completionNode.get("doneWhen")` — a `JsonNode`, ready for the helper

This follows the existing pattern used for binding raw node access.

### §5 TypedMvelRegistry interaction

When `contextType` is set and `expressionLang` resolves to `"mvel"`, the mapper wraps the
registry in `TypedMvelRegistry` (private inner class of `CaseDefinitionYamlMapper`).
`TypedMvelRegistry.create()` checks `if ("mvel".equals(expressionLang))` and wraps the
evaluator in `TypedMvelExpressionEvaluator` carrying the context class. For non-MVEL
languages, it delegates to the underlying registry untouched.

A per-expression JQ override calls `registry.create(expr, "jq")` through the
`TypedMvelRegistry`. The wrapper sees `"jq" != "mvel"` and delegates to the underlying
`DefaultExpressionEngineRegistry`, which produces a standard `JQExpressionEvaluator`.
At evaluation time, the JQ evaluator operates on the WORKING layer's `JsonNode`
representation — not the typed POJO. This is the expected and correct behavior: JQ
evaluates JSON, MVEL evaluates typed objects. No registry changes needed.

### §6 JSON Schema update

Each expression field in the case definition JSON Schema becomes a union type:

```json
{
  "oneOf": [
    { "type": "string" },
    {
      "type": "object",
      "minProperties": 1,
      "maxProperties": 1,
      "additionalProperties": { "type": "string" }
    }
  ]
}
```

The `maxProperties: 1` constraint enforces single-key maps at the schema level.
`additionalProperties: { "type": "string" }` ensures the value is always a string
(the expression text).

### §7 Evaluation target semantics

Switching languages at a specific expression site changes the evaluation target:

| Language | Evaluation target | Field access syntax |
|----------|------------------|---------------------|
| JQ | `context.layer(WORKING).asJsonNode()` | `.transaction.amount` |
| MVEL | Typed POJO (via `ContextBridge`) | `transaction.amount` |

This is inherent in having multiple expression languages and is the intended use case.
A user who overrides a binding's `when` from MVEL to JQ does so because they want JSON
semantics at that site (e.g., JSON projection, null-coalescing with `//`, array operations).
The evaluation target difference is not a trap — it's the feature.

### §8 Backward compatibility

| Existing behavior | After #925 |
|-------------------|-----------|
| Plain-string expressions | Unchanged — use definition-level `expressionLang` |
| `expressionLang: jq` (explicit) | Unchanged |
| `expressionLang: mvel` (explicit or inferred) | Unchanged |
| `doneWhen` always JQ | Supports map override (`doneWhen: { mvel: "expr" }`); default remains JQ for backward compat |
| Schema model `getWhen()` etc. | Returns `Object` (was `String`) — mapper reads from raw `JsonNode` |
| Java DSL (programmatic definitions) | Unchanged — already supports per-evaluator language via `ExpressionEvaluator.type()` |

No existing YAML definitions break. `doneWhen`'s default language remains JQ regardless
of definition-level `expressionLang` — preserving current behavior exactly. Users who want
MVEL `doneWhen` use the new override syntax explicitly: `doneWhen: { mvel: "expr" }`.

Schema model accessor return types change from `String` to `Object` for expression fields.
The mapper already reads from raw `JsonNode` for these fields, so call sites that use the
schema model accessors (if any exist outside the mapper) would need updating.

## Out of Scope

- **New expression engine types** — this design supports any engine registered with the
  registry. Adding new engines (e.g., Drools, SpEL) is an orthogonal concern.
- **MVEL sandboxing** — restricting MVEL to property-only access for production. Tracked
  separately (pre-release, full MVEL is acceptable).
- **Data transform overrides** — `inputProjection`/`outputProjection` are always JQ
  (JSON-to-JSON transforms). Per-expression override could apply to them but is not
  included in this scope — they don't use `registry.create()` and would need different
  plumbing.

## Affected Code

| Area | Module | What changes |
|------|--------|-------------|
| Expression resolution | `api/` | New `resolveExpression()` helper in `CaseDefinitionYamlMapper` |
| Binding `when` | `api/` | `convertBinding()` — use helper instead of `registry.create()` |
| Trigger filter | `api/` | `convertTrigger()` — thread raw trigger node, use helper |
| Milestone conditions | `api/` | Milestone loop — thread raw milestone nodes, use helper |
| Goal condition | `api/` | Goal loop — thread raw goal nodes, use helper |
| `doneWhen` | `api/` | Completion handling — route through helper instead of direct constructor |
| JSON Schema | `schema/` | Expression fields become `oneOf: [string, object]` |
| ADR | `docs/adr/` | Update ADR-0009 status or write ADR-0010 superseding it |
| Tests | `api/` | New YAML fixtures exercising map syntax at each site |

## Cross-Repo Impact

None. This is entirely within the engine's `api` module (YAML parsing). The platform's
`ExpressionEngineRegistry` and engine implementations are unchanged. Consumer apps can
adopt the syntax when ready — existing YAML continues to work unchanged.

## References

- `api/src/main/java/io/casehub/api/model/converter/CaseDefinitionYamlMapper.java` — YAML parser
- `api/src/main/java/io/casehub/api/engine/ExpressionEngineRegistry.java` — registry SPI
- `runtime/src/main/java/io/casehub/engine/internal/engine/DefaultExpressionEngineRegistry.java` — registry impl
- `docs/adr/0009-expression-lang-granularity.md` — ADR: per-definition expressionLang
- #238 spec D3 — per-expression override proposal
- GE-20260420-18fbd4 — ExpressionEvaluator marker interface dispatch pattern
- GE-20260818-68c8a3 — TypedEvaluator pattern for carrying context class
- GE-20260609-eee30f — backward-compatible SPI extension pattern (supportsStringCreation)
