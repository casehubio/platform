# io.casehub.platform.subscription.NoOpEventTypeRegistry

**Package:** `io.casehub.platform.subscription`

**Kind:** `class`

No-op `EventTypeRegistry` — active when no domain bridges are deployed.

<p>All queries return empty. `.register(EventTypeDescriptor)` is a silent no-op.

<p>Displaced by any `@ApplicationScoped` implementation on the classpath,
per the `@DefaultBean` CDI displacement contract.

## Constructors

### `public NoOpEventTypeRegistry()`

## Methods

### `public java.util.Set<EventTypeDescriptor> discover()`

### `public void register(EventTypeDescriptor descriptor)`

#### Parameters

- `descriptor` (`EventTypeDescriptor`)

### `public java.util.Optional<EventTypeDescriptor> resolve(java.lang.String eventType)`

#### Parameters

- `eventType` (`java.lang.String`)
