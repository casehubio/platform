# Security Model for Dynamic Step Invoke Handlers

**Repo:** casehubio/platform
**Depends on:** #433 (dynamic step catalog — trust model, StepExecutionEvent, ProcessExecutor SPI)

## Problem

The dynamic step catalog enables arbitrary code execution via ProcessInvokeHandler, PythonInvokeHandler, and AgentInvokeHandler from YAML-declared step definitions. While step definitions are versioned, developer-reviewed artifacts (not arbitrary user input), the FSI/clinical compliance context requires explicit hardening:

1. ProcessExecutor has no allow-list, resource limits, or sandboxing beyond timeout
2. PythonInvokeHandler bypasses ProcessExecutor entirely — uses raw ProcessBuilder with a hardcoded 30s timeout
3. StepExecutionEvent lacks structured fields for compliance audit trails
4. The trust model is implicit, not documented as an explicit architectural decision

## Scope

**In scope:**
- ProcessCommand gains resource limit fields (memory, filesystem path restrictions) and stdin piping
- ProcessExecutor SPI gains allow-list enforcement (command + args validation)
- PythonInvokeHandler refactored to delegate to ProcessExecutor
- InvokeBinding.Python gains configurable timeout
- StepExecutionEvent enriched with compliance-grade audit fields
- Trust model formalized as architectural documentation

**Out of scope:**
- Container-native ProcessExecutor implementations (nsjail, cgroup adapters) — SPI only
- Audit persistence/query layer — consumers own persistence
- Step definition marketplace or dynamic loading — no untrusted sources exist
- REST URL allow-lists or MCP tool restrictions — different enforcement points

## Design

### 1. ProcessCommand Resource Limits

ProcessCommand gains three new fields via immutable builder methods:

```java
public final class ProcessCommand {
    // Existing: command, workingDir, timeout, mergeStderr

    // New fields:
    private final byte[] stdin;           // piped to process stdin, null = no piping
    private final Long memoryLimitBytes;  // advisory limit, enforced by executor impl
    private final List<String> allowedPaths; // filesystem path restrictions

    public ProcessCommand stdin(byte[] input) { ... }       // defensive copy
    public ProcessCommand memoryLimit(long bytes) { ... }
    public ProcessCommand allowedPaths(List<String> paths) { ... }

    public byte[] stdin() { return stdin != null ? stdin.clone() : null; }
    public Long memoryLimitBytes() { return memoryLimitBytes; }
    public List<String> allowedPaths() { return allowedPaths; }
}
```

**stdin:** PythonInvokeHandler needs this for JSON-over-stdin protocol. ProcessExecutor writes the bytes to the process's stdin and closes the stream before reading output. Defensive copy in constructor and accessor (byte arrays are mutable).

**memoryLimitBytes:** Advisory — DefaultProcessExecutor ignores it (JVM cannot enforce per-process memory limits). Container-native implementations use cgroups. Null = no limit.

**allowedPaths:** Filesystem path restrictions. DefaultProcessExecutor ignores them. Sandboxed implementations use chroot/bind-mount to restrict the filesystem view. Null = unrestricted.

### 2. ProcessExecutor Allow-List

ProcessExecutor gains a pre-execution validation hook. The allow-list is a property of the executor, not the command.

```java
public interface ProcessExecutor {

    ProcessResult execute(String... command);
    ProcessResult execute(ProcessCommand command);
}
```

The interface stays unchanged — the allow-list is an implementation concern. `DefaultProcessExecutor` gains an optional allow-list via constructor:

```java
public class DefaultProcessExecutor implements ProcessExecutor {

    private final List<CommandPattern> allowList; // null = unrestricted

    public DefaultProcessExecutor() { this(null); }
    public DefaultProcessExecutor(List<CommandPattern> allowList) { ... }

    @Override
    public ProcessResult execute(ProcessCommand command) {
        if (allowList != null) {
            validateCommand(command.command(), allowList);
        }
        // ... existing execution logic + stdin piping
    }
}
```

**CommandPattern:** Matches command + args. Three match modes:
- Exact: `["python3", "/opt/scripts/deploy.py"]` — matches this exact command
- Prefix: `["python3", "/opt/scripts/"]` — matches any script under /opt/scripts/
- Glob: `["python3", "/opt/scripts/*.py"]` — matches .py files in /opt/scripts/

```java
public record CommandPattern(List<String> segments, MatchMode mode) {
    public enum MatchMode { EXACT, PREFIX, GLOB }

    public boolean matches(List<String> command) { ... }
}
```

**CommandNotAllowedException** (extends ProcessExecutionException): thrown when a command doesn't match any pattern in the allow-list. Carries the rejected command for audit logging.

### 3. PythonInvokeHandler Delegation

PythonInvokeHandler refactored to delegate to ProcessExecutor:

```java
public class PythonInvokeHandler implements InvokeHandler {

    private final ObjectMapper    objectMapper;
    private final ProcessExecutor processExecutor;  // NEW — replaces raw ProcessBuilder

    // ...

    private StepResult executePython(InvokeBinding.Python python, Map<String, Object> params) {
        try {
            byte[] jsonInput = objectMapper.writeValueAsBytes(params);

            ProcessCommand cmd = ProcessCommand.of("python3", python.script())
                    .stdin(jsonInput)
                    .mergeStderr(false);

            Duration timeout = parseTimeout(python.timeout());
            if (timeout != null) {
                cmd = cmd.timeout(timeout);
            }

            long start = System.nanoTime();
            ProcessResult result = processExecutor.execute(cmd);
            long durationMs = (System.nanoTime() - start) / 1_000_000;

            if (!result.isSuccess()) {
                String error = result.stderr() != null && !result.stderr().isBlank()
                               ? result.stderr().trim()
                               : "Python script exited with code " + result.exitCode();
                return StepResult.failed(error);
            }

            Map<String, Object> output = objectMapper.readValue(
                    result.stdout().trim(), LinkedHashMap.class);
            return StepResult.of(output, Map.of("durationMs", durationMs, "script", python.script()));

        } catch (CommandNotAllowedException e) {
            return StepResult.failed("Python script not allowed: " + e.getMessage());
        } catch (ProcessExecutionException e) {
            return StepResult.failed("Python execution failed: " + e.getMessage());
        } catch (Exception e) {
            return StepResult.failed("Python execution failed: " + e.getMessage());
        }
    }
}
```

