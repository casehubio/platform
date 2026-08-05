# io.casehub.platform.notification.settings.NoOpSuppressionStore

**Package:** `io.casehub.platform.notification.settings`

**Kind:** `class`

No-op `SuppressionStore` — active when no backend module is on the classpath.

<p>`.addMute(MuteRuleInput)` returns a structurally valid `MuteRule`
(UUID v7 id, current timestamp) so callers that use the return value get valid data.
String) returns empty list. String, String)
returns false.

<p>`.activateSnooze(SnoozeInput)` returns a structurally valid `Snooze`
(current timestamp). String) returns empty.
String) returns false.

<p>Does NOT fire CDI events per protocol — no-op implementations must not fire events.

<p>Displaced by any `@Alternative` or bare `@ApplicationScoped`
`SuppressionStore` implementation on the classpath, per the
`@DefaultBean` CDI displacement contract.

## Constructors

### `public NoOpSuppressionStore()`

## Methods

### `public Snooze activateSnooze(SnoozeInput input)`

#### Parameters

- `input` (`SnoozeInput`)

### `public java.util.List<MuteRule> activeMutes(java.lang.String userId, java.lang.String tenancyId)`

#### Parameters

- `userId` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)

### `public java.util.Optional<Snooze> activeSnooze(java.lang.String userId, java.lang.String tenancyId)`

#### Parameters

- `userId` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)

### `public MuteRule addMute(MuteRuleInput input)`

#### Parameters

- `input` (`MuteRuleInput`)

### `public boolean cancelSnooze(java.lang.String userId, java.lang.String tenancyId)`

#### Parameters

- `userId` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)

### `public boolean removeMute(java.lang.String muteId, java.lang.String userId, java.lang.String tenancyId)`

#### Parameters

- `muteId` (`java.lang.String`)
- `userId` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)
