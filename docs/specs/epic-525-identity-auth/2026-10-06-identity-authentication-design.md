# Identity Authentication — Design Spec

**Epic:** casehubio/platform#525
**Date:** 2026-10-06
**Status:** Draft

## Problem

The platform has identity *reading* — `CurrentPrincipal` extracts actorId, groups,
and tenancyId from an externally-issued JWT via `SecurityIdentityCurrentPrincipal`
(Quarkus) or `SpringSecurityCurrentPrincipal` (Spring). But there is no identity
*creation*: no pluggable authentication strategy framework, no WebAuthn/passkey
support, no social login flows, no session management.

Applications that need user-facing login today must implement authentication
end-to-end with no platform support. Every consumer re-invents WebAuthn ceremonies,
OAuth2 code exchange, session stores, and JWT issuance.

## Scope

All five child issues of epic #525:

| Issue | Title | Scale | Complexity |
|-------|-------|-------|------------|
| #526 | Identity authentication SPI — pluggable authn strategies | M | Med |
| #527 | WebAuthn/passkey authentication provider | M | High |
| #528 | Social login providers — Login with Google/GitHub/Apple | M | Med |
| #529 | Session management | M | Med |
| #530 | Link social login to service connection | S | High |

---

## Authentication SPI (#526)

### AuthenticationProvider

Two-phase ceremony SPI (D9). Each provider handles one authentication method.
Discovered by the router via CDI `Instance<AuthenticationProvider>` or Spring
`List<AuthenticationProvider>`.

```java
package io.casehub.platform.api.authn;

import java.util.Map;

public interface AuthenticationProvider {

    String method();

    ChallengeResponse initiate(AuthenticationContext context);

    AuthenticationResult verify(ChallengeRecord challenge, Map<String, Object> data);
}
```

The `method()` string identifies the provider: `"webauthn"`, `"google"`,
`"github"`, `"apple"`. The router dispatches by matching the requested method
to a provider's `method()` value.

`verify()` receives the consumed `ChallengeRecord` (owned and validated by
the router — see §AuthenticationRouter) plus the raw verification data map
from the REST layer. The provider extracts its own typed fields from the map
and performs method-specific validation (signature check, token exchange, etc.).

### AuthenticationContext

Input to `initiate()`. Carries the requested method, tenant context, request
origin, and optional identity hints for credential binding during registration.

```java
package io.casehub.platform.api.authn;

import io.casehub.platform.api.identity.PrincipalId;
import java.util.Map;
import java.util.Optional;

public record AuthenticationContext(
    String method,
    String tenancyId,
    String origin,
    Optional<PrincipalId> existingPrincipal,
    Map<String, Object> hints
) {
    public AuthenticationContext {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(tenancyId, "tenancyId");
        Objects.requireNonNull(origin, "origin");
    }
}
```

`origin` is the HTTP request origin — used for WebAuthn assertion validation,
CORS enforcement, and security audit logging. Always available from the HTTP
layer.

`existingPrincipal` is set during credential registration (binding a new
passkey or social login to a known user). Empty during login.

WebAuthn-specific configuration (`rpId`, `rpName`) is read by the WebAuthn
provider from its own deployment config (`casehub.authn.webauthn.rp-id`,
`casehub.authn.webauthn.rp-name`), not from the authentication context.

### ChallengeResponse

Output of `initiate()`. Unsealed interface — providers define their own
subtypes to carry method-specific challenge data.

```java
package io.casehub.platform.api.authn;

import java.time.Instant;

public interface ChallengeResponse {

    String challengeId();

    Instant expiresAt();
}
```

Method-specific subtypes live in their respective provider modules:

**In `authn-webauthn-core`:**

```java
package io.casehub.platform.authn.webauthn;

public record WebAuthnChallengeResponse(
    String challengeId,
    Instant expiresAt,
    byte[] challenge,
    String rpId,
    String rpName,
    byte[] userId,
    String userName,
    String userDisplayName,
    java.util.List<String> allowCredentialIds,
    String attestation,
    String userVerification
) implements ChallengeResponse {}
```

**In `authn-social-core`:**

```java
package io.casehub.platform.authn.social;

public record OAuthChallengeResponse(
    String challengeId,
    Instant expiresAt,
    String redirectUrl
) implements ChallengeResponse {}
```

**OAuth state = challengeId.** The OAuth `state` parameter in the
authorization URL is set to the `challengeId` (UUID from `SecureRandom`).
When the identity provider redirects back with `?state=<uuid>&code=<code>`,
the client extracts the `state` value and passes it as `challengeId` in
the `POST /auth/verify` request. No client-side storage needed — the
challengeId round-trips through the OAuth state parameter.

The UUID satisfies the OAuth 2.0 CSRF requirement (RFC 6749 §10.12) —
it is unpredictable and bound to the user-agent's session via the
challenge store.

The `nonce` (OIDC ID token replay protection) is stored in
`ChallengeRecord.challengeData` as provider-owned opaque state. The
provider validates it during `verify()` when checking the ID token.
The client never sees or handles the nonce.

### Verification Data Convention

Providers receive verification data as `Map<String, Object>` — the raw
JSON body from the REST layer. Each provider extracts its own typed fields.
The SPI contract does not prescribe the map keys; each provider documents
its expected keys in its own module.

**WebAuthn expected keys** (in `authn-webauthn-core`):

| Key | Type | Description |
|-----|------|-------------|
| `credentialId` | `String` | Base64url-encoded credential ID |
| `authenticatorData` | `String` | Base64url-encoded authenticator data |
| `clientDataJSON` | `String` | Base64url-encoded client data JSON |
| `signature` | `String` | Base64url-encoded signature |
| `userHandle` | `String` | Base64url-encoded user handle (optional) |

**OAuth expected keys** (in `authn-social-core`):

| Key | Type | Description |
|-----|------|-------------|
| `code` | `String` | Authorization code from OAuth redirect |
| `state` | `String` | OAuth state parameter for CSRF validation |
| `redirectUri` | `String` | Redirect URI used in the authorization request |

Providers validate required keys and throw `InvalidCredentialException`
with a descriptive message for missing or malformed data. Binary data
(WebAuthn `byte[]` fields) is conveyed as base64url strings in the map
and decoded by the provider.

### AuthenticationResult

Uniform output of `verify()`. Carries the resolved identity and metadata
needed for session creation.

```java
package io.casehub.platform.api.authn;

import io.casehub.platform.api.identity.PrincipalId;
import java.util.Map;
import java.util.Set;

public record AuthenticationResult(
    PrincipalId principal,
    String tenancyId,
    Set<String> groups,
    String method,
    Map<String, Object> metadata
) {
    public AuthenticationResult {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(tenancyId, "tenancyId");
        Objects.requireNonNull(method, "method");
        groups = groups != null ? Set.copyOf(groups) : Set.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }
}
```

