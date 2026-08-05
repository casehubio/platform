# io.casehub.platform.delivery.NoOpDeliveryChannelRegistry

**Package:** `io.casehub.platform.delivery`

**Kind:** `class`

No-op `DeliveryChannelRegistry` — active when no backend module is on the classpath.

<p>All queries return empty. NotificationDeliverer)
is a silent no-op. Does NOT fire CDI events per protocol — no-op implementations must not
fire events.

<p>Displaced by any `@Alternative` or bare `@ApplicationScoped`
`DeliveryChannelRegistry` implementation on the classpath, per the
`@DefaultBean` CDI displacement contract.

## Constructors

### `public NoOpDeliveryChannelRegistry()`

## Methods

### `public java.util.Set<DeliveryChannelDescriptor> discover()`

### `public void register(DeliveryChannelDescriptor descriptor, NotificationDeliverer deliverer)`

#### Parameters

- `descriptor` (`DeliveryChannelDescriptor`)
- `deliverer` (`NotificationDeliverer`)

### `public java.util.Optional<DeliveryChannelDescriptor> resolve(java.lang.String channelId)`

#### Parameters

- `channelId` (`java.lang.String`)

### `public java.util.Optional<NotificationDeliverer> resolveDeliverer(java.lang.String channelId)`

#### Parameters

- `channelId` (`java.lang.String`)
