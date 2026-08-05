# io.casehub.platform.delivery.NoOpDigestBuffer

**Package:** `io.casehub.platform.delivery`

**Kind:** `class`

## Constructors

### `public NoOpDigestBuffer()`

## Methods

### `public void add(DigestBufferKey key, NotificationInput notification)`

#### Parameters

- `key` (`DigestBufferKey`)
- `notification` (`NotificationInput`)

### `public java.util.List<NotificationInput> drain(DigestBufferKey key)`

#### Parameters

- `key` (`DigestBufferKey`)

### `public java.util.Optional<java.time.Instant> oldestPendingTimestamp(DigestBufferKey key)`

#### Parameters

- `key` (`DigestBufferKey`)

### `public int pendingCount(DigestBufferKey key)`

#### Parameters

- `key` (`DigestBufferKey`)

### `public java.util.Set<DigestBufferKey> pendingKeys()`

### `public java.util.Set<DigestBufferKey> pendingKeysForUser(java.lang.String userId, java.lang.String tenancyId)`

#### Parameters

- `userId` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)
