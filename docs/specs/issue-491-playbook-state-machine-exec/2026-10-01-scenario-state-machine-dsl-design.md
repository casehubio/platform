# Scenario State Machine DSL — Design Spec

**Issue:** casehubio/platform#491
**Pages alignment:** casehubio/casehub-pages#509
**Date:** 2026-10-01
**Module:** yaml-core (format spec), yaml-step-runtime (Java compiler)

---

## Problem

The platform provides orchestration primitives — `OrcStateMachine`, `ScenarioScope`, `StructuralStepEvaluator`, `DeadlineContext`, `EventRouter`, `MatchPattern` — that fully support state-machine-driven scenarios. But composing them requires Java code: builder calls, handler registrations, deadline wiring, error transition setup. There is no declarative way to define a state-machine-driven scenario in YAML.

The incident lifecycle pattern — DETECTED → TRIAGING → RESPONDING → RESOLVED with deadline fallbacks and cleanup guarantees — is cross-domain. Trading (flash crash), SOC (security incident response), clinical (patient care escalation), AML (investigation lifecycle) all follow the same shape. Each domain would write the same Java boilerplate to wire the same primitives.

## Solution

A YAML DSL for state-machine-driven scenarios that compiles to existing orchestration primitives. Not a new runtime — a declarative front-end.

### Three deliverables

1. **Generalize `OrcStateMachine`** — remove `<S extends Enum<S>>` bound, add strategy-based state matching
2. **YAML format specification** — defined in yaml-core as a cross-platform spec
3. **DSL compiler** — in yaml-step-runtime, compiles YAML to `OrcStateMachine` builder calls, `onEnter` handler registrations, deadline wiring, error transition setup

---

## Architecture

### Strategy-based state matching (D1)

Remove the `extends Enum<S>` bound from `OrcStateMachine<S>`, `BlockingOrcStateMachine<S>`, `EventRouter<S>`, `ScenarioScope.stateMachine()`, and `PrimitiveFactory.createStateMachine()`. Introduce `StateMatchingStrategy<S>` to handle comparison and concurrency:

| Model | State type | Strategy | Concurrency |
|-------|-----------|----------|-------------|
| **Enum** | `E extends Enum<E>` | Identity (`==`) | CAS via `AtomicReference` |
| **Term string** | `String` | Equality via interning | CAS (interned strings share reference identity) |
| **Structured term** | `Map<String,Object>` | Pattern/expression matching | `ReentrantLock` |

Existing enum callers continue to work unchanged. The expression evaluator infrastructure (lambda, MVEL, JQ via `ExpressionEngineRegistry`) handles the pattern strategy.

### DSL compilation — not a new runtime (D2)

The DSL compiler reads the YAML and wires existing primitives:

```
YAML scenario definition
    ↓ parse + validate
ScenarioStateMachineCompiler
    ↓ compile
OrcStateMachine          ← builder calls (states, transitions, terminal states)
  + onEnter handlers     ← StructuralStepEvaluator.evaluate(steps, resolver, runner)
  + deadline wiring      ← ScenarioScope.withDeadline(duration, → transition)
  + error transitions    ← try/catch around step execution → transition to on-failure target
  + event routing        ← EventRouter for on: event mappings
```

`ScenarioScope` gains a new overload: `stateMachine(String name, String initialState, StateMatchingStrategy)`.

### Format ownership — yaml-core (D6)

The YAML state machine format is defined once in yaml-core as a cross-platform specification. Platform (Java) and pages (TypeScript) each provide runtime compilers. The TypeScript `OrcStateMachine` in yaml-core is already untyped, confirming cross-platform viability.

---

## YAML Syntax

### One unified syntax (D3)

Each state definition is a list. Some entries are steps (passed to StepWalker), some are state metadata (extracted by the compiler in a first parsing phase). No modes, no flags.

### State metadata entries

| Entry | Form | Meaning |
|-------|------|---------|
| `next` | `next: STATE` | Completion successor — transition to STATE when steps complete |
| `on-failure` | `on-failure: STATE` | On any step failure, transition to STATE |
| `deadline` | `deadline: 120s -> STATE` | Timeout → transition to STATE. Duration supports `${...}` expressions |
| `on` | `on: { event: STATE }` | Event-driven — wait for events after entry steps complete |
| `terminal` | `terminal: true` | Terminal state (no outgoing transitions). Or bare `terminal` scalar for states with no entry steps |

