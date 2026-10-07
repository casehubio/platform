# Identity Authentication — Design Spec Outline

## Problem

Current platform has identity *reading* (CurrentPrincipal, OIDC resource-server) but no identity *creation* or authentication flows. No WebAuthn, social login, session management, or pluggable authn strategy infrastructure exists.

## Scope

All five child issues of epic #525: authentication SPI (#526), WebAuthn provider (#527), social login providers (#528), session management (#529), and social-login-to-service-connection linking (#530).

## Authentication SPI (#526)

### AuthenticationProvider Interface

Two-phase ceremony SPI: `initiate(AuthenticationContext) → ChallengeResponse` and `verify(VerificationRequest) → AuthenticationResult`. Each provider declares a `method()` string ("webauthn", "google", "github", "apple"). Router dispatches by method name.

### AuthenticationContext

Input to `initiate()` — carries the requested method, tenancyId, relying party info (origin, rpId), and optional hints (e.g., existing actorId for credential binding during registration).

### ChallengeResponse

Output of `initiate()` — sealed interface with method-specific subtypes. WebAuthn: contains challenge bytes, credential request options. OAuth: contains redirect URL, state parameter, PKCE code verifier. Includes a `challengeId` for correlating with the verify phase.

### VerificationRequest

Input to `verify()` — carries the challengeId plus method-specific verification data. WebAuthn: authenticator assertion response. OAuth: authorization code, state, redirect URI.

### AuthenticationResult

Output of `verify()` — uniform result carrying the resolved PrincipalId, tenancyId, groups, authentication method, and provider-specific metadata (e.g., OAuth scopes granted, credential ID used).

### AuthenticationRouter

Dispatches to the correct AuthenticationProvider by method name. Composite pattern matching agent-router's RoutingAgentProvider. Discovers providers via CDI `Instance<AuthenticationProvider>` / Spring `List<AuthenticationProvider>`.

### ChallengeStore SPI

Stores challenge state between initiate() and verify() — needed because the two phases span separate HTTP requests. Inmem (ConcurrentHashMap with TTL eviction) and JPA backends. Keyed by challengeId, expires after configurable timeout (default 5 minutes).

## WebAuthn Provider (#527)

### WebAuthn Registration Ceremony

Generates registration options (challenge, rpId, rpName, user info from SCIM lookup, attestation preference). Validates attestation response via webauthn4j. Stores the public key credential in WebAuthnCredentialStore.

### WebAuthn Authentication Ceremony

Generates assertion options (challenge, allowCredentials from stored credentials for the user). Validates assertion response via webauthn4j (signature verification, counter check). Returns AuthenticationResult.

### WebAuthnCredentialStore SPI

Stores WebAuthn public key credentials: credentialId, publicKey (COSE), signCount, transports, aaguid, createdAt, lastUsedAt, displayName, actorId, tenancyId. Inmem and JPA backends following the existing pattern.

### Resident Key / Discoverable Credential Support

Support both server-side credentials (allowCredentials list) and client-side discoverable credentials (passkeys). Discoverable credentials enable usernameless login.

### Platform and Roaming Authenticator Support

Support Touch ID, Face ID (platform authenticators) and YubiKey, security keys (roaming authenticators). Attestation conveyance preference configurable.

## Social Login Providers (#528)

### OAuth2/OIDC Code Flow Core

Framework-neutral OAuth2 authorization code flow implementation using java.net.http.HttpClient. Handles: authorization URL construction, PKCE (S256), token exchange, ID token validation (for OIDC providers), userinfo endpoint calls.

### Google Provider

OIDC-compliant. Uses OIDC discovery (`accounts.google.com/.well-known/openid-configuration`). ID token contains email, name, picture. Maps email to existing SCIM identity.

### GitHub Provider

OAuth2 (not OIDC). Custom authorization/token endpoints. Requires separate `/user` API call for profile data. Maps login or email to SCIM identity.

### Apple Provider

OIDC with special handling: client secret is a JWT signed with an Apple-provisioned P-256 key (refreshed every ~5 minutes). ID token contains email (may be relay address). User info only available on first authorization.

### SocialLoginProvider Interface

Extends AuthenticationProvider with provider-specific configuration: clientId, clientSecret (or key for Apple), scopes, endpoints. Each provider is a separate bean/module.

### Identity Resolution

After token exchange, resolve the authenticated external identity (email, external ID) to an existing PrincipalId via SCIM lookup. Fail if no matching SCIM user exists (D11). Fire IdentityLinked event on first successful mapping.

## Session Management (#529)

### Session Lifecycle

Creation after successful AuthenticationResult. Two modes: JWT (API clients) and cookie session (browser clients). Session carries: actorId, tenancyId, groups, authMethod, createdAt, expiresAt, deviceInfo.

### JWT Issuance

Platform-issued JWT using ES256 via platform-signing's KeyStoreManager and TenantKeyStoreResolver. Claims: sub (actorId), tenancyId, groups, iat, exp, jti, iss (platform), auth_method. Short-lived (configurable, default 15 minutes). Paired with a longer-lived refresh token.

### Refresh Token Flow

Opaque refresh tokens stored server-side. Refresh endpoint validates the refresh token, issues a new JWT + rotates the refresh token (rotation prevents replay). Configurable refresh token TTL (default 7 days).

### Cookie Session Store SPI

