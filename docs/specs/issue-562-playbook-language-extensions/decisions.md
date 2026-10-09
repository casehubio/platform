# Decisions — Issue #562: Playbook Language Extensions

## D1: Scope — all three features in one branch

**Choice:** Design and implement all three features (at: triggers, on-complete: continuations, priority/resource contention) together in one issue/branch.
**Alternatives:**
- Design together, implement incrementally — unnecessary overhead for features that are conceptually linked
- Split into separate issues — they share the SC2 build-order motivation and interact at the language level
**Rationale:** The three features are conceptually linked: SC2 build orders are supply-gated, completion-chained, priority-mediated sequences. They share the same YAML surface and the same runtime context.
**Trade-offs:** Larger single PR; all three must be stable before landing.
**Sources:** Issue #562 body
**Exploration:** quick
**Status:** captured

## D2: Metric source for `at:` triggers — OrcGauge/OrcCounter observation

**Choice:** `at:` triggers watch existing orchestration primitives (OrcGauge, OrcCounter, OrcAccumulator) rather than introducing a new MetricResolver SPI. Restricted to numeric primitives: `OrcCounter`, `OrcAccumulator`, and `OrcGauge<? extends Number>`. Non-numeric gauges are rejected at parse-time validation.
**Alternatives:**
- Domain-registered MetricResolver SPI — adds a new SPI and polling/subscription complexity for external metrics
- Expression-engine evaluation — blurs the line between `at:` (blocking threshold gate) and `if:` (boolean guard)
**Rationale:** The orchestration primitives already exist and are updated by steps during execution. Watching them is the simplest path with zero new abstractions.
**Trade-offs:** Metrics from external systems (outside the playbook) require steps to bridge values into gauges/counters first.
**Sources:** yaml-core orchestration primitives (OrcGauge, OrcCounter, OrcAccumulator)
**Exploration:** quick
**Status:** revised (R1-09: restricted to numeric primitives)

## D3: `at:` semantics — hybrid blocking/guard

**Choice:** `at:` is a blocking wait by default. A `mode: guard` option switches to non-blocking (evaluate once, skip if not met). ADR-0011 compliance: `at:` belongs to the imperative layer — it blocks the current step's thread (like `wait:`), not a reactive binding. The guard mode is a one-shot evaluation (like `if:`).
**Alternatives:**
- Blocking wait only — loses flexibility for cases where you want "run if ready, skip otherwise"
- Non-blocking guard only — loses the "wait for it" semantics that makes supply gates work
**Rationale:** The primary use case (supply gates) requires blocking. Guard mode covers secondary use cases without a separate syntax.
**Trade-offs:** Two modes to document and test. Guard mode overlaps slightly with `if:` but `if:` can't reference primitives by name with threshold comparison.
**Sources:** Issue #562 (SC2, IoT, trading, AML examples), ADR-0011 (keyword reservation)
**Exploration:** quick
**Status:** revised (R1-01: ADR-0011 compliance classified)

## D4: Threshold operators — directional with `>= > < <=`, compound conditions supported

**Choice:** Support four comparison operators (`>=`, `>`, `<`, `<=`), default `>=` when omitted. No equality operator. Compound conditions via list syntax — all conditions must be met (AND semantics).
**Alternatives:**
- `>=` only — a subset of the chosen set; no reason to restrict artificially
- Include `=` — exact-match on a continuously changing metric is fragile
- Include `!=` — no real use case for "wait until supply is not 14"
**Rationale:** Directional operators are a superset of `>=`-only with trivial parser cost. Compound conditions address the primary SC2 use case directly: "build barracks when supply >= 14 AND minerals >= 150".
**Trade-offs:** Four operators to parse and test. Compound conditions add list parsing but keep the syntax clean.
**Syntax:**
```yaml
# Single condition (default >=)
at: 14 supply

# Single with explicit operator
at: <80 temperature

# Compound (AND — all must be met)
at:
  - >=14 supply
  - >=150 minerals
```
**Sources:** Discussion during brainstorming, R1-08 (compound conditions)
**Exploration:** quick
**Status:** revised (R1-08: added compound conditions)

## D5: `at:` integration — new decorator in DecoratorChain

