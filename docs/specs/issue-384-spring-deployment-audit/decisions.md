# Decisions — #396 SpringParityRule

## D1: Parity check approach

**Choice:** POM-scanning Enforcer rule
**Alternatives:**
- Generator coverage manifest — more precise but duplicates existing verify-drift; adds generator complexity for observability the rule doesn't need
- Full Jandex scan in Enforcer — most granular but duplicates JandexProducerScanner; redundant with generator verify goals
**Rationale:** Bean-level parity is already enforced by generator verify goals. The actual gap is module-level: detecting when a Quarkus CDI module has no Spring generation path at all. POM-scanning solves this without duplicating existing infrastructure.
**Trade-offs:** Cannot detect a misconfigured generator that silently skips some @Produces methods — relies on generator verify goals for that.
**Sources:** DriftDetectionRule.java, spring-generator verify-drift executions in governance-spring/pom.xml and platform-spring/pom.xml, Maven Enforcer 3.x API docs
**Exploration:** deep-analysis
**Status:** captured

## D2: How to detect which modules need Spring coverage

**Choice:** Auto-exclude by naming convention + exceptions file
**Alternatives:**
- Full Jandex scan for @Produces/@ApplicationScoped — accurate but requires verify phase and duplicates generator logic
- CDI dependency check (scan POMs for quarkus-arc/cdi-api) — too broad, picks up 100+ modules
- Jandex plugin presence — also too broad (100+ modules have it, including -core, -api, etc.)
**Rationale:** Module naming conventions in this codebase are strong and consistent. Auto-excluding `-core`, `-api`, `-spring`, `-jpa-common`, `-jpa`, `-testing`, `-generator`, `-starter`, `-alpha` etc. reduces candidates to ~52. The exceptions file handles the remaining Quarkus-only modules (~10-15). New modules that don't match any pattern trigger a build failure forcing explicit categorization.
**Trade-offs:** Relies on naming conventions staying consistent. A module that breaks convention (e.g., Quarkus CDI module named `*-core`) would be silently excluded. Mitigated: all current modules follow conventions.
**Sources:** grep for jandex-maven-plugin across all modules, module naming analysis
**Exploration:** deep-analysis
**Depends on:** D1 (POM-scanning approach)
**Status:** captured

## D3: Where to execute the rule

**Choice:** Configure in `spring-integration-test` POM
**Alternatives:**
- Root POM — runs for every module in reactor, needs skip logic or last-module guard
- Dedicated aggregator module — more modules to maintain
**Rationale:** spring-integration-test is already the Spring composition gate. It runs last in the Spring module build order. Running the parity check there is a natural extension of its role.
**Trade-offs:** The check only runs when spring-integration-test is in the build (`-pl` partial builds skip it). Acceptable: partial builds don't need reactor-level validation.
**Sources:** spring-integration-test existing role as composition gate
**Exploration:** quick
**Status:** captured

## D4: Enforcer API version

**Choice:** New 3.x API (`AbstractEnforcerRule` with `@Named` + `@Inject`)
**Alternatives:**
- Legacy API (`implements EnforcerRule` with `EnforcerRuleHelper`) — what DriftDetectionRule uses, but can't inject MavenSession cleanly
**Rationale:** SpringParityRule needs MavenSession for reactor access. The 3.x API supports constructor injection of MavenSession, MavenProject, and other Maven components. Our enforcer-api dependency is already 3.5.0.
**Trade-offs:** Two API styles coexist in drift-detection module (DriftDetectionRule on legacy, SpringParityRule on 3.x). They're compatible — Plexus and JSR 330 discovery work side by side. Upgrading DriftDetectionRule is out of scope for #396.
**Sources:** Maven Enforcer 3.x custom rule docs, ReactorModuleConvergence source
**Exploration:** quick
**Status:** captured

## D5: Meta-verify scope

**Choice:** Check generator completeness only for *-spring modules that have generators configured
**Alternatives:**
- Require ALL *-spring modules to have generators — wrong for hand-written modules (callback-spring, agent-gate-spring, mcp-spring use BeanPostProcessor patterns, not generators)
**Rationale:** Some *-spring modules are intentionally hand-written. The meta-verify check should be: "if you configured a generator, did you configure it correctly (both generate and verify goals)?" — not "you must use a generator."
**Trade-offs:** Doesn't catch a hand-written *-spring module that SHOULD be generated. Mitigated: code review catches this; the module-level check ensures coverage exists regardless of mechanism.
**Sources:** callback-spring/src (hand-written BeanPostProcessor), agent-gate-spring (BeanPostProcessor wrapper)
**Exploration:** quick
**Depends on:** D1 (POM-scanning approach)
**Status:** captured

