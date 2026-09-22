# Agent Gate Spring Auto-Configuration — Design Spec

**Issue:** casehubio/parent#500
**Epic:** casehubio/parent#501 (Spring deployment readiness)
**Date:** 2026-09-18

## Goal

Create `agent-gate-spring` — the Spring auto-configuration for the agent gate rate limiter. The CDI `@Decorator` pattern used by `GatedAgentProvider` has no direct Spring equivalent; this design translates it using `BeanPostProcessor` wrapping and core extraction, following the established callback-spring pattern (D14).

## Scope

Three work streams:

1. **Core extraction** — move `AgentGateProperties` and wrapping logic to agent-gate-core
2. **Spring module** — create agent-gate-spring with BeanPostProcessor + generated beans
3. **Integration** — update starter and E2E gate

### Out of scope

- General-purpose `@Decorator` → Spring generator (D13 — only 1 @Decorator exists)
- Changes to callback-spring (already works via BeanPostProcessor)
- Changes to consumer repos

## Architecture

### CDI @Decorator vs Spring BeanPostProcessor

In CDI, `@Decorator` is a container-managed pattern: the container injects the non-decorator implementation into the `@Delegate` field automatically. There is no Spring equivalent — `@Primary` on a wrapping bean that takes the same interface creates a circular dependency.

Spring's idiomatic solution is `BeanPostProcessor`: intercept bean creation, wrap the target after construction, return the wrapper. callback-spring already proves this pattern at `CallbackDecoratorBeanPostProcessor`.

```
CDI path:
  AgentProvider (RoutingAgentProvider)
    → CDI resolves @Delegate
    → GatedAgentProvider @Decorator wraps calls
    → Consumers get gated provider

Spring path:
  AgentProvider (RoutingAgentProvider) — @Bean from agent-router-spring
    → AgentGateBeanPostProcessor intercepts
    → Wraps with GatedAgentProviderWrapper (from core)
    → Consumers get gated provider
```

### Core extraction — agent-gate-core changes

#### AgentGateProperties interface

Extract from the Quarkus `@ConfigMapping` interface to a plain Java interface in agent-gate-core. The Quarkus interface extends it and adds `@ConfigMapping`. The Spring `@ConfigurationProperties` record implements it.

```java
// agent-gate-core (pure Java)
public interface AgentGateProperties {
    Duration acquireTimeout();
    Duration queryAcquireTimeout();
    Concurrency concurrency();
    TokenBucketConfig tokenBucket();
    SlidingWindow slidingWindow();
    Reaper reaper();

    interface Concurrency {
        int max();
    }
    interface TokenBucketConfig {
        double permitsPerSecond();
        int burstCapacity();
    }
    interface SlidingWindow {
        int maxActions();
        int windowSeconds();
    }
    interface Reaper {
        Duration scanInterval();
        Duration warnThreshold();
        boolean forceCloseEnabled();
        Duration forceCloseThreshold();
        Duration maxRegistryAge();
    }
}
```

Default values live in the framework-specific implementations:
- Quarkus: `@WithDefault` on the `@ConfigMapping` subinterface
- Spring: `@DefaultValue` on the `@ConfigurationProperties` record

#### GatedAgentProviderWrapper

New class in agent-gate-core implementing `AgentProvider`. Encapsulates all gating logic — strategy building, acquire/release, session wrapping.

```java
// agent-gate-core
public class GatedAgentProviderWrapper implements AgentProvider {

    private final AgentProvider delegate;
    private final SessionRegistry registry;
    private final List<AdmissionStrategy> strategies;
    private final List<AdmissionStrategy> sessionStrategies;
    private final List<AdmissionStrategy> invocationStrategies;
    private final Duration acquireTimeout;
    private final Duration queryAcquireTimeout;
    private final boolean active;

    public GatedAgentProviderWrapper(AgentProvider delegate,
                                     AgentGateProperties properties,
                                     SessionRegistry registry) {
        this.delegate = delegate;
        this.registry = registry;
        this.acquireTimeout = properties.acquireTimeout();
        this.queryAcquireTimeout = properties.queryAcquireTimeout();

        var built = new ArrayList<AdmissionStrategy>();
        // build from properties.slidingWindow(), .tokenBucket(), .concurrency()
        // ... identical to current GatedAgentProvider.init()

        this.strategies = List.copyOf(built);
        this.sessionStrategies = built.stream()
                .filter(s -> s.scope() == AdmissionStrategy.Scope.SESSION).toList();
        this.invocationStrategies = built.stream()
                .filter(s -> s.scope() == AdmissionStrategy.Scope.INVOCATION).toList();
        this.active = !built.isEmpty();
    }

    @Override
    public Multi<AgentEvent> invoke(AgentSessionConfig config) {
        // identical to current GatedAgentProvider.invoke()
    }

    @Override
    public AgentSession openSession(AgentSessionInit init) {
        // identical to current GatedAgentProvider.openSession()
    }
}
```