**Choice:** Add `at:` as a decorator layer inside `timeout:` — positioned after timeout and alongside `wait:` (both are blocking-before-execution decorators). This ensures `timeout:` covers the `at:` blocking wait.
**Alternatives:**
- Position 2 (after `if:`, before `forEach:`) — timeout would NOT cover the `at:` wait, creating inconsistent timeout behavior vs `wait:` (R1-02)
- New ResolvedStep variant (ThresholdStep) — breaks the pattern that ResolvedStep variants represent *what* runs while decorators represent *how/when*
- Hybrid decorator + SelectStep integration — two integration points to maintain for marginal benefit
**Rationale:** `at:` and `wait:` are both "block until a condition is met" decorators. They should be at the same depth in the decorator chain, both covered by `timeout:`. The `if:` guard (outermost) can still skip the step before `at:` blocks.
**Trade-offs:** The DecoratorChain grows from 12 to 13 layers. `if: false` still skips without waiting on the threshold (correct behavior — `if:` remains outermost).
**Sources:** yaml-step-runtime DecoratorChain (12 existing layers), R1-02 (timeout inconsistency)
**Exploration:** quick
**Status:** revised (R1-02: moved inside timeout)
**Depends on:** D2 (metric source), D3 (blocking/guard semantics)

## D6: `at:` observation mechanism — callback-driven via onChange()

**Choice:** Add `onChange(Consumer<T>)` and `removeListener(Consumer<T>)` methods to OrcGauge, OrcCounter, and OrcAccumulator. The `at:` decorator registers a listener that checks the threshold on every update and unblocks via CompletableFuture when crossed.
**Implementation note:** The primitives are lock-free (`AtomicReference` for OrcGauge, `LongAdder` for OrcCounter, `DoubleAccumulator` for OrcAccumulator). Listener list uses `CopyOnWriteArrayList<Consumer<T>>`. Notification happens after the CAS/accumulate succeeds — the listener sees the committed value. If the value changes again between CAS and notification, the next notification covers it (threshold check is idempotent).
**Alternatives:**
- Polling with configurable interval — wastes cycles and adds latency equal to the poll interval
- Condition variable on primitive mutation — would require switching lock-free primitives to lock-based (performance regression)
- PrimitiveWatcher service (mutation-driven polling) — avoids changing primitive interfaces but adds a central bottleneck
**Rationale:** Callback-driven is zero-latency, zero-waste, and keeps the observation logic outside the primitive implementation. CAS-then-notify preserves lock-free fast paths.
**Trade-offs:** Adds listener management to three primitive types. Listeners must be removed on completion (or scope close) to avoid leaks. Listener callbacks execute on the mutating thread — must be non-blocking (CompletableFuture.complete() is non-blocking).
**Sources:** DefaultOrcGauge (AtomicReference), DefaultOrcCounter (LongAdder), DefaultOrcAccumulator (DoubleAccumulator), R1-03 (lock-free correction)
**Exploration:** quick
**Status:** revised (R1-03: corrected lock-free claim, specified CAS-then-notify)
**Depends on:** D5 (decorator integration)

## D7: `on-complete:` desugaring — YAML parse phase

**Choice:** Desugar `on-complete:` during YAML parsing, before step resolution. The parser generates a name for the parent step, extracts the continuation as a sibling step with `wait: parent-signal`, and adds `signal: parent-signal` to the parent. Source location mapping must be preserved so that error messages reference the original `on-complete:` block, not the generated signal/wait names.
**Alternatives:**
- Step resolution phase (new ResolvedStep wrapping) — adds resolution complexity for what is fundamentally syntax sugar
- New decorator (on-complete as post-execution hook) — heavier runtime feature when signal/wait already does the job
**Rationale:** The issue explicitly says "the compiler desugars to the current trigger model." Signal/wait already handles completion chaining. This is a pure syntax transform with zero runtime changes.
**Trade-offs:** Desugaring must handle nested on-complete (on-complete inside an on-complete continuation) by recursive expansion. Generated names must be unique within the playbook. Error message mapping adds implementation cost but is essential for usability (R1-04).
**Sources:** Issue #562 ("the compiler desugars to the current trigger model"), existing signal/wait decorator, R1-04 (debuggability)
**Exploration:** quick
**Status:** revised (R1-04: added source location mapping requirement)

## D8: `on-complete:` continuation scope — child ExecutionScope