Metadata carries method-specific data: OAuth scopes granted, WebAuthn
credential ID used, authenticator type. Consumers read metadata by key —
the platform does not interpret it.

### AuthenticationRouter

Dispatches to the correct `AuthenticationProvider` by method name.
Owns challenge lifecycle — the router consumes, validates, and dispatches.

```java
package io.casehub.platform.api.authn;

import java.util.Map;

public interface AuthenticationRouter {

    ChallengeResponse initiate(AuthenticationContext context);

    AuthenticationResult verify(String method, String challengeId,
                                Map<String, Object> data);

    java.util.Set<String> availableMethods();
}
```

The router implementation (in `authn-core`) discovers providers via
constructor-injected `List<AuthenticationProvider>`, indexes by `method()`,
and delegates. Unknown methods fail fast with `UnknownMethodException`.

**Verify dispatch flow:**

1. Router receives `(method, challengeId, data)` from REST layer
2. Router calls `ChallengeStore.consume(challengeId)` — atomic retrieve
   and delete
3. Router validates: challenge exists, not expired, `challenge.method()`
   matches `method`
4. Router dispatches `provider.verify(challenge, data)` to the matched
   provider
5. Provider performs method-specific validation and returns
   `AuthenticationResult`

Challenge lifecycle enforcement is centralised in the router — providers
never call `ChallengeStore` during verify. This eliminates the
double-consume problem and ensures every verify path enforces challenge
validation consistently, regardless of provider implementation quality.

### Exception Hierarchy

Authentication failures are typed for proper HTTP status mapping in the
REST layer. Unsealed base class — providers may define additional subtypes.

```java
package io.casehub.platform.api.authn;

public abstract class AuthenticationException extends RuntimeException {

    private final String method;

    protected AuthenticationException(String method, String message) {
        super(message);
        this.method = method;
    }

    public String method() { return method; }
}
```

| Exception | Meaning | HTTP Status |
|-----------|---------|-------------|
| `InvalidChallengeException` | Challenge expired, already consumed, or wrong method | 400 |
| `InvalidCredentialException` | Bad signature, wrong code, verification failed | 401 |
| `UnknownIdentityException` | No SCIM user for social login identity | 403 |
| `AccountLockedException` | Rate limit exceeded, account temporarily locked | 429 |
| `ProviderUnavailableException` | OAuth endpoint down, SCIM unreachable | 503 |
| `UnknownMethodException` | No provider registered for requested method | 400 |

All subtypes in `platform-api` (`io.casehub.platform.api.authn` package).

### ChallengeStore SPI

Stores challenge state between `initiate()` and `verify()` — the two phases
span separate HTTP requests.

```java
package io.casehub.platform.api.authn;

import java.util.Optional;

public interface ChallengeStore {

    void store(ChallengeRecord record);

    Optional<ChallengeRecord> consume(String challengeId);
}
```

```java
public record ChallengeRecord(
    String challengeId,
    String method,
    String tenancyId,
    byte[] challengeData,
    java.time.Instant createdAt,
    java.time.Instant expiresAt
) {}
```

`consume()` is atomic: retrieves and deletes in one operation. Prevents
replay. Challenge TTL defaults to 5 minutes (configurable). Inmem
implementation uses `ConcurrentHashMap` with lazy TTL eviction. JPA
implementation uses `SELECT ... FOR UPDATE` with `@Scheduled` purge.

**`challengeId` uniqueness:** Generated as UUID — globally unique, not
merely tenant-unique. `consume(challengeId)` is a global lookup by
design; tenant-scoping is unnecessary given UUID collision probability.

**`challengeData` semantics:** Provider-owned opaque state, serialized by
the provider into `byte[]` and deserialized by the same provider during
`verify()`. The store does not interpret or validate `challengeData`.
- **WebAuthn:** raw 32-byte cryptographic challenge
- **OAuth:** provider-serialized state containing PKCE code verifier,
  nonce, redirect URI, and any provider-specific parameters. Each
  provider defines its own serialization format (e.g., JSON, protobuf).

---

## WebAuthn Provider (#527)

### Registration Ceremony

1. Caller provides `AuthenticationContext` with `existingPrincipal` set
   (the user to bind the passkey to)
2. `WebAuthnAuthenticationProvider.initiate()`:
   - Looks up user via `ScimUserLookup.findByActorId()` for display name
   - Generates random challenge (32 bytes, `SecureRandom`)
   - Builds `PublicKeyCredentialCreationOptions` via webauthn4j
   - Stores challenge in `ChallengeStore`
   - Returns `WebAuthnChallengeResponse` with rpId, rpName, user info,
     excludeCredentials (existing credentials for this user)
3. Browser executes `navigator.credentials.create()` with the options
4. Caller sends attestation response via `verify()`:
   - Router has already consumed and validated the challenge
   - Provider receives `ChallengeRecord` + raw data map
   - Decodes base64url fields from the map (`authenticatorData`, etc.)
   - Validates attestation via webauthn4j `WebAuthnManager.verify()`
     using `challenge.challengeData()` for the expected challenge bytes
   - Stores credential in `WebAuthnCredentialStore`
   - Returns `AuthenticationResult` with the bound principal

### Authentication Ceremony

1. Caller provides `AuthenticationContext` without `existingPrincipal`
   (or with it for usernameless discoverable credentials)
2. `WebAuthnAuthenticationProvider.initiate()`:
   - Generates random challenge
   - If actorId known: builds `allowCredentials` from stored credentials
   - If usernameless: `allowCredentials` is empty (discoverable credential)
   - Stores challenge in `ChallengeStore`
   - Returns `WebAuthnChallengeResponse`
3. Browser executes `navigator.credentials.get()`
4. Caller sends assertion response via `verify()`:
   - Router has already consumed and validated the challenge
   - Provider receives `ChallengeRecord` + raw data map
   - Decodes base64url fields, extracts `credentialId`
   - Looks up credential by `credentialId` from `WebAuthnCredentialStore`
   - Validates assertion via webauthn4j (signature, counter, origin, rpId)
     using `challenge.challengeData()` for the expected challenge bytes
   - Calls `WebAuthnCredentialStore.updateAfterAuthentication()` with
     new sign count and current timestamp
   - Resolves the credential's `actorId` to a `PrincipalId`
   - Returns `AuthenticationResult`

### WebAuthnCredentialStore SPI

