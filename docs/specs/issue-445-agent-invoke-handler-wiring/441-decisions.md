# Decisions — Script Auto-Discovery (#441)

## D1: Universal InvokeBinding.Script replaces InvokeBinding.Python

**Choice:** Replace `InvokeBinding.Python` with `InvokeBinding.Script(runtime, script, timeout, workingDir, env)`. One binding type for all script runtimes (Python, Node.js, Ruby, etc.). One `ScriptInvokeHandler` replaces `PythonInvokeHandler`. YAML vocabulary preserves language-specific shorthand (`python:`, `node:`) as syntactic sugar.
**Alternatives:**
- Keep Python, add Node.js alongside — separate types for identical logic, grows linearly with runtimes
- Process binding with input mode flag — overloads Process semantics (Process is arbitrary commands; Script is structured I/O)
**Rationale:** The only difference between Python and Node.js invocation is the runtime executable name. Everything else (JSON stdin/stdout, timeout, ProcessExecutor delegation) is identical. One type eliminates duplication.
**Trade-offs:** Pre-release, so InvokeBinding.Python removal has zero migration cost. `InvokeBinding.Script` is slightly more verbose than `InvokeBinding.Python` (runtime field), but YAML shorthand hides this from authors.
**Sources:** PythonInvokeHandler (just refactored in #440), workers-script ScriptDefinition (same shape), InvokeBinding.Process (field parity: workingDir, env)
**Exploration:** deep-analysis
**Status:** captured

## D2: ScriptSource as universal catalog source with extension-based runtime detection

**Choice:** `ScriptSource` implements `CatalogSource`. Scans configurable filesystem paths for script files with companion `.schema.yaml` files. File extension determines runtime (`.py` → `python3`, `.js`/`.mjs` → `node`). Extensible via runtime registry for custom extensions.
**Alternatives:**
- Classpath-only discovery — prevents ops teams from dropping scripts into a directory without redeploying
- One source per language — duplicates discovery logic per runtime
**Rationale:** Filesystem discovery is the use case: "drop a script + schema, it appears in the catalog." Extension-based runtime detection eliminates configuration for common languages. Custom runtimes are supported via explicit configuration.
**Sources:** AptPluginSource (classpath scanning pattern), McpToolSource (CatalogSource populate pattern)
**Exploration:** quick
**Status:** captured

## D3: Full shape for workers-script alignment

**Choice:** `InvokeBinding.Script` includes `workingDir` and `env` fields (matching `InvokeBinding.Process`). `ProcessCommand` gains `maxOutputBytes` resource limit field. This makes Script a superset of workers-script's `ScriptDefinition`, enabling future migration of workers-script to consume ProcessExecutor + InvokeBinding.Script.
**Alternatives:**
- Minimal shape (runtime, script, timeout) — requires breaking change to InvokeBinding.Script when workers-script migrates
**Rationale:** Workers-script's `ScriptDefinition(command, args, workingDirectory, environment, timeoutSeconds, maxOutputBytes)` maps directly to `InvokeBinding.Script` + `ProcessCommand`. Designing the platform type as a superset now means the migration path is clean.
**Depends on:** D1 (universal Script binding)
**Sources:** workers-script-core ScriptDefinition.java, workers-script ScriptWorkerExecutionManager.java (raw ProcessBuilder — should migrate to ProcessExecutor)
**Exploration:** quick
**Status:** captured
