# Preference Schema Versioning — Design Spec

**Issue:** casehubio/platform#198
**Date:** 2026-07-30
**Status:** Draft

## Problem

`GET /preferences/schema` returns the full schema on every call with no caching support. UI editors that fetch schema on load have no way to avoid redundant transfers when the schema hasn't changed. Schema changes only happen at deployment time (domain modules register keys via `@Observes StartupEvent`), so the response is static after startup.

## Decision

Add a monotonic version counter to the `PreferenceSchemaRegistry` SPI and use it as an HTTP ETag on the schema endpoint. Clients send `If-None-Match` to get a 304 when nothing has changed.

## Design

### SPI Change — `PreferenceSchemaRegistry.version()`

Add a default method to the existing SPI interface:

```java
default long version() { return 0L; }
```

This follows the `spi-evolution-default-methods` protocol: existing implementations (including `NoOpPreferenceSchemaRegistry @DefaultBean`) inherit the safe no-op default and do not break.

### `InMemoryPreferenceSchemaRegistry` — Version Counter

Add an `AtomicLong` field. Increment on every `register()` call — including overwrites of existing keys. The counter starts at 0; after N registrations, `version()` returns N.

### `PreferenceSchemaResource` — Conditional GET

Change the return type from `List<PreferenceSchemaDescriptor>` to `jakarta.ws.rs.core.Response`. Logic:

1. Build `EntityTag` from `String.valueOf(registry.version())`
2. Call `request.evaluatePreconditions(etag)` — returns a `ResponseBuilder` for 304 if `If-None-Match` matches
3. If preconditions pass, return full list with `ETag` header

One ETag for the entire schema regardless of `namespace` query parameter — the schema changes as a whole on deployment, per-namespace granularity is not needed at this stage.

### `NoOpPreferenceSchemaRegistry`

No change. Inherits `default long version()` returning 0.

## Scope Boundaries

**In scope:**
- `PreferenceSchemaRegistry.version()` default method in platform-api
- `AtomicLong` counter in `InMemoryPreferenceSchemaRegistry`
- ETag + conditional GET in `PreferenceSchemaResource`
- Unit tests for version counter
- Integration tests for ETag/304 behavior

**Out of scope:**
- Per-namespace ETags (deferred — can be added later if needed)
- `Last-Modified` header (ETag is stronger and sufficient)
- Explicit version field in the JSON response body (ETag header covers the UI use case)
- Schema change notification via CDI events or SSE

## Test Plan

### Unit Tests — `InMemoryPreferenceSchemaRegistryTest` (new)

| Test | Assertion |
|------|-----------|
| `version_starts_at_zero` | `version()` returns 0 before any registration |
| `version_increments_on_register` | `version()` returns 1 after first register, 2 after second |
| `version_increments_on_overwrite` | re-registering same qualifiedName still increments version |

### Integration Tests — `PreferenceSchemaResourceTest` (extend existing)

| Test | Assertion |
|------|-----------|
| `get_schema_returns_etag_header` | 200 response includes `ETag` header |
| `get_schema_with_matching_if_none_match_returns_304` | `If-None-Match` with current ETag → 304, no body |
| `get_schema_with_stale_if_none_match_returns_200` | `If-None-Match` with wrong value → 200 with body and new ETag |
| `get_schema_without_if_none_match_returns_200` | no header → 200 with ETag (backward compatible) |
| `etag_is_same_regardless_of_namespace_filter` | ETag value identical with and without `?namespace=` |

### NoOp Coverage

`NoOpPreferenceSchemaRegistry.version()` returns 0 — verified by existing `MockBeansTest` or a trivial assertion added there.

## Files Changed

| File | Change |
|------|--------|
| `platform-api/.../PreferenceSchemaRegistry.java` | Add `default long version()` |
| `preferences-editor/.../InMemoryPreferenceSchemaRegistry.java` | Add `AtomicLong`, increment in `register()`, override `version()` |
| `preferences-editor/.../PreferenceSchemaResource.java` | Return `Response`, add ETag + conditional GET logic |
| `preferences-editor/.../ InMemoryPreferenceSchemaRegistryTest.java` | New — unit tests for version counter |
| `preferences-editor/.../PreferenceSchemaResourceTest.java` | Extend — ETag/304 integration tests |
