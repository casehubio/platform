# io.casehub.platform.notification.NoOpNotificationStore

**Package:** `io.casehub.platform.notification`

**Kind:** `class`

No-op `NotificationStore` — active when no backend module is on the classpath.

<p>`.store(NotificationInput)` and `.storeAll(List)` return structurally
valid `Notification` records (UUID v7 id, UNREAD status, current timestamp) so
callers that use the return value get valid data. All queries return empty. All mutations
return empty/zero.

<p>Does NOT fire CDI events per protocol — no-op implementations must not fire events.

<p>Displaced by any `@Alternative` or bare `@ApplicationScoped`
`NotificationStore` implementation on the classpath, per the
`@DefaultBean` CDI displacement contract.

## Constructors

### `public NoOpNotificationStore()`

## Methods

### `public java.util.Optional<Notification> dismiss(java.lang.String id, java.lang.String userId, java.lang.String tenancyId)`

#### Parameters

- `id` (`java.lang.String`)
- `userId` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)

### `public NotificationPage find(NotificationQuery query)`

#### Parameters

- `query` (`NotificationQuery`)

### `public int markAllRead(java.lang.String userId, java.lang.String tenancyId)`

#### Parameters

- `userId` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)

### `public java.util.Optional<Notification> markRead(java.lang.String id, java.lang.String userId, java.lang.String tenancyId)`

#### Parameters

- `id` (`java.lang.String`)
- `userId` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)

### `public Notification store(NotificationInput input)`

#### Parameters

- `input` (`NotificationInput`)

### `public java.util.List<Notification> storeAll(java.util.List<NotificationInput> inputs)`

#### Parameters

- `inputs` (`java.util.List<NotificationInput>`)

### `private Notification toNotification(NotificationInput input)`

Convert `NotificationInput` to a structurally valid `Notification`.
Generates UUID v7 id, sets UNREAD status, captures current timestamp.

#### Parameters

- `input` (`NotificationInput`) — notification input

#### Returns

notification with generated id and timestamps

### `public long unreadCount(java.lang.String userId, java.lang.String tenancyId)`

#### Parameters

- `userId` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)
