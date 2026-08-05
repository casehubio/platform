# io.casehub.platform.subscription.NoOpEntityWatcherProvider

**Package:** `io.casehub.platform.subscription`

**Kind:** `class`

No-op `EntityWatcherProvider` that always returns empty results.
Warns on each invocation to surface missing implementation.

## Fields

### `LOG` (`Logger`)

## Constructors

### `public NoOpEntityWatcherProvider()`

## Methods

### `public java.util.Set<java.lang.String> watchersOf(java.lang.String entityType, java.lang.String entityId, java.lang.String tenancyId)`

#### Parameters

- `entityType` (`java.lang.String`)
- `entityId` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)
