# Decisions — issue-511-manifest-pools

## D1: Pool type ownership

**Choice:** Platform-neutral records in agent-config-core
**Alternatives:**
- Raw Map passthrough — simpler but no compile-time safety or schema validation
- Shared SPI types in platform-api — more coupling between platform and claudony
**Rationale:** Keeps manifest self-contained and validatable at the platform layer. Claudony maps PoolDeclaration to its own AgentPoolDefinition. Same pattern as ManifestProcessor handling models/aliases — platform parses, consumer interprets.
**Trade-offs:** Claudony must maintain a mapping layer from PoolDeclaration to AgentPoolDefinition. New pool config fields require platform record changes.
**Sources:** agent-config-core/src/main/java/io/casehub/platform/agent/config/Manifest.java, claudony/casehub/fleet/AgentPoolDefinition.java
**Exploration:** quick
**Status:** captured

## D2: ManifestResult pool exposure

**Choice:** Add pools list to ManifestResult
**Alternatives:**
- Separate PoolManifestResult — isolates pool concerns but fragments the result type
- Callback SPI — reverses control flow, more complex wiring
**Rationale:** Consistent with how aliases are already exposed. ManifestResult is the single output of ManifestProcessor — adding pools keeps consumers in one place. Claudony reads pools() and maps to AgentPoolDefinition.
**Trade-offs:** ManifestResult grows. Consumers that don't care about pools carry the field (empty list).
**Sources:** agent-config-core/src/main/java/io/casehub/platform/agent/config/ManifestResult.java
**Exploration:** quick
**Depends on:** D1 (pool type ownership determines what ManifestResult carries)
**Status:** captured

## D3: Pool declaration detail level

**Choice:** Core fields + opaque extensions
**Alternatives:**
- Full typed hierarchy — mirrors claudony's scaling/budget/eviction types, tightly coupled
- Minimal reference only — just name+agentId+backend+capacity, too sparse for standalone validation
**Rationale:** Core fields (name, agentId, backend, minActive, maxActive, workingDir) are universally meaningful and schema-validatable. Scaling and extensions as Map<String, Object> let claudony evolve its config without platform changes. Schema validates structure; consumer validates semantics.
**Trade-offs:** Scaling config errors surface at claudony interpretation time, not platform parse time. No compile-time guarantee on scaling field shapes.
**Sources:** claudony/casehub/fleet/AgentPoolYamlParser.java (scaling config variety), casehubio/platform#511 issue example YAML
**Exploration:** quick
**Depends on:** D1 (pool type ownership)
**Status:** captured

## D4: Pool merge strategy

**Choice:** Name-keyed replace
**Alternatives:**
- Deep merge per pool — flexible but hard to reason about field provenance
- Append-only, no override — prevents per-environment overrides
**Rationale:** Consistent with existing manifest merge behavior. A child manifest declaring the same pool name fully replaces the parent's version. Simple mental model: "last writer wins per pool name."
**Trade-offs:** Can't selectively override one field of a parent pool declaration — must redeclare the entire pool. Acceptable given pool declarations are typically small.
**Sources:** agent-config-core/src/main/java/io/casehub/platform/agent/config/ManifestLoader.java (existing merge logic)
**Exploration:** quick
**Status:** captured
