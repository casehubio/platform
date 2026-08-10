# ScimDIDResolver — Design Spec

**Issue:** casehubio/platform#85
**Date:** 2026-06-29
**Branch:** issue-85-scim-did-resolver

---

## Problem

Enterprise deployments using SCIM as the identity authority must currently also
host DID documents externally (via did:web URLs). SCIM already holds the
certificate material (`x509Certificates[]`) and the actorId binding
(`externalId`). A ScimDIDResolver constructs a synthetic DIDDocument directly
from SCIM data, eliminating the external hosting requirement.

## Decision Prerequisites (Issue #85)

Issue #85 posed two decision prerequisites before implementation:

1. **W3C DID Core compliance trade-off:** Accepted for SCIM-sourced actors.
   Synthetic DIDDocuments constructed from SCIM data are not independently
   resolvable by third parties — they exist only within the platform's identity
   resolution pipeline. The composite resolver pattern mitigates the original
   concern: external issuer DIDs still resolve via did:web, so VC validation
   continues to work for all externally-issued credentials.

2. **Deployment scenario:** Enterprises using SCIM as the sole identity
   authority for actors. The SCIM endpoint holds certificate material
   (`x509Certificates[]`) and DID mappings (extension `did` field).
   Maintaining separately-hosted DID documents at did:web URLs is additional
   operational overhead that this resolver eliminates. The composite pattern
   means SCIM and did:web coexist — SCIM handles actor DIDs while did:web
   handles external issuer DIDs.

## Root Finding — Composite Resolver

Exploration uncovered a deeper architectural problem. `DIDResolver` has three
production callers:

| Caller | DID meaning | Has actorId? |
|--------|-------------|-------------|
| `ActorIdentityValidationEnricher` (ledger) | actor's DID | yes |
| `AgentIdentityVerificationService` (identity/) | actor's DID | yes |
| `JwtVCValidator` (identity/) | **issuer's** DID | **no** |

JwtVCValidator resolves the credential *issuer's* DID — a different entity
than the actor. In a SCIM-only deployment where ScimDIDResolver replaces
WebDIDResolver (`@Alternative`), issuer DID resolution silently fails because
ScimDIDResolver cannot resolve non-SCIM DIDs. VC validation returns
`ISSUER_UNKNOWN` for all external issuers.

Root cause: the `@Alternative` pattern (single active resolver) is
fundamentally wrong. SCIM introduces a resolver that handles a *subset* of
DIDs (those belonging to known actors). External issuer DIDs still need
did:web resolution. Both must coexist.

The SPI Javadoc already promised "Multiple resolvers may be registered; the
resolution pipeline selects the first non-empty result." That pipeline was
never implemented. This design builds it.

## Design

### 1. SPI change — DIDResolver (platform-api)

Change the method signature:

```java
// Before
Optional<DIDDocument> resolve(String did);

// After
Optional<DIDDocument> resolve(String actorId, String did);
```

`actorId` — the actor that claims this DID, or `null` when the actor is
unknown (e.g., resolving a credential issuer). No default method — clean
break, all implementations update.

Rationale: the identity SPI family is inconsistent. `ActorDIDProvider.didFor`
takes actorId; `AgentCredentialValidator.validate` takes both actorId and did;
only `DIDResolver.resolve` drops the actorId. Fixing this makes the SPI
family consistent and enables SCIM-based resolution.

Updated Javadoc:

```java
/**
 * Resolves a DID URI to a DID document.
 *
 * <p>Return empty when the DID is unresolvable — for example when the method is
 * unsupported, the network is unreachable, or the document does not exist.
 * Implementations MUST NOT throw — return empty for any failure case.
 *
 * <p>Implementations are CDI beans annotated with {@code @DIDMethod}.
 * {@link CompositeDIDResolver} iterates all {@code @DIDMethod} resolvers
 * in {@code @Priority} order, returning the first non-empty result.
 * Method-specific resolvers (did:web, did:key) should use higher priority
 * than actorId-based resolvers (SCIM) to ensure authoritative sources
 * are consulted first.
 */
```

### 2. CDI qualifier — @DIDMethod (platform-api)

New CDI qualifier annotation to distinguish individual (contributor) resolvers
from the composite (consumer-facing) resolver:

```java
@Qualifier
@Retention(RUNTIME)
@Target({TYPE, METHOD, FIELD, PARAMETER})
public @interface DIDMethod {}
```

### 3. Composite resolver — CompositeDIDResolver (identity/)

New `@ApplicationScoped` bean implementing `DIDResolver` (unqualified).
Injects `@DIDMethod Instance<DIDResolver>` and iterates in `@Priority`
order, returning the first non-empty result. Catches exceptions from
individual resolvers to prevent one failure from blocking the pipeline.

