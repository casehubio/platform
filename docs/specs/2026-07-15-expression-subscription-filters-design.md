# Expression-Based Subscription Filters

**Issue:** casehubio/platform#151
**Date:** 2026-07-15
**Status:** Approved

## Summary

Replace the structured `Constraint(field, op, value)` model in notification
subscriptions with `List<ExpressionEvaluator>` filters — typed expressions
compiled directly by the `ExpressionEngineRegistry`. This eliminates the
`Constraint`/`ConstraintOp`/`ConstraintCompiler` abstraction layer, which is
a limited DSL that would need continual extension (OR, NOT, compound groups,
cross-field comparisons) to approach what MVEL and JQ already provide natively.

## Motivation

The current `Constraint` model is a dead end:

- Only flat AND'd field/op/value triples — no OR, no nesting, no cross-field logic
- `ConstraintCompiler` translates constraints to MVEL internally anyway
- Every new capability requires extending `ConstraintOp` and the compiler
- The expression SPI (landed in #141) already provides MVEL and JQ engines

The alpha network infrastructure (`DataSourceRouter` → `AlphaDataSource` →
`TypeNode` → `FilterNode` with collapsed sharing) is already generic — it only
cares about `FilterExpression`. The subscription layer is the only part that
needs to change.

## Design

### Data Model Changes (platform-api)

**Subscription** record:

```java
public record Subscription(
        String id,
        String ownerId,
        String tenancyId,
        String name,
        String eventType,
        List<ExpressionEvaluator> filters,   // was: List<Constraint> constraints
        List<NotificationTarget> targets,
        boolean includeActor,
        NotificationTemplate template,
        boolean enabled,
        SubscriptionScope scope,
        Instant createdAt,
        Instant updatedAt
) { ... }
```

**SubscriptionInput** record — same change: `List<ExpressionEvaluator> filters`.

**SubscriptionUpdate** record — same change: `List<ExpressionEvaluator> filters`
(nullable = unchanged).

Multiple filters are AND'd together at evaluation time.

### Removed Types (platform-api)

- `Constraint` — structured triple, replaced by expression evaluators
- `ConstraintOp` — operator enum, subsumed by expression languages

### Removed Types (subscriptions/)

- `ConstraintCompiler` — no longer needed; SubscriptionEngine compiles
  directly via ExpressionEngineRegistry

### Filter Compilation (SubscriptionEngine)

The `SubscriptionEngine` replaces `ConstraintCompiler` with direct expression
compilation:

1. For each `ExpressionEvaluator` in the subscription's filters:
   - Extract properties from the POJO via reflection (same `extractProperties()`
     logic, moved from ConstraintCompiler)
   - Compile via `ExpressionEngineRegistry.compile(filter.type(), expression,
     Map.class, Boolean.class, variables)`
   - `variables` includes `$me → ownerId` for owner self-reference
2. AND all compiled predicates together
3. Wrap with tenant isolation predicate:
   `obj instanceof SubscribableEvent e && tenancyId.equals(e.tenancyId())`
4. Build `FilterExpression` with deterministic expression string for alpha
   network sharing identity: `"tenant=" + tenancyId + ":" + canonicalFilterString`

The canonical filter string is built by joining each filter's `type() + ":" +
expression` with `" && "`, producing a deterministic key for FilterNode sharing.
Two subscriptions with identical filters in the same tenant share a FilterNode.

### $me Variable Binding

The current `$me` placeholder (substitutes the subscription owner's ID in
constraint values) becomes a bound variable passed to expression compilation:

```java
Map<String, Object> variables = Map.of("$me", ownerId);
registry.compile(type, expression, Map.class, Boolean.class, variables);
```

MVEL's parameterized compilation handles this natively — `$me` is available as
a variable in any expression: `assignee == $me`.

### SYSTEM Scope Validation

The REST layer validates that SYSTEM scope subscriptions do not use `$me` in
their filter expressions. This changes from checking `constraint.value()` to
scanning expression strings for `$me`. Since `$me` is a bound variable (not a
string substitution), this is a best-effort validation at input time — the
compilation would also fail at runtime if `$me` were unbound.

### Persistence

**JPA entity** (`SubscriptionEntity`):

- `constraintsJson` column → `filtersJson`
- Serialization format:

```json
[
  {"type": "mvel", "expression": "status == 'active' && priority > 3"},
  {"type": "jq", "expression": ".category == \"urgent\""}
]
```

- Custom serialization/deserialization dispatching on `type` field to create the
  correct `ExpressionEvaluator` record (`MvelExpressionEvaluator`,
  `JQExpressionEvaluator`)
- Pre-release: modify existing Flyway V1 migration directly (rename column)

**In-memory store**: straightforward field change — `List<ExpressionEvaluator>`
stored directly.

### REST API

`SubscriptionResource` accepts `List<ExpressionEvaluator>` in request bodies.
Jackson polymorphic deserialization via a custom deserializer module (platform-api
is zero-dependency — no Jackson annotations on `ExpressionEvaluator`).

The deserializer reads `{"type": "mvel", "expression": "..."}` and dispatches to
the correct record constructor.

### Expression Validation

At subscription creation time, each filter expression is validated via
`ExpressionEngineRegistry.validate(type, expression)`. Invalid expressions are
rejected with 400 before persistence.

Note: MVEL3's `validate()` is syntactic-only (no type context), so some
expressions may pass validation but fail compilation at evaluation time. This is
documented in the expression SPI — callers who need full validation should call
`compile()` and discard the result.

## What Doesn't Change

- `DataSourceRouter` — CloudEvent → DataSource routing
- `AlphaDataSource`, `TypeNode`, `FilterNode` — alpha network with collapsed sharing
- `FilterExpression` — alpha network filter identity
- `EventTypeObjectType` — event type discrimination
- `ExpressionEngineRegistry`, `ExpressionEngine` — compilation infrastructure
- `MvelExpressionEngine`, `JQExpressionEngine` — engine implementations
- `SubscriptionMatched` CDI event — fired on match, observed by NotificationDispatcher

## Affected Modules

| Module | Change |
|--------|--------|
| `platform-api` | Subscription/Input/Update records, remove Constraint/ConstraintOp |
| `subscriptions/` | SubscriptionEngine direct compilation, remove ConstraintCompiler, REST deserializer |
| `subscriptions-inmem/` | Field change in InMemorySubscriptionStore |
| `subscriptions-jpa/` | Entity column rename, serialization, Flyway V1 update |
| `platform/` | NoOpSubscriptionStore field update |

## Review Guidance

This change removes a filter representation (`Constraint`) and unifies on the
expression SPI. Reviewers should look beyond the subscription module and verify
coherence across the broader filter and expression system:

- **Expression SPI consistency**: Do `ExpressionEvaluator`, `ExpressionEngine`,
  `CompiledExpression`, and `FilterExpression` form a clean pipeline with no
  redundant concepts? Does the subscription usage align with how the expression
  SPI was designed to be consumed?
- **Alpha network integration**: Does the `FilterExpression` sharing identity
  (canonical expression string) produce correct collapsed sharing? Could two
  subscriptions with semantically identical but textually different expressions
  create duplicate FilterNodes? Is this acceptable?
- **DataSource filter model**: `FilterExpression` in `datasource-alpha` wraps a
  `Predicate` + type + expression string. The subscription layer now produces
  these directly. Verify that the contract between subscription filters and
  the alpha network is clean — no subscription-specific assumptions leaking
  into the generic infrastructure.
- **Cross-module filter patterns**: Other modules that consume `FilterExpression`
  or the alpha network (DataSourceRouter, any future RAS signal integration)
  should not be broken or constrained by this change. Verify no coupling was
  introduced.
- **Serialization round-trip**: `ExpressionEvaluator` is an interface in
  zero-dependency platform-api. The JPA entity and REST layer both need custom
  (de)serialization. Verify the JSON format is unambiguous and extensible for
  future expression engine types.
- **Removed types**: Confirm no remaining references to `Constraint`,
  `ConstraintOp`, or `ConstraintCompiler` anywhere in the platform codebase
  or in consumer repos (engine, work, etc.).

## Test Strategy

- **SPI tests**: Subscription/Input/Update construction with ExpressionEvaluator filters
- **Contract tests**: Store contract tests updated for new field type
- **SubscriptionEngine tests**: Expression compilation, tenant isolation, $me binding,
  multi-filter AND logic, FilterExpression sharing identity
- **REST tests**: Expression filter create/update/validate, SYSTEM scope $me rejection,
  invalid expression 400 response
- **Integration**: End-to-end event → subscription match → SubscriptionMatched with
  MVEL and JQ filters
