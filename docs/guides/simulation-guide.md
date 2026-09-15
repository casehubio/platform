# Simulation Framework — User Guide

> Configurable simulation for any SPI. Real responses when you have a real
> backend. Simulated responses when you don't. Captured traffic when you
> want to build a corpus.

---

## When to use this

You're building an app that depends on platform SPIs — `CaseMemoryStore`,
`AgentProvider`, `PreferenceStore`, or any other SPI. In production, a real
implementation is wired. In dev, tests, or demos, you want controlled
responses without standing up the full backend.

The simulation framework gives you three modes per SPI method:

| Mode | What happens | When to use |
|------|-------------|-------------|
| **Simulation** | Strategy resolves responses from a corpus | Dev, demos, load testing |
| **Capture** | Real impl runs; input/output recorded to corpus | Building a corpus from production traffic |
| **Passthrough** | No simulation, no capture — transparent delegation | Production, or methods you don't need to simulate |

All three are configured per method. A multi-method SPI like `CaseMemoryStore`
can simulate `query`, capture `store`, and pass through `erase` — simultaneously.

---

## Quick start

### 1. Add dependencies

```xml
<!-- Core contracts — always needed -->
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-platform-simulation-api</artifactId>
    <version>${casehub.version}</version>
</dependency>

<!-- Strategy implementations (Sequential, KeyLookup, Random, RecordedReplay) -->
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-platform-simulation-core</artifactId>
    <version>${casehub.version}</version>
</dependency>

<!-- In-memory corpus — use for tests and ephemeral dev -->
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-platform-simulation-inmem</artifactId>
    <version>${casehub.version}</version>
    <scope>test</scope>
</dependency>
```

For the annotation processor (generates `@Decorator` per `@SimulationEligible` SPI):

```xml
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-platform-simulation-generator</artifactId>
    <version>${casehub.version}</version>
    <scope>provided</scope>
</dependency>
```

For AgentProvider simulation (Path B):

```xml
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-platform-agent-simulation-core</artifactId>
    <version>${casehub.version}</version>
</dependency>
```

### 2. Configure simulation in application.properties

```properties
# Simulate query with sequential responses
casehub.simulation.case-memory-store.query.strategy=sequential

# Capture real store calls for corpus building
casehub.simulation.case-memory-store.store.capture=true

# erase: no config → passthrough
```

### 3. Seed a corpus

```java
@Inject InMemorySimulationCorpus<String, String> corpus;

void seedTestData() {
    corpus.seed("case-memory-store.query", List.of(
        new InvocationRecord<>("tenant-1", "patient-123",
            "patient-123", "Lab results for patient 123", Instant.now()),
        new InvocationRecord<>("tenant-1", "patient-456",
            "patient-456", "X-ray for patient 456", Instant.now())));
}
```

That's it. The generated `@Decorator` intercepts `CaseMemoryStore.query()`,
resolves from the corpus via the configured strategy, and returns the seeded
response. No changes to the SPI, no changes to the NoOp, no changes to
production code.

---

## Core concepts

### Qualified name

Every simulated method is identified by a **qualified name**:
`"spi-name.method-name"`. This is the namespace key that separates corpus
data, strategy configuration, and capture recording across methods.

```
case-memory-store.query    → one corpus, one strategy
case-memory-store.store    → different corpus, different strategy
agent-provider.invoke      → yet another
```

The SPI name comes from `@SimulationEligible(name = "case-memory-store")`.
If `name` is omitted, it defaults to the kebab-case of the interface name.

### Corpus

A `SimulationCorpus<I, O>` stores input/output pairs keyed by qualified name.
Strategies read from it; capture mode writes to it.

```java
// Seed with known data
corpus.seed("my-spi.method", List.of(
    new InvocationRecord<>("tenant", "key", input, output, Instant.now())));

// Record a real invocation
corpus.record("my-spi.method", "tenant-1", "key", input, output);

// Look up by key or index
corpus.lookupByKey("my-spi.method", "patient-123");  // → Optional<O>
corpus.lookupByIndex("my-spi.method", 0);             // → Optional<O>
```