```java
@ApplicationScoped
public class CompositeDIDResolver implements DIDResolver {

    @Inject @DIDMethod
    Instance<DIDResolver> methodResolvers;

    @Override
    public Optional<DIDDocument> resolve(String actorId, String did) {
        for (DIDResolver r : sortedByPriority(methodResolvers)) {
            try {
                Optional<DIDDocument> result = r.resolve(actorId, did);
                if (result.isPresent()) return result;
            } catch (Exception e) {
                LOG.warnf("Resolver %s failed for DID %s: %s",
                    r.getClass().getSimpleName(), did, e.getMessage());
            }
        }
        return Optional.empty();
    }

}
```

- CDI resolution: consumers inject unqualified `DIDResolver` and get
  `CompositeDIDResolver` (beats `NoOpDIDResolver @DefaultBean`)
- When no `@DIDMethod` resolvers are on the classpath, composite returns
  empty — equivalent to the current NoOp
- NoOpDIDResolver stays in identity/ as `@DefaultBean` — fallback when
  identity/ itself is absent (per convention: platform/ ships @DefaultBean
  implementations). Move to platform/ in this change.
- Exception handling: resolvers SHOULD honor the SPI contract (return empty,
  not throw). The try-catch is a safety net — if a resolver throws, the
  composite logs the failure and continues to the next resolver. This
  prevents a transient SCIM failure from blocking did:web resolution.
- Cache invalidation is NOT routed through the composite — each cached
  service (`ScimAgentLookup`) is invalidated directly by the ledger's
  `IdentityCacheInvalidator` via `Instance<>` injection (see §6).

### 4. Existing resolvers — annotation + priority change (identity/)

Remove `@Alternative`, add `@DIDMethod` and `@Priority`:

| Resolver | Before | After |
|----------|--------|-------|
| WebDIDResolver | `@Alternative @ApplicationScoped` | `@DIDMethod @ApplicationScoped @Priority(100)` |
| KeyDIDResolver | `@Alternative @ApplicationScoped` | `@DIDMethod @ApplicationScoped @Priority(100)` |

Method-specific resolvers at `@Priority(100)` run before the SCIM
fallback at `@Priority(1000)`. This ensures authoritative did:web and
did:key resolution takes precedence over synthetic SCIM-derived
documents.

Logic unchanged. Method signature gains the ignored `actorId` parameter.
No more `quarkus.arc.selected-alternatives` needed for resolver selection —
all available resolvers are active automatically by classpath presence.

### 5. ScimAgentLookup — shared SCIM client (identity/)

New `@ApplicationScoped` bean. Centralizes SCIM HTTP queries and caching
for agent resources. Used by both `ScimActorDIDProvider` and
`ScimDIDResolver`, eliminating duplicate SCIM queries and duplicate
HTTP client / JSON parsing / error handling code.

```java
@ApplicationScoped
public class ScimAgentLookup
        extends AbstractCachingIdentityProvider<ScimAgentResource> {

    ScimAgentLookup() { super(Duration.ZERO); /* CDI proxy */ }

    @Inject
    public ScimAgentLookup(IdentityConfig config) {
        super(Duration.ofMinutes(config.scim().cacheTtlMinutes()));
        // store endpoint, auth token, timeout, HTTPS requirement
    }

    @Override
    protected Optional<ScimAgentResource> loadContext(String actorId) {
        if (!isConfigured()) return Optional.empty();
        // SCIM HTTP query: GET /scim/v2/Agents?filter=externalId eq "{actorId}"
        // Parse extension DID + x509Certificates[] from response
        // Return ScimAgentResource(did, derCertificates)
    }

    public boolean isConfigured() {
        return scimEndpoint != null && !scimEndpoint.isBlank();
    }

    public void validate() {
        if (!isConfigured()) {
            throw new IllegalArgumentException(
                "casehub.identity.scim.endpoint must be configured");
        }
        if (requireHttps && !scimEndpoint.startsWith("https://")) {
            throw new IllegalArgumentException(
                "casehub.identity.scim.endpoint must use HTTPS, got: " + scimEndpoint);
        }
        if (authToken == null || authToken.isBlank()) {
            throw new IllegalArgumentException(
                "casehub.identity.scim.auth-token must not be blank");
        }
    }
}
```

`ScimAgentResource` gains certificate data:

```java
public record ScimAgentResource(String did, List<byte[]> derCertificates) {
    public ScimAgentResource {
        derCertificates = derCertificates == null ? List.of() : List.copyOf(derCertificates);
    }
}
```

`ScimActorDIDProvider` refactored to delegate:

```java
@Alternative
@ApplicationScoped
public class ScimActorDIDProvider implements ActorDIDProvider {

    @Inject ScimAgentLookup lookup;

    @Override
    public Optional<String> didFor(String actorId) {
        return lookup.get(actorId).map(ScimAgentResource::did);
    }

    @PostConstruct
    public void validateEndpoint() {
        lookup.validate();
    }
}
```

