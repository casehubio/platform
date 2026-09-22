# Design: OIDC CurrentPrincipal + SCIM + Credentials for Spring Security

**Issue:** casehubio/parent#508
**Scale:** M | **Complexity:** Med
**Branch:** issue-501-spring-deployment-readiness

## Summary

Four new modules translate Quarkus identity, group membership, and credential infrastructure to Spring Boot:

1. **oidc-spring** — `SpringSecurityCurrentPrincipal` backed by Spring Security `Authentication`
2. **scim-core** — framework-neutral SCIM protocol logic extracted from `scim/`
3. **scim-spring** — Spring auto-config for SCIM with `RestClient` + `@Cacheable`
4. **credentials-spring** — `EnvironmentCredentialResolver` reading secrets from Spring `Environment`

## Module 1: oidc-spring

### Design (D17)

`SpringSecurityCurrentPrincipal implements CurrentPrincipal` — reads from `SecurityContextHolder.getContext().getAuthentication()`.

**Mapping:**

| CurrentPrincipal | Spring Security source |
|---|---|
| `actorId()` | `Authentication.getName()` |
| `groups()` | `Authentication.getAuthorities()` → extract `GrantedAuthority.getAuthority()`, strip `ROLE_` prefix |
| `tenancyId()` | `JwtAuthenticationToken.getToken().getClaimAsString("tenancyId")` |
| `isCrossTenantAdmin()` | `JwtAuthenticationToken.getToken().getClaim("crossTenantAdmin")` as `Boolean` |

**Anonymous handling:** When `Authentication` is null or `AnonymousAuthenticationToken`, return sentinels (`"anonymous"`, empty groups, `DEFAULT_TENANT_ID`, false) — matching Quarkus `identity.isAnonymous()` semantics.

**Tenancy resolution:**
1. JWT claim — `Jwt.getClaimAsString(SecurityIdentityAttributes.TENANCY_ID)` when `Authentication` is `JwtAuthenticationToken` (same claim constant as Quarkus)
2. Non-JWT `Authentication` → throw `MissingTenancyException` (Spring Security OIDC is always JWT-based; non-JWT auth mechanisms are custom and should provide their own `CurrentPrincipal`)
3. Missing/blank claim → throw `MissingTenancyException`

Blank/empty claim values fall through (consistent with Quarkus). Wrong-type claims throw `IllegalStateException`.

**Exception handling:** `MissingTenancyExceptionHandler` as `@ControllerAdvice @ExceptionHandler(MissingTenancyException.class)` → 403 JSON (same response body as Quarkus `ExceptionMapper`).

**Scope:** `@RequestScope` bean — Spring creates a proxy; injecting into singleton services is safe (same CDI client proxy semantics). `@ConditionalOnMissingBean(CurrentPrincipal.class)` — backs off when app provides its own.

**No core extraction** — `SecurityContextHolder` is static thread-local. No constructor params for the generator to scan.

### Files

| File | Purpose |
|------|---------|
| `SpringSecurityCurrentPrincipal.java` | `CurrentPrincipal` impl reading from `SecurityContextHolder` |
| `MissingTenancyExceptionHandler.java` | `@ControllerAdvice` returning 403 JSON |
| `OidcSpringAutoConfiguration.java` | `@AutoConfiguration` producing the beans |

### Dependencies

- `casehub-platform-api` (CurrentPrincipal SPI)
- `spring-boot-autoconfigure`
- `spring-security-oauth2-resource-server` (for `JwtAuthenticationToken`)
- `spring-security-core` (for `Authentication`, `GrantedAuthority`)

## Module 2: scim-core (D16)

### Design

Extract framework-neutral SCIM protocol logic from `scim/`. The core module owns the SCIM client interface, business logic, configuration interface, and model records.

### Files

| File | Purpose |
|------|---------|
| `QuarkusScimClient.java` | Plain Java interface — `listGroups(filter, attributes)`, `getGroup(id, attributes)`, `getGroup(id, attributes, startIndex, count)` |
| `ScimGroupMembershipProviderCore.java` | POJO implementing `GroupMembershipProvider`, constructor `(ScimClient, ScimProperties)` |
| `ScimProperties.java` | Interface — `int memberPageSize()` |
| `model/ScimListResponse.java` | Moved from `scim/` (unchanged) |
| `model/ScimGroupResource.java` | Moved from `scim/` (unchanged) |
| `model/ScimMemberRef.java` | Moved from `scim/` (unchanged) |

### Dependencies

- `casehub-platform-api` (GroupMembershipProvider SPI)
- `jackson-annotations` (model record annotations)

### scim/ refactoring

