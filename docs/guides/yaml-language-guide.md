# CaseHub YAML Language Guide

yaml-core is a composable meta-language that layers on top of any YAML structure. It provides six constructs — variables, conditions, iteration, data, modules, and typed expansion — that a host application can adopt individually or together. The host owns the YAML schema; yaml-core owns the dynamic behaviour.

Think of it as what Jinja is to HTML, or what HCL is to Terraform — except it works with any YAML structure, not a specific one.

---

## How It Layers

yaml-core does not parse YAML. It does not define what your YAML means. It operates on the parsed result — a `Map<String, Object>` tree — and transforms it.

Your application:
1. Defines its own YAML schema (what keys exist, what they mean)
2. Parses YAML into maps (Jackson, SnakeYAML, whatever you use)
3. Hands the maps to yaml-core constructs for variable substitution, iteration, conditional filtering, and module composition
4. Gets back transformed maps with all dynamic behaviour resolved

A simple application might use only variables. A full declarative platform uses everything.

```
Your YAML schema (pages, desiredstate, engine, anything)
    │
    ├── Variable resolution     ${prefix.name}
    ├── Conditional inclusion   when: "${var.enabled}"
    ├── ForEach expansion       forEach + stamp N copies
    ├── Typed CSV data          inline tabular data as iteration sources
    ├── Module system           import, parameterise, alias, compose
    └── Typed expansion         ModuleBridge<T> for domain-specific compilation
```

---

## 1. Variables

**Syntax:** `${prefix.name}`

Every variable has a prefix that identifies its source and a name that identifies the value within that source. The prefix is the key idea — it makes clear where each value comes from.

```yaml
# DesiredState: infrastructure configuration
variables:
  payment_provider: stripe
  currency: USD

nodes:
  payment:
    spec:
      provider: ${var.payment_provider}
      currency: ${var.currency}
```

```yaml
# Pages: dashboard templating
properties:
  metricsUrl: /api/metrics

datasets:
  - uuid: metrics
    url: ${metricsUrl}

html:
  html: >-
    <div style="background-color: ${bgColor}">
      <h2>${value}</h2>
      <p>${title}</p>
    </div>
```

### Default values

Use `:-` for fallback when a variable might not be set:

```yaml
timeout: ${var.request_timeout:-30}
region: ${var.deploy_region:-us-east-1}
```

### Deferred resolution

Some variables can't be resolved at compile time — they exist at runtime. Register a prefix as "deferred" and yaml-core passes it through literally:

```yaml
# ${match.sink.id} stays as-is in the output — resolved at runtime by the pattern engine
route: ${match.sink.id}
```

### Variable sources

Applications register sources per prefix. Built-in sources:

| Source | What it resolves |
|--------|-----------------|
| `VariableSource.env()` | `System.getenv()` |
| `VariableSource.systemProperty()` | `System.getProperty()` |
| `VariableSource.nested(data)` | Dot-path drilling into nested maps |
| `VariableSource.chain(a, b, c)` | First non-null across multiple sources |
| `VariableSource.forEachContext(...)` | ForEach iteration values (see §3) |

Scoping is immutable — `resolver.withScope("myprefix", mySource)` returns a new resolver with the added prefix. This makes it safe to layer sources without mutation.

---

## 2. Conditional Inclusion (`when`)

**Syntax:** `when: "${var.expression}"`

A boolean guard on any element. The expression resolves to a string, then truthiness evaluation decides include or exclude.

```yaml
nodes:
  gift-wrapping:
    when: "${var.gift_wrapping_enabled}"
    spec:
      style: premium

  premium-support:
    when: "${var.tier:-free}"
    spec:
      channel: phone
```

### Truthiness

Case-insensitive evaluation. These pairs are equivalent:

| True | False |
|------|-------|
| `true` | `false` |
| `yes` | `no` |
| `on` | `off` |
| `y` | `n` |
| `1` | `0` |

Anything else throws `IllegalArgumentException` — no silent coercion, no guessing.

### Interaction with forEach

When `when` appears on an element that also has `forEach`, the condition is evaluated per iteration value. Individual copies can be excluded while others are included:

```yaml
nodes:
  regional-cache:
    forEach:
      as: region
      in: [us-east, eu-west, ap-south]
    when: "${var.cache_enabled_${each.region}}"
    spec:
      region: ${each.region}
```

