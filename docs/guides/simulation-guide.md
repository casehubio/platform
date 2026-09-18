# Simulation Framework

> Configurable simulation for any SPI. Real responses when you have a real
> backend. Simulated responses when you don't. Captured traffic when you
> want to build a corpus.

---

## Scenarios — when to reach for simulation

The simulation framework handles any SPI where you need controlled
responses without a live backend. These are the problems it solves,
grouped by domain.

### LLM and agent testing

**Replace a router LLM** — You have an agent that decides which specialist
to call. You want to test routing logic without LLM costs. Seed the corpus
with known prompt→routing pairs, use key-lookup for deterministic dispatch.

**Replace a judge LLM** — Quality evaluation that needs to approve or reject.
Sequential with a known approve/reject sequence for scenario testing, or
key-lookup for input-dependent verdicts.

**Replay captured LLM traffic** — You recorded real Claude or OpenAI sessions
during development. Replay them in CI with recorded-replay. A normalizing
extractor strips UUIDs and timestamps so keys match across runs.

### Banking and financial services

**Simulate a payment gateway** — Key-lookup by transaction type and amount
range. Test approval paths, decline paths, and timeout handling without
touching a real gateway.

**Simulate a bank feed** — Sequential feed of transactions for reconciliation
testing. Capture real feed data from staging, replay it later in CI.

**Simulate KYC/AML screening** — Key-lookup by entity name. Known-clean
entities return clear, known-flagged entities return hits. Test the decision
logic, not the screening service.

### Healthcare and clinical

**Simulate a lab results service** — Key-lookup by patient ID. Test case
lifecycle workflows without a FHIR backend.

**Simulate a diagnostic engine** — Recorded-replay from captured real
diagnoses. Same patient presentation produces the same differential.

### Integration and connectors

**Simulate an external REST API** — Key-lookup by endpoint and request hash.
Test integration code without the third party being available.

**Error injection** — Seed error responses into the corpus to test error
handling paths. Sequential with a mix of success and failure responses.

### Testing workflows

**Capture → CI replay** — Capture staging traffic during manual testing,
replay it in CI. Real-shaped data, no network dependency.

**Tenant isolation testing** — Captured data is tenant-scoped. Verify that
tenant A's corpus never leaks into tenant B's responses.

**Load testing without backend costs** — Sequential with WRAP exhaustion
policy. Finite corpus, unlimited calls.

---

## Named patterns

Every usage pattern has a name. The guide uses these names, and the tutorial
tests carry them as method names. When discussing simulation with your team,
use the pattern name — it's more precise than describing the mechanism.

| Pattern | Problem it solves | Strategy | Tutorial test |
|---------|-------------------|----------|---------------|
| **Deterministic Replay** | Same input → same output, every time | key-lookup | `keyLookupForDeterministicResponses` |
| **Capture → Replay** | Record real traffic, simulate it later | capture + recorded-replay | `captureWithKeysForDeterministicReplay` |
| **Infinite Load** | Finite corpus, unlimited calls | sequential (WRAP) | `wrapPolicyRecyclesCorpusIndefinitely` |
| **Counted Scenario** | Exactly N calls, then fail | sequential (THROW) | `throwPolicyFailsWhenCorpusExhausted` |
| **Normalizing Extractor** | Strip UUIDs/timestamps for stable keys | key-lookup + normalizer | `normalizingExtractorStripsNoise` |
| **Composite Key** | Multi-param method → single lookup key | key-lookup + composite extractor | `compositeExtractorForMultipleParams` |
| **Per-Method Mix** | Different strategies per SPI method | mixed | `differentStrategiesPerMethod` |
| **Tenant-Isolated Capture** | Captured data scoped to tenant | capture | `capturedDataIsTenantScoped` |
| **Best-Effort Replay** | Key-first, sequential fallback | recorded-replay | `recordedReplayKeyFirstSequentialFallback` |
| **Backend Simulation** | Simulate a routed SPI (Path B) | any | `invokeWithStrategyReturnsEventsFromCorpus` |

---

## Quick start

### 1. Add dependencies

```xml
<!-- Core contracts — always needed -->
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-platform-simulation-api</artifactId>
</dependency>

<!-- Strategy implementations -->
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-platform-simulation-core</artifactId>
</dependency>

<!-- Config binding + YAML corpus + declarative extractors -->
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-platform-simulation-config</artifactId>
</dependency>
```

