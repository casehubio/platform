## D1: Keep casehub_model alongside dynamic casehub_action

**Choice:** Keep both tools. `casehub_model` stays for interactive exploration (runtime state, enricher summaries, event details). `casehub_action` gets the dynamic schema for proactive operation guidance.
**Alternatives:**
- Remove casehub_model — everything in schema. Loses runtime state/enricher data that doesn't belong in a static schema.
- Simplify casehub_model — strip operations. Possible but premature; reassess after dynamic schema proves sufficient.
**Rationale:** The schema gives LLMs the operation catalog; casehub_model gives them live context. Different purposes, both needed.
**Trade-offs:** Two tools instead of one. Acceptable — they serve distinct roles.
**Sources:** CaseHubMcpTools.java (current two-tool design), issue #240 body
**Exploration:** quick
**Status:** captured

## D2: Schema representation — config-driven mode selection

**Choice:** Config property (`casehub.mcp.schema-mode=rich|simple`) selects schema shape at startup. `rich` = domain enum + if-then per-domain operation enums. `simple` = domain enum + description-based operation catalog. One mode active per deployment, not both in the same schema.
**Alternatives:**
- Both in same schema — duplicative bloat, operation catalog appears twice in different formats
- Client-aware detection — inspect client name at initialize, serve different schemas. Fragile (client names change), harder to test.
**Rationale:** Avoids repetitive bloat. Config is explicit, testable, and operator-controlled. Client-aware selection can be added later if multiple client types actually connect.
**Trade-offs:** Operator must choose a mode. Default to `simple` — works everywhere. `rich` is opt-in for environments where the client is known to parse conditional JSON Schema.
**Depends on:** D1 (both tools kept — schema is only on casehub_action)
**Sources:** JSON Schema if-then spec, MCP protocol initialize handshake
**Exploration:** quick
**Status:** captured

## D3: Replace static @Tool with ToolManager registration

**Choice:** Remove the `@Tool casehub_action` annotation. Register the dynamic tool via `ToolManager.newTool()` after scanner completes at startup. `casehub_model` stays as `@Tool` (no dynamic schema needed).
**Alternatives:**
- Register alongside as casehub_action_v2 — confusing, two action tools visible to clients
**Rationale:** Pre-release platform, no external consumers to break. One tool, one schema. Clean.
**Trade-offs:** None meaningful. The @Tool method body moves to the ToolManager handler.
**Depends on:** D1 (casehub_model stays as @Tool)
**Sources:** quarkus-mcp-server ToolManager API (v1.11.1), investigation confirming newTool/setInputSchema/register/automatic list_changed
**Exploration:** quick
**Status:** captured