### Transition model (D4)

- `next: STATE` → **completion-driven**: steps complete → transition to next state
- `on: { ... }` → **event-driven**: steps complete → wait for external event
- `next:` and `on:` are **mutually exclusive** — if a state needs both a default completion path and event-driven transitions, use `on:` with all paths explicit (e.g. `on: { complete: RESOLUTION, escalate: ESCALATED }`)
- No `next:` and no `on:` → **terminal** (must have `terminal: true` or be declared as bare `terminal`)
- Conditional branching on completion uses existing step DSL (`if/then/else`, `match/cases` with `transition` decorator)

### Event guard syntax — scalar-or-object polymorphism

```yaml
# Simple: event name → target state
on:
  approve: APPROVED
  reject: REJECTED

# Guarded: event name → { to, when }
on:
  ship:
    to: SHIPPED
    when: ${inventory.available}
  cancel: CANCELLED

# Match-based: event name → array of match cases
on:
  assess:
    - match: { severity: critical }
      to: IMMEDIATE_RESPONSE
    - match: { severity: low }
      to: DEFERRED
    - to: STANDARD_RESPONSE
```

### Expression-resolvable deadlines (D5)

```yaml
- deadline: 120s -> ESCALATED                              # literal
- deadline: ${config.sla.response-time} -> ESCALATED       # config-derived
- deadline: ${result.INTAKE.sla-deadline} -> ESCALATED     # result-derived
```

Validation: resolved value must parse as a valid duration, be positive, and not exceed `casehub.playbook.max-deadline` (default 72h). On resolution failure: transition to `on-failure` target if defined, otherwise throw `InvalidDeadlineException`.

---

## Use cases

### Linear incident lifecycle

```yaml
states:
  DETECTED:
    - next: TRIAGING
    - on-failure: ESCALATED
    - deadline: 120s -> ESCALATED
    - notify.triage-team:
        channel: ${config.triage.channel}

  TRIAGING:
    - next: RESPONDING
    - on-failure: ESCALATED
    - classify.severity:
        input: ${result.DETECTED.output}

  RESPONDING:
    - next: RESOLVED
    - deadline: 300s -> ESCALATED
    - execute.remediation:
        plan: ${result.TRIAGING.plan}

  RESOLVED: terminal

  ESCALATED:
    - terminal: true
    - notify.escalation:
        severity: critical
```

### Event-driven order processing

```yaml
states:
  PENDING:
    - on:
        approve: APPROVED
        reject: REJECTED
    - validate.order: {}

  APPROVED:
    - on:
        ship:
          to: SHIPPED
          when: ${inventory.available}
        cancel: CANCELLED
    - reserve.inventory: {}

  SHIPPED:
    - on:
        deliver: DELIVERED

  DELIVERED: terminal
  CANCELLED: terminal
  REJECTED: terminal
```

### Conditional branching on completion

```yaml
states:
  DETECTED:
    - assess.incident:
        input: ${event.data}
    - if: ${result.assess-incident.severity} == "critical"
      then:
        - transition: IMMEDIATE_RESPONSE
      else:
        - transition: STANDARD_RESPONSE

  IMMEDIATE_RESPONSE:
    - next: RESOLVED
    - execute.emergency-protocol: {}

  STANDARD_RESPONSE:
    - next: RESOLVED
    - execute.standard-protocol: {}

  RESOLVED: terminal
```

### Mixed: completion-driven with error handling and events

```yaml
states:
  INTAKE:
    - next: ASSESSMENT
    - on-failure: FAILED
    - receive.case:
        source: ${config.intake.source}

  ASSESSMENT:
    - on-failure: FAILED
    - deadline: ${config.sla.assessment} -> ESCALATED
    - on:
        complete: RESOLUTION
        escalate: ESCALATED
    - run.assessment:
        case-id: ${result.INTAKE.case-id}

  RESOLUTION:
    - next: CLOSED
    - deadline: ${config.sla.resolution} -> ESCALATED
    - execute.resolution:
        plan: ${result.ASSESSMENT.plan}

  CLOSED: terminal
  FAILED: terminal

  ESCALATED:
    - terminal: true
    - notify.supervisor:
        case-id: ${result.INTAKE.case-id}
```

