# Epic 525 — Identity Authentication Design Decisions

## D1: Authentication Architecture

**Choice:** Platform-native ceremonies
**Alternatives:**
- External IdP delegation (Keycloak/Auth0) — simpler security posture but adds IdP dependency, limits UX control
- Hybrid SPI supporting both — maximum flexibility but premature abstraction
**Rationale:** Platform owns the auth flows (WebAuthn registration/attestation, OAuth2 code exchange, session issuance). SPIs in platform-api, framework-specific implementations in separate modules. Matches the established pattern where platform provides real implementations.
**Trade-offs:** Platform takes on credential security responsibility. Must handle key management, token signing, and secure storage.
**Sources:** Existing platform pattern (identity-core, oidc, scim modules), issue #525 body
**Exploration:** quick
**Status:** captured

## D2: Session Management Model

**Choice:** Dual — JWT for APIs, cookie sessions for browser
**Alternatives:**
- Platform-issued JWT only — simpler, stateless, but no server-side revocation for browser sessions
- Opaque session tokens + server-side store only — more control but adds statefulness everywhere
**Rationale:** API clients need stateless JWT for service-to-service calls. Browser clients need HTTP-only cookie sessions for security (XSS protection, CSRF tokens). The dual model serves both use cases natively.
**Trade-offs:** Two code paths for session validation. Browser sessions need a server-side store (inmem/JPA). JWT revocation requires a deny-list or short expiry + refresh tokens.
**Sources:** SecurityIdentityCurrentPrincipal JWT-first resolution, garden entries on HttpAuthenticationMechanism
**Exploration:** quick
**Status:** captured

## D3: User Profile Storage

**Choice:** SCIM for profiles, local persistence for auth credentials only
**Alternatives:**
- All local — platform manages profiles too — duplicates SCIM, adds registration flow
**Rationale:** Platform already integrates with SCIM (scim-core, scim, scim-spring). User profiles live in the external directory. Platform adds only: WebAuthnCredentialStore, OAuthTokenStore, SessionStore. Clean separation.
**Trade-offs:** Requires a functioning SCIM directory for user lookup during registration flows. First-login must map authn identity to existing SCIM identity.
**Sources:** Existing SCIM integration (GroupMembershipProvider, ScimAgentLookup), consumer-guide.md identity section
**Exploration:** quick
**Status:** captured

## D4: WebAuthn Library Approach

**Choice:** Framework-neutral core on raw webauthn4j
**Alternatives:**
- Framework-native extensions (quarkus-security-webauthn, spring-security-webauthn) — less code but two separate implementations with different storage contracts
- Wrap framework extensions behind unified SPI — gets framework polish but adds adapter layer
**Rationale:** Build authn-webauthn-core with raw webauthn4j for registration/attestation/assertion verification. Thin Quarkus and Spring modules provide HTTP endpoints and CDI/Spring wiring. Matches the platform's -core / Quarkus / -spring pattern. Full control over ceremony flow and credential storage.
**Trade-offs:** More code than using framework extensions. Must implement challenge management, attestation validation, and ceremony state ourselves.
**Sources:** webauthn4j library (Java 17+, Apache 2.0, FIDO2 certified), Quarkus pins webauthn4j 0.30.3
**Exploration:** quick
**Status:** captured

## D5: Social Login Implementation

**Choice:** nimbus-oauth2-oidc-sdk (revised from raw HttpClient after decision review R1-03)
**Alternatives:**
- Direct java.net.http.HttpClient — hand-rolls PKCE, ID token validation, Apple JWT client secrets from scratch. Security risk.
- ScribeJava library — handles 30+ providers, ScribeJava 8.x added AppleClientSecretGenerator
- Framework-native OAuth2 clients — framework-tested but two separate code paths
**Rationale:** nimbus-oauth2-oidc-sdk (Apache 2.0, pure Java, ~300KB) correctly handles PKCE (RFC 7636), ID token validation (OIDC Core §3.1.3.7), Apple's JWT-signed client_secret, and GitHub's non-OIDC token format. Used internally by Spring Security OAuth2. Framework-neutral — fits the -core module pattern.
**Trade-offs:** Adds a dependency (~300KB). nimbus API is comprehensive but verbose. Provider-specific adapters still needed for endpoint configuration.
**Sources:** nimbus-oauth2-oidc-sdk docs, OAuth2/OIDC specs, decision review R1-03
**Exploration:** quick
**Status:** revised (R1-03)

## D6: CurrentPrincipal Integration

