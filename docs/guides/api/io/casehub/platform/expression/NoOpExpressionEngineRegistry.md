# io.casehub.platform.expression.NoOpExpressionEngineRegistry

**Package:** `io.casehub.platform.expression`

**Kind:** `class`

## Fields

### `MESSAGE` (`java.lang.String`)

## Constructors

### `public NoOpExpressionEngineRegistry()`

## Methods

### `public CompiledExpression<C,R> compile(java.lang.String type, java.lang.String expression, java.lang.Class<C> contextType, java.lang.Class<R> resultType)`

#### Parameters

- `type` (`java.lang.String`)
- `expression` (`java.lang.String`)
- `contextType` (`java.lang.Class<C>`)
- `resultType` (`java.lang.Class<R>`)

### `public CompiledExpression<C,R> compile(java.lang.String type, java.lang.String expression, java.lang.Class<C> contextType, java.lang.Class<R> resultType, java.util.Map<java.lang.String,java.lang.Object> variables)`

#### Parameters

- `type` (`java.lang.String`)
- `expression` (`java.lang.String`)
- `contextType` (`java.lang.Class<C>`)
- `resultType` (`java.lang.Class<R>`)
- `variables` (`java.util.Map<java.lang.String,java.lang.Object>`)

### `public void register(ExpressionEngine engine)`

#### Parameters

- `engine` (`ExpressionEngine`)

### `public java.util.Optional<ExpressionEngine> resolve(java.lang.String type)`

#### Parameters

- `type` (`java.lang.String`)

### `public void validate(java.lang.String type, java.lang.String expression)`

#### Parameters

- `type` (`java.lang.String`)
- `expression` (`java.lang.String`)
