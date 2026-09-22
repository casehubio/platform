# Spring Generator Constructor-Scanning Enhancement — Design Spec

**Issue:** casehubio/parent#504
**Epic:** casehubio/parent#501 (Spring deployment readiness)
**Date:** 2026-09-18

## Goal

Enhance the `spring-generator` Maven plugin to produce Spring auto-configurations for ALL beans, including those with CDI dependencies. Today the generator skips beans with `@ConfigMapping` params, `Instance<T>`, `@Inject` fields, or CDI qualifiers — exactly the beans that matter most. After this enhancement, every Spring auto-configuration module is fully generated with zero hand-written code. Drift between Quarkus and Spring modules becomes structurally impossible.

## Scope

Two complementary work streams:

1. **Generator enhancement** — teach `spring-generator` to follow `@Produces` return types to core POJO constructors and map constructor params to Spring equivalents
2. **Core module factory extractions** — move 4 pieces of wiring logic from Quarkus modules to core, making them framework-neutral and generator-compatible

### Out of scope

- CDI `@Decorator` translation (agent-gate `@Decorator` pattern — issue #500)
- JavaParser-based source translation (documented as evolution path, not needed today)
- New Spring modules beyond the 12 in scope (agent-gate deferred to #500)
- Jackson 3 migration (deferred to Quarkus 4 GA, ~Nov 2026)

## Architecture

### How construction-scanning works

The generator currently scans Quarkus modules for `@Produces` methods. When a method parameter has CDI-specific types (`Instance<T>`, `@ConfigMapping` types, CDI qualifiers), the scanner marks the bean as `requiresManualConfig()` and skips it.

**The insight:** The core extraction pattern guarantees that every CDI-coupled module has a `-core` counterpart with framework-neutral POJOs. The core POJO's constructor IS the framework-neutral contract. The generator should inspect that constructor, not the Quarkus `@Produces` method's parameters.

**Enhanced flow:**

```
@Produces method (Quarkus module)
    │
    ├── Metadata: bean name, conditionality (@DefaultBean → @ConditionalOnMissingBean),
    │   scope (@Alternative → @Primary), qualifiers (@DIDMethod)
    │
    └── Return type → Core POJO class
                          │
                          └── Constructor params (framework-neutral)
                                ├── Plain type T → @Bean param T (Spring auto-injects)
                                ├── List<T> → @Bean param List<T> (Spring auto-collects)
                                ├── Optional<T> → @Bean param ObjectProvider<T>
                                ├── (Consumer<T> → ApplicationEventPublisher — reserved, no current use)
                                ├── *Properties interface → @ConfigurationProperties record
                                └── lifecycle (@PreDestroy) → @Bean(destroyMethod=...)
```

### Scanner changes (JandexProducerScanner)

Current behavior: scans `@Produces` methods, sets `hasCdiDeps=true` and skips when CDI constructs detected.

New behavior:

1. **Scan `@Produces` as today** — discover beans, extract metadata (conditionality, scope, qualifiers)
2. **Follow return type** — resolve the `@Produces` method's return type to its class definition
3. **Resolve core types via existing composite index** — `AbstractGeneratorMojo.loadCompositeIndex()` already loads Jandex indexes from all compile-scope dependency JARs. The core module's JAR is a compile dependency of the `-spring` module, so its types are already in the composite index. No new plugin configuration needed. (Review R1-09.)
4. **Inspect constructor or factory** — check the return type for a `public static` method annotated with `@FactoryMethod` (new marker annotation in platform-api, framework-neutral). If found, use the factory method's parameters. Otherwise, find the public constructor (single-constructor convention). For each parameter:
   - If type has a `@ConfigMapping` supertype in the Quarkus Jandex index → flag as config property (generate `@ConfigurationProperties` record)
   - If type is `List<T>` → emit `List<T>` param (Spring auto-collects)
   - If type is `Optional<T>` → emit `ObjectProvider<T>` param with `.getIfAvailable()`
   - Otherwise → emit as `@Bean` method parameter (Spring injects matching bean)
5. **Detect lifecycle** — Spring's `@Bean` annotation defaults `destroyMethod` to `AbstractBeanDefinition.INFER_METHOD`, which auto-detects `close()` and `shutdown()` methods on the bean. No explicit lifecycle scanning needed for destroy methods. For **init methods**: scan the Quarkus declaring class for `@PostConstruct` methods that delegate to a core POJO method (e.g., `validateBinary()`) → emit `@Bean(initMethod = "validateBinary")`. Also detect `@Startup` on the declaring class → emit `@DependsOn` or eager initialization via `SmartInitializingSingleton`.
6. **Detect CDI qualifiers** — if the `@Produces` method has qualifier-annotated `Instance<T>` params (e.g., `@DIDMethod Instance<DIDResolver>`), propagate the qualifier to the generated `@Bean` method on the individual resolver beans. The composite's `@Bean` method is additionally marked `@Primary` — in Spring, custom-qualified beans are still eligible for unqualified injection points, so the composite must be `@Primary` to avoid `NoUniqueBeanDefinitionException` when other beans inject the unqualified type (e.g., `AgentIdentityVerificationService(DIDResolver)`). (D10, revised per review R1-01.)
7. **Propagate `@Priority` as `@Order`** — CDI `@Priority(N)` on individual beans controls ordering in `Instance<T>` collections. The generator emits `@Order(N)` on the corresponding `@Bean` methods to preserve iteration order in Spring's `List<T>` injection. Critical for composites like `CompositeDIDResolver` where resolver ordering determines resolution priority. (Review R1-04.)
8. **Never skip** — `requiresManualConfig()` should always return `false` after enhancement. The verify goal then covers ALL beans automatically.

### Writer changes (AutoConfigurationWriter → JavaPoet)

Migrate from string concatenation to JavaPoet (D6). Aligns with platform convention (rest-spring-generator, mcp-spring-generator, graphql-spring-generator all use JavaPoet).

**Output per module — two generated files:**

**1. `<Module>SpringAutoConfiguration.java`:**

```java
@AutoConfiguration
@ConditionalOnClass(CorePojoType.class)  // anchor on core module's primary type
@EnableConfigurationProperties(CorePojoSpringProperties.class)
public class AgentClaudeSpringAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ClaudeAgentClient claudeAgentClient(ClaudeAgentSpringProperties config) {
        return new ClaudeAgentClient(config);
    }

    @Bean
    @ConditionalOnMissingBean
    public ClaudeAgentProvider claudeAgentProvider(ClaudeAgentClient client) {
        return new ClaudeAgentProvider(client);
    }
}
```

**2. `<CorePojo>SpringProperties.java`** (when config Properties interface detected):

```java
@ConfigurationProperties(prefix = "casehub.platform.agent.claude")
public record ClaudeAgentSpringProperties(
    @DefaultValue("claude") String binaryPath,
    @DefaultValue("120s") Duration defaultTimeout,
    @DefaultValue("5") int maxConcurrentSessions
) implements ClaudeAgentProperties {}
```

- Record components derived from Properties interface methods
- `@DefaultValue` annotations derived from `@WithDefault` on the Quarkus `@ConfigMapping` extension interface
- Prefix derived from `@ConfigMapping(prefix=...)` on the extension interface

**@ConditionalOnClass anchor selection:** The anchor type is the core module's return type with the lexicographically earliest fully-qualified name across all `@Produces` methods in the module. Deterministic regardless of Jandex iteration order. This ensures the auto-configuration activates precisely when the core module is on the classpath. (Addresses review I2, R1-11.)

**@Primary limitation:** The generator maps CDI `@Alternative` to Spring `@Primary`. `@Primary` is binary (no priority ordering). This is safe under the platform's current mutual exclusion rules ("Do NOT combine X-inmem with X-jpa in the same scope"). If a future SPI requires multiple `@Alternative` producers at different priorities on the same classpath, the Spring mapping will need `@Order` or a custom `BeanFactoryPostProcessor`. Documented as a known limitation. (Review I4.)

### Core module factory extractions

Four factories move wiring logic from Quarkus modules to core. After extraction, every `@Produces` method becomes a pure constructor/factory call.

**D8: `AgentConfigLoader` (agent-config-core)**

Constructor-injected POJO (~7 dependencies). Single `load()` method encapsulates YAML discovery, profile resolution, ManifestLoader + ManifestProcessor orchestration. Both frameworks instantiate via constructor injection and call `load()` from their respective startup hooks (`@Observes StartupEvent` / `@EventListener ApplicationStartedEvent`).

The generator detects this pattern via a new `@StartupProducer` marker annotation on the Quarkus `@Produces` method (or by convention: a `@Startup`-annotated declaring class). When detected, the generator emits an `@EventListener(ApplicationStartedEvent.class)` method that calls `load()` on the injected `AgentConfigLoader` and publishes the `ManifestResult` as a singleton bean. This is a targeted extension — only needed when the Quarkus module uses startup lifecycle hooks.

**D11: `RoutingAgentProvider.create(...)` (agent-router-core)**

Static factory taking `BackendInstanceRegistry`, `String configDefault`, `ModelRegistry`, `Optional<ManifestResult>`. Encapsulates: if ManifestResult present, use its aliases and defaultBackendKey; otherwise use configDefault with empty aliases.

**D12: `ChatModelAgentProvider.create(...)` (agent-langchain4j-core)**

Static factory taking `List<ChatModel>`, `AgentLangchain4jProperties`. Encapsulates: filter out `AgentProviderChatModel` (prevents circular injection), take first match, cast to `StreamingChatModel` if applicable.

**D10: Qualifier propagation (identity-core — no code change)**

`@DIDMethod` and `@ActorDIDSource` are already framework-neutral annotations in platform-api. The generator propagates them to `@Bean` methods. `CompositeDIDResolver` receives `@DIDMethod List<DIDResolver>`. No core module changes needed — the generator handles this via qualifier detection in the scanner.

### Identity Properties interfaces (D9, expanded per R1-05)

Extract config interfaces to identity-core for all config-bearing beans:

| Interface | Beans served | Params covered |
|---|---|---|
| `ScimAgentLookupProperties` | ScimAgentLookup | endpoint, authToken, timeoutMs, cacheTtl, requireHttps |
| `WebDIDResolverProperties` | WebDIDResolver | timeoutMs, maxResponseBytes |
| `IdentityDIDProperties` | ConfiguredActorDIDProvider | `Map<String,String> dids` |
| `CredentialValidationProperties` | JwtVCValidator | `Map<String,String> credentials`, cacheTtl |

Core POJOs take the Properties interface instead of primitives. The Quarkus `IdentityConfig` extends these interfaces via `@ConfigMapping`. Consistent with the agent `*Properties` pattern.

### Verify goal

Once the generator handles all beans (`requiresManualConfig()` always returns `false`), the verify goal automatically covers the full Quarkus→Spring bean surface. The existing `SpringVerifyMojo.collectSourceTypes()` no longer skips any beans, so a new `@Produces` method without a corresponding generated `@Bean` fails the build. Zero-drift guarantee is enforced at compile time.

### Spring Boot Starter update

After generating the 12 new `-spring` modules, add them as dependencies in `spring-boot-starter/pom.xml`. The starter remains the single consumer dependency.

## Module impact

| Module | Change |
|---|---|
| spring-generator | Enhanced scanner + JavaPoet writer + `@FactoryMethod` detection |
| platform-api | Add `@FactoryMethod` marker annotation |
| generator-common | No change (JandexTypeConverter already handles parameterized types) |
| agent-config-core | Add `AgentConfigLoader` class |
| agent-router-core | Add `RoutingAgentProvider.create(...)` `@FactoryMethod` static factory |
| agent-langchain4j-core | Add `ChatModelAgentProvider.create(...)` `@FactoryMethod` static factory |
| identity-core | Add 4 Properties interfaces (Scim, WebDID, DID, Credential) |
| agent-claude, agent-openai, agent-codex, agent-gemini, agent-gemini-cli | Simplify `@Produces` to call core constructors |
| agent-router, agent-langchain4j, agent-config | Simplify `@Produces` to call core factories |
| agent-runtime, governance | No change needed — already handled by current generator |
| expression | No Quarkus change — registry's `Instance→List` handled by enhanced scanner |
| identity | Simplify `@Produces`, use Properties interfaces |
| 12 new `-spring` modules | Generated by plugin — no hand-written code |
| spring-boot-starter | Add 12 new dependencies |

## Testing

- **Generator unit tests:** Verify scanner handles all param mapping patterns (plain, List, Optional, ConfigProperties, `@FactoryMethod`). Verify qualifier propagation + `@Primary` on composites. Verify `@Priority` → `@Order` mapping. Verify init-method detection.
- **Verify goal tests:** Confirm that new `@Produces` methods without generated `@Bean` equivalents fail the build.
- **spring-integration-test:** After generating all 12 modules and adding to starter, the existing E2E composition gate (context loads, health check, Jackson bridge) covers the new modules automatically.

## Known limitations

- `@Primary` is binary — no priority ordering. Safe under platform's mutual exclusion rules. (Review I4)
- Auto-configuration ordering uses Spring's default lazy dependency resolution. For inter-module dependencies (e.g., ManifestResult consumed by RouterBeans), both beans are in the same auto-config class or Spring resolves order automatically. Explicit `@AutoConfigureAfter` added only if needed. (Review I3)
- JavaParser-based source translation (Approach B/C from brainstorm) is the documented evolution path if future wiring patterns outgrow the constructor-scanning model.

## References

- `spring-generator/src/main/java/` — current scanner and writer source
- `generator-common/src/main/java/` — JandexTypeConverter, AbstractGeneratorMojo, AbstractVerifyMojo
- `platform-spring/` — existing generated Spring module (reference pattern)
- Agent core module constructor signatures (agent-claude-core through identity-core)
- RouterBeans, AgentConfigBeans, Langchain4jBeans, IdentityBeans — Quarkus wiring method bodies
- Decision review R1-09 (D8 complexity), R1-11 (D10 semantic divergence), R1-06 (D11/D12 gaps), R1-16 (I4 @Primary limitation)
- casehubio/parent#504 issue specification