```java
package io.casehub.platform.api.authn;

import java.util.List;
import java.util.Optional;

public interface WebAuthnCredentialStore {

    void store(WebAuthnCredential credential);

    Optional<WebAuthnCredential> findByCredentialId(String credentialId);

    List<WebAuthnCredential> findByActorId(String actorId, String tenancyId);

    void updateAfterAuthentication(String credentialId, long newSignCount,
                                   java.time.Instant lastUsedAt);

    void delete(String credentialId);
}
```

```java
public record WebAuthnCredential(
    String credentialId,
    String actorId,
    String tenancyId,
    byte[] publicKeyCose,
    long signCount,
    java.util.Set<String> transports,
    String aaguid,
    String displayName,
    java.time.Instant createdAt,
    java.time.Instant lastUsedAt,
    boolean discoverable
) {}
```

### webauthn4j Version Management

`authn-webauthn-core` depends on webauthn4j directly. The Quarkus BOM
manages webauthn4j versions (currently 0.30.3.RELEASE on Jackson 2).
The platform must use the Quarkus-managed version to avoid classpath
conflicts. The parent POM imports webauthn4j version from the Quarkus
BOM — no explicit version override.

For Spring Boot deployments, spring-security-webauthn also depends on
webauthn4j transitively. The platform's explicit dependency must align
with the Spring BOM version.

### Authenticator Support

Both platform authenticators (Touch ID, Face ID, Windows Hello) and roaming
authenticators (YubiKey, security keys) are supported. The `attestation`
preference is configurable:

- `"none"` (default) — no attestation, maximises compatibility
- `"indirect"` — anonymised attestation
- `"direct"` — full attestation for enterprise deployments

User verification (`"required"` | `"preferred"` | `"discouraged"`) is
configurable per deployment.

---

## Social Login Providers (#528)

### OAuth2/OIDC Code Flow Core

Framework-neutral implementation using nimbus-oauth2-oidc-sdk (D5). Each
social login provider extends `AuthenticationProvider` and configures nimbus
with provider-specific endpoints, scopes, and client authentication.

```java
package io.casehub.platform.authn.social;

import io.casehub.platform.api.authn.AuthenticationProvider;

public interface SocialLoginProvider extends AuthenticationProvider {

    String providerId();

    SocialLoginConfig config();
}
```

```java
public record SocialLoginConfig(
    String clientId,
    String clientSecret,
    java.util.Set<String> defaultScopes,
    String authorizationEndpoint,
    String tokenEndpoint,
    String userinfoEndpoint,
    String issuer
) {}
```

### Google Provider

OIDC-compliant. Uses OIDC Discovery at
`accounts.google.com/.well-known/openid-configuration`.

- `method()` returns `"google"`
- `initiate()`: Constructs authorization URL via nimbus `AuthorizationRequest`
  with PKCE (S256), scopes `openid email profile`, state parameter
- `verify()`: Exchanges code via nimbus `TokenRequest`, validates ID token
  (issuer, audience, nonce, expiry), extracts email + name + picture
- Identity resolution: `ScimUserLookup.findByEmail(email, tenancyId)`

### GitHub Provider

OAuth2 only (not OIDC). Custom endpoints.

- `method()` returns `"github"`
- Authorization: `https://github.com/login/oauth/authorize`
- Token: `https://github.com/login/oauth/access_token`
  (requires `Accept: application/json` header)
- No ID token — separate `GET /user` API call for profile
- Identity resolution: `ScimUserLookup.findByExternalId(githubLogin, tenancyId)`
  or `findByEmail(email, tenancyId)` as fallback

### Apple Provider

OIDC with Apple-specific handling:

- `method()` returns `"apple"`
- Client secret is a JWT signed with an Apple-provisioned P-256 key
  (team ID, key ID, audience `https://appleid.apple.com`). Nimbus handles
  `private_key_jwt` client authentication.
- ID token contains email (may be Apple relay address `@privaterelay.appleid.com`)
- User info (name) only available on first consent. Apple-provided names
  are **not stored** — the SCIM directory is the authoritative source for
  user display names (D11 requires pre-existing SCIM identity). The
  `IdentityBinding` record stores the provider/externalId mapping, not
  profile data. If the SCIM user's display name needs updating from Apple
  data, that is a directory management concern outside the auth flow.
- `response_mode=form_post` for security

### Identity Resolution

After token exchange, the provider resolves the authenticated external
identity to an existing `PrincipalId` (D11):

1. Extract identifier from token/profile (email for Google/Apple,
   login for GitHub)
2. Check `IdentityBindingStore` for existing mapping
   (`provider:externalId → actorId`)
3. If no binding: query `ScimUserLookup.findByEmail(email, tenancyId)`
4. If SCIM user found: create binding in `IdentityBindingStore`,
   return `AuthenticationResult` with the SCIM user's actorId
5. If no SCIM user: fail with `UnknownIdentityException`

The binding table (`IdentityBindingStore`) maps social login external IDs
to platform actorIds, providing stable resolution across SCIM directory
migrations (D15).

```java
package io.casehub.platform.api.authn;

import java.util.Optional;

public interface IdentityBindingStore {

    void bind(IdentityBinding binding);

    Optional<IdentityBinding> findByExternalId(
        String provider, String externalId, String tenancyId);

    Optional<IdentityBinding> findByActorId(
        String actorId, String provider, String tenancyId);

    void unbind(String provider, String externalId, String tenancyId);
}
```

```java
public record IdentityBinding(
    String provider,
    String externalId,
    String actorId,
    String tenancyId,
    String email,
    java.time.Instant createdAt
) {}
```

---

## Session Management (#529)

### Session Lifecycle

```
AuthenticationResult
        │
        ├─── API client ──→ JwtIssuer.issue() ──→ JWT + RefreshToken
        │                                            │
        │                                     subsequent request
        │                                            │
        │                              Quarkus OIDC / Spring Security
        │                                validates JWT claims
        │                                            │
        │                               SecurityIdentityCurrentPrincipal
        │                                reads actorId, tenancyId, groups
        │
        └─── Browser ──→ SessionManager.create() ──→ Set-Cookie: sid=<opaque>
                                                          │
                                                   subsequent request
                                                          │
                                            SessionAuthenticationMechanism
                                              looks up session in SessionStore
                                              creates SecurityIdentity with
                                              attributes (tenancyId, groups)
                                                          │
                                               SecurityIdentityCurrentPrincipal
                                                reads from attribute fallback
```

### JWT Issuance (D10, D12)

`JwtIssuer` issues platform JWTs after successful authentication:

```java
package io.casehub.platform.authn.session;

public interface JwtIssuer {

    TokenPair issue(AuthenticationResult result);

    TokenPair refresh(String refreshToken);

    void revoke(String refreshToken);
}
```

