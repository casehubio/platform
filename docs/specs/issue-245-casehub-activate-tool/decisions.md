## D1: Per-operation tool naming — domain_operation

**Choice:** `{domain}_{operationName}` (e.g., `devtown_caseReasoning`). Matches the issue description.
**Alternatives:**
- casehub_domain_operation — verbose, casehub_ prefix adds no information (all tools come from the casehub server)
- domain.operation — dot separator is unconventional for MCP tool names
**Rationale:** Clean, discoverable. Domain names don't collide with the `casehub_` prefix used by built-in meta-tools. Tool list naturally groups by domain prefix.
**Trade-offs:** If two domains have operations with the same name, the tool names are still unique because the domain prefix differs.
**Sources:** DynamicToolRegistrar.java (mcp/), issue #245
**Exploration:** quick
**Status:** captured