For generated `@Decorator` per `@SimulationEligible` SPI:

```xml
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-platform-simulation-generator</artifactId>
    <scope>provided</scope>
</dependency>
```

For AgentProvider simulation (Path B):

```xml
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-platform-agent-simulation-core</artifactId>
</dependency>
```

### 2. Configure simulation

```properties
# Simulate with sequential responses
casehub.simulation.my-spi.query.strategy=sequential

# Capture real calls for corpus building
casehub.simulation.my-spi.store.capture=true

# No config → passthrough (transparent delegation)
```

### 3. Seed a corpus and resolve

```java
@Inject SimulationCorpus corpus;
@Inject SimulationRuntime simulation;

void setup() {
    corpus.seed("my-spi.query", List.of(
        new InvocationRecord<>("tenant-1", "key-1",
            "input-a", "response-a", Instant.now()),
        new InvocationRecord<>("tenant-1", "key-2",
            "input-b", "response-b", Instant.now())));
}

void resolve() {
    var strategy = simulation.strategyFor("my-spi.query");
    // strategy.resolve("input-a") → "response-a"
}
```

That's it. The generated `@Decorator` intercepts your SPI method,
resolves from the corpus via the configured strategy, and returns the
seeded response. No changes to the SPI, no changes to the NoOp, no
changes to production code.

---

## Core concepts

### Qualified name

Every simulated method is identified by a **qualified name**:
`"spi-name.method-name"`. This is the namespace key that separates corpus
data, strategy configuration, and capture recording across methods.

```
my-spi.query      → one corpus, one strategy
my-spi.store      → different corpus, different strategy
agent-provider.invoke  → yet another
```

The SPI name comes from `@SimulationEligible(name = "my-spi")`.
If `name` is omitted, it defaults to the kebab-case of the interface name.

### Three modes per method

| Mode | What happens | When to use |
|------|-------------|-------------|
| **Simulation** | Strategy resolves responses from a corpus | Dev, demos, load testing |
| **Capture** | Real impl runs; input/output recorded to corpus | Building a corpus from live traffic |
| **Passthrough** | No simulation, no capture — transparent delegation | Production, or methods you don't need to simulate |

All three are configured per method. A multi-method SPI can simulate
`query`, capture `store`, and pass through `erase` — simultaneously.
This is the **Per-Method Mix** pattern.

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
corpus.lookupByKey("my-spi.method", "patient-123");   // → Optional<O>
corpus.lookupByIndex("my-spi.method", 0);              // → Optional<O>
```

Two corpus backends ship:

| Backend | CDI tier | Behaviour |
|---------|----------|-----------|
| `NoOpSimulationCorpus` | @DefaultBean (Tier 1b) | Returns empty, discards records — active when no backend on classpath |
| `InMemorySimulationCorpus` | @Alternative @Priority(100) (Tier 4) | ConcurrentHashMap — volatile, thread-safe, lost on restart |

### SimulationRuntime

The `SimulationRuntime` wires strategies to qualified names. Generated
decorators and backend adapters inject it and call:

```java
Optional<SimulationStrategy<I, O>> strategy = runtime.strategyFor("my-spi.method");
if (strategy.isPresent() && strategy.get().canResolve(input)) {
    return strategy.get().resolve(input);
}
// else: delegate to real impl
```

Strategy instances are cached — `strategyFor()` returns the same instance
on repeated calls.

---

## Strategies

The framework ships with four strategies. Each implements the same
`SimulationStrategy<I, O>` contract:

```java
public interface SimulationStrategy<I, O> {
    O resolve(I input);
    boolean canResolve(I input);
}
```

### Sequential — cycling through a list

Returns responses in corpus insertion order. When the list runs out,
behaviour depends on the exhaustion policy.

```properties
casehub.simulation.my-spi.method.strategy=sequential
casehub.simulation.my-spi.method.exhaustion-policy=THROW  # default: WRAP
```

| Policy | Behaviour | Pattern |
|--------|-----------|---------|
| `WRAP` (default) | Cycles back to the beginning — infinite responses from finite corpus | **Infinite Load** |
| `THROW` | Throws `SimulationExhaustedException` — expects exactly N calls | **Counted Scenario** |

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
runtime.registerExtractor("my-spi.method",
    (String patientId) -> patientId.toLowerCase());
```