```java
public record TokenPair(
    String accessToken,
    String refreshToken,
    long expiresInSeconds
) {}
```

**JWT claims:**

| Claim | Value |
|-------|-------|
| `sub` | `PrincipalId.value()` (e.g. `human:alice`) |
| `iss` | Platform issuer URI (configurable) |
| `aud` | Configured audience |
| `iat` | Issued-at timestamp |
| `exp` | Expiry (default 15 minutes, configurable) |
| `jti` | Unique token ID (UUID) |
| `tenancyId` | From `AuthenticationResult.tenancyId()` |
| `groups` | From `AuthenticationResult.groups()` |
| `auth_method` | From `AuthenticationResult.method()` |

**Signing:** ES256 (ECDSA P-256) via `JwtSigningKeyResolver` SPI.
SmallRye JWT on Quarkus, Nimbus JOSE+JWT on Spring.

### JwtSigningKeyResolver SPI (D10)

```java
package io.casehub.platform.api.authn;

import java.security.KeyPair;
import java.util.List;

public interface JwtSigningKeyResolver {

    KeyPair signingKeyPair(String tenancyId);

    String keyId(String tenancyId);

    List<PublicKeyDescriptor> publicKeys();
}
```

```java
public record PublicKeyDescriptor(
    String keyId,
    java.security.PublicKey publicKey,
    String algorithm,
    String tenancyId
) {}
```

`publicKeys()` returns all active public keys for JWKS endpoint
publication. Supports key rotation — new key pair becomes active while
old key pair remains in the JWKS until all JWTs signed with it expire.

### Refresh Token Flow (D12)

Opaque refresh tokens stored server-side. Token rotation on every refresh
with replay detection via consumed-flag marking:

1. Client sends refresh token to `/auth/refresh`
2. `JwtIssuer.refresh()` looks up refresh token in `RefreshTokenStore`
3. If `consumed == true`: **replay detected** — `revokeFamily(familyId)`,
   reject request. This indicates the refresh token was intercepted and
   used by both the legitimate client and the attacker.
4. Validates: not expired, family not revoked
5. Marks current token as consumed (`consume()` sets `consumed = true`,
   does **not** delete the record)
6. Issues new JWT + new refresh token with same `familyId`

Expired and consumed tokens are purged by a background `@Scheduled` sweep
(configurable interval, default 1 hour). This preserves replay detection
for the token's TTL window while preventing unbounded storage growth.

```java
package io.casehub.platform.api.authn;

import java.util.Optional;

public interface RefreshTokenStore {

    void store(RefreshTokenRecord record);

    Optional<RefreshTokenRecord> findByToken(String token);

    void consume(String token);

    void revokeFamily(String familyId);

    void revokeByActorId(String actorId, String tenancyId);

    int purgeExpired(java.time.Instant before);
}
```

`findByToken()` retrieves the record without mutation. `consume()` marks
the token's `consumed` flag as `true` (atomic update, not delete).
`purgeExpired()` deletes records with `expiresAt` before the given
instant — called by the background sweep.

```java
public record RefreshTokenRecord(
    String token,
    String familyId,
    String actorId,
    String tenancyId,
    java.time.Instant createdAt,
    java.time.Instant expiresAt,
    boolean consumed
) {}
```

### Cookie Session Store (D2)

Server-side session store for browser clients.

```java
package io.casehub.platform.api.authn;

import java.util.Optional;

public interface SessionStore {

    void store(SessionRecord session);

    Optional<SessionRecord> findById(String sessionId);

    void delete(String sessionId);

    void deleteByActorId(String actorId, String tenancyId);
}
```

```java
public record SessionRecord(
    String sessionId,
    String actorId,
    String tenancyId,
    java.util.Set<String> groups,
    String authMethod,
    String csrfToken,
    java.time.Instant createdAt,
    java.time.Instant expiresAt,
    String deviceFingerprint
) {}
```

Cookie attributes (D16):
- `HttpOnly` — always (prevents XSS access)
- `Secure` — always in production (HTTPS only)
- `SameSite=Lax` — prevents cross-origin form POSTs
- `Path=/` — accessible across all endpoints
- Cookie name: `casehub.sid` (configurable)

Session fixation: session ID regenerated after every successful authentication.

### HttpAuthenticationMechanism (Quarkus)

Custom `HttpAuthenticationMechanism` that validates cookie sessions:

1. Check for `casehub.sid` cookie
2. Look up session in `SessionStore`
3. If valid: build `SecurityIdentity` with:
   - `principal.getName()` = `actorId`
   - `roles` = `groups`
   - `attribute(TENANCY_ID)` = `tenancyId`
   - `attribute(CROSS_TENANT_ADMIN)` = resolved from groups
4. If missing/expired: return `Uni.createFrom().nullItem()`
   (falls through to OIDC or other mechanisms)

**Credential transport:** Return `Uni.createFrom().nullItem()` from
`getCredentialTransport()` to avoid conflicting with OIDC's bearer
transport type. Per garden entry GE-20260628-04a38c.

**Initialization:** Use `@PostConstruct` for eager service initialization,
not `@Observes StartupEvent` — auth mechanisms resolve before StartupEvent
fires. Per garden entry GE-0062.

### Spring Filter

`SessionAuthenticationFilter extends OncePerRequestFilter`:

1. Check for `casehub.sid` cookie
2. Look up session in `SessionStore`
3. If valid: set `SecurityContextHolder` with an `Authentication` carrying
   actorId as principal, groups as authorities, tenancyId in details
4. Chain continues — `SpringSecurityCurrentPrincipal` reads from
   the security context as today

### CSRF Protection (D16)