**InvokeBinding.Python** gains timeout:

```java
record Python(String script, String timeout) implements InvokeBinding {
    public Python {
        if (script == null)
            throw new IllegalArgumentException("Python binding requires script");
        if (timeout == null) timeout = "30s";
    }
    // Backward-compatible: existing single-arg usage gets 30s default
}
```

### 4. StepExecutionEvent Enrichment

The existing `StepExecutionEvent` (fired by `ValidatingAction`) gains structured audit fields:

```java
public record StepExecutionEvent(
        // Existing fields:
        String stepName,
        Map<String, Object> inputs,
        StepResult result,
        long durationMs,

        // New audit fields (all nullable):
        String bindingType,           // "process", "python", "agent", "mcp", "rest", "graphql"
        String handlerClass,          // handler implementation class name
        String actorId,               // from CurrentPrincipal, null if unavailable
        String tenancyId,             // from CurrentPrincipal, null if unavailable
        String inputHash,             // SHA-256 of serialized inputs for replay detection
        String parentStepName,        // if nested inside block/match/if-else
        String executionEnvironment,  // container ID, node name — from env vars
        String resultClassification   // SUCCESS, FAILURE, TIMEOUT, DENIED, ERROR
) {}
```

**Consumer profiles** are a deployment config, not part of the event:

| Profile | Fields persisted | Use case |
|---------|-----------------|----------|
| MINIMAL | stepName, result, durationMs, resultClassification | Dev, testing |
| STANDARD | + bindingType, actorId, tenancyId, handlerClass | Production |
| FULL | All fields including inputHash, parentStepName, executionEnvironment | FSI, clinical, regulated |

The event always carries all fields. The audit logger CDI observer filters at persistence time based on the configured profile.

### 5. Trust Model

Step definitions are loaded from three sources:
1. **Classpath YAML** — versioned files compiled into the deployment artifact
2. **CDI-discovered MCP tools** — Java beans on the classpath (McpStepCatalogWiring)
3. **Compiled @StepPlugin classes** — annotation-processed Java records (AptPluginSource)

All three are part of the deployed application. The trust boundary is deployment access — who can commit code to the repository and what CI gates exist. This is consistent with how Ansible treats Python modules and how Terraform treats providers.

The platform enforces what trusted definitions can **do** (ProcessExecutor allow-list and sandboxing), audits what **happened** (StepExecutionEvent), and closes **bypasses** (PythonInvokeHandler delegation). It does not enforce who can **create** step definitions — that's the deployment pipeline's job.

**Future revision trigger:** If a step definition marketplace, dynamic loading, or user-authored steps are ever added, the trust model must be revisited. Runtime signature verification would become necessary for untrusted sources.

### What changes where

| Module | Change |
|--------|--------|
| `platform-api/` | `ProcessCommand` — add `stdin(byte[])`, `memoryLimit(long)`, `allowedPaths(List<String>)` |
| `platform-api/` | `CommandPattern` record + `MatchMode` enum (new, `io.casehub.platform.api.process`) |
| `platform-api/` | `CommandNotAllowedException` (new, extends `ProcessExecutionException`) |
| `platform-api/` | `DefaultProcessExecutor` — add allow-list constructor, stdin piping, command validation |
| `yaml-core/` | `InvokeBinding.Python` — add `timeout` field (default "30s") |
| `yaml-step-runtime/` | `PythonInvokeHandler` — inject ProcessExecutor, delegate execution, use configurable timeout |
| `yaml-step-runtime/` | `StepExecutionEvent` — add audit fields (bindingType, actorId, tenancyId, inputHash, etc.) |
| `yaml-step-runtime/` | `ValidatingAction` — populate new audit fields on event emission |

### What does NOT change

- `ProcessExecutor` interface — stays the same (allow-list is an implementation concern)
- `ProcessInvokeHandler` — already delegates to ProcessExecutor correctly
- `AgentInvokeHandler` — already managed by agent-gate (rate limiting, semaphores)
- `McpInvokeHandler`, `GraphqlInvokeHandler`, `RestInvokeHandler` — platform-internal, different trust boundary
- `InvokeBinding.Process` — already has timeout field

## References

- `platform-api/src/main/java/io/casehub/platform/api/process/ProcessExecutor.java` — current SPI
- `platform-api/src/main/java/io/casehub/platform/api/process/ProcessCommand.java` — current command descriptor
- `platform-api/src/main/java/io/casehub/platform/api/process/DefaultProcessExecutor.java` — current impl
- `yaml-step-runtime/src/main/java/io/casehub/yaml/step/handler/PythonInvokeHandler.java` — raw ProcessBuilder bypass
- `yaml-step-runtime/src/main/java/io/casehub/yaml/step/handler/ProcessInvokeHandler.java` — correct delegation pattern
- `specs/issue-429-yaml-type-system/2026-09-25-dynamic-step-catalog-design.md` — trust model section, StepExecutionEvent
- `440-decisions.md` — D1-D5 design decisions
