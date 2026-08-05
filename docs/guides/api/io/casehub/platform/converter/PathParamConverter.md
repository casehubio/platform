# io.casehub.platform.converter.PathParamConverter

**Package:** `io.casehub.platform.converter`

**Kind:** `class`

JAX-RS `ParamConverter` for casehub `Path`.
Converts between string representations and `Path` instances using
the platform-configured default parser (`Path.parse(String)`).

<p>Registered automatically via `PathParamConverterProvider`.

## Constructors

### `public PathParamConverter()`

## Methods

### `public Path fromString(java.lang.String value)`

#### Parameters

- `value` (`java.lang.String`)

### `public java.lang.String toString(Path value)`

#### Parameters

- `value` (`Path`)
