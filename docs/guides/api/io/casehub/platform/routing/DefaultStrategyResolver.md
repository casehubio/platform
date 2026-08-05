# io.casehub.platform.routing.DefaultStrategyResolver

**Package:** `io.casehub.platform.routing`

**Kind:** `class`

## Fields

### `defaults` (`java.util.Map<java.lang.Class<?>,NamedStrategy>`)

### `index` (`java.util.Map<java.lang.Class<?>,java.util.Map<java.lang.String,NamedStrategy>>`)

## Constructors

### `public DefaultStrategyResolver(Instance<NamedStrategy> strategies)`

#### Parameters

- `strategies` (`Instance<NamedStrategy>`)

## Methods

### `public java.util.List<T> available(java.lang.Class<T> type)`

#### Parameters

- `type` (`java.lang.Class<T>`)

### `public T defaultStrategy(java.lang.Class<T> type)`

#### Parameters

- `type` (`java.lang.Class<T>`)

### `public java.util.Optional<T> find(java.lang.Class<T> type, java.lang.String id)`

#### Parameters

- `type` (`java.lang.Class<T>`)
- `id` (`java.lang.String`)

### `public T resolve(java.lang.Class<T> type, java.lang.String id)`

#### Parameters

- `type` (`java.lang.Class<T>`)
- `id` (`java.lang.String`)

### `private static java.util.Set<java.lang.Class<?>> resolveStrategyTypes(java.lang.Class<?> clazz)`

#### Parameters

- `clazz` (`java.lang.Class<?>`)
