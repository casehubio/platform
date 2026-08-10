# Design: EndpointPermissions + endpoints-config

**Date:** 2026-06-12  
**Issues:** platform#89, platform#88  
**Branch:** `issue-89-endpoint-permissions-config`

---

## Context

Two deferred items from platform#73 (EndpointRegistry SPI delivery):

- **#89** — `EndpointPermissions.assertTenant()` write-auth utility for runtime endpoint registration
- **#88** — `casehub-platform-endpoints-config` YAML-backed registrar for multi-tenant endpoint configuration

---

## Part 1 — EndpointPermissions (platform#89)

### Location

`platform-api/src/main/java/io/casehub/platform/api/endpoints/EndpointPermissions.java`

Same package as `EndpointRegistry`, `EndpointDescriptor`, etc. Mirrors `MemoryPermissions` in structure.

### API — 2-arg only

```java
public final class EndpointPermissions {
    private EndpointPermissions() {}

    public static void assertTenant(String tenancyId, CurrentPrincipal principal);
}
```

Error message on mismatch: `"Tenant ID mismatch: claimed=<tenancyId>, authenticated=<principal.tenancyId()>"`

### No 3-arg overload

`MemoryPermissions` has a 3-arg async-aware form because reactive-only `CaseMemoryStore` adapters (Mem0, Graphiti) receive `store()` calls from `@ObservesAsync` CDI event handlers — those run off the request thread, where the CDI request context is not active. The 3-arg form lets them skip the check safely. (ARC42STORIES §9: "adapters use the 3-arg form with `Arc.container()` null-check for `@ObservesAsync` support.")

No equivalent async endpoint-registration flow exists or is planned. The described trigger for `EndpointPermissions` is casehub-deployment's future HTTP registration endpoint — synchronous, always in a request context. Add the 3-arg when a concrete async endpoint-registration caller arrives and the `@ObservesAsync` pattern actually applies.

### Design decisions

- **No special handling for `PLATFORM_TENANT_ID`** — same as `MemoryPermissions`. Simple tenancyId equality.

- **`isCrossTenantAdmin()` gap acknowledged** — `CurrentPrincipal.isCrossTenantAdmin()` is documented as bypassing per-tenant filters. Neither `MemoryPermissions` nor `EndpointPermissions` honour it. A cross-tenant admin calling a future runtime endpoint registration endpoint would fail `assertTenant()` unless their `tenancyId()` exactly matches the descriptor's. This is a known gap shared with `MemoryPermissions`. A future platform admin endpoint must either: (a) bypass this check explicitly before delegating to the registry, or (b) require the admin to supply a matching `tenancyId`. Document at call-site level when that caller exists.

- **`InMemoryEndpointRegistry` is NOT modified** — startup registration (`@PostConstruct`) is trusted system code. No request context is active; the caller is the application runtime, not an external actor.

- **No callers wired yet** — preparatory infrastructure. Trigger: when casehub-deployment exposes a runtime endpoint registration HTTP resource.

### Tests — 2 (pure JUnit5, no Quarkus)

1. Matching tenant does not throw
2. Mismatched tenant throws `SecurityException` with both claimed and authenticated tenancyId in message

---

## Part 2 — casehub-platform-endpoints-config (platform#88)

### Module

| Field | Value |
|-------|-------|
| Folder | `endpoints-config/` |
| Artifact | `casehub-platform-endpoints-config` |
| Package | `io.casehub.platform.endpoints.config` |
| ARC42STORIES layer | L4 — Platform Extensions (alongside `config/`, `oidc/`, `expression/`) |

### YAML schema

This is the operator contract for writing endpoint config files:

