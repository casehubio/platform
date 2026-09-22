# Decisions — Spring Deployment Readiness (#501 / #499)

## D1: Jackson migration strategy for Spring Boot 4 + Quarkus 3

**Choice:** Use `spring-boot-jackson2` bridge module now. Both frameworks stay on Jackson 2 `ObjectMapper`. Migrate to Jackson 3 in one pass when Quarkus 4 GA lands (~Nov 2026).
**Alternatives:**
- Migrate core modules to Jackson 3 now — feasible (different Maven coordinates coexist), but requires manually producing Jackson 3 mapper beans in Quarkus modules since Quarkus 3 won't auto-configure one. Fragile, unusual, and throwaway when Quarkus 4 adds native Jackson 3.
- Abstract over the mapper with a serialization SPI — proper long-term but adds indirection for a problem that goes away in ~2 months.
**Rationale:** Three core modules (`DeliveryTracker`, `DeliveryRetryProcessor`, `CallbackDispatcher`) take `ObjectMapper` as a constructor parameter. Spring Boot 4 auto-configures Jackson 3 `JsonMapper` by default, which is a different type. The `spring-boot-jackson2` bridge exists explicitly for this migration window — it auto-configures a Jackson 2 `ObjectMapper` so both frameworks inject the same type. Zero code changes to core modules now; one clean migration when Quarkus 4 aligns.
**Trade-offs:** `spring-boot-jackson2` is deprecated and will be removed in a future Spring Boot release. Acceptable — Quarkus 4 GA (Nov 2026) is well before that removal.
**Sources:** [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide), [Jackson 3 GA announcement](https://cowtowncoder.medium.com/jackson-3-0-0-ga-released-1f669cda529a), [Quarkus Jackson 3 epic #52036](https://github.com/quarkusio/quarkus/issues/52036), core module analysis (CallbackDispatcher, DeliveryTracker, DeliveryRetryProcessor constructor signatures)
**Exploration:** deep-analysis
**Status:** captured

## D2: Parent BOM bump vs platform alignment — separate or one branch

**Choice:** Separate — bump parent BOM to main first (quarkus 3.32.2→3.39.3, spring-boot 4.1.0→4.1.1), then platform branch removes local overrides and inherits.
**Alternatives:**
- Single branch — bump parent + platform together. Tighter coupling, blocks other repos from benefiting until the platform branch lands.
**Rationale:** The parent BOM is a version-only POM with no code. Bumping it to main is safe and unblocks all repos immediately. Platform then removes its local version overrides on the feature branch, adds spring-boot-jackson2, and writes the E2E test. Other repos can align to the new BOM independently.
**Trade-offs:** Two commits to coordinate (parent main, then platform branch). Trivial overhead.
**Sources:** Parent pom.xml structure (version properties only, no code), platform pom.xml (local overrides at lines 161, 165)
**Exploration:** quick
**Status:** captured

## D3: E2E test module location

**Choice:** New `spring-integration-test/` module — depends on `casehub-spring-boot-starter` + H2 + `spring-testing`.
**Alternatives:**
- Add to existing `spring-testing/` — fewer modules, but bloats a thin fixture library meant as a test-scope consumer dependency with the full starter + H2 + `@SpringBootTest`.
**Rationale:** Clean separation of concerns. `spring-testing` stays lightweight (fixtures only). `spring-integration-test/` verifies the full composition, its POM doubles as documentation of a consumer's dependency set, and it naturally houses future Spring integration tests.
**Trade-offs:** One more Maven module. Trivial overhead.
**Sources:** spring-testing/pom.xml (3 deps, no starter), spring-boot-starter/pom.xml (13 runtime modules)
**Exploration:** quick
**Status:** captured

## D4: E2E test scope and assertions

**Choice:** Four verification layers: (1) context loads — all @Bean methods resolve, no circular deps; (2) generated controllers register — inject and verify REST endpoint mappings exist; (3) health check returns OK — `GET /actuator/health` returns 200; (4) Jackson bridge verification — injected ObjectMapper is Jackson 2 (`com.fasterxml.jackson.databind.ObjectMapper`), not Jackson 3.
**Alternatives:**
- Context-loads only — minimal, but doesn't catch HTTP layer wiring issues or Jackson compatibility problems.
- Full functional tests — overkill for a "does it boot?" gate; individual modules already have functional tests.
**Rationale:** These four checks verify the critical integration surface: CDI→Spring bean translation, generated code registration, HTTP stack health, and the Jackson 2 bridge that D1 depends on. Each is a single assertion — the test stays fast and focused.
**Trade-offs:** Does not test actual business logic through the Spring stack. Acceptable — that's the role of per-module tests.
**Sources:** casehubio/parent#499 issue scope, D1 (Jackson bridge dependency)
**Depends on:** D1, D3
**Exploration:** quick
**Status:** captured

## D5: Generator enhancement strategy — constructor-scanning vs source translation

**Choice:** Constructor-scanning (Jandex-only). Generator follows `@Produces` return types to core POJO constructors via Jandex index. Maps constructor params mechanically to Spring equivalents. 5 factory methods extracted to core modules to eliminate all non-constructor wiring logic from Quarkus modules. Result: 100% generated Spring auto-configurations, zero hand-written code, zero drift.
**Alternatives:**
- Source translation (JavaParser + Jandex) — parse Quarkus `@Produces` method bodies, apply AST transformation rules (CDI→Spring). More powerful for arbitrary wiring, but higher complexity, needs source access, fragile to formatting. Overkill when factory extractions eliminate the need.
- Hybrid (constructor-scan + JavaParser fallback) — default Jandex path, fall back to JavaParser for complex cases. Two code paths to maintain, decision logic for when to fall back. Deferred as evolution path if future patterns outgrow constructor model.
**Rationale:** The core extraction pattern guarantees framework-neutral constructors. 22 of 27 beans across 13 modules map directly via constructor params. The remaining 5 need small factory extractions (3-10 lines each) that are architecturally correct regardless — they move wiring logic from framework-specific modules to shared core. After extraction, every `@Produces` method becomes `return new CorePojo(params)` or `return CorePojo.create(params)`, which the generator handles mechanically. The verify goal catches drift at build time.
**Trade-offs:** Requires 5 core-module refactors (factory extractions + IdentityProperties interface). Small scope, correct architecture, but touches modules beyond the generator.
**Sources:** JandexProducerScanner source analysis, RouterBeans/AgentConfigBeans/Langchain4jBeans/IdentityBeans method body analysis, core module constructor signatures
**Depends on:** D1 (Jackson bridge — affects ObjectMapper wiring in some modules)
**Exploration:** deep-analysis
**Status:** captured

## D6: Code generation output — JavaPoet migration

**Choice:** Migrate AutoConfigurationWriter from string concatenation to JavaPoet. Aligns spring-generator with platform convention (rest-spring-generator, mcp-spring-generator, graphql-spring-generator all use JavaPoet). generator-common already provides `JandexTypeConverter` for Jandex→JavaPoet type mapping.
**Alternatives:**
- Stay with string concat — matches existing style but diverges from all sibling generators. New patterns (ConfigProperties records, destroyMethod, List params) add enough complexity that string templates become fragile.
- Hybrid (JavaPoet for new code only) — creates two generation styles in one plugin.
**Rationale:** Every other Spring generator in the platform uses JavaPoet via `com.palantir.javapoet`. The dependency already exists in `generator-common`. String concatenation is the historical anomaly, not the norm.
**Trade-offs:** Rewrites the writer — but it's small (one class) and the output is verifiable via existing tests.
**Sources:** IDE search: `com.palantir.javapoet` used in rest-spring-generator, mcp-spring-generator, graphql-spring-generator, generator-common
**Exploration:** quick (user correction — checked platform convention)
**Status:** captured

## D7: Config binding shape — @ConfigurationProperties records

**Choice:** Generator emits `@ConfigurationProperties` records implementing core `*Properties` interfaces. Immutable, Spring Boot 3+ native constructor binding. `@DefaultValue` annotations derived from Quarkus `@WithDefault` values on the extending `*Config` interface.
**Alternatives:**
- JavaBean class with getters/setters — more traditional, handles nested config groups. More verbose.
- Direct interface binding via proxy — Spring Boot 3.4+ experimental feature. Loses `@DefaultValue` support.
**Rationale:** Records are clean, immutable, and align with the platform's preference for value types. The core `*Properties` interfaces define the contract; the generated record is a thin binding layer. Spring Boot's relaxed binding handles kebab-case mapping (`apiKey()` → `api-key`).
**Trade-offs:** Records can't have nested mutable groups — not needed for current Properties interfaces. Generator only emits ConfigProperties records for types encountered as core POJO constructor params — not for every @ConfigMapping interface in the Quarkus module. Scope is bounded by core constructors, not by @ConfigMapping proliferation.
**Sources:** Spring Boot docs (constructor binding), ClaudeAgentProperties/OpenAiAgentProperties interface analysis, decision review R1-08
**Depends on:** D5 (constructor-scanning relies on Properties interface detection)
**Exploration:** quick → clarified after decision review
**Status:** captured

## D8: AgentConfig (Pattern D) — factory extraction to core

**Choice:** Extract `AgentConfigLoader` class to agent-config-core. Constructor-injected POJO taking ~7 dependencies: `LlmCredentialStore`, `MutableModelRegistry`, `CredentialResolver`, `Map<String,List<String>>` vendorRequirements, `LocalModelReconciler`, `String` profile, `Path` projectDir. Single `load()` method encapsulates: discover YAML, resolve profile, run ManifestLoader + ManifestProcessor, return ManifestResult. Both Quarkus (`@Observes StartupEvent`) and Spring (`@EventListener ApplicationStartedEvent`) instantiate `AgentConfigLoader` and call `load()`.
**Alternatives:**
- `ManifestResult.load(...)` static factory — correct extraction but 7-param static methods are unwieldy. An injectable class with constructor injection is cleaner. (Revised per decision review R1-09.)
- Manual Spring config + verify goal — accepts drift risk for 1 module. Pragmatic but violates the 100% generation guarantee.
**Rationale:** The orchestration logic belongs in core, not in framework-specific wiring. The class shape (vs static factory) accommodates the ~7 dependencies via constructor injection, which the generator handles natively — `AgentConfigLoader` becomes a normal constructor-scanned POJO. The Quarkus and Spring modules only differ in the lifecycle hook that calls `load()`.
**Trade-offs:** Modifies agent-config-core (adds class, ~30 lines). Moderate scope — the extraction moves existing procedural logic, doesn't create new complexity.
**Sources:** AgentConfigBeans source analysis (startup method body), decision review R1-09
**Depends on:** D5
**Exploration:** quick → revised after decision review
**Status:** revised

## D9: Identity config — extract Properties interfaces

**Choice:** Extract `WebDIDResolverProperties` and `ScimAgentLookupProperties` interfaces to identity-core. Replaces 5+ primitive constructor params with a typed config interface. Consistent with the agent `*Properties` pattern.
**Alternatives:**
- Generator binds @Value primitives — no core change needed, but breaks the Properties pattern and requires inferring property key names from @ConfigMapping prefix + field names.
**Rationale:** Consistency with the established agent config pattern. All config-bearing core POJOs take a Properties interface; identity should follow suit.
**Trade-offs:** Modifies identity-core (adds 2 interfaces). Minor scope addition.
**Sources:** WebDIDResolver(int, int), ScimAgentLookup(String, String, int, Duration, boolean) constructor signatures
**Depends on:** D5, D7
**Exploration:** quick
**Status:** captured

## D10: Composite qualifier preservation — reuse @DIDMethod/@ActorDIDSource as Spring qualifiers

**Choice:** Reuse the existing `@DIDMethod` and `@ActorDIDSource` annotations from platform-api as Spring `@Qualifier` annotations. The generator emits `@DIDMethod @Bean KeyDIDResolver keyDIDResolver()` for individual resolvers, and the composite receives `@DIDMethod List<DIDResolver>`. Spring natively supports custom qualifier annotations, so `@DIDMethod` works as both a CDI qualifier and a Spring qualifier without modification. Exact semantic parity with Quarkus.
**Alternatives:**
- Self-filtering factory (`create(List<T>)` that filters `instanceof Composite`) — changes semantics from qualifier-based inclusion to type-based exclusion. A future non-@DIDMethod DIDResolver would be incorrectly included in Spring but correctly excluded in Quarkus. Semantic divergence risk. (Revised per decision review R1-11.)
- `@ConditionalOnMissingBean` or `@Lazy` — doesn't solve the circular collection problem cleanly.
**Rationale:** `@DIDMethod` and `@ActorDIDSource` are plain Java annotations in platform-api (zero dependencies). They're already on the Spring classpath. Spring's `@Qualifier` mechanism supports arbitrary custom qualifier annotations. The generator just needs to detect CDI qualifier annotations on `@Produces` method params and propagate them to the generated `@Bean` methods. No new annotations, no semantic divergence, no core module changes.
**Trade-offs:** Generator must detect and propagate qualifier annotations — adds a mapping rule. But this is mechanical and eliminates the framework-specific behavior divergence that self-filtering introduces.
**Sources:** IdentityBeans `@DIDMethod Instance<DIDResolver>` qualifier usage, Spring Framework @Qualifier documentation, platform-api @DIDMethod/@ActorDIDSource annotation definitions, decision review R1-11
**Depends on:** D5
**Exploration:** quick → revised after decision review
**Status:** revised

## D11: RoutingAgentProvider — factory extraction for conditional ManifestResult

**Choice:** Add `RoutingAgentProvider.create(BackendInstanceRegistry, String configDefault, ModelRegistry, Optional<ManifestResult>)` static factory to agent-router-core. Encapsulates: if ManifestResult present, use its aliases and defaultBackendKey; otherwise use configDefault with no aliases. Returns fully constructed `RoutingAgentProvider`.
**Alternatives:**
- Leave constructor as-is and hand-write Spring config — breaks 100% generation guarantee.
**Rationale:** The conditional logic (3 lines) belongs in core. After extraction, both Quarkus and Spring call the factory with `Optional<ManifestResult>` (CDI: `Instance.isResolvable()`, Spring: `ObjectProvider.getIfAvailable()`). The generator handles `Optional<T>` params via `ObjectProvider<T>.getIfAvailable()`.
**Trade-offs:** Modifies agent-router-core (adds static factory). Trivial.
**Sources:** RouterBeans.routingAgentProvider() method body analysis
**Depends on:** D5
**Exploration:** quick (enumerated in D5 deep analysis, made explicit per decision review R1-06)
**Status:** captured

## D12: ChatModelAgentProvider — factory extraction for ChatModel filtering

**Choice:** Add `ChatModelAgentProvider.create(List<ChatModel>, AgentLangchain4jProperties)` static factory to agent-langchain4j-core. Encapsulates: filter out `AgentProviderChatModel` from candidates (prevents circular), take first match, cast to `StreamingChatModel` if applicable. Returns fully constructed `ChatModelAgentProvider`.
**Alternatives:**
- Leave filtering in Quarkus module — breaks 100% generation guarantee.
**Rationale:** The filtering logic (4 lines) belongs in core — it's a domain rule ("don't inject yourself into yourself"), not a framework concern. After extraction, both Quarkus and Spring pass `List<ChatModel>` and let the factory sort it out.
**Trade-offs:** Modifies agent-langchain4j-core (adds static factory). Trivial.
**Sources:** Langchain4jBeans.chatModelAgentProvider() method body analysis
**Depends on:** D5
**Exploration:** quick (enumerated in D5 deep analysis, made explicit per decision review R1-06)
**Status:** captured

## D13: @Decorator translation scope — specific solution vs general-purpose generator

**Choice:** Specific solution for GatedAgentProvider. Extract wrapping logic to agent-gate-core, generate agent-gate-spring via existing spring-generator. No new plugin.
**Alternatives:**
- General-purpose @Decorator generator (new Maven plugin or spring-generator extension) — reusable but speculative. Only 1 @Decorator exists in the platform codebase (GatedAgentProvider). Consumer repos' callback-generated @Decorators are already handled by callback-spring's BeanPostProcessor.
- Generic BeanPostProcessor (runtime discovery of @Decorator classes) — no codegen, but runtime scanning for a pattern with 1 instance is overweight.
**Rationale:** YAGNI. One instance doesn't justify a generator. The existing spring-generator handles the @Produces beans (SessionRegistry), and the BPP is small enough to hand-write. If future @Decorators appear, revisit then.
**Trade-offs:** If multiple @Decorators emerge, we'd need to hand-write each Spring equivalent or build the generator retroactively. Acceptable — the pattern is understood and extraction is mechanical.
**Sources:** IDE search: 1 `@Decorator` in platform (GatedAgentProvider). callback-spring BeanPostProcessor already covers @CallbackEligible decorators. casehubio/parent#500 issue scope.
**Exploration:** quick
**Status:** captured

## D14: Spring wrapping mechanism — BeanPostProcessor vs @Bean @Primary

**Choice:** BeanPostProcessor. A static @Bean BeanPostProcessor wraps AgentProvider after construction — same pattern as callback-spring. No circular dependency. Ordering via `Ordered.getOrder()` mapped from CDI `@Priority(APPLICATION)` = 2000.
**Alternatives:**
- @Bean @Primary + @Qualifier — explicit but couples to specific delegate bean name ("routingAgentProvider"). Breaks if the delegate bean is renamed or provided by a different module.
- @Bean @Primary + ObjectProvider — avoids naming coupling but adds runtime type filtering to break circular reference. More complex than BPP for the same result.
**Rationale:** CDI @Decorator uses container magic to resolve the non-decorator delegate. Spring has no equivalent — @Primary on a wrapping bean that takes the same interface creates a cycle. BeanPostProcessor is Spring's idiomatic pattern for post-construction wrapping. callback-spring already proves this works. The BPP intercepts AgentProvider beans, wraps with the core POJO, no circular dependency possible.
**Trade-offs:** BPP is runtime (wrapping decision at startup, not visible in @Bean graph). Acceptable — the BPP logs its wrapping action, and the core wrapper is type-safe.
**Sources:** CallbackDecoratorBeanPostProcessor implementation, Spring Framework BeanPostProcessor docs, CDI @Decorator spec (delegate resolution semantics)
**Exploration:** quick
**Status:** captured

## D15: AgentGateProperties extraction — Properties interface to core

**Choice:** Extract AgentGateProperties (minus @ConfigMapping) to agent-gate-core as a plain Java interface with nested interfaces (Concurrency, TokenBucketConfig, SlidingWindow, Reaper). Quarkus @ConfigMapping extends the core interface. Spring @ConfigurationProperties record implements it (generated by spring-generator).
**Alternatives:**
- Strategy-builder factory only — keep Properties Quarkus-only, add a static factory in core that takes primitive params. Simpler but diverges from the established Properties extraction pattern (D7, D9).
**Rationale:** Consistency with D7 (@ConfigurationProperties records implementing core Properties interfaces) and D9 (identity Properties extraction). The spring-generator already handles @ConfigMapping → @ConfigurationProperties record generation including nested groups. Following the established pattern means the generator handles this mechanically.
**Trade-offs:** Nested interfaces (4 levels) add extraction surface. Mitigated — the interfaces are simple value types with no behavior.
**Sources:** AgentGateProperties @ConfigMapping interface (6 nested interfaces), D7 (ConfigurationProperties records), D9 (identity Properties extraction)
**Depends on:** D7
**Exploration:** quick
**Status:** captured

## D16: SCIM core extraction — generator-compatible vs hand-written Spring module

**Choice:** Generator-compatible core extraction. Extract `ScimClient` (plain Java interface), `ScimGroupMembershipProviderCore` (POJO), `ScimProperties` (interface), and model records to `scim-core`. Refactor `scim/` to use `@Produces` returning the core POJO. The spring-generator handles wiring; only HTTP client (`RestClient`-based `ScimClient` impl) and caching (`@Cacheable` wrapper) are hand-written in `scim-spring`.
**Alternatives:**
- Hand-write scim-spring directly — more code duplication but simpler module structure. No core extraction needed.
- Skip core extraction, go direct — write scim-spring from scratch. SCIM protocol logic duplicated between Quarkus and Spring modules.
**Rationale:** The spring-generator's walk-DOWN detection confirms `ScimProperties` (core, no `@ConfigMapping`) is auto-detected as a config type when `ScimConfig extends ScimProperties` with `@ConfigMapping` exists in the Quarkus Jandex. `ScimClient` resolves as a plain bean dependency. Consistent with D5/D7/D9 core extraction patterns. Generator produces the wiring automatically; only framework-specific concerns (HTTP transport, caching) are hand-written.
**Trade-offs:** Requires refactoring existing `scim/` module (add `@Produces`, extract model records, rename `ScimClient` interface). Moderate refactor, but architecturally correct — shared SCIM protocol logic lives in one place.
**Sources:** spring-generator `findConfigMapping` walk-DOWN logic (lines 246–290), D5 (constructor-scanning), D7 (config binding), ScimGroupMembershipProvider source analysis
**Depends on:** D5, D7
**Exploration:** deep-analysis (generator source traced end-to-end)
**Status:** captured

## D17: oidc-spring — Spring Security Authentication mapping

**Choice:** Hand-write `SpringSecurityCurrentPrincipal` reading from `SecurityContextHolder.getContext().getAuthentication()`. Map `JwtAuthenticationToken.getToken().getClaim()` for tenancyId/crossTenantAdmin (same claim names as Quarkus via `SecurityIdentityAttributes`). `Authentication.getName()` for actorId. `Authentication.getAuthorities()` → extract role names for groups. `MissingTenancyExceptionHandler` as `@ControllerAdvice` (Spring equivalent of JAX-RS `@Provider ExceptionMapper`).
**Alternatives:**
- Generator-compatible extraction — not viable. `SecurityContextHolder` is a static thread-local, not an injectable dependency. No constructor params for the generator to scan.
- Extract a core claim-reading utility — extract the claim resolution logic (JWT claim → attribute fallback) to a shared utility. Adds indirection for 2 consumers.
**Rationale:** `SpringSecurityCurrentPrincipal` has no constructor params (reads from `SecurityContextHolder` static context). The generator can't produce anything useful. Hand-writing is 2 small classes (~50 lines each) with well-defined semantics. The same `SecurityIdentityAttributes` claim names work in both frameworks — the claim contract is framework-neutral, only the source differs.
**Trade-offs:** Claim resolution logic is duplicated between `SecurityIdentityCurrentPrincipal` (Quarkus) and `SpringSecurityCurrentPrincipal` (Spring). Acceptable — the logic is 20 lines, tightly coupled to the security context type, and the two implementations will diverge as framework-specific auth features evolve.
**Sources:** SecurityIdentityCurrentPrincipal source, SecurityIdentityAttributes constants, Spring Security JwtAuthenticationToken API, CurrentPrincipal SPI contract
**Exploration:** quick
**Status:** captured

## D18: credentials-spring — Environment-based CredentialResolver

**Choice:** `EnvironmentCredentialResolver` reading from Spring `Environment`. Spring Boot externalises secrets as properties (via spring-cloud-vault, AWS SSM, spring-cloud-kubernetes, etc.). The resolver reads `environment.getProperty(credentialRef + "." + key)` for each `CredentialPropertyKeys` constant. Covers all backends automatically since they all surface as Spring properties.
**Alternatives:**
- Direct cloud SDK bridges (VaultTemplate, AwsSecretsManagerClient) — more type-safe but couples to specific cloud SDKs, requires per-backend modules, and duplicates what spring-cloud already abstracts.
- Defer — skip for now, NoOp mock works for dev/test. Risk: consumers can't deploy to production without credentials.
**Rationale:** Spring Boot's property abstraction is the idiomatic way to access secrets. `spring-cloud-vault` maps Vault paths to property names. `spring-cloud-aws-secrets-manager` does the same for AWS. The `Environment` interface is the universal read API for all of these. One implementation covers all backends — consistent with how Quarkus `CredentialsProvider` works (single SPI, pluggable backends).
**Trade-offs:** Credential structure is flattened to property keys (`credentialRef.USER`, `credentialRef.PASSWORD`). Consumers must configure their spring-cloud backend to map secrets to this naming convention. Documented in consumer guide.
**Sources:** QuarkusCredentialResolver source, CredentialPropertyKeys constants, Spring Boot Externalized Configuration docs
**Exploration:** quick
**Status:** captured

## D19: Shared streams-core module for common CloudEvent construction

**Choice:** Create a shared `streams-core` module with `StreamCloudEventFactory` utility. All per-module cores depend on it. Eliminates the `buildCloudEvent()` duplication across kafka, amqp, poll, and camel (4 near-identical copies → 1 shared utility).
**Alternatives:**
- Per-module cores only (each with own copy) — simpler dependency graph, fully independent modules. But 4 copies of ~30 lines with identical semantics.
- Extend streams-webhook-core — saves a module but muddies webhook-core's purpose (shared library + webhook-specific logic).
**Rationale:** The `buildCloudEvent()` implementations are structurally identical: read STREAM_EVENT_TYPE from descriptor → determine tenancyId → construct CloudEvent v1 with UUID/type/source/timestamp/data/tenancyid extension → optionally set datacontenttype. The only variations (source URI prefix, tenancyId source) are parameterizable. A shared factory with a clean API eliminates duplication and ensures consistent CloudEvent construction across all streams.
**Trade-offs:** Adds one more Maven module. All per-module cores gain a dependency on streams-core.
**Sources:** KafkaStreamProcessor.buildCloudEvent(), AmqpStreamProcessor.buildCloudEvent(), PollStreamProcessor.buildCloudEvent(), CamelStreamProcessor.buildCloudEvent() — structural comparison
**Exploration:** quick
**Status:** captured

## D20: Native @KafkaListener and @RabbitListener for Spring adapters

**Choice:** Native `@KafkaListener` for `streams-kafka-spring`, native `@RabbitListener` for `streams-amqp-spring`. Each extracts protocol-specific metadata (Kafka headers, AMQP application properties) and delegates to the core POJO's `processMessage()`. Dependencies: `spring-kafka` and `spring-amqp` (already in `spring-boot-starter`). Config: standard `spring.kafka.consumer.*` and `spring.rabbitmq.*`.
**Alternatives:**
- Spring Cloud Stream functional bindings (`Consumer<Message<byte[]>>`) — higher-level abstraction, unified programming model for both brokers. But kafka-spring and amqp-spring are separate Maven modules sharing zero adapter code, so the "unified model" provides no structural benefit. Adds a large transitive dependency tree (`spring-cloud-stream` + binder), introduces its own configuration namespace (`spring.cloud.stream.bindings.*`), and brings a spring-cloud version train to manage — all for a thin adapter that delegates to a core POJO.
**Rationale:** The core extraction (D19, D23) already provides the unified logic — `StreamCloudEventFactory` and per-module core POJOs are shared across frameworks. The Spring adapters are thin plumbing: extract protocol metadata → call `core.processMessage()`. For thin plumbing, native listeners (`@KafkaListener`, `@RabbitListener`) are simpler, use well-known Spring configuration, have fewer dependencies, and avoid the spring-cloud version compatibility surface with Spring Boot 4.
**Trade-offs:** Two different listener annotations (@KafkaListener vs @RabbitListener) instead of one functional binding pattern. Acceptable — the modules are already broker-specific by design, and the annotation difference is 2 lines of framework boilerplate, not domain logic.
**Sources:** Decision review R1-02 (Spring Cloud Stream dependency weight challenge), spring-kafka @KafkaListener docs, spring-amqp @RabbitListener docs, casehubio/parent#507
**Exploration:** quick → revised after decision review R1-02
**Status:** revised

## D21: Full core extraction for streams-camel

**Choice:** Create `streams-camel-core` with `CamelStreamProcessorCore` POJO. Constructor-injected `CamelContext` (directly — no wrapping abstraction), `EndpointRegistry`, `Consumer<CloudEvent>`. Owns route-building loop, idempotency tracking, startup-window logic. Exposes `init()` and `onEndpointRegistered(EndpointDescriptor)`. Both Quarkus and Spring become thin lifecycle wrappers.
**Alternatives:**
- Thin Spring wrapper only (no core extraction) — less work, CamelContext is already framework-neutral. But duplicates ~25-30 lines of orchestration logic (endpoint correlation, route building, idempotency tracking, startup-window management) between Quarkus and Spring modules. Breaks the pattern established by all other streams modules.
**Rationale:** Consistency is load-bearing in a 80+ module dual-framework platform. The "CamelContext is already framework-neutral" observation is technically correct but architecturally irrelevant — the CODE AROUND CamelContext (endpoint correlation, idempotent route registration, startup-window ordering, processor lambdas capturing the event callback) is domain logic that should exist in one place. Pure JUnit testing with DefaultCamelContext is a genuine improvement over container-only testing. The incorporation from Option 1: CamelContext is taken directly as a constructor param — no additional abstraction layer.
**Trade-offs:** One more Maven module for ~80 lines of core code. The @ObservesAsync EndpointRegistered pattern splits: core exposes onEndpointRegistered(descriptor), wrapper subscribes to framework events and calls it.
**Sources:** CamelStreamProcessor source analysis, webhook-core extraction pattern, deep analysis (steelman + devil's advocate + first principles)
**Exploration:** deep-analysis
**Status:** captured

## D22: JDK HttpClient in poll-core (not Spring RestClient)

**Choice:** `PollStreamProcessorCore` owns the `java.net.http.HttpClient` directly. Both Quarkus and Spring frameworks use the same JDK HTTP layer via core. Spring module is pure `@Scheduled` lifecycle wiring.
**Alternatives:**
- Spring RestClient in Spring adapter — more idiomatic Spring, integrates with Spring observability/interceptors. But duplicates fetch logic outside core, breaks the extraction pattern.
**Rationale:** `java.net.http.HttpClient` is already used by the existing Quarkus module and is framework-neutral by nature. Moving it to core means both frameworks share identical HTTP behavior. The fetch logic (`fetchBytes()` with explicit status code checking and InterruptedException handling) is non-trivial enough to single-source.
**Trade-offs:** Spring consumers don't get Spring RestClient integration (interceptors, observability). Acceptable — this is a background poller, not a user-facing HTTP client. If observability is needed later, it can be added at the HttpClient level (JDK HttpClient supports custom handlers).
**Sources:** PollStreamProcessor.fetchBytes() source, java.net.http.HttpClient API
**Exploration:** quick
**Status:** captured

## D23: Refactor existing Quarkus modules to delegate to cores

**Choice:** Refactor all 4 existing Quarkus streams modules (kafka, amqp, poll, camel) to delegate to their new -core POJOs and use the shared `StreamCloudEventFactory` from streams-core. Each Quarkus module becomes a thin wrapper: `@Produces` creating the core POJO + framework lifecycle annotations.
**Alternatives:**
- New modules only — create cores and Spring adapters, leave Quarkus modules untouched with duplicated buildCloudEvent. Smaller diff, less risk. Refactor later.
- Refactor only buildCloudEvent — replace duplication with shared factory, don't restructure Quarkus modules to use -core POJOs. Partial cleanup.
**Rationale:** The -core modules must exist for the Spring adapters. Having both the Quarkus module AND the -core module contain the same logic is the worst outcome — it creates a third copy of the business logic (core + Quarkus + Spring instead of core + thin Quarkus wrapper + thin Spring wrapper). Refactoring now ensures a single source of truth from the start.
**Trade-offs:** Touches existing working modules. Mitigated by: existing tests validate behavior equivalence, the refactoring is mechanical (extract logic → delegate to core), and the core tests add a new verification layer.
**Sources:** All 4 Quarkus module sources, webhook/webhook-core extraction precedent
**Exploration:** quick
**Status:** captured

## D24: Module structure — 9 new modules + 4 refactored

**Choice:** 9 new modules: `streams-core` (shared), `streams-poll-core`, `streams-kafka-core`, `streams-amqp-core`, `streams-camel-core` (per-module cores), `streams-poll-spring`, `streams-kafka-spring`, `streams-amqp-spring`, `streams-camel-spring` (Spring adapters). 4 refactored modules: `streams-poll`, `streams-kafka`, `streams-amqp`, `streams-camel` (Quarkus wrappers delegating to cores).
**Alternatives:**
- Fewer modules (combine some cores into streams-core) — fewer POMs but less separation of concerns. Each core has different dependencies (camel-core needs camel-core-model, kafka-core doesn't).
**Rationale:** Each -core module has a distinct dependency profile: poll-core needs nothing beyond streams-core, kafka-core/amqp-core need only streams-core, camel-core needs camel-core-model + streams-core. Spring adapters have distinct dependencies: poll-spring needs only spring scheduling, kafka-spring needs spring-kafka, amqp-spring needs spring-amqp, camel-spring needs camel-spring-boot. Separate modules keep classpaths minimal and avoid pulling unnecessary transitive dependencies.
**Trade-offs:** 9 new Maven modules is significant. Mitigated: each is small (1-2 classes), the pattern is mechanical, and the platform already has 80+ modules.
**Sources:** D19-D23, pom.xml dependency analysis per module
**Depends on:** D19, D20, D21, D22, D23
**Exploration:** quick
**Status:** captured

## D25: Synchronous event dispatch in Spring adapters

**Choice:** Spring adapters use synchronous `ApplicationEventPublisher.publishEvent()` for the `Consumer<CloudEvent>` callback. Listeners run on the message consumer thread (Kafka/AMQP consumer thread for messaging, scheduler thread for poll).
**Alternatives:**
- Async dispatch via `@Async @EventListener` or `ApplicationEventMulticaster` with `TaskExecutor` — matches Quarkus CDI `fireAsync()` threading model (observers run on CDI async executor pool, consumer thread blocks at barrier). More complex (async error handling, thread pool config).
- Document as known divergence — keep synchronous, let consumers add `@Async` on their listeners as needed.
**Rationale:** Semantically equivalent to Quarkus: message is acked only after all observers/listeners complete. The execution model differs (synchronous on consumer thread vs async on executor pool with barrier), but the end-to-end guarantee is the same. The performance difference (slow listeners blocking consumer thread) only matters under heavy load with slow observers, which this platform doesn't have. Adding `@Async` introduces error handling complexity for a theoretical concern.
**Trade-offs:** A slow `@EventListener` blocks the Kafka/AMQP consumer thread in Spring, reducing throughput. Quarkus dispatches observers on separate threads. If this becomes a problem, consumers can add `@Async` to their listeners individually.
**Sources:** Decision review R1-06 (CDI fireAsync vs Spring publishEvent semantics), CDI 4.0 Event.fireAsync() spec, Spring ApplicationEventPublisher docs
**Exploration:** quick
**Status:** captured

## D26: Explicit Category C → A reclassification for streams modules

**Choice:** Reclassify streams modules from Category C ("framework-specific implementations sharing utility logic" — D3 from issue #469) to Category A (full core extraction). D3's assessment that core extraction would create "nearly-empty cores" was incorrect for streams — the modules contain 25-80 lines of substantive domain logic (CloudEvent construction, endpoint discovery/correlation, route building, idempotency tracking, startup-window management) beyond their framework binding layer. The existing `streams-webhook-core` extraction already proved the pattern works.
**Alternatives:**
- Honor D3's Category C classification — skip core extraction, create Spring adapters as independent implementations. Duplicates domain logic across frameworks.
**Rationale:** D3 performed a surface-level assessment of streams as "connector-specific framework binding." Detailed analysis (this issue) reveals that SmallRye `@Incoming`/`@Scheduled`/`@ObservesAsync` annotations are the framework binding; the endpoint discovery, CloudEvent construction, and route orchestration are framework-neutral business logic. The reclassification must be explicit because D3 was a `deep-analysis` decision that informed the entire extraction strategy.
**Trade-offs:** Contradicts a prior architectural decision. Mitigated: the contradiction is based on deeper analysis, not a change in principles.
**Sources:** D3 (issue #469, dual-framework core extraction), streams module source analysis, decision review R1-01
**Depends on:** D19, D21, D23
**Exploration:** quick
**Status:** captured

## D27: CBR filter matching extraction — not base class, not duplication

**Choice:** Extract filter matching (~60 lines) to a static utility `CbrCaseFilterMatcher` in memory-api (package `io.casehub.neocortex.memory.cbr`), next to existing `CbrSimilarityScorer` and `CbrFeatureValidator`. Keep jpa-common entity-only (consistent with all 10+ platform jpa-common modules). Accept ~30 lines of entity reconstruction duplication in each JPA module — sealed `CbrCase` hierarchy gives compile-time safety.
**Alternatives:**
- Abstract base class in jpa-common — breaks entity-only convention across the project, mixes persistence with domain logic via Template Method pattern, requires Jackson compile dep in a data module
- Full duplication (~90 lines) — filter matching has 8 CbrFilter variants; new variants added independently of entity, no compile-time signal to update the second implementation
- Extract all to memory-core — wrong location. CbrSimilarityScorer and CbrFeatureValidator are in memory-api, not core. Filter matching is SPI-adjacent domain logic.
**Rationale:** First-principles analysis: filter matching operates on domain types (CbrCase, CbrFilter, CbrFeatureSchema) with zero JPA dependency — it's domain logic that belongs with domain types. Entity reconstruction maps entity fields → domain constructors — it's persistence-adjacent and acceptable to duplicate when the sealed type hierarchy guarantees compile-time enforcement.
**Trade-offs:** Touches memory-api in addition to JPA modules. Reconstruction duplication (~30 lines) must stay in sync manually, but sealed types mitigate.
**Sources:** JpaCbrCaseMemoryStore.java analysis, CbrSimilarityScorer/CbrFeatureValidator pattern, platform jpa-common convention (10+ entity-only modules)
**Exploration:** deep-analysis
**Status:** captured

## D28: FTS support in neocortex memory-spring-jpa

**Choice:** Same dual-path as Quarkus — native SQL query for PostgreSQL FTS (websearch_to_tsquery) when enabled, standard JPQL for chronological ordering otherwise. Config via Spring `@ConfigurationProperties` (replacing SmallRye `@ConfigMapping`).
**Alternatives:**
- Chronological only — simpler but loses relevance ordering for question-based queries
- Spring-native FTS via @Query(nativeQuery=true) — same SQL, marginally more Spring-idiomatic but no functional difference
**Rationale:** Feature parity with Quarkus module. The FTS queries are PostgreSQL-native SQL regardless of framework — `EntityManager.createNativeQuery()` and `@Query(nativeQuery=true)` are equivalent. Tests use H2 (chronological path) like Quarkus does.
**Trade-offs:** Native SQL ties both implementations to PostgreSQL for FTS. Acceptable — FTS is already PostgreSQL-specific by design.
**Sources:** JpaMemoryStore.java (queryFts method), MemoryJpaConfig (SmallRye ConfigMapping)
**Exploration:** quick
**Status:** captured

## D29: TenantContextManager — extract to persistence-jpa-common

**Choice:** Extract a framework-neutral `TenantContextManager` POJO to `persistence-jpa-common`. Constructor-injected `EntityManager` + `String rlsConfigProperty` + `boolean rlsEnabled`. Provides `setTenantContext(tenancyId)` and `setCrossTenantContext()` methods that run `SET LOCAL` SQL. Both `persistence-hibernate` (Quarkus) and `persistence-spring-jpa` (Spring) use it via constructor injection, replacing the CDI-coupled `TenantAwareRepository` base class.
**Alternatives:**
- Spring-specific equivalent with `@PersistenceContext` + `@Value` — duplicates ~30 lines of `SET LOCAL` logic but avoids touching `persistence-hibernate`
- Spring `@TenantId` + `TenantIdentifierResolver` — different RLS mechanism (application-level filtering vs PostgreSQL policy enforcement), diverges from Quarkus security model
**Rationale:** `TenantAwareRepository` has 2 CDI-specific annotations (`@Inject EntityManager`, `@ConfigProperty`) but the actual tenant-setting logic is pure JDBC SQL strings. Extracting the SQL logic to a POJO eliminates the CDI coupling. The Quarkus `TenantAwareRepository` becomes a thin wrapper that constructs `TenantContextManager` from injected dependencies. Consistent with D5 core-extraction pattern across the epic.
**Trade-offs:** Refactors `persistence-hibernate` — all 8 repository classes that extend `TenantAwareRepository` change from inheritance to composition. Moderate mechanical refactor, but inheritance→composition is architecturally correct regardless of Spring.
**Sources:** TenantAwareRepository.java (CDI annotations, SET LOCAL SQL), D5 (constructor-scanning pattern), platform jpa-common convention
**Exploration:** quick
**Status:** captured

## D30: RlsPolicySetup — extract to persistence-jpa-common

**Choice:** Extract `RlsPolicySetup` POJO to `persistence-jpa-common`. Constructor-injected `DataSource` + `String rlsRole`. Provides `apply()` method with the raw SQL DDL that creates the crosstenancy role and applies RLS policies to 5 tables. Both Quarkus (`@Observes StartupEvent`) and Spring (`@EventListener ApplicationStartedEvent`) instantiate the POJO and call `apply()`.
**Alternatives:**
- Spring-specific rewrite — duplicates ~80 lines of SQL DDL
- Skip RLS in Spring — defers to external DB provisioning. Risk: Spring consumers can't deploy without separate RLS setup
**Rationale:** The RLS setup SQL is pure PostgreSQL DDL — framework-neutral by nature. Only the lifecycle hook that triggers it is framework-specific. Extracting the SQL to a POJO is trivial and prevents duplication of non-trivial DDL logic (role creation, policy application, idempotency checks).
**Trade-offs:** Adds jpa-common dependency on `javax.sql.DataSource` (already available via `jakarta.persistence-api` transitive). Refactors `RlsPolicyApplicator` to delegate to the POJO.
**Sources:** RlsPolicyApplicator.java (startup SQL, 5 table policies), D29 (jpa-common as shared location)
**Depends on:** D29
**Exploration:** quick
**Status:** captured

## D31: In-memory displacement — @ConditionalOnMissingBean

**Choice:** Modify `EngineSupportAutoConfiguration` to add `@ConditionalOnMissingBean` on each in-memory persistence `@Bean` method (`InMemoryCaseInstanceRepository`, etc). When `persistence-spring-jpa` is on the classpath, its unconditional `@Bean` methods win, displacing the in-memory defaults automatically.
**Alternatives:**
- `@Primary` on JPA beans — overrides the existing `@Primary` on in-memory. Two `@Primary` on the same type causes confusion
- `@ConditionalOnClass` gating — gates in-memory on absence of a JPA marker class. Introduces coupling via class name string
**Rationale:** `@ConditionalOnMissingBean` is the standard Spring Boot displacement pattern — used consistently across platform's spring-jpa modules. The in-memory implementations become genuine defaults that are displaced by any real persistence backend, not just JPA.
**Trade-offs:** Requires modifying `engine-support-spring` (existing module in engine repo). Small, backwards-compatible change — the `@Primary` annotations stay, `@ConditionalOnMissingBean` gates them.
**Sources:** EngineSupportAutoConfiguration.java (in-memory @Bean methods with @Primary), platform spring-jpa @ConditionalOnMissingBean pattern, D3 (#499 E2E test)
**Exploration:** quick
**Status:** captured

## D32: ActorStateResource — rest-spring-generator in engine build

**Choice:** Configure `rest-spring-generator` Maven plugin in `engine-support-spring/pom.xml`. Generates a Spring MVC `@RestController` wrapping `ActorStateResource` core POJO (already a `@Bean`). Verify goal catches drift. Consistent with platform convention — all Spring REST controllers are generated from JAX-RS-annotated core POJOs.
**Alternatives:**
- Hand-write controller — only 1 endpoint (`@GET /actors/{actorId}/state`), generator setup is heavier than output. But sets no precedent for future engine REST endpoints
**Rationale:** The rest-spring-generator is proven (platform uses it for all REST generation). Adding it now means any future JAX-RS resources in the engine get Spring equivalents automatically. The verify goal prevents hand-written and generated code from drifting. The one-time POM setup cost is amortized across all future endpoints.
**Trade-offs:** Plugin dependency + configuration in engine POM. Trivial overhead.
**Sources:** rest-spring-generator plugin (platform), ActorStateResource.java (@Path("/actors"), 1 endpoint), engine-support-spring/pom.xml
**Depends on:** D5 (generator ecosystem)
**Exploration:** quick
**Status:** captured

## D33: TenantContextManager location — persistence-jpa-common

**Choice:** `TenantContextManager` lives in `persistence-jpa-common` alongside the entities, not in `common-core`. Both Quarkus (`persistence-hibernate`) and Spring (`persistence-spring-jpa`) depend on `persistence-jpa-common` already. Keeps `common-core` JPA-free.
**Alternatives:**
- `common-core` with JPA dep — adds `jakarta.persistence-api` (provided) to the SPI module. Pollutes framework-neutral common-core with a persistence API
- New `persistence-core` module — separate module for framework-neutral persistence utilities. Clean but another module for one small class
**Rationale:** `TenantContextManager` takes `EntityManager` as a constructor param — it's inherently a JPA type. Placing it in `persistence-jpa-common` keeps `common-core` JPA-free and avoids creating a module for a single class. The "core extraction" target is a shared persistence module, not the SPI module.
**Trade-offs:** None significant — `persistence-jpa-common` is the natural home for JPA-coupled shared code.
**Sources:** common-core pom.xml (no JPA deps), persistence-jpa-common (entities + migrations), D29 (TenantContextManager design)
**Depends on:** D29
**Exploration:** quick
**Status:** captured

## D34: Work persistence-mongodb — full core extraction with MongoClient-based POJOs

**Choice:** Full core extraction. Create `persistence-mongodb-core` with 16 framework-neutral store POJOs using `com.mongodb.client.MongoClient`/`MongoDatabase`/`MongoCollection` directly. 13 document classes become plain POJOs with `org.bson` annotations only (strip `PanacheMongoEntityBase`). Refactor existing Quarkus `persistence-mongodb` to thin CDI wrappers delegating to core POJOs. Create `persistence-spring-mongodb` as thin Spring `@AutoConfiguration` wiring.
**Alternatives:**
- Spring-only, no Quarkus changes — create persistence-spring-mongodb with its own document classes and Spring Data repositories. Faster, but duplicates 13 document classes and all mapping/query logic. Breaks the core module pattern established across the epic.
- Hybrid (shared documents only) — extract document POJOs to persistence-mongodb-common, keep store logic framework-specific. Middle ground but still duplicates query building, OCC, atomic ops across frameworks.
**Rationale:** Consistent with CLAUDE.md ("every CDI-coupled module has a `-core` counterpart") and the established precedent (D27-D28 for JPA, D19-D26 for streams). The MongoDB Java driver's `MongoClient` is framework-neutral — both Quarkus and Spring Boot auto-configure `com.mongodb.client.MongoClient`. Panache is syntactic sugar over MongoCollection; the core stores already build BSON filters manually. The refactoring moves domain logic (tenant filtering, OCC, atomic ops, query building) to a single location that both frameworks consume.
**Trade-offs:** Refactors existing Quarkus module away from Panache. 16 stores + 13 documents + 1 index initializer to migrate. The Panache dependency is removed entirely from persistence-mongodb.
**Sources:** persistence-mongodb source analysis (16 stores, 13 documents, all extend PanacheMongoEntityBase), D27-D28 (JPA common pattern), D19-D26 (streams core extraction), CLAUDE.md core module architecture
**Exploration:** quick
**Status:** captured

## D35: No Spring Data repositories — core POJOs + thin wiring

**Choice:** Spring module only provides `MongoClient` bean and produces core POJOs as `@Bean`. No Spring Data `MongoRepository` interfaces. All query logic stays in framework-neutral core POJOs using the MongoDB driver directly.
**Alternatives:**
- Spring Data MongoRepository interfaces — more Spring-idiomatic. But requires splitting query logic between core (complex queries, OCC, atomic ops) and Spring Data (simple CRUD), or duplicating store logic entirely. Conflicts with core extraction.
**Rationale:** With core extraction, the 16 store POJOs already contain all query building, OCC, and atomic operations using `MongoCollection` from the driver. Spring Data repos would add a layer that either duplicates or splits this logic. The Spring module's only job is wiring: provide `MongoDatabase` (from Spring Boot's auto-configured `MongoClient`) + `CurrentPrincipal` (from platform's Spring security module) to the core POJO constructors.
**Trade-offs:** Spring consumers don't get Spring Data features (query derivation from method names, `@Query` annotations, repository events). Acceptable — the core POJOs are the repository implementations, and all queries are already hand-written with BSON filters.
**Sources:** MongoWorkItemStore.buildFilter() (complex query builder), D34 (core extraction strategy)
**Depends on:** D34
**Exploration:** quick
**Status:** captured

## D36: MongoDatabase as core POJO injection point

**Choice:** Each core store POJO takes `MongoDatabase` via constructor. Derives its typed `MongoCollection<DocType>` internally via `database.getCollection("collection_name", DocType.class)`. Framework wrappers resolve `MongoDatabase` from their auto-configured `MongoClient` + database name config. Core stores are unaware of database name configuration.
**Alternatives:**
- `MongoClient` + `String databaseName` — core resolves database. Pushes config knowledge into core unnecessarily.
- `MongoCollection<DocType>` per store — cleanest injection but requires the wiring layer to know each store's collection name and document type. 16 typed collections to configure.
**Rationale:** `MongoDatabase` is the right abstraction level — the framework wrapper handles MongoClient → database resolution (one line: `client.getDatabase(dbName)`), and each core store internally knows its own collection name and document type (already hardcoded via `@MongoEntity(collection="...")` in the Panache documents). The `PojoCodecProvider` is configured once on the `MongoDatabase`'s codec registry.
**Trade-offs:** All stores in one module share a single database. If per-store databases were needed (unlikely), this would need changing.
**Sources:** MongoDB Java driver API, Panache @MongoEntity collection attributes
**Depends on:** D34
**Exploration:** quick
**Status:** captured

## D37: BSON PojoCodecProvider for document marshalling

**Choice:** MongoDB driver's built-in `PojoCodecProvider.builder().automatic(true)` for BSON↔POJO marshalling. Documents use `@BsonId` and `@BsonProperty` annotations from `org.bson.codecs.pojo.annotations`. Codec registry configured once when obtaining `MongoDatabase` — both framework wrappers do this identically.
**Alternatives:**
- Jackson-based codec (`JacksonCodecProvider`) — more flexible serialization, reuses existing ObjectMapper config. But adds Jackson dependency to core, introduces D1 Jackson 2/3 concerns, and BSON native codecs are more efficient.
- Manual codec per document — maximum control but enormous boilerplate for 13 document types.
**Rationale:** `PojoCodecProvider` is part of the MongoDB driver itself — zero additional dependencies. It handles `@BsonId`, `@BsonProperty`, nested documents, lists, and enums natively. The existing Panache documents already use these annotations (Panache uses the same codec internally). Stripping `PanacheMongoEntityBase` and keeping the BSON annotations produces codec-compatible POJOs with minimal changes.
**Trade-offs:** Less flexible than Jackson for complex custom serialization. Not needed — all document types are simple field mappings.
**Sources:** MongoDB driver PojoCodecProvider docs, existing document class @BsonId/@BsonProperty usage
**Depends on:** D34
**Exploration:** quick
**Status:** captured

## D38: Shared index initialization with framework lifecycle triggers

**Choice:** Core provides an `IndexInitializer` POJO that takes `MongoDatabase` and creates the 5 compound unique indexes. Pure logic — `database.getCollection(...).createIndex(keys, options)`. Quarkus triggers via `@Observes StartupEvent`; Spring triggers via `@EventListener(ApplicationReadyEvent.class)`.
**Alternatives:**
- Framework-specific index creation — duplicate the 5 index definitions in each module. Simple but divergence risk.
- Spring Data MongoDB auto-index (`@Indexed`, `@CompoundIndex` on document classes) — only works with Spring Data, not with raw MongoCollection. Would require Spring Data dependency in core or duplicate annotations.
**Rationale:** Index definitions are pure MongoDB operations — `createIndex()` with `Indexes.compoundIndex()` and `IndexOptions.unique(true)`. No framework involvement. The only framework-specific piece is the lifecycle event that triggers `init()`. Consistent with D30 (RlsPolicySetup extraction — same pattern: pure DDL/schema logic in core, framework lifecycle triggers in wrapper).
**Trade-offs:** None significant. Index creation is idempotent.
**Sources:** MongoIndexInitializer source (5 compound indexes), D30 (RlsPolicySetup pattern)
**Depends on:** D34, D36
**Exploration:** quick
**Status:** captured

## D39: Same-module core extraction — no new Maven modules for REST POJOs

**Choice:** Core POJOs live in the same module as their @Path resource (e.g., `WorkItemBulkCore` lives in `rest/`), in a `.core` subpackage. A `@Produces @ApplicationScoped` method in the resource module wires the core POJO as a CDI bean. The spring-generator picks up the `@Produces` and generates a `@Bean` in work-spring automatically.
**Alternatives:**
- Per-module -core counterparts (rest-core/, federation-core/, queues-core/, etc.) — follows the platform convention strictly. Creates 6-9 new Maven modules for 25 thin POJOs (most with 1-3 methods). Each would have the exact same dependency graph as its parent module.
- Consolidated into existing cores (runtime-core, work-support-core) — fewer modules but muddles dependency boundaries. runtime-core would need imports from ledger, queues, federation, etc.
**Rationale:** The work repo's Spring story is "generate REST controllers for the API surface," not "run the full work runtime in Spring." The resources inject module-local services — extracting to separate modules would create a mirror dependency graph with no structural benefit. 6-9 new modules for 25 thin POJOs is YAGNI. The core POJOs are pure Java (constructor injection, no CDI annotations) — they're framework-neutral in code, just not in module location.
**Trade-offs:** Breaks the platform convention of "every CDI module gets a -core counterpart." Acceptable for the work repo's scope. If full dual-framework parity becomes a requirement later, extracting to separate modules is mechanical and non-breaking.
**Sources:** Work repo module structure, rest-spring-generator Jandex scanning (module-agnostic), spring-generator @Produces → @Bean pipeline, resource audit (25 resources across 9 modules)
**Exploration:** quick
**Status:** captured

## D40: Mirror pattern — one core POJO per resource with matching methods

**Choice:** One core POJO per resource with matching method names, typed return values (no `Response`), and constructor-injected dependencies. Resource becomes a 1-line-per-method adapter delegating to the core POJO. `@Transactional` moves from the resource to the core POJO (`jakarta.transaction.Transactional` — supported natively by both Quarkus and Spring). Inner records (DTOs) become top-level classes in the core subpackage.
**Alternatives:**
- Service consolidation — group related resources into fewer service classes (e.g., all WorkItem* resources → one WorkItemRestService). Fewer classes but larger interfaces, harder to trace method origins, and breaks the 1:1 generator assumption.
- Resource-as-delegate — enhance the generator to delegate to the resource class itself. Requires the resource to be Spring-instantiable (constructor injection, no CDI-specific features). Avoids extraction but couples the generator to CDI-aware classes.
**Rationale:** 1:1 mapping is mechanical and predictable. The rest-spring-generator's sample test (`SampleResource` → `SampleCore`) already demonstrates this exact pattern. No judgment calls about grouping. The generator finds the first `@Inject` field as the delegate type and generates `delegate.methodName(args)` — mirror pattern satisfies this contract directly.
**Trade-offs:** 25 new POJO classes + 19 extracted record files. Mechanical overhead, but each extraction is a straightforward move of business logic with return type change from `Response` to typed.
**Sources:** RestControllerWriterTest (SampleResource/SampleCore pattern), resource audit (25 resources, 81 methods, 19 inner records)
**Depends on:** D39
**Exploration:** quick
**Status:** captured

## D41: Generator enhancement — Multi/Flow.Publisher → SseEmitter translation

**Choice:** Enhance `RestControllerWriter` to detect `java.util.concurrent.Flow.Publisher<T>` return types and generate Spring MVC `SseEmitter`-based controller methods. After core extraction, the 2 SSE resources (ProgressResource.streamEvents, QueueResource.streamQueueEvents) return `Flow.Publisher<T>` instead of `Multi<T>`. The Quarkus resource converts via `Multi.createFrom().publisher()`. The generated Spring controller creates an `SseEmitter`, subscribes to the publisher, and sends events.
**Alternatives:**
- Skip SSE endpoints — exclude from core extraction and generator. Hand-write Spring equivalents later. Incomplete coverage.
- Hand-write Spring SSE controllers in work-spring — outside the generator, bypasses the generation pipeline and drift detection.
**Rationale:** `Flow.Publisher<T>` is the JDK 9+ reactive streams standard. Both Mutiny (`Multi.createFrom().publisher()`) and Reactor (`Flux.from()`) interop with it natively. Using it as the core streaming abstraction is framework-neutral and future-proof. The generator enhancement is small (one new case in `wrapReturnType` + `buildMethodBody`) and applies to any future SSE endpoints across all repos.
**Trade-offs:** Adds `SseEmitter` dependency to generated controllers (part of spring-webmvc, already on classpath). `Flow.Publisher` is a pull-based API; both framework adapters handle backpressure internally.
**Sources:** ProgressResource.streamEvents(), QueueResource.streamQueueEvents(), java.util.concurrent.Flow.Publisher API, SseEmitter Spring MVC docs
**Depends on:** D40
**Exploration:** quick
**Status:** captured

## D42: AsyncApiResource exclusion — hand-written in work-spring

**Choice:** Skip `AsyncApiResource` (0 `@Inject` fields, reads classpath resource directly) from core extraction and generator. Hand-write a Spring equivalent in work-spring if needed. The generator requires a delegate — resources with no injectable dependencies produce a null delegate and would NPE in the writer.
**Alternatives:**
- Enhance scanner to handle no-delegate resources — generate a controller that instantiates the resource directly. Speculative — only 1 resource across the entire codebase has this shape.
- Extract a core POJO with no dependencies — wrapping a classpath resource read in a POJO adds indirection for no benefit.
**Rationale:** YAGNI. One resource without dependencies doesn't justify a generator enhancement. The resource serves a static OpenAPI/AsyncAPI spec file — if Spring needs it, a `@GetMapping` returning a classpath resource is 5 lines.
**Trade-offs:** AsyncApiResource has no Spring equivalent unless hand-written. Acceptable — it's a documentation endpoint, not a business API.
**Sources:** AsyncApiResource source (0 @Inject, 1 GET method returning classpath resource), RestResourceScanner delegate detection logic
**Exploration:** quick
**Status:** captured

## D43: Spring-generator CDI bridging — type-argument correspondence

**Choice:** Enhance the spring-generator to bridge `Event<T>` → `ApplicationEventPublisher` and `Instance<T>` → `ObjectProvider<T>` using type-argument correspondence between `@Produces` method params and core POJO constructor params. Three mappings: `Event<T>` ↔ `Consumer<T>` (event bridge), `Instance<T>` ↔ nullable/`Supplier<T>` (optional bridge), `@Any Instance<T>` ↔ `List<T>` (multi bridge).
**Alternatives:**
- Positional matching between producer and constructor params — fragile, producer methods may reorder or transform params before passing to constructor.
- Annotations on core POJO constructor params (`@EventBridge`, `@OptionalDep`) — requires modifying all existing core POJOs, introduces framework coupling in framework-neutral code.
- Scan `@ApplicationScoped` Cdi* classes instead of `@Produces` methods — different entry point but same bridging problem. May be needed as a follow-up for beans declared via subclass pattern rather than producer methods.
**Rationale:** Type-argument matching is unambiguous — `Consumer<FooEvent>` can only correspond to `Event<FooEvent>`. No positional fragility, no annotations needed on core POJOs, works with existing codebase unchanged.
**Trade-offs:** Won't cover beans declared via `Cdi*` subclass pattern (8 beans in qhorus) — these use `@ApplicationScoped` classes extending core POJOs instead of `@Produces` methods. Requires a separate scanner enhancement. Addressed iteratively.
**Sources:** `JandexProducerScanner.java` lines 100-101 (hasCdiDeps flag), `AutoConfigurationWriter.java` `buildEnhancedBeanMethod()`, qhorus `RuntimeAutoConfiguration.java` (50 hand-written beans)
**Exploration:** deep-analysis
**Status:** captured

## D44: Qhorus REST — separate qhorus-rest-spring module, A2A excluded

**Choice:** Create a new `qhorus-rest-spring` module (like `work-rest-spring`) that scans all 5 resource-bearing modules via `rest-spring-generator`. Exclude A2AResource (JAX-RS native SSE + JSON-RPC dispatch). Extract core POJOs for 9 resources across runtime, compliance-report, a2a-outbound, slack-channel, and webhook-observer.
**Alternatives:**
- Add rest-spring-generator to existing runtime-spring — awkward fit since 4 of 10 resources live outside `runtime/`. runtime-spring currently scans only `runtime/` for service beans.
- Generate all 10 including A2A — A2A uses JAX-RS native `SseEventSink`+`Sse`, not `Flow.Publisher`. The generator's SseEmitter translation won't trigger. Would require generator enhancement for a single endpoint.
**Rationale:** Consistent with work-rest-spring pattern from #494. Clean separation — runtime-spring stays focused on service bean wiring. A2A exclusion is YAGNI: one endpoint with non-standard SSE doesn't justify a generator enhancement.
**Trade-offs:** A2AResource has no Spring controller unless hand-written. ComplianceReportResource's `@RestForm FileUpload` method excluded from core (remaining 11 methods generate normally).
**Sources:** qhorus resource survey (10 resources, 59 methods, 5 modules), work-rest-spring/pom.xml (reference configuration), #494 design spec
**Depends on:** D39 (same-module extraction pattern), D40 (mirror pattern — one core POJO per resource)
**Exploration:** quick
**Status:** captured
