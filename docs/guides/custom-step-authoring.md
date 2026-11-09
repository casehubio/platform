# Custom Step Authoring

Add domain-specific steps to the playbook engine. Four paths — choose by what
the step does and how much Java is acceptable.

## Decision table

| Path | Java required | Best for | Registration |
|------|---------------|----------|--------------|
| **YAML definition** | None | Wrapping existing APIs (REST, GraphQL, MCP) | Automatic — classpath scan |
| **`@Plugin` record + APT** | One record class | Custom logic with typed inputs | Automatic — `META-INF/yaml-plugins/` scan |
| **`Definition.of()` builder** | One lambda | Dynamic steps, platform dependencies injected | Manual — call `registry.register()` |
| **CDI `PluginScanner`** | One record class | Runtime scanning without APT | Manual — call `scanner.scanAndRegister()` |

**Start with YAML definitions.** If the step wraps a REST call, GraphQL query,
or MCP tool — no Java needed. Move to `@Plugin` or `Definition.of()` only when
the step needs logic that can't be expressed as an API binding.

---

## Path 1: YAML definition (zero Java)

Define steps in a YAML file on the classpath. Each step binds to a backend
(`rest`, `graphql`, `mcp`, `agent`, `process`, `aria`) — the engine handles
invocation, simulation, and validation.

### Step definition file

```yaml
# src/main/resources/definitions/crm-actions.yaml
actions:
  lookup-customer:
    description: Look up customer by ID
    inputs:
      customerId: { type: string, required: true }
    invoke: { rest: GET /api/customers/${customerId} }

  create-ticket:
    description: Create a support ticket
    inputs:
      customerId: { type: string, required: true }
      subject:    { type: string, required: true }
      priority:   { type: string, default: "normal" }
    invoke: { rest: POST /api/tickets }

  enrich-profile:
    description: Enrich customer profile via MCP tool
    inputs:
      customerId: { type: string, required: true }
    invoke: { mcp: enrich-customer-profile }
```

### Supported invoke bindings

| Binding | Syntax | What it does |
|---------|--------|-------------|
| `rest` | `{ rest: METHOD /path }` | HTTP request via configured endpoint |
| `graphql` | `{ graphql: queryName }` | GraphQL query or mutation |
| `graphql-exec` | `{ graphql-exec: "query { ... }" }` | Inline GraphQL |
| `mcp` | `{ mcp: tool-name }` | MCP tool invocation |
| `agent` | `{ agent: prompt }` | Single-shot agent invocation |
| `process` | `{ process: command }` | Subprocess execution |
| `aria` | `{ aria: action }` | UI automation action |

### Wire it up

Register the definition file path in the `YamlDefinitionSource` (Quarkus CDI):

```java
@Produces
@ApplicationScoped
public YamlDefinitionSource crmSteps(List<InvokeHandler> handlers) {
    return new YamlDefinitionSource(
            List.of("definitions/crm-actions.yaml"),
            handlers, null);
}
```

### Use it in a playbook

```yaml
steps:
  - lookup-customer:
      customerId: ${case.customerId}
    capture: customer

  - create-ticket:
      customerId: ${customer.id}
      subject: "Follow-up on case ${case.id}"
      priority: high
    capture: ticket
```

### What this gives you for free

- **Simulation** — `SimulatingAction` wraps every YAML-defined step automatically.
  Corpus-based test responses work without extra code.
- **Validation** — `ValidatingAction` checks required inputs before invocation.
- **Namespace support** — add `namespace: crm` at the top of the YAML file.
  Steps register as both `crm.lookup-customer` and `lookup-customer`.

---

## Path 2: `@Plugin` record + APT (typed Java, zero boilerplate)

When the step needs custom logic — validation, transformation, branching — that
can't be expressed as an API binding.

### Define the plugin

```java
@Plugin(value = "stt-transcribe", streaming = true,
        description = "Transcribe audio bytes to text via VoicePlatform STT")
public record SttTranscribePlugin(
        @Optional String language,
        @Optional String model) {

    @Execute
    public TranscriptionResult apply(byte[] audio, VoicePlatform voicePlatform) {
        var options = TranscriptionOptions.defaults()
                .withLanguageHint(language)
                .withModelSize(model);
        return voicePlatform.speechToText().transcribe(audio, options);
    }
}
```

### What the APT generates

At compile time, `yaml-plugin-processor` produces three files:

| File | Purpose |
|------|---------|
| `META-INF/yaml-plugins/stt-transcribe.json` | Manifest — action class, portability, capability |
| `META-INF/yaml-plugins/stt-transcribe.schema.json` | JSON Schema from record fields |
| `SttTranscribePluginAction.java` | `Action` implementation — constructs the record, calls `@Execute` |

### Wire it up

Add the APT to the compiler configuration:

