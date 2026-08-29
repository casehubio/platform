# Shared Schema Generator — Design Spec

**Issue:** TBD (file on casehubio/parent)
**Date:** 2026-08-29
**Status:** Draft

## Summary

Extract JSON Schema generation from Java types into a shared platform module
(`casehub-schema-generator`). Engine already has this as `CaseHubSchemaGenerator` in its
`generator/` module using **victools/jsonschema-generator** (reflection-based). Eidos needs the
same capability to generate `descriptor-schema.json` from `AgentDescriptor`. Rather than
duplicate, extract the shared base to platform.

## Approach — Port Engine's Generator, Generalise

Engine's generator is production-tested. The shared module should **port the base configuration
and common modules**, leaving domain-specific modules in each repo.

### What moves to platform

| Source file | Target in shared module | Notes |
|---|---|---|
| `CaseHubSchemaGenerator` core setup | `io.casehub.schema.generator.PlatformSchemaGenerator` | SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON, DEFINITIONS_FOR_ALL_OBJECTS, FLATTENED_ENUMS_FROM_TOSTRING, JacksonModule, JakartaValidationModule |
| `SchemaPostProcessor` (generic parts) | `io.casehub.schema.generator.SchemaPostProcessor` | $schema insertion, generic $def cleanup. Domain-specific renames stay in each repo. |
| `EnumInliningModule` | `io.casehub.schema.generator.module.EnumInliningModule` | Generic — inlines enum values rather than generating $ref. Useful for any repo. |
| `UnevaluatedPropertiesModule` | `io.casehub.schema.generator.module.UnevaluatedPropertiesModule` | Generic JSON Schema 2020-12 support. |

### What stays in engine

| File | Reason |
|---|---|
| `WorkerSchemaModule` | Worker is engine-domain (extension point with additionalProperties) |
| `CaseCompletionSchemaModule` | CaseCompletion is engine-domain (typed additionalProperties) |
| `TriggerModule` | Engine-domain trigger oneOf schema |
| `BindingTargetModule` | Engine-domain binding target schema |
| `SpecNestingModule` | Engine-domain spec nesting |
| Domain-specific `SchemaPostProcessor` renames | Engine's UNWANTED_DEFS, RENAME_MAP |

### What eidos adds

| File | Purpose |
|---|---|
| `DispositionSchemaModule` | Custom schema for disposition axes (String axis values, not List<DispositionValue>) + convenience fields (mbtiType, enneagramType) |
| `CapabilitySchemaModule` | Custom schema for capabilities with epistemicDomains, excludedDomains |
| `EidosSchemaPostProcessor` | Eidos-specific $def cleanup |

## Module Structure

```
casehub-schema-generator/
  src/main/java/io/casehub/schema/generator/
    PlatformSchemaGenerator.java    — base config, generate(Class<?>) → JsonNode
    SchemaPostProcessor.java         — generic post-processing
    module/
      EnumInliningModule.java        — inline enums
      UnevaluatedPropertiesModule.java — JSON Schema 2020-12 keyword
  src/test/java/...
```

**Maven coordinates:**

| Element | Value |
|---|---|
| groupId | `io.casehub` |
| artifactId | `casehub-schema-generator` |
| parent | `casehub-parent` |

**Dependencies:**
- `com.github.victools:jsonschema-generator` (core)
- `com.github.victools:jsonschema-module-jackson` (respects @JsonPropertyDescription, @JsonProperty order)
- `com.github.victools:jsonschema-module-jakarta-validation` (pattern, not-nullable)
- `com.fasterxml.jackson.core:jackson-databind` (for JsonNode output)
- `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml` (optional — for YAML output)

## API

```java
public class PlatformSchemaGenerator {

    public PlatformSchemaGenerator(Module... customModules) {
        // Base config: DRAFT_2020_12, PLAIN_JSON, DEFINITIONS_FOR_ALL_OBJECTS,
        // FLATTENED_ENUMS_FROM_TOSTRING, JacksonModule, JakartaValidationModule,
        // EnumInliningModule, UnevaluatedPropertiesModule
        // + caller-provided custom modules
    }

    public JsonNode generate(Class<?> rootType) { ... }

    public void generateToYaml(Class<?> rootType, Path output) throws IOException { ... }

    public void generateToJson(Class<?> rootType, Path output) throws IOException { ... }
}
```

**Usage in engine:**

```java
var generator = new PlatformSchemaGenerator(
    new WorkerSchemaModule(),
    new CaseCompletionSchemaModule(),
    new TriggerModule(),
    new BindingTargetModule(),
    new SpecNestingModule()
);
JsonNode schema = generator.generate(CaseDefinition.class);
EngineSchemaPostProcessor.process(schema);  // engine-specific renames
```

**Usage in eidos:**

```java
var generator = new PlatformSchemaGenerator(
    new DispositionSchemaModule(),
    new CapabilitySchemaModule()
);
JsonNode schema = generator.generate(AgentDescriptor.class);
EidosSchemaPostProcessor.process(schema);
```

## Validation Pattern

Each consuming repo has a test that:
1. Generates schema from the canonical Java type
2. Validates example YAML files against the generated schema
3. Fails if examples don't match the schema (catches drift)

Engine already has `GeneratedSchemaValidationTest`. Eidos will add `DescriptorSchemaValidationTest`.

## Protocol

Write an indexed protocol: `schema-generation-from-java.md`

**Rule:** Any CaseHub repo that exposes a YAML declaration surface MUST:
1. Depend on `casehub-schema-generator`
2. Generate JSON Schema from the canonical Java type (not hand-write it)
3. Publish the schema as a classpath resource (`META-INF/<module>/schema.json`)
4. Have a validation test that checks example YAMLs against the generated schema

**Applies to:** engine (CaseDefinition), eidos (AgentDescriptor), desiredstate (future),
any new repo with YAML-declared configuration.

## Engine Migration Path

1. Add `casehub-schema-generator` dependency to engine `generator/` module
2. Replace `CaseHubSchemaGenerator` base setup with `new PlatformSchemaGenerator(...)`
3. Keep engine-specific modules and post-processor
4. Run `GeneratedSchemaValidationTest` — must still pass

## References

- `io.casehub.generator.CaseHubSchemaGenerator` in engine — existing implementation (82 lines)
- `io.casehub.generator.SchemaPostProcessor` in engine — post-processing
- `io.casehub.generator.module.*` in engine — 8 custom modules (2 generic, 6 domain-specific)
- `io.casehub.generator.GeneratedSchemaValidationTest` — existing validation test
- victools/jsonschema-generator docs: https://victools.github.io/jsonschema-generator/
- D3 decision in eidos#147 specs
