# Social Login → Service Connection Bridge

**Issue:** #530 — Link social login to service connection
**Scope:** Platform-side only. Connectors-side observer is a follow-up issue.

## Problem

When a user authenticates via "Login with Google", the platform requests only identity scopes (`openid`, `email`, `profile`). If a downstream module (e.g. connectors) also needs Google API access (Drive, Calendar, etc.), the user must go through a second OAuth consent flow via incremental consent.

This design merges service scopes into the initial login flow — one consent screen covers both identity and service access — and provides an SPI for downstream modules to query connection status and obtain valid access tokens.

## Why a facade SPI

The platform already has `ScopeRegistry`, `OAuthTokenManagerCore`, `OAuthTokenStore`, and `IncrementalConsentHandler` — these primitives can answer every service connection question. The `ServiceConnectionProvider` SPI exists to give cross-repo consumers (connectors, integrations) a single clean contract: inject 1 SPI instead of 3, with no knowledge of how scopes, tokens, and refresh interact. The facade encapsulates composition logic that would otherwise be duplicated in every consumer.

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│ platform-api (.authn package)                           │
│  ServiceConnectionProvider (SPI)                        │
│  ServiceConnection (record)                             │
│  ServiceConnectionStatus (enum)                         │
│  ServiceAccessToken (record)                            │
│  ServiceConnectionException                             │
└──────────────────────┬──────────────────────────────────┘
                       │ implements
┌──────────────────────▼──────────────────────────────────┐
│ authn-social-core                                       │
│  ServiceConnectionProviderCore (POJO)                   │
│    ├── OAuthTokenManagerCore (transparent refresh)      │
│    ├── OAuthTokenStore (token lookup)                   │
│    ├── ScopeRegistry (scope satisfaction check)         │
│    └── ScopeMergingLoginCustomizer (scope injection)    │
└──────────────────────┬──────────────────────────────────┘
                       │ @Produces
┌──────────────────────▼──────────────────────────────────┐
│ authn                                                   │
│  AuthnBeans @Produces ServiceConnectionProviderCore     │
│  AuthnBeans @Produces ScopeMergingLoginCustomizer       │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│ platform (io.casehub.platform.authn)                    │
│  NoOpServiceConnectionProvider (@DefaultBean)           │
└─────────────────────────────────────────────────────────┘
```

## New Types in platform-api

All types in package `io.casehub.platform.api.authn`, consistent with existing authn SPIs (`ScopeRegistry`, `OAuthTokenStore`, `IncrementalConsentHandler`).

### ServiceConnectionProvider SPI

```java
package io.casehub.platform.api.authn;

public interface ServiceConnectionProvider {

    ServiceConnection getConnection(String actorId, String provider, String tenancyId);

    List<ServiceConnection> listConnections(String actorId, String tenancyId);

    ServiceAccessToken getAccessToken(String actorId, String provider, String tenancyId);

    default void disconnect(String actorId, String provider, String tenancyId) {
        throw new UnsupportedOperationException("disconnect not supported");
    }

    Set<String> missingScopes(String actorId, String provider, String tenancyId);
}
```

- `getConnection()` returns a `ServiceConnection` with status — never null. Returns `DISCONNECTED` status when no token exists.
- `getAccessToken()` transparently refreshes expired tokens. Returns `ServiceAccessToken` with token, expiry, and granted scopes. Throws `ServiceConnectionException` if no connection exists or refresh fails — exception carries `provider`, `actorId`, `requiredScopes`, `grantedScopes`, `missingScopes` for actionable error handling.
- `disconnect()` SPI default throws `UnsupportedOperationException`. Concrete implementations delegate to `OAuthTokenManagerCore.revoke()` (not `OAuthTokenStore.delete()` directly) to ensure future server-side revocation logic is honoured.
- `listConnections()` enumerates all providers with either a stored token OR registered scopes in `ScopeRegistry`. Providers with registered scopes but no token appear as `DISCONNECTED` entries — enables connection management UIs to show "Connect Google" prompts.
- `missingScopes()` returns scopes registered in `ScopeRegistry` but not yet granted — empty set means fully connected.

### ServiceAccessToken record

```java
package io.casehub.platform.api.authn;

public record ServiceAccessToken(
    String accessToken,
    Instant expiresAt,
    Set<String> grantedScopes
) {}
```

Returned by `getAccessToken()` — preserves token metadata that consumers need for caching and preemptive refresh scheduling.

### ServiceConnection record

```java
package io.casehub.platform.api.authn;

public record ServiceConnection(
    String actorId,
    String provider,
    String tenancyId,
    ServiceConnectionStatus status,
    Set<String> grantedScopes,
    Set<String> missingScopes,
    Instant connectedAt
) {}
```

### ServiceConnectionStatus enum

```java
package io.casehub.platform.api.authn;

