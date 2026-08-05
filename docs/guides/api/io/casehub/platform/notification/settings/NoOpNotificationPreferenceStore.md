# io.casehub.platform.notification.settings.NoOpNotificationPreferenceStore

**Package:** `io.casehub.platform.notification.settings`

**Kind:** `class`

No-op `NotificationPreferenceStore` — active when no backend module is on the classpath.

<p>String) returns empty. String, NotificationPreferenceUpdate)
returns a structurally valid `NotificationPreferences` record (current timestamp, empty
channelDefaults, no quiet hours) so callers that use the return value get valid data.

<p>Does NOT fire CDI events per protocol — no-op implementations must not fire events.

<p>Displaced by any `@Alternative` or bare `@ApplicationScoped`
`NotificationPreferenceStore` implementation on the classpath, per the
`@DefaultBean` CDI displacement contract.

## Constructors

### `public NoOpNotificationPreferenceStore()`

## Methods

### `public java.util.Optional<NotificationPreferences> get(java.lang.String userId, java.lang.String tenancyId)`

#### Parameters

- `userId` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)

### `public NotificationPreferences update(java.lang.String userId, java.lang.String tenancyId, NotificationPreferenceUpdate update)`

#### Parameters

- `userId` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)
- `update` (`NotificationPreferenceUpdate`)