For cookie-based sessions only (JWT clients don't need CSRF protection):

1. Session creation stores a CSRF token in `SessionRecord.csrfToken`
2. Login response includes the CSRF token in a response header:
   `X-CSRF-Token: <token>`
3. Browser JavaScript reads the header, includes it in subsequent
   state-mutating requests: `X-CSRF-Token: <token>`
4. Server validates: request header matches session's stored token
5. `SameSite=Lax` provides baseline protection; synchronizer token
   provides defense-in-depth

### Session Revocation

- **Logout:** `DELETE /auth/session` — deletes session from store, clears cookie
- **Admin revocation:** `SessionStore.deleteByActorId()` — revokes all
  sessions for an actor
- **JWT revocation:** Short-lived JWT (15 min) + refresh token revocation.
  No explicit JWT deny-list — the 15-minute expiry window is accepted
  as a trade-off for stateless validation

### JWKS Endpoint (D13)

```
GET /.well-known/jwks.json
```

Returns all active public keys from `JwtSigningKeyResolver.publicKeys()`
in JWK Set format (RFC 7517). Supports key rotation — old keys remain
published until all JWTs signed with them have expired.

Per-tenant variant:
```
GET /{tenancyId}/.well-known/jwks.json
```

Implementation: `JwksResource` in `authn` (Quarkus) / `JwksController`
in `authn-spring`. Reads from `JwtSigningKeyResolver`, converts `PublicKey`
to JWK via nimbus-jose-jwt's `JWKSet`.

---

## Social Login → Service Connection (#530)

### OAuthTokenStore SPI (D7)

Stores OAuth access and refresh tokens from social login for downstream
service connection bootstrapping.

```java
package io.casehub.platform.api.authn;

import java.util.List;
import java.util.Optional;

public interface OAuthTokenStore {

    void store(OAuthTokenRecord record);

    Optional<OAuthTokenRecord> findByActorId(
        String actorId, String provider, String tenancyId);

    List<OAuthTokenRecord> findAllByActorId(String actorId, String tenancyId);

    void delete(String actorId, String provider, String tenancyId);

    void updateTokens(String actorId, String provider, String tenancyId,
                      String accessToken, String refreshToken,
                      java.time.Instant expiresAt);
}
```

```java
public record OAuthTokenRecord(
    String actorId,
    String tenancyId,
    String provider,
    String accessToken,
    String refreshToken,
    java.util.Set<String> grantedScopes,
    java.time.Instant expiresAt,
    java.time.Instant createdAt
) {}
```

**Token encryption at rest:** The SPI contract (`OAuthTokenRecord`) carries
plaintext tokens. Encryption is a persistence-layer concern: JPA entities
encrypt access and refresh tokens before write (AES-256-GCM) and decrypt
on read, using a key from `casehub.authn.token-encryption-key`. Inmem
stores hold plaintext (volatile, test-only). This matches the existing
pattern where SPI records carry logical values and persistence handles
storage concerns.

### AuthenticationEventListener SPI (D7)

Framework-neutral callback SPI in `platform-api`. Core modules call the
listener directly — no CDI/Spring event dependency in framework-neutral
code. The `authn` (Quarkus) module provides a CDI event bridge that
re-fires as CDI events; `authn-spring` provides a Spring
`ApplicationEventPublisher` bridge.

```java
package io.casehub.platform.api.authn;

public interface AuthenticationEventListener {

    default void onSocialLoginCompleted(SocialLoginCompleted event) {}

    default void onOAuthTokenRefreshFailed(OAuthTokenRefreshFailed event) {}
}
```

```java
public record SocialLoginCompleted(
    String provider,
    String actorId,
    String tenancyId,
    java.util.Set<String> grantedScopes,
    boolean firstLogin
) {}
```

```java
public record OAuthTokenRefreshFailed(
    String actorId,
    String tenancyId,
    String provider,
    String reason
) {}
```

`@DefaultBean` no-op listener in `platform/`. Connectors repo provides an
implementation that bootstraps `ConnectionPlatform` entries on social login
completion — reading tokens via `OAuthTokenStore`.

### Scope Elevation

Social login for authentication uses minimal scopes (`openid email profile`).
Connection bootstrapping may request additional scopes (e.g., Google
Calendar, Google Drive).

Configurable per deployment:
```properties
casehub.authn.social.google.connection-scopes=\
    https://www.googleapis.com/auth/calendar,\
    https://www.googleapis.com/auth/drive.readonly
```

When `connection-scopes` are configured, the OAuth authorization request
includes them alongside the authentication scopes. One consent screen for
both identity and service access.

### OAuthTokenManager (D7)

Service in `authn-social-core/` that wraps `OAuthTokenStore` with
refresh logic. The store remains a dumb persistence layer — no HTTP,
no provider configuration. Following the established pattern where
services compose stores with external calls (cf. `ScimAgentLookup`
is a service, not a store).

```java
package io.casehub.platform.authn.social;

public interface OAuthTokenManager {

    Optional<OAuthTokenRecord> getValidToken(
        String actorId, String provider, String tenancyId);

    List<OAuthTokenRecord> getAllValidTokens(
        String actorId, String tenancyId);
}
```

`getValidToken()` retrieves the token from `OAuthTokenStore`, checks
expiry, and if expired attempts refresh via nimbus `TokenRequest` with
`RefreshTokenGrant`. On successful refresh, calls
`OAuthTokenStore.updateTokens()`. On failure, notifies
`AuthenticationEventListener.onOAuthTokenRefreshFailed()`.

**Scheduled refresh:** Optional `@Scheduled` sweep for tokens expiring
within a configurable window (default: 5 minutes). The sweep is
framework-specific — wired in `authn/` (Quarkus `@Scheduled`) or
`authn-spring/` (Spring `@Scheduled`).

---

## Module Structure (D8)

### Module Map

| Module | Artifact | Purpose |
|--------|----------|---------|
| platform-api (extended) | `casehub-platform-api` | `io.casehub.platform.api.authn` package: SPIs, exception hierarchy, event records |
| `authn-core/` | `casehub-platform-authn-core` | Framework-neutral: AuthenticationRouterCore, JwtIssuerCore, SessionManagerCore, ChallengeStore logic |
| `authn-webauthn-core/` | `casehub-platform-authn-webauthn-core` | webauthn4j-based registration/authentication ceremony logic + WebAuthn request/response records |
| `authn-social-core/` | `casehub-platform-authn-social-core` | nimbus-oauth2-oidc-sdk code flow + Google/GitHub/Apple providers + OAuthTokenManager + OAuth request/response records |
| `authn-inmem/` | `casehub-platform-authn-inmem` | @Alternative in-memory stores: InMemorySessionStore, InMemoryChallengeStore, InMemoryRefreshTokenStore, InMemoryWebAuthnCredentialStore, InMemoryOAuthTokenStore, InMemoryIdentityBindingStore |
| `session-jpa/` | `casehub-platform-session-jpa` | JPA SessionStore, ChallengeStore, RefreshTokenStore + Flyway (V6000–V6999) |
| `authn-webauthn-jpa/` | `casehub-platform-authn-webauthn-jpa` | JPA WebAuthnCredentialStore + Flyway (V7000–V7999) |
| `authn-social-jpa/` | `casehub-platform-authn-social-jpa` | JPA OAuthTokenStore, IdentityBindingStore + Flyway (V8000–V8999) |
| `authn/` | `casehub-platform-authn` | Quarkus CDI wiring + REST endpoints + HttpAuthenticationMechanism + JWKS + CDI event bridge |
| `authn-spring/` | `casehub-platform-authn-spring` | Spring Boot auto-config + REST controllers + SessionFilter + JWKS + Spring event bridge |
| `session-spring-jpa/` | `casehub-platform-session-spring-jpa` | Spring Data JPA session stores |
| `authn-webauthn-spring-jpa/` | `casehub-platform-authn-webauthn-spring-jpa` | Spring Data JPA WebAuthn stores |
| `authn-social-spring-jpa/` | `casehub-platform-authn-social-spring-jpa` | Spring Data JPA OAuth/binding stores |

### Flyway Version Ranges

Per protocol PP-20260508-07b9f6, each JPA module claims a non-overlapping
thousand-block:

| Range | Module |
|-------|--------|
| V6000–V6999 | `session-jpa/` (session, challenge, refresh_token tables) |
| V7000–V7999 | `authn-webauthn-jpa/` (webauthn_credential table) |
| V8000–V8999 | `authn-social-jpa/` (oauth_token, identity_binding tables) |

Continues the platform allocation: V1–V999 persistence-jpa, V1000–V1999
memory-jpa, V2000–V2999 digest-jpa, V3000–V3999 delivery-tracking-jpa,
V4000–V4999 datasource-jpa, V5000–V5999 platform-view-jpa.

### Dependency Graph

```
platform-api (io.casehub.platform.api.authn)
    │
    ├── authn-core ────────────────── authn (Quarkus CDI + CDI event bridge)
    │       │                             │
    │       ├── authn-webauthn-core ──── authn (wires WebAuthn provider)
    │       │       └── webauthn4j
    │       │
    │       ├── authn-social-core ───── authn (wires social providers)
    │       │       └── nimbus-oauth2-oidc-sdk
    │       │
    │       └── (JwtIssuerCore uses SmallRye JWT on Quarkus, Nimbus JOSE on Spring)
    │
    ├── authn-inmem ──── test scope (all in-memory stores)
    │
    ├── session-jpa ──────────── compile scope (V6000–V6999)
    ├── authn-webauthn-jpa ──── compile scope (V7000–V7999)
    ├── authn-social-jpa ────── compile scope (V8000–V8999)
    │
    └── authn-spring ─── Spring Boot auto-config + Spring event bridge
            ├── authn-core
            ├── authn-webauthn-core
            ├── authn-social-core
            ├── session-spring-jpa
            ├── authn-webauthn-spring-jpa
            └── authn-social-spring-jpa
```

### SPI and Default Bean Pattern

All SPIs in `platform-api` get `@DefaultBean` no-ops in the `platform/`
module:

- `NoOpAuthenticationRouter @DefaultBean` — `availableMethods()` returns
  empty, `initiate()`/`verify()` throw `UnsupportedOperationException`
- `NoOpWebAuthnCredentialStore @DefaultBean` — empty results
- `NoOpOAuthTokenStore @DefaultBean` — empty results
- `NoOpSessionStore @DefaultBean` — empty results
- `NoOpChallengeStore @DefaultBean` — empty results
- `NoOpRefreshTokenStore @DefaultBean` — empty results
- `NoOpIdentityBindingStore @DefaultBean` — empty results
- `NoOpJwtSigningKeyResolver @DefaultBean` — throws
  `UnsupportedOperationException` (signing requires configuration)
- `NoOpAuthenticationEventListener @DefaultBean` — all callbacks are no-ops

---

## REST API

Authentication endpoints in `authn/` (Quarkus JAX-RS) and `authn-spring/`
(Spring MVC). Both frameworks expose the same HTTP contract.

### Endpoints

| Method | Path | Purpose | Request Body | Success | Auth Required |
|--------|------|---------|-------------|---------|---------------|
| `POST` | `/auth/initiate` | Start auth ceremony | `InitiateRequest` | 200 + `ChallengeResponse` | No |
| `POST` | `/auth/verify` | Complete auth ceremony | `VerifyRequest` | 200 + `AuthenticationResponse` or 204 | No |
| `POST` | `/auth/register` | Register new credential | `RegisterRequest` | 200 + `ChallengeResponse` | Yes |
| `POST` | `/auth/refresh` | Refresh JWT | `RefreshRequest` | 200 + `TokenPair` | No |
| `GET` | `/auth/methods` | List available methods | — | 200 + `Set<String>` | No |
| `DELETE` | `/auth/session` | Logout (delete session) | — | 204 | Yes (session cookie) |
| `GET` | `/.well-known/jwks.json` | JWKS public keys | — | 200 + JWK Set | No |
| `GET` | `/{tenancyId}/.well-known/jwks.json` | Per-tenant JWKS | — | 200 + JWK Set | No |

### Request/Response DTOs

```java
// POST /auth/initiate
public record InitiateRequest(
    String method,     // "webauthn", "google", "github", "apple"
    String tenancyId
) {}

// POST /auth/verify
public record VerifyRequest(
    String method,
    String challengeId,
    String responseType,  // "jwt" (default) or "session"
    Map<String, Object> data  // method-specific verification data
) {}

// POST /auth/register (authenticated — binds credential to current user)
public record RegisterRequest(
    String method,
    String tenancyId
) {}

// POST /auth/refresh
public record RefreshRequest(
    String refreshToken
) {}

// Success response for verify (responseType = "jwt")
public record AuthenticationResponse(
    String accessToken,
    String refreshToken,
    long expiresInSeconds
) {}
```

### Response Type Selection

The `responseType` field in `VerifyRequest` determines the session
creation path:

- **`"jwt"` (default):** Server issues JWT access token + opaque refresh
  token. Returns `AuthenticationResponse` in the response body. No cookie.
  For API clients, SPAs with token-based auth, and mobile apps.

- **`"session"`:** Server creates a server-side session in `SessionStore`.
  Sets `casehub.sid` cookie (`HttpOnly`, `Secure`, `SameSite=Lax`).
  Returns `X-CSRF-Token` header. Response body is empty (204). The
  session ID is **not** returned in the response body — it exists only
  in the `HttpOnly` cookie, preserving XSS protection. JavaScript reads
  the CSRF token from the response header for subsequent requests.

If `responseType` is omitted, defaults to `"jwt"`.

### Method Selection

The `method` field in request bodies selects the authentication provider.
The REST layer passes `method` to `AuthenticationRouter.verify()` for
dispatch. Available methods discoverable via `GET /auth/methods` →
`Set<String>`.

### Error Response Format

RFC 7807 Problem Details (`application/problem+json`):

```json
{
  "type": "urn:casehub:authn:invalid-credential",
  "title": "Invalid Credential",
  "status": 401,
  "detail": "WebAuthn assertion signature verification failed"
}
```

Exception-to-status mapping defined in the exception hierarchy (§Authentication SPI).

### CORS

For browser-based WebAuthn and social login flows, CORS is configured
per deployment:

```properties
casehub.authn.cors.allowed-origins=https://app.example.com
casehub.authn.cors.allowed-methods=POST,DELETE,GET
casehub.authn.cors.allowed-headers=Content-Type,X-CSRF-Token
casehub.authn.cors.expose-headers=X-CSRF-Token
```

---

## Integration with Existing Infrastructure

### CurrentPrincipal (D6)

**JWT path (API clients):** Platform-issued JWTs contain `sub`, `tenancyId`,
and `groups` claims. `SecurityIdentityCurrentPrincipal` reads these via its
existing JWT-first resolution:

1. `actorId()` → `identity.getPrincipal().getName()` (JWT `sub` claim)
2. `tenancyId()` → `jwt.getClaim("tenancyId")` → attribute fallback
3. `groups()` → `identity.getRoles()`

**Session path (browser clients):** `SessionAuthenticationMechanism` creates
a `SecurityIdentity` with attributes:

1. `principal.getName()` = `actorId` from `SessionRecord`
2. `roles` = `groups` from `SessionRecord`
3. `attribute(TENANCY_ID)` = `tenancyId` from `SessionRecord`

`SecurityIdentityCurrentPrincipal.tenancyId()` falls through the JWT check
(no JWT) to the `SecurityIdentity.getAttribute()` path — already
implemented per the non-OIDC graceful handling spec (issue #121).

**Quarkus:** Zero changes to `SecurityIdentityCurrentPrincipal` — the
three-tier resolution (anonymous → JWT claim → SecurityIdentity attribute)
already handles non-JWT `SecurityIdentity` instances.

**Spring:** `SpringSecurityCurrentPrincipal` currently only checks
`JwtAuthenticationToken` — there is no attribute/details fallback. The
`SessionAuthenticationFilter` creates a non-JWT `Authentication` carrying
tenancyId in details. `SpringSecurityCurrentPrincipal` must be updated
with the equivalent fallback:

```java
// In SpringSecurityCurrentPrincipal.tenancyId() — after JWT check:
if (auth instanceof AbstractAuthenticationToken aat) {
    Object details = aat.getDetails();
    if (details instanceof Map<?, ?> map) {
        Object tenancy = map.get(SecurityIdentityAttributes.TENANCY_ID);
        if (tenancy instanceof String s && !s.isBlank()) return s;
    }
}
```

Same pattern for `isCrossTenantAdmin()`. This matches the Quarkus
`SecurityIdentityCurrentPrincipal` resolution order: JWT-first, attribute
fallback, then exception.

### OIDC Coexistence

In deployments where both external IdP JWTs and platform-issued JWTs exist:

**Quarkus:** Use Quarkus OIDC multi-tenancy. `TenantResolver` inspects the
JWT `iss` claim to route to the correct OIDC tenant config:
- `iss` = platform issuer → validate against platform's JWKS
- `iss` = external IdP → validate against external IdP's JWKS

**Spring:** Multi-issuer support via Spring Security's
`JwtIssuerAuthenticationManagerResolver`. Configure multiple issuers in
`spring.security.oauth2.resourceserver.jwt.issuer-uri` (Spring Boot 4).

### SCIM Extension (D15)

New `ScimUserLookup` in `identity-core/`, alongside `ScimAgentLookup`
(following the same pattern — framework-neutral, raw `java.net.http.HttpClient`):

```java
package io.casehub.platform.identity;

import java.util.Optional;

public interface ScimUserLookup {

    Optional<ScimUser> findByEmail(String email, String tenancyId);

    Optional<ScimUser> findByExternalId(String externalId, String tenancyId);

    Optional<ScimUser> findByActorId(String actorId, String tenancyId);
}
```

```java
public record ScimUser(
    String actorId,
    String email,
    String displayName,
    String tenancyId
) {}
```

SCIM filter queries:
- `findByEmail`: `GET /Users?filter=emails.value eq "user@example.com"`
- `findByExternalId`: `GET /Users?filter=externalId eq "github:12345"`
- `findByActorId`: `GET /Users?filter=id eq "actorId"`

---

## Security Considerations

### Rate Limiting (D14)

Auth endpoints require per-IP and per-account rate limiting:

| Endpoint | Per-IP limit | Per-account limit | Lockout |
|----------|-------------|-------------------|---------|
| `POST /auth/initiate` | 30/min | 10/min | 5 failures → 15 min lock |
| `POST /auth/verify` | 30/min | 10/min | 5 failures → 15 min lock |
| `POST /auth/refresh` | 60/min | 20/min | No lockout |
| `POST /auth/register` | 10/min | 5/min | 3 failures → 30 min lock |

Implementation: `AuthRateLimiter` in `authn-core` using token bucket
(reusable pattern from `agent-gate-core/` — pure Java, no CDI). Per-IP
tracking via `ConcurrentHashMap<InetAddress, TokenBucket>` with lazy
eviction.

**Per-instance limitation:** Rate limiting state is JVM-local. In
multi-instance deployments behind a load balancer, an attacker can
distribute requests across instances, multiplying the effective rate
by the instance count. Mitigations for clustered deployments:

1. **API gateway rate limiting** (recommended) — rate limit at the load
   balancer or API gateway (e.g. Envoy, Kong, AWS WAF) before requests
   reach application instances
2. **Sticky sessions** — route by source IP at the LB so one IP always
   hits the same instance (limits bypass but not elimination)

A centralized rate limiting SPI (e.g. Redis-backed) is a future
enhancement if infrastructure-level rate limiting proves insufficient.

**Failure definition:** A "failure" for rate limiting purposes is any
`verify()` call that throws `InvalidCredentialException` or
`InvalidChallengeException`. Lockout state is maintained in the same
per-instance `ConcurrentHashMap` with TTL-based expiry.

### Account Enumeration Protection (D14)

- **WebAuthn initiate:** Return a valid-looking challenge response even
  when the user does not exist. The challenge is useless (no credentials
  to satisfy it), but the response shape is identical.
- **Social login:** Always redirect to the provider. Failure happens at
  the identity resolution step, which returns a generic "authentication
  failed" error — never "user not found."

### Credential Storage

- WebAuthn public keys (`publicKeyCose`) are not secret but must be
  tamper-proof — stored with integrity checks (JPA entity with
  `@Column(updatable = false)`)
- OAuth tokens encrypted at rest (AES-256-GCM, key from config)
- Refresh tokens hashed (SHA-256) in the database — raw value returned
  to client only at issuance
- Session IDs generated via `SecureRandom` (256 bits, URL-safe base64)

### PKCE Enforcement

All OAuth2 authorization code flows use PKCE (RFC 7636) with S256 method.
Enforced in `authn-social-core` via nimbus — not optional.

---

## Testing Strategy

| Module | Testing approach |
|--------|-----------------|
| `authn-core` | Unit tests for router dispatch, JWT issuance (mock key resolver), session lifecycle |
| `authn-webauthn-core` | webauthn4j test utilities — mock authenticator for registration/assertion ceremonies |
| `authn-social-core` | WireMock HTTP server for provider token/userinfo endpoints. Test PKCE, state, nonce validation |
| `authn-inmem` | Unit tests for TTL eviction, concurrent access, atomic consume |
| `session-jpa` | `@QuarkusTest` with `@TestTransaction`. Flyway migration tests |
| `authn` (Quarkus) | `@QuarkusTest` integration: full ceremony flow via REST endpoints. `@TestSecurity` for authenticated paths. Cookie session + CSRF round-trip |
| `authn-spring` | `@SpringBootTest` integration: full ceremony flow. `MockMvc` for endpoint tests |
| All inmem stores | `@Alternative @Priority(100)` — test scope isolation |

### Test Identity Fixtures

`FixedCurrentPrincipal @Priority(200)` continues to work for tests that
don't exercise the auth flow. Auth ceremony tests use real
`AuthenticationProvider` instances with mock stores.

---

## Configuration

```properties
# --- Authentication ---
casehub.authn.issuer=https://platform.example.com
casehub.authn.audience=casehub
casehub.authn.token-encryption-key=  # AES key for OAuth token encryption at rest

# --- JWT ---
casehub.authn.jwt.expiry=PT15M       # JWT expiry (ISO 8601 duration)
casehub.authn.jwt.refresh-ttl=P7D    # Refresh token TTL
casehub.authn.jwt.keystore-path=     # Path to PKCS12/JKS keystore
casehub.authn.jwt.keystore-password= # Keystore password
casehub.authn.jwt.key-alias=         # Key alias within keystore

# --- WebAuthn ---
casehub.authn.webauthn.rp-id=example.com
casehub.authn.webauthn.rp-name=CaseHub
casehub.authn.webauthn.attestation=none
casehub.authn.webauthn.user-verification=preferred
casehub.authn.webauthn.challenge-ttl=PT5M

# --- Social Login ---
casehub.authn.social.google.client-id=
casehub.authn.social.google.client-secret=
casehub.authn.social.google.connection-scopes=

casehub.authn.social.github.client-id=
casehub.authn.social.github.client-secret=

casehub.authn.social.apple.client-id=
casehub.authn.social.apple.team-id=
casehub.authn.social.apple.key-id=
casehub.authn.social.apple.private-key-path=

# --- Sessions ---
casehub.authn.session.cookie-name=casehub.sid
casehub.authn.session.cookie-domain=  # Defaults to request host
casehub.authn.session.ttl=PT8H

# --- Rate Limiting ---
casehub.authn.rate-limit.per-ip=30
casehub.authn.rate-limit.per-account=10
casehub.authn.rate-limit.lockout-threshold=5
casehub.authn.rate-limit.lockout-duration=PT15M
```

---

## Documentation Updates

### Consumer Guide (`docs/guides/consumer-guide.md`)

New section "Authentication" covering:
- Authentication module overview and available providers
- WebAuthn/passkey setup: rpId, rpName, registration flow
- Social login setup: provider configuration, scope elevation
- Session management: JWT vs cookie, refresh flow
- Multi-issuer configuration for mixed IdP + platform auth

### Contributor Guide (`docs/guides/contributor-guide.md`)

New section "Adding an Authentication Provider":
- Implement `AuthenticationProvider` interface
- Register as CDI bean / Spring `@Component`
- Two-phase ceremony contract: `initiate(context)` returns challenge,
  `verify(challenge, data)` receives consumed challenge + raw data map
- Challenge lifecycle owned by the router — providers never call
  `ChallengeStore` during verify
- Define method-specific `ChallengeResponse` record in provider module
- Document expected verification data map keys

### ARC42STORIES.MD

New glossary entries: Authentication Ceremony, Challenge, Passkey,
Refresh Token, JWKS.

---

## Scope Exclusions

| Concern | Rationale | Issue |
|---------|-----------|-------|
| User registration / self-signup | Out of scope — D11 requires pre-existing SCIM identity | #531 |
| Password authentication | Focused on passwordless (WebAuthn) and social login | #532 |
| MFA / step-up authentication | Future enhancement — requires SPI evolution (`verify()` return type must support partial success / step-up continuation). Current SPI is single-factor only. | #533 |
| Authorization (RBAC) | Existing `@RolesAllowed` + ACL subsystem. Auth != authz | — |
| ConnectionPlatform SPI | Lives in connectors repo — platform fires `AuthenticationEventListener` callbacks | — |
| ActorType → PrincipalType rename | Tracked in #272, separate slot | #272 |
| CurrentPrincipal → CurrentActor rename | Tracked in #273, separate slot | #273 |

---

## References

- casehubio/platform#525 — epic
- casehubio/platform#526 — authentication SPI
- casehubio/platform#527 — WebAuthn provider
- casehubio/platform#528 — social login providers
- casehubio/platform#529 — session management
- casehubio/platform#530 — social login → service connection
- `platform-api/src/main/java/io/casehub/platform/api/identity/CurrentPrincipal.java` — existing identity SPI
- `platform-api/src/main/java/io/casehub/platform/api/identity/Identity.java` — sealed identity hierarchy
- `platform-api/src/main/java/io/casehub/platform/api/identity/SecurityIdentityAttributes.java` — JWT/attribute convention
- `oidc/src/main/java/io/casehub/platform/oidc/SecurityIdentityCurrentPrincipal.java` — JWT-first resolution
- `docs/specs/2026-06-28-oidc-graceful-non-oidc-design.md` — non-OIDC SecurityIdentity handling
- `specs/issue-271-principal-identity-model/2026-09-03-principal-identity-model-design.md` — identity hierarchy spec
- Garden entry GE-20260628-04a38c — HttpAuthenticationMechanism credential transport gotcha
- Garden entry GE-0062 — HttpAuthenticationMechanism StartupEvent timing
- W3C Web Authentication (WebAuthn) Level 2 spec
- OAuth 2.0 Authorization Framework (RFC 6749)
- OAuth 2.0 for Native Apps (RFC 8252)
- PKCE (RFC 7636)
- JSON Web Key (RFC 7517)
- OAuth 2.0 Threat Model (RFC 6819)
- webauthn4j library — https://github.com/webauthn4j/webauthn4j
- nimbus-oauth2-oidc-sdk — https://connect2id.com/products/nimbus-oauth-openid-connect-sdk
- OWASP Authentication Cheat Sheet
- OWASP CSRF Prevention Cheat Sheet
