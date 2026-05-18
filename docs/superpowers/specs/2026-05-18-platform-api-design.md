# Design: casehub-platform-api SPIs and platform mock implementations

**Date:** 2026-05-18  
**Issue:** casehubio/platform#1  
**Epic:** epic-platform-api  
**Status:** Approved

---

## Context

`casehub-platform` is the zero-dependency foundational module, first in the build order. It
publishes two artifacts: `casehub-platform-api` (pure Java SPIs, no Quarkus) and
`casehub-platform` (@DefaultBean Quarkus mocks). Every SPI gets a @DefaultBean mock
displaceable by a real @ApplicationScoped implementation in consumer deployments.

---

## Module Structure

Two modules, following the three-tier rule (module-tier-structure protocol):

| Folder | Artifact | Tier | What it contains |
|--------|----------|------|-----------------|
| `platform-api/` | `casehub-platform-api` | Tier 1 | Pure Java interfaces, records, final classes — zero deps |
| `platform/` | `casehub-platform` | Tier 3 | @DefaultBean @ApplicationScoped mocks; depends on platform-api + quarkus-arc |

Note: folder names deviate from canonical `api/` / `runtime/` per maven-submodule-folder-naming
protocol. This is intentional — the structure is pre-created and documented in CLAUDE.md.

---

## Package Structure

```
platform-api/src/main/java/io/casehub/platform/api/
  path/
    Path.java
  preferences/
    Preference.java
    SingleValuePreference.java
    MultiValuePreference.java
    PreferenceKey.java
    Preferences.java
    PreferenceProvider.java
    SettingsScope.java
    MapPreferences.java

platform/src/main/java/io/casehub/platform/mock/
  MockCurrentPrincipal.java
  MockGroupMembershipProvider.java
  MockPreferenceProvider.java
```

---

## Type Specifications

### Path (io.casehub.platform.api.path)

```java
public record Path(String value, List<String> segments) {
    public static Path of(String value) { ... }
    public static Path of(String... segments) { ... }
    public Path parent() { ... }           // null at root (single-segment path)
    public boolean isAncestorOf(Path other) { ... }
    public int depth() { return segments.size(); }
}
```

**`of(String value)` contract:**
- Strips outer whitespace (`value.strip()`)
- Throws `IllegalArgumentException` on blank input
- Splits on `/` with `-1` limit (preserves empty parts for detection)
- Throws `IllegalArgumentException` on any empty segment (leading, trailing, or consecutive slashes)
- `value` preserves the stripped original string — no mutation — guaranteeing round-trip equality
- `segments` is `List.of(parts)` — immutable

**`of(String... segments)` contract:**
- Applies the same per-segment validation to each element
- Joins with `/` to form `value`

**`parent()`:** returns `Path` of all segments except the last; `null` at root.

**`isAncestorOf(Path other)`:** `other.segments` starts with this path's segments AND is strictly longer.

Note: `Path.ofLenient(String)` is reserved as a named opt-in for user-facing input that
needs silent trimming. Do not add it unless explicitly needed.

---

### Preferences (io.casehub.platform.api.preferences)

```java
public interface Preference {}
public interface SingleValuePreference extends Preference {}
public interface MultiValuePreference extends Preference {}

public final class PreferenceKey<T extends Preference> {
    public PreferenceKey(String namespace, String name) { ... }
    public String namespace() { ... }
    public String name() { ... }
    public String qualifiedName() { return namespace + "." + name; }
}

public record SettingsScope(Path scope, Instant effectiveAt) {
    public static SettingsScope of(Path scope) { ... }
    public static SettingsScope of(String... segments) { ... }
}

public interface Preferences {
    <T extends SingleValuePreference> T get(PreferenceKey<T> key);
    <T extends MultiValuePreference> T get(PreferenceKey<T> key, String subKey);
    Map<String, Object> asMap();   // for CaseContext/JQ injection
}

public interface PreferenceProvider {
    /** resolve() must apply parent-scope inheritance before returning. */
    Preferences resolve(SettingsScope scope);
}
```

**Missing-key contract:** `Preferences.get()` returns `null` when the key is not present.
Callers fall back to a `DEFAULT` constant on the Preference record:

```java
HumanApprovalThreshold t = prefs.get(HumanApprovalThreshold.KEY);
int threshold = t != null ? t.value() : HumanApprovalThreshold.DEFAULT.value();
```

This keeps the `Preferences` interface surface minimal. Defaults are discoverable alongside
the key definition on the record, not scattered across call sites.

---

### MapPreferences (io.casehub.platform.api.preferences)

```java
public final class MapPreferences implements Preferences {
    public MapPreferences(Map<String, Object> values) { ... }
    // get() unchecked-casts from map.get(key.qualifiedName()); returns null if absent
    // asMap() returns unmodifiable copy
}
```

Utility implementation in `platform-api` (zero Quarkus dependency). Used by
`MockPreferenceProvider` and by test code that needs a programmatically constructed
`Preferences` instance.

---

### Identity (io.casehub.platform.api.identity)