### Quarkus module refactor — agent-gate

`GatedAgentProvider` delegates to `GatedAgentProviderWrapper`:

```java
@Decorator
@Priority(Interceptor.Priority.APPLICATION)
public class GatedAgentProvider implements AgentProvider {

    @Inject @Delegate @Any AgentProvider delegate;
    @Inject AgentGateProperties properties;
    @Inject SessionRegistry registry;

    private GatedAgentProviderWrapper wrapper;

    protected GatedAgentProvider() {}

    @PostConstruct
    void init() {
        wrapper = new GatedAgentProviderWrapper(delegate, properties, registry);
    }

    @Override
    public Multi<AgentEvent> invoke(AgentSessionConfig config) {
        return wrapper.invoke(config);
    }

    @Override
    public AgentSession openSession(AgentSessionInit init) {
        return wrapper.openSession(init);
    }
}
```

The test constructor is removed — tests use `GatedAgentProviderWrapper` directly.

`AgentGateProperties` (Quarkus) changes to extend core:

```java
@ConfigMapping(prefix = "casehub.platform.agent.gate")
public interface AgentGateQuarkusProperties extends AgentGateProperties {
    // @WithDefault annotations on overridden methods
}
```

CDI injection in `GatedAgentProvider` uses the core type (`AgentGateProperties`), not the Quarkus subinterface. CDI satisfies this — `AgentGateQuarkusProperties` is assignable to `AgentGateProperties`, and `@ConfigMapping` beans are `@ApplicationScoped` singletons resolved by type hierarchy.

### Spring module — agent-gate-spring

The entire module is hand-written. The spring-generator's `@ConfigurationProperties` record generation does not support nested `@ConfigMapping` interfaces (it produces flat records only). Since the BPP and reaper also require hand-written code, there is no benefit to running the generator for just the `SessionRegistry` `@Bean`. All beans live in a single auto-configuration class.

#### Hand-written: @ConfigurationProperties record

Spring Boot's constructor binding requires concrete records for nested config groups. The record implements the core `AgentGateProperties` interface:

```java
@ConfigurationProperties(prefix = "casehub.platform.agent.gate")
public record AgentGateSpringProperties(
        @DefaultValue("PT30S") Duration acquireTimeout,
        @DefaultValue("PT5S") Duration queryAcquireTimeout,
        @DefaultValue ConcurrencyProperties concurrency,
        @DefaultValue TokenBucketProperties tokenBucket,
        @DefaultValue SlidingWindowProperties slidingWindow,
        @DefaultValue ReaperProperties reaper
) implements AgentGateProperties {

    public record ConcurrencyProperties(
            @DefaultValue("0") int max
    ) implements AgentGateProperties.Concurrency {}

    public record TokenBucketProperties(
            @DefaultValue("0.0") double permitsPerSecond,
            @DefaultValue("0") int burstCapacity
    ) implements AgentGateProperties.TokenBucketConfig {}

    public record SlidingWindowProperties(
            @DefaultValue("0") int maxActions,
            @DefaultValue("60") int windowSeconds
    ) implements AgentGateProperties.SlidingWindow {}

    public record ReaperProperties(
            @DefaultValue("60s") Duration scanInterval,
            @DefaultValue("5m") Duration warnThreshold,
            @DefaultValue("false") boolean forceCloseEnabled,
            @DefaultValue("30m") Duration forceCloseThreshold,
            @DefaultValue("24h") Duration maxRegistryAge
    ) implements AgentGateProperties.Reaper {}
}
```

#### Hand-written: AgentGateBeanPostProcessor