Throws `SimulationKeyNotFoundException` when no corpus entry matches
the extracted key.

This is the **Deterministic Replay** pattern — the most common starting
point for simulation.

### Random — sampling from corpus

Picks a random entry from the corpus each time. Pass a seeded `Random`
for reproducible tests.

```properties
casehub.simulation.my-spi.method.strategy=random
```

Use for load testing with varied responses, or fuzzing.

### Recorded-replay — key-first with fallback

Tries key-based lookup first. If the key matches, returns it
(deterministic). If not, falls back to sequential traversal.

```properties
casehub.simulation.my-spi.method.strategy=recorded-replay
```

This is the **Best-Effort Replay** pattern — ideal for replaying captured
traffic where known requests replay exactly and unexpected requests get
sequential responses.

### Nearest-match — weighted similarity scoring

Scores all corpus entries against the input using a `SimilarityScorer<I>`
and returns the best match above a configurable threshold. Use when inputs
vary between runs (different UUIDs, amounts, entity names) but the response
shape is stable — and a normalizing KeyExtractor can't make the key exact.

```properties
casehub.simulation.my-spi.method.strategy=nearest-match
casehub.simulation.my-spi.method.threshold=0.7
casehub.simulation.my-spi.method.scorer=fields:domain:exact:1.0,question:substring:0.5
```

Programmatic registration for complex scoring:

```java
runtime.registerScorer("my-spi.method",
    RecordFieldScorer.<MyInput>builder()
        .field("domain", FieldSimilarity.EXACT, 1.0)
        .field("question", FieldSimilarity.SUBSTRING, 0.5)
        .field("limit", FieldSimilarity.IGNORE, 0.0)
        .build());
```

Or a custom lambda:

```java
runtime.registerScorer("my-spi.method",
    (MyInput q, MyInput c) -> /* custom scoring logic */);
```

Built-in field scorers: `exact` (1.0/0.0), `substring` (containment),
`numeric-range` (proportional distance), `ignore` (always 1.0).

O(n) corpus scan — suitable for small corpora (10-50 entries).

---

## KeyExtractors

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

**Normalizing** — strip noise for stable matching (**Normalizing Extractor**
pattern):
```java
runtime.registerExtractor("spi.method", (String prompt) ->
    prompt.replaceAll("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}", "<UUID>")
          .replaceAll("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}", "<TIMESTAMP>")
          .toLowerCase().trim());
```

**Composite** — for multi-parameter methods (**Composite Key** pattern):
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
traffic. This is the first half of the **Capture → Replay** pattern.

```properties
casehub.simulation.my-spi.query.capture=true
```

The generated decorator does this automatically:

```java
// Generated code (simplified):
Result result = delegate.query(input);
if (simulation.captureEnabled("my-spi.query")) {
    String key = extractor.extract(input);  // if registered
    simulation.capture("my-spi.query", tenancyId, key, input, result);
}
return result;
```

Captured data is tenant-scoped — each invocation records the tenant context.
This enables the **Tenant-Isolated Capture** pattern.

### Capture → Replay workflow

1. Deploy with capture enabled against a real backend
2. Run the scenarios you want to simulate
3. Export the corpus (or keep it in memory for the session)
4. Switch config from capture to simulation:
   ```properties
   # Before: capture
   casehub.simulation.my-spi.query.capture=true
   # After: simulate
   casehub.simulation.my-spi.query.strategy=recorded-replay
   ```
5. The captured corpus now drives simulation

### Capture with keys

For deterministic replay after capture, register a `KeyExtractor` before
enabling capture. The generated decorator derives keys from the registered
extractor, making each captured entry addressable by key.

---

## Integration paths

### Path A — Generated @Decorator (request-response SPIs)

For SPIs with direct CDI injection and request-response methods. Add
`@SimulationEligible` to the SPI interface:

```java
@SimulationEligible(name = "my-spi")
public interface MySpi {
    Result query(QueryInput input);
    void store(String tenancyId, StoreInput input);
    void erase(EraseRequest request);
}
```

