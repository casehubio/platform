# io.casehub.platform.delivery.NoOpDeliveryAttemptStore

**Package:** `io.casehub.platform.delivery`

**Kind:** `class`

## Constructors

### `public NoOpDeliveryAttemptStore()`

## Methods

### `public java.util.List<DeliveryAttempt> claimRetryable(java.time.Instant now, int batchSize)`

#### Parameters

- `now` (`java.time.Instant`)
- `batchSize` (`int`)

### `public DeliveryAttemptPage find(DeliveryAttemptQuery query)`

#### Parameters

- `query` (`DeliveryAttemptQuery`)

### `public DeliveryAttempt findById(java.lang.String id)`

#### Parameters

- `id` (`java.lang.String`)

### `public DeliveryAttempt findById(java.lang.String id, java.lang.String tenancyId)`

#### Parameters

- `id` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)

### `public java.util.List<DeliveryAttempt> findBySource(java.lang.String sourceId, DeliverySourceType sourceType, java.lang.String tenancyId)`

#### Parameters

- `sourceId` (`java.lang.String`)
- `sourceType` (`DeliverySourceType`)
- `tenancyId` (`java.lang.String`)

### `public java.util.List<EngagementEvent> findEngagementsByAttemptId(java.lang.String attemptId, java.lang.String tenancyId)`

#### Parameters

- `attemptId` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)

### `public java.util.List<EngagementEvent> findEngagementsBySource(java.lang.String sourceId, DeliverySourceType sourceType, java.lang.String tenancyId)`

#### Parameters

- `sourceId` (`java.lang.String`)
- `sourceType` (`DeliverySourceType`)
- `tenancyId` (`java.lang.String`)

### `public void recordEngagement(EngagementEvent event)`

#### Parameters

- `event` (`EngagementEvent`)

### `public void store(DeliveryAttempt attempt)`

#### Parameters

- `attempt` (`DeliveryAttempt`)

### `public void update(DeliveryAttempt attempt)`

#### Parameters

- `attempt` (`DeliveryAttempt`)
