# io.casehub.platform.endpoints.NoOpEndpointRegistry

**Package:** `io.casehub.platform.endpoints`

**Kind:** `class`

No-op `EndpointRegistry` — active when no backend module is on the classpath.

<p>`.register(EndpointDescriptor)` and String) are
silent no-ops. String) always returns empty.
`.discover(EndpointQuery)` always returns an empty list.

<p>Displaced by any `@Alternative` or bare `@ApplicationScoped`
`io.casehub.platform.api.endpoints.EndpointRegistry` implementation on the
classpath, per the `@DefaultBean` CDI displacement contract.

## Constructors

### `public NoOpEndpointRegistry()`

## Methods

### `public void deregister(Path path, java.lang.String tenancyId)`

#### Parameters

- `path` (`Path`)
- `tenancyId` (`java.lang.String`)

### `public java.util.List<EndpointDescriptor> discover(EndpointQuery query)`

#### Parameters

- `query` (`EndpointQuery`)

### `public void register(EndpointDescriptor endpoint)`

#### Parameters

- `endpoint` (`EndpointDescriptor`)

### `public java.util.Optional<EndpointDescriptor> resolve(Path path, java.lang.String tenancyId)`

#### Parameters

- `path` (`Path`)
- `tenancyId` (`java.lang.String`)