The `simulation-generator` annotation processor scans this via Jandex and
generates a `@Decorator` class that:

- Checks `SimulationRuntime.strategyFor()` for each method
- Delegates to the real impl when no strategy is configured
- Captures invocations when capture is enabled
- Runs at `@Priority(APPLICATION + 200)` — after the real impl's priority

No manual decorator code needed. No changes to the SPI or its implementations.

**For SPIs that can't depend on simulation-api** (e.g., SPIs in peer repos
like `CaseMemoryStore` in neocortex-memory-api), the generator also reads
`META-INF/simulation-eligible.txt` alongside the annotation scan:

```
# META-INF/simulation-eligible.txt
io.casehub.neocortex.memory.CaseMemoryStore=case-memory-store
```

Each line maps a fully qualified interface name to an SPI name (the config
key prefix). The generator indexes the class from the classpath and generates
the same decorator as the annotation path. The SPI's JAR must be on the
`annotationProcessorPaths` in the consuming module's pom.xml so the generator
can read the class bytes. Annotation takes precedence when both are present.

The first consumer of this mechanism is `memory-simulation-core`, which
generates `SimulatedCaseMemoryStore` for CaseMemoryStore.

### Path B — Backend integration (routed SPIs)

SPIs with existing multi-backend routing (like `AgentProvider` →
`RoutingAgentProvider` → `AgentBackend`) integrate simulation as a backend
rather than a decorator. The simulation backend registers with the existing
routing infrastructure.

`SimulatedAgentBackend` (key: `"simulated"`) is dispatched by the router
when the model resolves to the simulated backend:

```properties
casehub.simulation.agent-provider.invoke.strategy=sequential
```

```java
corpus.seed("agent-provider.invoke", List.of(
    new InvocationRecord<>("t1", null,
        new AgentSimulationInput("system-prompt", "hello", null),
        List.of(new AgentEvent.TextDelta("Simulated response")),
        Instant.now())));
```

Both paths use the same `SimulationStrategy<I, O>` contract, corpus, and
configuration model. The difference is where interception happens — CDI
decorator vs routing-layer backend.

### Choosing a path

| Criteria | Path A (Decorator) | Path B (Backend) |
|----------|-------------------|-----------------|
| SPI shape | Request-response methods | Routing layer with multiple backends |
| Return types | Blocking / data types | Reactive streams, stateful sessions |
| Integration | Generated @Decorator wrapping SPI | Implements backend interface, registered with router |
| Strategy contract | `SimulationStrategy<I, O>` | `SimulationStrategy<I, O>` |

---

## Corpus population

How you populate the corpus depends on where you are in the development
lifecycle and what data you have available.

### Hand-crafted — small, precise corpora

Construct `InvocationRecord` instances directly. Best for small test
scenarios where you know exactly what inputs and outputs you need.

```java
corpus.seed("my-spi.query", List.of(
    new InvocationRecord<>("tenant-1", "patient-123",
        queryInput, expectedResult, Instant.now())));
```

### Captured — real-shaped data from live systems

Enable capture mode against a real backend, run your scenarios, then
replay the captured corpus. Best when you have a working backend and
want CI-reproducible tests with realistic data.

### Data realism spectrum

The `DataRealism` enum classifies how realistic corpus data is:

| Level | Meaning | Source |
|-------|---------|--------|
| `GARBAGE` | Structurally valid but semantically meaningless | Random generation |
| `STRUCTURALLY_VALID` | Correct types and shapes, plausible values | Schema-driven generation |
| `DOMAIN_PLAUSIBLE` | Realistic within the domain | Hand-crafted or LLM-generated |
| `RECORDED_REAL` | Captured from a real system | Capture mode |

Choose the realism level that matches your testing goal. Load testing
needs volume (`STRUCTURALLY_VALID`). Scenario testing needs accuracy
(`DOMAIN_PLAUSIBLE` or `RECORDED_REAL`).

---

## Strategy combinations

### Across methods on one SPI

Different strategies for different methods is normal — it's the
**Per-Method Mix** pattern:

```properties
# Simulate query with deterministic responses
casehub.simulation.bank-feed.list-transactions.strategy=key-lookup

# Pass through to real balance API
# (no config = passthrough)

# Capture real payment calls for corpus building
casehub.simulation.bank-feed.post-payment.capture=true
```

