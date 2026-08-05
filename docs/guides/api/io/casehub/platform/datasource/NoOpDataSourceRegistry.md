# io.casehub.platform.datasource.NoOpDataSourceRegistry

**Package:** `io.casehub.platform.datasource`

**Kind:** `class`

No-op `DataSourceRegistry` — active when no backend module is on the classpath.

<p>`.register(DataSourceDescriptor)` returns a stub `DataSource` that accepts
`add()` calls (silently dropped) and `subscribe()` calls (returns inert handle).
String) and String) always return empty.
`.discover(DataSourceQuery)` always returns an empty list.
String) is a silent no-op.

<p>Displaced by any `@Alternative` or bare `@ApplicationScoped`
`DataSourceRegistry` implementation on the classpath, per the
`@DefaultBean` CDI displacement contract.

## Constructors

### `public NoOpDataSourceRegistry()`

## Methods

### `public void deregister(Path path, java.lang.String tenancyId)`

#### Parameters

- `path` (`Path`)
- `tenancyId` (`java.lang.String`)

### `public java.util.List<DataSourceDescriptor> discover(DataSourceQuery query)`

#### Parameters

- `query` (`DataSourceQuery`)

### `public DataSource<?> register(DataSourceDescriptor descriptor)`

#### Parameters

- `descriptor` (`DataSourceDescriptor`)

### `public java.util.Optional<DataSourceDescriptor> resolve(Path path, java.lang.String tenancyId)`

#### Parameters

- `path` (`Path`)
- `tenancyId` (`java.lang.String`)

### `public java.util.Optional<DataSource<?>> resolveSource(Path path, java.lang.String tenancyId)`

#### Parameters

- `path` (`Path`)
- `tenancyId` (`java.lang.String`)

### `public void update(DataSourceDescriptor descriptor)`

#### Parameters

- `descriptor` (`DataSourceDescriptor`)
