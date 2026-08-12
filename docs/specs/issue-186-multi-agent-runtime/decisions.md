# Decisions — issue-186-multi-agent-runtime

## D1: Multi-provider selection mechanism

**Choice:** Single-injection router with model field on config
**Alternatives:**
- Registry pattern (AgentProviderRegistry.resolve("claude")) — adds a new SPI and lookup pattern; callers must change injection
- CDI qualifier (@AgentBackend("openai")) — static wiring at CDI resolution time; can't route at runtime
**Rationale:** Least disruptive — callers keep `@Inject AgentProvider`. A router implementation dispatches based on `AgentSessionConfig.model`. Maps naturally to engine routing (trust/semantic/LLM routing selects agent → agent has model preference).
**Trade-offs:** Router adds one layer of indirection. Model string must be unambiguous across providers.
**Exploration:** quick
**Status:** captured

## D2: Each provider in its own module

**Choice:** Separate Maven module per provider (e.g. agent-openai/, agent-codex/, agent-gemini/, agent-gemini-cli/)
**Alternatives:**
- Bundled module per vendor (agent-openai/ contains both API and CLI) — fewer modules but bloats classpath with unused deps
- Single multi-provider module — defeats the classpath-presence activation pattern
**Rationale:** User direction: "make sure each one gets its own module so we don't bloat classpaths." Consistent with existing agent-claude/ pattern. LangChain4j is the fallback; native providers are removable.
**Trade-offs:** More Maven modules to maintain. Acceptable given they're thin.
**Exploration:** quick
**Status:** captured

## D3: Provider type — API and CLI

**Choice:** Both API-native and CLI subprocess providers for each vendor
**Alternatives:**
- API-only — simpler, good for inference, but no agentic capabilities
- CLI-only — matches agent-claude/ pattern but misses API-level caching control for inference tasks
**Rationale:** Different use cases need different provider types. CLI for agentic tasks (file editing, code analysis). API for inference tasks (query expansion, classification) with native caching control. LangChain4j covers API-level inference as fallback already, so API providers are justified only where caching advantages exist.
**Trade-offs:** Two modules per vendor instead of one. But each is thin and independently deployable.
**Depends on:** D2 (separate modules)
**Exploration:** quick
**Status:** captured

## D4: Model string semantics

**Choice:** Provider key ("claude", "openai", "gemini") with configurable default model per provider
**Alternatives:**
- Model ID ("gpt-5.5", "gemini-3.6-flash") — more granular but couples platform to provider-specific model names that change frequently
**Rationale:** Provider key is stable. Each provider's default model is configurable via Quarkus config (e.g. `casehub.platform.agent.openai.default-model=gpt-5.5`). Keeps model selection as a provider-internal concern.
**Trade-offs:** Caller can't request a specific model variant through the platform SPI — must configure per-provider defaults. Fine for now; can add optional modelId later if needed.
**Depends on:** D1 (router dispatches on this key)
**Exploration:** quick
**Status:** captured

## D5: Scope — runtime abstraction and ACP included

**Choice:** Include runtime provider abstraction and ACP evaluation in this epic
**Alternatives:**
- Defer both until production deployment on K8s — avoids YAGNI risk but leaves competitive gap open longer
**Rationale:** User preference to close the full competitive gap now. Runtime abstraction decouples "which model" from "how to execute" (subprocess, K8s, container). ACP evaluation determines whether MCP + Qhorus covers the same ground or a new protocol is needed.
**Trade-offs:** Larger epic. Runtime abstraction designed before production K8s deployment may need revision. Accepted risk.
**Depends on:** D3 (CLI providers need runtime abstraction most)
**Exploration:** quick
**Status:** captured

## D6: Runtime abstraction boundary

**Choice:** Below the provider — runtime is a strategy injected into CLI providers
**Alternatives:**
- Above the provider — router selects both provider and runtime; runtime wraps entire invocation. More flexible but conflates routing with deployment concerns.
**Rationale:** Compositional. CLI providers delegate subprocess management to an injected AgentRuntime. The runtime handles spawn/connect/close; the provider handles protocol (CLI args, event mapping). New runtimes (K8s, container) added without touching providers.
**Trade-offs:** API providers don't use the runtime abstraction (they make HTTP calls directly). The abstraction only applies to CLI providers. Accepted — this matches the reality that runtime is a subprocess concern.
**Depends on:** D3 (CLI providers need this), D5 (runtime in scope)
**Exploration:** quick
**Status:** captured

## D7: AgentBackend SPI design

**Choice:** New `AgentBackend` interface in agent-api/, separate from `AgentProvider`
**Alternatives:**
- AgentBackend extends AgentProvider — backward-compatible but creates ambiguity in Instance<AgentProvider> (router must filter itself out)
- Default key() method on AgentProvider — simplest but muddies the SPI with dual-purpose interface
**Rationale:** Clean separation of concerns. AgentProvider is for callers, AgentBackend is for implementations. Router is the only AgentProvider bean. Breaking change to ClaudeAgentProvider is internal to this repo and costs nothing (pre-release). Downstream consumers inject AgentProvider, not ClaudeAgentProvider.
**Trade-offs:** Duplicate method signatures between AgentProvider and AgentBackend. Minor — both have invoke() and openSession() with identical contracts.
**Depends on:** D1 (router pattern requires discoverable backends)
**Exploration:** deep-analysis
**Status:** captured

## D8: AgentRuntime SPI design

**Choice:** Process-oriented — AgentRuntime spawns and manages a process lifecycle (stdin/stdout/stderr/exitCode)
**Alternatives:**
- Connection-oriented — AgentRuntime provides a higher-level AgentConnection with send()/interrupt(). Pushes too much into runtime; event mapping is provider-specific, not runtime-specific.
**Rationale:** Keeps responsibilities in the right places. CLI provider knows its protocol (parse output, emit AgentEvents). Runtime knows its environment (spawn process locally, in K8s pod, in container). Clean I/O boundary.
**Trade-offs:** Low-level — CLI providers do more work mapping process I/O to AgentEvents. claude-code-sdk manages its own subprocess internally, so ClaudeAgentProvider may keep its own subprocess path initially while newer providers use AgentRuntime from the start.
**Depends on:** D6 (runtime sits below provider)
**Exploration:** deep-analysis
**Status:** captured
