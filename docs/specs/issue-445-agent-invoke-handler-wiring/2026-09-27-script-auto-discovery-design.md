# Script Auto-Discovery — Universal Script Step Catalog Source

**Repo:** casehubio/platform
**Depends on:** #440 (ProcessExecutor allow-list + stdin piping), #433 (step catalog, CatalogSource)

## Problem

The step catalog supports plugin actions (@StepPlugin) and MCP tools (McpToolSource) as catalog sources. Python and Node.js scripts require a wrapping YAML step definition to be accessible. Operations teams should be able to drop a script + companion schema file into a directory and have it appear in the catalog automatically.

Additionally, `InvokeBinding.Python` is Python-specific — adding Node.js or any other scripting runtime would require duplicating the binding type and handler. The JSON stdin/stdout protocol is runtime-agnostic.

## Scope

**In scope:**
- `InvokeBinding.Script` replaces `InvokeBinding.Python` — universal (runtime, script, timeout, workingDir, env)
- `ScriptInvokeHandler` replaces `PythonInvokeHandler` — delegates to ProcessExecutor
- `ScriptSource` — filesystem catalog source, discovers scripts with companion `.schema.yaml`
- `ProcessCommand.maxOutputBytes` resource limit field
- Extension-based runtime detection (`.py` → python3, `.js`/`.mjs` → node)

**Out of scope:**
- Phase 2: Python type-hints derivation (separate issue)
- Workers-script migration to ProcessExecutor (separate issue against workers repo)
- YAML deserialization of shorthand `python:` / `node:` keys (yaml-jackson, deferred)

## Design

### 1. InvokeBinding.Script

Replaces `InvokeBinding.Python`. Universal binding for any scripting runtime:

```java
record Script(String runtime, String script, String timeout,
              String workingDir, Map<String, String> env) implements InvokeBinding {
    public static final String PYTHON = "python3";
    public static final String NODE = "node";

    public Script {
        if (runtime == null) throw new IllegalArgumentException("Script binding requires runtime");
        if (script == null) throw new IllegalArgumentException("Script binding requires script");
        if (timeout == null) timeout = "30s";
        if (env == null) env = Map.of();
    }
}
```

`InvokeBinding.Python` is removed. All references updated. Pre-release, zero migration cost.

### 2. ScriptInvokeHandler

Replaces `PythonInvokeHandler`. Identical logic, works with any runtime:

```java
public class ScriptInvokeHandler implements InvokeHandler {
    // Constructor: ObjectMapper + ProcessExecutor
    // supports(): InvokeBinding.Script
    // create(): builds ProcessCommand.of(runtime, script).stdin(jsonBytes)
    //           .timeout(timeout).workingDir(workingDir) → delegates to ProcessExecutor
    // Parses JSON stdout, handles errors
}
```

### 3. ScriptSource — Catalog Source

Implements `CatalogSource`. Scans configurable filesystem paths for script files with companion `.schema.yaml` files:

```
/opt/casehub/scripts/
  sentiment.py
  sentiment.schema.yaml
  transform.js
  transform.schema.yaml
```

**Discovery algorithm:**
1. Scan each configured path for files matching known extensions
2. For each script file, look for companion `<basename>.schema.yaml`
3. Parse schema YAML → StepDefinition (name, description, inputs, outputs)
4. Determine runtime from extension (`.py` → `python3`, `.js`/`.mjs` → `node`)
5. Create CatalogEntry with InvokeBinding.Script + ScriptInvokeHandler-created StepAction

**Schema file format** (companion `.schema.yaml`):

```yaml
name: sentiment-analysis         # optional, defaults to filename
description: Analyze sentiment   # optional
inputs:
  text:
    type: string
    required: true
    description: Text to analyze
  language:
    type: string
    description: Language code
outputs:
  score:
    type: number
    description: Sentiment score -1.0 to 1.0
  label:
    type: string
    description: positive/negative/neutral
timeout: 60s                     # optional, overrides default
```

**Runtime registry:**

```java
private static final Map<String, String> EXTENSION_RUNTIMES = Map.of(
    ".py", "python3",
    ".js", "node",
    ".mjs", "node"
);
```

Extensible via configuration: `casehub.script.runtimes.rb=ruby` adds `.rb` → `ruby`.

**Configuration:**

```properties
casehub.script.paths=/opt/casehub/scripts,/app/scripts
casehub.script.runtimes.rb=ruby
```

**Priority:** 400 (after MCP tools at 300, APT plugins at 200).

### 4. ProcessCommand.maxOutputBytes

Advisory resource limit field, matching workers-script's `maxOutputBytes`:

```java
public ProcessCommand maxOutputBytes(long bytes) { ... }
public Long maxOutputBytes() { return maxOutputBytes; }
```

DefaultProcessExecutor ignores this field (same as memoryLimitBytes). Sandboxed implementations can enforce it.

### What changes where

| Module | Change |
|--------|--------|
| `yaml-core/` | `InvokeBinding.Python` → `InvokeBinding.Script` (runtime, script, timeout, workingDir, env) |
| `platform-api/` | `ProcessCommand.maxOutputBytes(long)` field |
| `yaml-step-runtime/` | `PythonInvokeHandler` → `ScriptInvokeHandler` (rename + universal runtime) |
| `yaml-step-runtime/` | `ScriptSource` (new CatalogSource — filesystem discovery) |

### What does NOT change

- `InvokeBinding.Process` — arbitrary command execution, different abstraction
- `ProcessExecutor` SPI — ScriptInvokeHandler delegates to it (no SPI changes)
- `AptPluginSource`, `McpToolSource` — other catalog sources unchanged
- `CompositeStepCatalog` — accepts any CatalogSource, no changes needed

## References

- `yaml-core/src/main/java/io/casehub/yaml/core/step/InvokeBinding.java` — current Python binding
- `yaml-step-runtime/src/main/java/io/casehub/yaml/step/handler/PythonInvokeHandler.java` — current handler
- `yaml-step-runtime/src/main/java/io/casehub/yaml/step/CatalogSource.java` — catalog source SPI
- `yaml-step-runtime/src/main/java/io/casehub/yaml/step/catalog/AptPluginSource.java` — classpath discovery pattern
- `workers-script-core/ScriptDefinition.java` — workers-script alignment target
- `workers-script-core/ScriptWorkerExecutionManager.java` — raw ProcessBuilder (migration target)
- `440-decisions.md` — ProcessExecutor security model
- GitHub #441, #433, #440