One SCIM HTTP call per actorId per cache TTL. Shared across both the DID
provider and the DID resolver.

### 6. ScimDIDResolver — new bean (identity/)

```java
@DIDMethod
@ApplicationScoped
@Priority(1000)
public class ScimDIDResolver implements DIDResolver {

    @Inject ScimAgentLookup lookup;

    @Override
    public Optional<DIDDocument> resolve(String actorId, String did) {
        if (actorId == null) return Optional.empty();
        if (!lookup.isConfigured()) return Optional.empty();
        try {
            Optional<ScimAgentResource> agent = lookup.get(actorId);
            if (agent.isEmpty()) return Optional.empty();

            ScimAgentResource resource = agent.get();
            if (!did.equals(resource.did())) {
                LOG.warnf("ScimDIDResolver: DID mismatch — SCIM has %s, "
                    + "request has %s for actorId %s", resource.did(), did, actorId);
                return Optional.empty();
            }

            List<VerificationMethod> vms = extractVerificationMethods(resource);
            return Optional.of(new DIDDocument(
                resource.did(), vms, List.of(actorId)));
        } catch (Exception e) {
            LOG.warnf("ScimDIDResolver: lookup failed for actorId %s: %s",
                actorId, e.getMessage());
            return Optional.empty();
        }
    }
}
```

**Graceful degradation:** When SCIM is not configured
(`IdentityConfig.ScimConfig.endpoint()` is empty),
`ScimAgentLookup.isConfigured()` returns false and
`ScimDIDResolver.resolve()` returns `Optional.empty()`. No `@PostConstruct`
validation — the resolver is always active on the classpath but inert
without SCIM configuration. This avoids `CreationException` in non-SCIM
deployments where `identity/` is on the classpath.

**@Priority(1000):** Runs after method-specific resolvers (`@Priority(100)`).
ScimDIDResolver is a fallback for actors whose DIDs cannot be resolved via
standard did:web/did:key methods. If WebDIDResolver resolves a `did:web:`
DID from its authoritative source, the composite returns that result and
ScimDIDResolver is never invoked. This prevents silent correctness
divergence between authoritative and synthetic documents.

**Key extraction from X.509 certificate — raw key bytes:**

```java
private List<VerificationMethod> extractVerificationMethods(
        ScimAgentResource resource) {
    List<VerificationMethod> vms = new ArrayList<>();
    List<byte[]> certs = resource.derCertificates();
    for (int i = 0; i < certs.size(); i++) {
        extractVerificationMethod(resource.did(), certs.get(i), i)
            .ifPresent(vms::add);
    }
    return List.copyOf(vms);
}

private Optional<VerificationMethod> extractVerificationMethod(
        String did, byte[] derBytes, int index) {
    X509Certificate cert = (X509Certificate) CertificateFactory
        .getInstance("X.509")
        .generateCertificate(new ByteArrayInputStream(derBytes));

    byte[] rawKeyBytes = extractRawKeyBytes(cert.getPublicKey());
    if (rawKeyBytes == null) return Optional.empty();

    String vmType = switch (cert.getPublicKey().getAlgorithm()) {
        case "Ed25519", "EdDSA" -> "Ed25519VerificationKey2020";
        case "EC" -> "EcdsaSecp256r1VerificationKey2019";
        default -> null;
    };
    if (vmType == null) return Optional.empty();

    return Optional.of(new VerificationMethod(
        did + "#scim-key-" + index, vmType, rawKeyBytes));
}

private byte[] extractRawKeyBytes(PublicKey publicKey) {
    byte[] spki = publicKey.getEncoded();
    return switch (publicKey.getAlgorithm()) {
        case "Ed25519", "EdDSA" -> {
            // SPKI = 12-byte ASN.1 prefix + 32-byte raw key
            byte[] raw = new byte[32];
            System.arraycopy(spki, spki.length - 32, raw, 0, 32);
            yield raw;
        }
        case "EC" -> {
            // Extract uncompressed EC point (0x04 || x || y)
            ECPublicKey ecKey = (ECPublicKey) publicKey;
            yield buildUncompressedPoint(
                ecKey.getW().getAffineX(), ecKey.getW().getAffineY());
        }
        default -> {
            LOG.warnf("Unsupported key algorithm for raw extraction: %s",
                publicKey.getAlgorithm());
            yield null;
        }
    };
}
```

Raw key byte extraction matches the convention established by
`JwtVCValidatorTest.issuerDocument()` (line 79-81) and expected by
`JwtVCValidator.verifyEd25519()` (which prepends the SPKI ASN.1 prefix
internally). RSA certificates are not supported — RSA has no compact raw
key format.