| Change | Detail |
|--------|--------|
| `ScimClient` → `QuarkusScimClient extends ScimClient` | Adds `@RegisterRestClient`, JAX-RS annotations |
| New `ScimBeans` | `@Produces ScimGroupMembershipProviderCore(scimClient, config)` |
| New `CachingScimGroupMembershipProvider` | Wraps core with `@CacheResult(cacheName = "scim-group-members")` |
| `ScimConfig extends ScimProperties` | Core inherits `memberPageSize()`, config adds `token()` |
| Model records | Move to scim-core, scim/ imports from there |

## Module 3: scim-spring

### Design

Spring-generator produces the auto-config for `ScimGroupMembershipProviderCore`. Two hand-written components provide the framework-specific HTTP client and caching.

### Generated (by spring-generator)

| File | What generator produces |
|------|------------------------|
| `ScimAutoConfiguration.java` | `@Bean ScimGroupMembershipProviderCore(ScimClient, ScimSpringProperties)` |
| `ScimSpringProperties.java` | `@ConfigurationProperties(prefix = "casehub.platform.scim") record ... implements ScimProperties` |

### Hand-written

| File | Purpose |
|------|---------|
| `RestClientScimClient.java` | `implements ScimClient` — uses Spring `RestClient` for SCIM HTTP calls |
| `ScimSpringManualConfig.java` | `@Bean ScimClient` (RestClient setup + auth), `@Bean GroupMembershipProvider` (caching wrapper) |

**Auth:** Static bearer token from `casehub.platform.scim.token` property. OAuth2 client-credentials via Spring Security's `OAuth2AuthorizedClientManager` (equivalent to Quarkus `@NamedOidcClient`).

**Caching:** `@Cacheable("scim-group-members")` on the wrapper's `membersOf()`.

### Dependencies

- `casehub-platform-scim-core`
- `casehub-platform-scim` (provided — for Jandex scanning)
- `spring-boot-autoconfigure`
- `spring-web` (for RestClient)
- `spring-boot-starter-cache` (for @Cacheable)

## Module 4: credentials-spring (D18)

### Design

`EnvironmentCredentialResolver implements CredentialResolver` — reads secrets from Spring `Environment`. All spring-cloud secret backends (Vault, AWS SSM, Kubernetes) surface as Spring properties.

**Resolution:** `environment.getProperty(credentialRef + "." + key)` for each `CredentialPropertyKeys` constant (`USER`, `PASSWORD`, `BEARER_TOKEN`, `API_KEY`, `EXPIRES_AT`, `SIGNING_SECRET`). Returns non-null entries as `Map<String, String>`.

**Null/empty credentialRef:** returns `Map.of()` (matching Quarkus behaviour).

### Files

| File | Purpose |
|------|---------|
| `EnvironmentCredentialResolver.java` | `CredentialResolver` impl reading from Spring `Environment` |
| `CredentialsSpringAutoConfiguration.java` | `@AutoConfiguration` producing the bean with `@ConditionalOnMissingBean` |

### Dependencies

- `casehub-platform-api` (CredentialResolver SPI)
- `spring-boot-autoconfigure`

### Consumer configuration example

```properties
# Vault-backed (via spring-cloud-vault)
spring.cloud.vault.kv.backend=secret
spring.cloud.vault.kv.application-name=casehub

# Then reference in code or YAML:
# credentialRef = "my-api-creds"
# Resolves: my-api-creds.API_KEY → spring property
```

## Build changes

- Add `scim-core`, `oidc-spring`, `scim-spring`, `credentials-spring` to root POM `<modules>`
- Add `oidc-spring`, `scim-spring`, `credentials-spring` to `spring-boot-starter` dependencies
- Add Jandex index generation to `scim-core`
- Add `additional-spring-configuration-metadata.json` to `oidc-spring` (principal config) and `scim-spring` (SCIM config)

## Testing

| Module | Test approach |
|--------|---------------|
| `oidc-spring` | Unit tests with mocked `SecurityContext` — mirror all cases from `SecurityIdentityCurrentPrincipalTest` (JWT path, non-JWT path, anonymous, edge cases) |
| `scim-core` | Unit tests with mock `ScimClient` — business logic only, no HTTP |
| `scim-spring` | Integration test with `MockRestServiceServer` or WireMock |
| `credentials-spring` | Unit test with `MockEnvironment` |

## References

- `oidc/src/main/java/.../SecurityIdentityCurrentPrincipal.java` — Quarkus implementation
- `scim/src/main/java/.../ScimGroupMembershipProvider.java` — Quarkus SCIM provider
- `credentials-quarkus/src/main/java/.../QuarkusCredentialResolver.java` — Quarkus credential bridge
- `platform-api/src/main/java/.../SecurityIdentityAttributes.java` — shared claim constants
- `spring-generator` `findConfigMapping` (walk-DOWN detection) — confirms ScimProperties extraction viable
- D16 (SCIM core extraction), D17 (oidc-spring mapping), D18 (credentials Environment resolver)
