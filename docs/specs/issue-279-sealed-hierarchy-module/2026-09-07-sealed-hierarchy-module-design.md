# Promote SealedHierarchyModule to casehub-schema-generator — Design Spec

**Issue:** casehubio/platform#279
**Date:** 2026-09-07
**Status:** Approved

## Summary

Promote neocortex's `SealedHierarchyModule` to the shared `casehub-schema-generator`
module alongside `EnumInliningModule` and `UnevaluatedPropertiesModule`. The module is
fully generic — uses `Class.getPermittedSubclasses()` with configurable discriminator
overrides. Blocks needs it for ~35 sealed interfaces in its YAML DSL parity work
(casehubio/blocks#165).

## Approach

Copy the module source from `io.casehub.neocortex.schema.SealedHierarchyModule` to
`io.casehub.schema.generator.module.SealedHierarchyModule`. Same API, new package.
Opt-in inclusion — not added to `PlatformSchemaGenerator`'s default module set.

## Module Placement

```
schema-generator/src/main/java/io/casehub/schema/generator/module/
  EnumInliningModule.java          — existing
  UnevaluatedPropertiesModule.java — existing
  SealedHierarchyModule.java       — new
```

## API

```java
public class SealedHierarchyModule implements Module {

    public SealedHierarchyModule() { /* default — lowerCamelCase discriminators */ }

    public SealedHierarchyModule(Map<Class<?>, Map<Class<?>, String>> overrides) {
        /* per-parent, per-subtype discriminator value overrides */
    }

    @Override
    public void applyToConfigBuilder(SchemaGeneratorConfigBuilder builder) { ... }
}
```

**Behaviour:**
- Intercepts sealed types via `Class.getPermittedSubclasses()`
- Generates JSON Schema `oneOf` with a `"type": {"const": "<discriminator>"}` property on each variant
- Default discriminator: lowerCamelCase of the subtype's simple name (`WallClock` → `"wallClock"`)
- Per-parent/per-subtype overrides via the constructor map
- Returns `null` for non-sealed types (no interference with other modules)
- Discriminator property name hardcoded to `"type"` (JSON Schema / OpenAPI convention)

**Consumer usage:**

```java
var gen = new PlatformSchemaGenerator(
    new SealedHierarchyModule(Map.of(
        MySealed.class, Map.of(MySealed.VariantA.class, "variant-a")
    ))
);
```

## Inclusion Strategy

Opt-in via `PlatformSchemaGenerator`'s `Module... customModules` varargs. Not
auto-included in the default set. This is backwards-compatible — no schema output
changes for existing consumers who happen to generate schemas for sealed types.

## Test Strategy

Create local sealed interfaces in the test package — no dependency on neocortex types.

```java
sealed interface Shape permits Circle, Rectangle, Triangle {}
record Circle(double radius) implements Shape {}
record Rectangle(double width, double height) implements Shape {}
record Triangle(double base, double height) implements Shape {}
```

Test cases (mirroring neocortex's coverage):
1. Sealed interface generates `oneOf` with type discriminators
2. Default discriminators are lowerCamelCase
3. Discriminator overrides replace defaults
4. Non-sealed types are not intercepted
5. Each `oneOf` entry has discriminator property, `required`, and `$ref`
6. `defaultDiscriminator()` static method produces correct casing

## Files Changed

| File | Action |
|------|--------|
| `schema-generator/src/main/java/io/casehub/schema/generator/module/SealedHierarchyModule.java` | New — module source |
| `schema-generator/src/test/java/io/casehub/schema/generator/module/SealedHierarchyModuleTest.java` | New — tests with local sealed types |

## Follow-up

File issue on casehubio/neocortex to migrate from local
`io.casehub.neocortex.schema.SealedHierarchyModule` to shared
`io.casehub.schema.generator.module.SealedHierarchyModule` and delete the local copy.

## References

- `io.casehub.neocortex.schema.SealedHierarchyModule` — source implementation (72 lines)
- `io.casehub.neocortex.schema.SealedHierarchyModuleTest` — source tests (148 lines)
- `io.casehub.schema.generator.PlatformSchemaGenerator` — target module's entry point
- `io.casehub.schema.generator.module.EnumInliningModule` — existing shared module (pattern reference)
- `docs/specs/2026-08-29-shared-schema-generator-design.md` — original schema-generator extraction spec
- casehubio/blocks#165 — primary consumer (YAML DSL parity, ~35 sealed interfaces)
