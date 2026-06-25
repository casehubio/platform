# CredentialResolver Bridge to Quarkus CredentialsProvider

**Issue:** casehubio/platform#116
**Date:** 2026-06-25
**Status:** Approved (revised after review)

## Summary

New module `credentials-quarkus/` providing `QuarkusCredentialResolver` — an
`@Alternative @Priority(1)` bridge that delegates `CredentialResolver.resolve()`
to the Quarkus `CredentialsProvider.getCredentials()` API. Displaces the
`DefaultCredentialResolver` @DefaultBean when on the classpath, enabling Vault,
AWS Secrets Manager, GCP Secret Manager, and any other Quarkus credential
extension without application code changes.

## Module

- **Directory:** `credentials-quarkus/`
- **Artifact:** `casehub-platform-credentials-quarkus`
- **Dependencies:**
  - `io.casehub:casehub-platform-api` (compile scope)
  - `io.quarkus:quarkus-credentials` (compile scope)
- **Parent POM:** new `<module>credentials-quarkus</module>` entry

## Class: QuarkusCredentialResolver

- **Package:** `io.casehub.platform.credentials.quarkus`
- **Annotations:** `@Alternative @Priority(1) @ApplicationScoped`
- **Injection:** `@Inject @Any Instance<CredentialsProvider>`

### Provider resolution

Quarkus CredentialsProvider beans use `@Named` qualifiers (per the Quarkus
credential provider guide). `@Named` beans do not carry `@Default` in CDI,
so direct `@Inject CredentialsProvider` would fail with an unsatisfied
dependency for `@Named`-only providers. The bridge uses
`@Any Instance<CredentialsProvider>` with `@PostConstruct` validation:

- `isUnsatisfied()` → startup error: "No CredentialsProvider found — add a
  Quarkus credential extension (vault, aws-secrets-manager, etc.) or remove
  credentials-quarkus/ from the classpath."
- `isResolvable() && !isAmbiguous()` → single provider (named or unnamed),
  store as `delegate` field
- `isAmbiguous()` → startup error: "Multiple CredentialsProvider beans found.
  This bridge requires exactly one."

Pattern reference: `io.quarkiverse.flow.providers.CredentialsProviderSecretManager`
(quarkus-flow 0.6.0), adapted to fail-fast for zero/multiple providers since
multi-provider routing is out of scope.

### resolve(String credentialRef)

1. Null or blank `credentialRef` → return `Map.of()`
2. Delegate to `delegate.getCredentials(credentialRef)`
3. Null or empty return from provider → return `Map.of()`
4. Otherwise return `Map.copyOf(result)` — defensive copy consistent with
   `DefaultCredentialResolver` and the SPI's implicit immutability contract

### Key alignment

| CredentialPropertyKeys | Quarkus CredentialsProvider constant | Match |
|---|---|---|
| `USER = "user"` | `USER_PROPERTY_NAME = "user"` | Exact |
| `PASSWORD = "password"` | `PASSWORD_PROPERTY_NAME = "password"` | Exact |
| `EXPIRES_AT = "expires-at"` | `EXPIRATION_TIMESTAMP_PROPERTY_NAME = "expires-at"` | Exact |
| `BEARER_TOKEN = "bearer-token"` | *(none)* | casehub-only |
| `API_KEY = "api-key"` | *(none)* | casehub-only |

Three of five `CredentialPropertyKeys` have exact Quarkus equivalents. The
remaining two (`bearer-token`, `api-key`) are casehub conventions — they pass
through naturally if the secret backend stores fields with those names. No key
translation is needed regardless.

### Error handling

Infrastructure errors from the Quarkus provider (Vault unreachable, auth
failure) propagate as-is. The SPI contract "never throws for missing
credentials" refers to absence, not backend failures.

The bridge relies on the Quarkus convention that providers return null for
missing credentials. Providers that express absence as an exception would
violate the `CredentialResolver` "never throws for missing" contract. All
mainstream Quarkus extensions (Vault, AWS, GCP wrappers) follow the
null-return convention.

### Async variant

Not used. The bridge calls the sync `getCredentials()` only.

The Quarkus `CredentialsProvider` javadoc states "Quarkus extensions MUST
invoke the asynchronous variant." This bridge is not a Quarkus platform
extension — it is a consumer-space CDI bean. The `CredentialResolver` SPI is
blocking by design, and calling the async variant from a blocking context
would just double-schedule onto a worker thread with no benefit.

Every mainstream Quarkus CredentialsProvider implements the sync path; the
async default wraps it automatically. If a future provider only implements
async, that signals the need for a reactive `CredentialResolver` SPI variant —
not a fallback hack in the bridge.

## Testing

`@QuarkusTest` with a test `@ApplicationScoped` `CredentialsProvider` bean
(the only provider on the test classpath, so `Instance` resolves it directly).
Four cases:

1. **Null/blank ref** → empty map
2. **Known ref** → mock returns map with user/password, verify pass-through
   and immutability (`Map.copyOf`)
3. **Unknown ref** → mock returns null, verify empty map
4. **Empty map ref** → mock returns empty map, verify empty map

No integration test against a real secret backend — that belongs in consumers.

## Out of scope

- Named provider selection (credentialRef is a credential name within a
  provider, not a provider selector)
- Reactive CredentialResolver SPI variant
- Key translation or enrichment beyond pass-through