---

## 3. ForEach Expansion

**What it does:** Takes a single element and stamps N copies of it, one per value in a list. Each copy gets a stamped ID and access to the current iteration value via `${each.*}`.

### Inline iteration

The values are declared directly:

```yaml
nodes:
  shipping:
    forEach:
      as: warehouse
      in: [us-east, eu-west, ap-south]
    spec:
      warehouse: ${each.warehouse}
      endpoint: https://${each.warehouse}.shipping.internal
```

This produces three nodes: `shipping.us-east`, `shipping.eu-west`, `shipping.ap-south`. The original `shipping` ID is gone — replaced by the stamped copies.

### Named iteration groups

When multiple elements iterate over the same list, declare the group once and reference it by name:

```yaml
iterations:
  warehouses:
    as: warehouse
    in: [us-east, eu-west, ap-south]

nodes:
  shipping:
    forEach: warehouses
    spec:
      warehouse: ${each.warehouse}

  monitoring:
    forEach: warehouses
    spec:
      target: shipping.${each.warehouse}
```

Both `shipping` and `monitoring` expand in lockstep. Cross-references between them are rewritten automatically — `dependsOn: [monitoring]` becomes `dependsOn: [monitoring.us-east]` in the `shipping.us-east` copy.

### ID stamping

The stamped ID follows a predictable pattern: `originalId.iterationValue`.

| Original ID | Iteration value | Stamped ID |
|-------------|----------------|------------|
| `shipping` | `us-east` | `shipping.us-east` |
| `cache` | `hot` | `cache.hot` |
| `db-replica` | `reader-1` | `db-replica.reader-1` |

### Reference rewriting

After expansion, yaml-core rewrites cross-references between elements in the same iteration group. If `shipping` depends on `monitor`, then `shipping.us-east` depends on `monitor.us-east` — not on the pre-expansion `monitor`.

This is the key difference from a simple loop. It preserves the topology of your graph across expansion.

---

## 4. Typed CSV Data

Inline tabular data with typed columns, usable as forEach sources.

```yaml
data:
  environments: |
    name:STRING,port:INTEGER,enabled:BOOLEAN
    staging,8080,true
    production,9090,yes
    dr,8080,no

nodes:
  service:
    forEach:
      as: env
      in: environments
    when: "${each.env.enabled}"
    spec:
      name: ${each.env.name}
      port: ${each.env.port}
```

### Column types

| Type | Java type | Example values |
|------|-----------|---------------|
| `STRING` | `String` | `hello`, `us-east` |
| `INTEGER` | `Integer` | `8080`, `42` |
| `NUMBER` | `Double` | `3.14`, `0.95` |
| `BOOLEAN` | `Boolean` | `true`, `yes`, `1`, `on` |

Parse-time validation catches type errors before expansion — an `INTEGER` column with a value of `abc` fails immediately, not at runtime.

### Field access

When iterating over CSV data, `${each.<as>.<column>}` drills into row fields:

```yaml
forEach:
  as: env
  in: environments        # references the data source above

spec:
  name: ${each.env.name}       # STRING → staging
  port: ${each.env.port}       # INTEGER → 8080
  active: ${each.env.enabled}  # BOOLEAN → true
```

---

## 5. Modules

The composition system. A module is a reusable, parameterised unit of YAML content that can be imported into other YAML files.

### Defining a module

A module declares its name, typed parameters, and content sections:

```yaml
# modules/order-notifications.yaml
module:
  name: order-notifications
  parameters:
    watched_step:
      type: string
      required: true
      pattern: "^[a-z][a-z0-9-]*$"
    severity:
      type: string
      default: medium
      allowedValues: [low, medium, high, critical]

nodes:
  email-alert:
    type: notification
    dependsOn: [${param.watched_step}]
    spec:
      severity: ${param.severity}
      channel: email
  
  slack-alert:
    type: notification
    dependsOn: [${param.watched_step}]
    spec:
      severity: ${param.severity}
      channel: slack
```

### Importing a module

```yaml
imports:
  - module: order-notifications
    as: payment-alerts
    parameters:
      watched_step: payment
      severity: critical

  - module: order-notifications
    as: shipping-alerts
    parameters:
      watched_step: shipping
```