```yaml
endpoints:
  - path: external/salesforce/prod        # required; parsed with casehub.platform.path.separator (default "/")
    tenancyId: tenant-a                   # required; or "platform" for TenancyConstants.PLATFORM_TENANT_ID
    type: WORKER                          # required; EndpointType enum literal — NOT interpolated
    protocol: HTTP                        # required; EndpointProtocol enum literal — NOT interpolated
    properties:                           # optional; absent → Map.of()
      url: https://${SALESFORCE_URL}/api  # ${VAR} interpolated; unresolved → startup failure
    credentialRef: sf-bearer-token        # optional; interpolated; absent → null
    capabilities:                         # required key (absent → error); empty list → Set.of()
      - SEND                              # EndpointCapability enum literals — NOT interpolated
      - QUERY
```

Multiple endpoints per file. Any mix of tenants and `tenancyId: platform` (platform-global) in the same file.

**`${VAR}` interpolation** applies to all string-typed fields: `path`, `tenancyId`, `credentialRef`, and all `properties` values. Resolution order: system property first, env var fallback, unresolved refs → startup failure. Properties values are treated as deployment-time configuration literals — `${...}` is always a variable reference, never a literal value (e.g., a Camel expression containing `${header.foo}` would need escaping or a different approach).

Enum-typed fields (`type`, `protocol`, capability strings) are NOT interpolated — they are Java enum names and must be literal.

---

### Architecture

Two classes. Strict separation between YAML parsing and CDI/deployment concerns.

---

#### `YamlEndpointLoader` — pure-Java static extractor

**Single responsibility:** open a YAML stream, extract the `endpoints:` list, return raw maps. Zero domain logic. No interpolation. No enum parsing. No file path awareness.

```java
public final class YamlEndpointLoader {
    private YamlEndpointLoader() {}
    public static List<Map<String, Object>> load(InputStream is);
}
```

`load(null)` → `List.of()`. Missing `endpoints:` key → `List.of()`. `endpoints: []` → `List.of()`. Passes raw SnakeYAML output (`Map<String, Object>`) to the caller; the caller is responsible for all type extraction, interpolation, validation, and enum parsing.

**Tests — 4 (pure JUnit5):**

1. Valid multi-endpoint YAML → correct raw list with expected keys and values
2. YAML without `endpoints:` key → empty list (no error)
3. Null stream → empty list (no error)
4. `endpoints: []` (key present, value empty) → empty list — distinct from case 2: `doc.get("endpoints")` returns an empty `List`, not `null`

---

#### `EndpointConfigLoader` — `@io.quarkus.runtime.Startup @ApplicationScoped` CDI bean

`@Startup` forces eager initialization at boot — without it, `@ApplicationScoped` is lazy and `@PostConstruct` never runs because nothing injects this bean directly.

**Injected fields:**
```java
@Inject
EndpointRegistry registry;                 // whichever CDI picks — in-memory, JPA, or no-op @DefaultBean

@ConfigProperty(name = "casehub.platform.endpoints.files")
Optional<List<String>> endpointFiles;      // absent → no-op

@ConfigProperty(name = "casehub.platform.path.separator", defaultValue = "/")
String pathSeparator;                      // same config key as PathParserConfigurator; read pre-PostConstruct
```

**Path separator and startup ordering:** Both `EndpointConfigLoader` and `PathParserConfigurator` (in `platform/`) are `@Startup @ApplicationScoped`. CDI provides no initialization ordering guarantee between two `@Startup` beans. `EndpointConfigLoader` avoids depending on `PathParserConfigurator`'s side effect (`Path.setDefaultParser()`) by reading `casehub.platform.path.separator` directly — CDI injects `@ConfigProperty` fields before `@PostConstruct` runs, regardless of startup ordering. `parseDescriptor()` receives an explicit `PathParser` constructed from this value; `Path.parse()` (no-arg, global default) is never called.

**`@PostConstruct load()` pipeline:**

When `casehub.platform.endpoints.files` is absent, the method is a complete no-op — no log, no registration.

When files are configured:

```java
endpointFiles.ifPresent(files -> {
    PathParser parser = PathParser.of(pathSeparator);
    int count = 0;
    for (String fileSpec : files) {
        try (InputStream is = openStream(fileSpec)) {
            List<Map<String, Object>> raw = YamlEndpointLoader.load(is);
            for (Map<String, Object> entry : raw) {
                registry.register(parseDescriptor(entry, parser));
                count++;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load endpoints from: " + fileSpec, e);
        }
    }
    LOG.infof("Loaded %d endpoints into %s", count, registry.getClass().getSuperclass().getSimpleName());
});
```

