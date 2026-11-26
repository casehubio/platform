# CaseHub vs LangChain4j: Agent Invocation Architecture

**Date:** 2026-11-26
**Context:** @AgentService design (#984) — understanding the strategic position

---

## Two approaches to the same problem

Both CaseHub and LangChain4j solve "Java developer wants to call an LLM." They take fundamentally different approaches, and those differences matter more now than they did in 2024.

### LangChain4j: Normalisation layer

LangChain4j's core value proposition was **vendor normalisation**. In 2023-2024, vendor Java SDKs were immature or absent. LangChain4j gave Java developers a usable API when vendors only had Python SDKs, and a consistent abstraction across inconsistent APIs.

The architecture reflects this: `ChatModel.chat(ChatRequest) → ChatResponse` is a stateless request/response contract. Tool use, memory, structured output, and guardrails are layered on top via `AiServices` — a proxy that wraps `ChatModel` with additional behavior.

**What it normalises well:** Basic LLM invocation, tool use protocol, structured output, provider switching.

**What it can't normalise:** Provider-specific features that don't fit `ChatRequest → ChatResponse`. Prompt caching, real sessions, provider-specific optimisation modes — these arrive as bolted-on config flags (`cacheSystemMessages`, `cacheAutomatically`, `cacheTools`) and special message attributes that break the promise of vendor-neutrality.

### CaseHub: Direct SDK with routing

CaseHub uses vendor SDKs directly — `AnthropicNativeChatModel` calls the Anthropic Java SDK, `OpenAiNativeChatModel` calls the OpenAI Java SDK, `GeminiNativeChatModel` calls the Google GenAI SDK. No middleware layer between the platform and the provider.

The abstraction is at the **invocation level**, not the vendor level:
- `AgentProvider` — unified invocation SPI (`invoke()` streaming, `openSession()` multi-turn)
- `RoutingAgentProvider` — manifest-driven model resolution (alias → chain → backend)
- `AgentBackend` — vendor-specific implementations with full SDK access

Each backend leverages its full SDK capabilities. When Anthropic adds a caching mode, the backend can adopt it immediately — no waiting for a middleware layer to expose it.

---

## How the market changed

### 2023-2024: Normalisation was valuable

- Vendor SDKs: immature, inconsistent, or Java-absent
- LangChain4j's value: real and significant — unified Java API across providers
- Native SDK approach: harder to justify, fewer providers with Java SDKs
- Winner: LangChain4j for velocity

### 2025-2026: SDKs converged, normalisation became a tax

- Anthropic, OpenAI, Google all ship mature Java SDKs with streaming, tool use, structured output, caching
- SDKs converged on similar patterns — the differences that needed normalising shrank
- Provider-specific features grew — caching modes, real sessions, breakpoints, context windows
- LangChain4j's abstraction became a barrier to these features rather than a bridge over missing ones
- Winner: direct SDK for platforms that need full provider capabilities

The structural problem: LangChain4j's `ChatModel` interface was designed around stateless request/response. Features like true sessions, streaming with backpressure, and provider-specific optimisations fight the abstraction. Adding caching to a normalisation layer designed before caching existed produces special attributes and config flags, not a natural API.

---

## Session and caching: the clearest divergence

### LangChain4j: Stateless replay

LangChain4j's `AiServices` emulates multi-turn via `ChatMemory` — an in-memory list of messages replayed in full on every API call. The LLM sees the same conversation each time, but every call pays the full token cost of the history.

Caching was added after the fact:
- Anthropic: `cacheSystemMessages`, `cacheTools`, `cacheAutomatically`, per-message `cache_control`, configurable TTL — all opt-in booleans and attributes
- OpenAI: `OpenAiPromptCacheBreakpoint`, explicit/implicit modes, configurable TTL

These are well-implemented features, but they're bolted onto a stateless architecture. The replay still happens; caching reduces the cost of replaying the prefix.

### CaseHub: Two native models

**Claude CLI subprocess** — true stateful session. `ClaudeAgentSession` holds a live process. First turn: `connect(prompt)`, subsequent turns: `query(prompt)`. The LLM holds full conversational context server-side. No replay, no caching needed — the state persists in the process.

**Anthropic HTTP API** — replay with explicit caching. `AnthropicNativeChatModel` applies `CacheControlEphemeral` on system message blocks and tool definitions via the Anthropic Java SDK directly. The expensive preamble is cached server-side.

**Gap:** CaseHub currently exposes fewer caching knobs than LangChain4j (no auto-caching, no per-message control, no configurable TTL). This is a wiring gap, not an architectural one — the SDK supports all these features, they just haven't been mapped through the config layer yet. Filed as #988.

---

## AiServices vs @AgentService

LangChain4j's `AiServices` and CaseHub's `@AgentService` solve the same user problem: turn a Java interface into an LLM-backed implementation. The design differences flow from the architectural differences above.

### What AiServices does

```java
interface Assistant {
    @SystemMessage("You are a helpful assistant")
    String chat(String userMessage);
}

Assistant assistant = AiServices.builder(Assistant.class)
    .chatLanguageModel(model)
    .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
    .tools(new Calculator())
    .build();
```

Builder-based, runtime proxy. `ChatModel` is passed explicitly. Memory is an in-memory message list. Tools are `@Tool`-annotated Java methods. Guardrails are coupled to the AiServices proxy — bypass the proxy and you bypass the guardrails.

### What @AgentService does

```java
@AgentService(model = "fast", descriptor = "coach", tools = {Calculator.class})
public interface PlanningCoach {
    @Query("Break this goal into concrete tasks")
    List<Task> decompose(String goal);

    Multi<AgentEvent> stream(String prompt);

    AgentSession openConversation();
}
```

Annotation-based, compile-time proxy (APT). Model resolved via manifest alias — YAML, not code. Personality resolved via `AgentResolver` SPI — separates contract from persona. Tools declared on the interface — the declaration is the contract. Three return modes: blocking (structured output), streaming (`Multi<AgentEvent>`), session (`AgentSession` for multi-turn with real session infrastructure).

### Comparison

| Dimension | AiServices | @AgentService |
|-----------|-----------|---------------|
| Generation | Runtime proxy (builder) | Compile-time proxy (APT) |
| Model binding | `ChatModel` instance in builder code | Manifest alias — YAML, not code |
| Personality | `@SystemMessage` hardcoded annotation | `AgentResolver` SPI: descriptor → system prompt, swappable |
| Session | `ChatMemory` (in-memory replay) | `SessionPool` — real sessions (subprocess) or cached replay |
| Tools | Builder: `.tools(new Calculator())` | Annotation: `tools = {Calculator.class}` — part of the contract |
| Streaming | Not supported on AiServices | `Multi<AgentEvent>` return type |
| Multi-turn | Implicit via ChatMemory replay | Explicit via `AgentSession` return type |
| Guardrails | Coupled to AiServices proxy only | CDI `@Decorator` on `AgentProvider` — fires on all paths |
| Framework | Java only | Core + Quarkus + Spring (three-tier) |
| Caching | Per-call config flags (opt-in) | Provider-native: subprocess sessions or SDK-level cache control |
| Budget | None | Per-invocation cost tracking, telemetry |
| Routing | None — one model | Alias → chain → tier → fallback |

### Where AiServices is better

- **Larger ecosystem.** More examples, more community answers, more tutorials. This is community size, not technical merit — it reflects LangChain4j's head start and broader adoption, not a capability gap.

AiServices previously had two additional advantages that are being eliminated:
- **Simpler getting-started** — the builder pattern required no annotation processing or manifest YAML. With `@AgentService`, the annotation is less ceremony than the builder: one annotation vs explicit model/memory/tools wiring in code. The manifest YAML is project-level configuration, not per-interface overhead.
- **More caching knobs** — LangChain4j exposed more SDK caching features through their wrapper. Since CaseHub uses the same SDKs directly, this was a wiring gap, not an architectural one. #988 wires the remaining caching features through the platform's config layer.

### Where @AgentService is better

- **Declarative and inspectable.** The interface declaration tells you everything — model, descriptor, tools, return type. No runtime builder state to trace.
- **Real sessions.** Claude CLI subprocess holds context server-side. No replay, no caching workaround. Genuinely cheaper and faster for multi-turn.
- **Guardrails can't be bypassed.** CDI decorator on `AgentProvider` fires on all invocation paths — `@AgentService`, playbooks, direct calls. AiServices guardrails only fire through the AiServices proxy.
- **Framework-neutral.** One APT processor generates code that works with Quarkus CDI and Spring auto-configuration. AiServices is `quarkus-langchain4j` only (the Spring integration is a separate, less mature project).
- **Separation of contract and personality.** Same interface, different descriptors, different behaviour. AiServices bakes the system prompt into code via `@SystemMessage`.
- **Full return type spectrum.** Blocking, streaming, and session from the same interface. AiServices is blocking only.

---

## The strategic position

CaseHub doesn't compete with LangChain4j on normalisation — that's a problem the market has largely outgrown as vendor SDKs matured. CaseHub competes on **platform infrastructure**: routing, sessions, guardrails, budget tracking, simulation, and the ability to leverage each provider's full SDK without an abstraction barrier in the way.

`@AgentService` is the declarative surface on that infrastructure. It's not "CaseHub's version of AiServices" — it's a type-safe entry point into a richer runtime that AiServices can't reach because `ChatModel.chat()` is the wrong abstraction for sessions, streaming, and cross-cutting guardrails.

The honest risk: if LangChain4j evolves `ChatModel` to support sessions natively (beyond ChatMemory replay), the gap narrows. But their normalisation architecture makes that structurally hard — real sessions are inherently provider-specific, and normalising them would require either exposing provider differences (breaking the abstraction) or emulating them (which is what ChatMemory already does).