### Alias prefixing

The `as:` field prefixes all section keys from the imported module. This prevents collisions when importing the same module twice:

| Original key | Alias | Result key |
|-------------|-------|------------|
| `email-alert` | `payment-alerts` | `payment-alerts.email-alert` |
| `slack-alert` | `payment-alerts` | `payment-alerts.slack-alert` |
| `email-alert` | `shipping-alerts` | `shipping-alerts.email-alert` |

Cross-references within the module are rewritten to use the prefixed names — `dependsOn: [email-alert]` inside the module becomes `dependsOn: [payment-alerts.email-alert]` after import.

### Conditional imports

```yaml
imports:
  - module: order-notifications
    as: payment-alerts
    when: "${var.payment_notifications_enabled}"
    parameters:
      watched_step: payment
```

The entire module is included or excluded based on the `when` condition.

### Parameter validation

Parameters are type-checked and constraint-validated at expansion time:

| Constraint | Applies to | What it checks |
|-----------|------------|---------------|
| `required: true` | all types | Parameter must be provided |
| `default: value` | all types | Used when parameter is omitted |
| `minLength` / `maxLength` | STRING, LIST | Length bounds |
| `pattern: "regex"` | STRING, LIST | Each value must match |
| `minimum` / `maximum` | INTEGER, NUMBER | Numeric bounds |
| `allowedValues: [...]` | all types | Enum restriction |
| `constraintDescription` | all types | Human-readable error message |

Validation collects all errors before failing — you see every invalid parameter at once, not one at a time.

### Module outputs

Modules can declare typed output values, computed from parameters and content. Downstream imports can reference them:

```yaml
# Module declares outputs
module:
  name: database
  parameters:
    port:
      type: integer
      default: 5432
  outputs:
    connection_string: "postgres://localhost:${param.port}/mydb"

# Consumer references output
nodes:
  app:
    spec:
      db: ${module.infra-db.connection_string}
```

---

## 6. The Plugin Model (DesiredState)

DesiredState uses yaml-core to implement a plugin-based ops platform. Plugins define **node types** — what a resource looks like, how to check its actual state, and how to provision it.

```yaml
plugin:
  type: test-resource
  version: 1
  resyncInterval: 30s
  auth:
    test-api:
      credentialRef: test-api-credentials

spec:
  fields:
    name: { type: string, required: true }
    count: { type: integer, default: 1, min: 0, max: 100 }
    mode: { type: enum, values: [fast, slow, balanced] }

actual-state:
  steps:
    - rest-call:
        url: "https://${auth.test-api.endpoint}/resources/${spec.name}"
        result: response
    - compare-state:
        present-when: "${result.response.status} == 200"
        drifted-when: "${result.response.body.count} < ${spec.count}"

provisioner:
  provision:
    steps:
      - rest-call:
          method: PUT
          url: "https://${auth.test-api.endpoint}/resources/${spec.name}"
          body: { name: "${spec.name}", count: ${spec.count} }
```

The plugin uses multiple variable prefixes, each resolved by a different source:

| Prefix | Source | Resolved when |
|--------|--------|--------------|
| `${spec.*}` | Declared spec fields from the node definition | Compile time |
| `${auth.*}` | Credential store (API keys, endpoints) | Runtime |
| `${result.*}` | Step outputs (REST responses, command results) | Runtime (deferred) |
| `${var.*}` | User-defined variables section | Compile time |
| `${each.*}` | ForEach iteration context | Expansion time |
| `${fault.*}` | Fault context (error details) | Runtime (deferred) |

---

## 7. Bridging: Teaching yaml-core About Your Domain

yaml-core knows nothing about pages, desiredstate, or your domain. Host applications teach it through two adapter interfaces.

### ForEachAdapter

Teaches yaml-core how to stamp copies of your domain elements:

```java
public interface ForEachAdapter<E> {
    ForEachDirective getForEach(E element);     // read the forEach field
    String getWhen(E element);                   // read the when condition
    E stamp(E element, String stampedId, ...);   // create a copy with new ID + resolved vars
    List<String> getReferences(E element);       // extract cross-references for rewriting
    E withReferences(E element, List<String>);   // rewrite references after expansion
}
```