Two backends ship:

| Backend | Scope | Behaviour |
|---------|-------|-----------|
| `NoOpSimulationCorpus` | Default | Returns empty, discards records — active when no backend module on classpath |
| `InMemorySimulationCorpus` | `@Alternative @Priority(100)` | ConcurrentHashMap — volatile, thread-safe, lost on restart |

### Strategy

A `SimulationStrategy<I, O>` resolves a response from the corpus. Four
strategies ship:

| Strategy | Key | How it picks a response |
|----------|-----|------------------------|
| **Sequential** | `sequential` | Returns entries in insertion order. Wraps at end (WRAP) or throws (THROW) |
| **Key-lookup** | `key-lookup` | Extracts a key from the input, exact-matches against corpus keys. Requires a `KeyExtractor` |
| **Random** | `random` | Samples randomly from corpus. Seeded `Random` for reproducibility |
| **Recorded-replay** | `recorded-replay` | Key-first (deterministic), sequential fallback when key not found. Requires a `KeyExtractor` |

Every strategy has two methods:

```java
O resolve(I input);           // return a simulated response
boolean canResolve(I input);  // check without consuming
```

### SimulationRuntime

The `SimulationRuntime` wires strategies to qualified names at boot time.
Generated decorators and backend adapters inject it and call:

```java
Optional<SimulationStrategy<I, O>> strategy = runtime.strategyFor("my-spi.method");
if (strategy.isPresent() && strategy.get().canResolve(input)) {
    return strategy.get().resolve(input);
}
// else: delegate to real impl
```

---

## Strategies in detail

### Sequential — cycling through a list

Returns responses in insertion order. When the list runs out, behaviour
depends on the exhaustion policy.

```properties
casehub.simulation.my-spi.method.strategy=sequential
# Optional — default is WRAP
casehub.simulation.my-spi.method.exhaustion-policy=THROW
```

| Policy | Behaviour |
|--------|-----------|
| `WRAP` (default) | Cycles back to the beginning — infinite responses from a finite corpus |
| `THROW` | Throws `SimulationExhaustedException` — use when you expect exactly N calls |

**Use for:** load testing (WRAP), scenario testing with known call count (THROW).

```java
// WRAP: always returns something
strategy.resolve("x");  // → "first"
strategy.resolve("x");  // → "second"
strategy.resolve("x");  // → "first" (wrapped)

// THROW: fails when done
strategy.resolve("x");  // → "only-one"
strategy.resolve("x");  // → SimulationExhaustedException
```

### Key-lookup — deterministic matching

Same input → same output, every time. Requires a `KeyExtractor<I>` that
derives a lookup key from the method input.

```properties
casehub.simulation.my-spi.method.strategy=key-lookup
```

```java
// Register how to derive a key from the input
runtime.registerExtractor("my-spi.method",
    (String patientId) -> patientId.toLowerCase());
```

Throws `SimulationKeyNotFoundException` when no corpus entry matches.

**Use for:** deterministic test scenarios, demo environments.

### Random — sampling from corpus

Picks a random entry from the corpus each time. Pass a seeded `Random` for
reproducible tests.

```properties
casehub.simulation.my-spi.method.strategy=random
```

**Use for:** load testing with varied responses, fuzzing.

### Recorded-replay — key-first with fallback

Tries key-based lookup first. If the key matches a corpus entry, returns
it (deterministic). If not, falls back to sequential traversal.

```properties
casehub.simulation.my-spi.method.strategy=recorded-replay
```

```java
runtime.registerExtractor("my-spi.method", (String input) -> input);
```

**Use for:** replaying captured traffic — known requests replay exactly,
unexpected requests get best-effort sequential responses.

---

## Writing KeyExtractors

A `KeyExtractor<I>` is a `@FunctionalInterface` — one method:

