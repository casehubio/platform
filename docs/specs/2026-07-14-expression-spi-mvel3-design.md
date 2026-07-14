# Expression SPI + MVEL3 Evaluator Design

**Issue:** #141 — MVEL3 real evaluator
**Date:** 2026-07-14
**Status:** Draft

## Problem

Expression evaluation is currently split across two codebases with no shared
contract:

- **casehub-engine** has `ExpressionEvaluator`, `ExpressionEngine`, and
  `ExpressionEngineRegistry` in `engine-api`, coupled to `CaseContext`. Three
  implementations: JQ (string-based), Lambda (programmatic, hardcoded to
  `Predicate<CaseContext>`), and a registry dispatcher.
- **casehub-platform** has `JQEvaluator` in `expression/` — standalone, no
  shared interface, no pluggability.
- **ConstraintCompiler** in `subscriptions/` builds MVEL expression strings
  but hardcodes the predicate to `true` (mock phase) because no MVEL
  evaluator exists.

MVEL3 (`org.mvel:mvel3:3.0.0-SNAPSHOT`) is now available on JBoss Nexus
snapshots. The parent BOM needs the JBoss Nexus snapshots repository
configured (casehubio/parent#372 — currently open, must land first).

## Design

### Core Insight

MVEL3 transpiles expressions to Java source, compiles in-memory via javac,
and loads the resulting bytecode. The compiled result is functionally a lambda
— `Function<C, R>` equivalent. A `LambdaExpression` (programmatic) and an
MVEL-compiled expression (string-based) differ only in authoring — at runtime
they are the same thing.

This means the SPI needs one compiled expression type, not separate types per
language.

### SPI — platform-api

Package: `io.casehub.platform.api.expression`

#### CompiledExpression<C, R>

The runtime contract. Everything compiles down to this.

```java
public interface CompiledExpression<C, R> {
    String type();
    R eval(C context);
}
```

- MVEL compiled expression: delegates to `Evaluator<C, Void, R>`
- JQ compiled expression: delegates to jackson-jq `JsonQuery`
- Lambda: wraps `Function<C, R>`

#### ExpressionEvaluator

Marker interface for uncompiled expression descriptors. Carries `type()`
discriminator for registry dispatch. Concrete evaluators carry their own
data:

```java
public interface ExpressionEvaluator {
    String type();
}
```

String-based evaluators (JQ, MVEL) carry an `expression()` field. Lambda
evaluators carry a `Function<C, R>`. The interface does not force either shape.

```java
public record JQExpressionEvaluator(String expression)
        implements ExpressionEvaluator {
    public String type() { return "jq"; }
}

public record MvelExpressionEvaluator(String expression)
        implements ExpressionEvaluator {
    public String type() { return "mvel"; }
}

public class LambdaExpression<C, R>
        implements ExpressionEvaluator, CompiledExpression<C, R> {
    private final Function<C, R> function;

    public LambdaExpression(Function<C, R> function) {
        this.function = function;
    }

    public String type() { return "lambda"; }
    public R eval(C context) { return function.apply(context); }
}
```

`LambdaExpression` implements both interfaces — it is already compiled. Not
serialisable. Not tied to any specific context type. `LambdaExpression` is
intentionally outside the registry flow — no `LambdaExpressionEngine` exists
because lambdas are pre-compiled. `resolve("lambda")` returns empty;
`LambdaExpression` instances are created directly, never via `compile()`.

#### ExpressionEngine

Factory that compiles string expressions into `CompiledExpression`. One
engine per expression language.

```java
public interface ExpressionEngine {
    String type();

    <C, R> CompiledExpression<C, R> compile(
            String expression, Class<C> contextType, Class<R> resultType);

    <C, R> CompiledExpression<C, R> compile(
            String expression, Class<C> contextType, Class<R> resultType,
            Map<String, Object> variables);

    void validate(String expression);

    default boolean supportsStringCreation() { return true; }
}
```

The `compile` overload with `variables` binds external variables into the
compiled expression at compile time. The returned `CompiledExpression.eval(C)`
injects these variables transparently on each call — callers don't need to
know about parameterization. This is the expression-language equivalent of
prepared statements: the expression structure is compiled once, variable
values are captured at bind time. Both MVEL3 and JQ (scope variables)
support this pattern natively.

#### Error Handling Contracts

- **`ExpressionEngine.compile()`** — throws unchecked
  `ExpressionCompilationException` on invalid expression syntax (e.g., MVEL3
  transpilation or javac failure). Callers should treat this as a programming
  error or invalid user input.
- **`ExpressionEngine.validate()`** — throws `ExpressionCompilationException`
  on invalid syntax, same as `compile()` but without producing a compiled
  result. **Syntactic-only:** `validate()` takes no `contextType` or
  `resultType` parameters, so it cannot verify property existence on a
  specific POJO or type compatibility. An expression that passes `validate()`
  may still fail `compile()` for a given context type. Callers who need full
  type-aware validation should call `compile()` directly and discard the
  result.
- **`CompiledExpression.eval()`** — throws unchecked
  `ExpressionEvaluationException` on runtime errors (null field access, type
  mismatch, missing property). For boolean expressions, null field access
  evaluates to `false` rather than throwing, consistent with rule engine
  semantics.

Both exception types are defined in `io.casehub.platform.api.expression`
and extend `RuntimeException`.

#### ExpressionEngineRegistry

Dispatches to engines by type key. CDI-discovered.

```java
public interface ExpressionEngineRegistry {
    void register(ExpressionEngine engine);
    Optional<ExpressionEngine> resolve(String type);

    <C, R> CompiledExpression<C, R> compile(
            String type, String expression,
            Class<C> contextType, Class<R> resultType);

    <C, R> CompiledExpression<C, R> compile(
            String type, String expression,
            Class<C> contextType, Class<R> resultType,
            Map<String, Object> variables);

    void validate(String type, String expression);
}
```

### Default Implementations — platform/ module

Per ARC42STORIES §2 module-tier rule ("Every SPI in `platform-api/` gets a
`@DefaultBean` implementation in `platform/`"):

**`NoOpExpressionEngineRegistry`** — `@DefaultBean @ApplicationScoped` in
`platform/`. Active when no engine module (expression/) is on the classpath.
- `resolve(type)` → `Optional.empty()`
- `register(engine)` → silent no-op
- `compile(...)` → throws `UnsupportedOperationException` with a message
  directing the developer to add `casehub-platform-expression` to classpath
- `validate(...)` → throws `UnsupportedOperationException` similarly

Displaced by `DefaultExpressionEngineRegistry` in `expression/` when that
module is on the classpath, per the standard CDI displacement pattern.

### Implementations — expression/ module

The `expression/` module gains:

1. **MvelExpressionEngine** — delegates to MVEL3's fluent builder API:
   - POJO context: `MVEL.pojo(contextType).out(resultType).expression(expr).compile()`
     — no upfront field declarations needed; MVEL3 extracts fields via getters
   - When `contextType` is `Object.class`, MVEL3 generates code that uses its
     runtime property resolver (reflection-based) rather than typed getter
     calls. This is by design for rule engine scenarios where the context type
     is heterogeneous or unknown at expression compile time. The `contextType`
     parameter enables bytecode optimization when the type is known, but is
     not a hard requirement — `Object.class` triggers reflective resolution.
   - Map context: `MVEL.map().out(resultType).expression(expr).compile()`
   - The compiled `Evaluator<C, Void, R>` is wrapped in a `CompiledExpression<C, R>`
     — `Evaluator.eval(C)` maps directly to `CompiledExpression.eval(C)`
   - MVEL3 handles type inference, imports, and bytecode generation
   - `Evaluator.getReadProperties()` available for introspection if needed
   - **Compilation cache:** `MvelExpressionEngine` caches compiled
     `CompiledExpression` instances in a `ConcurrentHashMap` keyed by
     `(expression, contextType, resultType)`. MVEL3 compilation is
     heavyweight (transpile → javac → classload), so caching avoids
     redundant compilations when multiple callers compile the same
     expression. This follows the existing `JQEvaluator` precedent
     (which caches `JsonQuery` instances by expression string).
     Identical constraint expressions across subscriptions (e.g., both
     filtering on `status == "completed"`) resolve to the same cached
     `CompiledExpression` instance.

2. **JQExpressionEngine** — wraps existing `JQEvaluator` logic (jackson-jq
   compile + scope injection) behind the `ExpressionEngine` interface.

3. **DefaultExpressionEngineRegistry** — `@ApplicationScoped`, CDI-discovers
   all `ExpressionEngine` beans, dispatches by `type()`.

4. The existing `JQEvaluator` class stays as-is for backward compatibility
   — it has scope injection (`$secret`, `$config`) that the generic SPI
   doesn't need to know about. The `JQExpressionEngine` can delegate to it
   internally.

### Dependencies

```xml
<!-- expression/pom.xml -->
<dependency>
    <groupId>org.mvel</groupId>
    <artifactId>mvel3</artifactId>
    <version>3.0.0-SNAPSHOT</version>
</dependency>
```

**Prerequisite:** casehubio/parent#372 (JBoss Nexus snapshots repository)
must be merged before this branch can build.

**SNAPSHOT risk:** `3.0.0-SNAPSHOT` introduces CI fragility (JBoss Nexus
availability), API instability (pre-release API changes), and build
non-reproducibility. This is accepted as a temporary state — when MVEL3
reaches a GA release on Maven Central, the dependency will be updated to
the stable version and the JBoss Nexus repository can be removed.

### ConstraintCompiler Changes

**CDI conversion:** `ConstraintCompiler` converts from a static utility class
to an `@ApplicationScoped` CDI bean with injected `ExpressionEngineRegistry`.
`SubscriptionEngine` injects it instead of calling static methods. The two
call sites (`wireSubscription` and `wireAndReturnHandle`) change from
`ConstraintCompiler.compile(...)` to `constraintCompiler.compile(...)`.

#### Expression Injection Prevention

Enabling real MVEL evaluation activates a dormant code injection
vulnerability: `toMvelClause()` string-concatenates user-provided
`Constraint.field()` and `Constraint.value()` directly into MVEL expression
strings. Neither `SubscriptionInput`, `SubscriptionResource`, nor
`ConstraintCompiler` validates or escapes these values. A malicious value
like `x"; Runtime.getRuntime().exec("id"); //` would produce executable
MVEL that MVEL3 transpiles, compiles via javac, and loads — full JVM code
execution.

**Fix — two layers of defense:**

1. **Field name validation.** `Constraint.field` must match the property
   path pattern `[a-zA-Z_][a-zA-Z0-9_]*(\.[a-zA-Z_][a-zA-Z0-9_]*)*`
   (identifier-dot-separated paths only, e.g., `status`, `assignee.name`).
   Validated in the `Constraint` record constructor — rejects at
   construction time, before the value reaches the compiler. This is a
   structural constraint: field names are MVEL property paths, not
   arbitrary expressions.

2. **Value parameterization.** Constraint values are passed as bound
   variables, never concatenated into the expression string. The expression
   becomes `status == $p0` with `$p0` bound to the constraint value via
   the `compile(expr, ctxType, resultType, variables)` overload.

   This is the expression-language equivalent of prepared statements: the
   expression structure (field access + operator) is compiled, user values
   are injected as opaque data. Even a malicious value is treated as a
   string literal by MVEL3, not as executable code.

**After (parameterized):**
```java
// toMvelClause() generates parameterized expressions
// e.g., "status == $p0 && priority > $p1"
Map<String, Object> variables = Map.of("$p0", "OPEN", "$p1", 3);

CompiledExpression<Object, Boolean> userConstraint =
    registry.compile("mvel", mvelExpression,
                     Object.class, Boolean.class, variables);

Predicate<Object> predicate = obj ->
    tenantCheck.test(obj) && userConstraint.eval(obj);
```

Constraint values are constants for a given subscription — they don't
change between evaluations. The variable bindings are captured at compile
time and injected transparently on each `eval()` call.

Tenant isolation remains MVEL-independent (MethodHandle-based).

**MVEL syntax — parameterized operator mapping:**

| ConstraintOp | Parameterized MVEL3 |
|---|---|
| `EQ` | `field == $pN` |
| `NEQ` | `field != $pN` |
| `GT` | `field > $pN` |
| `LT` | `field < $pN` |
| `GTE` | `field >= $pN` |
| `LTE` | `field <= $pN` |
| `IN` | `$pN.contains(field)` (variable is the collection) |
| `STARTS_WITH` | `field.startsWith($pN)` |
| `CONTAINS` | `field.contains($pN)` |

With parameterization, MVEL3 handles type coercion of the bound variable
value automatically — no need for numeric quoting logic in
`toMvelClause()`.

### Engine Migration Path (#176, not this branch)

casehub-engine's `ExpressionEngine` / `ExpressionEngineRegistry` / evaluator
types in `engine-api` become thin adapters over platform's SPI:

- `engine-api.ExpressionEvaluator` → extends `platform-api.ExpressionEvaluator`
- `engine-api.ExpressionEngine` → wraps `platform-api.ExpressionEngine`, adds
  `CaseContext`-specific convenience methods (`extractString`, `transform`)
- `engine-api.LambdaExpressionEvaluator` → replaced by
  `platform-api.LambdaExpression<CaseContext, Boolean>`
- Runtime `DefaultExpressionEngineRegistry` → delegates to platform's registry

Tracked as #176. The platform SPI is designed to make this migration
straightforward but does not require it immediately.

## Scope

**In scope (this branch):**
- `ExpressionEvaluator`, `CompiledExpression`, `ExpressionEngine`,
  `ExpressionEngineRegistry` interfaces in platform-api
- `ExpressionCompilationException`, `ExpressionEvaluationException` in
  platform-api
- `JQExpressionEvaluator`, `MvelExpressionEvaluator`, `LambdaExpression`
  concrete types in platform-api
- `NoOpExpressionEngineRegistry` `@DefaultBean` in platform/
- `MvelExpressionEngine` in expression/ (MVEL3 dependency)
- `JQExpressionEngine` in expression/ (wraps existing JQEvaluator)
- `DefaultExpressionEngineRegistry` in expression/
- `ConstraintCompiler` CDI conversion + wired to real MVEL evaluation
- `ConstraintCompiler` expression injection prevention (field validation +
  value parameterization)
- `Constraint` record field name validation
- ARC42STORIES.MD updated: §1 core capabilities, §5 expression container,
  layer taxonomy (L1 expression SPI types, L4 MVEL3)
- Tests for all of the above

**Out of scope:**
- Engine migration (#176)
- Map and List context support in MvelExpressionEngine (#177 — POJO context
  sufficient for ConstraintCompiler; add when needed)
- MVEL block expressions (#178 — single expressions only for now)

## Testing

- **Unit:** `MvelExpressionEngine` — compile and evaluate POJO field access,
  comparisons, boolean logic, null handling
- **Unit:** `JQExpressionEngine` — existing JQEvaluator tests adapted to new
  interface
- **Unit:** `LambdaExpression` — function wrapping, type parameterisation
- **Unit:** `DefaultExpressionEngineRegistry` — dispatch by type, unknown type
  error, validate delegation
- **Unit:** `ConstraintCompiler` — existing tests updated to verify user
  constraints actually evaluate (no longer mock-true)
- **Unit:** `Constraint` — field name validation rejects injection patterns
  (`"; Runtime.exec(...)`, property paths with MVEL metacharacters)
- **Unit:** `ConstraintCompiler` — parameterized expressions: verify that
  malicious constraint values are treated as data, not code
- **Integration:** `SubscriptionEngine` with real MVEL constraint evaluation
  end-to-end