public enum ServiceConnectionStatus {
    CONNECTED,
    PARTIAL,
    DISCONNECTED
}
```

- `CONNECTED` — token exists and all registered scopes are satisfied
- `PARTIAL` — token exists but some registered scopes are missing (incremental consent needed)
- `DISCONNECTED` — no token for this provider

### ServiceConnectionException

```java
package io.casehub.platform.api.authn;

public class ServiceConnectionException extends RuntimeException {
    private final String provider;
    private final String actorId;
    private final Set<String> requiredScopes;
    private final Set<String> grantedScopes;
    private final Set<String> missingScopes;
    // constructor, getters
}
```

Thrown by `getAccessToken()` when connection doesn't exist or token refresh fails. Carries scope context so consumers can decide whether to trigger incremental consent.

## Implementation in authn-social-core

### ServiceConnectionProviderCore

```java
package io.casehub.platform.authn.social;

public class ServiceConnectionProviderCore implements ServiceConnectionProvider {

    private final OAuthTokenStore tokenStore;
    private final OAuthTokenManagerCore tokenManager;
    private final ScopeRegistry scopeRegistry;

    // Constructor injection — depends on ScopeRegistry SPI, not ScopeRegistryCore

    @Override
    public ServiceConnection getConnection(String actorId, String provider, String tenancyId) {
        var record = tokenStore.findByActorId(actorId, provider, tenancyId);
        if (record.isEmpty()) {
            return new ServiceConnection(actorId, provider, tenancyId,
                DISCONNECTED, Set.of(), scopeRegistry.requiredScopes(provider), null);
        }
        var token = record.get();
        var missing = scopeRegistry.missingScopes(provider, token.grantedScopes());
        var status = missing.isEmpty() ? CONNECTED : PARTIAL;
        return new ServiceConnection(actorId, provider, tenancyId,
            status, token.grantedScopes(), missing, token.createdAt());
    }

    @Override
    public ServiceAccessToken getAccessToken(String actorId, String provider, String tenancyId) {
        var token = tokenManager.getValidToken(actorId, provider, tenancyId);
        if (token.isEmpty()) {
            var required = scopeRegistry.requiredScopes(provider);
            throw new ServiceConnectionException("No connection for provider: " + provider,
                provider, actorId, required, Set.of(), required);
        }
        var record = token.get();
        return new ServiceAccessToken(record.accessToken(), record.expiresAt(),
            record.grantedScopes());
    }

    @Override
    public List<ServiceConnection> listConnections(String actorId, String tenancyId) {
        var tokenRecords = tokenStore.findAllByActorId(actorId, tenancyId);
        var providersWithTokens = tokenRecords.stream()
            .map(OAuthTokenRecord::provider)
            .collect(Collectors.toSet());

        var connections = new ArrayList<ServiceConnection>();

        // Providers with tokens
        for (var record : tokenRecords) {
            connections.add(getConnection(actorId, record.provider(), tenancyId));
        }

        // Providers with registered scopes but no token
        for (var provider : scopeRegistry.registeredProviders()) {
            if (!providersWithTokens.contains(provider)) {
                connections.add(getConnection(actorId, provider, tenancyId));
            }
        }

        return List.copyOf(connections);
    }

    @Override
    public void disconnect(String actorId, String provider, String tenancyId) {
        tokenManager.revoke(actorId, provider, tenancyId);
    }

    @Override
    public Set<String> missingScopes(String actorId, String provider, String tenancyId) {
        return getConnection(actorId, provider, tenancyId).missingScopes();
    }
}
```

**Note:** `listConnections()` uses `ScopeRegistry.registeredProviders()` — a new method returning the set of provider keys with at least one scope registration. This is a small addition to the existing SPI.

### ScopeMergingLoginCustomizer

Intercepts social login initiation to merge service scopes into the authorization URL. Called by `AuthenticationRouterCore` before delegating to the provider — this avoids modifying `AbstractOAuthAuthenticationProvider`'s constructor or its three subclasses.

```java
package io.casehub.platform.authn.social;

public class ScopeMergingLoginCustomizer {

    private final ScopeRegistry scopeRegistry;
    private final boolean enabled;

    // Constructor: scopeRegistry SPI + enabled flag

