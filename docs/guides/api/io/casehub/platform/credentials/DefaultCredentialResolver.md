# io.casehub.platform.credentials.DefaultCredentialResolver

**Package:** `io.casehub.platform.credentials`

**Kind:** `class`

MicroProfile Config-backed credential resolver.

<p>Resolves credentials from the `casehub.credentials.<ref>` config namespace.
Supports two modes:
<ul>
  <li><b>Compound</b> — sub-keys checked first (`casehub.credentials.<ref>.user`,
      `.password`, `.bearer-token`, `.api-key`, `.expires-at`).
      If any sub-key is found, returns the compound map.</li>
  <li><b>Simple</b> — bare key (`casehub.credentials.<ref>`) returned under
      `CredentialPropertyKeys.BEARER_TOKEN`. Used only when no sub-keys match.</li>
</ul>

<p>Compound or simple, never both — if sub-keys exist, the bare key is not consulted.

## Fields

### `config` (`Config`)

## Constructors

### `DefaultCredentialResolver(Config config)`

#### Parameters

- `config` (`Config`)

## Methods

### `private void checkSubKey(java.lang.String prefix, java.lang.String key, java.util.Map<java.lang.String,java.lang.String> target)`

#### Parameters

- `prefix` (`java.lang.String`)
- `key` (`java.lang.String`)
- `target` (`java.util.Map<java.lang.String,java.lang.String>`)

### `public java.util.Map<java.lang.String,java.lang.String> resolve(java.lang.String credentialRef)`

#### Parameters

- `credentialRef` (`java.lang.String`)
