# Design: pools section in platform manifest

## Summary

Add an optional `pools:` section to the platform manifest schema (`agent-config.yaml`), allowing declarative pool definitions alongside existing `models:`, `providers:`, `sources:`, and `aliases:` sections. Pool declarations use platform-neutral types in `agent-config-core`; consumers (claudony) map them to their own pool provisioning types.

## YAML notation

```yaml
pools:
  code-reviewer-pool:
    agent-id: code-reviewer
    backend: claudony
    min-active: 2
    max-active: 8
    working-dir: ~/workspace/reviews
    scaling:
      type: target-tracking
      target: 0.7
    extensions:
      model-chain:
        - opus
        - sonnet
```

The map key (`code-reviewer-pool`) becomes the pool name. All fields except `agent-id` have defaults or are optional.

## New type: PoolDeclaration

```java
// agent-config-core
public record PoolDeclaration(
    String name,
    @JsonProperty("agent-id") String agentId,
    String backend,
    @JsonProperty("min-active") int minActive,
    @JsonProperty("max-active") int maxActive,
    @JsonProperty("working-dir") String workingDir,
    Map<String, Object> scaling,
    Map<String, Object> extensions
) {
    public PoolDeclaration {
        Objects.requireNonNull(agentId, "agent-id is required");
        if (minActive < 0) throw new IllegalArgumentException("min-active must be >= 0");
        if (maxActive < 1) throw new IllegalArgumentException("max-active must be >= 1");
        if (maxActive < minActive) throw new IllegalArgumentException("max-active must be >= min-active");
        scaling = scaling != null ? Map.copyOf(scaling) : Map.of();
        extensions = extensions != null ? Map.copyOf(extensions) : Map.of();
    }
}
```

**Core fields** (typed, validated at parse time):
- `name` — injected from the YAML map key, not a YAML field
- `agentId` — required, identifies the agent definition
- `backend` — optional, which backend provisions this pool (e.g. "claudony")
- `minActive` / `maxActive` — capacity bounds, validated
- `workingDir` — optional, filesystem path for agent working directories

**Opaque fields** (consumer-interpreted):
- `scaling` — scaling config map, claudony interprets via its existing `parseScaling()` logic
- `extensions` — catch-all for consumer-specific config (model-chain, budget, eviction, etc.)

## Modified types

### Manifest

```java
public record Manifest(
    List<ModelDescriptor> models,
    List<ProviderDeclaration> providers,
    List<SourceDeclaration> sources,
    Map<String, AliasDeclaration> aliases,
    @JsonProperty("local-models") List<LocalModelDeclaration> localModels,
    ManifestDefaults defaults,
    Map<String, PoolDeclaration> pools          // NEW
) { ... }
```

The `pools` map key is the pool name. The compact constructor iterates entries and creates new `PoolDeclaration` instances with the map key as `name` (since Jackson populates the map but can't inject the key into the record). Then defensive copy.

```java
if (pools != null) {
    var injected = new LinkedHashMap<String, PoolDeclaration>();
    for (var e : pools.entrySet()) {
        var p = e.getValue();
        injected.put(e.getKey(), new PoolDeclaration(
            e.getKey(), p.agentId(), p.backend(), p.minActive(), p.maxActive(),
            p.workingDir(), p.scaling(), p.extensions()));
    }
    pools = Map.copyOf(injected);
} else {
    pools = Map.of();
}
```

### ManifestResult

```java
public record ManifestResult(
    Map<String, ModelQuery> aliases,
    String defaultBackendKey,
    List<PoolDeclaration> pools                 // NEW
) { ... }
```

`ManifestResult.empty()` returns empty pools list. Pools are pass-through — no processing by `ManifestProcessor`, just collected and forwarded.

### ManifestProcessor

`process()` collects `manifest.pools()` values into a `List<PoolDeclaration>` and includes them in the returned `ManifestResult`. Logs pool count alongside existing alias/backend logging.

### ManifestLoader

Pool merge follows name-keyed replace: when merging manifests from the directory hierarchy, a child manifest's pool with the same name fully replaces the parent's declaration. Implementation: standard map merge — `putAll` child pools into merged map after parent pools.

## JSON Schema

New `pool-declaration.schema.json` alongside existing `model-selection.schema.json`:

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "pool-declaration",
  "type": "object",
  "required": ["agent-id"],
  "properties": {
    "agent-id": { "type": "string" },
    "backend": { "type": "string" },
    "min-active": { "type": "integer", "minimum": 0, "default": 0 },
    "max-active": { "type": "integer", "minimum": 1, "default": 10 },
    "working-dir": { "type": "string" },
    "scaling": { "type": "object" },
    "extensions": { "type": "object" }
  },
  "additionalProperties": false
}
```

## Test plan

1. **PoolDeclarationTest** — validation: null agentId throws, minActive < 0 throws, maxActive < minActive throws, defaults applied for scaling/extensions
2. **ManifestLoaderTest** — YAML with `pools:` section parses correctly, pool name injected from map key
3. **ManifestLoaderTest** — merge: child pool replaces parent pool with same name, parent-only pools preserved
4. **ManifestProcessorTest** — pools pass through to ManifestResult unchanged

## Out of scope

- Claudony `PoolDeclaration` → `AgentPoolDefinition` mapping (claudony repo)
- Spring auto-configuration for pool declarations (claudony concern)
- Pool provisioning, session pre-warming (claudony runtime)
- Fleet script integration (casehubio/claudony#248)

## References

- `agent-config-core/src/main/java/io/casehub/platform/agent/config/Manifest.java` — existing manifest record
- `agent-config-core/src/main/java/io/casehub/platform/agent/config/ManifestLoader.java` — discovery, parsing, merging
- `agent-config-core/src/main/java/io/casehub/platform/agent/config/ManifestProcessor.java` — processing pipeline
- `agent-config-core/src/main/java/io/casehub/platform/agent/config/ManifestResult.java` — processor output
- `claudony/casehub/fleet/AgentPoolDefinition.java` — claudony pool types (consumer reference)
- `claudony/casehub/fleet/AgentPoolYamlParser.java` — existing pool YAML parsing (consumer reference)
- casehubio/platform#511 — this issue
- casehubio/claudony#248 — fleet script lifecycle epic (context)
