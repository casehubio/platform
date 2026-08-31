---
capability: expressions
audience: consumer
repo: casehub-platform
anchors:
  classes:
    - io.casehub.platform.expression.ExpressionEngineRegistry
    - io.casehub.platform.expression.JQExpressionEngine
    - io.casehub.platform.expression.MvelExpressionEngine
    - io.casehub.platform.expression.JexlExpressionEngine
    - io.casehub.platform.expression.ConfigManager
    - io.casehub.platform.expression.SecretManager
  spis:
    - io.casehub.platform.expression.ExpressionEvaluator
    - io.casehub.platform.expression.ConfigManager
    - io.casehub.platform.expression.SecretManager
  config-keys:
    - casehub.platform.secrets
---

# Expression Evaluation

Multi-engine expression evaluation: JQ, MVEL3, and JEXL3, with config and secret injection.

## Modules

| Artifact | What it activates |
|----------|-------------------|
| `casehub-platform-expression` | JQ + MVEL3 + JEXL3 expression engines; `DefaultExpressionEngineRegistry`; `ConfigManager`; `SecretManager` |

## Engines

`ExpressionEngineRegistry` dispatches by type key. Three engines are available:

| Engine | Type Key | Backend | Context Type | Notes |
|--------|----------|---------|-------------|-------|
| `JQExpressionEngine` | `"jq"` | jackson-jq 1.6 | `JsonNode` or `Map<String, Object>` (auto-adapted) | Boolean, List, and Scalar result types. `$config` and `$secret` scope injection. |
| `MvelExpressionEngine` | `"mvel"` | MVEL3 3.0.0-SNAPSHOT | `Map<String, Object>` or POJO (auto-adapted via BeanInfo) | Block expressions (semicolon-delimited). Lazy compilation on first eval. |
| `JexlExpressionEngine` | `"jexl"` | Commons JEXL 3.4.0 | `Map<String, Object>` | MapContext-based. Strict mode off, silent mode off. Cached compilation. |

## Scope Injection

**ConfigManager SPI:** Provides access to configuration properties in JQ expressions via `$config.{configMapName}.{property}`. Default implementation reads from SmallRye Config (MicroProfile Config API). Supports Kubernetes ConfigMaps via optional `quarkus-kubernetes-config` dependency.

**SecretManager SPI:** Resolves secrets in JQ expressions via `$secret.{secretName}.{property}`. Default reads from `casehub.platform.secrets.{secretName}.{property}` config keys. Supports Kubernetes Secrets via optional `quarkus-kubernetes-config`.

**StringExpressionEvaluator:** Sub-interface of `ExpressionEvaluator` for string-based evaluators (carries `expression()` string). Concrete records: `JQExpressionEvaluator`, `MvelExpressionEvaluator`.

## Configuration

| Property | Purpose | Default |
|----------|---------|---------|
| `casehub.platform.secrets.{name}.{property}` | Secret values accessible as `$secret.{name}.{property}` in JQ | -- |
| `%prod.quarkus.kubernetes-config.enabled` | Enable Kubernetes ConfigMap/Secret integration | false |
| `%prod.quarkus.kubernetes-config.config-maps` | Kubernetes ConfigMaps to read | -- |
| `%prod.quarkus.kubernetes-config.secrets` | Kubernetes Secrets to read | -- |