```java
String extract(I input);
```

The extracted key is matched against corpus entry keys. The key must be
**deterministic** — same input always produces the same key.

### Patterns

**Identity** — the input IS the key:
```java
runtime.registerExtractor("spi.method", (String id) -> id);
```

**Normalizing** — strip noise for stable matching:
```java
runtime.registerExtractor("spi.method", (String prompt) ->
    prompt.replaceAll("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}", "<UUID>")
          .replaceAll("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}", "<TIMESTAMP>")
          .toLowerCase().trim());
```

**Composite** — for multi-parameter methods:
```java
runtime.registerExtractor("spi.method", (Object[] args) ->
    "department=" + args[0] + ":severity=" + args[1]);
```

**Case-insensitive:**
```java
runtime.registerExtractor("spi.method", (String input) -> input.toLowerCase());
```

Register extractors at startup — typically in a `@Startup @ApplicationScoped`
adapter bean:

```java
@ApplicationScoped
public class MySimulationAdapter {
    @Inject SimulationRuntime simulation;

    void onStartup(@Observes StartupEvent event) {
        simulation.registerExtractor("my-spi.query",
            (QueryInput input) -> input.domain() + ":" + input.question());
    }
}
```

---

## Capture mode

Capture records real SPI invocations to the corpus while the real
implementation runs. Use it to build a corpus from production or staging
traffic.

```properties
# Enable capture (no strategy needed — real impl runs)
casehub.simulation.my-spi.query.capture=true
```

The generated decorator does this automatically:

```java
// Generated code (simplified):
Result result = delegate.query(input);
if (simulation.captureEnabled("my-spi.query")) {
    simulation.capture("my-spi.query", tenancyId, input, result);
}
return result;
```

Captured data is tenant-scoped — each invocation records the tenant context.

### Capture → replay workflow

1. Deploy with capture enabled against a real backend
2. Run the scenarios you want to simulate
3. Export the corpus (or keep it in memory)
4. Switch config from capture to simulation:
   ```properties
   # Before: capture
   casehub.simulation.my-spi.query.capture=true
   # After: simulate
   casehub.simulation.my-spi.query.strategy=recorded-replay
   ```
5. The captured corpus now drives simulation

### Capture with keys

For deterministic replay, capture with explicit keys:

```java
runtime.capture("my-spi.query", tenancyId, key, input, output);
```

The generated decorator derives keys from the registered `KeyExtractor`.

---

## Integration paths

### Path A — Generated @Decorator (simple SPIs)

For SPIs with direct CDI injection and simple request-response methods.
Add `@SimulationEligible` to the SPI interface:

```java
@SimulationEligible(name = "case-memory-store")
public interface CaseMemoryStore {
    List<Memory> query(MemoryQuery query);
    void store(String tenancyId, MemoryInput input);
    void erase(EraseRequest request);
}
```

The `simulation-generator` annotation processor scans this via Jandex and
generates a `@Decorator` class (`SimulatedCaseMemoryStore`) that:

- Checks `SimulationRuntime.strategyFor()` for each method
- Delegates to the real impl when no strategy is configured
- Captures invocations when capture is enabled
- Runs at `@Priority(APPLICATION + 200)` — after the real impl's priority

No manual decorator code needed. No changes to the SPI or its implementations.

### Path B — Backend integration (routed SPIs)

For SPIs with existing multi-backend routing (like `AgentProvider` →
`RoutingAgentProvider` → `AgentBackend`), simulation registers as a backend
rather than a decorator.

`SimulatedAgentBackend` (key: `"simulated"`) is dispatched by the existing
router when the model resolves to it:

```properties
# In ModelRegistry or application.properties:
casehub.simulation.agent-provider.invoke.strategy=sequential
```

```java
// Seed with agent responses
corpus.seed("agent-provider.invoke", List.of(
    new InvocationRecord<>("t1", null,
        new AgentSimulationInput("system-prompt", "hello", null),
        List.of(new AgentEvent.TextDelta("Simulated response")),
        Instant.now())));
```

