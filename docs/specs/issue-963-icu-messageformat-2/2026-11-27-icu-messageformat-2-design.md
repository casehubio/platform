# ICU MessageFormat 2.0 Integration for Multilingual Template Formatting

**Issue:** casehubio/platform#963
**Epic:** #943 (Message Extraction Pipeline)
**Scale:** L | **Complexity:** High

## Purpose

Extend the unified template registry (#959) with locale-aware message formatting via ICU MessageFormat 2.0. MF2 handles selector-based variant choice (plural forms, gender selection, ordinal formatting) while the existing slot-filling handles positional substitution. These are complementary — MF2 is per-template, not a pipeline stage.

The existing `TemplateGenerator` produces surface forms via `${var}` substitution. This works for governed protocol vocabulary ("I approve this architecture") where the template IS the grammar. But it fails for quantity-bearing or locale-sensitive templates — "1 items have been approved" is grammatically wrong, and date/number formatting varies by locale.

MF2 addresses this by selecting between pre-defined message variants based on variable values and CLDR locale rules.

## Architecture

### Integration Model

MF2 affects the **generation** side only. Matching (regex + NLP structural) is unchanged. The integration is per-template: each `TemplateDefinition` optionally carries an MF2 message pattern alongside its matching patterns.

```
Template with messageFormat    →  generate() uses MF2 formatting
Template without messageFormat →  generate() uses slot-filling (unchanged)
```

**MF2 is generation-only (D10).** MF2 syntax is not designed for extraction — its variant structure (`.match`/`when`) selects output forms, not input patterns. Matching continues to use regex and NLP structural patterns. This creates intentional asymmetry: matching patterns and MF2 generation patterns are authored separately. Authors are responsible for ensuring the matching patterns cover the surface forms MF2 can generate. The bidirectionality contract from #959 D6 holds at the template level (each template can match and generate) but the mechanisms differ.

Generation dispatch in `NlpTemplateRegistry.generate()` (overrides `InMemoryTemplateRegistry`):
1. Look up `TemplateDefinition` from namespace
2. If `def.messageFormat() != null` and `MessageFormatResolver` is available → delegate to resolver's `format()` with pre-compiled handle
3. If `def.messageFormat() != null` and no resolver → throw `IllegalStateException` (fail-fast — MF2 template declared but no MF2 module on classpath)
4. If `def.messageFormat() == null` → slot-fill via `TemplateGenerator.generate()` (existing behavior)

### Module Structure

```
template-registry-api (zero-dep — existing)
├── TemplateDefinition (+ messageFormat field)
├── MessageFormatResolver (new @FunctionalInterface SPI)
├── MessageFormatSchema (new record — schema metadata)
├── SelectorInfo (new record — selector metadata)
└── ... existing types unchanged ...

template-registry-mf2 (new module)
├── IcuMessageFormatResolver implements MessageFormatResolver
│   └── wraps ICU4J MessageFormatter
├── Mf2PatternValidator (parse + validate at registration)
└── Mf2SchemaExtractor (extract selectors/variants for schema)

template-registry (existing — wiring changes only)
└── NlpTemplateRegistryProducer (inject optional MessageFormatResolver)
```

Dependencies:
- `template-registry-mf2` → `template-registry-api` + `com.ibm.icu:icu4j:79.1`
- `template-registry-api` has no new external dependencies (SPI is a Java interface)

### Classpath Activation

Follows the established pattern (ARC42STORIES §4):
- **Without `template-registry-mf2` on classpath:** Templates with `messageFormat` fail at registration time. All generation uses slot-filling.
- **With `template-registry-mf2` on classpath:** `IcuMessageFormatResolver` is discovered via CDI `Instance<MessageFormatResolver>`. Templates with `messageFormat` are validated and compiled at registration time. Generation uses ICU4J's `MessageFormatter`.

## Core Types

### MessageFormatResolver (SPI)

Lives in `template-registry-api`. Multi-method interface separating compilation (registration time) from formatting (generation time). Follows the `MatchingStrategy` precedent — not a `@FunctionalInterface`.

```java
package io.casehub.template.registry;

import java.util.Map;

public interface MessageFormatResolver {

    interface CompiledMessage {}

    CompiledMessage compile(String pattern, String languageTag);

    String format(CompiledMessage compiledPattern, Map<String, String> values);

    MessageFormatSchema extractSchema(String pattern);
}
```

**Contract:**
- `compile(pattern, languageTag)` parses and validates an MF2 pattern at registration time. Returns an opaque compiled handle (the implementation caches internally — typically a pre-built `MessageFormatter`). Throws `IllegalArgumentException` on syntax errors. The `languageTag` is a BCP 47 tag (from `TemplateNamespace.language()`) used for locale-specific CLDR rule binding.
- `format(compiledPattern, values)` formats a message using a pre-compiled handle and variable values. All values are strings — the resolver coerces to typed values per the MF2 pattern's function annotations (`:integer`, `:number`, `:date`). Throws `IllegalArgumentException` on:
  - Missing required variable
  - Value that cannot be coerced to the declared type (e.g., "abc" for `:integer`)
- `extractSchema(pattern)` extracts structured metadata (selectors, variant keys) for schema publication.

**Coercion failure semantics:** Fail-fast. Any coercion failure throws `IllegalArgumentException` with a message identifying the variable name, declared type, and actual value. No partial results, no best-effort — consistent with `TemplateGenerator.generate()` throwing on missing required variables. MF2's own fallback behaviour (§ "Handling Formatting Errors") is not used; the platform treats coercion failure as a programming error.

### TemplateDefinition (modified)

```java
public record TemplateDefinition(
    String name,
    List<String> patterns,
    Map<String, VariableConstraint> variables,
    String messageFormat  // nullable — null means slot-fill generation
) {
    public TemplateDefinition(String name, List<String> patterns,
                               Map<String, VariableConstraint> variables) {
        this(name, patterns, variables, null);
    }

    public TemplateDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(patterns, "patterns");
        if (patterns.isEmpty()) {
            throw new IllegalArgumentException(
                "Template must have at least one pattern");
        }
        patterns = List.copyOf(patterns);
        variables = variables != null
            ? Collections.unmodifiableMap(new LinkedHashMap<>(variables))
            : Map.of();
    }

    public static Builder builder(String name) { ... }
}
```

The `Builder` gains `.messageFormat(String)`. The existing 3-arg constructor delegates to the new 4-arg with `messageFormat = null`. Backward compatible.

### MessageFormatSchema (new)

Schema metadata for MF2 templates, published alongside `StructuralSummary`.

```java
package io.casehub.template.registry;

import java.util.List;
import java.util.Objects;

public record MessageFormatSchema(
    String pattern,
    List<SelectorInfo> selectors,
    List<String> variantKeys
) {
    public MessageFormatSchema {
        Objects.requireNonNull(pattern, "pattern");
        selectors = selectors != null ? List.copyOf(selectors) : List.of();
        variantKeys = variantKeys != null ? List.copyOf(variantKeys) : List.of();
    }
}
```

### SelectorInfo (new)

```java
package io.casehub.template.registry;

import java.util.Objects;

public record SelectorInfo(
    String variableName,
    String function
) {
    public SelectorInfo {
        Objects.requireNonNull(variableName, "variableName");
        Objects.requireNonNull(function, "function");
    }
}
```

### TemplateSchema (modified)

```java
public record TemplateSchema(
    String namespace,
    String templateName,
    List<String> patterns,
    Map<String, VariableConstraint> variables,
    List<StructuralSummary> structuralSummaries,
    MessageFormatSchema messageFormatSchema  // nullable — new
) {
    // Backward-compatible constructor without messageFormatSchema
    public TemplateSchema(String namespace, String templateName,
                           List<String> patterns,
                           Map<String, VariableConstraint> variables,
                           List<StructuralSummary> structuralSummaries) {
        this(namespace, templateName, patterns, variables,
             structuralSummaries, null);
    }

    public TemplateSchema {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(templateName, "templateName");
        Objects.requireNonNull(patterns, "patterns");
        Objects.requireNonNull(variables, "variables");
        patterns = List.copyOf(patterns);
        variables = Collections.unmodifiableMap(new LinkedHashMap<>(variables));
        structuralSummaries = structuralSummaries != null
            ? List.copyOf(structuralSummaries) : List.of();
    }
}
```

## InMemoryTemplateRegistry — Minimal Change (fail-fast guard only)

`InMemoryTemplateRegistry` is a Declaration Primitive (#959 D10) in `template-registry-api`. No constructor changes, no resolver parameter. MF2 generation is handled by `NlpTemplateRegistry` in the extension module, following the established subclass extension pattern (NLP matching, LLM classification).

`InMemoryTemplateRegistry.generate()` gains a **fail-fast guard** — if the requested template has `messageFormat != null`, it throws `IllegalStateException` rather than silently slot-filling from `patterns[0]`. Silent slot-fill fallback would produce grammatically incorrect output (e.g., "1 items" instead of "1 item") with no error:

```java
@Override
public String generate(String namespaceName, String templateName,
                        Map<String, String> values) {
    // ... existing namespace/template lookup ...
    if (def.messageFormat() != null) {
        throw new IllegalStateException(
            "Template '" + templateName + "' in namespace '"
            + namespaceName + "' declares messageFormat but "
            + "InMemoryTemplateRegistry does not support MF2 generation. "
            + "Use NlpTemplateRegistry with a MessageFormatResolver.");
    }
    return TemplateGenerator.generate(def, values);
}
```

This is consistent with the spec's fail-fast philosophy — MF2 templates without a resolver are configuration errors, not silent degradation.

## NlpTemplateRegistry Changes

### Constructor

```java
public NlpTemplateRegistry(NlpTokenizerFactory tokenizerFactory,
                             LanguageFamilyRegistry familyRegistry,
                             Map<String, MatchingStrategy> strategies,
                             double scoreThreshold,
                             MessageFormatResolver resolver) {  // nullable
    super(DEFAULT_MAX_RETAINED_VERSIONS);
    // ... existing field assignments ...
    this.messageFormatResolver = resolver;
}
```

### Registration — MF2 Compilation

In `buildNamespaceState()`, after strategy index building:

```java
// Compile MF2 patterns at registration time (D5)
Map<String, MessageFormatResolver.CompiledMessage> compiledMf2 = new LinkedHashMap<>();
for (TemplateDefinition def : namespace.templates().values()) {
    if (def.messageFormat() != null) {
        if (messageFormatResolver == null) {
            throw new IllegalStateException(
                "Template '" + def.name() + "' in namespace '"
                + namespace.name() + "' declares messageFormat but no "
                + "MessageFormatResolver is configured. "
                + "Add template-registry-mf2 to the classpath.");
        }
        compiledMf2.put(def.name(),
            messageFormatResolver.compile(def.messageFormat(), lang));
    }
}
```

Compiled handles are stored in `NamespaceState`:

```java
private record NamespaceState(
    NlpTokenizer tokenizer,
    MatchingStrategy strategy,
    MatchingStrategy.Index index,
    Map<String, MessageFormatResolver.CompiledMessage> compiledMf2  // template name → compiled handle
) {}
```

### generate() Override

```java
@Override
public String generate(String namespaceName, String templateName,
                        Map<String, String> values) {
    NamespaceState state = namespaceStates.get(namespaceName);
    if (state != null && state.compiledMf2().containsKey(templateName)) {
        return messageFormatResolver.format(
            state.compiledMf2().get(templateName), values);
    }
    return super.generate(namespaceName, templateName, values);
}
```

### buildSchema() Override

```java
@Override
protected TemplateSchema buildSchema(String namespaceName,
                                      TemplateDefinition def) {
    NamespaceState state = namespaceStates.get(namespaceName);
    List<TemplateSchema.StructuralSummary> summaries = state != null
        ? state.strategy().structuralSummaries(state.index(), def.name())
        : List.of();

    MessageFormatSchema mfSchema = null;
    if (def.messageFormat() != null && messageFormatResolver != null) {
        mfSchema = messageFormatResolver.extractSchema(def.messageFormat());
    }

    return new TemplateSchema(namespaceName, def.name(),
        def.patterns(), def.variables(), summaries, mfSchema);
}
```

## IcuMessageFormatResolver Implementation

Lives in `template-registry-mf2`. Wraps ICU4J's `com.ibm.icu.message2.MessageFormatter`.

```java
package io.casehub.template.registry.mf2;

import com.ibm.icu.message2.MessageFormatter;
import io.casehub.template.registry.MessageFormatResolver;
import io.casehub.template.registry.MessageFormatSchema;

import java.util.Locale;
import java.util.Map;

public class IcuMessageFormatResolver implements MessageFormatResolver {

    private record IcuCompiledMessage(
        MessageFormatter formatter
    ) implements CompiledMessage {}

    @Override
    public CompiledMessage compile(String pattern, String languageTag) {
        Locale locale = Locale.forLanguageTag(languageTag);
        if (locale.getLanguage().isEmpty()) {
            throw new IllegalArgumentException(
                "Invalid BCP 47 language tag: '" + languageTag + "'");
        }
        // Parse and validate at registration time — throws on syntax errors
        MessageFormatter formatter = MessageFormatter.builder()
            .setPattern(pattern)
            .setLocale(locale)
            .build();
        return new IcuCompiledMessage(formatter);
    }

    @Override
    public String format(CompiledMessage compiledPattern,
                          Map<String, String> values) {
        IcuCompiledMessage compiled = (IcuCompiledMessage) compiledPattern;
        Map<String, Object> typedValues = coerceValues(compiled.formatter(), values);
        return compiled.formatter().formatToString(typedValues);
    }

    @Override
    public MessageFormatSchema extractSchema(String pattern) {
        // Extract selectors and variant keys from the parsed MF2 pattern.
        // Implementation parses the MF2 data model to find .match selectors
        // and when-clause keys.
        // ...
    }

    private Map<String, Object> coerceValues(MessageFormatter formatter,
                                              Map<String, String> values) {
        // Extract function annotations (:integer, :number, etc.) from the
        // compiled pattern's data model. Coerce string values to appropriate
        // Java types. Throw IllegalArgumentException on coercion failure with
        // diagnostic message: variable name, declared type, actual value.
        // ...
    }
}
```

### Type Coercion Rules

| MF2 Function | Java Target Type | Coercion |
|---|---|---|
| `:string` | `String` | No coercion needed |
| `:number` | `Double` | `Double.parseDouble(value)` |
| `:integer` | `Long` | `Long.parseLong(value)` |
| `:date` | `java.time.LocalDate` | `LocalDate.parse(value)` (ISO 8601) |
| `:time` | `java.time.LocalTime` | `LocalTime.parse(value)` (ISO 8601) |
| `:datetime` | `java.time.LocalDateTime` | `LocalDateTime.parse(value)` (ISO 8601) |

Variables without function annotations are passed as strings. All coercion failures throw `IllegalArgumentException` with a diagnostic message: `"Variable 'count' declared as :integer but value 'abc' is not parseable"`. No partial results — if any variable fails coercion, the entire `format()` call fails.

## YAML Declaration Syntax

MF2 templates use a `message-format:` sibling key alongside `patterns:` in YAML template definitions:

```yaml
templates:
  vote:
    patterns:
      - "I ${decision} this ${subject}[ because ${rationale}]"
    variables:
      decision:
        allowed-values: [approve, reject, defer]

  item-count:
    patterns:
      - "${count} items have been ${action}"
    message-format: |
      .match {$count :integer}
      1 {{{$count} item has been {$action}}}
      * {{{$count} items have been {$action}}}
    variables:
      count:
        type: INTEGER
      action:
        allowed-values: [approved, rejected, deferred]

  assignment:
    patterns:
      - "${agent} assigned to ${entity}"
    message-format: |
      .match {$gender :string}
      masculine {{{$agent} has been assigned to {$entity}. He will begin shortly.}}
      feminine {{{$agent} has been assigned to {$entity}. She will begin shortly.}}
      * {{{$agent} has been assigned to {$entity}. They will begin shortly.}}
    variables:
      agent: {}
      entity: {}
      gender:
        allowed-values: [masculine, feminine, other]
```

**Key points:**
- `patterns:` for matching (regex + NLP structural matching) — unchanged
- `message-format:` for generation — optional, absent means slot-fill
- YAML block scalar (`|`) preserves MF2 multiline syntax
- Variables referenced in `message-format` should also appear in `variables:` for constraint validation
- Variables in `message-format` that are not in `patterns:` (like `gender` above) are generation-only — they're provided at generate() time but not extracted from matched input

## CDI Wiring

### NlpTemplateRegistryProducer (modified)

```java
@ApplicationScoped
public class NlpTemplateRegistryProducer {

    @Produces
    @ApplicationScoped
    public NlpTemplateRegistry produce(
            Instance<MatchingStrategy> strategies,
            Instance<NlpTokenizerFactory> factories,
            Instance<MessageFormatResolver> resolvers,  // new
            LanguageFamilyRegistry familyRegistry) {

        // ... existing strategy/factory wiring ...

        MessageFormatResolver resolver = resolvers.isUnsatisfied()
            ? null : resolvers.get();

        double scoreThreshold = 0.5;
        return new NlpTemplateRegistry(
                compositeFactory, familyRegistry, strategyMap,
                scoreThreshold, resolver);
    }
}
```

### IcuMessageFormatResolver CDI bean

```java
// In template-registry-mf2
@ApplicationScoped
public class IcuMessageFormatResolver implements MessageFormatResolver {
    // ...
}
```

Adding `template-registry-mf2` to the classpath provides the bean. CDI `Instance<MessageFormatResolver>` resolves it. No configuration needed. The base class `InMemoryTemplateRegistry` is unchanged — MF2 support is entirely in `NlpTemplateRegistry` and its CDI producer.

**Note:** `NlpTemplateRegistryProducer` is new CDI infrastructure — it does not exist in the codebase yet (#1013 filed for CDI producer wiring). This issue creates it as part of the MF2 integration. The producer is the natural home for assembling the registry from discovered SPIs (strategies, factories, resolver).

### AdaptiveTemplateRegistry Impact

`AdaptiveTemplateRegistry` extends `NlpTemplateRegistry`. Its constructor calls `super(tokenizerFactory, familyRegistry, strategies, scoreThreshold)`. The 5-arg constructor (adding `MessageFormatResolver`) requires `AdaptiveTemplateRegistry` to accept and forward the resolver:

```java
public AdaptiveTemplateRegistry(NlpTokenizerFactory tokenizerFactory,
                                 LanguageFamilyRegistry familyRegistry,
                                 Map<String, MatchingStrategy> strategies,
                                 double scoreThreshold,
                                 MessageFormatResolver resolver) {
    super(tokenizerFactory, familyRegistry, strategies,
          scoreThreshold, resolver);
    // ...
}
```

The 4-arg convenience constructor is preserved, delegating with `resolver = null`. This is a mechanical change — `AdaptiveTemplateRegistry` does not add MF2-specific behavior.

### VariationSeedingService Impact

`VariationSeedingService.expandWithVariations()` constructs `TemplateDefinition` from existing definitions. The new `messageFormat` field must be carried forward:

```java
new TemplateDefinition(def.name(), allPatterns, def.variables(), def.messageFormat())
```

Similarly, `AdaptiveTemplateRegistry.registerLearnedVariation()` and any code that reconstructs `TemplateDefinition` from an existing one must carry the `messageFormat` field.

## ICU4J Dependency

**Artifact:** `com.ibm.icu:icu4j:79.1`
**Size:** ~14 MB
**API status:** MF2 spec is stable (CLDR 47, March 2025). ICU4J MF2 API is at Draft status (promoted from tech preview in ICU 78). The `MessageFormatResolver` SPI insulates the platform from API changes — if ICU4J's API evolves, only the adapter internals change.
**Custom functions:** Built-in functions only (`:string`, `:number`, `:integer`, `:date`, `:time`, `:datetime`). In MF2, pluralization is handled by `.match {$count :integer}` — the `:integer` function provides plural category selection automatically. Gender selection uses `.match {$gender :string}`. Ordinal selection uses `:integer select=ordinal`. There are no separate `:plural`, `:select`, or `:selectordinal` functions in MF2 (those are MF1 concepts). Custom function extension point deferred — no concrete use case exists.

## Thread Safety

`IcuMessageFormatResolver` is stateless and thread-safe. `compile()` returns a pre-built `MessageFormatter` that is stored in `NamespaceState.compiledMf2`. ICU4J's `MessageFormatter.formatToString()` is documented as thread-safe for concurrent reads. The compiled `MessageFormatter` is immutable after construction — locale and pattern are bound at compile time, only values vary at format time. If thread-safety concerns arise with a future ICU4J version, the adapter can clone or rebuild per-call without changing the SPI.

## Scope

### In scope
- `MessageFormatResolver` SPI in `template-registry-api`
- `messageFormat` field on `TemplateDefinition`
- `MessageFormatSchema` and `SelectorInfo` schema types
- `TemplateSchema` extension with `messageFormatSchema`
- `template-registry-mf2` module with ICU4J 79.1
- `IcuMessageFormatResolver` implementation
- Type coercion from `Map<String, String>` per MF2 function annotations
- Eager MF2 pattern validation at registration time
- `InMemoryTemplateRegistry.generate()` fail-fast guard for MF2 templates
- `NlpTemplateRegistry.generate()` dispatch (MF2 or slot-fill)
- CDI wiring via `Instance<MessageFormatResolver>`
- YAML `message-format:` declaration syntax
- Unit and integration tests

### Out of scope
- Custom MF2 function registry (deferred — YAGNI)
- Notification pipeline integration (separate concern, separate issue)
- Consumer migration to MF2 templates (adoption is incremental)
- MF2 message catalogs / resource bundles (CLDR data is embedded in ICU4J)

## References

- `template-registry-api/src/main/java/io/casehub/template/registry/TemplateDefinition.java` — template type being extended
- `template-registry-api/src/main/java/io/casehub/template/registry/TemplateGenerator.java` — existing slot-fill generation
- `template-registry-api/src/main/java/io/casehub/template/registry/InMemoryTemplateRegistry.java` — generate() dispatch point
- `template-registry-api/src/main/java/io/casehub/template/registry/UnifiedTemplateRegistry.java` — registry SPI
- `template-registry-api/src/main/java/io/casehub/template/registry/TemplateSchema.java` — schema type being extended
- `template-registry-api/src/main/java/io/casehub/template/registry/MatchingStrategy.java` — SPI pattern reference
- `template-registry/src/main/java/io/casehub/template/registry/nlp/NlpTemplateRegistry.java` — NLP registry with strategy delegation
- `template-registry-nlp-svo/` — module structure reference for classpath-activated extension
- ICU4J 79.1 `MessageFormatter` API (Draft status since ICU 78)
- CLDR 47 MessageFormat 2.0 specification (stable since March 2025)
- Issue #959 spec — unified template registry design (prerequisite)
- Issue #965 spec — multi-language NLP tokenizer design (MatchingStrategy SPI reference)
- Decisions: `specs/issue-963-icu-messageformat-2/decisions.md` (D1–D9)