### Cross-SPI coordination

Multiple SPIs can be simulated simultaneously, each with its own
strategy and corpus:

```properties
# LLM: replay captured traffic
casehub.simulation.agent-provider.invoke.strategy=recorded-replay

# Memory: deterministic test data
casehub.simulation.case-memory-store.query.strategy=key-lookup

# Notifications: sequential to verify delivery order
casehub.simulation.notification-store.store.strategy=sequential
```

### Strategy lifecycle progression

Simulation typically evolves through stages as a project matures:

1. **No simulation** — real backend available, no need
2. **Enable capture** — build a corpus from real traffic
3. **Switch to recorded-replay** — replay captured traffic in CI
4. **Curate the corpus** — key-lookup for deterministic scenarios
5. **Expand** — sequential/random for load and fuzz testing

Each stage is a configuration change, not a code change.

---

## Design for simulation

### Flat interfaces are a prerequisite

The framework intercepts at the SPI method level. A capability-based SPI
like `platform.messaging().send()` is awkward — the decorator intercepts
`messaging()` (which returns a Messaging object), not `send()` (the actual
operation).

**Guidance:** if your SPI returns capability objects, flatten the interface
so each method is a complete operation. Flat interfaces map directly to
simulation strategies.

### You still need a @DefaultBean no-op

The decorator wraps whatever CDI bean is active. When no real
implementation is wired, CDI still needs something to inject as the
`@Delegate`. A `@DefaultBean` no-op fills this role. The decorator
intercepts before the no-op is reached when simulation is active.

### The framework eliminates simulation boilerplate, not SPI design

What methods go on the interface, what the model records look like,
pagination contracts, error semantics — these still need the same design
thought. `@SimulationEligible` eliminates the hand-written simulation
class, not the SPI design work.

---

## Generated code walkthrough

When the annotation processor runs on an `@SimulationEligible` SPI, it
generates a `@Decorator` in `target/generated-sources/annotations/`.
Here's what the generated code does for each method:

```java
@Decorator
@Priority(APPLICATION + 200)
public abstract class SimulatedMySpi implements MySpi {

    @Inject @Delegate MySpi delegate;
    @Inject SimulationRuntime simulation;

    @Override
    public Result query(QueryInput input) {
        String qualifiedName = "my-spi.query";

        // 1. Check for simulation strategy
        Optional<SimulationStrategy<QueryInput, Result>> strategy =
            simulation.strategyFor(qualifiedName);
        if (strategy.isPresent() && strategy.get().canResolve(input)) {
            return strategy.get().resolve(input);
        }

        // 2. Delegate to real implementation
        Result result = delegate.query(input);

        // 3. Capture if enabled
        if (simulation.captureEnabled(qualifiedName)) {
            simulation.capture(qualifiedName, tenancyId, input, result);
        }

        return result;
    }

    // ... same pattern for each method
}
```

Inspect the generated source in `target/generated-sources/annotations/`
to see the exact output for your SPI.

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
| `MyPaymentGateway` | `my-payment-gateway` |
| `PreferenceStore` | `preference-store` |

---

## Choosing a strategy

```
Is the input deterministic? (same input = same expected output)
├── YES → key-lookup (Deterministic Replay pattern)
│
├── PARTIALLY — I have some known inputs, but expect unknown ones too
│   └── recorded-replay (Best-Effort Replay pattern)
│
├── FUZZY — inputs vary but are structurally similar
│   └── nearest-match + SimilarityScorer (threshold-based)
│
├── NO — I need a predictable sequence
│   ├── Finite calls? → sequential + THROW (Counted Scenario)
│   └── Unlimited calls? → sequential + WRAP (Infinite Load)
│
└── NO — I need variety
    └── random
```

If you have captured traffic from a real system, start with
**Capture → Replay**: enable capture, run scenarios, switch to
recorded-replay.

---

## Module dependency map

```
simulation-api            zero-dep: contracts, @SimulationEligible, NoOp corpus
  ├── simulation-core     strategies, SimulationRuntime, SimulationConfig
  ├── simulation-inmem    InMemorySimulationCorpus (plain POJO)
  ├── simulation-config   CDI wiring, YAML corpus, declarative extractors
  ├── simulation-generator  APT: generates @Decorator per @SimulationEligible
  ├── agent-simulation-core SimulatedAgentBackend (Path B)
  └── memory-simulation-core SimulatedCaseMemoryStore (Path A, listing file)
```