```java
public interface CurrentPrincipal {
    String actorId();
    Set<String> groups();

    /**
     * Groups serve as roles by convention — wires directly to @RolesAllowed without
     * an interface change. Override to separate roles from group membership once RBAC matures.
     */
    default Set<String> roles() { return groups(); }

    /**
     * Override in directory-backed implementations — iterating the full group set on
     * every call is wasteful in production.
     */
    default boolean hasGroup(String group) { return groups().contains(group); }

    default boolean isSystem() { return "system".equals(actorId()); }
    default boolean isAuthenticated() { return !"anonymous".equals(actorId()); }

    // TODO: add ActorType actorType() once ActorType migrates from casehub-ledger-api
    //       to casehub-platform-api (see casehubio/ledger migration issue)
}

public interface GroupMembershipProvider {
    Set<String> membersOf(String groupName);   // empty set = unknown group
}
```

**RBAC notes:**
- `roles()` defaults to `groups()`. This documents the groups-as-roles convention and makes
  `@RolesAllowed` wiring work without an interface change.
- `isAuthenticated()` uses `"anonymous"` as the sentinel for unauthenticated principals.
  The mock defaults to `actorId = "system"` (authenticated).
- `ActorType` integration is deferred pending migration from `casehub-ledger-api`.

**CDI scope note (for implementors):**
Real `CurrentPrincipal` implementations must be `@RequestScoped`, backed by the active
security context (e.g. Quarkus `SecurityIdentity`). Injecting a `@RequestScoped` bean into
an `@ApplicationScoped` REST resource is safe — CDI client proxies delegate to the correct
contextual instance per request.

`MockCurrentPrincipal` is intentionally `@ApplicationScoped`: no request context exists in
dev/test mode, and the mock reads from `@ConfigProperty` (fixed values, no per-request
state). `@DefaultBean` yields to any non-default bean regardless of scope, so the mock is
cleanly displaced by a `@RequestScoped` real implementation.

⚠️ Do not access `CurrentPrincipal` inside reactive pipelines (`Uni`/`Multi`) without
`@ActivateRequestContext` — `@RequestScoped` implementations will throw
`ContextNotActiveException` when the request context is not active on the executing thread.

---

## Mock Implementations (io.casehub.platform.mock)

All mocks: `@ApplicationScoped @DefaultBean` (using `io.quarkus.arc.DefaultBean`).

### MockCurrentPrincipal

```java
@ConfigProperty(name = "casehub.platform.principal.actorId", defaultValue = "system")
String actorId;

@ConfigProperty(name = "casehub.platform.principal.groups", defaultValue = "")
List<String> groups;
```

Implements `actorId()` and `groups()`. All default methods (`roles()`, `hasGroup()`,
`isSystem()`, `isAuthenticated()`) are inherited from the interface.

To simulate unauthenticated: `casehub.platform.principal.actorId=anonymous`.

### MockGroupMembershipProvider

`membersOf(String groupName)` always returns `Set.of()`. Tasks route to any available worker.

### MockPreferenceProvider

```java
/**
 * Mock implementation for dev and test.
 *
 * Typed get() always returns null — callers must fall back to the Preference
 * record's DEFAULT constant. This is by design: typed Preference instances cannot
 * be injected via SmallRye config.
 *
 * asMap() returns String values suitable for CaseContext/JQ injection. Config keys
 * match PreferenceKey.qualifiedName() (namespace.name), e.g.:
 *   casehub.platform.preferences.defaults.devtown.humanApprovalThreshold=500
 *
 * Ignores scope hierarchy — returns the same flat map for every SettingsScope.
 * Real implementations walk scope.scope().segments() applying inheritance per level.
 */
@ConfigProperty(name = "casehub.platform.preferences.defaults")
Map<String, String> defaults;
```

`resolve(scope)` returns `new MapPreferences(new HashMap<>(defaults))`.

---

## Testing

### platform-api tests (no Quarkus)

**`PathTest`**
- `of(String)` happy path: segments split correctly, value preserved
- Strict validation: blank input, leading slash, trailing slash, consecutive slashes — all throw `IllegalArgumentException`
- `of(String... segments)`: per-segment validation
- `parent()`: non-root and root (null)
- `isAncestorOf()`: ancestor true, ancestor false, same path false

**`CurrentPrincipalSpiTest`** (anonymous impl, per spi-default-method-contract-test protocol)
- `roles()` delegates to `groups()`
- `hasGroup()` returns true/false correctly
- `isSystem()` checks actorId == "system"
- `isAuthenticated()` returns false for "anonymous", true for "system"

**`MapPreferencesTest`**
- Missing key returns null
- Present key casts and returns correctly
- `asMap()` returns unmodifiable map

### platform tests (@QuarkusTest)

One test class boots all three mock beans and verifies:
- `MockCurrentPrincipal`: default actorId "system", groups empty, isSystem() true, isAuthenticated() true
- `MockGroupMembershipProvider`: membersOf() always returns empty
- `MockPreferenceProvider`: resolve() returns MapPreferences, asMap() contains configured defaults

---

## Deferred / Out of Scope

- `Path.ofLenient(String)` — reserved name for future lenient parsing if needed
- `ActorType actorType()` on `CurrentPrincipal` — pending `ActorType` migration from `casehub-ledger-api`
- `platform-testing/` module — correct future direction for @Alternative test fixtures; premature now

---

## Downstream (issue tasks — not in this implementation)

Per issue #1 section 5 and 6:
- Update casehub-parent BOM: add `casehub-platform-api` and `casehub-platform` to `<dependencyManagement>`
- Update CI workflows: `casehub-platform` builds before `casehub-ledger`
- Raise migration issues in casehubio/work, casehubio/ledger, casehubio/engine (do not modify those repos)