Server-side session store for browser clients. HTTP-only, Secure, SameSite=Lax cookie carries an opaque session ID. SessionStore SPI with inmem and JPA backends. Session data: actorId, tenancyId, groups, authMethod, createdAt, expiresAt, lastAccessedAt, deviceFingerprint.

### HttpAuthenticationMechanism (Quarkus) / Filter (Spring)

Custom auth mechanism that checks for session cookie, looks up session in SessionStore, creates SecurityIdentity with actorId as principal name, groups as roles, tenancyId as attribute. Follows SecurityIdentityAttributes convention. Returns null transport to avoid OIDC credential transport conflict (per garden entry).

### Session Revocation

Explicit logout (delete session, clear cookie). Admin revocation (delete by actorId or tenancyId). JWT deny-list for revoking issued-but-unexpired JWTs (optional, TTL-bounded to JWT expiry window).

### CSRF Protection

For cookie-based sessions: synchronizer token pattern or double-submit cookie. Not needed for JWT-only API clients.

## Social Login → Service Connection (#530)

### OAuthTokenStore SPI

Stores OAuth access + refresh tokens from social login: provider, actorId, tenancyId, accessToken (encrypted), refreshToken (encrypted), scopes, expiresAt, createdAt. Inmem and JPA backends. Token encryption at rest via CredentialResolver or config-based key.

### SocialLoginCompleted CDI Event

Fired after successful social login authentication. Carries: provider, actorId, tenancyId, granted scopes, tokenStoreReference (not the raw tokens). Connectors repo observes this event to bootstrap ConnectionPlatform entries.

### Scope Elevation

Social login for authentication uses minimal scopes (email, profile). Connection bootstrapping requests additional scopes (e.g., Google Calendar, Drive). Support requesting elevated scopes during the OAuth flow when the deployment is configured to bootstrap connections.

### Token Refresh

Platform manages OAuth token refresh for stored tokens. Refresh runs on access (lazy) or on schedule. Fires TokenRefreshFailed event when refresh fails (expired refresh token, revoked access).

## Module Structure (D8)

### Module Map

List all new modules with artifact IDs, dependency relationships, and what each contains. Follow the -core / Quarkus / -spring / -inmem / -jpa pattern.

Planned modules:
- `authn-api` — SPIs and types in platform-api package (no new Maven module — extends platform-api)
- `authn-core` — Framework-neutral: AuthenticationRouter, ChallengeStore logic, JwtIssuer, SessionManager
- `authn-webauthn-core` — webauthn4j-based registration/authentication ceremony logic
- `authn-social-core` — OAuth2 code flow + provider implementations (Google, GitHub, Apple)
- `session-core` — SessionStore logic, cookie session management
- `session-inmem` — @Alternative InMemorySessionStore + InMemoryChallengeStore
- `session-jpa` — JPA SessionStore + ChallengeStore + Flyway migrations
- `authn-webauthn-inmem` — @Alternative InMemoryWebAuthnCredentialStore
- `authn-webauthn-jpa` — JPA WebAuthnCredentialStore + Flyway
- `authn-social-inmem` — @Alternative InMemoryOAuthTokenStore
- `authn-social-jpa` — JPA OAuthTokenStore + Flyway
- `authn` — Quarkus CDI wiring + REST endpoints (login, register, callback, session)
- `authn-spring` — Spring Boot auto-configuration + REST controllers
- `authn-spring-jpa` — Spring Data JPA stores (session, challenge, webauthn, oauth)

### Dependency Graph

Diagram showing module dependencies. authn-core depends on platform-api. authn-webauthn-core depends on authn-core + webauthn4j. authn-social-core depends on authn-core. session-core depends on authn-core. Quarkus/Spring modules depend on their respective -core modules.

## Integration with Existing Infrastructure

### CurrentPrincipal (D6)

How platform-issued JWTs flow through SecurityIdentityCurrentPrincipal. How cookie sessions create SecurityIdentity via HttpAuthenticationMechanism with attributes.

### Platform-Signing (D10)

How JwtIssuer obtains signing keys from KeyStoreManager. Fallback when platform-signing is not on classpath.

### SCIM (D3, D11)

How identity resolution queries SCIM during registration and first login. ScimAgentLookup reuse or new ScimUserLookup.

### Existing OIDC Module

Co-existence: platform-issued JWTs and external IdP JWTs can both be validated. Configuration for multi-issuer support.

## Security Considerations

Credential storage encryption, PKCE enforcement, state parameter validation, timing-safe comparisons, rate limiting on auth endpoints, brute-force protection, secure cookie attributes, CORS configuration for WebAuthn.

## Testing Strategy

Per-module unit tests. Integration tests with in-memory stores. WebAuthn ceremony tests using webauthn4j's test utilities. Social login tests with mock HTTP server. Session lifecycle tests. Quarkus @QuarkusTest + Spring @SpringBootTest integration.

## Configuration

Config properties for: enabled providers, JWT expiry/refresh TTL, session TTL, WebAuthn rpId/rpName/attestation, OAuth client credentials per provider, cookie settings (name, domain, path, SameSite).

## Documentation Updates

Consumer guide additions: authentication module usage, provider setup, session configuration. Contributor guide: how to add a new authentication provider.

## References

Links to: issue #525 and children, WebAuthn spec, OAuth2/OIDC specs, webauthn4j docs, platform-signing module, existing identity specs, garden entries on HttpAuthenticationMechanism gotchas.
