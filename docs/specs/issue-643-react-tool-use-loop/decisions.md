# Decisions — issue-643-react-tool-use-loop

## D1: LangChain4j types as lingua franca for tool definitions

**Choice:** Use LangChain4j's `ToolSpecification` for tool definitions. Platform adds `Tool` (binding record: spec + executor) and `ToolExecutor` (functional interface) — the execution contract LangChain4j doesn't provide.
**Alternatives:**
- Platform-owned `ToolDefinition` replacing `ToolSpecification` — avoids LangChain4j dep for consumers, but creates split brain since ChatModel is already LangChain4j and all backends map to its types
- Vendor-neutral platform types mapping to each SDK — over-engineering; LangChain4j already fills this role
**Rationale:** Consumers already depend on LangChain4j transitively via ChatModel. Adding a parallel type system for tool definitions fragments the vocabulary. Platform's value-add is the binding (Tool record), execution contract (ToolExecutor), and the loop itself — not re-wrapping types LangChain4j already standardises.
**Trade-offs:** Consumers import LangChain4j tool types directly. If LangChain4j changes ToolSpecification, platform's API surface is affected.
**Sources:** platform agent-router-core RoutingChatModel, engine-support-core ReActWorkerFunctionHandler, epic #635 principle
**Exploration:** quick
**Status:** captured

## D2: Blocking loop, caller owns the thread

**Choice:** `ToolUseLoop.run()` is a plain blocking method. Caller controls threading. Loop checks `Thread.interrupted()` each cycle for cancellation.
**Alternatives:**
- Loop owns an executor, returns CompletableFuture — built-in async, but couples concurrency model and requires lifecycle management (@PreDestroy shutdown)
**Rationale:** Matches platform convention (@RunOnVirtualThread on REST, PolicyEnforcer uses virtual threads externally). Simpler, composable — engine wraps in its own executor as it does today.
**Trade-offs:** Caller must manage threading. No built-in timeout — caller uses PolicyEnforcer or executor-level timeout.
**Sources:** engine ReActWorkerFunctionHandler (virtual thread executor), platform governance PolicyEnforcer pattern
**Exploration:** quick
**Status:** captured

## D3: Callback interface for per-cycle observability

**Choice:** Define `ToolUseListener` functional interface with `void onCycle(ToolUseCycleEvent event)`. Loop calls it after each cycle. No-op default.
**Alternatives:**
- CDI event built into the loop — violates framework-neutral rule for agent-react-core
- Return-only, no per-cycle hooks — simpler but loses real-time cycle visibility
**Rationale:** Keeps module framework-neutral. Engine wires to EventDispatcher, Quarkus modules to CDI events, Spring to ApplicationEventPublisher.
**Trade-offs:** Caller must implement the wiring. Listener exceptions need a policy (swallow + log, or propagate and abort the loop).
**Sources:** engine ReActCycleEvent/ReActCycleEventHandler pattern, platform-core Consumer<T> callback convention
**Exploration:** quick
**Status:** captured

## D5: Single loop for both frameworks, no Spring AI adapter

**Choice:** `ToolUseLoop` is framework-neutral Java — both Quarkus and Spring paths use it directly. No adapter to Spring AI's `ToolCallingAdvisor`. No framework-specific loop variants.
**Alternatives:**
- Use LangChain4j AiServices — requires adding full `langchain4j` artifact (platform uses only `langchain4j-core`), proxy-heavy, no per-cycle hooks, no structured results
- Split: ToolUseLoop for Quarkus, ToolCallingAdvisor for Spring — two loop implementations consuming the same tool types, maintenance burden, potential behavioral divergence
- Bridge platform Tool → Spring AI ToolCallback — premature; no Spring consumer needs it today
**Rationale:** One loop, one set of types, both frameworks. ToolUseLoop depends only on langchain4j-core (already in dependency tree). Spring AI adapter can be added later if a consumer needs advisor chain integration (memory, RAG). YAGNI until then.
**Trade-offs:** Spring consumers don't get Spring AI advisor chain features (ChatMemory, retrieval augmentation) via the tool loop. They can still use those features independently.
**Sources:** LangChain4j artifact split (AiServices in `langchain4j`, not `langchain4j-core`), Spring AI ToolCallingAdvisor API, platform dual-framework convention
**Exploration:** deep-analysis
**Status:** captured

## D4: Error-as-result, loop continues for self-correction

**Choice:** When a tool executor throws or the LLM calls a non-existent tool, wrap the error as a tool result message and continue. The LLM sees the error and can self-correct.
**Alternatives:**
- Fail-fast on errors — simpler but loses ReAct's self-correction capability
**Rationale:** Self-correction is the defining advantage of the ReAct pattern. Engine already does this successfully. Errors are recorded in the audit trail for full visibility.
**Trade-offs:** A broken tool can burn cycles with repeated failed calls. maxCycles cap limits the damage.
**Sources:** engine ReActWorkerFunctionHandler hallucinated-tool-name handling, ReAct paper pattern
**Exploration:** quick
**Status:** captured