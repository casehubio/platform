# Decisions — Security Model for Invoke Handlers (#440)

## D1: SPI with pluggable enforcement for sandboxing

**Choice:** ProcessExecutor SPI declares resource limits (memory, filesystem, timeout) on ProcessCommand. Enforcement is pluggable: DefaultProcessExecutor = bare JVM (timeout only), container-native implementations enforce cgroups/namespaces, nsjail adapter for Linux. Platform defines policy, infrastructure enforces.
**Alternatives:**
- Container-native only — excludes local dev and non-container deployments
- JVM-level only — weaker guarantees, no real filesystem/memory isolation post-SecurityManager deprecation
**Rationale:** The ProcessExecutor SPI is already the execution boundary for ProcessInvokeHandler. Adding resource limit fields to ProcessCommand makes the SPI the single enforcement point. Swappable implementations support the full range from dev laptop to hardened Kubernetes.
**Trade-offs:** DefaultProcessExecutor provides timeout enforcement only — real sandboxing requires an alternative implementation. Deployments without a sandboxed executor get documentation-level security only.
**Sources:** ProcessExecutor SPI (platform-api/process/), ProcessInvokeHandler, PythonInvokeHandler
**Exploration:** quick
**Status:** captured

## D2: Allow-list enforcement on ProcessExecutor

**Choice:** Allow-list is a property of the ProcessExecutor, not the InvokeHandler. ProcessCommand carries the command; the executor checks it against the allow-list before executing. Consistent enforcement regardless of which handler triggers execution.
**Alternatives:**
- InvokeHandler layer — each handler checks its own allow-list. Enforcement is per-handler, easy to bypass if a new handler forgets.
- Cross-cutting StepSecurityPolicy SPI — new SPI consulted before any step executes. Inspects InvokeBinding. Decoupled but adds a new abstraction layer.
**Rationale:** ProcessExecutor is the chokepoint. Every process execution — from ProcessInvokeHandler, PythonInvokeHandler, or any future handler — flows through this SPI. Putting the allow-list here means zero handlers can bypass it.
**Trade-offs:** Allow-list can only restrict commands, not other binding types (REST URLs, MCP tools). Those would need separate mechanisms if ever needed.
**Sources:** ProcessExecutor SPI, ProcessInvokeHandler, PythonInvokeHandler
**Exploration:** quick
**Status:** captured

## D3: Rich audit event with tiered consumer profiles

**Choice:** StepExecutionEvent carries all fields — binding type, handler class, actor, tenant, input hash, parent step context, execution environment metadata. All nullable. Consumer profiles (MINIMAL / STANDARD / FULL) control what the audit logger persists. FSI/clinical deployments select FULL; dev uses MINIMAL.
**Alternatives:**
- Minimal fields only — insufficient for regulatory compliance (MiFID II, HIPAA, FDA 21 CFR Part 11)
- Full fields always persisted — storage overhead in non-regulated deployments
- Tiered emission + tiered consumption — avoids computing expensive fields when not consumed, but adds complexity to the emitter
**Rationale:** The event is the contract. Making it rich and nullable means consumers don't need to request field computation — they filter at persistence time. This is the same SPI-declares-capability / deployment-selects-enforcement pattern used in D1.
**Trade-offs:** All fields are always computed (input hash, parent context). For most fields this is negligible. If profiling reveals a hot field, it can be lazily computed later without changing the event contract.
**Sources:** ValidatingStepAction (existing StepExecutionEvent emission), FSI compliance requirements (MiFID II, SOX), clinical compliance (HIPAA, FDA 21 CFR Part 11)
**Exploration:** deep-analysis
**Status:** captured

## D4: PythonInvokeHandler delegates to ProcessExecutor

**Choice:** PythonInvokeHandler delegates to ProcessExecutor — a direct consequence of D1 and D2, not a design choice. ProcessCommand gains `stdin(byte[])` for piping JSON input. InvokeBinding.Python gains `timeout` (default "30s"). PythonInvokeHandler builds `ProcessCommand.of("python3", script).stdin(jsonBytes).timeout(timeout)`, delegates to ProcessExecutor, parses stdout JSON. The JSON marshalling protocol stays in PythonInvokeHandler (domain expertise); security enforcement stays in ProcessExecutor (domain expertise).
**Alternatives:**
- Merge Python into Process — loses the ergonomic `python: my-script.py` YAML syntax and the binding type semantics (useful for D3 audit logging). The I/O protocol (JSON-over-stdin) is a convention, not inherent to Process.
- Keep raw ProcessBuilder — security hole: bypasses D1 sandboxing and D2 allow-list enforcement
**Rationale:** Not a choice but a logical consequence. If ProcessExecutor is the security boundary (D1) and the allow-list enforcement point (D2), then every subprocess must flow through it. PythonInvokeHandler currently bypasses it via raw ProcessBuilder. The only gap to close is stdin piping on ProcessCommand.
**Depends on:** D1 (pluggable enforcement), D2 (allow-list)
**Sources:** PythonInvokeHandler (raw ProcessBuilder, hardcoded 30s timeout), ProcessCommand (no stdin field), InvokeBinding.Python (no timeout field)
**Exploration:** deep-analysis
**Status:** captured

## D5: Trust model is documented, not enforced

**Choice:** The trust model is a documentation section, not a runtime feature. Step definitions come from three sources — classpath YAML, CDI-discovered MCP tools, compiled @StepPlugin classes — all of which are part of the deployed application and trusted by definition. There is no untrusted source of step definitions, so there is nothing to enforce at the application level. "Who can modify step definitions" = "who can deploy code" = an operator concern (repo access, CI gates, code review), not a platform feature.
**Alternatives:**
- Deploy-time checksum validation — defends against supply chain tampering between build and deploy, but container registries already provide this
- Runtime signature verification — defends against untrusted runtime sources (step definition marketplace), but no such marketplace exists
**Rationale:** The platform's security job is to enforce what trusted definitions can DO (D1, D2), audit what happened (D3), and close bypasses (D4). The trust boundary itself is deployment access — the same boundary that governs Java classes, CDI beans, and every other piece of application code.
**Trade-offs:** If a step definition marketplace or dynamic loading feature is added in the future, this decision must be revisited — runtime verification would become necessary.
**Sources:** Dynamic step catalog spec §Trust model, InvokeBinding sealed interface (classpath-only sources), AptPluginSource (classpath scanning), McpStepCatalogWiring (CDI discovery)
**Exploration:** deep-analysis
**Status:** captured
