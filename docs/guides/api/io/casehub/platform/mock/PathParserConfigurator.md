# io.casehub.platform.mock.PathParserConfigurator

**Package:** `io.casehub.platform.mock`

**Kind:** `class`

Registers the platform-configured path separator as the default `PathParser`.
Runs at application startup before any beans use `Path.parse(String)`.

<p>Configure via: `casehub.platform.path.separator=/` (default: `/`)

<p>This config is installation-wide and must NOT go through `PreferenceProvider` —
that would create a circular dependency since `SettingsScope` contains a `Path`.

## Fields

### `separator` (`java.lang.String`)

## Constructors

### `public PathParserConfigurator()`

## Methods

### `void configure()`