The `ifPresent` guard: when `endpointFiles` is absent the lambda is never invoked — no log, no registration, no access to `registry` or `pathSeparator`.

The per-file `try-catch` wraps all exceptions — I/O errors, YAML parse errors, field validation failures, bad enum values, unresolved `${VAR}` — as `RuntimeException("Failed to load endpoints from: <file>", cause)`. `parseDescriptor()` itself throws short-form messages (no file prefix); the outer catch provides the file context. The cause chain carries the full detail.

After all files: log count and registry implementation class name. `getSuperclass()` strips the ARC client proxy suffix (`_ClientProxy`) to return the actual implementation class name — reliable for single-level proxy chains (Quarkus ARC never generates multi-level proxies). If `NoOpEndpointRegistry` is active, the name is immediately visible in the startup log.

**`static EndpointDescriptor parseDescriptor(Map<String, Object> entry, PathParser parser)` — package-visible static:**

Static — has no bean-lifecycle dependency. Consistent with `openStream()` and `interpolate()`.

Field groups:

| Group | Fields | Treatment |
|-------|--------|-----------|
| Required string (interpolated) | `path`, `tenancyId` | Extract → interpolate → validate no unresolved `${VAR}` → parse |
| Required enum/list (not interpolated) | `type`, `protocol`, `capabilities` | Extract as-is → validate present → `Enum.valueOf()` / stream to enum set |
| Optional string (interpolated) | `credentialRef` | Absent → `null`; present → interpolate → validate no unresolved `${VAR}` |
| Optional map (values interpolated) | `properties` | Absent → `Map.of()`; present → interpolate each value → validate each |

Required field absent → `RuntimeException("missing field: <field>")` — no file prefix; the outer `load()` catch provides it.

Post-interpolation unresolved `${VAR}` → `RuntimeException("Unresolved variable in field '<field>': <value>")` — same, no file prefix.

`path` is parsed via `Path.parse(raw, parser)` — uses the injected `PathParser`, never the global default.

Unknown enum value → `IllegalArgumentException` from `Enum.valueOf()` — propagates up to the outer catch, wrapped with file context.

`capabilities: []` (present, empty) → `Set.of()` accepted. `capabilities:` key absent → `RuntimeException("missing field: capabilities")`.

**`static String interpolate(String value)` — package-visible static:** System property → env var → leave `${KEY}` literal. Same implementation as `ConfigFilePreferenceProvider.interpolate()`.

**`static InputStream openStream(String fileSpec)` — package-visible static:**
- `classpath:` prefix → `Thread.currentThread().getContextClassLoader().getResourceAsStream(...)` — missing resource → `IllegalArgumentException("Classpath resource not found: <path>")`
- Bare path → `new FileInputStream(fileSpec)`

---

### Tests

**`YamlEndpointLoaderTest` — 4 tests (pure JUnit5).** See above.

---

**`EndpointConfigLoaderTest` — `@QuarkusTest`, `casehub-platform-endpoints-memory` on test classpath:**

Tests the full CDI integration: bean initialization, `@ConfigProperty` injection, registry wiring.

**YAML constraint:** All test YAML resource files in this class must use pre-resolved literal values only — no `${VAR}` references. `QuarkusTestExtension` implements `BeforeAllCallback`; Quarkus starts (including all `@Startup @PostConstruct` beans) inside the extension's callback, which runs before any `@BeforeAll` in the test class. A YAML file with unresolved `${VAR}` would fail post-interpolation validation during `EndpointConfigLoader.load()`, aborting Quarkus context startup and preventing any test from running. Interpolation is tested in `EndpointDescriptorParserTest` via direct `parseDescriptor()` invocation.