---

## Error model (D8)

Two-level composition:

1. **Step-level `on-error`** (existing decorator) — individual step recovery. If the step handles the error, execution continues.
2. **State-level `on-failure`** (new metadata) — state escalation. If a step fails after step-level handling is exhausted, the state machine transitions to the `on-failure` target state.

Without `on-failure`: unhandled step failure halts the state machine in the current state and returns the failure to the caller.

Naming: `on-error` = recoverable (step handles it). `on-failure` = terminal-for-this-scope (state escalates). `on-failure` is reserved at the state metadata level — not available as a step decorator.

---

## Persistence and resumability (D9)

YAML state machines are in-memory coordination primitives. State machine position is not persisted. Resumability after process restart is achieved by reconstructing from `StepResultStore`:

1. Walk the state sequence (following `next:` chain)
2. Query `StepResultStore.hasCompleted(stepName)` for each named step in each state
3. All named steps complete → state is complete, advance
4. Some steps complete, one failed → re-enter state from first incomplete step
5. No steps complete → this is the target state, start from beginning

Definition changes between runs are detected via a definition hash stored alongside step results — mismatch falls back to starting from the initial state.

Deadline timers reset on reconstruction (elapsed wall-clock time during downtime is lost).

---

## Lifecycle Protocol exemption (D7)

YAML-declared state machines are operational workflow coordinators, not domain lifecycle state machines. They do NOT register with the Lifecycle Protocol (PP-20260620-ed1230). The `terminal` metadata entry provides runtime classification equivalent to `isTerminal()` on domain enums.

---

## Parse-time validation

The DSL compiler validates at parse time:

- All referenced states (next, on-failure, deadline targets, event targets) exist in the state declaration
- Initial state is the first state in the definition and must not be terminal
- At least one terminal state exists
- `next:` and `on:` are mutually exclusive per state
- No transitions defined from terminal states
- All non-terminal states have either `next:` or `on:` (no dead-end states)
- All states reachable from the initial state (no orphans)
- No duplicate state names
- Deadline durations parse correctly (for literal values; expression-derived validated at runtime)
- Step names unique across the entire playbook (enforced by StepWalker)

---

## Scope

### In scope
- Remove `extends Enum<S>` bound from `OrcStateMachine` interface hierarchy
- `StateMatchingStrategy<S>` with identity, equality, pattern implementations
- YAML format spec in yaml-core (metadata keys, transition rules, validation contract)
- DSL compiler in yaml-step-runtime (parse → validate → wire primitives)
- `ScenarioScope.stateMachine()` overload for string-keyed state machines
- Parse-time validation checks
- Unit tests for all components

### Out of scope
- Persistent state machine checkpointing (D9 — in-memory by design)
- Distributed execution / SWF delegation (casehubio/platform#486)
- TypeScript implementation (casehubio/casehub-pages#509 — tracked separately)
- Visual scenario editor integration
- JSON Schema for the YAML format (deferred to operational experience)

## References

- `OrcStateMachine.java` — current interface with `<S extends Enum<S>>` bound
- `DefaultOrcStateMachine.java` — builder, CAS transitions, EventRouter integration
- `BlockingOrcStateMachine.java` — await/awaitAnyState for blocking coordination
- `EventRouter.java` — MatchPattern-based event dispatch, already converts enum→string
- `ScenarioScope.java` — factory for orchestration primitives
- `StructuralStepEvaluator.java` — step evaluation with decorator chain
- `StepWalker.java` — YAML step resolution, reserved key handling
- `DecoratorChain.java` — existing `transition` decorator (lines 451-500)
- `DeadlineContext.java` — deadline tracking with timeout composition
- `ExpressionEngineRegistry` — pluggable expression evaluation (lambda/MVEL/JQ)
- `MatchPattern.java` — sealed interface: ValuePattern, StructuralPattern, AnyOfPattern, DefaultPattern
- `ConfigVariableSource.java` — #490, bridges SmallRyeConfig to VariableResolver
- casehubio/platform#486 — distributed command dispatch (future, builds on this)
- casehubio/casehub-pages#509 — TypeScript alignment issue
- PP-20260620-ed1230 — Lifecycle Protocol (exemption documented in D7)
