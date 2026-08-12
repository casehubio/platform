# Multi-Agent Runtime Support — Design Spec

**Date:** 2026-08-12
**Issue:** casehubio/devtown#186
**Branch:** `issue-186-multi-agent-runtime`
**Status:** Draft

---

## Context

Gas City competitive analysis v6 identifies agent ecosystem breadth as a competitive gap. Gas City supports 9 CLI agents and 7 runtime providers. CaseHub primarily supports Claude (`ClaudeAgentProvider` + `ClaudeAgentSession`) with LangChain4j bridge for model-agnostic access.

CaseHub's approach is architecturally sound (SPI-based, CDI displacement, session management, rate-limiting decorator) but has a single native implementation. This spec adds native providers for OpenAI and Google (both API and CLI), a routing layer for multi-provider dispatch, and a runtime abstraction for pluggable execution environments.

### Research: native SDK advantages

The existing `ClaudeAgentProvider` bypasses LangChain4j because the Claude CLI manages `cache_control` breakpoints transparently — a capability LangChain4j can't express. The same analysis for OpenAI and Google:

**OpenAI:** Prompt caching is automatic (90% discount, no code changes). LangChain4j benefits passively. However, GPT-5.6+ adds `prompt_cache_key` (routing hint for cache affinity) and `prompt_cache_retention` (24h extended TTL) — parameters LangChain4j doesn't expose. A native API provider enables these.

