# Decisions — YAML Parity Gaps (#370)

## D1: Parameter registry format — classpath listing file

**Choice:** APT emits `META-INF/simulation-parameters.properties` mapping each qualified method name to its parameter names and positions. Format: `spi.method=paramName:position,paramName:position`. Multiple JARs aggregate via classpath merging.
**Alternatives:**
- Generated Java class per SPI (like *QN) — type-safe, compile-time available, but harder to aggregate at runtime across multiple SPIs from different JARs. Per-SPI discovery adds complexity.
- Inline in decorator static initializer — tightly coupled, runtime must already be constructed when decorator CDI bean initializes.
**Rationale:** Follows the existing `simulation-eligible.txt` pattern. Flat key-value, aggregatable across JARs, simple runtime parsing. The factory loads all entries once at startup.
**Trade-offs:** Runtime parsing of a properties file vs compile-time type safety. Acceptable — the file is small and parsed once.
**Sources:** SimulationDecoratorProcessor.java (existing listing file pattern), META-INF/simulation-eligible.txt
**Exploration:** quick
**Status:** captured

## D2: Factory resolution — bare parameter names as extractor specs

**Choice:** `DeclarativeExtractorFactory` accepts a `ParameterRegistry` (loaded from the classpath listing). When a spec doesn't match `identity`, `field:*`, `composite:*`, or `rest-client`, the factory consults the registry: if the spec matches a parameter name for the given qualified name, it creates a positional extractor. Single-arg methods (position 0, total 1) → identity. Multi-arg methods → `((Object[])input)[position].toString()`.
**Alternatives:**
- Keep existing specs only — users write `field:accountId` for single-arg records or `identity` for primitives. Doesn't help multi-arg methods.
- Add `param:accountId` prefix — explicit prefix avoids ambiguity with field names, but adds ceremony for the 80% case.
**Rationale:** Bare names are the natural authoring unit in YAML — `key-extractor: accountId` reads like the SPI's method signature. The factory already has a prefix-dispatch pattern; bare names are the fallback after all prefixed specs fail, so there's no ambiguity.
**Trade-offs:** A bare name that matches both a parameter name and a field name on the input object will resolve to parameter-position extraction, not field extraction. Use `field:name` to force field extraction.
**Depends on:** D1 (parameter registry format)
**Sources:** DeclarativeExtractorFactory.java (existing prefix dispatch), SimulationDecoratorProcessor.java (parameter name access)
**Exploration:** quick
**Status:** captured

## D3: Corpus loader — extract into public YamlCorpusLoader

**Choice:** Extract the private corpus file loading logic from `YamlSimulationConfig` into a new public `YamlCorpusLoader` class in simulation-config-core. `YamlSimulationConfig` delegates to it. Public API: `load(InputStream)` and `loadFromPaths(List<String>)`, both returning `Map<String, List<InvocationRecord<Object, Object>>>`. Constructor accepts optional `defaultTenancyId`.
**Alternatives:**
- Make `loadExternalCorpusFiles()` public on `YamlSimulationConfig` — simpler, but exposes internal API and couples pages to the config class. Pages would need to construct a `YamlSimulationConfig` just to load corpus files.
- Have pages parse corpus YAML itself — duplicates logic, no reuse.
**Rationale:** Matches the class name pages already imports (`io.casehub.platform.simulation.config.YamlCorpusLoader`). Clean separation — corpus loading is a distinct concern from simulation config parsing. `YamlSimulationConfig` becomes thinner by delegating.
**Trade-offs:** One more public class. Justified by external consumer (pages) needs.
**Sources:** YamlSimulationConfig.java (loadExternalCorpusFiles, openStream), issue #370 §Gap 4
**Exploration:** quick
**Status:** revised (R1: generalized to CorpusLoader SPI per D4)

## D4: Format-independent corpus loading — CorpusLoader SPI

**Choice:** Define a `CorpusLoader` interface in simulation-config-core. Implementations dispatch by file extension. Ship two implementations: `YamlCorpusLoader` (existing logic, .yaml/.yml) and `CsvCorpusLoader` (.csv — using a simple built-in parser, not yaml-core's CsvParser to avoid a cross-module dependency). `YamlSimulationConfig`'s `corpus-files:` loading delegates to a `CompositeCorpusLoader` that iterates registered loaders.
**Alternatives:**
- YAML-only loader (D3 original) — simpler, but bakes in a format assumption that doesn't match real-world data. Financial and clinical domains commonly have CSV reference data.
- Plugin SPI via ServiceLoader — maximum extensibility, but adds discovery complexity for two implementations.
**Rationale:** Different industries have established data standards — CSV for finance/regulatory, JSON for APIs, YAML for config. Corpus data is domain data, not config, so it shouldn't be tied to the config format. The SPI makes this explicit while keeping the implementation minimal (two concrete loaders, extension dispatch).
**Trade-offs:** CSV corpus files need a convention for mapping flat rows to InvocationRecords (key column, tenancy-id column, input vs output columns). This convention is defined in the CSV format spec below.
**Depends on:** D3 (YamlCorpusLoader extraction)
**Sources:** yaml-core CsvParser (prior art), user feedback on industry data format independence
**Exploration:** quick
**Status:** captured