| Module | pom.xml scope | When to add |
|--------|--------------------|-------------|
| `simulation-api` | compile | Your SPI uses `@SimulationEligible` |
| `simulation-core` | compile | You need SimulationRuntime (strategy resolution) |
| `simulation-config` | compile | Config binding, YAML corpus, declarative extractors — required alongside `simulation-generator` |
| `simulation-generator` | provided | Your SPI has `@SimulationEligible` and you want generated decorators |
| `agent-simulation-core` | compile | You want to simulate AgentProvider responses |
| `memory-simulation-core` | compile | You want to simulate CaseMemoryStore (store/query/erase) |

---

## Error handling

| Exception | When | What to do |
|-----------|------|------------|
| `SimulationExhaustedException` | Sequential + THROW, corpus empty | Seed more data, or switch to WRAP |
| `SimulationKeyNotFoundException` | Key-lookup, no matching entry | Seed the missing key, or use recorded-replay for fallback |
| `SimulationConfigException` | Unknown strategy name, or key-lookup without extractor | Check config, register your KeyExtractor at startup |

All three extend `RuntimeException` — they propagate through the SPI
contract without checked exception declarations.

---

## Tutorial tests

The `simulation-core` module includes tutorial-style tests that demonstrate
every named pattern. Read them as how-to guides:

| Test class | Patterns demonstrated |
|------------|----------------------|
| `SimulationGettingStartedTest` | Deterministic Replay, Infinite Load, passthrough |
| `PerMethodStrategyTest` | Per-Method Mix |
| `CaptureAndReplayTest` | Capture → Replay, Tenant-Isolated Capture, Best-Effort Replay |
| `CustomKeyExtractorTest` | Normalizing Extractor, Composite Key |
| `ExhaustionAndEdgeCasesTest` | Counted Scenario, Infinite Load, error handling |

Package: `io.casehub.platform.simulation.tutorial` in `simulation-core/src/test/`.

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

## Event simulation

Event simulation generates synthetic CloudEvents and injects them
into the CDI event bus — the same path real stream processors use.
Events traverse DataSourceRouter's tenancy check and
acceptedEventTypes filter before reaching wired DataSources.

### Core components

| Type | Purpose |
|------|---------|
| `EventTrigger` | Input record — eventType, tenancyId, context |
| `EventSourceConfig` | Per-source config — qualifiedName, eventType, tenancyId |
| `SimulatedEventEmitter` | Core emitter with `tick()` method |
| `CloudEventFixtureBuilder` | Map ↔ CloudEvent conversion for YAML corpus |
| `EmissionResult` | Synchronous result from `tick()` |

### Usage

```java
// 1. Build a corpus with CloudEvent entries
var corpus = new InMemorySimulationCorpus<EventTrigger, CloudEvent>();
CloudEvent template = CloudEventFixtureBuilder.fromMap(Map.of(
        "type", "io.casehub.work.workitem.completed",
        "source", "/simulation/event-emitter",
        "tenancyid", "default",
        "datacontenttype", "application/json",
        "data", Map.of("workItemId", "WI-001")));
corpus.seed("event-emitter.wic", List.of(new InvocationRecord<>(
        "default", "wic",
        new EventTrigger("io.casehub.work.workitem.completed", "default", Map.of()),
        template, Instant.now())));

// 2. Configure and create the emitter
var config = ...; // strategy = "sequential" for "event-emitter.wic"
var runtime = new SimulationRuntime(config, corpus);
List<CloudEvent> sink = new ArrayList<>();
var emitter = new SimulatedEventEmitter(runtime, sink::add,
        List.of(new EventSourceConfig("event-emitter.wic",
                "io.casehub.work.workitem.completed", "default")));

// 3. Emit
EmissionResult result = emitter.tick();
assertThat(result.emittedCount()).isEqualTo(1);
assertThat(sink.get(0).getType())
        .isEqualTo("io.casehub.work.workitem.completed");
```

The emitter stamps a fresh UUID `id` and `time` on each emission.
Corpus entries store CloudEvent templates without these fields.