**Google Gemini:** Implicit caching is automatic. Explicit caching via `cachedContent` provides controlled cache lifecycle (create, reference, TTL). LangChain4j is adding support (PR #5300). However, Gemini's API **forbids passing tools or system_instruction when using `cachedContent`** — a fundamental incompatibility with LangChain4j's standard tool binding. A native API provider can structure requests to work around this.

**CLI agents:** Both Codex CLI and Gemini CLI are full agentic tools (ReAct loops, tool use, MCP, subagents, sandboxing). Wrapping them as subprocess-based providers gives CaseHub agentic coding capabilities beyond API-level inference.

---

## Architecture

```
Caller
  │
  @Inject AgentProvider
  │
  ▼
GatedAgentProvider (@Decorator — rate limiting)
  │
  ▼
RoutingAgentProvider (agent-router/)
  │  reads config.model() → provider key
  │
  ├──► AgentBackend "claude"      (agent-claude/)
  ├──► AgentBackend "openai"      (agent-openai/)
  ├──► AgentBackend "codex"       (agent-codex/)       ──► AgentRuntime
  ├──► AgentBackend "gemini"      (agent-gemini/)
  ├──► AgentBackend "gemini-cli"  (agent-gemini-cli/)   ──► AgentRuntime
  └──► AgentBackend "langchain4j" (agent-langchain4j/)  ← catch-all fallback
                                                          │
                                                    AgentRuntime (agent-runtime/)
                                                      └── SubprocessRuntime
```

### Key invariants

- Callers always inject `AgentProvider` — never `AgentBackend` or a concrete provider
- `GatedAgentProvider` (@Decorator) wraps the router, rate-limiting all backends uniformly
- `model` field on config records is nullable — null uses the configurable default backend
- LangChain4j is the catch-all: any key with no native match falls through to it
- No downstream CDI errors in any deployment scenario — `Instance<AgentBackend>` is always resolvable (empty collection, not missing bean)
- `AgentRuntime` is only injected by CLI providers that transitively pull `agent-runtime/`

---

## SPI Changes in `agent-api/`

### `AgentBackend` — new interface

```java
package io.casehub.platform.agent;

import io.smallrye.mutiny.Multi;

public interface AgentBackend {

    String key();

    Multi<AgentEvent> invoke(AgentSessionConfig config);

    AgentSession openSession(AgentSessionInit init);
}
```

Same method signatures as `AgentProvider` but a separate type. `AgentProvider` is the caller-facing SPI. `AgentBackend` is the implementor-facing SPI. The router bridges them.

### `AgentSessionConfig` — gains nullable `model` field

```java
public record AgentSessionConfig(
        String systemPrompt,
        String userPrompt,
        List<AgentMcpServer> mcpServers,
        Duration timeout,
        String correlationId,
        String model
) {
    public AgentSessionConfig {
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        Objects.requireNonNull(userPrompt, "userPrompt");
        mcpServers = mcpServers != null ? List.copyOf(mcpServers) : List.of();
    }

    public static AgentSessionConfig of(String systemPrompt, String userPrompt) {
        return new AgentSessionConfig(systemPrompt, userPrompt, List.of(), null, null, null);
    }

    public static AgentSessionConfig of(String systemPrompt, String userPrompt,
                                        Duration timeout) {
        return new AgentSessionConfig(systemPrompt, userPrompt, List.of(), timeout, null, null);
    }

    public static AgentSessionConfig of(String systemPrompt, String userPrompt,
                                        String model) {
        return new AgentSessionConfig(systemPrompt, userPrompt, List.of(), null, null, model);
    }
}
```

### `AgentSessionInit` — same addition

```java
public record AgentSessionInit(
        String systemPrompt,
        List<AgentMcpServer> mcpServers,
        Duration timeout,
        String correlationId,
        String model
) {
    public AgentSessionInit {
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        mcpServers = mcpServers != null ? List.copyOf(mcpServers) : List.of();
    }

    public static AgentSessionInit of(String systemPrompt) {
        return new AgentSessionInit(systemPrompt, List.of(), null, null, null);
    }

    public static AgentSessionInit of(String systemPrompt, String model) {
        return new AgentSessionInit(systemPrompt, List.of(), null, null, model);
    }
}
```

### `AgentRuntime` + `AgentProcess` — new interfaces

```java
package io.casehub.platform.agent;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface AgentRuntime {
    AgentProcess spawn(AgentRuntimeConfig config);
}

public record AgentRuntimeConfig(
        String command,
        List<String> args,
        Map<String, String> env,
        Path workingDirectory
) {
    public AgentRuntimeConfig {
        Objects.requireNonNull(command, "command");
        args = args != null ? List.copyOf(args) : List.of();
        env = env != null ? Map.copyOf(env) : Map.of();
    }
}

public interface AgentProcess extends AutoCloseable {
    OutputStream stdin();
    InputStream stdout();
    InputStream stderr();
    CompletableFuture<Integer> exitCode();
    void destroy();
    void destroyForcibly();
}
```

All new types are pure Java + Mutiny. No Quarkus, no CDI annotations. Consistent with `agent-api/`'s zero-dependency constraint.

---

## Router Module — `agent-router/`

Artifact: `casehub-platform-agent-router`

### `RoutingAgentProvider`

```java
@ApplicationScoped
public class RoutingAgentProvider implements AgentProvider {

    private final Map<String, AgentBackend> backends;
    private final AgentBackend defaultBackend;

    @Inject
    RoutingAgentProvider(@Any Instance<AgentBackend> backends,
                         RoutingAgentProperties properties) {
        this.backends = new HashMap<>();
        AgentBackend fallback = null;
        for (AgentBackend backend : backends) {
            this.backends.put(backend.key(), backend);
            if (backend.key().equals(properties.defaultBackend())) {
                fallback = backend;
            }
        }
        this.defaultBackend = fallback;
    }

    @Override
    public Multi<AgentEvent> invoke(AgentSessionConfig config) {
        return resolve(config.model()).invoke(config);
    }

    @Override
    public AgentSession openSession(AgentSessionInit init) {
        return resolve(init.model()).openSession(init);
    }

    private AgentBackend resolve(String model) {
        if (model == null) {
            if (defaultBackend == null)
                throw new IllegalStateException("No default backend configured");
            return defaultBackend;
        }
        AgentBackend backend = backends.get(model);
        if (backend != null) return backend;
        AgentBackend catchAll = backends.get("langchain4j");
        if (catchAll != null) return catchAll;
        throw new IllegalArgumentException("No backend for key: " + model);
    }
}
```

### `RoutingAgentProperties`

```java
@ConfigMapping(prefix = "casehub.platform.agent")
public interface RoutingAgentProperties {
    @WithDefault("claude")
    String defaultBackend();
}
```

### CDI tier changes

| Before | After |
|--------|-------|
| NoOpAgentProvider @DefaultBean | Unchanged — active without agent-router/ |
| ClaudeAgentProvider @Alternative @Priority(10) implements AgentProvider | @ApplicationScoped implements AgentBackend |
| ChatModelAgentProvider @Alternative @Priority(1) implements AgentProvider | @ApplicationScoped implements AgentBackend |
| GatedAgentProvider @Decorator on AgentProvider | Unchanged — wraps router |
| — | RoutingAgentProvider @ApplicationScoped implements AgentProvider (new) |

Dependencies: `agent-api/`, `quarkus-arc`.

`agent-router/` is a transitive compile dependency of every `agent-*` provider module. Adding any provider to the classpath automatically brings the router.

---

## Runtime Module — `agent-runtime/`

Artifact: `casehub-platform-agent-runtime`

### `SubprocessRuntime`

```java
@ApplicationScoped
public class SubprocessRuntime implements AgentRuntime {

    @Override
    public AgentProcess spawn(AgentRuntimeConfig config) {
        ProcessBuilder pb = new ProcessBuilder()
            .command(buildCommand(config.command(), config.args()))
            .directory(config.workingDirectory() != null
                ? config.workingDirectory().toFile() : null);
        pb.environment().putAll(config.env());
        Process process = pb.start();
        return new LocalAgentProcess(process);
    }

    private static List<String> buildCommand(String command, List<String> args) {
        List<String> cmd = new ArrayList<>();
        cmd.add(command);
        cmd.addAll(args);
        return cmd;
    }
}
```

### `LocalAgentProcess`

```java
class LocalAgentProcess implements AgentProcess {
    private final Process process;

    LocalAgentProcess(Process process) {
        this.process = process;
    }

    @Override public OutputStream stdin()  { return process.getOutputStream(); }
    @Override public InputStream stdout()  { return process.getInputStream(); }
    @Override public InputStream stderr()  { return process.getErrorStream(); }
    @Override public CompletableFuture<Integer> exitCode() {
        return process.onExit().thenApply(Process::exitValue);
    }
    @Override public void destroy()         { process.destroy(); }
    @Override public void destroyForcibly() { process.destroyForcibly(); }
    @Override public void close()           { destroy(); }
}
```

No `NoOpAgentRuntime` in `platform/`. `AgentRuntime` is only injected by CLI providers that transitively pull this module. CDI enforces the contract — missing runtime = deployment failure (correct signal).

Dependencies: `agent-api/`, `quarkus-arc`.

Future runtimes (not in this epic): `agent-runtime-k8s/` (KubernetesRuntime), `agent-runtime-container/` (Docker/Podman).

---

## Provider Modules

### Refactored: `agent-claude/`

`ClaudeAgentProvider` changes:
- `implements AgentProvider` → `implements AgentBackend`
- Adds `@Override public String key() { return "claude"; }`
- Removes `@Alternative @Priority(10)`
- Remains `@ApplicationScoped`

Does NOT adopt `AgentRuntime` — `claude-code-sdk` manages its own subprocess. Acknowledged exception.

Compile dependency on `agent-router/` added. All existing tests adapted (minimal — test the interface change, not behavior).

### Refactored: `agent-langchain4j/`

`ChatModelAgentProvider` changes:
- `implements AgentProvider` → `implements AgentBackend`
- Adds `@Override public String key() { return "langchain4j"; }`
- Removes `@Alternative @Priority(1)`
- Graceful deactivation when no ChatModel exists — unchanged

`AgentProviderChatModel` (AgentProvider → ChatModel) injects `AgentProvider` — gets the router. No changes.

### New: `agent-openai/`

Artifact: `casehub-platform-agent-openai`
Provider key: `"openai"`
Type: API (OpenAI Java SDK)

```java
@ApplicationScoped
public class OpenAiAgentBackend implements AgentBackend {
    @Override public String key() { return "openai"; }
    // Chat Completions API with prompt_cache_key + prompt_cache_retention
    // Streaming response → AgentEvent mapping (TextDelta, ThinkingDelta, ToolCallComplete)
}
```

Config prefix: `casehub.platform.agent.openai`
Properties: `api-key`, `default-model` (default: gpt-5.5), `prompt-cache-retention` (default: in_memory), `default-timeout` (default: PT5M), `max-concurrent-sessions`

Dependencies: `agent-api/`, `agent-router/` (transitive), `com.openai:openai-java`, `quarkus-arc`.

### New: `agent-codex/`

Artifact: `casehub-platform-agent-codex`
Provider key: `"codex"`
Type: CLI subprocess

```java
@ApplicationScoped
public class CodexAgentBackend implements AgentBackend {
    @Inject AgentRuntime runtime;
    @Override public String key() { return "codex"; }
    // Spawns 'codex' CLI via AgentRuntime
    // Parses JSON-line output → AgentEvent stream
    // Timeout via ScheduledExecutorService (same pattern as ClaudeAgentClient)
    // Semaphore-gated concurrent sessions
}
```

Config prefix: `casehub.platform.agent.codex`
Properties: `binary-path` (default: codex), `default-timeout` (default: PT5M), `max-concurrent-sessions` (default: 4)

Dependencies: `agent-api/`, `agent-router/` (transitive), `agent-runtime/` (transitive), `quarkus-arc`.

### New: `agent-gemini/`

Artifact: `casehub-platform-agent-gemini`
Provider key: `"gemini"`
Type: API (Google GenAI SDK)

```java
@ApplicationScoped
public class GeminiAgentBackend implements AgentBackend {
    @Override public String key() { return "gemini"; }
    // Explicit caching: creates cachedContent, references in generateContent
    // Works around tools+caching incompatibility by separating cached context
    //   from tool declarations in request structure
    // Streaming response → AgentEvent mapping
}
```

Config prefix: `casehub.platform.agent.gemini`
Properties: `api-key`, `default-model` (default: gemini-3.6-flash), `cache-ttl` (default: PT1H), `default-timeout` (default: PT5M), `max-concurrent-sessions`

Dependencies: `agent-api/`, `agent-router/` (transitive), `com.google.genai:google-genai`, `quarkus-arc`.

### New: `agent-gemini-cli/`

Artifact: `casehub-platform-agent-gemini-cli`
Provider key: `"gemini-cli"`
Type: CLI subprocess

```java
@ApplicationScoped
public class GeminiCliAgentBackend implements AgentBackend {
    @Inject AgentRuntime runtime;
    @Override public String key() { return "gemini-cli"; }
    // Spawns 'gemini' CLI via AgentRuntime
    // Same subprocess management pattern as agent-codex/
}
```

Config prefix: `casehub.platform.agent.gemini-cli`
Properties: `binary-path` (default: gemini), `default-timeout` (default: PT5M), `max-concurrent-sessions` (default: 4)

Dependencies: `agent-api/`, `agent-router/` (transitive), `agent-runtime/` (transitive), `quarkus-arc`.

### Module summary

| Module | Key | Type | SDK | Uses AgentRuntime |
|--------|-----|------|-----|-------------------|
| agent-claude/ | claude | CLI | claude-code-sdk | No (SDK owns subprocess) |
| agent-openai/ | openai | API | openai-java | No |
| agent-codex/ | codex | CLI | none (subprocess) | Yes |
| agent-gemini/ | gemini | API | google-genai | No |
| agent-gemini-cli/ | gemini-cli | CLI | none (subprocess) | Yes |
| agent-langchain4j/ | langchain4j | API | langchain4j-core | No |

---

## ACP Evaluation

The issue asks: "Evaluate whether an ACP-equivalent is needed or whether MCP + Qhorus covers the same ground."

Gas City's Agent Client Protocol is JSON-RPC over stdio for agent-to-orchestrator communication.

| Concern | ACP | CaseHub |
|---------|-----|---------|
| Agent → tool invocation | JSON-RPC tool calls | MCP (industry standard, all three CLIs support it) |
| Agent → orchestrator status | Bead lifecycle events | Qhorus speech acts (9 types: COMMIT, DONE, REJECT, ESCALATE, CHECKPOINT, etc.) |
| Orchestrator → agent dispatch | Formula execution | CasePlanModel bindings + WorkerProvisioner |
| Agent identity | Role name in pack config | Eidos 4-layer descriptors |
| Session management | Agent process lifecycle | AgentSession state machine + AgentRuntime SPI |

**Conclusion:** ACP is not needed. MCP covers tool calling more richly. Qhorus covers accountability far more deeply. The new `AgentRuntime` SPI addresses the process-lifecycle concern. No implementation work.

---

## Impact on Existing Modules

### `platform/`

- `NoOpAgentProvider @DefaultBean` — unchanged. Active only without `agent-router/`.
- Log message updated to mention the router.
- No new NoOp beans.

### `agent-gate/`

No changes. `@Decorator` on `AgentProvider` wraps the router transparently.

### Root POM

New `<module>` entries:
```xml
<module>agent-runtime</module>
<module>agent-router</module>
<module>agent-openai</module>
<module>agent-codex</module>
<module>agent-gemini</module>
<module>agent-gemini-cli</module>
```

### Parent BOM (casehub-parent)

Six new `<dependency>` entries in `<dependencyManagement>`.

### Downstream consumers

- Apps injecting `AgentProvider` — **no changes**. They get the router.
- Apps wanting multi-provider — add multiple `agent-*` modules, set `casehub.platform.agent.default-backend`, pass `model("codex")` in config.
- No downstream CDI errors in any scenario. `Instance<AgentBackend>` is always resolvable.

### CDI resolution by deployment

| Deployment | AgentProvider resolves to | AgentRuntime | CDI errors? |
|---|---|---|---|
| No agent modules | NoOpAgentProvider @DefaultBean | Not injected | No |
| agent-claude/ | RoutingAgentProvider | Not injected | No |
| agent-openai/ | RoutingAgentProvider | Not injected | No |
| agent-codex/ | RoutingAgentProvider | SubprocessRuntime | No |
| Multiple providers | RoutingAgentProvider (all backends) | Where CLI providers need it | No |

---

## Testing Strategy

| Module | Test type | Covers |
|---|---|---|
| agent-api/ | Plain JUnit 5 | AgentBackend, AgentRuntime, AgentProcess contracts; config records with model field |
| agent-runtime/ | Plain JUnit 5 | SubprocessRuntime spawns process, LocalAgentProcess wraps I/O, destroy, exitCode |
| agent-router/ | Quarkus @QuarkusTest | Router discovers backends, routes by key, default backend, catch-all, unknown key |
| agent-claude/ | Plain JUnit 5 + IT | Refactor to AgentBackend. Existing tests adapted. IT requires Claude CLI |
| agent-langchain4j/ | Quarkus @QuarkusTest | Refactor to AgentBackend. Catch-all behavior. Existing tests adapted |
| agent-openai/ | Plain JUnit 5 + IT | API construction, cache key wiring, response→AgentEvent mapping. IT requires OPENAI_API_KEY |
| agent-codex/ | Plain JUnit 5 + IT | CLI args, subprocess I/O→AgentEvent mapping, timeout, semaphore. IT requires `codex` binary |
| agent-gemini/ | Plain JUnit 5 + IT | Cache lifecycle, cachedContent wiring, response→AgentEvent. IT requires GEMINI_API_KEY |
| agent-gemini-cli/ | Plain JUnit 5 + IT | CLI args, subprocess I/O→AgentEvent mapping. IT requires `gemini` binary |

Test constructor pattern: same as `ClaudeAgentClient` — test constructor accepts stream factory, bypasses real subprocess/API. IT tests gated by `@EnabledIfEnvironmentVariable`.

---

## Maven Build Order

```xml
<module>platform-api</module>
<module>agent-api</module>
<module>platform</module>
<module>testing</module>
<module>agent-runtime</module>
<!-- existing modules... -->
<module>agent-claude</module>
<module>agent-openai</module>
<module>agent-codex</module>
<module>agent-gemini</module>
<module>agent-gemini-cli</module>
<module>agent-langchain4j</module>
<module>agent-router</module>
<module>agent-gate</module>
```

---

## Out of Scope

- **Runtime implementations beyond SubprocessRuntime** — K8s, container, remote HTTP runtimes are future work. The `AgentRuntime` SPI supports them.
- **ACP implementation** — evaluated and concluded not needed (MCP + Qhorus covers it).
- **Per-backend rate limiting** — `agent-gate/` applies uniformly via the router. Per-backend gates can be added later by extending the decorator.
- **Model variant selection** — callers specify provider key, not model ID. Per-provider default model is configurable. Fine-grained model selection deferred.

---

## References

- casehubio/devtown#186 — originating issue
- `docs/gastown-casehub-analysis-v6.md` §7.4 — agent ecosystem breadth gap
- `docs/specs/2026-06-02-agent-module-design.md` — original AgentProvider design
- `docs/specs/2026-06-15-agent-session-multi-turn-design.md` — AgentSession design
- `docs/specs/2026-06-26-agent-langchain4j-interop-design.md` — LangChain4j interop
- `docs/specs/2026-06-29-claude-messages-api-design.md` — messages() API upgrade
- OpenAI prompt caching: https://developers.openai.com/api/docs/guides/prompt-caching
- Gemini context caching: https://ai.google.dev/gemini-api/docs/caching
- Codex CLI: https://github.com/openai/codex
- Gemini CLI: https://github.com/google-gemini/gemini-cli
