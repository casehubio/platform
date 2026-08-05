# io.casehub.platform.converter.PathParamConverterProvider

**Package:** `io.casehub.platform.converter`

**Kind:** `class`

JAX-RS `ParamConverterProvider` that registers `PathParamConverter`
for casehub `Path` parameters. Activated automatically via Jandex discovery
when `casehub-platform` is on the classpath.

<p>Allows REST endpoints to declare `@PathParam` and `@QueryParam`
of type `Path` without manual string conversion:
<pre>
  `@`GET `@`Path("/prefs/{scope}")
  public Response getPrefs(`@`PathParam("scope") Path scope) { ... }
</pre>

## Fields

### `INSTANCE` (`io.casehub.platform.converter.PathParamConverter`)

## Constructors

### `public PathParamConverterProvider()`

## Methods

### `public ParamConverter<T> getConverter(java.lang.Class<T> rawType, java.lang.reflect.Type genericType, java.lang.annotation.Annotation[] annotations)`

#### Parameters

- `rawType` (`java.lang.Class<T>`)
- `genericType` (`java.lang.reflect.Type`)
- `annotations` (`java.lang.annotation.Annotation[]`)