**Cache invalidation:** `ScimDIDResolver` has no separate cache — it
delegates to `ScimAgentLookup` which holds the shared cache.
The ledger's `IdentityCacheInvalidator` is updated to invalidate the
`ScimAgentLookup` cache. The existing `ActorDIDProvider instanceof
AbstractCachingIdentityProvider<?>` check is removed — after the
refactoring, `ScimActorDIDProvider` no longer extends
`AbstractCachingIdentityProvider` (it delegates to `ScimAgentLookup`),
so the check was always false.

```java
// IdentityCacheInvalidator (ledger) — updated
@Inject Instance<ScimAgentLookup> scimAgentLookup;

void onKeyRotated(@Observes AgentKeyRotatedEvent event) {
    scimAgentLookup.stream().findFirst()
        .ifPresent(lookup -> lookup.invalidate(event.actorId()));
}
```

`Instance<ScimAgentLookup>` handles the optional dependency cleanly —
when `identity/` is not on the classpath, the instance is empty.

### 7. NoOpDIDResolver — move to platform/

Per CLAUDE.md convention: "Every SPI in platform-api gets a @DefaultBean
implementation in platform." Currently NoOpDIDResolver lives in identity/.
Move to platform/ for consistency. Signature updated to include actorId.

### 8. Caller updates (identity/ — same commit)

**JwtVCValidator** (line 131):
```java
// Before
resolver.resolve(issuerDid)
// After
resolver.resolve(null, issuerDid)
```

**AgentIdentityVerificationService** (line 46):
```java
// Before
resolver.resolve(actorDid)
// After
resolver.resolve(actorId, actorDid)
```

### 9. Cross-repo — casehub-ledger (separate issue)

**ActorIdentityValidationEnricher** (line 109):
```java
// Before
resolver.resolve(entry.actorDid)
// After
resolver.resolve(entry.actorId, entry.actorDid)
```

**IdentityCacheInvalidator**: add `ScimAgentLookup` injection and
invalidation (see §6 cache invalidation).

Test resolvers (`TestDIDResolver`, `InjectableTestDIDResolver`): update
method signature.

File as casehubio/ledger issue — mechanical update, blocks on platform
SNAPSHOT publish.

## Resolution Flow Examples

**Actor DID in enricher** — `resolve("claude:reviewer@v1", "did:web:example.com:agents:reviewer")`:
- Composite → WebDIDResolver `@Priority(100)`: did:web prefix matches → HTTPS fetch → DIDDocument ✓

**Actor DID with no did:web hosting** — `resolve("claude:reviewer@v1", "did:web:scim-only.example.com:agents:reviewer")`:
- Composite → WebDIDResolver `@Priority(100)`: did:web fetch → HTTP 404 → empty
- Composite → KeyDIDResolver `@Priority(100)`: not did:key → empty
- Composite → ScimDIDResolver `@Priority(1000)`: actorId non-null, SCIM lookup → DIDDocument ✓

**Issuer DID in JwtVCValidator** — `resolve(null, "did:web:issuer.example.com")`:
- Composite → WebDIDResolver: did:web → HTTPS fetch → DIDDocument ✓
- (ScimDIDResolver never reached — actorId is null → empty)

**did:key DID** — `resolve("claude:bot@v1", "did:key:z6Mk...")`:
- Composite → WebDIDResolver: not did:web → empty
- Composite → KeyDIDResolver: did:key → decode → DIDDocument ✓

**SCIM transient failure** — `resolve("claude:reviewer@v1", "did:web:example.com:agents:reviewer")`:
- Composite → WebDIDResolver: did:web → HTTPS fetch → DIDDocument ✓
- (ScimDIDResolver never reached — WebDIDResolver succeeded)
- If WebDIDResolver also fails: composite catches exception, logs warning
- ScimDIDResolver: SCIM failure → exception caught by composite → empty
- Result: empty (both resolvers failed)

## Module Impact

| Module | Changes |
|--------|---------|
| `platform-api/` | DIDResolver signature + Javadoc, new @DIDMethod qualifier |
| `platform/` | NoOpDIDResolver moved here |
| `identity/` | CompositeDIDResolver (new), ScimDIDResolver (new), ScimAgentLookup (new), ScimAgentResource extended, ScimActorDIDProvider refactored to delegate, Web/KeyDIDResolver annotations + @Priority, JwtVCValidator + AgentIdentityVerificationService call sites |
| `scim/` | No changes |

## Out of Scope

- SCIM filter by DID extension attribute — fragile, SCIM-server-dependent.
  Tracked: casehubio/platform#127
- Composite resolver pattern for `ActorDIDProvider` — only one provider is
  active per deployment; no multi-provider use case exists.
  Tracked: casehubio/platform#128
