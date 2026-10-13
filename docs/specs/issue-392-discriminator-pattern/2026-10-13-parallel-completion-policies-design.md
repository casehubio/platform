# Parallel Completion Policies — Discriminator Pattern

**Issue:** casehubio/platform#392
**Parent:** casehubio/platform#386 (runtime orchestration primitives)
**Date:** 2026-10-13
**Module:** yaml-step-runtime (evaluator + parser), yaml-core (primitives — unchanged)

## Summary

Extend the existing `parallel:` block with two optional fields — `advance-after` and `cancel-remaining` — to support four completion policies: wait-all (today's default), race (first wins + cancel rest), discriminator (first wins + continue rest), and quorum-continue (N wins + continue rest). This eliminates the need for separate `race:` and `discriminator:` keywords and keeps the evaluator logic unified.

## Motivation

The runtime orchestration primitives spec (#386) identifies three parallel coordination patterns:

- **parallel** (implemented): wait for all steps, return aggregate result
- **race/firstWins** (reserved, not implemented): advance on first completion, cancel remaining
- **discriminator** (deferred to #392): advance on first completion, continue collecting

These differ only in **completion policy** — not in how parallel steps are spawned or collected. Modeling them as one `parallel:` construct with policy options avoids duplicated evaluator logic and ensures consistent behavior.

## YAML Syntax

### Current parallel syntax

Today's `parallel:` takes a list directly:

```yaml
- parallel:
    - step: a
      action: fetch-primary
    - step: b
      action: fetch-secondary
```

Walker.java:233-237 casts the `parallel:` value to `List<Map<String, Object>>` and resolves each entry as a step.

### Extended parallel syntax (new)

When completion policy options are needed, `parallel:` takes a map with a `steps` key:

```yaml
- step: multi-source-query
  parallel:
    advance-after: 1
    steps:
      - step: primary-db
        action: query-primary
      - step: cache-lookup
        action: query-cache
```

The Walker must handle both forms — **polymorphic parsing**:
- If `parallel:` value is a **List**: today's behavior (advance-after defaults to `all`, no policy options)
- If `parallel:` value is a **Map**: extract `advance-after`, `cancel-remaining`, `collection-timeout` from the map, then resolve `steps` as the child step list

Both forms produce a `ParallelStep` record. The list form is syntactic sugar for the map form with all defaults.

### Discriminator: advance on first, continue collecting

```yaml
- step: multi-source-query
  parallel:
    advance-after: 1
    steps:
      - step: primary-db
        action: query-primary
      - step: cache-lookup
        action: query-cache
      - step: fallback-api
        action: query-fallback
```

The main flow advances after the first successful completion. The remaining steps continue executing in the background. Results populate `${result.primary-db}`, `${result.cache-lookup}`, `${result.fallback-api}` as each completes.

### Race: advance on first, cancel remaining

```yaml
- step: race-feeds
  parallel:
    advance-after: 1
    cancel-remaining: true
    steps:
      - step: primary-feed
        action: connect-primary
      - step: backup-feed
        action: connect-backup
```

After the first successful completion, remaining steps are interrupted.

### Quorum-continue: advance on N, continue rest

```yaml
- step: consensus
  parallel:
    advance-after: 2
    steps:
      - step: judge-1
        action: evaluate
      - step: judge-2
        action: evaluate
      - step: judge-3
        action: evaluate
```

Advances after 2 successful completions. The third judge continues executing.

### Optional collection timeout

```yaml
- step: best-effort-collection
  parallel:
    advance-after: 1
    collection-timeout: 60s
    steps:
      - step: fast-source
        action: query-fast
      - step: slow-source
        action: query-slow
```

The step-level `timeout` decorator (in `decorators` map, handled by DecoratorChain) applies to reaching the `advance-after` threshold. `collection-timeout` applies to the background collection phase after advancement. If `collection-timeout` is absent, background steps run until parent scope close.

### Result access from downstream steps

```yaml
- step: multi-source-query
  parallel:
    advance-after: 1
    steps:
      - step: primary-db
        action: query-primary
      - step: fallback-api
        action: query-fallback

- step: use-results
  action: process
  data:
    primary: ${result.primary-db}
    fallback: ${result.fallback-api}
```

`${result.<step-name>}` returns null for steps that haven't completed yet. For discriminator branches, null is the expected value for not-yet-complete steps — the parser suppresses the "concurrent sibling reference" warning for branches inside a parallel block with `advance-after < steps.size()`.

`${result.<parallel-step>}` resolves to the result of the step that triggered advancement (the Nth successful completion). This is intentionally non-deterministic for `advance-after: 1` — consumers should access individual branch results via `${result.<branch-step>}` when they need deterministic access.

### Waiting for total completion

When downstream steps need all results before proceeding, use a `barrier` that awaits the parallel step's completion:

```yaml
- step: multi-source-query
  parallel:
    advance-after: 1
    steps:
      - step: primary-db
        action: query-primary
      - step: fallback-api
        action: query-fallback

- step: wait-for-all
  barrier:
    await: [multi-source-query]

- step: aggregate
  action: combine-all-results
  data:
    primary: ${result.primary-db}
    fallback: ${result.fallback-api}
```

When a `barrier` awaits a parallel step with `advanceAfter < steps.size()`, the barrier waits on the **completion latch** (all branches done), not the advancement latch. This is the key distinction: `barrier: { await: [multi-source-query] }` after a discriminator parallel block means "wait until all branches of that block have terminated."

The barrier validation (`Walker.validateBarrierQuorumReferences()`) accepts the parallel step's name. At the evaluator level, `preRegisterLatches()` registers the parallel block's **completion latch** against its step name. When `recordResult()` fires for the parallel step itself (after all branches terminate), it counts down the barrier latch — same mechanism as today.

**Scope close interaction:** If the parent scope closes before all branches complete, `releaseForClose()` force-counts-down all latches. The barrier step will unblock but downstream steps should treat this as partial completion. Background task interruption on scope close is the existing contract for all spawned tasks — not a special case.

### Quorum-inside-parallel vs advance-after

The parent spec (#386) describes quorum inside parallel as an early-advance pattern where the quorum step runs an action when satisfied but the parallel block still waits for ALL steps. The `advance-after` option is different: the main flow advances past the parallel block entirely.

| Pattern | Main flow advances when | Remaining steps |
|---------|------------------------|-----------------|
| Quorum inside parallel | All steps done (quorum fires action mid-block) | N/A — all waited |
| advance-after: N | N successes | Continue in background |

Choose quorum-inside-parallel when you want to trigger a side effect on N-of-M while still waiting for all. Choose advance-after when the main flow should proceed without waiting.

## Architecture

### Completion policy model

The `ParallelStep` record (currently `name`, `steps`, `decorators` at `ResolvedStep.java:82-91`) gains three fields:

```java
record ParallelStep(
    String name,                    // existing
    List<ResolvedStep> steps,       // existing
    int advanceAfter,               // new: default = steps.size() (all)
    boolean cancelRemaining,        // new: default = false
    Duration collectionTimeout,     // new: nullable
    Map<String, Object> decorators  // existing (timeout lives in decorators)
) implements ResolvedStep { ... }
```

`advanceAfter` values:
- `steps.size()` (or `all`) — today's parallel behavior
- `1` — discriminator (with cancel-remaining: false) or race (with cancel-remaining: true)
- `N` where `1 < N < steps.size()` — quorum-continue

### Two-latch pattern

The evaluator creates two `OrcLatch` instances per parallel block when `advanceAfter < steps.size()`:

1. **Advancement latch** `OrcLatch(count=advanceAfter)` — counts down on each *successful* completion. The main flow blocks on `latch.await()` until N successes.

2. **Completion latch** `OrcLatch(count=steps.size())` — counts down on any termination (success or failure). Fires when all branches are done. This latch is registered with `preRegisterLatches()` against the parallel step's name for downstream barrier synchronization.

When `advanceAfter == steps.size()` (today's parallel), the two latches are equivalent. The implementation uses the existing executor-based approach (unchanged) — the two-latch pattern only activates for `advanceAfter < steps.size()`.

### Execution model: dual path

The evaluator uses **two different execution models** depending on the completion policy:

**Path 1: `advanceAfter == steps.size()` (today's parallel)**
Unchanged. Uses `Executors.newVirtualThreadPerTaskExecutor()` in a try-with-resources block. Waits for all `Future.get()`. Returns first failure or last result. No latches, no SpawnedTask.

**Path 2: `advanceAfter < steps.size()` (discriminator/race/quorum-continue)**
Uses `scope.spawn()` to create `SpawnedTask` instances on the parent scope. Each SpawnedTask wraps:

```
Runnable branchTask = () -> {
    Result result = evaluateInternal(branchStep, ctx, runner);
    if (result.isSuccess()) {
        resultStore.recordSuccess(branchStep.name(), result.value());
        advancementLatch.countDown();
    } else {
        resultStore.recordFailure(branchStep.name(), result.error());
        tracker.onFailure();  // unreachable-threshold check
    }
    completionLatch.countDown();
};
scope.spawn(branchStep.name(), branchTask);
```

The SpawnedTasks live on the parent scope and survive the parallel step's return.

### Latch registration and recordResult() integration

`preRegisterLatches()` gains a new case for `ParallelStep` with `advanceAfter < steps.size()`:

- Creates a `ParallelCompletionTracker` (analogous to `QuorumTracker`) holding the advancement latch, completion latch, failure counter, and a reference to the parallel step's SpawnedTasks.
- Registers the completion latch in `stepLatches` against the parallel step's name — so downstream `barrier: { await: [<parallel-step>] }` connects to the right latch.
- Does NOT register individual branch steps in `stepLatches` — the branch-level coordination is handled internally by the SpawnedTask runnables, not through `recordResult()`.

`recordResult()` fires when the parallel step itself completes (after advancement latch fires). This counts down any downstream barrier latches registered against the parallel step name.

For the completion latch, a separate mechanism fires: when the completion latch reaches zero (all branches done), it triggers `recordResult()` again for the parallel step's completion event — counting down downstream barrier latches that are waiting on total completion.

### Scope ownership

Background steps spawn as `SpawnedTask` on the **parent** scope — the evaluator's `ExecutionScope`. This ensures they survive the discriminator step's completion. The parent scope's `close()` method handles final cleanup (interrupt + join with 5s timeout per task).

### Unreachable threshold detection

Same algorithm as `QuorumTracker` (`QuorumTracker.java:1-30`):

```
totalSteps = M
required = advanceAfter (N)
failures = atomic counter

onFailure:
  failures++
  if failures > totalSteps - required:
    // threshold is unreachable
    advancement latch → force count down to 0
    interrupt all running SpawnedTasks
    discriminator returns Result.failed("Advance threshold unreachable:
        required N successes from M steps, but F have failed")
```

### Background failure behavior

After advancement, background steps that fail:
- Record their error in StepResultStore (`${result.<failed-branch>}` returns an error map per StepResultStore convention)
- Count down the completion latch
- Do NOT retroactively affect the main flow — the parallel block already returned success
- Failures are logged automatically by the SpawnedTask error handling

### Variable resolution

When `advanceAfter < steps.size()`, the parser suppresses the "concurrent sibling reference" warning for `${result.<branch-step>}` references. At runtime, `StepResultStore.result(stepName)` returns null for uncompleted steps — no `UnresolvedVariableException` is thrown.

This is a semantic shift limited to parallel blocks with `advance-after` policies. Standard sequential step references are unchanged.

## Walker / StepSchemaComposer Changes

### Polymorphic parallel parsing (Walker)

`race` is already reserved in both `Walker.java:23` (`RESERVED_KEYS`) and `StepSchemaComposer.java:19` (`DECORATOR_KEYS`) but has no implementation. The unified parallel model supersedes the reserved `race` keyword.

The Walker's `parallel` branch (`Walker.java:233-237`) must handle both forms:

```
if (structuralValue is List):
    // Today's syntax: parallel: [step-a, step-b]
    resolve as ParallelStep with defaults (advanceAfter=size, cancelRemaining=false)
else if (structuralValue is Map):
    // New syntax: parallel: { advance-after: N, steps: [...] }
    extract advance-after (default: "all" → steps.size())
    extract cancel-remaining (default: false)
    extract collection-timeout (optional)
    resolve steps from the "steps" key
    construct ParallelStep with policy fields
```

### Validation rules

- `advance-after` must be a positive integer or the literal `all` (reject 0, non-integers)
- `advance-after` must be <= `steps.size()`
- `cancel-remaining` is only valid when `advance-after < steps.size()`
- `collection-timeout` is only valid when `advance-after < steps.size()` and `cancel-remaining` is false
- `advance-after: all` with `cancel-remaining: true` is accepted but treated as a no-op (no validation error — no remaining steps to cancel)
- Single-step parallel with `advance-after: 1` is valid (immediate advancement, no background work)

### StepSchemaComposer

Generate JSON Schema for the polymorphic `parallel:` value — `oneOf` accepting a list (today's form) or a map with `advance-after`, `cancel-remaining`, `collection-timeout`, and `steps` keys.

## Testing Strategy

### Integration tests (yaml-step-runtime)

- Discriminator: advance on first, verify remaining steps continue, verify all results available after completion latch fires
- Race: advance on first, verify remaining steps cancelled (interrupted)
- Quorum-continue: advance on N, verify remaining steps continue
- Backward compatibility: `parallel:` with a list (no policy) behaves identically to today
- Backward compatibility: `parallel:` with map form and no advance-after behaves identically to today
- Failure semantics: failures don't count toward advance-after
- Unreachable threshold: enough failures → discriminator fails, remaining tasks interrupted
- Timeout (decorator): advance-after not reached within timeout → failure
- Collection timeout: background steps interrupted after collection deadline
- Variable resolution: `${result.<step>}` returns null for uncompleted steps, no exception
- Barrier on parallel step: waits for completion latch (all branches done), not advancement
- Scope close during background collection → interrupt + join, barrier released by releaseForClose()
- Background failure: error recorded in StepResultStore, main flow unaffected
- Non-deterministic result: `${result.<parallel-step>}` is the advancing step's result

### Edge cases

- Single-step parallel with advance-after: 1 → immediate advancement, no background work
- All steps fail → unreachable threshold → discriminator fails
- advance-after: all with cancel-remaining: false → today's parallel
- advance-after: all with cancel-remaining: true → accepted, no-op (no remaining steps to cancel)
- advance-after: 0 → validation error
- advance-after: 1.5 → validation error (non-integer)
- advance-after > steps.size() → validation error
- Scope close during background collection → interrupt + join

## Scope and Non-Goals

### In scope

- `advance-after` and `cancel-remaining` fields on `ParallelStep`
- Polymorphic `parallel:` parsing (list or map form)
- Two-latch evaluator pattern for `advanceAfter < steps.size()`
- `collection-timeout` for background collection deadline
- Barrier integration for downstream total-completion synchronization
- Variable resolution semantic adjustment for concurrent branches
- Tests for discriminator, race, and quorum-continue policies
- Backward compatibility for existing `parallel:` list syntax

### Not in scope

- YAML aliases (`race:` as syntactic sugar for `parallel: { advance-after: 1, cancel-remaining: true }`) — can be added later as a parser convenience
- Quorum step refactoring to use the unified model — quorum exists as a reference-based step (`of: [...]`) and has different semantics (references external steps, triggers a side-effect action mid-block). It stays as-is. See "Quorum-inside-parallel vs advance-after" section for when to use each.
- Dynamic advance-after (condition-based advancement) — deferred per D3
- RaceStep (if any WIP exists on other branches) — superseded by the unified parallel model

## References

- casehubio/platform#386 — parent issue (runtime orchestration primitives spec)
- casehubio/platform#392 — this issue
- `yaml-step-runtime/.../ResolvedStep.java:82-91` — current ParallelStep record
- `yaml-step-runtime/.../StructuralStepEvaluator.java:297-332` — current evaluateParallel()
- `yaml-step-runtime/.../QuorumTracker.java:1-30` — unreachable-threshold pattern
- `yaml-step-runtime/.../Walker.java:23` — race reserved key
- `yaml-step-runtime/.../Walker.java:233-237` — parallel parsing (list cast)
- `yaml-step-runtime/.../Walker.java:383-410` — barrier/quorum reference validation
- `yaml-core/.../OrcLatch.java` — countdown latch primitive
- `yaml-core/.../SpawnedTask.java` — parallel task lifecycle
- `yaml-core/.../StepResultStore.java` — per-step result recording
- `yaml-core/.../DefaultExecutionScope.java` — scope lifecycle (spawn, close)
- `docs/specs/issue-386-runtime-orchestration/2026-09-22-runtime-orchestration-primitives-design.md` — pattern mapping table
- Decision review responses — R1-01 through R1-14
