# Decisions — State Machine Scenario DSL (#491)

## D1: State matching — strategy-based generalization

**Choice:** Generalize `OrcStateMachine<S>` by removing the `<S extends Enum<S>>` bound to `<S>`, with strategy-based state matching — identity (enum), equality (string terms), pattern (structured terms via expression evaluators). The enum bound is removed from the interface hierarchy (`OrcStateMachine`, `BlockingOrcStateMachine`, `EventRouter`, `ScenarioScope.stateMachine()`, `PrimitiveFactory.createStateMachine()`). Existing enum callers continue to work unchanged — the bound was more restrictive than necessary.
**Alternatives:**
- String-keyed state machine only — simple but a regression from enum richness, loses MatchPattern/EventRouter expressiveness
- Dynamic enum generation — fragile, classloader complications
- Keep enum-only — can't support YAML-declared states
- Parallel interface hierarchy (new `StringOrcStateMachine`) — creates a type-system split, doubles the API surface, and every consumer that dispatches between enum and string machines needs conditional logic
**Rationale:** Three state models (enum, term string, JSON/structured term) serve different use cases. Strategy pattern lets the state machine core delegate comparison/matching to pluggable strategies. Expression evaluators (lambda, MVEL, JQ) handle the pattern strategy — already exist and are pluggable via `ExpressionEngineRegistry`. Removing the bound (rather than creating parallel interfaces) is the right design — it forces every caller to be explicit about their state type while preserving backward compatibility for enum callers. The TypeScript `OrcStateMachine` in yaml-core is already untyped, confirming cross-platform viability.
**Trade-offs:** More complex state machine internals. Identity strategy (enum) continues to use CAS via `AtomicReference` (enum singletons have reference identity). Equality strategy (strings) uses state interning to preserve CAS correctness — interned strings share reference identity, so `compareAndSet` works. Pattern strategy uses `ReentrantLock` (no identity guarantee for structured terms). The interning vs locking choice is per-strategy, not global.
**Sources:** OrcStateMachine.java, EventRouter.java (already converts enum→string for matching), ExpressionEngineRegistry (platform-api), DecoratorChain.java (existing string→enum mapping), TypeScript OrcStateMachine (yaml-core — already untyped)
**Exploration:** deep-analysis
**Revised from:** R1-02 — clarified mechanism (remove bound, not parallel interfaces) and concurrency strategy per state type. Core decision unchanged.
**Status:** revised

## D2: Not a new runtime — YAML DSL compiling to existing primitives

**Choice:** Build a YAML DSL layer on top of existing orchestration primitives, not a new executor or runtime concept
**Alternatives:**
- PlaybookStateMachineExecutor as a new runtime class — creates parallel system
- New "playbook" document type — diverges from "scenario" terminology, misaligns platform/pages
**Rationale:** ScenarioScope, OrcStateMachine, StructuralStepEvaluator, StepWalker, DeadlineContext, DecoratorChain already provide all runtime building blocks. What's missing is the declarative front-end. The YAML DSL compiles to builder calls, onEnter registrations, deadline wiring, and error transition setup. ScenarioScope and PrimitiveFactory require new overloads for string-keyed state machines (factory extension, not new primitives). A DSL compiler coordinates state lifecycle (enter → run steps → enforce deadline → await events → transition) by wiring existing primitives — this is compilation output, not a new runtime layer.
**Trade-offs:** No new abstractions to name/discover — the DSL is the interface, the runtime is existing primitives. ScenarioScope gains a new `stateMachine(String name, String initialState, StateMatchingStrategy)` overload.
**Sources:** ScenarioScope.java (factory for all primitives), StructuralStepEvaluator.java (step evaluation), DeadlineContext.java (deadline tracking)
**Exploration:** deep-analysis
**Depends on:** D1 (strategy-based matching enables YAML-declared states)
**Status:** captured

## D3: Unified YAML syntax — one syntax, two-phase parsing