---

# Decisions — #398 Platform Observability + Spring Actuator

## D10: Health check logic placement

**Choice:** Framework modules directly — no core POJOs for health checks
**Alternatives:**
- Core POJOs delegated by framework modules — ensures exact consistency but adds 5 wrapper classes for 5 one-liner checks; abstraction without substance
**Rationale:** Health check logic is 1-3 lines per indicator (e.g., `registry.all().isEmpty()`). Both framework modules implement the same trivial logic independently. The cost of inconsistency is near zero — each check queries a single SPI method.
**Trade-offs:** Slight risk of divergence if health check semantics evolve. Mitigated: health checks are stable once defined.
**Sources:** ModelRegistry.java, DeliveryChannelRegistry.java, AgentBackend.java, ScimClient.java, CertificateExpiryMonitor.java
**Exploration:** quick
**Status:** captured

## D11: Reactive timer wrapping for AgentProvider

**Choice:** Deferred timer with Mutiny onTermination — `Multi.createFrom().deferred(() -> { sample = Timer.start(); return delegate.invoke(config).onTermination().invoke(() -> sample.stop(timer)); })`
**Alternatives:**
- Observation API wrapping — more structured (context propagation, conventions) but heavier; requires micrometer-observation dependency and more boilerplate for the same result
**Rationale:** Timer must measure from subscription to stream termination (not method call to return) because `Multi<AgentEvent>` is a cold stream. `deferred` ensures the timer starts on subscription. `onTermination` covers completion, failure, and cancellation. The core module already depends on agent-api which depends on Mutiny — no new dependency.
**Trade-offs:** Ties the core module to Mutiny operators. Acceptable since Mutiny is already a transitive dependency via agent-api.
**Sources:** AgentProvider.java (line 39: `Multi<AgentEvent> invoke(AgentSessionConfig config)`)
**Exploration:** quick
**Status:** captured

## D13: Certificate expiry health indicator — JDK keystore reader

**Choice:** Pure JDK `KeyStoreExpiryChecker` POJO in `platform-observability-core`
**Alternatives:**
- Extract signing-core from platform-signing — more principled but larger scope; refactors an existing module for a health check
- Quarkus-only, defer Spring — violates parity, fills backlog
- Duplicate KeyStoreManager logic — wrong; KeyStoreManager wraps DSS for signing, this is read-only metadata
**Rationale:** Certificate expiry checking is a pure JDK operation (`java.security.KeyStore` + `X509Certificate.getNotAfter()`). No DSS needed. ~30 lines. Both framework modules construct from config and wire into their health indicator. Not duplicating `KeyStoreManager` — different purpose (read-only metadata vs signing), different dependency profile (pure JDK vs DSS).
**Trade-offs:** If platform-signing is deployed, both `KeyStoreManager` and `KeyStoreExpiryChecker` read the same keystore file. Acceptable: reads are idempotent, health checks are infrequent (30s-5m), and the two serve different purposes.
**Sources:** CertificateExpiryMonitor.java, KeyStoreManager.java (DSS dependency: Pkcs12SignatureToken, DSSPrivateKeyEntry), java.security.KeyStore JDK API
**Exploration:** deep-analysis
**Status:** captured

## D14: Starter inclusion

**Choice:** Include `platform-spring-actuator` in the core `spring-boot-starter`
**Alternatives:**
- Separate optional dependency — cleanest separation but adds friction; most consumers will want it and need to discover it exists
**Rationale:** Actuator is standard practice for production Spring Boot apps. The module guards all beans with `@ConditionalOnClass(HealthIndicator.class)` — if `spring-boot-starter-actuator` isn't on classpath, nothing activates. Zero cost when absent, immediate value when present.
**Trade-offs:** Adds a transitive dependency on `micrometer-core` (via `platform-observability-core`) to the starter. Micrometer is already pulled by `spring-boot-starter-actuator`, so this only matters when actuator is absent — and the `@ConditionalOnClass` guard means the beans never register anyway.
**Sources:** spring-boot-starter/pom.xml, spring-integration-test/pom.xml (already has spring-boot-starter-actuator)
**Exploration:** quick
**Status:** captured

## D15: Metric naming convention

**Choice:** `casehub.platform.*` prefix
**Alternatives:**
- `casehub.*` (flatter) — shorter but risks collision with domain-specific metrics from consumer apps
**Rationale:** Groups all platform metrics under one namespace. Examples: `casehub.platform.agent.invocations`, `casehub.platform.model.registry.size`, `casehub.platform.acl.can_access`. Follows Micrometer lowercase dot-separated convention.
**Trade-offs:** Slightly longer metric names. Acceptable for namespace clarity.
**Sources:** Micrometer naming convention docs
**Exploration:** quick
**Status:** captured

