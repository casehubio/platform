# Label Infrastructure Design

**Issue:** casehubio/platform#187
**Date:** 2026-07-19
**Status:** approved

## Problem

Two domains (casehub-engine and casehub-work) need to evaluate conditions against
subjects and mutate labels based on results. Labels drive view/queue membership
via the existing view toolkit (`SubjectViewOrchestrator`, `LabelPatternMatcher`).

Work already has this coupled to JEXL and work-specific types (`FilterRegistryEngine`,
`FilterDefinition`, `JexlConditionEvaluator`). Engine needs the same capability for
case queues (engine#730). Rather than duplicate, extract the shared vocabulary to
platform.

## Design

### Scope

Two types in `platform-api/io.casehub.platform.api.label`. Zero new modules, zero
new SPIs, zero persistence. The platform provides the shared vocabulary and trivial
evaluation logic. Domains own compilation, storage, and application of mutations.

The `label` package is separate from `view` — label mutation types describe
condition-driven add/remove semantics, not view membership. The view toolkit
consumes labels (via `LabelPatternMatcher`) but label infrastructure doesn't
depend on views.

### LabelAction

Sealed interface for label mutations:

```java
package io.casehub.platform.api.label;

import java.util.Objects;

public sealed interface LabelAction permits LabelAction.Add, LabelAction.Remove {
    String label();

    record Add(String label) implements LabelAction {
        public Add {
            Objects.requireNonNull(label, "label must not be null");
            if (label.isBlank()) throw new IllegalArgumentException("label must not be blank");
        }
    }

    record Remove(String label) implements LabelAction {
        public Remove {
            Objects.requireNonNull(label, "label must not be null");
            if (label.isBlank()) throw new IllegalArgumentException("label must not be blank");
        }
    }
}
```

Common `label()` accessor on the sealed interface — callers can get the label string
without pattern matching when they don't care about the direction.

### LabelRule

Condition + actions record with static evaluation:

```java
package io.casehub.platform.api.label;

import io.casehub.platform.api.expression.CompiledExpression;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record LabelRule(
        String name,
        CompiledExpression<Map<String, Object>, Boolean> condition,
        List<LabelAction> actions) {

    public LabelRule {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(condition, "condition must not be null");
        actions = List.copyOf(actions);
    }

    public static List<LabelAction> evaluate(
            List<LabelRule> rules, Map<String, Object> context) {
        return rules.stream()
                .filter(r -> Boolean.TRUE.equals(r.condition().eval(context)))
                .flatMap(r -> r.actions().stream())
                .toList();
    }
}
```

Key decisions:

- **`CompiledExpression<Map<String, Object>, Boolean>`** — not `ExpressionEvaluator`.
  `ExpressionEvaluator` is a specification marker (`type()` only, no `eval()`).
  `CompiledExpression` is the runtime evaluable form. All registered engines
  (MVEL3, JQ, Lambda) produce compatible `CompiledExpression<Map<String, Object>, R>`
  instances — see JQ engine fix below.
- **`Boolean.TRUE.equals(...)`** — null-safe. A condition returning null is treated
  as false, not NPE.
- **`List.copyOf(actions)`** — defensive copy in compact constructor, immutable.
- **Fail-fast on condition errors** — if a condition throws during evaluation, the
  exception propagates. Partial results from an incomplete rule set are of unknown
  correctness: a skipped rule might have contradicted an earlier rule's actions
  (e.g., a throwing `Remove` after a successful `Add` leaves a label that should
  have been removed). Rule evaluation is a unit of work — either all rules are
  evaluated or none. Callers who prefer skip-and-log semantics can implement
  per-rule exception handling trivially by iterating rules directly; the static
  `evaluate()` method provides the strict default.
- **No deduplication** — returns all actions from all matching rules in order.
  Caller decides whether to deduplicate.
- **Rule ordering is significant** — when rules produce conflicting actions for the
  same label (e.g., one rule adds `priority/high`, another removes it), the result
  depends on evaluation order. Applied sequentially, `[Add, Remove]` yields no
  label; `[Remove, Add]` yields the label present. Rule authors must be aware that
  ordering determines the outcome when conflicts exist.

### Domain usage pattern

```java
// 1. Compile rules once (from CaseDefinition, config, wherever)
var rules = definitions.stream()
    .map(d -> new LabelRule(d.name(),
        registry.compile(d.language(), d.expression(), Map.class, Boolean.class),
        d.labelActions()))
    .toList();

// 2. On domain event, build context and evaluate
var context = new HashMap<String, Object>();
context.put("severity", "HIGH");
context.put("assignee", null);
var actions = LabelRule.evaluate(rules, context);

// 3. Apply mutations (domain-specific)
for (var action : actions) {
    switch (action) {
        case LabelAction.Add a -> entity.addLabel(a.label());
        case LabelAction.Remove r -> entity.removeLabel(r.label());
    }
}

// 4. Re-evaluate view membership (existing infrastructure)
orchestrator.evaluateAndTrack(entity.getId(), tenancyId, entity.getLabelPaths());
```

### What's excluded (and why)

| Proposed in issue | Excluded | Reason |
|---|---|---|
| `Labellable` interface | Yes | View toolkit works with `Set<String>`, not interfaces. Evaluator is functional — returns actions, doesn't mutate subjects. |
| `LabelRuleRecord` | Yes | Persistence DTO — domains own their rule storage (CaseDefinition, FilterDefinition). |
| `LabelRuleStore` SPI | Yes | No consumer for a generic store. Engine and work already persist rules in domain-specific structures. |
| `LabelRuleCompiler` | Yes | One line of code: `registry.compile(lang, expr, Map.class, Boolean.class)`. Not worth a class. |
| `platform-label/` module | Yes | Two types in platform-api is the entire deliverable. Zero new modules. |
| `platform-label-jpa/` | Yes | No LabelRuleStore means no persistence adapters needed. |
| `platform-label-inmem/` | Yes | Same. |

### JQ engine fix (included in this issue)

`JQExpressionEngine.compile()` currently ignores its `contextType` parameter —
it always returns `CompiledExpression<JsonNode, R>` and hides the mismatch with
`@SuppressWarnings("unchecked")`. When a caller requests `Map.class` context, the
returned expression throws `ClassCastException` at eval time. This violates the
`ExpressionEngine.compile()` contract.

**Fix:** when `contextType != JsonNode.class`, wrap the internal JQ expression with
a `MapAdaptedJQExpression` that converts `Map<String, Object>` → `JsonNode` via
`ObjectMapper.valueToTree()` before delegating:

```java
private record MapAdaptedJQExpression<R>(
        CompiledExpression<JsonNode, R> delegate,
        ObjectMapper mapper)
        implements CompiledExpression<Map<String, Object>, R> {

    @Override public String type() { return "jq"; }

    @Override public R eval(Map<String, Object> context) {
        return delegate.eval(mapper.valueToTree(context));
    }
}
```

`CacheKey` must also include `contextType` — without it, a cached `JsonNode`-context
expression could be returned for a `Map`-context caller.

After this fix, `registry.compile("jq", expr, Map.class, Boolean.class)` returns a
`CompiledExpression<Map<String, Object>, Boolean>` that works correctly. All three
engines (MVEL3, JQ, Lambda) are fully compatible with `LabelRule.condition`.

### Expression engine note

JEXL is not added — MVEL3 covers the same Java-like expression niche. Work's
existing JEXL expressions migrate to MVEL3 when work adopts the platform label
infrastructure.

If a lighter-weight interpreted engine is needed later, adding one is an
`ExpressionEngine` SPI implementation — no changes to the label types required.

## Testing

`LabelRule.evaluate()` is a pure function. Tests use `LambdaExpression` conditions
(no expression engine dependency):

- All rules match — all actions returned in order
- No rules match — empty list
- Mixed match — only matching rules' actions returned
- Condition returns null — treated as false (not NPE)
- Condition throws — exception propagates (fail-fast)
- Empty rules list — empty actions list
- Rule with empty actions list — no actions contributed from that rule
- Multiple rules adding same label — both Add actions in result (no dedup)

`LabelAction`: both `Add` and `Remove` reject null labels via `Objects.requireNonNull`
and blank labels via `isBlank()` in compact constructors.

`JQExpressionEngine` Map adaptation:

- `compile()` with `contextType=Map.class` returns `CompiledExpression<Map, Boolean>` that evaluates correctly
- `compile()` with `contextType=JsonNode.class` returns `CompiledExpression<JsonNode, Boolean>` (unchanged behavior)
- Cache distinguishes `Map` vs `JsonNode` context for same expression
- `MapAdaptedJQExpression.eval()` converts map to JsonNode and delegates

## Downstream consumers

- **casehub-engine** (engine#730): `CaseInstance` gets mutable labels. Engine compiles
  rules from `CaseDefinition`, calls `LabelRule.evaluate()` on lifecycle events, applies
  mutations, calls `SubjectViewOrchestrator.evaluateAndTrack()`. Note: engine#730's
  issue body references `Labellable` and `LabelRuleEvaluator` from the original design
  proposal. The refined API is functional: `LabelRule.evaluate()` returns actions, the
  domain applies them. Engine#730's issue body should be updated to reflect this.
- **casehub-work** (future): `WorkItem` already has labels. `FilterRegistryEngine`
  migrates label evaluation to `LabelRule.evaluate()`. JEXL expressions migrate to MVEL3.
  Non-label actions (SET_PRIORITY, OVERRIDE_CANDIDATE_GROUPS) remain work-owned.
