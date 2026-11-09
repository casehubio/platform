# Custom Step Authoring

Add domain-specific steps to the playbook engine. A step is a contract — a
name, typed inputs, and a description. The same playbook YAML works whether
the step executes against a real backend or a simulation corpus. The author
doesn't choose; the runtime wiring does.

## The primary path: `@Plugin` record

Define a Java record annotated with `@Plugin`. The APT generates the step
schema, manifest, and action class at compile time. `AptPluginSource` discovers
it on the classpath at startup. No manual registration.

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

The test uses the same YAML — simulation provides the response:

```yaml
simulate:
  bank-balance:
    - input: { accountId: "acc-100" }
      output: { accountId: "acc-100", amount: 1500.0, currency: "EUR" }

tests:
  - name: "balance lookup"
    steps:
      - bank-balance:
          accountId: acc-100
        capture: balance
      - assertThat: balance.amount
        equals: 1500.0
```

Same step name, same YAML syntax, same inputs. The test classpath doesn't need
`BankPlatform` or any of its transitives — the simulation layer handles
execution when the real action class isn't loadable.

### What the APT generates

| File | Purpose |
|------|---------|
| `META-INF/yaml-plugins/<name>.json` | Manifest — action class, portability, capability |
| `META-INF/yaml-plugins/<name>.schema.json` | JSON Schema from record fields |
| `<Name>PluginAction.java` | `Action` implementation — constructs record, calls `@Execute` |

### Annotation reference

| Annotation | Target | Effect |
|------------|--------|--------|
| `@Plugin("name")` | Record class | Names the step, triggers APT generation |
| `@Execute` | Method | Marks the execution method (exactly one) |
| `@Required` | Field | Must be present in YAML |
| `@Optional` | Field | May be absent |

Record field names become kebab-case YAML keys (`languageHint` → `language-hint`).

### Maven setup

Add the APT to the compiler:

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

## How simulation makes this work

The simulation layer wraps every registered step transparently. When a
simulation is active and a corpus entry matches the inputs, the step returns
the corpus response without calling the real backend. When no simulation is
active, the real `@Execute` method runs.

This is not a test-mode flag. It's the same registration path — `AptPluginSource`
loads the manifest, `SimulatingAction` wraps the action, and the runtime
decides which path executes. The playbook YAML is identical in both cases.

When the real action class can't be loaded (implementation SPI not on the
classpath), the manifest still provides the step schema. The simulation layer
handles execution. This is how test classpaths stay thin — they need the
manifest JAR, not the implementation dependencies.

---

## Multi-operation steps with compact form

Some domains group related operations under one step name. The `bank` step
handles `balance`, `transactions`, and `accounts` as sub-operations.

### Compact YAML syntax

```yaml
- bank balance: "acc-100"
  userId: user-1
  capture: balance

- bank transactions: "acc-100"
  capture: txns
```

The Walker splits `bank balance` at the space — `bank` is the plugin name,
`balance` becomes the primary input. The scalar value `"acc-100"` goes into
`_value`.

### Implementing multi-operation plugins

Use `Definition.of()` with `.absorbSiblings(true)` when the step dispatches
on an operation parameter:

```java
public static Definition definition(BankPlatform bankPlatform) {
    return Definition.of("bank")
            .description("Bank operations — balance, transactions, accounts")
            .input("operation", ParameterType.STRING, true)
            .absorbSiblings(true)
            .execute((params, services) -> {
                String operation = (String) params.get("operation");
                return switch (operation) {
                    case "balance"      -> balance(params, bankPlatform);
                    case "transactions" -> transactions(params, bankPlatform);
                    case "accounts"     -> accounts(bankPlatform);
                    default -> Result.failed("Unknown: " + operation);
                };
            })
            .build();
}
```

Registration is manual for `Definition.of()` — call `registry.register()` from
a CDI startup bean.

---

## YAML definitions (zero Java)

When a step wraps an existing API endpoint and needs no custom logic, define it
in YAML. No Java required.

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
```

### Supported invoke bindings

| Binding | Syntax | What it does |
|---------|--------|-------------|
| `rest` | `{ rest: METHOD /path }` | HTTP request |
| `graphql` | `{ graphql: queryName }` | GraphQL query or mutation |
| `mcp` | `{ mcp: tool-name }` | MCP tool invocation |
| `agent` | `{ agent: prompt }` | Single-shot agent call |
| `process` | `{ process: command }` | Subprocess execution |

Register the file path in a `YamlDefinitionSource` CDI producer. Steps get
simulation wrapping, input validation, and namespace support automatically.

Same YAML in playbooks and tests — same principle as `@Plugin` steps.

---

## Other registration paths

These exist for specific situations. Prefer `@Plugin` + APT for new steps.

**`Definition.of()` builder** — programmatic registration. Use when the step
needs CDI-injected dependencies or multi-operation dispatch (see compact form
section above). Manual `registry.register()` required.

**CDI `PluginScanner`** — runtime scanning of `@Plugin` records without APT.
Use when the APT processor isn't on the compile classpath. Same record, same
annotations — discovered at startup instead of compile time.

```java
var scanner = new PluginScanner();
scanner.scanAndRegister(MyPlugin.class, registry);
```

---

## Testing

### Playbook tests with simulation

```yaml
simulate:
  bank-balance:
    - input: { accountId: "acc-100" }
      output: { accountId: "acc-100", amount: 1500.0, currency: "EUR" }
  chat-send:
    - input: { channel: "family" }
      output: { ok: true }

tests:
  - name: "balance check and alert"
    steps:
      - bank-balance:
          accountId: acc-100
        capture: balance
      - chat-send:
          channel: family
          message: "Balance: ${balance.amount}"
```

No `BankPlatform`, no `ChatPlatform` on the test classpath. The manifest
provides the step schema; the `simulate:` block provides the responses.

### Unit testing the plugin record directly

```java
@Test
void balanceLookup() {
    var plugin = new BankBalancePlugin("acc-100", "user-1");
    var result = plugin.execute(mockBankPlatform);
    assertThat(result.output().get("amount")).isEqualTo(1500.0);
}
```

---

## Dependencies

| What you're doing | Dependencies needed |
|-------------------|---------------------|
| `@Plugin` + APT | `yaml-plugin-api` (compile) + `yaml-plugin-processor` (APT) |
| YAML definitions | `yaml-plugin-api` + `yaml-step-runtime` |
| `Definition.of()` | `yaml-plugin-api` |
| Testing with simulation | `simulation-starter` (test scope) |
