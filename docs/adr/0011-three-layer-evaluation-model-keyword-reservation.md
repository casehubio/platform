# 0011 — Three-layer evaluation model — keyword reservation

Date: 2026-09-27
Status: Accepted

## Context and Problem Statement

The YAML orchestration language has accumulated keywords across two distinct evaluation
models: `if` and `match/cases` for imperative step-level branching (#449, #445), and
`when` for reactive binding evaluation in the engine layer. A future rules engine
(Rete/PHREAK-style temporal reasoning) will introduce a third evaluation model. Without
an explicit vocabulary contract, new keywords risk colliding with existing ones — the
same word meaning different things in different contexts.

The `when` → `if` rename (#449) was the first enforcement of this principle: `when`
was repurposed from imperative guard to reactive trigger, and `if` replaced it in the
imperative vocabulary. This ADR formalises the pattern as a standing constraint.

## Decision Drivers

* Vocabulary collisions confuse authors — the same keyword meaning "check once" in one
  context and "watch continuously" in another is a design flaw, not a feature
* The three evaluation models have genuinely different semantics: one-shot vs reactive
  vs continuous temporal — keywords should signal which model they belong to
* Guard/condition syntax should converge within each layer but diverge between layers
* `${match}`, `${each}`, and future pattern-bound variables must compose without
  shadowing ambiguity across layers

## Decision

The YAML language has three evaluation layers, each owning distinct keywords:

| Layer | Keywords | Evaluation model | Scope |
|-------|----------|------------------|-------|
| Imperative (steps) | `if`, `match/cases` | Once, at execution point | yaml-core, yaml-step-runtime |
| Reactive (bindings) | `when`, `on: contextChange` | Fires when context changes | casehub-engine |
| Rules (future) | TBD — must not reuse `match`, `when`, or `if` | Continuous, temporal, Rete/PHREAK | TBD |

**Reserved keywords per layer:**

* **`if`** — imperative only. Guard (decorator) or branching (structural `if/then/else`).
  Evaluated once when the step executes. Never reactive.
* **`match`** — imperative only. N-way pattern dispatch via `match/cases`. One-shot
  evaluation against a scrutinee at execution time. Also used in state machine
  transitions via `MatchPattern` (#459). Never reactive.
* **`when`** — reactive only. Engine-layer binding condition that watches for context
  changes and fires when the condition becomes true. Never imperative.
* **`at`** — imperative only. Blocking threshold gate (default) or non-blocking guard
  (`mode: guard`). Watches numeric orchestration primitives (`OrcCounter`,
  `OrcAccumulator`, `OrcGauge<Number>`). Added by #562.
* **`on-complete`** — imperative only. Syntax sugar desugared at parse time to
  `signal:`/`wait:` pairs. Not present in the runtime AST. Added by #562.
* **`resource`** — imperative only. Decorator binding a step to a named contended
  resource (`PriorityOrcSemaphore`). Added by #562.
* **`priority`** — imperative only. Decorator annotation qualifying `resource:` with
  `background`/`normal`/`high` contention priority. Added by #562.
* **Rules keywords** — must be distinct from all of the above. Candidates should be
  evaluated in the rules engine design phase, not pre-reserved here.

**Variable scoping:**

* `${match}` and `${match.*}` — scoped to the imperative layer. Available inside
  `match/cases` case step lists and state machine transition guards where a
  MatchPattern matched. Provided by `VariableSource.matchContext()`.
* `${each}` and `${each.*}` — scoped to iteration context (`forEach`). Coexists
  with `${match}` inside nested constructs (different prefixes, no shadowing).
* Future pattern-bound variables from the rules layer must use a distinct prefix
  to avoid shadowing `${match}` or `${each}`.

## Consequences

* Any new keyword proposal must identify which layer it belongs to and verify no
  collision with the other two layers' reserved words.
* The `when` → `if` rename (#449) is not reversible — `when` is permanently
  reactive, `if` is permanently imperative.
* The rules engine design (#TBD) is free to choose its own keywords but is
  explicitly excluded from `if`, `match`, and `when`.
* Guard syntax (`when` on transitions in §2.5) is reactive vocabulary in an
  imperative context — this is intentional. The guard is evaluated at transition
  time (imperative), but the keyword `when` signals that it watches a condition
  (reactive semantics within the state machine's event-driven model).

## References

* #449 — `when` → `if` rename establishing the imperative/reactive vocabulary split
* #445 — inline block control flow design (if/else, match/cases)
* #459 — MatchPattern unification with state machine transitions
* #386 — runtime orchestration primitives (decorator evaluation order, StateMachine §2.5)
* D2 in `specs/issue-445-agent-invoke-handler-wiring/decisions.md` — vocabulary split rationale
