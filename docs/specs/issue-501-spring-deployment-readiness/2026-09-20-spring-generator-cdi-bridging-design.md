# Design: Spring Generator CDI Bridging (#510)

Enhance the spring-generator to automatically bridge CDI `Event<T>` and
`Instance<T>` types to their Spring equivalents, eliminating hand-written
auto-configuration classes that drift.

## Context

The spring-generator scans `@Produces` methods via Jandex and generates
Spring `@AutoConfiguration` classes with `@Bean` methods. It already follows
the core POJO constructor to resolve dependencies. However, it skips any
`@Produces` method whose params include `Event<T>` or `Instance<T>` — setting
`hasCdiDeps = true` and deferring to hand-written code.

In qhorus, this means 50 hand-written `@Bean` methods in
`RuntimeAutoConfiguration` — ~32 of which are blocked solely by this flag.
The core POJOs already accept framework-neutral types (`Consumer<T>`,
`Supplier<T>`, `List<T>`), so the bridging is mechanical and deterministic.

## Architecture

### Type-Argument Correspondence

The scanner already sees both layers:
- **Producer params:** `Event<FooEvent>`, `Instance<Bar>`, `@Any Instance<Baz>`
- **Constructor params:** `Consumer<FooEvent>`, nullable `Bar` / `Supplier<Bar>`, `List<Baz>`

Match them by type argument — `Consumer<FooEvent>` can only correspond
to `Event<FooEvent>`. No positional fragility.

### New ParamKind Values

Add to `ProducerDescriptor.ParamKind`:

| Kind | Constructor param type | Spring injection | Generated bridge expression |
|------|----------------------|------------------|-----------------------------|
| `EVENT_CONSUMER` | `Consumer<T>` | `ApplicationEventPublisher` | `event -> publisher.publishEvent(event)` |
| `OPTIONAL_DEP` | nullable `T` or `Supplier<T>` | `ObjectProvider<T>` | `provider.getIfAvailable()` |

The existing `LIST` kind already handles `List<T>` → `ObjectProvider<T>` —
extend it to cover `@Any Instance<T>` from producer params (currently blocked
by `hasCdiDeps`).

### Scanner Changes (`JandexProducerScanner`)

1. Collect CDI type mappings from `@Produces` params before setting
   `hasCdiDeps`: build a `Map<String, CdiBridge>` keyed by type argument
   (e.g., `FooEvent` → `EVENT`, `Bar` → `INSTANCE`).

2. In `resolveParamKind` for the constructor: when a param is
   `Consumer<T>` and `T` appears in the CDI bridge map as `EVENT`,
   return `EVENT_CONSUMER`. When param is `T` or `Supplier<T>` and `T`
   appears as `INSTANCE`, return `OPTIONAL_DEP`.

3. Only set `hasCdiDeps = true` when a CDI type has NO corresponding
   constructor param — genuinely unbridgeable.

### Writer Changes (`AutoConfigurationWriter`)

1. If any constructor param has kind `EVENT_CONSUMER`: add
   `ApplicationEventPublisher publisher` as a method parameter (once per
   `@Bean` method, regardless of how many event bridges exist).

2. For `EVENT_CONSUMER` params: emit `event -> publisher.publishEvent(event)`
   in the constructor args.

3. For `OPTIONAL_DEP` params: emit `ObjectProvider<T>` parameter +
   `provider.getIfAvailable()` (similar to existing `OPTIONAL` handling,
   but for types that come from `Instance<T>` rather than `Optional<T>`).

### Not In Scope (Iteration 2+)

- `@ApplicationScoped` Cdi* subclass scanning — ~8 qhorus beans use
  `CdiMessageService extends MessageService` instead of `@Produces`.
  Different entry point, same bridging needed. Separate enhancement.
- `ManagedExecutor` → `Executors.newVirtualThreadPerTaskExecutor()` bridging.
- `@ConfigMapping` params in `@Produces` methods — already partially handled
  by constructor following, but flagged as `hasCdiDeps` on lines 106-108.

## Verification

After implementation, run the generator against qhorus `runtime/` and diff
the output against `RuntimeAutoConfiguration`. Beans that match can be
deleted from the hand-written file. Target: eliminate ≥20 of 32 blocked beans
in iteration 1 (Event bridging alone covers ~14).

## References

- `JandexProducerScanner.java` — lines 100-101 (hasCdiDeps flag), 128-172 (constructor following)
- `AutoConfigurationWriter.java` — `buildEnhancedBeanMethod()` (existing param kind handling)
- `ProducerDescriptor.java` — `ParamKind` enum, `requiresManualConfig()` logic
- qhorus `RuntimeAutoConfiguration.java` — 50 hand-written beans, the drift surface
- D43 in decisions.md