### Tenant context

The emitter does not use `CurrentPrincipal`. Tenant context comes
from `EventSourceConfig.tenancyId()` and the `tenancyid` CloudEvent
extension in the corpus. For multi-tenant testing, configure multiple
EventSourceConfig entries with different tenant IDs.

### Timed sequences

`TimedSequence<E>` provides ordered events with relative delays:

```java
var sequence = new TimedSequence<>(List.of(
        new TimedEntry<>(eventA, Duration.ZERO),
        new TimedEntry<>(eventB, Duration.ofSeconds(5)),
        new TimedEntry<>(eventC, Duration.ofSeconds(30))));

// 10x speed — 35s becomes 3.5s
var fast = sequence.withMultiplier(10.0);
```

Build from captured data — timing derived from
`InvocationRecord.recordedAt()`:

```java
var sequence = TimedSequence.fromRecorded(capturedRecords);
var demo = sequence.withMultiplier(100.0); // 30 minutes → 18 seconds
```

Execute with `EventSequenceRunner` (sleeps between events on caller's
thread — run on a virtual thread for non-blocking execution):

```java
var runner = new EventSequenceRunner(event -> cloudEventBus.fireAsync(event));
SequenceResult result = runner.run(sequence);
assertThat(result.emittedCount()).isEqualTo(3);
```

### Continuous emission

Add the `event-simulation` module and configure:

```properties
casehub.simulation.event.interval=10s
casehub.simulation.event.sources.workitem-completed.event-type=io.casehub.work.workitem.completed
casehub.simulation.event.sources.workitem-completed.tenancy-id=default
casehub.simulation.event-emitter.workitem-completed.strategy=sequential
```

The scheduler calls `emitter.tick()` on the configured interval. Set
`interval=OFF` (default) to disable.

---

## REST client simulation

Simulates `@RegisterRestClient` interfaces — external HTTP APIs that may
not be available during scenario execution.

### Quick start

Add the processor and runtime dependencies to the module containing or
depending on the REST client interface:

```xml
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-platform-rest-client-simulation-generator</artifactId>
    <scope>provided</scope>
</dependency>
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-platform-simulation-core</artifactId>
</dependency>
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-platform-simulation-config</artifactId>
</dependency>
```

The processor auto-detects `@RegisterRestClient` interfaces in the Jandex
index and generates `@Decorator` classes with `@RestClient`-qualified
delegates.

### Configuration

```properties
# Simulate ScimClient.membersOf with key-lookup strategy
casehub.simulation.scim.membersOf.strategy=key-lookup
casehub.simulation.scim.membersOf.key-extractor=rest-client

# Capture real responses from ScimClient.getGroup
casehub.simulation.scim.getGroup.capture=true
```

The spi name is the `configKey` from `@RegisterRestClient` (e.g., `scim`
for `@RegisterRestClient(configKey = "scim")`), or the kebab-cased
interface name if no configKey is set.

### RestInvocation

All REST client method calls are wrapped in a `RestInvocation` record:

```java
RestInvocation(
    String spiName,       // "scim"
    String methodName,    // "membersOf"
    String httpMethod,    // "GET"
    String pathTemplate,  // "/Groups/{id}/Members"
    Map<String, Object> params,  // {id: "grp-1"}
    Object body           // null (or request body POJO)
)
```

### Key extraction

The built-in `rest-client` key extractor produces keys like
`GET /Groups/grp-1/Members` — HTTP method + resolved path template.

### Limitations

- Reactive return types (`Uni<T>`, `Multi<T>`) are passed through to
  the real client without simulation or capture.
- Interfaces annotated with both `@SimulationEligible` and
  `@RegisterRestClient` are handled by the base
  `SimulationDecoratorProcessor`, not this processor.

## What's next

The simulation framework is actively growing. Planned capabilities that
will extend the patterns above:

- **Corpus builders** — fluent, domain-specific builders that eliminate
  hand-crafted `InvocationRecord` construction
- **Verification API** — assertion DSL on captured invocations
  (`wasCalled()`, `wasCalledWith()`, `verifyInOrder()`) replacing
  ad-hoc corpus inspection

Each extends the existing strategy/corpus/config model — no breaking
changes to the patterns documented above.
