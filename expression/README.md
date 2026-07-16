# casehub-platform-expression

Pluggable expression engines with `$secret` and `$config` scope injection for JQ expressions.

## Engines

| Engine | Type key | Description |
|--------|----------|-------------|
| MVEL3 | `mvel` | MVEL3 transpiler, lazy compilation, ConcurrentHashMap cache |
| JQ | `jq` | jackson-jq wrapper, Boolean and List result types |

## Secret and Config injection

JQ expressions can reference secrets and config maps via scope variables:

```
$secret.openai.apiKey        # → SecretManager.secret("openai").get("apiKey")
$config.app-config.timeout   # → ConfigManager.configMap("app-config").get("timeout")
```

### Default implementations

`MockSecretManager` and `MockConfigManager` (`@DefaultBean`) read from SmallRye Config (MicroProfile Config API). In dev/test, values come from `application.properties`:

```properties
casehub.platform.secrets.openai.apiKey=sk-test-key
casehub.platform.secrets.openai.organizationId=org-test
```

### Kubernetes deployment

Add `quarkus-kubernetes-config` to the consumer application and enable it under the `%prod` profile. The extension reads Kubernetes ConfigMaps and Secrets via the K8s API and injects them into SmallRye Config, where the default implementations pick them up automatically.

**1. Add the dependency to the consumer POM:**

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-kubernetes-config</artifactId>
</dependency>
```

**2. Configure `application.properties`:**

```properties
# Enable only in production (disabled by default — safe for dev/test)
%prod.quarkus.kubernetes-config.enabled=true
%prod.quarkus.kubernetes-config.secrets=casehub-secrets
%prod.quarkus.kubernetes-config.secrets.enabled=true
%prod.quarkus.kubernetes-config.config-maps=casehub-config
%prod.quarkus.kubernetes-config.fail-on-missing-config=true
```

**3. Create the Kubernetes Secret:**

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: casehub-secrets
type: Opaque
stringData:
  casehub.platform.secrets.openai.apiKey: "sk-proj-..."
  casehub.platform.secrets.openai.organizationId: "org-..."
  casehub.platform.secrets.anthropic.apiKey: "sk-ant-..."
```

**4. Create the Kubernetes ConfigMap:**

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: casehub-config
data:
  app-config.timeout: "5000"
  app-config.retries: "3"
  app-config.database.host: "postgres.default.svc.cluster.local"
  app-config.database.port: "5432"
```

**5. RBAC (auto-generated when using `quarkus-kubernetes` extension):**

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: casehub-config-reader
rules:
  - apiGroups: [""]
    resources: ["configmaps"]
    verbs: ["get"]
  - apiGroups: [""]
    resources: ["secrets"]
    verbs: ["get"]
```

### Key points

- The extension is **disabled by default** — dev and test environments are never affected.
- Use the `%prod` profile prefix to activate only in Kubernetes.
- Secret keys must use the `casehub.platform.secrets.{name}.{property}` convention.
- ConfigMap keys must use the `{configMapName}.{property}` convention.
- Dotted keys are parsed into nested maps (e.g. `database.host` becomes `{database: {host: ...}}`).
- No additional modules or implementations are needed beyond the Quarkus extension.