**Choice:** Issue JWT → existing OIDC validation path
**Alternatives:**
- New AuthenticatedCurrentPrincipal at higher priority — breaks security context abstraction
- SecurityIdentityAugmentor — works for OIDC but doesn't help session-based auth
**Rationale:** Platform issues its own JWT after authn. Quarkus OIDC validates it on subsequent requests. SecurityIdentityCurrentPrincipal reads claims as today. For browser sessions, a custom HttpAuthenticationMechanism looks up the session and creates a SecurityIdentity with tenancyId/groups as attributes. Zero changes to CurrentPrincipal.
**Trade-offs:** Platform must manage JWT signing keys. Session-based auth path uses the SecurityIdentity attribute fallback (already implemented). Must configure OIDC to accept platform-issued tokens.
**Sources:** SecurityIdentityCurrentPrincipal (JWT-first → attribute fallback), OIDC design spec (issue #121), HttpAuthenticationMechanism garden entries (credential transport gotchas, StartupEvent timing)
**Exploration:** quick
**Status:** captured

## D7: Social Login → Service Connection

**Choice:** OAuthTokenStore SPI + CDI event
**Alternatives:**
- Pass tokens directly to ConnectionPlatform — tighter coupling, platform needs connectors-api dependency
- Event only, no token storage — simpler but connectors needs its own token refresh
**Rationale:** Platform stores OAuth tokens (access + refresh) in OAuthTokenStore SPI (inmem/JPA pattern). On first social login, fires SocialLoginCompleted CDI event. Connectors repo observes the event to bootstrap a ConnectionPlatform entry. Clean separation.
**Trade-offs:** Token refresh responsibility stays with platform (must implement OAuth2 refresh flow). Connectors depends on the CDI event contract.
**Sources:** Existing CDI event patterns (SubscriptionMatched, EndpointRegistered, DataSourceRegistered)
**Exploration:** quick
**Status:** captured

## D8: Module Structure

**Choice:** Per-concern modules
**Alternatives:**
- Consolidated modules (fewer, larger) — simpler dependency graph but can't pick individual providers
**Rationale:** Separate modules per concern: authn-api (SPIs), authn-core (shared ceremony logic), authn-webauthn-core, authn-social-core, session-core, plus Quarkus and Spring wiring modules. Each concern is independently deployable — use WebAuthn without social login.
**Trade-offs:** More modules in the reactor. Must manage inter-module dependencies carefully.
**Sources:** Existing module pattern (agent-claude, agent-openai, agent-ollama — per-provider modules)
**Exploration:** quick
**Status:** captured

## D9: AuthenticationProvider SPI Shape

**Choice:** Two-phase ceremony (initiate + verify)
**Alternatives:**
- Single-shot authenticate() — simpler SPI but ceremony state management leaks into the HTTP layer
**Rationale:** WebAuthn and OAuth2 are both two-phase protocols. AuthenticationProvider has `initiate(context) → ChallengeResponse` and `verify(request) → AuthenticationResult`. WebAuthn initiate() generates a challenge; verify() validates the assertion. OAuth initiate() returns a redirect URL; verify() exchanges the code. Captures the ceremony lifecycle at the SPI level.
**Trade-offs:** Password/API-key auth doesn't need two phases — initiate() would be a no-op. But a single-phase adapter is trivial.
**Sources:** WebAuthn spec (challenge-response), OAuth2 authorization code flow (redirect-callback)
**Exploration:** quick
**Status:** captured

## D10: JWT Signing Infrastructure

**Choice:** JwtSigningKeyResolver SPI with standard Java KeyStore (revised from platform-signing after decision review R1-04)
**Alternatives:**
- Reuse platform-signing (EU DSS) — incompatible type system (DSSPrivateKeyEntry vs JCA PrivateKey), no JWKS concept
- Separate HMAC secret per tenant — no public key distribution
- Dedicated auth keystore without SPI — doesn't support multi-framework
**Rationale:** New thin SPI: JwtSigningKeyResolver reads signing keys from a standard Java KeyStore (JKS/PKCS12). SmallRye JWT handles signing on Quarkus side (already a transitive dependency via quarkus-oidc). Nimbus JOSE+JWT on Spring side (already in spring-security-oauth2-jose). JWKS endpoint (`/.well-known/jwks.json`) for public key distribution. ES256 (ECDSA P-256) for compact JWTs. Per-tenant key resolution via SPI.
**Trade-offs:** Does not reuse platform-signing infrastructure — separate key management for auth. But the key types and lifecycle requirements are genuinely different (DSS for document signing, JCA for JWT).
**Depends on:** D1 (platform-native ceremonies require JWT issuance)
**Sources:** SmallRye JWT docs, Nimbus JOSE+JWT, decision review R1-04, RFC 7517 (JWK)
**Exploration:** quick
**Status:** revised (R1-04)

## D11: First Login Policy

**Choice:** Pre-existing SCIM identity required
**Alternatives:**
- Auto-provision on first login — requires SCIM write access, blurs auth/provisioning boundary
- Configurable policy — flexible but more complex SPI surface
**Rationale:** User must exist in SCIM before registering a passkey or linking a social login. Registration binds a credential to an existing actorId. Provisioning happens out-of-band. Keeps platform as infrastructure, not a user registration system.
**Trade-offs:** Users can't self-register — admin must create them first. Social login discovery flow ("does this email exist?") requires SCIM read access during the auth ceremony.
**Depends on:** D3 (SCIM for profiles)
**Sources:** Existing SCIM integration (ScimAgentLookup, GroupMembershipProvider)
**Exploration:** quick
**Status:** captured

## D12: JWT Token Lifecycle (surfaced by decision review R1-12)

**Choice:** Short-lived JWT (15 min) + opaque refresh tokens with rotation
**Alternatives:**
- JWT deny-list — requires shared low-latency cache, propagation across nodes
- Long-lived JWT — simpler but no revocation capability
**Rationale:** Short JWT expiry limits damage from token theft. Opaque refresh tokens stored server-side enable revocation. Token rotation on refresh (RFC 6819) prevents replay — each refresh token is single-use, issuing a new refresh token alongside the new JWT. Refresh token family tracking detects stolen tokens.
**Trade-offs:** Requires refresh token store (JPA/inmem). Clients must implement refresh flow. 15-minute window where a revoked JWT remains valid.
**Depends on:** D2 (dual session model), D10 (JWT signing)
**Sources:** RFC 6819 (OAuth 2.0 Threat Model), decision review R1-12
**Exploration:** quick
**Status:** captured

## D13: JWKS Endpoint (surfaced by decision review R1-13)

**Choice:** Platform exposes `/.well-known/jwks.json` endpoint
**Rationale:** Standard mechanism for distributing JWT verification public keys. Service-to-service callers resolve the JWKS endpoint to validate platform-issued JWTs. Supports key rotation — new keys appear in JWKS before old keys expire from issued tokens. Per-tenant JWKS via path: `/{tenancyId}/.well-known/jwks.json`.
**Depends on:** D10 (JwtSigningKeyResolver provides the keys to publish)
**Sources:** RFC 7517 (JSON Web Key), decision review R1-13
**Exploration:** quick
**Status:** captured

## D14: Auth Endpoint Security (surfaced by decision review R1-10)

**Choice:** Rate limiting + account enumeration protection + lockout policy
**Rationale:** Auth endpoints are public attack surfaces. Per-IP rate limiting via token bucket (reusable pattern from agent-gate, adapted for HTTP). Account enumeration protection: WebAuthn initiate() returns consistent responses regardless of whether user exists. Social login redirects always succeed (failure happens at provider). Configurable lockout: N failed attempts → temporary lock (default: 5 attempts, 15-minute lockout). These are OWASP Authentication requirements.
**Sources:** OWASP Authentication Cheat Sheet, agent-gate token bucket pattern, decision review R1-10
**Exploration:** quick
**Status:** captured

## D15: SCIM User Lookup Extension (surfaced by decision review R1-08)

**Choice:** Add ScimUserLookup service for email/external-ID-based user search
**Rationale:** Current SCIM module only queries groups (GroupMembershipProvider). Social login and WebAuthn registration require looking up users by email or external ID. New ScimUserLookup service in scim-core: `findByEmail(email, tenancyId) → Optional<ScimUser>`, `findByExternalId(externalId, tenancyId) → Optional<ScimUser>`. Uses SCIM Users endpoint filter queries. Binding table maps social login external IDs to actorIds for stable resolution across SCIM directory migrations.
**Depends on:** D3 (SCIM for profiles), D11 (pre-existing identity required)
**Sources:** SCIM v2 spec (RFC 7644), existing ScimAgentLookup pattern, decision review R1-08
**Exploration:** quick
**Status:** captured

## D16: CSRF Protection (surfaced by decision review R1-06)

**Choice:** SameSite=Lax cookie + synchronizer token pattern for state-mutating requests
**Rationale:** Cookie-based sessions require CSRF protection. SameSite=Lax prevents cross-origin form POSTs. Synchronizer token (CSRF token in session, validated on POST/PUT/DELETE) provides defense-in-depth. Session fixation addressed by regenerating session ID after authentication. Secure + HttpOnly cookie flags mandatory.
**Depends on:** D2 (cookie sessions for browser)
**Sources:** OWASP CSRF Prevention Cheat Sheet, decision review R1-06
**Exploration:** quick
**Status:** captured
