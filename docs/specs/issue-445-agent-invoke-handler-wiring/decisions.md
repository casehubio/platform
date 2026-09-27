# Decisions — Inline Block-Level Control Flow

## D1: Block grouping via `block:` structural keyword

**Choice:** Introduce `block:` as a structural keyword that groups 1..N steps into a compound step. All existing decorators (`loop`, `forEach`, `if`, `timeout`, `retry`, `on-error`, `semaphore`, `delay`, `signal`, `transition`, `transform`) apply uniformly to blocks, exactly as they do to single steps.
**Alternatives:**
- Every control flow keyword becomes structural (contains `steps:`) — composition requires nesting, deep indentation, violates the tipping-point guidance at 2+ control flow concerns
- No grouping keyword, structural form only for loop/forEach/if/match — inconsistent (why can loop wrap a group but retry can't?), still requires nesting for composition
- Module extraction for multi-step iteration — current approach; requires a separate file for inline grouping, excessive ceremony for 2-3 steps
- `do:` keyword (CNCF Serverless Workflow 1.0 precedent) — semantically clearer as a verb ("do these steps"), aligns with platform's SW 1.0 architectural ancestor. Rejected: (1) `block:` is structurally consistent with `parallel:` — both are noun-like compound step types describing execution topology; `do:` mixes a verb with `parallel:`'s noun for the same class of construct, (2) decorator composition reads naturally with a noun target ("retry the block", "loop the block"), (3) platform step vocabulary already diverges from SW 1.0 (action vs call, decorators vs try/catch, parallel vs fork) — selective alignment on one keyword creates false expectation of broader alignment, (4) `block` has broad programming precedent (Ruby, Kotlin, Smalltalk, C) beyond Ansible's block/rescue/always
**Rationale:** `block:` is one concept that unlocks everything. Decorator composition stays flat (stacked on the block) rather than nested. Consistent with `parallel:` (already a structural keyword containing a step list). Adds exactly one line of overhead vs a single step.
**Trade-offs:** Two ways to express "conditional group" — `block:` + `if` decorator vs `if/then` structural. Convention: use decorator for simple guards (no else), structural for branching (with else).
**Sources:** issue-386 §Parallel Execution (parallel: structural keyword precedent), issue-386 §Decorator Evaluation Order, Ansible block/rescue/always pattern, CNCF Serverless Workflow 1.0 `do:` task (considered, rejected — see Alternatives)
**Exploration:** deep-analysis
**Status:** captured

## D2: `if` replaces `when` for imperative guards

**Choice:** Rename `when` → `if` everywhere in the imperative step vocabulary (step decorators, import conditions, ForEachAdapter, YamlImport). Engine-layer `Binding.getWhen()` is NOT renamed — it IS reactive (fires on context-change events) and retains the `when` keyword correctly.
**Alternatives:**
- Keep `when` for imperative guards — semantically misleading; `when` implies watching for state change (reactive), not checking a condition at a point in execution (imperative)
- Drop `when` entirely with no replacement — removes conditional execution, obviously wrong
**Rationale:** In rules engines (Drools, OPA), `when` is reactive — it watches for a pattern to match. In imperative sequences, `if` is the correct keyword — it checks a condition at execution time and branches. The orchestration language is imperative (steps execute sequentially), so `if` is the right vocabulary. This creates an intentional vocabulary split: step-layer `if:` (imperative, check now) vs engine-layer `when:` (reactive, watch for context change). The split reflects the genuine architectural boundary between imperative orchestration and reactive binding evaluation.
**Trade-offs:** Breaking change to issue-386 spec (which uses `when` throughout). Migration scope: ~20 Java references in yaml-core (ForEachAdapter.getWhen() interface + 3 implementations + 5 call sites, YamlImport.when record field + 6 call sites, ImportExpander + ModuleExpander), ~15 TypeScript references (schema.ts, types.ts, expand.ts, foreach-expander.ts). All mechanical renames. The `when` field on YamlImport is also renamed to `if`.
**Sources:** Drools LHS/RHS semantics, Java if/else, issue-386 §1.1 `when` decorator, issue-386 D3 (dropped if/else — revisited here)
**Exploration:** deep-analysis
**Depends on:** Revises issue-386 D3
**Status:** revised — absorbed D6 content; corrected migration scope from "no code impact" to ~35 references across Java and TypeScript; documented engine-layer `when:` vocabulary split

## D3: `if/then/else` structural branching

**Choice:** `if:` + `then:` [+ `else:`] as a structural step type for 2-way branching. Disambiguated from `if` decorator by presence of `then:` key. `else:` is optional. Decorators can wrap the entire if/else (timeout, loop, retry, etc.).
**Alternatives:**
- `when`-pairs only (issue-386 D3 original) — manually maintaining mutually exclusive conditions over step groups is error-prone and verbose
- `branches:` keyword with named branches — more complex, less familiar
**Rationale:** `if/else` is universally understood. The disambiguation rule (presence of `then:` → structural) is simple and unambiguous. The YAML duplicate-key constraint means `if` decorator + `if/else` structural can't coexist on one entry — resolved by wrapping in `block:` for the rare guard-on-conditional case.
**Trade-offs:** Two forms of `if` (decorator vs structural). Convention makes this clear: no `then:` → guard, has `then:` → branch. When both forms must coexist on the same step (guard-on-conditional: "if enabled, then if value do A else do B"), the YAML duplicate-key constraint forces a `block:` wrapper — the `if` decorator goes on the outer block, the `if/then/else` structural goes inside. This adds one level of nesting for what is conceptually a single concern. The pattern is rare in orchestration YAML (orchestration steps tend to have one control-flow concern per step), but should be documented in usage guidance with explicit examples. Alternative keywords (`guard:`, `condition:`) were considered to avoid the duplicate-key issue — `guard:` would replace `if:` as the decorator name (keeping the keyword count at 2, not adding a third), eliminating the duplicate-key problem entirely. Rejected because `if` is universally understood as an imperative guard and trading DX familiarity for duplicate-key avoidance that `block:` already handles is not worth the vocabulary unfamiliarity. The `block:` wrapper makes the grouping explicit, which is the intended design.
**Sources:** Every imperative language ever, issue-386 D3 (revisited), YAML duplicate key constraint analysis
**Exploration:** deep-analysis
**Depends on:** D2 (if replaces when)
**Status:** revised — expanded trade-offs with explicit guard-on-conditional pattern documentation and alternative keyword analysis

## D4: `match/cases` pattern matching

**Choice:** `match:` structural step type with `cases:` list for N-way branching. Each case has `pattern:` (value or structural equality), optional `guard:` (boolean expression), and `steps:` (step list). `default:` for catch-all. First-match-wins evaluation. `${match}` and `${match.*}` expose the matched value inside cases. Parse-time exhaustiveness warnings when no `default:`.
**Alternatives:**
- `StateMachine` only for N-way branching (issue-386 D3) — too heavy for simple value dispatch; StateMachine is for stateful routing, not one-shot branching
- `switch:` keyword — less expressive than pattern matching; doesn't support structural matching or guards
- Nested if/else chains — ugly in YAML, deep indentation, no exhaustiveness checking
**Rationale:** Pattern matching is the modern standard (Java 21+, Rust, Scala, Kotlin). Value matching handles simple dispatch. Structural matching handles event-type routing. Guards handle constrained cases. First-match-wins gives ordered specificity (most specific patterns first). This replaces both `when`-pairs for N-way branching and the `StateMachine` recommendation for simple dispatch. Note: the construct borrows the syntax and structure of pattern matching from these languages, not their type-safety guarantees. YAML `match:` operates on runtime expression values, not compile-time types — exhaustiveness checking is a parse-time lint (warning when no `default:` case exists), not a sealed-type exhaustiveness proof as in Java 21+ or Rust.
**Trade-offs:** `match` is a richer construct than `if/else` — more to learn. But it replaces the error-prone `when`-pair pattern for anything beyond 2 branches, which is a net DX win.
**Sources:** Java 21+ pattern matching (JEP 441 — syntax/structure precedent, not type-safety equivalence), Drools pattern matching, Rust match, issue-386 D3 (StateMachine for complex routing — still valid for stateful cases)
**Exploration:** deep-analysis
**Depends on:** D2 (when → if rename frees the vocabulary space)
**Status:** revised — clarified exhaustiveness scope: parse-time lint, not type-safe proof

## D5: Uniform decorator-on-step-type model

**Choice:** All decorators apply uniformly to all step types. Position 10 in the decorator evaluation order becomes polymorphic: action / block / if-else / match / parallel. No decorator knows or cares what step type it wraps. The decorator evaluation order is otherwise unchanged (position 1: `if` renamed from `when`).
**Alternatives:**
- Restrict certain decorators to certain step types — artificial limitations, implementation complexity for no user benefit
- Different evaluation orders per step type — confusing, hard to reason about
**Rationale:** The decorator stack is an execution wrapper. It wraps "the thing that runs" regardless of what that thing is. This is the same principle as Java's decorator pattern — the wrapper doesn't know the concrete type. Tested all decorator × step type combinations: no invalid or incoherent permutations found (except YAML duplicate-key for `if` decorator on `if/else` structural — resolved via `block:` wrapper).
**Trade-offs:** This explicitly deprecates the structural `loop.steps` and `forEach.steps` forms from issue-386 (§1.2 lines 195–205, §1.3 lines 245–256). Under the uniform model, `loop` and `forEach` are always decorators wrapping position 10 content. Multi-step loop/forEach bodies use `block:` + decorator instead of the structural `steps:` child. This eliminates duplication (two ways to express multi-step iteration) and makes the model consistent — every decorator wraps a step type, no decorator IS a step type.
**Sources:** issue-386 §Decorator Evaluation Order (13-position stack), permutation analysis in this brainstorm, issue-386 §1.2–1.3 (structural loop/forEach forms — now deprecated)
**Exploration:** deep-analysis
**Depends on:** D1 (block), D3 (if/else), D4 (match)
**Status:** revised — explicitly documented deprecation of issue-386 structural loop.steps and forEach.steps forms

## D6: ~~`when` reserved for reactive semantics~~ — RETIRED

**Status:** retired — absorbed into D2. The keyword reservation was speculative (no reactive model design exists) and redundant with D2's rename. D2 now documents the imperative/reactive vocabulary split (step-layer `if:` vs engine-layer `when:`) and the migration scope including YamlImport. Future reactive constructs should choose vocabulary through their own design process, unconstrained by premature reservation.
**Exploration:** quick
**Depends on:** D2