**Config coherence:** A `@QuarkusTest` class shares one Quarkus application context across all test methods. `casehub.platform.endpoints.files` is fixed at context startup. `application.properties` for this class configures all three files: `classpath:test-endpoints.yaml`, `classpath:test-endpoints-global.yaml`, `classpath:test-endpoints-override.yaml`. Tests 1–3 verify endpoints from the first two files; test 4 (multi-file) verifies the override file wins for paths defined in both. The absent-config no-op path is tested in `EndpointDescriptorParserTest` (test 11) via direct `load()` invocation — it is a code-logic test, not a CDI wiring test.

1. YAML file → endpoints registered — `registry.resolve()` returns correct descriptor; assert `assertInstanceOf(InMemoryEndpointRegistry.class, registry)` to verify CDI wired the correct backend. (`registry.getClass()` returns the ARC client proxy subclass, not the implementation class itself, so `getSimpleName()` equality is wrong here — `instanceof` works because the proxy IS-A `InMemoryEndpointRegistry`.)
2. Platform-global endpoint (`tenancyId: platform`) registered — visible to other tenants via `resolve(path, "other-tenant")`
3. Multi-tenant endpoints from one file all registered independently
4. Multi-file: later file replaces earlier file descriptor for same `(path, tenancyId)` — `resolve()` returns later file's descriptor

*Log assertion:* Startup log format (`"Loaded N endpoints into ClassName"`) verified via code review. Registry class identity is asserted in test 1.

---

**`EndpointDescriptorParserTest` — plain JUnit5, no Quarkus:**

Tests static methods directly. Failure-path tests MUST be here — a failing `@PostConstruct` aborts the Quarkus application context before any test can run.

`openStream` tests (static method, no CDI needed):

1. `classpath:` prefix → loads test resource correctly (via classpath)
2. Filesystem path → loads temp file correctly
3. Missing classpath resource → `IllegalArgumentException("Classpath resource not found: ...")`

`parseDescriptor` tests (call `EndpointConfigLoader.parseDescriptor(rawMap, PathParser.of("/"))` directly):

4. All fields present and valid → correct `EndpointDescriptor` returned
5. `${VAR}` in `properties.url` resolved from system property — set in `@BeforeEach`, cleared in `@AfterEach` — → descriptor carries interpolated URL; no startup lifecycle involved
6. Unresolved `${VAR}` in `tenancyId` → `RuntimeException` message contains "Unresolved variable in field 'tenancyId'"
7. Missing required field `path` → `RuntimeException` message contains "missing field: path"
8. Unknown `type` enum value → `IllegalArgumentException` thrown directly (no outer catch in the unit test; "wraps" language applies only to the full `load()` stack)
9. `capabilities:` absent → `RuntimeException` message contains "missing field: capabilities"
10. `capabilities: []` → `EndpointDescriptor` with `Set.of()` capabilities; valid multi-capability list → descriptor with both

`load()` no-op test (absent `endpointFiles` — cannot be tested in `@QuarkusTest` since the config is fixed at context startup; tested here via direct invocation):

11. Direct `load()` call with `endpointFiles = Optional.empty()` — `ifPresent` guard returns without touching `registry` or `pathSeparator`; no exception, no registration. Both `endpointFiles` and `load()` must be package-visible (no `private` modifier) to allow direct field assignment and invocation from the unit test. `load()` is package-visible consistent with `ConfigFilePreferenceProvider.load()` (which is also package-private). `new EndpointConfigLoader()` leaves all CDI-injected fields null; only `endpointFiles` needs to be set to `Optional.empty()` to exercise the guard.

---

### Lifecycle contract — explicit operational note

`EndpointConfigLoader` is a **startup-time populator only**. No `@PreDestroy`, no reconciliation loop, no diff against persisted state.

**Operational implications:**
- For `endpoints-memory` (volatile): re-registration from YAML at next boot is a feature.
- For a future JPA backend (persistent): removing an endpoint from YAML does NOT remove it from the JPA store. Operators must manually `deregister(path, tenancyId)`. When a JPA backend exists, introduce an optional reconciliation mode (YAML-as-source-of-truth) as a configurable behaviour — not mandatory by default.

