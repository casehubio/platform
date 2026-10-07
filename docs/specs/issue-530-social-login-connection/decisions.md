## D1: Scope — platform-side only

**Choice:** #530 scoped to platform-side SPI and bridge only. Connectors-side observer and ConnectionPlatform usage are follow-up issues in the connectors repo.
**Alternatives:**
- Full cross-repo implementation — builds both platform SPI and connectors consumer. Couples two repos in one issue, harder to review.
**Rationale:** Platform publishes before connectors in the build order. The SPI must exist before connectors can consume it. Clean separation of concerns.
**Trade-offs:** Connectors can't use service connections until follow-up issues land.
**Sources:** Issue #530 body, connectors repo (no ConnectionPlatform SPI exists yet)
**Exploration:** quick
**Status:** captured

## D2: SPI shape — ServiceConnectionProvider as derived view

**Choice:** New `ServiceConnectionProvider` SPI in `platform-api` with `isConnected()`, `getAccessToken()`, `listConnections()`, `disconnect()`, `missingScopes()`. Implementation is a derived view over `OAuthTokenStore` + `ScopeRegistryCore` + `OAuthTokenManagerCore`.
**Alternatives:**
- Event-driven with separate SPIs (ServiceConnectionQuery + ServiceConnectionTokenProvider) — more flexibility but unnecessary complexity; existing CDI events already cover the event-driven case
- Connection registry with persistence — duplicates OAuthTokenStore data, requires sync maintenance
**Rationale:** Connection status is derivable from existing data. No new persistence, no sync issues. Single SPI gives consumers a clean contract.
**Trade-offs:** No connection-specific metadata beyond what OAuthTokenRecord carries. If connections need custom properties later, the SPI would need extension.
**Sources:** OAuthTokenManagerCore (authn-social-core), ScopeRegistryCore (authn-core), OAuthTokenStore SPI (platform-api)
**Exploration:** quick
**Status:** captured

## D3: Scope merge strategy — automatic with opt-out

**Choice:** Automatic upfront scope merging. At login time, query `ScopeRegistry.requiredScopes(provider)` and inject into auth context as `additionalScopes`. Opt-out via config (`casehub.authn.social.merge-service-scopes`, default true).
**Alternatives:**
- Opt-in per provider — requires explicit configuration before scopes are merged. Safer but defeats the "one consent screen" goal by default.
**Rationale:** Matches the issue requirement of "one consent screen, both identity and service access." Incremental consent path exists as fallback for scopes added after initial login.
**Trade-offs:** Users see broader permission requests on first login. Opt-out config provides an escape hatch.
**Sources:** AbstractOAuthAuthenticationProvider.initiate() — already supports additionalScopes hint merging
**Exploration:** quick
**Status:** captured

## D4: Token refresh — transparent in ServiceConnectionProvider

**Choice:** `getAccessToken()` transparently refreshes expired tokens via `OAuthTokenManagerCore.getValidToken()`, which already handles refresh token exchange and store updates.
**Alternatives:**
- Return raw token, let caller handle refresh — pushes OAuth complexity to every consumer
**Rationale:** OAuthTokenManagerCore already implements the refresh flow with error handling and event firing. No reason to duplicate or expose it.
**Trade-offs:** Callers can't distinguish between "token refreshed" and "token was valid" — but they shouldn't need to.
**Sources:** OAuthTokenManagerCore.getValidToken() (authn-social-core)
**Exploration:** quick
**Status:** captured

## D5: Implementation location — authn-core family

**Choice:** `ServiceConnectionProviderCore` POJO in `authn-social-core`. Quarkus `@Produces` in `authn/`. `@DefaultBean` no-op in `platform/`.
**Alternatives:**
- New top-level module family (service-connection/ + service-connection-core/) — cleaner separation but the implementation is a thin wrapper over authn internals, creating an unnecessary module boundary
**Rationale:** Service connection is fundamentally an authn concern — it derives entirely from OAuth tokens and scope registrations. The SPI in platform-api ensures consumers don't couple to authn.
**Trade-offs:** Adds responsibility to the authn module family. Acceptable given the tight conceptual coupling.
**Sources:** authn-core module structure, core module architecture pattern (CLAUDE.md)
**Exploration:** quick
**Status:** captured