---

## Configuration reference

All configuration lives under the `casehub.simulation` prefix:

```
casehub.simulation.<spi-name>.<method-name>.strategy=<strategy-key>
casehub.simulation.<spi-name>.<method-name>.capture=true|false
casehub.simulation.<spi-name>.<method-name>.exhaustion-policy=WRAP|THROW
```

| Property | Values | Default |
|----------|--------|---------|
| `strategy` | `sequential`, `key-lookup`, `random`, `recorded-replay` | none (passthrough) |
| `capture` | `true`, `false` | `false` |
| `exhaustion-policy` | `WRAP`, `THROW` | `WRAP` |

### SPI name resolution

The SPI name in the config key comes from `@SimulationEligible(name = "...")`.
If omitted, it defaults to the kebab-case of the interface name:

| Interface | Default name |
|-----------|-------------|
| `CaseMemoryStore` | `case-memory-store` |
| `PreferenceStore` | `preference-store` |
| `AgentProvider` | `agent-provider` |

---

## Module dependency map

```
simulation-api          zero-dep: contracts, NoOp corpus
  ├── simulation-core   strategies, SimulationRuntime, SimulationConfig
  ├── simulation-inmem  InMemorySimulationCorpus @Alternative
  ├── simulation-generator  APT: generates @Decorator per @SimulationEligible
  └── agent-simulation-core SimulatedAgentBackend (Path B)
```

| Module | Your pom.xml scope | When to add |
|--------|--------------------|-------------|
| `simulation-api` | compile | Your SPI uses `@SimulationEligible` |
| `simulation-core` | compile | You need SimulationRuntime (strategy resolution) |
| `simulation-inmem` | test or compile | You need an in-memory corpus |
| `simulation-generator` | provided | Your SPI has `@SimulationEligible` and you want generated decorators |
| `agent-simulation-core` | compile | You want to simulate AgentProvider responses |

---

## Error handling

| Exception | When | What to do |
|-----------|------|------------|
| `SimulationExhaustedException` | Sequential strategy with THROW policy, corpus empty | Seed more data, or switch to WRAP |
| `SimulationKeyNotFoundException` | Key-lookup with no matching corpus entry | Seed the missing key, or use recorded-replay for fallback |
| `SimulationConfigException` | Unknown strategy name, or key-lookup without extractor | Check config spelling, register your KeyExtractor at startup |

All three extend `RuntimeException` — they propagate through the SPI
contract without requiring checked exception declarations.

---

## What simulation does NOT do

- **Modify NoOps.** NoOp implementations remain zero-dependency, zero-logic.
  Simulation wraps them via `@Decorator` — it never changes them.
- **Replace real implementations.** When a real backend is wired and no
  simulation strategy is configured, the decorator passes through
  transparently.
- **Run in production by default.** Simulation activates only when
  `casehub.simulation.*` config is present. No config = no overhead.
- **Provide runtime strategy switching.** Strategy selection is boot-time.
  Restart to change strategies (Quarkus dev mode makes this fast).

---

## Tutorial tests

The `simulation-core` module includes tutorial-style tests that demonstrate
every usage pattern. Read them as how-to guides:

| Test class | What it shows |
|------------|---------------|
| `SimulationGettingStartedTest` | Seed, configure, resolve — sequential, key-lookup, random, passthrough |
| `PerMethodStrategyTest` | Different strategies for different methods on the same SPI |
| `CaptureAndReplayTest` | Record real invocations, replay with keys, tenant isolation |
| `CustomKeyExtractorTest` | Identity, normalizing, composite, case-insensitive extractors |
| `ExhaustionAndEdgeCasesTest` | WRAP/THROW policies, canResolve, error handling, caching |

Package: `io.casehub.platform.simulation.tutorial` in `simulation-core/src/test/`.
