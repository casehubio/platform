# io.casehub.platform.subscription.NoOpSubscriptionStore

**Package:** `io.casehub.platform.subscription`

**Kind:** `class`

No-op `SubscriptionStore` — active when no backend module is on the classpath.

<p>`.store(SubscriptionInput)` returns a structurally valid `Subscription`
record (UUID v7 id, current timestamps) so callers that use the return value get valid
data. All queries return empty. All mutations return empty/false. `.findAllEnabled()`
returns empty stream.

<p>Does NOT fire CDI events per protocol — no-op implementations must not fire events.

<p>Displaced by any `@Alternative` or bare `@ApplicationScoped`
`SubscriptionStore` implementation on the classpath, per the
`@DefaultBean` CDI displacement contract.

## Constructors

### `public NoOpSubscriptionStore()`

## Methods

### `public boolean delete(java.lang.String id, java.lang.String ownerId, java.lang.String tenancyId)`

#### Parameters

- `id` (`java.lang.String`)
- `ownerId` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)

### `public SubscriptionPage find(SubscriptionQuery query)`

#### Parameters

- `query` (`SubscriptionQuery`)

### `public java.util.stream.Stream<Subscription> findAllEnabled()`

### `public java.util.Optional<Subscription> findById(java.lang.String id, java.lang.String ownerId, java.lang.String tenancyId)`

#### Parameters

- `id` (`java.lang.String`)
- `ownerId` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)

### `public Subscription store(SubscriptionInput input)`

#### Parameters

- `input` (`SubscriptionInput`)

### `private Subscription toSubscription(SubscriptionInput input)`

Convert `SubscriptionInput` to a structurally valid `Subscription`.
Generates UUID v7 id, sets current timestamps.

#### Parameters

- `input` (`SubscriptionInput`) — subscription input

#### Returns

subscription with generated id and timestamps

### `public java.util.Optional<Subscription> update(java.lang.String id, java.lang.String ownerId, java.lang.String tenancyId, SubscriptionUpdate update)`

#### Parameters

- `id` (`java.lang.String`)
- `ownerId` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)
- `update` (`SubscriptionUpdate`)