**Choice:** Continuations run in a child ExecutionScope. Parent step result is accessible via `${result.parentName}`.
**Scope behavior (corrected):** Child scopes share parent primitives via lookup delegation — `findPrimitive(name)` traverses the parent chain. This means continuations CAN read and modify parent-scope primitives directly (e.g., a continuation can increment the parent's counter). New primitives created in the continuation are local to the child scope.
**Alternatives:**
- Same scope — risks name collisions between parent and continuation primitives
- Isolated scope with explicit bindings — requires explicit wiring, more verbose
**Rationale:** Matches how BlockStep children work today. The delegation behavior is desirable for continuations — they often need to interact with the same counters/gauges as the parent.
**Trade-offs:** No namespace isolation for inherited primitives — a continuation using the same primitive name as a parent gets the parent's primitive, not a fresh one. This is a feature, not a bug, but must be documented.
**Sources:** DefaultExecutionScope parent-child hierarchy, R1-05 (scope behavior correction)
**Exploration:** quick
**Status:** revised (R1-05: corrected scope description to match actual behavior)
**Depends on:** D7 (desugaring approach)

## D9: Priority contention model — priority semaphore

**Choice:** Introduce `PriorityOrcSemaphore` — an OrcSemaphore where `acquire()` takes a priority level. Higher-priority acquirers jump the queue. Steps declare `resource: <name>` (maps to a named semaphore) and `priority: background|normal|high`.
**Alternatives:**
- Cooperative yield — lower-priority steps voluntarily release; requires steps to be interruptible at safe points
- Preemptive with suspension — most powerful but extremely complex; steps must be suspension-safe
**Rationale:** Queue-based priority is the simplest model that solves the contention problem. Higher-priority steps acquire first when the resource is released. No interruption, no cooperation protocol — just queue ordering.
**Trade-offs:** A running lower-priority step is not interrupted — it completes its current work before the higher-priority step can acquire. This is appropriate for most use cases (you don't want to interrupt a half-built building).
**Known limitation:** Priority inversion is possible in multi-resource scenarios (background holds A, high needs A+B). Priority inheritance (boosting background while it holds A) is deferred to a future issue — the three-level model is sufficient for v1 use cases (SC2 build orders don't create multi-resource chains that cause inversion).
**Sources:** OrcSemaphore existing implementation, java.util.concurrent PriorityBlockingQueue, R1-07 (priority inversion)
**Exploration:** quick
**Status:** revised (R1-07: acknowledged priority inversion limitation)

## D10: Resource declaration — contention-specific `resources:` section

**Choice:** A top-level `resources:` section in the playbook YAML where each entry declares a named contended resource with a concurrency limit. The runtime reads `sections.get("resources")` and creates `PriorityOrcSemaphore` instances. This is a new pattern — no existing section has runtime-interpreted semantics via `sections.get()`.
**Alternatives:**
- Generic global data section — risks becoming a dumping ground; runtime has to dispatch on entry type
- Implicit from first step reference — invisible resource topology; can't see what resources exist without scanning all steps
**Rationale:** Captured automatically by the existing `@JsonAnySetter` mechanism in `YamlModuleFileMixin`. Resources are visible and explicit at the playbook level. Parse-time validation catches typos in step `resource:` references against the declared set.
**Trade-offs:** New convention for runtime-interpreted sections. Steps must reference declared resources.
**Sources:** YamlModule.sections(), YamlModuleFileMixin @JsonAnySetter, R1-06 (groups: precedent corrected)
**Exploration:** quick
**Status:** revised (R1-06: dropped incorrect groups: precedent claim)
**Depends on:** D9 (priority semaphore model)

## D11: `on-complete:` + resource interaction

**Choice:** Continuations desugared from `on-complete:` are independent steps and must declare their own `resource:` and `priority:` if they contend for resources. The `resource:` and `priority:` decorators on the parent step do NOT automatically apply to continuations.
**Rationale:** Since desugaring produces sibling steps (not nested steps), each step has its own decorator set. Implicit inheritance would be surprising — the continuation might not need the same resource.
**Trade-offs:** Users must explicitly annotate continuations that share the parent's resource. This is visible in the YAML (the `on-complete:` block shows the annotations) so it's not hidden.
**Sources:** R1-12 (feature interaction), D7 (desugaring), D9 (priority model)
**Exploration:** quick
**Status:** captured
**Depends on:** D7 (desugaring), D9 (priority model)