```xml
<plugin>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>io.casehub</groupId>
                <artifactId>casehub-platform-yaml-plugin-processor</artifactId>
                <version>${casehub.version}</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

`AptPluginSource.populate()` discovers all `META-INF/yaml-plugins/*.json` on
the classpath at startup. No manual registration needed.

### Annotation reference

| Annotation | Target | Effect |
|------------|--------|--------|
| `@Plugin("name")` | Record class | Names the step, triggers APT generation |
| `@Execute` | Method | Marks the execution method (exactly one per plugin) |
| `@Required` | Field | Field must be present in YAML — schema marks as required |
| `@Optional` | Field | Field may be absent — schema marks as optional |

Record field names become kebab-case YAML keys (`languageHint` → `language-hint`).
The `@Execute` method must return `Result`.

---

## Path 3: `Definition.of()` builder (programmatic)

When the step needs dependencies injected from the CDI container or wraps a
platform SPI that isn't available at APT time.

### Real-world example — connectors repo

The `casehub-connectors` repo uses this pattern for domain plugins that need
platform SPIs (BankPlatform, ChatPlatform, VoicePlatform):

```java
public final class BankPlugin {

    public static Definition definition(BankPlatform bankPlatform) {
        return Definition.of("bank")
                .description("Bank platform operations — balance, transactions, accounts")
                .input("operation", ParameterType.STRING, true)
                .absorbSiblings(true)
                .execute((params, services) -> {
                    String operation = (String) params.get("operation");
                    String userId = (String) params.getOrDefault("userId", "user-1");
                    return switch (operation) {
                        case "balance" -> balance(params, bankPlatform, userId);
                        case "transactions" -> transactions(params, bankPlatform, userId);
                        case "accounts" -> accounts(bankPlatform, userId);
                        default -> new Result.Failure("Unknown bank operation: " + operation);
                    };
                })
                .build();
    }
}
```

### Registration

Manual — call `registry.register()` from a CDI producer or startup bean:

```java
@ApplicationScoped
public class ConnectorPluginRegistrar {

    @Inject PluginRegistry registry;
    @Inject BankPlatform bankPlatform;
    @Inject ChatPlatform chatPlatform;

    void onStartup(@Observes StartupEvent event) {
        registry.register(BankPlugin.definition(bankPlatform));
        registry.register(ChatPlugin.definition(chatPlatform));
    }
}
```

### Compact form with `absorbSiblings`

When a `Definition` sets `.absorbSiblings(true)`, unrecognised sibling keys in
the YAML step are absorbed into the params map instead of causing an error. This
enables the "operation as suffix" pattern:

```yaml
# "bank" is the plugin, "balance" becomes params.get("operation"),
# "acc-100" becomes params.get("_value")
- bank balance: "acc-100"
  userId: user-1
  capture: balance
```

The Walker splits `bank balance` into plugin prefix `bank` and suffix `balance`,
placing the suffix into the `operation` input (the primary input). The scalar
value `"acc-100"` goes into `_value`.

### Builder API

| Method | Effect |
|--------|--------|
| `.description(String)` | Step description for MCP discovery |
| `.input(name, type, required)` | Declare a typed input parameter |
| `.output(name, type)` | Declare an output field |
| `.execute(Action)` | The step logic — `(params, services) → Result` |
| `.portability(Portability)` | `JAVA`, `TS`, `BOTH`, or `UNIVERSAL` |
| `.capability(String)` | Capability group (default: `"steps"`) |
| `.absorbSiblings(true)` | Absorb unknown sibling keys as params |
| `.streamingType(info)` | Enable streaming step support |

---

## Path 4: CDI `PluginScanner` (runtime discovery)

Scans a `@Plugin` record at runtime without APT. Useful when the APT processor
is not on the classpath or when plugins are discovered dynamically.

```java
@ApplicationScoped
public class DynamicPluginLoader {

    @Inject PluginRegistry registry;

    void onStartup(@Observes StartupEvent event) {
        var scanner = new PluginScanner();
        scanner.scanAndRegister(SttTranscribePlugin.class, registry);
        scanner.scanAndRegister(TtsSynthesisePlugin.class, registry);
    }
}
```

`PluginScanner` reads the `@Plugin` annotation, builds inputs from record
components, finds the `@Execute` method, and constructs a `Definition`. Same
result as the APT path but at runtime instead of compile time — no
`META-INF/yaml-plugins/` files generated.

---

## Testing custom steps

### Simulation corpus

Every step — regardless of authoring path — supports simulation via the
`simulate:` block in `.test.yaml` files. Define canned responses:

```yaml
simulate:
  crm:
    - input: { customerId: "C-100" }
      output: { id: "C-100", name: "Acme Corp", tier: "enterprise" }

tests:
  - name: "lookup returns customer"
    steps:
      - lookup-customer:
          customerId: "C-100"
        capture: customer
      - assertThat: customer.name
        equals: "Acme Corp"
```

### Unit testing `@Plugin` records

```java
@Test
void transcribesAudio() {
    var plugin = new SttTranscribePlugin("en", null);
    var result = plugin.apply(testAudio, mockVoicePlatform);
    assertThat(result.text()).contains("hello");
}
```

### Unit testing `Definition.of()` steps

```java
@Test
void bankBalanceReturnsAmount() {
    var def = BankPlugin.definition(mockBankPlatform);
    var result = def.action().execute(
            Map.of("operation", "balance", "_value", "acc-100", "userId", "user-1"),
            new MapServiceRegistry());
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.output().get("amount")).isEqualTo(1500.0);
}
```

---

## Dependencies

| What you're doing | Dependencies needed |
|-------------------|---------------------|
| YAML definition only | `yaml-plugin-api`, `yaml-step-runtime` |
| `@Plugin` + APT | `yaml-plugin-api` (compile), `yaml-plugin-processor` (APT) |
| `Definition.of()` builder | `yaml-plugin-api` |
| CDI `PluginScanner` | `yaml-plugin-api`, `yaml-step-runtime` |
| Simulation/testing | Add `simulation-starter` (test scope) |
