# Playbook Language Extensions — Design Spec

**Issue:** #562
**Date:** 2026-10-09
**Branch:** issue-562-playbook-language-extensions

## Overview

Three new playbook language primitives for yaml-core and yaml-step-runtime:

1. **`at:` metric-threshold triggers** — block (or guard) on orchestration primitive values crossing a threshold
2. **`on-complete:` inline continuations** — syntactic sugar for step chaining without explicit naming
3. **`priority:` + `resource:` step priority** — priority-aware resource contention via a new `PriorityOrcSemaphore`

All three surfaced from SC2 build-order design (#384) but are domain-generic. Build orders are fundamentally supply-gated, completion-chained, priority-mediated sequences — patterns that map directly to IoT, AML, and trading playbooks.

## 1. `at:` — Metric-Threshold Triggers

### 1.1 Syntax

```yaml
# Single condition (default >= operator)
- build: PYLON
  at: 14 supply

# Explicit operator
- action: turn-off-boost
  at: <22 temperature

# Compound (AND — all must be met)
- build: BARRACKS
  at:
    - >=14 supply
    - >=150 minerals

# Guard mode (non-blocking — skip if not met)
- action: optional-upgrade
  at: { metric: 200 supply, mode: guard }
```

Each condition follows the pattern `[operator]<number> <primitive-name>`.

- **Operators:** `>=` (default when omitted), `>`, `<`, `<=`. No equality — exact-match on continuously changing values is fragile.
- **Compound conditions:** YAML list with AND semantics — all conditions must be simultaneously met.
- **Modes:** `wait` (default, blocking) and `guard` (non-blocking, skip if not met).

### 1.2 Type Restriction and `OrcNumericPrimitive`

`at:` is restricted to numeric orchestration primitives. A new marker interface unifies them:

```java
public interface OrcNumericPrimitive extends OrcPrimitive {
    double doubleValue();
    void onThresholdChange(DoubleConsumer listener);
    void removeThresholdListener(DoubleConsumer listener);
}
```

Implemented by:
- `OrcCounter` (backed by `LongAdder`) — `doubleValue()` returns `(double) sum()`
- `OrcAccumulator` (backed by `DoubleAccumulator`) — `doubleValue()` returns `get()`
- `OrcGauge<T extends Number>` — `doubleValue()` returns `get().doubleValue()`

Non-numeric gauges do not implement `OrcNumericPrimitive` and are rejected at parse-time validation. The threshold number is parsed as `double` for comparison against all three types.

New method on `ExecutionScope`:
```java
OrcNumericPrimitive numericPrimitive(String name);
```

This provides typed access without exposing a raw untyped `findPrimitive()` lookup. Throws `IllegalArgumentException` if the named primitive exists but is not numeric.

### 1.3 Observation Mechanism

The primitives are lock-free (`AtomicReference`, `LongAdder`, `DoubleAccumulator`). Observation uses a callback pattern that preserves lock-free fast paths:

**Listener methods** are defined on `OrcNumericPrimitive` (§1.2):
```java
void onThresholdChange(DoubleConsumer listener);
void removeThresholdListener(DoubleConsumer listener);
```

Using `DoubleConsumer` (not `Consumer<T>`) normalizes the callback type across all three numeric primitives — `OrcCounter` widens `long` to `double`, `OrcAccumulator` passes `double` directly, `OrcGauge<Number>` calls `doubleValue()`.

**Implementation:**
- Listener list: `CopyOnWriteArrayList<DoubleConsumer>` — optimized for few listeners with many reads (typical: 1-3 `at:` watchers per primitive).
- Notification timing: after the mutation completes. For `OrcGauge` this is after `AtomicReference.set()` or `compareAndSet()`. For `OrcCounter` this is after `LongAdder.add()`. For `OrcAccumulator` this is after `DoubleAccumulator.accumulate()`.
- **`LongAdder.sum()` caveat:** `sum()` is not an atomic read-after-write — concurrent mutations may not be reflected. For threshold detection on monotonically increasing counters this is acceptable: the threshold will eventually be detected on a subsequent mutation notification. The listener value should be treated as "at least this much" for `>=`/`>` operators.
- Listener callbacks execute on the mutating thread — must be non-blocking. `CompletableFuture.complete()` satisfies this.

**Compound conditions TOCTOU note:** `allConditionsMet()` iterates multiple primitives sequentially, not atomically. Between checking condition A and condition B, condition A could change. For the primary use case (monotonically increasing supply/minerals with `>=`/`>` operators), this is harmless — once crossed, thresholds stay crossed. For oscillating values with `<`/`<=` operators, the check may miss a transient window where all conditions are simultaneously true. This is an acceptable limitation — compound conditions assume monotonic convergence for correctness.

**`at:` decorator pseudo-code (blocking mode):**
```java
var future = new CompletableFuture<Void>();
List<DoubleConsumer> listeners = new ArrayList<>();

for (ThresholdCondition cond : conditions) {
    var primitive = scope.numericPrimitive(cond.metricName());
    DoubleConsumer listener = value -> {
        if (allConditionsMet(conditions, scope)) {
            future.complete(null);
        }
    };
    listeners.add(listener);
    primitive.onThresholdChange(listener);
}

// Check current values first (may already be met)
if (allConditionsMet(conditions, scope)) {
    future.complete(null);
}

try {
    future.get(); // blocks until all thresholds crossed
} finally {
    // Remove all listeners
    for (int i = 0; i < conditions.size(); i++) {
        scope.numericPrimitive(conditions.get(i).metricName())
             .removeThresholdListener(listeners.get(i));
    }
}
```

### 1.4 DecoratorChain Position

`at:` is positioned inside `timeout:`, alongside `wait:`. Both are "block before execution" decorators, both covered by the step's timeout.

```
 1. if         ← guard (skip if false)
 2. forEach    ← iteration
 3. loop       ← repetition
 4. on-error   ← error handling
 5. timeout    ← time limit
 6. at         ← NEW: threshold gate (inside timeout)
 7. wait       ← signal wait
 8. retry      ← retry on failure
 9. semaphore  ← mutual exclusion / resource contention
10. delay      ← sleep before exec
11. signal     ← fire after success
12. transition ← state machine
13. transform  ← (placeholder)
```

`if: false` skips the step entirely without waiting on the threshold (correct — `if:` is outermost). `timeout:` covers the `at:` blocking wait (consistent with `wait:` behavior).

### 1.5 ADR-0011 Compliance

`at:` belongs to the **imperative layer**. It blocks the current step's thread at execution time — the same evaluation model as `wait:` (signal wait) and `if:` (guard). It is not a reactive binding (`when:` layer) that fires on context change across the engine.

## 2. `on-complete:` — Inline Continuations

### 2.1 Syntax

```yaml
# Simple chain
- build: GATEWAY
  on-complete:
    - train: STALKER
    - train: SENTRY

# Nested continuation
- build: NEXUS
  on-complete:
    - train: PROBE
      on-complete:
        - action: chrono-boost

# Continuation with its own decorators
- build: GATEWAY
  on-complete:
    - train: STALKER
      resource: gateway
      priority: high
```

### 2.2 Desugaring (YAML Parse Phase)

`on-complete:` is pure syntax sugar — desugared before step resolution, zero runtime changes.

**Transform rules:**

1. Generate a unique name for the parent step: `__oc_<counter>` (counter is global within the playbook parse, monotonically increasing).
2. Add `signal: __oc_<counter>_done` decorator to the parent step. If the parent already has a `signal:` decorator, merge both into a list — the runtime fires all signals in the list sequentially after step success.
3. Extract each item in the `on-complete:` list as a sibling step with `wait: __oc_<counter>_done` decorator.
4. If the parent step already has a `name:`, use it instead of generating one (and derive the signal name from it).
5. Nested `on-complete:` blocks are expanded recursively (depth-first).
6. Generated names are deterministic within a parse — same input always produces the same names.
7. Desugaring is a single pass over the fully-expanded step list (after module expansion), NOT per-module. This guarantees counter uniqueness across module boundaries.

**Failure semantics:** The `signal:` decorator only fires on parent step success (consistent with existing `wrapPostSignal` behavior). If the parent step fails, the signal is not fired and continuations remain blocked on `wait:`. This is the correct semantic for `on-complete:` — "on complete" means "on successful completion." If a `timeout:` decorator is present on continuations, it will unblock them after the timeout. Without `timeout:`, continuations of a failed parent are never executed. This matches the explicit `signal:/wait:` pattern that `on-complete:` desugars to — no hidden magic.

**Example desugaring:**
```yaml
# Input
- build: GATEWAY
  on-complete:
    - train: STALKER
    - train: SENTRY

# Desugars to
- build: GATEWAY
  name: __oc_1
  signal: __oc_1_done
- train: STALKER
  wait: __oc_1_done
- train: SENTRY
  wait: __oc_1_done
```

### 2.3 Source Location Mapping

Error messages must reference the original `on-complete:` block, not the generated signal/wait names. The desugaring phase preserves a source location map: `generated-name → (source-file, line, column)`. Error formatters consult this map to display user-authored locations.

### 2.4 Scope and Result Access

Continuations are sibling steps after desugaring — they share the same `ExecutionScope` as the parent step (consistent with "zero runtime changes"). Continuations can read and modify the same counters, gauges, and state machines as the parent.

**Result access:** The parent step's result is accessible via `${signal.*}` in continuation steps — this is the existing `wait:` variable binding mechanism (`wrapWait` pushes the signal payload into a `signal` variable scope). The `${result.__oc_1}` path also works via `StepResultStore` (the step executor records results under the step name), but `${signal.*}` is the canonical access for `on-complete:` continuations.

### 2.5 Resource Interaction

Continuations are independent steps after desugaring. They do NOT inherit the parent's `resource:` or `priority:` decorators — declare explicitly if the continuation contends for a resource.

## 3. `priority:` + `resource:` — Step Priority for Resource Contention

### 3.1 Resource Declaration

A top-level `resources:` section in the playbook YAML:

```yaml
resources:
  minerals:
    concurrency: 1    # mutex — one consumer at a time
  gas:
    concurrency: 2    # two workers max
  barracks:
    concurrency: 3    # three training queues
```

Each entry declares a named contended resource with a concurrency limit (number of permits).

**Parsing:** `resources:` is a first-class `@JsonProperty` field on `YamlModuleFileBuilder` (alongside `module:` and `imports:`), NOT an `@JsonAnySetter` entry. This gives parse-time type safety and validation — the field is typed as `Map<String, ResourceDeclaration>` where `ResourceDeclaration` is a record with `concurrency` (int, required, minimum 1). Parse errors surface at deserialization time, not at runtime.

**Startup wiring:** At playbook startup, the runtime iterates the parsed `resources` map and registers a `PriorityOrcSemaphore` per entry into the `ExecutionScope` via `PrimitiveFactory.createPrioritySemaphore(name, permits)` (new factory method).

**Parse-time validation:** Step `resource:` references are checked against the declared resource set. Undeclared resources are a parse error.

### 3.2 Step Annotations

```yaml
steps:
  - train: MARINE
    resource: barracks
    priority: normal

  - train: PROBE
    resource: minerals
    priority: background
    loop: { until: game-over }

  - build: PYLON
    resource: minerals
    priority: high
```

- **`resource:`** — name of a declared resource. Maps to a `PriorityOrcSemaphore`.
- **`priority:`** — `background` (0), `normal` (1), or `high` (2). Default is `normal` when `resource:` is declared without `priority:`.

### 3.3 `PriorityOrcSemaphore`

A new orchestration primitive in `io.casehub.yaml.core.orchestration`:

```java
public interface PriorityOrcSemaphore extends OrcPrimitive {
    void acquire(Priority priority) throws InterruptedException;
    boolean tryAcquire(Priority priority, long timeout, TimeUnit unit)
            throws InterruptedException;
    void release();
    int availablePermits();
}

public enum Priority {
    BACKGROUND(0), NORMAL(1), HIGH(2);
    private final int level;
}
```

`acquire()` and `tryAcquire()` throw `InterruptedException` — consistent with `OrcSemaphore` and required for timeout/cancellation. The `wrapTimeout` decorator cancels blocked inner decorators via `future.cancel(true)` which sends an interrupt; swallowing it would break timeout guarantees. `tryAcquire()` uses `long timeout, TimeUnit unit` to match `OrcSemaphore`'s existing signature.

**Implementation (`DefaultPriorityOrcSemaphore`):**
- Internal `PriorityBlockingQueue` of waiters, ordered by priority level (descending).
- `acquire(priority)` enqueues the current thread as a waiter with the given priority. When a permit is released, the highest-priority waiter is unblocked first.
- `release()` signals the highest-priority waiting thread.
- No preemption — a running step completes its current work before releasing the permit.
- Uses `j.u.c` locks (not `synchronized`) per the yaml-core threading rule (virtual thread pinning).

**`PrimitiveFactory` addition:**
```java
PriorityOrcSemaphore createPrioritySemaphore(String name, int permits);
```

**`ExecutionScope` accessor:**
```java
PriorityOrcSemaphore prioritySemaphore(String name);
```

The decorator calls `scope.prioritySemaphore(name)` to resolve the named resource.

### 3.4 DecoratorChain Integration

`resource:` reuses the existing `semaphore` decorator position (position 9). The decorator reads the step's `priority:` annotation and calls `acquire(priority)` instead of the existing `acquire()`. No new decorator layer — this extends the existing semaphore decorator to handle `PriorityOrcSemaphore` in addition to `OrcSemaphore`.

The decorator distinguishes between `semaphore:` (existing syntax → `OrcSemaphore`) and `resource:` (new syntax → `PriorityOrcSemaphore`) by which decorator key is present. No `instanceof` dispatch needed — different keys route to different code paths:

When `resource:` is present:
1. Calls `scope.prioritySemaphore(resourceName)` to resolve the named resource
2. Reads `priority:` (default: `NORMAL`)
3. Calls `semaphore.acquire(priority)` before step execution
4. Calls `semaphore.release()` after step execution (in a finally block)

When `semaphore:` is present: existing behavior unchanged.

### 3.5 Known Limitations

**Priority inversion:** In multi-resource scenarios (background holds resource A, high needs A + B), the high-priority step waits for background to finish. Priority inheritance (boosting background to high while it holds A) is deferred to a future issue. The three-level non-preemptive model is sufficient for v1 — SC2 build orders and IoT/trading playbooks don't typically create the multi-resource dependency chains that trigger inversion.

## 4. Cross-Cutting Concerns

### 4.1 Combined Example — SC2 Build Order

```yaml
resources:
  minerals:
    concurrency: 1
  gas:
    concurrency: 1
  gateway:
    concurrency: 1

steps:
  # Constant worker production — yields to army builds
  - train: PROBE
    resource: minerals
    priority: background
    loop: { until: supply >= 44 }

  # Supply gate — wait until 14 supply, then build pylon
  - build: PYLON
    at: 14 supply
    resource: minerals
    priority: high
    on-complete:
      - build: GATEWAY
        at: >=150 minerals
        resource: minerals
        priority: high
        on-complete:
          - train: STALKER
            resource: gateway
            priority: normal
```

### 4.2 Schema and Type Safety

All new syntax elements must be statically analyzable — code assistants (LSP, IDE completions) must offer completions and validation without executing the playbook. No ambiguity in the grammar.

**`StepSchemaComposer` changes** (`yaml-step-runtime/.../StepSchemaComposer.java`):

The composer already maintains `DECORATOR_KEYS` (the set of known decorator property names) and emits a composed JSON Schema with `oneOf` step variants plus shared decorator properties.

| New element | Schema location | Schema type |
|-------------|----------------|-------------|
| `at:` | `DECORATOR_KEYS` + typed schema | `oneOf: [string, array of strings, object{metric,mode}]` |
| `on-complete:` | Shared properties | `array` with recursive `$ref` to step schema |
| `resource:` | `DECORATOR_KEYS` | `string` |
| `priority:` | `DECORATOR_KEYS` | `enum: [background, normal, high]` |
| `resources:` section | First-class `@JsonProperty` on module builder | `object` with `additionalProperties: {type: object, properties: {concurrency: integer}}` |

**`at:` schema detail:**
```json
{
  "oneOf": [
    { "type": "string", "pattern": "^(>=|>|<|<=)?\\d+(\\.\\d+)?\\s+\\w+$" },
    {
      "type": "array",
      "items": { "type": "string", "pattern": "^(>=|>|<|<=)?\\d+(\\.\\d+)?\\s+\\w+$" }
    },
    {
      "type": "object",
      "properties": {
        "metric": { "type": "string" },
        "mode": { "enum": ["wait", "guard"] }
      },
      "required": ["metric"]
    }
  ]
}
```

**`on-complete:` schema detail:**
```json
{
  "type": "array",
  "items": { "$ref": "#" }
}
```

The recursive `$ref` is supported by JSON Schema Draft 2020-12 (which the existing schemas use). This allows code assistants to offer the full step vocabulary inside `on-complete:` blocks — decorators, plugin actions, structural steps, all with completions.

**`resources:` section schema** (added to module/playbook schema):
```json
{
  "resources": {
    "type": "object",
    "additionalProperties": {
      "type": "object",
      "properties": {
        "concurrency": { "type": "integer", "minimum": 1 }
      },
      "required": ["concurrency"]
    }
  }
}
```

**Parse-time validation** (beyond schema):
- `at:` primitive names validated against declared/discoverable primitives
- `at:` primitive types validated as numeric (`OrcCounter`, `OrcAccumulator`, `OrcGauge<Number>`)
- `resource:` references validated against the `resources:` section declarations
- `priority:` only valid when `resource:` is present on the same step

### 4.2b Module Interaction

All three features work within the existing module system:
- `at:`, `on-complete:`, `resource:`, and `priority:` can appear on steps inside imported modules.
- `on-complete:` desugaring runs after module expansion (imports are resolved first, then continuations are desugared).
- Resource declarations in the `resources:` section are module-level — imported modules can reference resources declared in the importing playbook.
- Module parameters can be used in threshold values: `at: ${params.threshold} supply`.

### 4.3 `at:` vs `loop until:` Evaluation Asymmetry

Both `at:` and `loop: { until: ... }` can reference the same metric (e.g., `supply`) but use different evaluation paths:

- **`at:`** — event-driven, zero-latency callback via `onThresholdChange` listener. Fires immediately when the threshold is crossed.
- **`loop: { until: ... }`** — polling, re-evaluated once per loop iteration via `ConditionEvaluator`.

They are not interchangeable. Use `at:` for "block until a precondition is met" (before a step). Use `loop: { until: ... }` for "repeat this step until a termination condition" (controlling loop exit).

### 4.4 ADR-0011 Update

ADR-0011 should be updated to include the new imperative-layer keywords:

| Keyword | Layer | Purpose |
|---------|-------|---------|
| `at:` | Imperative | Blocking threshold gate / guard |
| `on-complete:` | Imperative | Syntax sugar (removed at parse time) |
| `resource:` | Imperative | Decorator — resource contention |
| `priority:` | Imperative | Decorator annotation — contention priority |

### 4.5 Testing Strategy

- **`at:` decorator:** Unit tests with controlled `OrcGauge`/`OrcCounter` mutations from a separate thread. Test blocking wait, guard mode, compound conditions, timeout interaction, scope cleanup (listener removal), compound conditions under concurrent mutation (TOCTOU), multiple `at:` watchers on the same primitive, and listener cleanup after scope close (leak detection).
- **`on-complete:` desugaring:** Unit tests at the parser level — verify desugared output matches expected signal/wait structure. Test simple, nested, and multi-continuation cases. Test source location mapping. Test parent with existing `signal:` decorator (merge behavior). Test parent failure (continuations not executed).
- **`PriorityOrcSemaphore`:** Unit tests with concurrent threads at different priorities. Verify acquisition order. Test with concurrency > 1. Test `InterruptedException` propagation (timeout cancellation).
- **`resources:` parsing:** Validate `ResourceDeclaration` deserialization, parse-time rejection of invalid concurrency values, step `resource:` reference validation against declarations.
- **Integration:** End-to-end playbook tests combining all three features (the SC2 build order example above).

## References

- [Issue #562](https://github.com/casehubio/platform/issues/562) — playbook language: supply-gated triggers, inline continuations, step priority
- [ADR-0011](docs/adr/0011-three-layer-evaluation-model-keyword-reservation.md) — three-layer evaluation model, keyword reservation
- [Issue #384](https://github.com/casehubio/platform/issues/384) — SC2 build-order playbooks (quarkmind physics calibration)
- `yaml-core/src/main/java/io/casehub/yaml/core/orchestration/` — existing orchestration primitives
- `yaml-step-runtime/src/main/java/io/casehub/yaml/step/eval/DecoratorChain.java` — decorator chain
- `yaml-core/src/main/java/io/casehub/yaml/core/orchestration/DefaultOrcGauge.java` — lock-free AtomicReference implementation
- `yaml-core/src/main/java/io/casehub/yaml/core/orchestration/DefaultOrcCounter.java` — lock-free LongAdder implementation
- `yaml-core/src/main/java/io/casehub/yaml/core/orchestration/DefaultOrcAccumulator.java` — lock-free DoubleAccumulator implementation
- `yaml-core/src/main/java/io/casehub/yaml/core/orchestration/OrcSemaphore.java` — existing semaphore interface
- `yaml-jackson/src/main/java/io/casehub/yaml/jackson/YamlModuleFileMixin.java` — @JsonAnySetter section capture
- `yaml-step-runtime/src/main/java/io/casehub/yaml/step/catalog/StepSchemaComposer.java` — step envelope schema composition (DECORATOR_KEYS, oneOf variants)