---

### Module structure

```
endpoints-config/
  pom.xml
  src/main/java/io/casehub/platform/endpoints/config/
    EndpointConfigLoader.java
    YamlEndpointLoader.java
  src/test/java/io/casehub/platform/endpoints/config/
    EndpointConfigLoaderTest.java           ← @QuarkusTest — CDI integration tests 1–4
    EndpointDescriptorParserTest.java       ← plain JUnit5 — static method unit tests 1–11
    YamlEndpointLoaderTest.java             ← plain JUnit5 — YAML extraction tests 1–4
  src/test/resources/
    application.properties
    test-endpoints.yaml
    test-endpoints-global.yaml
    test-endpoints-override.yaml            ← multi-file ordering test (test 4)
```

### pom.xml

Follows `config/pom.xml` pattern:
- `quarkus-maven-plugin` with `build` + `generate-code` + `generate-code-tests` goals (required for `@QuarkusTest`)
- `jandex-maven-plugin` (CDI bean discovery when consumed as JAR by another module)
- Compile deps: `casehub-platform-api`, `quarkus-arc`, `snakeyaml`
- Test deps: `quarkus-junit5`, `casehub-platform-endpoints-memory`

No compile dependency on `casehub-platform` — path separator is read directly from config; `PathParserConfigurator` is not injected.

Added to parent `pom.xml` `<modules>` after `endpoints-memory`.

### CLAUDE.md module table entry

```
| `endpoints-config/` | `casehub-platform-endpoints-config` | @Startup @ApplicationScoped YAML-backed endpoint populator — reads `casehub.platform.endpoints.files` at startup, parses into EndpointDescriptor records, calls EndpointRegistry.register(). Populator, not a registry implementation — populates whichever EndpointRegistry CDI selects. Requires a working registry backend (e.g. endpoints-memory) to be meaningful; silently registers into NoOpEndpointRegistry @DefaultBean otherwise (startup log reveals this). Multi-file: later files replace earlier files for same (path, tenancyId). No lifecycle reconciliation. Path separator read directly from casehub.platform.path.separator — no dependency on PathParserConfigurator. |
```

---

## ARC42STORIES updates required (at implementation time)

- Layer table L4 entry: add `endpoints-config/` alongside `config/`, `oidc/`, `expression/`
- New chapter entry (C18 or next available): platform#89 + platform#88, L1 + L4, status pending
- Deferred list in C17: mark platform#88 and platform#89 closed when shipped

---

## Protocol coherence review

| Protocol | Check |
|----------|-------|
| PP-20260612-042941 (EndpointPropertyKeys cross-module only) | No new constants added ✅ |
| PP-20260529-57cc3b (assertTenant contract) | EndpointPermissions mirrors 2-arg contract; 3-arg not applicable ✅ |
| module-tier-structure (PP-20260512-module-tiers) | EndpointPermissions in L1/Tier 1 (platform-api, pure Java); endpoints-config in L4 Platform Extensions (Quarkus + snakeyaml, no JPA) ✅ |
| persistence-backend-cdi-priority (PP-20260522-0cfa30) | EndpointConfigLoader is a populator, not a competing registry backend — no CDI priority conflict ✅ |
| optional-module-pattern (PP-20260508-6d1f5c) | Jandex index generated; zero-cost when absent from classpath ✅ |
| casehub-platform-dependency-scope | endpoints-config has quarkus:build goal (needed for its own @QuarkusTest tests) — same pattern as config/ ✅ |

---

## Out of scope (tracked separately)

- JPA-backed `EndpointRegistry` (future platform issue) — first actual `EndpointPermissions.assertTenant()` caller
- casehub-deployment runtime registration HTTP endpoint — trigger for wiring `EndpointPermissions`
- YAML reconciliation for JPA backend (deferred per lifecycle note above)
- parent#229 — PLATFORM.md capability table sync (covers EndpointRegistry delivery from C17)
