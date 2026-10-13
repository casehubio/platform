# Decisions — Discriminator Coordination Pattern (#392)

## D1: Architecture layer

**Choice:** Step type + evaluator logic — compose from existing primitives at the orchestrator level
**Alternatives:**
- New OrcDiscriminator primitive in yaml-core — rejected after decision review: barrier and quorum are step types, not primitives. The discriminator follows the same pattern.
- YAML keyword only with no step type abstraction — too loosely structured
**Rationale:** Barrier uses `BarrierStep` + evaluator logic composing `OrcLatch`. Quorum uses `QuorumStep` + `QuorumTracker` composing `OrcLatch`. The discriminator follows the same pattern: extend the existing `ParallelStep` (or add policy fields to it) + evaluator logic composing `OrcLatch` + `SpawnedTask`. No new primitive class in yaml-core.
**Trade-offs:** No standalone Java primitive for direct programmatic use — but Java callers can compose OrcLatch + SpawnedTask directly, which is what the evaluator does.
**Sources:** yaml-core/src/main/java/io/casehub/yaml/core/orchestration/ (existing primitives), StructuralStepEvaluator (barrier, quorum evaluators)
**Exploration:** quick
**Status:** revised (R1-01: D1 contradicted D7; resolved in favor of D7's composition approach)

## D2: Result access for late-arriving completions

**Choice:** StepResultStore for per-step results + completion latch for total-completion signaling
**Alternatives:**
- StepResultStore only (no completion signal) — rejected after decision review: no mechanism for downstream to await collection-complete
- Callback/observer — push model, adds complexity without necessity
- Dedicated OrcChannel — results stream into a channel, adds indirection
**Rationale:** Late-arriving results populate `${result.<step-name>}` via StepResultStore as each step completes. A second `OrcLatch(count=M)` fires when ALL branches terminate, enabling downstream steps to await total completion. Two latches: advancement latch (count=N for the advance-after threshold) and completion latch (count=M for all branches done).
**Trade-offs:** Two latches add minor complexity but solve a real gap — without the completion latch, "continue collecting" has no synchronization point.
**Sources:** yaml-core/src/main/java/io/casehub/yaml/core/orchestration/StepResultStore.java, reviewer R1-02
**Exploration:** quick
**Status:** revised (R1-02: added completion latch for total-completion signaling)

## D3: Advancement threshold

**Choice:** Configurable advance-after: N (default 1)
**Alternatives:**
- First only — always advance on exactly the first completion
- Condition-based — advance when an arbitrary expression evaluates to true
**Rationale:** Default 1 gives "discriminator" semantics (first wins, rest continue). Configurable N subsumes quorum-then-continue without adding new concepts. The quorum step already validates that N-of-M is a well-understood pattern.
**Trade-offs:** Adds one config field vs first-only. Avoids the complexity of expression-based conditions.
**Sources:** docs/specs/issue-386-runtime-orchestration/2026-09-22-runtime-orchestration-primitives-design.md (n-of-m / quorum pattern)
**Exploration:** quick
**Status:** captured

## D4: Failure semantics

**Choice:** Failures don't count toward advancement threshold; unreachable threshold triggers immediate failure + background step interruption
**Alternatives:**
- Failures count as completions — any termination counts
- Configurable per-step — each branch declares whether failure counts
**Rationale:** Only successful completions should trigger advancement. A failed step is recorded in StepResultStore as an error but doesn't move the counter. When unreachable-threshold is detected (failures > totalSteps - required), the discriminator immediately returns failure AND interrupts all still-running background steps — they cannot contribute to a threshold that's already unreachable.
**Trade-offs:** A step that always fails can prevent advancement — this is intentional. The timeout mechanism handles the degenerate case.
**Sources:** Quorum step unreachable-threshold pattern in StructuralStepEvaluator, reviewer R1-06
**Exploration:** quick
**Status:** revised (R1-06: specified unreachable-threshold behavior for background steps)

## D5: Cleanup and scope ownership

**Choice:** Background steps spawn on parent scope; interrupt + join on scope close
**Alternatives:**
- Child scope — background tasks die when discriminator step completes (defeats purpose)
- Dedicated long-lived scope — new lifecycle pattern, added complexity
- Fire and forget — daemon threads, no cleanup guarantee
**Rationale:** Background steps must outlive the discriminator step completion. Spawning on the parent scope (the evaluator's scope) ensures they survive until the scenario ends. The standard interrupt + join 5s timeout applies at scope close. The completion latch (D2) provides the synchronization point for downstream steps that need all results before proceeding.
**Trade-offs:** Discriminator "leaks" its background work into the parent scope's task list. For a discriminator with many branches, this could delay scenario shutdown (5s join per task). Acceptable — this is the existing contract, and the completion latch gives downstream code a way to await naturally before scope close.
**Sources:** yaml-core/src/main/java/io/casehub/yaml/core/orchestration/DefaultExecutionScope.java (close() method, spawn()), reviewer R1-04
**Exploration:** quick
**Status:** revised (R1-04: explicit scope ownership decision)

## D6: YAML syntax — unified parallel completion policy

**Choice:** Extend parallel: with advance-after + cancel-remaining options
**Alternatives:**
- New top-level discriminator keyword — rejected after decision review: race/discriminator/parallel are completion policies on the same execution substrate, not three independent concepts
- Race option: cancel-remaining: false — only extends race, doesn't unify
**Rationale:** parallel/race/discriminator differ only in completion policy. A unified model:

```yaml
parallel:
  advance-after: 1         # default: all (today's semantics)
  cancel-remaining: false   # default: false
  steps: [...]
```

This gives: parallel (advance-after: all), race (advance-after: 1, cancel-remaining: true), discriminator (advance-after: 1, cancel-remaining: false), quorum-continue (advance-after: 2, cancel-remaining: false). One concept, two options, four behaviors. The underlying AST and evaluator handle these as one construct with policy options, not three separate step types.
**Trade-offs:** "discriminator" and "race" are no longer standalone keywords — less self-documenting. Can add YAML anchors or parse-time aliases if readability matters. The unified model eliminates duplicated evaluator logic.
**Sources:** reviewer R1-03, docs/specs/issue-386-runtime-orchestration (firstWins + discriminator patterns)
**Exploration:** quick
**Status:** revised (R1-03: unified under parallel with policy options)

## D7: Implementation approach

**Choice:** OrcLatch + SpawnedTask composition within StructuralStepEvaluator
**Alternatives:**
- BlockingOrcStateMachine per-step — state machine per branch + OrcCounter. Auditable but heavyweight.
- OrcChannel-based streaming — shared channel for results. Natural flow but adds indirection vs StepResultStore.
**Rationale:** Composes directly from existing primitives. OrcLatch provides exact N-completion counting (two latches: advancement + completion). SpawnedTask gives lifecycle tracking. StepResultStore gives result access. No new concurrency mechanisms. Failure-detection (unreachable threshold) is a proven pattern from quorum.
**Trade-offs:** No per-step state auditability (vs state machine approach). Acceptable — the discriminator's job is coordination, not state tracking.
**Sources:** yaml-core OrcLatch, SpawnedTask, StepResultStore interfaces; quorum step evaluator in StructuralStepEvaluator
**Exploration:** quick
**Depends on:** D1 (architecture layer), D2 (result access), D3 (threshold), D4 (failures), D5 (cleanup)
**Status:** captured

## D8: Step structure — container model

**Choice:** Container — steps are inline children of the parallel block
**Alternatives:**
- Reference-based (like quorum: of: [...]) — references named sibling steps. More composable but diverges from existing parallel syntax.
**Rationale:** The discriminator extends the existing parallel: syntax by adding policy options. Since today's parallel: is a container (ParallelStep has List<ResolvedStep> steps), the extended parallel with advance-after/cancel-remaining should remain a container. Consistent with existing AST structure.
**Trade-offs:** Cannot reference externally-defined steps like quorum does. Acceptable — the discriminator's steps are inherently "the set of branches in this parallel block."
**Sources:** ResolvedStep.java ParallelStep record, reviewer R1-07
**Exploration:** quick
**Status:** captured

## D9: Timeout semantics — advancement vs total completion

**Choice:** Timeout applies to advancement only; background steps have optional collection-timeout
**Alternatives:**
- Single timeout covering all steps — ambiguous for the discriminator case
- No timeout on background steps — they run until scope close
**Rationale:** The primary timeout covers reaching the advance-after threshold — if N steps haven't completed within the timeout, the discriminator fails. An optional collection-timeout (separate parameter) covers the background collection phase. If omitted, background steps run until scope close (bounded by parent scope's deadline or scenario end).
**Trade-offs:** Two timeout parameters adds configuration surface. The collection-timeout is optional (default: no limit) so the simple case stays simple.
**Sources:** reviewer R1-08, ExecutionScope.withDeadline()
**Exploration:** quick
**Status:** captured
