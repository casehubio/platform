# casehub-platform-testing Design

**Date:** 2026-05-19
**Issue:** casehubio/platform#4
**Epic:** epic-platform-testing
**Status:** Approved

---

## Context

Consumer repos writing `@QuarkusTest` suites need a way to inject typed preference
values, fixed identities, and controlled group memberships without going through
`MockPreferenceProvider`'s config-string path or bootstrapping a full Quarkus app.
This module provides `@Alternative @Priority(1)` in-memory implementations that
activate automatically when `casehub-platform-testing` is on the test classpath.

This follows the same pattern as `casehub-work-testing` and `casehub-engine/testing`.

---

## Module Structure

```
testing/
  pom.xml                            artifactId: casehub-platform-testing
  src/main/java/io/casehub/platform/testing/
    InMemoryPreferenceProvider.java
    FixedCurrentPrincipal.java
    InMemoryGroupMembershipProvider.java
  src/test/java/io/casehub/platform/testing/
    InMemoryPreferenceProviderTest.java
    FixedCurrentPrincipalTest.java
    InMemoryGroupMembershipProviderTest.java
```

**POM dependencies:**
- `casehub-platform-api` — SPI interfaces
- `jakarta.enterprise.cdi-api` (provided) — `@Alternative`, `@Priority`
- `junit-jupiter` (test) — module's own tests
- Jandex plugin — CDI bean discovery when consumed as a JAR

No `quarkus-arc`, no `casehub-platform` runtime dependency.

**Consumer usage:**
```xml
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-platform-testing</artifactId>
    <scope>test</scope>
</dependency>
```

---

## InMemoryPreferenceProvider

Implements `PreferenceProvider`. Activated via `@Alternative @Priority(1)` — CDI
selects it over `MockPreferenceProvider` when the module is on the test classpath.

### Storage model

Internal: `Map<Path, Map<String, Object>>` where `null` key = unscoped registration.

This maps 1:1 to a future DB schema:

```sql
CREATE TABLE preferences (
    namespace   VARCHAR  NOT NULL,
    name        VARCHAR  NOT NULL,
    scope_path  VARCHAR  NULL,    -- NULL = unscoped
    value       JSONB    NOT NULL,
    PRIMARY KEY (namespace, name, scope_path)
);
```

### Registration API

```java
/** Unscoped — returned when no path-based registration matches for this key. */
public <T extends Preference> void register(PreferenceKey<T> key, T value)

/** Scope-specific — overrides unscoped for any resolve() whose scope path matches. */
public <T extends Preference> void register(PreferenceKey<T> key, T value, Path scope)

/** Clears all registrations. Call in @BeforeEach to isolate tests. */
public void clear()
```

### Resolution — scope walking

`resolve(scope)` walks from unscoped (lowest priority) up to the most specific path
(highest priority). Most specific wins:

```
NULL (unscoped)              ← register(key, value)           — lowest priority
casehubio                    ← register(key, value, Path.of("casehubio"))
casehubio/devtown            ← register(key, value, Path.of("casehubio","devtown"))
casehubio/devtown/pr-review  ← register(key, value, Path.of("casehubio","devtown","pr-review"))
── resolve("casehubio/devtown/pr-review") → most specific non-null wins ──
key.defaultValue()           ← applied by getOrDefault() when resolve() returns null
```

Algorithm: collect unscoped into merged map, then recursively walk from root to
specific scope, each level's values overwriting the previous via `putAll`. Child
always overrides parent.

### Example

```java
@Inject InMemoryPreferenceProvider prefs;

@BeforeEach
void setup() {
    prefs.clear();
    // devtown-level default (inherited by all devtown case types)
    prefs.register(HumanApprovalThreshold.KEY, new HumanApprovalThreshold(500),
        Path.of("casehubio", "devtown"));
    // pr-review-specific override
    prefs.register(HumanApprovalThreshold.KEY, new HumanApprovalThreshold(100),
        Path.of("casehubio", "devtown", "pr-review"));
}

// resolve("casehubio/devtown/pr-review") → HumanApprovalThreshold(100)
// resolve("casehubio/devtown/code-review") → HumanApprovalThreshold(500)
// resolve("casehubio/aml/investigation") → null → getOrDefault() → key.defaultValue()
```

### Not thread-safe

Designed for single-threaded test use. Document in Javadoc. Call `clear()` in
`@BeforeEach` to isolate tests from one another.

---

## FixedCurrentPrincipal

Implements `CurrentPrincipal`. Defaults to `actorId="system"`, empty groups —
matching `MockCurrentPrincipal` defaults so switching providers has no surprise.

All four default methods (`roles()`, `hasGroup()`, `isSystem()`, `isAuthenticated()`)
are inherited from the interface — nothing to implement.

