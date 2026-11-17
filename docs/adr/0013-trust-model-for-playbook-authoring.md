# 0013 — Trust model for playbook authoring

Date: 2026-11-17
Status: Accepted

## Context and Problem Statement

Playbooks can contain steps that execute arbitrary shell commands (`process`), make arbitrary HTTP calls (`rest-call`), evaluate MVEL expressions with full Java interop, and read environment variables (`VariableSource.env()`) and system properties (`VariableSource.systemProperty()`). These make a playbook equivalent to arbitrary code execution.

The validator MCP tool (WS-6b) will expose the step catalogue to LLMs for assisted playbook authoring. Without filtering, an LLM-generated playbook could execute shell commands, exfiltrate secrets via HTTP, or read the server's environment. The step catalogue served to an LLM must exclude dangerous capabilities.

Findings: P26, P42. Direction: LD-9, TN-2.

## Decision Drivers

* The existing `SchemaFilteredRegistry` already wraps `PluginRegistry` and filters by capability set — zero new filtering abstractions needed
* `process` and `rest-call` currently share the `"steps"` capability with safe plugins — the capability taxonomy is too coarse for risk filtering
* `VariableSource.env()` and `systemProperty()` exist as factory methods but are not registered in production — variable source control is a registration decision, not a filtering one
* MVEL has full Java interop (reflection, method calls) and is equivalent-risk to `process` — expression engines must be tier-controlled alongside step types

## Considered Options

* **Option A** — Extend the capability taxonomy with risk-bearing constants; add `assisted` as a built-in schema that excludes them
* **Option B** — Introduce a separate `TrustTier` enum and `TrustFilteredRegistry` with per-definition `minimumTrust`

## Decision Outcome

Chosen option: **Option A — Extend the capability taxonomy**, because the existing schema and filtering infrastructure is expressive enough — it just needs finer-grained capability constants for dangerous steps.

### Authoring contexts

Two contexts, not three:

| Context | Description | Available steps |
|---------|-------------|-----------------|
| `developer` | Human writing YAML in an IDE/CLI. Full access. | All capabilities — unfiltered registry (today's default) |
| `assisted` | LLM generating a playbook via the validator MCP tool. Restricted. | All capabilities except `process-execution`, `raw-http`, `expression-mvel`, `env-access` |

"Tenant-admin" is not a CaseHub term. A middle tier can be added when a customer-facing authoring context exists — the architecture supports it via schema inheritance.

### New capability constants

Added to `PlaybookCapabilities`:

| Constant | Value | Dangerous step(s) |
|----------|-------|--------------------|
| `PROCESS_EXECUTION` | `"process-execution"` | `process` plugin |
| `RAW_HTTP` | `"raw-http"` | `rest-call` plugin |
| `EXPRESSION_MVEL` | `"expression-mvel"` | `MvelExpressionEngine` |
| `ENV_ACCESS` | `"env-access"` | `VariableSource.env()`, `systemProperty()` |

These are added to the `server` and `client` built-in schemas (developer has full access). The new `assisted` schema inherits from `server` but excludes these four capabilities.

### Step reclassification

The `process` plugin changes its `Definition.capability()` from `"steps"` to `"process-execution"`. The `rest-call` plugin changes from `"steps"` to `"raw-http"`. Both remain available to the `developer` context (which uses the unfiltered registry) but are excluded from the `assisted` schema.

Managed connector plugins (bank, voice, email, etc.) keep their existing domain-specific capabilities and remain available in the `assisted` context. They call managed SPIs with known inputs/outputs — the side-effect risk is handled by the existing consent infrastructure (`ConsentTier`, `WorkerAction`), which is orthogonal.

### Tier assignment

The calling context determines the tier, not the playbook itself:

* The validator MCP tool (WS-6b) passes `assisted` when creating its `SchemaFilteredRegistry`
* CLI and IDE usage defaults to `developer` (unfiltered registry — no schema filter applied)
* An LLM cannot declare itself a developer — the tool controls the context

No playbook header needed. The tier is a property of who is authoring, not of the playbook.

### Variable source and expression engine control

`VariableSource.env()` and `systemProperty()` are not registered in production today. The trust model formalises this: these sources may only be registered when the authoring context is `developer`. The `ENV_ACCESS` capability gates their registration.

MVEL expression evaluation is gated by the `EXPRESSION_MVEL` capability. JQ and JEXL remain available in both contexts (JQ is a JSON transform with no side effects; JEXL is sandboxed).

### Configuration surface

```yaml
casehub:
  playbook:
    trust:
      default-context: developer  # developer | assisted
```

The default is `developer` — today's behaviour, no breaking change. The validator MCP tool overrides this to `assisted` when it creates its filtered registry.

### Positive Consequences

* Zero new abstractions — reuses `SchemaFilteredRegistry`, `PlaybookCapabilities`, and `MapPlaybookSchemaRegistry`
* The WS-6b integration point is one line: `SchemaFilteredRegistry.forSchema(registry, schemas, "assisted")`
* Connector plugins remain available to LLM-authored playbooks, making the assisted surface useful
* Adding a future middle tier is a new schema entry with its own capability set — no structural change

### Negative Consequences

* Capability constants are stringly-typed — a typo silently excludes a step. Mitigated by constants in `PlaybookCapabilities` and schema test coverage.
* No per-playbook voluntary restriction — a developer cannot declare "this playbook should validate under the assisted rules." Acceptable; they can use the validator tool directly.

## References

* Issue: casehubio/platform#830
* Direction: `playbook-direction.md` (LD-9, TN-2)
* Findings: P26 (playbook equivalent to code), P42 (env/systemProperty sources)
* `SchemaFilteredRegistry.java` — existing capability-based filtering
* `PlaybookCapabilities.java` — capability constant definitions
* `MapPlaybookSchemaRegistry.java` — built-in schema registration
* `VariableSource.java:18-24` — `env()` and `systemProperty()` factory methods
* `ConsentTier.java` — orthogonal consent infrastructure for side-effect control
