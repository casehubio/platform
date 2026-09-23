# Platform Observability — Health, Metrics, Info

Vendor-neutral observability infrastructure for CaseHub platform SPIs. Three modules:
core Micrometer instrumentation, Spring Boot Actuator integration, Quarkus MicroProfile Health integration.

Refs #398

---

## 1. Module Architecture

### 1.1 platform-observability-core

Vendor-neutral POJOs depending on `platform-api`, `agent-api`, and `micrometer-core`. No CDI, no Spring annotations.

**Instrumented wrappers** — decorator POJOs that record Micrometer counters and timers around SPI calls:

- `InstrumentedAgentBackend implements AgentBackend` — wraps a single backend delegate. Records:
  - `casehub.platform.agent.invocations` (counter, tag: `backend` = `delegate.key()`)
  - `casehub.platform.agent.invocation.duration` (timer, tag: `backend` = `delegate.key()`)
  - `casehub.platform.agent.sessions.opened` (counter, tag: `backend` = `delegate.key()`)
  - For `invoke()`: timer uses `Multi.createFrom().deferred()` + `onTermination()` to measure from subscription to stream termination (correct for cold `Multi<AgentEvent>` streams)
  - For `openSession()`: timer wraps the blocking call directly; counter increments on entry
  - Backend key comes from `delegate.key()` — clean, no model string parsing needed. Instrumenting at `AgentBackend` (not `AgentProvider`) gives direct access to the backend identity.

- `InstrumentedAccessControlProvider implements AccessControlProvider` — wraps a delegate. Records:
  - `casehub.platform.acl.can_access` (counter, tag: `action`)
  - `casehub.platform.acl.can_access.duration` (timer, tag: `action`)
  - Only `canAccess()` is instrumented — mutations (`grant`, `revoke`, `deny`) are low-frequency admin operations

**Gauge binder** — `PlatformGaugeBinder implements MeterBinder`:
- Constructor takes optional dependencies (`ModelRegistry`, `DeliveryChannelRegistry`, `List<AgentBackend>`) — each may be null
- Registers gauges only for non-null dependencies:
  - `casehub.platform.model.registry.size` — `registry.all().size()`
  - `casehub.platform.delivery.channels.registered` — `registry.discover().size()`
  - `casehub.platform.agent.backends.discovered` — `backends.size()`

**Certificate utility** — `KeyStoreExpiryChecker`:
- Constructor: `(String path, char[] password, String type, int warningDays)`
- Method: `check()` → `CertificateExpiryResult`
- Uses pure JDK `java.security.KeyStore` — no DSS dependency
- `CertificateExpiryResult` record: `boolean healthy`, `List<CertificateStatus> certificates`
- `CertificateStatus` record: `String alias`, `String subjectDn`, `Instant notAfter`, `long daysRemaining`, `boolean expired`
- Returns empty result when path is null or blank (no keystore configured)

### 1.2 platform-spring-actuator

