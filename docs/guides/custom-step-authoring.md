# Custom Step Authoring

Add domain-specific steps to the playbook engine. Write a Java record,
add an annotation, and test with pure YAML — no manual registration,
no Java test wiring, no configuration.

## Writing a step

A step is a `@Plugin` record. Fields are inputs. The `@Execute` method
is the logic. Service dependencies are method parameters, resolved
automatically from the `ServiceRegistry`.

```java
@Plugin(value = "bank-balance",
        description = "Look up account balance")
public record BankBalancePlugin(
        @Required String accountId,
        @Optional String userId) {

    @Execute
    public Result execute(BankPlatform bankPlatform) {
        var balance = bankPlatform.accountInformation(
                userId != null ? userId : "default")
                .balance(accountId);
        return Result.of(Map.of(
                "accountId", balance.accountId(),
                "amount", balance.available(),
                "currency", balance.currency()));
    }
}
```

The playbook uses it:

```yaml
steps:
  - bank-balance:
      accountId: acc-100
      userId: user-1
    capture: balance
```

Record field names become kebab-case YAML keys (`accountId` → `account-id`).

### Streaming steps

Steps that participate in data pipelines use `streaming = true`. The
`@Execute` method's first parameter is the pipeline input; the return
type is the pipeline output. Both streaming and non-streaming execution
work from the same record — no separate wiring.

```java
@Plugin(value = "tts-synthesise", streaming = true)
public record TtsSynthesisePlugin(
        @Optional String voice,
        @Optional String language) {

    @Execute
    public SynthesisResult apply(String text, VoicePlatform voicePlatform) {
        return voicePlatform.textToSpeech().synthesise(text, voice, language);
    }
}
```

In non-streaming execution (playbook runner, test engine), the first
`@Execute` parameter resolves from the step's scalar value. Record
return types are auto-unwrapped to Maps with kebab-case keys — so
`SynthesisResult(audioData, audioFormat)` becomes
`{audio-data: "...", audio-format: "wav"}` in captures.

### What the APT generates

| File | Purpose |
|------|---------|
| `META-INF/yaml-plugins/<name>.json` | Manifest — action class, portability, capability |
| `META-INF/yaml-plugins/<name>.schema.json` | JSON Schema from record fields |
| `<Name>PluginAction.java` | `Action` implementation |

### Annotation reference

| Annotation | Target | Effect |
|------------|--------|--------|
| `@Plugin("name")` | Record class | Names the step, triggers APT |
| `@Execute` | Method | Execution method (exactly one) |
| `@Required` | Field | Must be present in YAML |
| `@Optional` | Field | May be absent |
| `streaming = true` | `@Plugin` | First `@Execute` param is pipeline input |
| `absorbSiblings = true` | `@Plugin` | Unknown YAML keys passed as params |

### Maven setup

```xml
<annotationProcessorPaths>
    <path>
        <groupId>io.casehub</groupId>
        <artifactId>casehub-platform-yaml-plugin-processor</artifactId>
        <version>${casehub.version}</version>
    </path>
</annotationProcessorPaths>
```

---

## Testing — pure YAML, zero Java wiring

The `YamlTestEngine` auto-discovers everything on the classpath:

- **Plugins** — `AptPluginSource` scans `META-INF/yaml-plugins/*.json`
- **Services** — `META-INF/yaml-services.yaml` maps SPI → implementation
- **Simulation decorators** — `META-INF/simulation-decorators.properties` wraps services

A test suite is one Java file:

```java
@Suite
@IncludeEngines("casehub-yaml")
@SelectDirectories("src/test/playbooks")
class ConnectorPlaybookSuite {}
```

Every `.test.yaml` under the directory runs automatically.

### Service registration

Drop `META-INF/yaml-services.yaml` in `src/test/resources/`:

```yaml
io.casehub.connectors.voice.spi.VoicePlatform: io.casehub.connectors.voice.ref.ReferenceVoicePlatform
io.casehub.connectors.bank.spi.BankPlatform: io.casehub.connectors.bank.ref.RefBankPlatform
```

The test engine instantiates each implementation via no-arg constructor
and registers it. Services registered this way are available to all
`@Execute` method parameters via `ServiceRegistry` forwarding — per-test
registries inherit from the base.

### Service profiles

Different profiles select different implementations or seeding
configurations. Activate with a system property:

```bash
mvn test -Dcasehub.yaml.services.profile=integration
```

The engine loads `META-INF/yaml-services-integration.yaml` instead of
the default `yaml-services.yaml`. No property = default file.

```
src/test/resources/META-INF/
  yaml-services.yaml                  # default — lightweight ref impls
  yaml-services-integration.yaml      # richer ref impls, full seed data
  yaml-services-smoke.yaml            # minimal stubs for CI
```

Same YAML tests, different service configurations per profile.

A test file can override the profile directly:

```yaml
services: integration

tests:
  - name: "full integration test"
    steps:
      - bank-balance:
          account-id: acc-100
        capture: balance
```

