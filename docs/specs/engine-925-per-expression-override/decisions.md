## D1: YAML syntax — map key discriminator

**Choice:** Per-expression override uses map syntax with the language as the key: `when: { jq: ".expr" }` or `when: { mvel: "expr" }`. Plain string retains definition-level language.
**Alternatives:**
- Explicit `lang` + `expr` object (`when: { lang: jq, expr: ".expr" }`) — more verbose, no added clarity
- String prefix convention (`when: "jq:.expr"`) — fragile, ambiguous with colons in JQ
- Per-binding `expressionLang` field — simpler schema change but insufficient granularity: a binding has both `when` and trigger `filter`, and doesn't cover goals/milestones
**Rationale:** Concise, backward-compatible, natural discriminator (language IS the key). Matches the D3 proposal from the #238 spec. JSON schema: `oneOf: [string, object]` with pattern properties.
**Trade-offs:** Map key syntax limits to one key per expression — exactly one language per expression, which is correct. A map with multiple keys (e.g. `{ jq: "a", mvel: "b" }`) would be invalid — schema enforces `maxProperties: 1`.
**Sources:** #238 spec D3, ADR-0009, `CaseDefinitionYamlMapper.java` lines 275-290
**Exploration:** quick
**Status:** captured

## D2: Mapper implementation — shared resolution helper

**Choice:** Extract a single `resolveExpression(JsonNode rawValue, ExpressionEngineRegistry registry, String defaultLang)` helper that handles both plain-string and map-syntax expressions. All expression sites call this helper.
**Alternatives:**
- Inline string-vs-map check at each call site — duplicates the same logic at 5+ sites, risks inconsistency
**Rationale:** DRY. The resolution logic (check if string → use defaultLang; check if object → extract key as language, value as expression, validate single key, call `assertLanguageSupported`) is identical at every site. One helper, one place to maintain.
**Trade-offs:** None — this is purely structural. The helper signature matches the existing `registry.create(expression, lang)` pattern.
**Depends on:** D1 (syntax determines what the helper parses)
**Sources:** `CaseDefinitionYamlMapper` expression sites (lines 566, 570, 614, 1048, 1188)
**Exploration:** quick
**Status:** captured

## D3: TypedMvelRegistry interaction — passthrough for non-MVEL overrides

**Choice:** `TypedMvelRegistry.create()` already checks `if ("mvel".equals(expressionLang))` and only wraps MVEL evaluators. JQ overrides pass through to the delegate registry untouched, producing standard `JQExpressionEvaluator`. No registry changes needed.
**Alternatives:**
- Add explicit override-aware logic to TypedMvelRegistry — unnecessary, current behavior is already correct
**Rationale:** The `TypedMvelRegistry` wrapper only intercepts MVEL creates. A per-expression JQ override calls `registry.create(expr, "jq")` — the wrapper sees `"jq" != "mvel"` and delegates. The JQ evaluator evaluates against JsonNode (the serialized WORKING layer), which is correct.
**Trade-offs:** None — this is a design verification, not a choice.
**Sources:** `TypedMvelRegistry.create()` (CaseDefinitionYamlMapper lines 1548-1554), GE-20260818-68c8a3
**Exploration:** quick
**Status:** captured

## D4: Scope — registry-mediated expression sites, excluding label rules

**Choice:** Registry-mediated expression sites support per-expression override: binding `when`, trigger `filter`, milestone `condition`/`entryCriteria`, goal `condition`, `doneWhen` (completion predicate). Label rules are excluded — they use `CompiledExpression<Map, Boolean>`, a different type system from `ExpressionEvaluator`.
**Alternatives:**
- Include label rules — requires redesigning `LabelRule`'s constructor and evaluation path. Separate concern.
- Binding sites only — inconsistent, users would expect uniform behavior across registry-mediated sites
**Rationale:** Uniform syntax across all registry-mediated sites. Label rules excluded because they use `compileJqBoolean()` with hardcoded JQ and return `CompiledExpression`, not `ExpressionEvaluator`. The shared helper (D2) cannot serve them without a type-system change. `doneWhen` currently bypasses the registry — refactored to route through the helper but with JQ as its default language (not the definition-level `expressionLang`) to preserve backward compatibility.
**Trade-offs:** Label rules remain JQ-only — tracked as a follow-on issue. Switching languages at a specific site changes the evaluation target (JsonNode for JQ, typed POJO for MVEL) — the spec documents this as expected behavior.
**Depends on:** D1 (syntax), D2 (implementation)
**Sources:** Issue #925, #238 spec §Out of Scope, spec review R1-02 (label rule type system), R1-03 (doneWhen compat), R1-07 (evaluation target)
**Exploration:** quick → revised after spec review
**Status:** revised

## D5: Schema model — JSON Schema change drives generated model, mapper reads raw node

**Choice:** The JSON Schema `oneOf: [string, object]` change (§6) causes jsonschema2pojo to generate `Object` return types for expression fields (`getWhen()`, `getCondition()`, etc.). Jackson accepts both strings and maps into `Object` fields without deserialization errors. The mapper reads expression values exclusively from the raw `JsonNode` — the `Object` return type is not used directly.
**Alternatives:**
- Keep String fields and add `DeserializationProblemHandler.handleUnexpectedToken()` — fragile, silently swallows all type mismatches
- Custom `@JsonDeserialize` per field — over-engineered for a parse-time concern
**Rationale:** The JSON Schema change is necessary for YAML validation and documentation. Its side effect — `Object` return types in generated classes — solves the Jackson deserialization problem naturally. The mapper already uses raw JsonNode access for binding/completion parsing. No custom deserialization infrastructure needed.
**Trade-offs:** Schema model accessor return types change from `String` to `Object`. Any code outside the mapper that calls `getWhen()` etc. would need updating. Raw node threading requires additional `JsonNode` parameters on `convertTrigger` and milestone/goal handling methods.
**Depends on:** D1 (syntax), D2 (helper operates on raw JsonNode)
**Sources:** `CaseDefinitionYamlMapper.convertToApiModel()`, spec review R1-01 (Jackson deserialization)
**Exploration:** quick → revised after spec review
**Status:** revised
