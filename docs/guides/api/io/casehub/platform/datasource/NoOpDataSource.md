# io.casehub.platform.datasource.NoOpDataSourceRegistry.NoOpDataSource

**Package:** `io.casehub.platform.datasource`

**Kind:** `enum`

Stub `DataSource` that accepts all operations and does nothing.

## Enum Constants

### `INSTANCE` (`io.casehub.platform.datasource.NoOpDataSourceRegistry.NoOpDataSource`)

## Constructors

### `private NoOpDataSource()`

## Methods

### `public void add(java.lang.Object value)`

#### Parameters

- `value` (`java.lang.Object`)

### `public SubscriptionHandle subscribe(DataProcessor<? super java.lang.Object> processor)`

#### Parameters

- `processor` (`DataProcessor<? super java.lang.Object>`)

### `public SubscriptionHandle subscribe(ObjectType<U> objectType, DataProcessor<? super U> processor)`

#### Parameters

- `objectType` (`ObjectType<U>`)
- `processor` (`DataProcessor<? super U>`)

### `public SubscriptionHandle subscribe(ObjectType<U> objectType, java.util.function.Predicate<U> filter, DataProcessor<? super U> processor)`

#### Parameters

- `objectType` (`ObjectType<U>`)
- `filter` (`java.util.function.Predicate<U>`)
- `processor` (`DataProcessor<? super U>`)

### `public SubscriptionHandle subscribe(java.lang.Class<U> type, java.util.function.Predicate<U> filter, DataProcessor<? super U> processor)`

#### Parameters

- `type` (`java.lang.Class<U>`)
- `filter` (`java.util.function.Predicate<U>`)
- `processor` (`DataProcessor<? super U>`)

### `public static io.casehub.platform.datasource.NoOpDataSourceRegistry.NoOpDataSource valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.platform.datasource.NoOpDataSourceRegistry.NoOpDataSource[] values()`