The `services:` key loads `yaml-services-integration.yaml` for that file,
regardless of the system property. Per-file overrides inherit from the
global registry — they add or replace, not reset.

### Captures for precise assertions

Playbook step outputs captured with `capture:` are available as named
scopes in `PlaybookRunResult.captures()`:

```yaml
steps:
  - tts-synthesise: "Hello"
    capture: audio
  - stt-transcribe: "${audio.audio-data}"
    capture: roundTrip
```

Test assertions use precise scope paths, not flat-merged keys:

```yaml
tests:
  - name: "round-trip preserves content"
    steps:
      - run-playbook: voice-interaction-patterns/01-voice-echo.playbook.yaml
        capture: result
      - assertThat: result.captures.roundTrip.text
        equals: "Hello"
      - assertThat: result.captures.audio.audio-format
        equals: "wav"
```

---

## Three execution modes

The same YAML, same steps, same corpus format — one word switches the
mode. The test author controls this from the `simulate:` block.

### 1. No simulation — ref implementation executes

```yaml
tests:
  - name: "real ref execution"
    steps:
      - bank-balance:
          account-id: acc-100
        capture: balance
      - assertThat: balance.amount
        not-null: true
```

The ref implementation registered via `yaml-services.yaml` executes.
Whatever data is in its backend is what the step returns.

### 2. Simulated — corpus-driven, no logic executes

```yaml
simulate:
  methods:
    bank-platform.get-balance:
      strategy: sequential
  corpus:
    bank-platform.get-balance:
      - input: { account-id: "acc-100" }
        output: { account-id: "acc-100", amount: 1500.0, currency: "GBP" }

tests:
  - name: "simulated balance"
    steps:
      - bank-balance:
          account-id: acc-100
        capture: balance
      - assertThat: balance.amount
        equals: 1500.0
```

The simulation decorator intercepts the service call. The corpus entry
is returned directly. The ref implementation is never called.

Available strategies: `sequential`, `key-lookup`, `random`,
`recorded-replay`, `nearest-match`.

### 3. Reference — seed ref implementation, execute real logic

```yaml
simulate:
  methods:
    bank-platform.get-balance:
      strategy: reference
  corpus:
    bank-platform.get-balance:
      - input: { account-id: "acc-100" }
        output: { account-id: "acc-100", amount: 1500.0, currency: "GBP" }

tests:
  - name: "seeded ref execution"
    steps:
      - bank-balance:
          account-id: acc-100
        capture: balance
      - assertThat: balance.amount
        equals: 1500.0
```

The corpus data seeds the ref implementation's backend via the `Seedable`
interface. Then the real `@Execute` logic runs against the seeded data.
Same corpus format as simulation — change `reference` to `sequential` to
switch modes.

Ref implementations opt into seeding:

```java
public class RefBankPlatform implements BankPlatform, Seedable {
    @Override
    public void seed(String qualifiedMethodName, List<Map<String, Object>> corpusOutputs) {
        for (var entry : corpusOutputs) {
            backend.addAccount(new Account(
                (String) entry.get("account-id"),
                ((Number) entry.get("amount")).doubleValue(),
                (String) entry.get("currency")));
        }
    }
}
```

### Mixed — selective simulation

Simulate agents, use real ref implementations for connectors:

```yaml
simulate:
  agent:
    - prompt: "balance"
      response: "BALANCE"
```

Only the agent is simulated. Voice, bank, email steps use their ref
implementations.

### What each mode tests

| Mode | What it tests | When to use |
|------|--------------|-------------|
| No simulation | Plugin wiring + SPI contract | Verify connector logic |
| Simulated | Playbook orchestration flow | Verify step ordering, captures, conditions |
| Reference | SPI contract with known data | Verify ref implementation against corpus |
| Mixed | Connector integration without LLM | End-to-end without API costs |

---

## Multi-operation steps with compact form

Some domains group related operations under one step name:

```yaml
- bank balance: "acc-100"
  userId: user-1
  capture: balance

- bank transactions: "acc-100"
  capture: txns
```

The Walker splits `bank balance` at the space — `bank` is the plugin name,
`balance` becomes the primary input. Use `absorbSiblings = true` on the
`@Plugin` annotation.

---

## YAML definitions (zero Java)

When a step wraps an existing API endpoint and needs no custom logic:

```yaml
# src/main/resources/definitions/crm-actions.yaml
actions:
  lookup-customer:
    description: Look up customer by ID
    inputs:
      customerId: { type: string, required: true }
    invoke: { rest: GET /api/customers/${customerId} }
```

Supported invoke bindings: `rest`, `graphql`, `mcp`, `agent`, `process`.

---

## Dependencies

| What you're doing | Dependencies needed |
|-------------------|---------------------|
| `@Plugin` + APT | `yaml-plugin-api` (compile) + `yaml-plugin-processor` (APT) |
| Pure YAML testing | `yaml-test` (test) + `simulation-starter` (test) |
| Service registration | `yaml-services.yaml` in `src/test/resources/META-INF/` |
| Seedable ref impl | `simulation-api` (compile) — implements `Seedable` |