```java
@ApplicationScoped @Alternative @Priority(1)
public class FixedCurrentPrincipal implements CurrentPrincipal {

    private String actorId = "system";
    private Set<String> groups = new HashSet<>();

    public void setActorId(String actorId) { ... }
    public void setGroups(Set<String> groups) { ... }
    public void addGroup(String group) { ... }

    /** Resets to defaults (actorId="system", groups=empty). Call in @BeforeEach. */
    public void reset() { ... }

    @Override public String actorId() { return actorId; }
    @Override public Set<String> groups() { return Set.copyOf(groups); }
}
```

---

## InMemoryGroupMembershipProvider

Implements `GroupMembershipProvider`. Unknown group returns empty set, consistent
with the SPI contract.

```java
@ApplicationScoped @Alternative @Priority(1)
public class InMemoryGroupMembershipProvider implements GroupMembershipProvider {

    private final Map<String, Set<String>> members = new HashMap<>();

    public void addMember(String groupName, String actorId) { ... }
    public void removeMember(String groupName, String actorId) { ... }

    /** Clears all memberships. Call in @BeforeEach. */
    public void clear() { ... }

    @Override
    public Set<String> membersOf(String groupName) {
        return Set.copyOf(members.getOrDefault(groupName, Set.of()));
    }
}
```

Group is created implicitly on first `addMember()`. No `addGroup()` needed.

---

## Testing the testing module

Plain JUnit 5 — no `@QuarkusTest`. CDI wiring (`@Alternative @Priority(1)`) is
proven by the Jandex index in the JAR; consumer repos verify CDI in their own suites.

**`InMemoryPreferenceProviderTest`** — resolution hierarchy:
- Unscoped registration returned when no path matches
- Scoped registration overrides unscoped for matching path
- Parent scope inherited by child scope
- Child scope overrides parent (most specific wins)
- Three-level walk: unscoped → root → app → case-type
- `clear()` removes all registrations
- Missing key returns null; `getOrDefault()` returns `key.defaultValue()`

**`FixedCurrentPrincipalTest`**:
- Default: `actorId="system"`, groups empty, `isSystem()` true, `isAuthenticated()` true
- `setActorId()`, `setGroups()`, `addGroup()` reflect correctly in accessors
- `reset()` restores defaults
- `isAuthenticated()` false for `"anonymous"`, `roles()` equals groups

**`InMemoryGroupMembershipProviderTest`**:
- Unknown group → empty set
- `addMember()` / `removeMember()` / `clear()` work correctly
- `membersOf()` returns unmodifiable copy

---

## Module Roadmap

This module is one piece of the broader platform preference architecture. All
providers implement the read-only `PreferenceProvider` SPI. The write path is
a separate concern owned by harnesses, not the platform.

| Module | Read/Write | Purpose |
|--------|-----------|---------|
| `platform-api/` | — | SPIs and types |
| `platform/` | — | @DefaultBean mocks |
| `testing/` | — | in-memory @Alternative fixtures **(this epic)** |
| `config/` | read | scope-aware YAML + env var provider (Drools ChainedProperties equivalent) |
| `persistence-jpa/` | read | JPA-backed scoped overrides |
| `persistence-mongodb/` | read | MongoDB-backed scoped overrides |
| `preferences-editor/` | write | admin UI/API for editing any backend |

**Key architectural invariant:** `PreferenceProvider` is permanently read-only.
No `save()` or `update()` method will ever be added. The editor module writes
directly to the backend; the provider never knows the editor exists.

**On `key.defaultValue()`:** The compile-time default on `PreferenceKey<T>` is a
type-safe null guard — the absolute last resort if no config file, env var, or
DB value exists. Real business defaults live in the harness's properties file
(loaded by the `config/` module), following the same pattern as Drools where each
domain ships its own `*.properties` file using the shared `OptionKey<T>` /
`SingleValueOption` infrastructure.

---

## Design Decisions

- **`register()` always requires either no path (unscoped) or an explicit path** —
  no implicit root. Forces tests to be explicit about what scope they're testing.
- **Unscoped = lowest priority, not highest** — consistent with the future DB schema
  (`scope_path NULL`) and every multi-tenant configuration system surveyed (SQL Server,
  AWS SSM, Octopus Deploy).
- **Not thread-safe by design** — mirrors `casehub-work-testing` pattern. Single-threaded
  test use only; `clear()` in `@BeforeEach` provides isolation.
- **No factory split (KISS)** — considered `InMemoryPreferenceProvider.global()` vs
  constructor-with-root-path; rejected as over-engineering. A no-path `register()` is
  sufficient for scope-free tests; explicit path for scope-aware tests.