## D12: SCIM health check classification

**Choice:** Readiness check — `/actuator/health/readiness` in Spring, `@Readiness` in MicroProfile
**Alternatives:**
- Liveness check — risks container restart if SCIM is temporarily unreachable; usually wrong for external dependencies
- Custom health group ("dependencies") — more granular but requires consumer configuration to act on it
**Rationale:** SCIM unavailability means the app can't resolve group memberships — it should stop receiving traffic. But the app itself is alive. Readiness is the correct classification for external dependency checks.
**Trade-offs:** Requires Kubernetes readiness probe configuration to act on this signal. Without it, the readiness status is informational only.
**Sources:** ScimClient.java, Spring Boot Actuator health groups docs
**Exploration:** quick
**Status:** captured

---

# Decisions — #394 Spring Module Merge

## D6: Per-module AutoConfiguration generation

**Choice:** spring-generator iterates over `resolveModules()` individually, generating a separate AutoConfiguration class per source module
**Alternatives:**
- Single merged AutoConfiguration per execution — wrong because one `@ConditionalOnClass` anchor would activate/deactivate ALL beans from ALL source modules together; removing one core module breaks all beans
- Manual AutoConfiguration classes per merged module — defeats the purpose of code generation
**Rationale:** Each source module's beans need independent `@ConditionalOnClass` guards. Per-module iteration preserves the same isolation that existed when they were separate Maven modules. The combined imports file lists all generated classes.
**Trade-offs:** Slightly more complex generator logic (iterate + accumulate imports). Acceptable — the complexity is in the generator, not in the modules.
**Sources:** SpringGeneratorMojo.java, AbstractGeneratorMojo.java (already supports quarkusModules), AutoConfigurationWriter.java (selectAnchorType picks one anchor per call)
**Exploration:** deep-analysis
**Status:** captured

## D7: Imports file merging strategy

**Choice:** Generator reads any existing hand-written imports file from `src/main/resources/META-INF/spring/`, appends generated entries, writes merged output
**Alternatives:**
- Hand-written imports listing all entries (status quo from #393) — manual maintenance burden, error-prone when @Produces methods change
- Separate imports files per generator — Spring Boot only supports one file per JAR
**Rationale:** The generator already knows which AutoConfiguration classes it produces. Reading the hand-written file and appending generated entries gives one correct merged file automatically. Eliminates the class of #393-style collision bugs.
**Trade-offs:** Generator now depends on reading source resources, adding a coupling that didn't exist before. Acceptable — the alternative is worse (manual maintenance).
**Sources:** #393 fix (cb8c6524), platform-spring imports file (3 entries: 1 manual + 2 generated)
**Exploration:** quick
**Status:** captured

## D8: agent-gate-spring hand-written code handling

**Choice:** Move source files directly into agent-spring/src/main/java; ManualBeanScanner prevents generator conflicts
**Alternatives:**
- Keep agent-gate-spring as a separate module — doesn't reduce module count, inconsistent with the merge goal
**Rationale:** ManualBeanScanner already scans `src/main/java` for `@Bean` return types and excludes them from generation. The hand-written BeanPostProcessor pattern doesn't conflict with generated `@Bean` methods. agent-gate's hand-written imports entry gets included via the merged imports file.
**Trade-offs:** One module (agent-spring) now has both generated and hand-written code. This already works for platform-spring — proven pattern.
**Sources:** ManualBeanScanner.java, agent-gate-spring/src (3 files + 1 test)
**Exploration:** quick
**Depends on:** D7 (imports merging)
**Status:** captured

## D9: streams-spring structure

**Choice:** Plain merge — move all 4 hand-written Java files into one module with no generator
**Alternatives:**
- Template generator for streams — 176 LOC across 4 files doesn't justify a generator
**Rationale:** The 4 stream auto-configs are each 33-52 LOC with framework-specific listener annotations (@KafkaListener, @RabbitListener, @Scheduled, CamelContext). Generator would need to understand framework-specific adapter patterns — not worth the complexity for 176 LOC total.
**Trade-offs:** 4 classes in one module instead of 4 modules with 1 class each. Net gain: 3 fewer POMs.
**Sources:** KafkaStreamSpringAutoConfiguration.java (53 LOC), dim6 audit analysis
**Exploration:** quick
**Status:** captured