**Choice:** Single YAML syntax where each state definition is a map containing both state-level metadata entries (`on-failure`, `deadline`, `on`, `terminal`) and step entries. No levels, modes, or flags. The parser uses two-phase extraction: (1) extract state-level metadata by reserved key names, (2) pass remaining entries to StepWalker as the step list. State metadata and step decorators use distinct reserved key namespaces — `on-failure` (state-level: transition to error state on any step failure) vs `on-error` (step-level: existing step decorator). `terminal` and `on` are pure state metadata with no step semantics.
**Alternatives:**
- Multi-level syntax with mode switches (`model: structured`) — extra ceremony, different mental models per level
- Separate document types per complexity level — fragmentation
- `steps:` wrapper key — unnecessary ceremony
- Separate `metadata:` and `steps:` sections per state — adds structural ceremony for a distinction the parser handles implicitly
**Rationale:** Progressive disclosure through optional keys, not modes. Each metadata entry adds a capability without restructuring existing content. Two-phase parsing cleanly separates concerns: state metadata is extracted first (configuring state lifecycle behavior), then remaining entries are resolved as steps via StepWalker. The reserved key namespace is partitioned — no ambiguity between state-level and step-level keys.
**Trade-offs:** Parser must recognize state-level reserved keys and extract them before step resolution. Reserved key namespace grows. Developers must understand the scope distinction: state metadata applies to the entire state lifecycle, step decorators apply to individual step execution. **Namespace decision:** `on-failure` is claimed by the state metadata level (transition to error state on any step failure). This intentionally forecloses `on-failure` as a future step-level decorator — step-level error handling uses `on-error` (existing). The naming is deliberate: `on-error` = recoverable (step handles it), `on-failure` = terminal-for-this-scope (state escalates to error state). `on-success` remains available at both state and step levels for future use.
**Sources:** StepWalker.java (existing reserved key handling, `on-failure` in RESERVED_KEYS line 21 — currently unprocessed), DecoratorChain.java (existing decorator extraction)
**Exploration:** deep-analysis
**Revised from:** R1-04 — clarified two-phase parsing approach, explicit scope distinction between state metadata and step decorators. R2-03 — explicit `on-failure` namespace acknowledgment added. Core syntax decision (unified map, no modes) unchanged.
**Depends on:** D2 (DSL-not-runtime framing)
**Status:** revised

## D4: Explicit completion transitions via `next:` state metadata

**Choice:** States declare their completion successor explicitly via `next: STATE_NAME` metadata entry. States without `next:` and without `on:` are terminal. States with `on:` are event-driven (wait for external triggers). Conditional branching on completion uses existing step DSL (`if/then/else`, `match/cases` with `transition` decorator).
**Alternatives:**
- Implicit insertion-order transitions (original choice) — relies on YAML map ordering which is not guaranteed by the YAML 1.2 spec (Jackson/SnakeYAML preserve insertion order via LinkedHashMap as an implementation artifact). Tooling reordering (YAML linters, formatters, git merge tools) could silently change program semantics. Validation cannot catch all reordering bugs when states have multiple inbound paths.
- All transitions explicit via step DSL — too verbose for the common linear case, forces every state to end with a transition step
- New `transitions:` section — separates flow topology from state behavior, making the playbook harder to read linearly
**Rationale:** The common case (linear state progression) needs low ceremony but must not rely on implementation artifacts. `next: STATE_NAME` is one line per state — trivially low cost — and makes the transition explicit and self-documenting. No dependency on YAML ordering. Each state's successor is visible in the state definition without consulting the document structure.
**Trade-offs:** One additional line per non-terminal, non-event-driven state. For a 5-state linear machine, that's 4 `next:` lines. Branching still uses existing step DSL — no new concepts needed.
**Sources:** StepWalker.java (existing `transition` reserved key), DecoratorChain.wrapTransition() (existing transition decorator handling)
**Exploration:** quick
**Revised from:** R1-05 — original insertion-order approach replaced with explicit `next:` metadata. Reviewer correctly identified that relying on YAML map ordering creates a category of silent, hard-to-debug failures for trivially low ergonomic savings.
**Depends on:** D3 (unified syntax — `next:` is state metadata, extracted in phase 1)
**Status:** revised