    public AuthenticationContext customize(AuthenticationContext context, String provider) {
        if (!enabled) return context;
        var serviceScopes = scopeRegistry.requiredScopes(provider);
        if (serviceScopes.isEmpty()) return context;
        var hints = new HashMap<>(context.hints());
        hints.put("additionalScopes", serviceScopes);
        return new AuthenticationContext(context.method(), context.tenancyId(),
            context.origin(), context.existingPrincipal(), hints);
    }
}
```

**Integration point:** `AuthenticationRouterCore.initiate()` calls `customizer.customize(context, provider)` before delegating to the provider's `initiate()`. The modified context carries the merged scopes via the `additionalScopes` hint, which `AbstractOAuthAuthenticationProvider.initiate()` already reads and merges.

### Refresh token guarantee: access_type=offline

For service connections to survive beyond the initial session, Google must issue a refresh token. This requires `access_type=offline` in the authorization URL. Without it, the access token expires after ~1 hour and `OAuthTokenManagerCore.getValidToken()` cannot refresh.

**Change to `AbstractOAuthAuthenticationProvider`:** When `additionalScopes` are present in the auth context (indicating service scopes were merged), add `access_type=offline` to the authorization URL parameters. This is provider-specific:

- **Google:** requires `access_type=offline` (and `prompt=consent` for re-authorization)
- **GitHub:** tokens don't expire, no refresh needed
- **Apple:** has its own refresh mechanism via `grant_type=refresh_token`

Implementation: Add a `Map<String, String> additionalParams()` method to `OAuthConfig` (default empty). `GoogleAuthenticationProvider` overrides to return `Map.of("access_type", "offline")` when service scopes are requested. `buildAuthorizationUrl()` appends these params.

## DefaultBean in platform/

```java
package io.casehub.platform.authn;

public class NoOpServiceConnectionProvider implements ServiceConnectionProvider {

    @Override
    public ServiceConnection getConnection(String actorId, String provider, String tenancyId) {
        return new ServiceConnection(actorId, provider, tenancyId,
            DISCONNECTED, Set.of(), Set.of(), null);
    }

    @Override
    public List<ServiceConnection> listConnections(String actorId, String tenancyId) {
        return List.of();
    }

    @Override
    public ServiceAccessToken getAccessToken(String actorId, String provider, String tenancyId) {
        throw new ServiceConnectionException("No service connection provider configured",
            provider, actorId, Set.of(), Set.of(), Set.of());
    }

    @Override
    public void disconnect(String actorId, String provider, String tenancyId) {
        // no-op — overrides SPI default throw
    }

    @Override
    public Set<String> missingScopes(String actorId, String provider, String tenancyId) {
        return Set.of();
    }
}
```

Quarkus `@Produces @DefaultBean` in `platform/`. Spring `@Bean @ConditionalOnMissingBean` in `platform-spring/`.

## Quarkus Wiring (authn/)

Add to `AuthnBeans`:

```java
@Produces
@ApplicationScoped
ServiceConnectionProviderCore serviceConnectionProvider(
        OAuthTokenStore tokenStore,
        OAuthTokenManagerCore tokenManager,
        ScopeRegistry scopeRegistry) {
    return new ServiceConnectionProviderCore(tokenStore, tokenManager, scopeRegistry);
}

@Produces
@ApplicationScoped
ScopeMergingLoginCustomizer scopeMergingLoginCustomizer(
        ScopeRegistry scopeRegistry,
        AuthnConfig config) {
    return new ScopeMergingLoginCustomizer(scopeRegistry,
        config.mergeServiceScopes());
}
```

## Spring Wiring (authn-spring/)

Add to existing `AuthnSpringAutoConfiguration`:

```java
@Bean
@ConditionalOnMissingBean
ServiceConnectionProviderCore serviceConnectionProvider(
        OAuthTokenStore tokenStore,
        OAuthTokenManagerCore tokenManager,
        ScopeRegistry scopeRegistry) {
    return new ServiceConnectionProviderCore(tokenStore, tokenManager, scopeRegistry);
}

@Bean
@ConditionalOnMissingBean
ScopeMergingLoginCustomizer scopeMergingLoginCustomizer(
        ScopeRegistry scopeRegistry,
        AuthnProperties config) {
    return new ScopeMergingLoginCustomizer(scopeRegistry,
        config.isMergeServiceScopes());
}
```

## Configuration

```yaml
casehub:
  authn:
    merge-service-scopes: true  # default — merge all registered service scopes into social login