Spring Boot Actuator integration. Depends on `platform-observability-core` and `spring-boot-actuator` (provided scope — the module's `@AutoConfiguration` is guarded by `@ConditionalOnClass(HealthIndicator.class)`).

**MetricsBeanPostProcessor** — `BeanPostProcessor` that wraps SPI beans:
- Wraps each `AgentBackend` bean with `InstrumentedAgentBackend`
- Wraps `AccessControlProvider` beans with `InstrumentedAccessControlProvider`
- Requires `MeterRegistry` injection
- For `AgentBackend` wrapping: each backend gets its own instrumented wrapper, metrics tagged with `backend.key()`. Sits inside the agent-gate layer (gate wraps `AgentProvider`, metrics wraps individual `AgentBackend` beans) — measures backend execution time, not admission delay

**PlatformGaugeBinder @Bean** — constructs from available beans, registered as `MeterBinder` for auto-discovery.

**Health indicators** — each `@ConditionalOnBean` on the relevant SPI type:

| Indicator | Guard | Logic | Group |
|-----------|-------|-------|-------|
| `ModelRegistryHealthIndicator` | `@ConditionalOnBean(MutableModelRegistry.class)` | UP when `registry.all().size() > 0`; details: model count, vendor breakdown | default |
| `AgentBackendHealthIndicator` | `@ConditionalOnBean(AgentBackend.class)` | UP when ≥1 backend discovered; details: backend keys | default |
| `DeliveryChannelHealthIndicator` | `@ConditionalOnBean(DeliveryChannelRegistry.class)` | UP when `registry.discover().size() > 0`; details: channel IDs | default |
| `ScimHealthIndicator` | `@ConditionalOnBean(ScimClient.class)` | Calls `ScimClient.listGroups` with minimal filter, catches exceptions; short timeout | readiness |
| `CertificateExpiryHealthIndicator` | `@ConditionalOnBean(KeyStoreExpiryChecker.class)` | DOWN when any cert expired; WARNING when within threshold; details: per-cert status | default |

`KeyStoreExpiryChecker` is produced as a `@Bean` only when `casehub.signing.keystore-path` is configured (via `@ConditionalOnProperty`).

The `ScimHealthIndicator` is registered in the **readiness** group — SCIM is an external dependency; its unavailability should stop traffic, not trigger container restart.

`ModelRegistryHealthIndicator` guards on `MutableModelRegistry` (not `ModelRegistry`) to avoid activating when only the `NoOpModelRegistry` fallback is present — an unconfigured capability is not unhealthy.

**PlatformInfoContributor** — contributes to `/actuator/info`:
- `casehub.platform.version` — from Maven `Implementation-Version` manifest entry
- `casehub.platform.modules` — list of detected CaseHub auto-configuration classes (via `AutoConfigurationPackages`)
- `casehub.platform.agent.backends` — backend keys from discovered `AgentBackend` beans
- `casehub.platform.model.registry.size` — model count

**AutoConfiguration class:** `PlatformActuatorAutoConfiguration`
- `@AutoConfiguration`
- `@ConditionalOnClass(HealthIndicator.class)`
- Registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**Starter inclusion:** Added as a dependency in `spring-boot-starter` POM. When `spring-boot-starter-actuator` is on classpath, all indicators activate. When absent, `@ConditionalOnClass` prevents any bean registration — zero cost.

### 1.3 platform-observability

Quarkus MicroProfile Health + Micrometer integration. Depends on `platform-observability-core`, `quarkus-smallrye-health`, and `quarkus-micrometer`.

**`@Decorator` wrappers:**
- `ObservedAgentBackend @Decorator @Priority(1900)` — wraps each `AgentBackend` with `InstrumentedAgentBackend`. Each backend gets its own decorator instance with metrics tagged by `key()`.
- `ObservedAccessControlProvider @Decorator @Priority(1900)` — wraps `AccessControlProvider` with `InstrumentedAccessControlProvider`

**MicroProfile health checks** — use `Instance<T>` injection for optional SPIs. When the SPI is unsatisfied, the check does not register (returns UP with "not configured" note).

| Check | Type | Logic |
|-------|------|-------|
| `ModelRegistryHealthCheck` | `@Liveness` | UP when `MutableModelRegistry` has models |
| `AgentBackendHealthCheck` | `@Liveness` | UP when ≥1 `AgentBackend` bean discovered |
| `DeliveryChannelHealthCheck` | `@Liveness` | UP when channels registered |
| `ScimHealthCheck` | `@Readiness` | Pings ScimClient, catches exceptions |
| `CertificateExpiryHealthCheck` | `@Liveness` | Checks KeyStoreExpiryChecker |

**`@Produces PlatformGaugeBinder`** — CDI bean, auto-discovered by `quarkus-micrometer`.

**`@Produces KeyStoreExpiryChecker`** — produced from `DssSigningConfig` when keystore path is configured.

---

## 2. Dependency Graph

```
platform-api        agent-api
     │                  │
     └────────┬─────────┘
              │
   platform-observability-core
       (+ micrometer-core)
              │
     ┌────────┴────────┐
     │                 │
platform-observability  platform-spring-actuator
  (+ quarkus-micrometer    (+ spring-boot-actuator
   + microprofile-health)   + scim-core optional)
```

`scim-core` is an optional dependency in both framework modules — the SCIM health indicator only activates when `ScimClient` is available.

---

## 3. Metric Catalog

### 3.1 Invocation Metrics (from instrumented wrappers)

| Metric | Type | Tags | Source |
|--------|------|------|--------|
| `casehub.platform.agent.invocations` | counter | `backend` | InstrumentedAgentBackend |
| `casehub.platform.agent.invocation.duration` | timer | `backend` | InstrumentedAgentBackend |
| `casehub.platform.agent.sessions.opened` | counter | `backend` | InstrumentedAgentBackend |
| `casehub.platform.acl.can_access` | counter | `action` | InstrumentedAccessControlProvider |
| `casehub.platform.acl.can_access.duration` | timer | `action` | InstrumentedAccessControlProvider |

### 3.2 Gauge Metrics (from PlatformGaugeBinder)

| Metric | Type | Source |
|--------|------|--------|
| `casehub.platform.model.registry.size` | gauge | ModelRegistry.all().size() |
| `casehub.platform.delivery.channels.registered` | gauge | DeliveryChannelRegistry.discover().size() |
| `casehub.platform.agent.backends.discovered` | gauge | List<AgentBackend>.size() |

---

## 4. Configuration

### 4.1 Certificate expiry checker

| Property | Default | Description |
|----------|---------|-------------|
| `casehub.signing.keystore-path` | (none) | Path to PKCS#12 keystore. Checker bean not created when absent. |
| `casehub.signing.keystore-password` | (none) | Keystore password |
| `casehub.signing.keystore-type` | `PKCS12` | Keystore type |
| `casehub.signing.expiry-warning-days` | `30` | Days before expiry to report WARNING |

These properties align with the existing `DssSigningConfig` in platform-signing — same namespace, same semantics.

### 4.2 SCIM health check

The SCIM health indicator uses the existing SCIM configuration (`casehub.platform.scim.endpoint`). No additional config.

### 4.3 Spring actuator grouping

The SCIM health indicator is in the `readiness` group. To configure Kubernetes probes:

```yaml
management:
  endpoint:
    health:
      group:
        readiness:
          include: readinessState,scim
```

---

## 5. Testing Strategy

### 5.1 platform-observability-core

- `InstrumentedAgentBackendTest` — verify counter increments, timer records duration, reactive stream timing correct (subscription to termination), cancellation handled
- `InstrumentedAccessControlProviderTest` — verify counter increments with action tag, timer records, delegate passthrough
- `PlatformGaugeBinderTest` — verify gauges register with correct values, null dependencies skipped
- `KeyStoreExpiryCheckerTest` — generate test PKCS#12 keystore in `@BeforeAll`, verify healthy/expiring/expired detection, null path returns empty result

### 5.2 platform-spring-actuator

- `MetricsBeanPostProcessorTest` — verify wrapping occurs, verify unwrapped when SPI not present
- Per-indicator tests: `@SpringBootTest` with mock SPI beans, verify Health status UP/DOWN/OUT_OF_SERVICE with details
- `PlatformInfoContributorTest` — verify info map structure
- Integration: extend `SpringBootCompositionTest` to verify actuator indicators register (already has actuator on classpath)

### 5.3 platform-observability

- Per-decorator tests: `@QuarkusTest` with mock SPIs, verify metrics recorded
- Per-health-check tests: verify liveness/readiness classification, verify optional SPI handling
- Integration: Quarkus dev services test verifying `/q/health` includes platform checks

---

## 6. SpringParityRule Impact

Three new modules:
- `platform-observability-core` — name ends with `-core`, auto-excluded by naming convention
- `platform-spring-actuator` — Spring module, needs exception entry (no generator — hand-written Actuator integration)
- `platform-observability` — Quarkus module, auto-detected as needing Spring coverage → but `platform-spring-actuator` IS the Spring counterpart. Exception needed with justification: "Spring parity via platform-spring-actuator (different name, same capability)"

---

## 7. CLAUDE.md Updates

New modules to add to the module table:

| Module | Artifact | Purpose |
|--------|----------|---------|
| `platform-observability-core/` | `casehub-platform-observability-core` | Vendor-neutral Micrometer instrumented wrappers + gauge binder + keystore expiry checker. Depends on platform-api, agent-api, micrometer-core. No CDI, no Spring. |
| `platform-spring-actuator/` | `casehub-platform-spring-actuator` | Spring Boot Actuator integration — HealthIndicators (model registry, agent backends, delivery channels, SCIM, certificate expiry), MetricsBeanPostProcessor (wraps AgentProvider + AccessControlProvider), PlatformInfoContributor. @ConditionalOnClass(HealthIndicator.class). Included in spring-boot-starter. |
| `platform-observability/` | `casehub-platform-observability` | Quarkus MicroProfile Health + Micrometer — @Decorator wrappers for AgentProvider + AccessControlProvider (Priority 1900), @Liveness/@Readiness HealthCheck implementations, PlatformGaugeBinder @Produces. |

---

## References

- `AgentProvider.java:26` — reactive `Multi<AgentEvent>` return type, cold stream semantics
- `AccessControlProvider.java:8` — `canAccess()` default method, hot path for authorization
- `ModelRegistry.java:6` — SPI interface, `all()` returns full catalog
- `DeliveryChannelRegistry.java:14` — SPI interface, `discover()` returns registered channels
- `AgentBackend.java:13` — `key()` for backend identification
- `ScimClient.java:6` — framework-neutral SCIM client in scim-core
- `KeyStoreManager.java:17` — Quarkus CDI + DSS dependency, motivates pure JDK approach
- `CertificateExpiryMonitor.java:11` — existing POJO pattern for cert checking
- `AgentGateBeanPostProcessor.java` — established BeanPostProcessor wrapping pattern
- `GatedAgentProvider.java` — CDI @Decorator pattern at Priority 2000
- `PlatformDefaultsManualConfig.java` — existing Spring auto-config pattern
- Audit REPORT.md finding #14 — "Zero Actuator integration"
- Decisions D10-D15 in `decisions.md`