```java
class AgentGateBeanPostProcessor implements BeanPostProcessor, Ordered {

    private static final Logger LOG = Logger.getLogger(
            AgentGateBeanPostProcessor.class.getName());

    private final AgentGateProperties properties;
    private final SessionRegistry registry;

    AgentGateBeanPostProcessor(AgentGateProperties properties,
                                SessionRegistry registry) {
        this.properties = properties;
        this.registry = registry;
    }

    @Override
    public int getOrder() {
        return 2000; // Interceptor.Priority.APPLICATION
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName)
            throws BeansException {
        if (bean instanceof AgentProvider provider
                && !(bean instanceof GatedAgentProviderWrapper)) {
            LOG.info("Wrapping AgentProvider '" + beanName
                    + "' with agent gate rate limiter");
            return new GatedAgentProviderWrapper(provider, properties, registry);
        }
        return bean;
    }
}
```

#### Hand-written: SessionLeakReaper scheduling

```java
@Bean
SessionLeakReaper sessionLeakReaper(SessionRegistry registry,
                                     AgentGateProperties properties) {
    var reaper = properties.reaper();
    return new SessionLeakReaper(registry,
            reaper.warnThreshold(), reaper.forceCloseEnabled(),
            reaper.forceCloseThreshold(), reaper.maxRegistryAge());
}

@Scheduled(fixedDelayString = "${casehub.platform.agent.gate.reaper.scan-interval:60s}")
void reaperScan() {
    sessionLeakReaper.scan();
}
```

#### Hand-written: Auto-configuration class

```java
@AutoConfiguration
@AutoConfigureAfter(AgentRouterSpringAutoConfiguration.class)
@ConditionalOnClass(GatedAgentProviderWrapper.class)
@EnableConfigurationProperties(AgentGateSpringProperties.class)
public class AgentGateSpringAutoConfiguration {

    @Bean
    static AgentGateBeanPostProcessor agentGateBeanPostProcessor(
            AgentGateProperties properties, SessionRegistry registry) {
        return new AgentGateBeanPostProcessor(properties, registry);
    }

    @Bean
    SessionRegistry sessionRegistry() {
        return new SessionRegistry();
    }

    @Bean
    SessionLeakReaper sessionLeakReaper(SessionRegistry registry,
                                         AgentGateProperties properties) {
        // ...
    }
}
```

The `static` keyword on the BPP factory is significant — it ensures the processor is registered early in the Spring lifecycle, before other beans are created.

`@AutoConfigureAfter(AgentRouterSpringAutoConfiguration.class)` ensures the `RoutingAgentProvider` bean exists before the BPP tries to wrap it.

### Testing

**Unit tests (agent-gate-core):**
- `GatedAgentProviderWrapperTest` — test strategy building, gating, session wrapping using the core POJO directly. Migrated from existing `GatedAgentProviderTest`.

**Unit tests (agent-gate-spring):**
- `AgentGateBeanPostProcessorTest` — verify wrapping behavior: wraps AgentProvider, skips non-AgentProvider, skips already-wrapped, correct order.

**Integration (spring-integration-test):**
- Existing E2E gate verifies composition still boots with agent-gate-spring in the starter.

### Module dependencies

```
agent-gate-core
  ├── platform-api (AgentProvider, AgentEvent, AgentSession, etc.)
  └── (no new deps — already has these)

agent-gate (Quarkus)
  ├── agent-gate-core
  └── (existing deps unchanged)

agent-gate-spring (NEW)
  ├── agent-gate-core
  ├── spring-boot-autoconfigure
  └── (transitive: platform-api)

casehub-spring-boot-starter
  └── agent-gate-spring (added)
```

## References

- `agent-gate/src/main/java/.../GatedAgentProvider.java` — current CDI @Decorator implementation
- `agent-gate/src/main/java/.../AgentGateProperties.java` — @ConfigMapping interface to extract
- `agent-gate-core/src/main/java/.../AdmissionGate.java` — existing core admission gate
- `agent-gate-core/src/main/java/.../SessionLeakReaper.java` — existing core reaper POJO
- `callback-spring/src/main/java/.../CallbackDecoratorBeanPostProcessor.java` — BPP pattern reference
- D13 (scope), D14 (wrapping mechanism), D15 (properties extraction)
- D5 (constructor-scanning), D7 (@ConfigurationProperties records), D9 (identity Properties)