## D5: Expression-resolvable deadlines with validation

**Choice:** Deadline duration supports `${...}` variable expressions resolved via VariableResolver at state entry time. Validation: resolved value must parse as a valid duration (via DurationParser), must be positive, and must not exceed a configurable maximum (`casehub.playbook.max-deadline`, default 72h). On resolution failure (null, non-duration string, negative value, exceeds maximum): the state machine transitions to its `on-failure` target if one is defined, otherwise throws `InvalidDeadlineException` which propagates to the caller. Config-derived deadlines (`${config.sla.response-time}`) are validated at parse time when the config value is statically known; result-derived deadlines (`${result.INTAKE.sla-deadline}`) can only be validated at runtime.
**Alternatives:**
- Hardcoded durations only — limits domain configurability
- Deadline profiles (named configs) — adds indirection without clear benefit over direct VariableResolver expressions which already support config sources
- Separate compile-time vs runtime resolution paths — ConfigVariableSource is already available during VariableResolver resolution; splitting the resolution path adds complexity without architectural benefit since VariableResolver handles both uniformly
**Rationale:** SLA deadlines are domain-specific and often configurable. VariableResolver is already available in the step evaluation context. `${config.sla.response-time}` and `${result.INTAKE.sla-deadline}` are natural expressions. Validation bounds prevent operational risks: a negative deadline or unbounded duration in an incident playbook is a safety hazard. The `on-failure` fallback ensures deadline resolution failures are recoverable in the same way as step failures.
**Trade-offs:** Deadline duration not known until state entry — can't validate result-derived deadlines at parse time, only at runtime. Config-derived deadlines get parse-time validation when the config source is available.
**Sources:** VariableResolver (yaml-core), ConfigVariableSource (yaml-step-runtime, #490), DeadlineContext.withTimeout(), DurationParser (yaml-core)
**Exploration:** quick
**Revised from:** R1-06 — added validation bounds, fallback behavior, and max-deadline configuration. Unified resolution approach defended; error semantics added.
**Depends on:** D3 (unified syntax)
**Status:** revised

## D6: Format ownership — yaml-core is the canonical spec layer

**Choice:** The YAML state machine format is defined once in yaml-core as a cross-platform specification. yaml-core owns the format grammar, reserved metadata keys, state-level semantics, and transition rules. Platform (Java) and pages (TypeScript) each provide runtime compilers that compile the format to their respective primitives. A detailed issue in casehub/pages tracks TypeScript alignment.
**Alternatives:**
- Platform-first design, pages retrofit — creates asymmetric constraints shaped by Java's type system (enums, j.u.c, AtomicReference CAS). TypeScript has dynamic objects as its natural abstraction; a Java-first format would feel foreign.
- Pages-first design — same asymmetry in the other direction
- Independent specs per platform — drift, inconsistency, maintenance burden
**Rationale:** yaml-core already spans both platforms — `OrcStateMachine`, `ScenarioScope`, `VariableResolver`, and `MatchPattern` all have both Java and TypeScript implementations. The YAML state machine format is a natural extension of yaml-core's cross-platform role. Defining the format in yaml-core ensures both platforms design from the same specification, avoiding platform-specific bias.
**Trade-offs:** Changes to the format require cross-platform coordination. The pages issue becomes a dependency for full cross-platform consistency.
**Exploration:** quick
**Revised from:** R1-07 — elevated from action item to design decision. Format ownership explicitly placed in yaml-core.
**Status:** revised

## D7: Lifecycle Protocol exemption — YAML state machines are operational workflow coordinators

**Choice:** YAML-declared state machines do NOT register with the Lifecycle Protocol (PP-20260620-ed1230). They are operational workflow coordinators, not domain lifecycle state machines. The `terminal` state metadata entry provides runtime classification equivalent to `isTerminal()` on domain enums.
**Alternatives:**
- Register YAML state machines with the Lifecycle Protocol — requires extending the protocol to handle runtime-declared state machines with string states that have no methods. Adds protocol complexity for a category of state machines the protocol was not designed to govern.
- Extend the Lifecycle Protocol to cover all state machines — the protocol exists specifically for domain-model enums (PlanItemStatus, WorkItemStatus, CommitmentState) where terminal-state confusion across boundaries caused real bugs. Operational workflow states (DETECTED → TRIAGING → RESOLVED) don't cross domain boundaries in the same way.
**Rationale:** The Lifecycle Protocol (PP-20260620-ed1230) mandates that lifecycle classification methods (`isTerminal()`, `isActive()`) live on the enum itself. This serves domain-model enums where consumers across multiple modules need to query state classification. YAML playbook state machines are scoped to a single scenario execution — their terminal/active classification is consumed by the DSL compiler, not by cross-module consumers. The `terminal` metadata entry serves the same purpose within the DSL's scope. Extending the protocol to cover runtime-declared string states would add complexity to a protocol designed for compile-time enum types, without the cross-boundary benefit that motivated the protocol.
**Trade-offs:** YAML state machine states cannot be queried via the standard `isTerminal()` protocol by external consumers. This is intentional — external consumers interact with the playbook's exposed API (start, status, cancel), not with individual state machine internals.
**Sources:** PP-20260620-ed1230 (lifecycle-enum-classification-on-enum.md), D3 (terminal metadata)
**Exploration:** surfaced by reviewer (R1-08)
**Status:** captured

## D8: Error model — step failure propagation and state-level `on-failure`

**Choice:** Step failure within a state follows the existing StepWalker/DecoratorChain error model. State-level error handling adds one new mechanism: the `on-failure` state metadata entry specifies a target state to transition to when any step in the state fails (after step-level error handling has been exhausted). Without `on-failure`, unhandled step failure halts the state machine in the current state and returns the failure to the caller — the DSL execution loop receives a `Result.Failure` or catches a `RuntimeException` and surfaces it via the playbook's status API. Step-level `on-error` (existing decorator) handles individual step failures; state-level `on-failure` handles state-level failure escalation. These compose: a step's `on-error` catches and handles the error → step succeeds → state continues. If `on-error` is absent or the step still fails → state-level `on-failure` fires the transition. Partial state execution is possible: completed steps' results are stored in StepResultStore; the failing step's result is a `Result.Failure`. Without `on-failure`, both failure types halt the state machine and notify the caller — `Result.Failure` is returned as a value (the caller inspects `isSuccess()`), `RuntimeException` is thrown (the caller catches). The difference is the notification mechanism, not whether notification occurs. The `on-failure` key is intentionally reserved at the state metadata level (see D3). This forecloses future use of `on-failure` as a step-level decorator — step-level error handling uses `on-error` (existing), and any future step-level failure semantics beyond `on-error` would use a different key name. `on-success` remains available at both levels.
**Alternatives:**
- Halt on any failure, no recovery — too rigid for incident lifecycle playbooks where degraded-mode operation is essential
- Automatic retry at state level — duplicates existing step-level `retry` decorator semantics; state-level retry is better expressed as a loop in the step DSL
- Transaction-like rollback (undo completed steps) — overly complex for a coordination DSL; step results are immutable once stored
**Rationale:** The error model composes existing primitives (StepWalker error handling, DecoratorChain `on-error`, Result.Failure) with one new state-level mechanism (`on-failure` transition). This follows the platform's pattern of layering new capabilities onto existing building blocks rather than creating parallel error handling systems. The two-level error model (step-level `on-error` for recovery, state-level `on-failure` for escalation) mirrors exception handling in structured programming: catch at the statement level, catch at the method level.
**Trade-offs:** Two error mechanisms at different scopes could confuse authors. Mitigated by distinct naming (`on-error` vs `on-failure`) and clear documentation of the composition rule.
**Sources:** StepWalker.java (existing error handling), DecoratorChain.wrapOnError() (existing on-error decorator), Result.Failure (existing failure type)
**Exploration:** surfaced by reviewer (R1-09)
**Revised from:** R2-01 — removed contradictory "halts silently" wording, specified exact propagation semantics for both failure types. R2-03 — added explicit `on-failure` namespace reservation note.
**Depends on:** D3 (on-failure is state metadata, extracted in phase 1)
**Status:** revised

## D9: Persistence — in-memory by design, resumability via StepResultStore

**Choice:** YAML state machines are in-memory coordination primitives. State machine position is not persisted. Resumability after process restart is achieved by reconstructing the state machine from step-level completion data in StepResultStore. Deadline timers reset on reconstruction (the elapsed wall-clock time during downtime is lost).

**Reconstruction mechanism:** StepResultStore is step-keyed (`recordSuccess(String stepName, ...)`, `hasCompleted(String stepName)`). Step names are unique across the entire playbook (enforced by StepWalker's duplicate name check). The DSL compiler knows which steps belong to which state from the YAML definition. Reconstruction walks the state sequence (following `next:` chain) and queries StepResultStore for each named step in each state:
- All named steps in a state completed successfully → state is complete, advance to next state
- Some steps completed but one failed → re-enter the state from the first incomplete step (skipping already-completed steps)
- No steps completed → this is the target state, start from the beginning

No step naming convention is needed — step names are already unique, and the state→step mapping is derived from the YAML definition at reconstruction time. If the YAML definition changes between runs (steps added or removed), reconstruction is invalidated — the compiler detects the mismatch via a definition hash stored alongside step results and falls back to starting from the initial state.

**Alternatives:**
- Persistent state machine with checkpoint/resume — adds a persistence dependency to a coordination primitive, couples the state machine to a specific storage backend, and introduces complex failure modes (stale checkpoints, concurrent resume, split-brain)
- Event-sourced state machine — architecturally elegant but introduces event store dependency and replay complexity that is disproportionate for the coordination use case
- Hybrid (in-memory + optional persistence adapter) — deferred to a future decision if operational experience shows that restart-resume is a common need
- State-keyed result store — adding state-level recording to StepResultStore would require extending the SPI with `recordStateCompletion(String stateName)` and `hasStateCompleted(String stateName)`. This adds state-machine-specific concerns to a general-purpose step result store, coupling the SPI to the playbook feature. Step-level data is sufficient for reconstruction.
**Rationale:** OrcStateMachine is a coordination primitive backed by `AtomicReference` (or `ReentrantLock` for blocking variant). It coordinates step execution within a scenario — it is not a durable workflow engine. Durable state belongs in StepResultStore (which can have persistent implementations). For incident lifecycle playbooks that span hours, the operational pattern is: (1) the monitoring system detects the incident, (2) the playbook runs in-memory, (3) if the process restarts, the monitoring system re-fires the incident event, (4) the playbook's DSL compiler reads the YAML definition, queries StepResultStore for each step, infers which states completed, and reconstructs the state machine at the appropriate state. This is reconstruction, not resume — simpler and more reliable than checkpoint-based persistence.
**Trade-offs:** Deadline timers reset on reconstruction — a 2-hour SLA deadline that had 30 minutes remaining is reset to 2 hours. This is a known limitation; deadline tracking relative to incident creation time (an external timestamp, not an in-memory timer) would address this but adds complexity deferred to operational experience. Definition changes between runs invalidate reconstruction — a rolling deployment that modifies the playbook mid-incident will restart the playbook from scratch.
**Sources:** DefaultOrcStateMachine.java (AtomicReference state), DefaultBlockingOrcStateMachine.java (ReentrantLock + Condition), StepResultStore (existing result persistence SPI), StepWalker.java (duplicate step name enforcement), StructuralStepEvaluator.recordResult() (step-keyed recording)
**Exploration:** surfaced by reviewer (R1-10)
**Revised from:** R2-02 — added reconstruction mechanism specification: step-level data is sufficient, no naming convention needed, definition hash for change detection.
**Status:** revised