```

Property at `casehub.authn.merge-service-scopes` level (not nested under `social`, since `social` is a `Map<String, SocialProvider>` in the existing config structure). Added to `AuthnConfig` interface and `AuthnProperties` class.

## Integration Points

### How connectors will use this (follow-up issue)

1. At startup, register required Google scopes via `ScopeRegistry`:
   ```java
   scopeRegistry.register("google",
       Set.of("https://www.googleapis.com/auth/drive.readonly"),
       GoogleDriveConnector.class);
   ```

2. When needing a Google API token:
   ```java
   var token = serviceConnectionProvider.getAccessToken(actorId, "google", tenancyId);
   // token.accessToken() for API calls, token.expiresAt() for cache TTL
   ```

3. Check connection status for UI:
   ```java
   var conn = serviceConnectionProvider.getConnection(actorId, "google", tenancyId);
   if (conn.status() == PARTIAL) {
       var missing = conn.missingScopes();
       // Prompt user for incremental consent
   }
   ```

### Event flow

1. User clicks "Login with Google"
2. `AuthenticationRouterCore` calls `ScopeMergingLoginCustomizer.customize()` — merges service scopes from `ScopeRegistry` into the auth context hints
3. `AbstractOAuthAuthenticationProvider.initiate()` reads `additionalScopes` hint, builds authorization URL with merged scopes + `access_type=offline` (when service scopes present)
4. Google shows one consent screen for identity + service scopes
5. `verify()` exchanges code, stores token with `grantedScopes` including service scopes and refresh token
6. `SocialLoginCompleted` event fires with full `grantedScopes`
7. `ServiceConnectionProvider.getConnection()` now returns `CONNECTED` status

### Partial consent handling

If the user denies some service scopes on the Google consent screen, Google returns only the approved scopes in the token response. The implementation handles this naturally — `grantedScopes` reflects what was actually granted, and `ScopeRegistry.missingScopes()` returns what was denied. `ServiceConnectionProvider.getConnection()` returns `PARTIAL` status. The consumer can then prompt for incremental consent on the missing scopes.

### Incremental consent fallback

If service scopes are registered after a user has already logged in (or merge was disabled):

1. Consumer calls `serviceConnectionProvider.missingScopes()` — returns non-empty set
2. Consumer calls `IncrementalConsentHandler.requestAdditionalScopes()` with missing scopes
3. User sees consent screen for just the additional scopes
4. Token updated with broader `grantedScopes`
5. `ServiceConnectionProvider.getConnection()` now returns `CONNECTED`

## ScopeRegistry SPI Addition

`registeredProviders()` — returns the set of provider keys that have at least one scope registration. Used by `listConnections()` to enumerate providers with registered scopes but no token.

```java
// Added to ScopeRegistry in platform-api
default Set<String> registeredProviders() {
    return Set.of();
}
```

Default returns empty for backwards compatibility. `ScopeRegistryCore` implements by returning the key set of its internal map.

## Testing

- **Unit tests** for `ServiceConnectionProviderCore` — mock `OAuthTokenStore`, `OAuthTokenManagerCore`, `ScopeRegistry`. Cover CONNECTED/PARTIAL/DISCONNECTED states, transparent refresh, disconnect via revoke, listConnections with mixed connected/disconnected providers.
- **Unit tests** for `ScopeMergingLoginCustomizer` — verify scope injection when enabled/disabled, empty registry, correct `AuthenticationContext` reconstruction (canonical constructor, not `withHints`).
- **Unit tests** for `NoOpServiceConnectionProvider` — verify safe defaults, disconnect no-ops, getAccessToken throws.
- **Integration test** in `authn/` — full flow: register scopes → social login with merged scopes → verify `access_type=offline` in authorization URL → verify connection status → get access token with refresh → disconnect via revoke.

## Follow-up Issues (connectors repo)

To be filed during implementation:

1. **ConnectionPlatform SPI** — define the connectors-side abstraction for platform connections (Google Drive, Calendar, etc.)
2. **Google service connection observer** — register Google API scopes at startup, use `ServiceConnectionProvider.getAccessToken()` for API calls
3. **Connection status UI** — surface connection status in the connectors UI, trigger incremental consent for PARTIAL connections

## References

- `authn-social-core/.../AbstractOAuthAuthenticationProvider.java` — scope merging via additionalScopes hint
- `authn-social-core/.../OAuthTokenManagerCore.java` — token refresh via TokenRefreshClient, revoke()
- `authn-core/.../ScopeRegistryCore.java` — multi-consumer scope registration
- `authn-core/.../AuthenticationRouterCore.java` — integration point for ScopeMergingLoginCustomizer
- `platform-api/.../OAuthTokenStore.java` — token persistence SPI
- `platform-api/.../ScopeRegistry.java` — scope registration SPI
- `platform-api/.../InsufficientScopesException.java` — scope context pattern for ServiceConnectionException
- `platform-api/.../AuthenticationEventListener.java` — SocialLoginCompleted event
- `platform/.../NoOpOAuthTokenStore.java` — DefaultBean no-op pattern reference (authn family)
- Issue #525 — parent epic (Identity Authentication)
- Issue #530 — this issue