DesiredState implements `YamlNodeForEachAdapter` — it knows that nodes have a `spec` map to resolve, a `dependsOn` list to rewrite, and a `forEach` field to read.

### ModuleBridge

Teaches yaml-core how to convert between raw maps and your typed domain model:

```java
public interface ModuleBridge<T> {
    T fromSections(Map<String, Map<String, Object>> sections);  // raw → typed
    Map<String, Map<String, Object>> toSections(T content);     // typed → raw
    // optional:
    SectionContentRewriter rewriter();     // rewrite refs after alias prefixing
    Map<String, String> deriveOutputs(T);  // compute module outputs from content
}
```

DesiredState implements `DesiredStateModuleBridge` — it knows that sections contain `nodes`, `rules`, and `invariants`, and that `dependsOn` references need rewriting when module aliases prefix IDs.

Pages does not implement either adapter — it uses only variable resolution, which needs no bridging.

---

## 8. Composition: Putting It All Together

A complete DesiredState YAML using all six constructs:

```yaml
variables:
  environment: production
  cache_enabled: "true"
  monitoring_enabled: "true"

data:
  regions: |
    name:STRING,primary:BOOLEAN
    us-east,true
    eu-west,false
    ap-south,false

imports:
  - module: monitoring-stack
    as: infra
    when: "${var.monitoring_enabled}"
    parameters:
      alert_threshold: "90"

iterations:
  regional:
    as: region
    in: regions

nodes:
  api-gateway:
    forEach: regional
    spec:
      region: ${each.region.name}
      primary: ${each.region.primary}
      cache: ${var.cache_enabled}

  cache:
    forEach: regional
    when: "${var.cache_enabled}"
    spec:
      region: ${each.region.name}
      ttl: 300

  database:
    spec:
      engine: postgres
      version: "16"
```

**What yaml-core does with this:**

1. **Variables** resolve `${var.environment}`, `${var.cache_enabled}`, `${var.monitoring_enabled}`
2. **CSV data** parses the `regions` table into typed rows
3. **Module import** expands `monitoring-stack` with alias `infra`, conditional on `monitoring_enabled`
4. **ForEach** stamps `api-gateway` and `cache` per region row → `api-gateway.us-east`, `api-gateway.eu-west`, etc.
5. **When** filters `cache` copies per region — only included where `cache_enabled` is truthy
6. **Reference rewriting** updates any `dependsOn` between `api-gateway` and `cache` to use stamped IDs

The output is a flat map of fully resolved, stamped nodes with no dynamic constructs remaining — ready for your reconciliation engine, renderer, or deployment pipeline.

---

## JSON Schema Composition

yaml-core publishes JSON Schema fragments for each construct in `src/main/resources/schema/`:

| Fragment | What it defines |
|----------|----------------|
| `variable.schema.json` | `${prefix.name}` string pattern |
| `foreach.schema.json` | `forEach` field (inline or group reference) |
| `iterations.schema.json` | Named iteration groups |
| `when.schema.json` | `when` condition field |
| `module.schema.json` | Module declaration + parameters + outputs |
| `data.schema.json` | Typed CSV data sources |

Host applications compose these into their domain schema via `$ref`:

```json
{
  "properties": {
    "nodes": {
      "additionalProperties": {
        "properties": {
          "forEach": { "$ref": "classpath:schema/foreach.schema.json" },
          "when": { "$ref": "classpath:schema/when.schema.json" },
          "spec": { "type": "object" }
        }
      }
    },
    "iterations": { "$ref": "classpath:schema/iterations.schema.json" },
    "data": { "$ref": "classpath:schema/data.schema.json" },
    "imports": { "$ref": "classpath:schema/module.schema.json#/definitions/imports" }
  }
}
```

This gives you IDE validation, auto-complete, and documentation for the yaml-core constructs while keeping your domain-specific fields under your own schema control.

---

## Dependencies

| Artifact | When to use |
|----------|------------|
| `casehub-platform-yaml-core` | Always — the language primitives. Zero external dependencies, J2CL-transpilable |
| `casehub-platform-yaml-jackson` | When parsing YAML with Jackson — registers mixins for yaml-core types, enables dynamic section capture and case-insensitive enum deserialization |
| `casehub-platform-yaml-codegen` | When generating Java records from JSON Schema — a Maven plugin, separate from the language runtime |
