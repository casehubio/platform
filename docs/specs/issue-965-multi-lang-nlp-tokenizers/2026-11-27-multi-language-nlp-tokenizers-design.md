# Multi-Language NLP Tokenizer Models for Template Matching

**Issue:** casehubio/platform#965
**Epic:** #943
**Scale:** M | **Complexity:** Med (revised from L/High after scope narrowing)

## Overview

Extend the unified template registry (#959) with multi-language NLP support. The design introduces three SPIs — `MatchingStrategy`, `NlpTokenizerFactory`, and `LanguageFamilyRegistry` — that decouple language-specific matching algorithms from the registry infrastructure. The initial implementation ships the SVO family strategy (English, French, Spanish, Italian, Portuguese) while the SPI design is informed by all language families.

The existing `StructuralMatcher`, `DiscriminationTrie`, `NlpTemplateCompiler`, and `LemmatisedKeywordFilter` move from `template-registry` to `template-registry-nlp-svo` — they are SVO implementation details, not registry infrastructure. Per-language OpenNLP models provide tokenization, POS tagging, and lemmatization for each supported language. CJK, agglutinative (Turkish, Finnish), and VSO (Arabic) families are deferred to separate issues — they require specialized NLP libraries, not OpenNLP.

## Scope Boundaries

**In scope:**
- `MatchingStrategy` SPI in `template-registry-api`
- `NlpTokenizerFactory` SPI in `template-registry-api`
- `LanguageFamilyRegistry` in `template-registry-api`
- SVO family strategy implementation in `template-registry-nlp-svo` module
- Per-language stop word sets in `template-registry-nlp-svo`
- OpenNLP model loading for French, Spanish, Italian, Portuguese
- `NlpTemplateRegistry` refactoring to delegate through the strategy layer
- CDI wiring for strategy discovery and activation

**Out of scope (future issues):**
- CJK matching strategy (requires Kuromoji, HanLP/Jieba — different segmentation paradigm)
- Agglutinative matching strategy (requires Zemberek, HFST/Voikko — morpheme decomposition)
- VSO matching strategy (Arabic — requires right-to-left token handling)
- German compound splitting (classification pending: may fit SVO-with-decompounding or separate strategy)

## Architecture

### Type Hierarchy

```
template-registry-api (zero-dep)
├── NlpTokenizer (existing @FunctionalInterface)
├── AnnotatedToken (existing record)
├── MatchingStrategy (new SPI — opaque Index, strategy owns compilation)
├── NlpTokenizerFactory (new SPI)
└── LanguageFamilyRegistry (new registry)

template-registry (existing, Quarkus CDI)
└── NlpTemplateRegistry (modified — delegates to MatchingStrategy)

template-registry-nlp-svo (new module)
├── SvoMatchingStrategy implements MatchingStrategy
├── OpenNlpTokenizerFactory implements NlpTokenizerFactory
├── SvoStopWords (per-language stop word sets)
├── StructuralMatcher (moved from template-registry)
├── DiscriminationTrie (moved from template-registry)
├── NlpTemplateCompiler (moved from template-registry)
└── LemmatisedKeywordFilter (moved from template-registry)
```

### MatchingStrategy SPI

Lives in `template-registry-api`. Each language family implements this interface.

```java
package io.casehub.template.registry;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface MatchingStrategy {

    String familyId();

    Index buildIndex(TemplateNamespace namespace, NlpTokenizer tokenizer);

    List<PatternRef> selectCandidates(Index index,
                                      List<AnnotatedToken> inputTokens);

    Optional<MatchResult> match(Index index, PatternRef candidate,
                                 List<AnnotatedToken> inputTokens);

    // --- Diagnostics (default implementations for strategies that don't support tracing) ---

    record TraceableMatchResult(Optional<MatchResult> result,
                                 List<ElementTrace> elements) {}

    default TraceableMatchResult matchWithTrace(Index index, PatternRef candidate,
                                                 List<AnnotatedToken> inputTokens) {
        Optional<MatchResult> result = match(index, candidate, inputTokens);
        return new TraceableMatchResult(result, List.of());
    }

    default List<CandidateTrace> traceAll(Index index,
                                           String namespaceName,
                                           List<AnnotatedToken> inputTokens,
                                           double scoreThreshold) {
        return List.of();
    }

    // --- Schema introspection ---

    default List<TemplateSchema.StructuralSummary> structuralSummaries(
            Index index, String templateName) {
        return List.of();
    }

    interface Index {}

    record PatternRef(String templateName, int patternIndex) {}

    record MatchResult(Map<String, String> fields, double score) {}
}
```

**Design rationale:** The strategy receives `TemplateNamespace` (which contains `TemplateDefinition` objects with patterns and variable constraints) and an `NlpTokenizer`. It performs its own compilation into whatever internal format it needs and returns an opaque `Index` handle. The registry stores this handle and passes it back for candidate selection and matching. This design:

- **Eliminates SVO leakage into the API.** No `StructuralElement` or `CompiledNlpTemplate` in the SPI. Each family compiles templates into its own internal format — SVO uses sequential-walk elements; CJK would use segment boundaries; agglutinative would use morpheme decomposition.
- **Strategy owns compilation end-to-end.** Compilation, indexing, candidate selection, and matching are all strategy-internal. The registry never touches NLP-specific intermediate representations.
- **Eliminates dual state ownership.** The `Index` is an opaque object owned by the registry's `NamespaceState`. When the registry deregisters a namespace, the `Index` is simply removed from the map and garbage collected. No `removeIndex()` lifecycle coordination needed.
- **Variable constraints are inside the index.** The strategy receives `TemplateDefinition.variables()` during `buildIndex` and stores them in its index. The `match` method does not need a separate constraints parameter — it looks them up from the index via `PatternRef`.
- **Diagnostics are opt-in.** `matchWithTrace()` returns per-candidate element trace data (default: empty trace). `traceAll()` produces a full diagnostic sweep with keyword affinity, outcome classification, and element traces (default: empty list). `structuralSummaries()` provides schema introspection data for the `@McpDomain("template-schemas")` endpoint (default: empty list). Strategies override when they support diagnostics — SVO implements all three.

### NlpTokenizerFactory SPI

```java
package io.casehub.template.registry;

import java.util.Set;

public interface NlpTokenizerFactory {

    NlpTokenizer create(String languageTag);

    Set<String> supportedLanguages();
}
```

**Contract:**
- `languageTag` uses BCP 47 language tags (e.g., `"en"`, `"fr"`, `"zh-TW"`, `"pt-BR"`)
- Returned `NlpTokenizer` instances must be safe for concurrent use. The existing `OpenNlpTokenizer` pattern (shared heavyweight model objects, per-call ME instances) is the reference design.
- `supportedLanguages()` returns only languages whose models are actually available on the classpath at runtime. The factory checks model availability at construction time.
- `create()` throws `UnsupportedOperationException` for language codes not in `supportedLanguages()`.
- Model loading may be slow (seconds). Callers should warm up at startup if latency-sensitive.

### LanguageFamilyRegistry

```java
package io.casehub.template.registry;

import java.util.Optional;

public class LanguageFamilyRegistry {

    public Optional<String> familyFor(String languageTag) { ... }

    public void register(String languageTag, String familyId) { ... }
}
```

**Default mappings (SVO scope):**

| Language Tag | Family ID |
|---|---|
| `en` | `svo` |
| `fr` | `svo` |
| `es` | `svo` |
| `it` | `svo` |
| `pt` | `svo` |

Future mappings are added when their family implementations ship. The registry is extensible at runtime for custom language-to-family bindings.

The language-to-family mapping is a policy decision (e.g., German could be SVO-with-decompounding or a separate family), not derivable from factory/strategy registrations. The CDI producer validates at startup that every registered language has a corresponding tokenizer factory entry — see §CDI Wiring.

## SVO Family Strategy

### SvoMatchingStrategy

Wraps the existing `StructuralMatcher` and `DiscriminationTrie` as its internal matching and candidate selection mechanisms. The SVO strategy is the sequential-walk algorithm that works for English and Romance languages — languages with Subject-Verb-Object word order and space-delimited tokens.

The strategy owns compilation, indexing, and both candidate selection paths.

**`buildIndex`:** Receives a `TemplateNamespace` and `NlpTokenizer`. Compiles each template pattern via `NlpTemplateCompiler` (which uses `StructuralMatcher.compile()` and `LemmatisedKeywordFilter.extract()` with per-language stop words from `SvoStopWords`). Conditionally builds a `DiscriminationTrie` from the compiled elements based on template count threshold. Returns an opaque `SvoIndex` containing compiled templates, keyword sets, optional trie, and stop words.

**`selectCandidates` (dual-path design):** The existing two-path candidate selection is preserved inside the strategy:
1. **Trie path** (when total patterns ≥ `trieActivationThreshold`, default 100): `DiscriminationTrie.walk(inputTokens)` — fast positional lemma lookup
2. **Keyword filtering path** (below threshold): iterate compiled templates, compute `LemmatisedKeywordFilter.affinity(keywords, inputTokens, stopWords)`, skip candidates below `affinityThreshold`

This matches the existing `NlpTemplateRegistry.matchInNamespace()` behavior — most real registries (10–50 templates per protocol, per #959 D1) use keyword filtering, not the trie.

**`match`:** Performs the sequential-walk structural matching (same algorithm as today's `StructuralMatcher.match()`). Lemma comparison is case-insensitive. Optional elements are skipped when unmatched. Resolves variable constraints from the index — the caller does not need to pass constraints separately. Returns `Optional<MatchResult>` with the raw score — the registry applies the score threshold.

**`matchWithTrace`:** Delegates to `StructuralMatcher.matchWithTrace()` to produce per-element trace data (`List<ElementTrace>` with expected/actual lemma, match outcome per element). Returns `TraceableMatchResult` with both the match result and element-level diagnostics.

**`traceAll`:** Performs the full diagnostic sweep used by `matchInNamespaceWithTrace`. Iterates ALL templates in the index (not just candidates), computes keyword affinity for each pattern, runs `matchWithTrace` for non-filtered candidates, and classifies outcomes using the passed `scoreThreshold`:
- `score > scoreThreshold` → `CandidateOutcome.MATCHED`
- `score > 0` but `≤ scoreThreshold` → `CandidateOutcome.BELOW_THRESHOLD`
- Affinity below threshold → `CandidateOutcome.KEYWORD_FILTERED`
- No structural match → `CandidateOutcome.STRUCTURAL_FAILED`

**`structuralSummaries`:** Returns `List<TemplateSchema.StructuralSummary>` for a template, extracted from the compiled elements in the index. Used by the `@McpDomain("template-schemas")` endpoint.

```java
record SvoIndex(
    Map<String, SvoCompiledTemplate> compiledTemplates,
    DiscriminationTrie trie,              // null when below threshold
    Set<String> stopWords,
    double affinityThreshold,
    int trieActivationThreshold
) implements MatchingStrategy.Index {}
```

### OpenNlpTokenizerFactory

Extends the existing `OpenNlpTokenizer` pattern to support multiple languages. Each language requires three OpenNLP UD models:

| Model Type | English Example | French Example |
|---|---|---|
| Tokenizer | `opennlp-en-ud-ewt-tokens-1.3-2.5.4.bin` | `opennlp-fr-ud-gsd-tokens-1.3-2.5.4.bin` |
| POS tagger | `opennlp-en-ud-ewt-pos-1.3-2.5.4.bin` | `opennlp-fr-ud-gsd-pos-1.3-2.5.4.bin` |
| Lemmatizer | `opennlp-en-ud-ewt-lemmas-1.3-2.5.4.bin` | `opennlp-fr-ud-gsd-lemmas-1.3-2.5.4.bin` |

**Model availability:** `supportedLanguages()` only returns languages whose three model files are found on the classpath at construction time. This prevents runtime failures from missing models — if a consumer adds `template-registry-nlp-svo` without French model artifacts, French is simply not in `supportedLanguages()` and `create("fr")` throws `UnsupportedOperationException`. The error message indicates the specific missing model files.

**Model caching:** Models are cached per language. For the SVO scope (5 languages, ~15 models, ~150-300 MB), unbounded caching is acceptable. The factory documents that model loading takes seconds — callers warm up at startup if needed. LRU eviction is a future concern when language count grows.

**Thread safety:** The factory returns thread-safe `NlpTokenizer` instances. OpenNLP model objects (`TokenizerModel`, `POSModel`, `LemmatizerModel`) are shared; the per-call tool instances (`TokenizerME`, `POSTaggerME`, `LemmatizerME`) are created per invocation.

### Per-Language Stop Words

The SVO strategy module provides per-language stop word sets:

```java
public final class SvoStopWords {

    public static Set<String> forLanguage(String languageTag) { ... }
}
```

Stop word sets sourced from established NLP corpora (Lucene analyzers, NLTK, scikit-learn).

`LemmatisedKeywordFilter` moves to `template-registry-nlp-svo`. The hardcoded English `STOP_WORDS` constant and the old overloads that use it (`extract(String, NlpTokenizer)` and `affinity(Set<String>, List<AnnotatedToken>)`) are removed. Stop words are always a required parameter:

```java
public static Set<String> extract(String template, NlpTokenizer tokenizer,
                                   Set<String> stopWords) { ... }

public static double affinity(Set<String> keywords, List<AnnotatedToken> inputTokens,
                               Set<String> stopWords) { ... }
```

`extractRaw(String)` is removed — it has zero production callers (1 test caller only) and uses the hardcoded `STOP_WORDS` constant that is being deleted. The parameterised `extract(String, NlpTokenizer, Set<String>)` is strictly superior. The synonym-aware `affinity(Set, List, Map)` overload is retained as-is — it is already parameterised and does not use hardcoded stop words.

## NlpTemplateRegistry Changes

The registry is refactored from holding a single `NlpTokenizer` to resolving per-namespace through the strategy and factory layers. With the opaque index design, the registry performs no NLP-specific work — it coordinates between SPIs.

### Constructor Change

```java
// Before
public NlpTemplateRegistry(NlpTokenizer tokenizer) { ... }

// After
public NlpTemplateRegistry(NlpTokenizerFactory tokenizerFactory,
                            LanguageFamilyRegistry familyRegistry,
                            Map<String, MatchingStrategy> strategies,
                            double scoreThreshold) { ... }
```

The strategies map is keyed by family ID. `scoreThreshold` (default 0.5) determines whether a structural match is accepted (`score > scoreThreshold` → `Matched`) or tracked as a near miss (`score > 0` but `≤ scoreThreshold` → `NearMiss`). The old single-tokenizer constructor is removed — all 14 usages are in test classes within `template-registry` and are mechanical updates to the new constructor.

### Namespace Registration

On `register(TemplateNamespace)`:
1. Resolve the namespace's language tag via `familyRegistry.familyFor(namespace.language())` — throws `IllegalArgumentException` if the language is not registered (message indicates the unsupported language and lists available families)
2. Resolve the `MatchingStrategy` for the family — throws `IllegalArgumentException` if no strategy is registered for the family
3. Resolve the `NlpTokenizer` via `tokenizerFactory.create(namespace.language())`
4. Call `strategy.buildIndex(namespace, tokenizer)` — the strategy compiles templates, builds keyword sets, and conditionally constructs its discrimination index
5. Store the per-namespace state: tokenizer, strategy, and opaque `Index`

Fail-fast at registration time — no silent fallback to regex-only matching for unconfigured languages. Silent degradation (some namespaces get NLP, others silently don't) is a debugging nightmare. If a language needs NLP matching, the caller should configure it explicitly.

On `deregister(String namespaceName)`:
1. Remove the `NamespaceState` from the map — the opaque `Index` is garbage collected with no strategy-side cleanup needed.

### Matching

On `matchInNamespace(...)`:
1. Look up the namespace's `NamespaceState`
2. Tokenize input with the namespace's tokenizer
3. Use the strategy's `selectCandidates(index, inputTokens)` to get candidate patterns
4. For each candidate, use the strategy's `match(index, candidate, inputTokens)` for structural matching
5. Apply `scoreThreshold`: if `score > scoreThreshold` → `Matched`; if `score > 0` but `≤ scoreThreshold` → track as near-miss candidate (best near-miss wins)
6. If no `Matched` but have a near-miss → return `NearMiss` (using pattern text from `TemplateNamespace.templates()` via the superclass)
7. Otherwise → `Unmatched`

On `matchInNamespaceWithTrace(...)`:
1. Collect regex candidates from `super.matchInNamespaceWithTrace(...)`
2. Tokenize input
3. Call `strategy.traceAll(index, namespaceName, inputTokens, scoreThreshold)` — the strategy performs the full diagnostic sweep: iterates all templates, computes keyword affinity, runs structural matching with element trace, classifies outcomes (`MATCHED`, `BELOW_THRESHOLD`, `KEYWORD_FILTERED`, `STRUCTURAL_FAILED`)
4. Combine regex and NLP trace candidates

On `buildSchema(namespaceName, def)`:
1. Call `strategy.structuralSummaries(index, def.name())` to retrieve compiled structural summaries
2. Build `TemplateSchema` with patterns, variables, and summaries

### Per-Namespace State

```java
private record NamespaceState(
    NlpTokenizer tokenizer,
    MatchingStrategy strategy,
    MatchingStrategy.Index index
) {}

private final ConcurrentMap<String, NamespaceState> namespaceStates = new ConcurrentHashMap<>();
```

## CDI Wiring

The multi-language registry uses the platform's CDI displacement pattern (ARC42STORIES §4).

**Strategy beans:** Each strategy module provides an `@ApplicationScoped` bean:

```java
// In template-registry-nlp-svo
@ApplicationScoped
public class SvoMatchingStrategy implements MatchingStrategy {
    public String familyId() { return "svo"; }
    // ...
}
```

**Tokenizer factory beans:** Each strategy module provides its factory:

```java
// In template-registry-nlp-svo
@ApplicationScoped
public class OpenNlpTokenizerFactory implements NlpTokenizerFactory {
    // ...
}
```

**Registry producer:** A CDI producer in `template-registry` assembles the registry from discovered beans:

```java
@ApplicationScoped
public class NlpTemplateRegistryProducer {

    @Produces
    @ApplicationScoped
    public NlpTemplateRegistry produce(
            Instance<MatchingStrategy> strategies,
            Instance<NlpTokenizerFactory> factories,
            LanguageFamilyRegistry familyRegistry) {

        Map<String, MatchingStrategy> strategyMap = new HashMap<>();
        for (MatchingStrategy s : strategies) {
            strategyMap.put(s.familyId(), s);
        }

        NlpTokenizerFactory compositeFactory =
                new CompositeNlpTokenizerFactory(factories);

        double scoreThreshold = 0.5; // configurable via MicroProfile Config
        return new NlpTemplateRegistry(
                compositeFactory, familyRegistry, strategyMap, scoreThreshold);
    }
}
```

**Startup validation:** The producer validates at startup:
- Every language in `LanguageFamilyRegistry` has a matching entry in a tokenizer factory's `supportedLanguages()`
- Every family ID in `LanguageFamilyRegistry` has a corresponding `MatchingStrategy` bean

Validation failures throw at startup — fail fast, not at first namespace registration.

**Activation pattern:** Adding `template-registry-nlp-svo` to the classpath provides `SvoMatchingStrategy` and `OpenNlpTokenizerFactory` as CDI beans. The producer discovers them via `Instance<MatchingStrategy>` injection. No configuration changes needed — this follows the platform's opt-in extension pattern.

**`LanguageFamilyRegistry` population:** The `LanguageFamilyRegistry` is populated via a CDI startup observer in `template-registry` that collects default mappings from configuration (e.g., `casehub.template.language-families.en=svo` in `application.properties`). Strategy modules provide default configuration via `src/main/resources/META-INF/microprofile-config.properties`.

## Module Structure

### template-registry-nlp-svo (new)

```
template-registry-nlp-svo/
├── pom.xml
└── src/main/java/io/casehub/template/registry/nlp/svo/
    ├── SvoMatchingStrategy.java
    ├── OpenNlpTokenizerFactory.java
    ├── SvoStopWords.java
    ├── SvoIndex.java
    ├── SvoCompiledTemplate.java
    ├── StructuralMatcher.java       (moved from template-registry)
    ├── DiscriminationTrie.java      (moved from template-registry)
    ├── NlpTemplateCompiler.java     (moved from template-registry)
    └── LemmatisedKeywordFilter.java (moved from template-registry)
```

**Dependencies:**
- `casehub-platform-template-registry-api` (compile)
- `org.apache.opennlp:opennlp-tools` (compile)
- Per-language OpenNLP model artifacts (optional compile — consumers add the languages they need)

### Existing module changes

**template-registry-api:**
- Add `MatchingStrategy` interface (with `Index`, `PatternRef`, `MatchResult`, `TraceableMatchResult`, default `matchWithTrace`/`traceAll`/`structuralSummaries`)
- Add `NlpTokenizerFactory` interface
- Add `LanguageFamilyRegistry` class

**template-registry:**
- Refactor `NlpTemplateRegistry` to delegate through strategy layer with opaque `Index`
- Remove old single-tokenizer constructor — all callers migrate to new constructor
- Remove `StructuralMatcher`, `DiscriminationTrie`, `NlpTemplateCompiler`, `LemmatisedKeywordFilter` (moved to SVO module)
- Add `NlpTemplateRegistryProducer` CDI producer
- Add `CompositeNlpTokenizerFactory` utility

## Relationship to AdaptiveFallbackMatcher

`MatchingStrategy` and `AdaptiveFallbackMatcher` are complementary, not overlapping:

- **MatchingStrategy** is the primary match pipeline — template compilation, candidate indexing, structural matching. It's per-family, deterministic, rule-based.
- **AdaptiveFallbackMatcher** is a secondary classification fallback for inputs that don't structurally match. It's probabilistic, language-aware (already takes a `language` parameter), and operates on pre-selected candidates.

Both can be language-aware. Neither subsumes the other. `AdaptiveFallbackMatcher` currently has zero usages in the codebase — it was designed but never connected.

**Disposition:** `AdaptiveFallbackMatcher` is retained as a future connection point for probabilistic fallback matching (a third tier after regex and structural matching). Its connection will be tracked as a separate issue. If no concrete use case materialises by the time the next language family ships, it should be removed as dead code.

## Testing Strategy

### Unit Tests

- `SvoMatchingStrategy` — verify match behavior matches existing `StructuralMatcher` tests when given English inputs; verify `matchWithTrace` returns element-level diagnostics; verify `traceAll` produces correct outcome classification (`MATCHED`/`BELOW_THRESHOLD`/`KEYWORD_FILTERED`/`STRUCTURAL_FAILED`); verify `structuralSummaries` returns compiled element data
- `OpenNlpTokenizerFactory` — verify tokenizer creation for each supported language, error on unsupported, classpath availability check
- `SvoStopWords` — verify stop word sets for each language
- `LanguageFamilyRegistry` — verify default mappings, runtime registration, unknown language handling
- `LemmatisedKeywordFilter` — verify stop-word-parameterised overloads (tests move with the class to SVO module)
- `NlpTemplateRegistryProducer` — verify startup validation catches missing strategies/factories

### Integration Tests

- Multi-language namespace registration and matching through `NlpTemplateRegistry`
- French templates matched with French input (using French tokenizer and stop words)
- English templates still match identically (backward compatibility)
- Cross-namespace matching with mixed languages
- Trie activation with per-language compiled templates
- Keyword filtering path with per-language stop words (below trie threshold)

### Corpus Tests

- Port existing English variation fixtures to validate no regression
- Create French variation fixtures for the same template patterns expressed in French

## Future Work

Each deferred family becomes its own issue (to be filed before spec acceptance):

| Family | Issue | Key Library | Algorithmic Challenge |
|---|---|---|---|
| CJK | TBD — file before acceptance | Kuromoji (Japanese), HanLP (Chinese) | No space boundaries — segmentation is the problem |
| Agglutinative | TBD — file before acceptance | Zemberek (Turkish), HFST (Finnish) | Single tokens encode multiple morphemes |
| VSO | TBD — file before acceptance | TBD | Verb-initial word order breaks sequential walk |
| Germanic | TBD — file before acceptance | OpenNLP + decompounding | Compound nouns, verb-final subclauses |
| AdaptiveFallbackMatcher connection | TBD — file before acceptance | N/A | Wire probabilistic fallback as third matching tier |

German is an open question: it may fit the SVO strategy with a decompounding tokenizer, or it may need a separate strategy. This classification is deferred until German template matching requirements are concretely defined.

## References

- `template-registry-api/src/main/java/io/casehub/template/registry/NlpTokenizer.java` — existing SPI
- `template-registry/src/main/java/io/casehub/template/registry/nlp/OpenNlpTokenizer.java` — existing English implementation
- `template-registry/src/main/java/io/casehub/template/registry/nlp/StructuralMatcher.java` — sequential-walk algorithm, moves to SVO module
- `template-registry/src/main/java/io/casehub/template/registry/nlp/DiscriminationTrie.java` — positional lemma trie, moves to SVO module
- `template-registry/src/main/java/io/casehub/template/registry/nlp/NlpTemplateCompiler.java` — template compilation, moves to SVO module
- `template-registry/src/main/java/io/casehub/template/registry/nlp/LemmatisedKeywordFilter.java` — keyword extraction/affinity, moves to SVO module
- `template-registry-api/src/main/java/io/casehub/template/registry/AdaptiveFallbackMatcher.java` — complementary SPI, zero usages, retained with disposition
- `template-registry-api/src/main/java/io/casehub/template/registry/TemplateNamespace.java` — `language` field already exists
- casehubio/platform#965 — issue body, algorithmic reality check
- casehubio/platform#959 — unified template registry (prerequisite)
- Decision review: `/Users/mdproctor/reviews/casehub-platform/issue-965-decision-20261127-174540/`
