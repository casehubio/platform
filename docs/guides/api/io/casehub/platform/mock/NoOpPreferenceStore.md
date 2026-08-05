# io.casehub.platform.mock.NoOpPreferenceStore

**Package:** `io.casehub.platform.mock`

**Kind:** `class`

## Constructors

### `public NoOpPreferenceStore()`

## Methods

### `public void delete(java.lang.String tenancyId, Path scope, java.lang.String namespace, java.lang.String name, java.lang.String subKey)`

#### Parameters

- `tenancyId` (`java.lang.String`)
- `scope` (`Path`)
- `namespace` (`java.lang.String`)
- `name` (`java.lang.String`)
- `subKey` (`java.lang.String`)

### `public void deleteAll(java.lang.String tenancyId, Path scope, java.lang.String namespace)`

#### Parameters

- `tenancyId` (`java.lang.String`)
- `scope` (`Path`)
- `namespace` (`java.lang.String`)

### `public java.util.List<PreferenceRecord> list(PreferenceQuery query)`

#### Parameters

- `query` (`PreferenceQuery`)

### `public void set(java.lang.String tenancyId, Path scope, java.lang.String namespace, java.lang.String name, java.lang.String subKey, java.lang.String value)`

#### Parameters

- `tenancyId` (`java.lang.String`)
- `scope` (`Path`)
- `namespace` (`java.lang.String`)
- `name` (`java.lang.String`)
- `subKey` (`java.lang.String`)
- `value` (`java.lang.String`)
