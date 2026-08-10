# KeyDIDResolver Multicodec + Composite ActorDIDProvider

**Issues:** #130, #128
**Module:** platform-api, identity
**Date:** 2026-06-30

---

## Problem

Two architectural issues in the identity module:

1. **KeyDIDResolver assumes Ed25519 for all did:key DIDs (#130).** The multicodec varint prefix is stripped but never inspected — raw key bytes are always wrapped in Ed25519 SPKI. For non-Ed25519 keys (P-256, secp256k1), this produces invalid SPKI that would cause `KEY_MISMATCH` in identity verification.

2. **ActorDIDProvider uses @Alternative instead of composite (#128).** `DIDResolver` uses `@DIDMethod` qualifier → `CompositeDIDResolver` iterating by `@Priority`. `ActorDIDProvider` uses `@Alternative` — only one implementation active per deployment. Architectural inconsistency that prevents config + SCIM coexistence.

## Critical Constraint: SPKI Byte Equality

`AgentIdentityVerificationService` and `ActorIdentityValidationEnricher` compare `VerificationMethod.publicKeyBytes()` via `Arrays.equals`. All SPKI output MUST use canonical (uncompressed) encoding — a compressed P-256 SPKI (59 bytes) vs uncompressed (91 bytes from `PublicKey.getEncoded()`) would cause false `KEY_MISMATCH` for identical keys.

---

## Design

### 1. platform-api: VerificationMethodType constants

New class in `io.casehub.platform.api.identity`:

```java
public final class VerificationMethodType {
    public static final String ED25519 = "Ed25519VerificationKey2020";
    public static final String P256 = "EcdsaSecp256r1VerificationKey2019";
    private VerificationMethodType() {}
}
```

Not an enum — `VerificationMethod.type` stays `String` for extensibility (external DID documents may use types the platform doesn't know). Both `ScimDIDResolver` and `KeyDIDResolver` reference these constants instead of hardcoding strings.

### 2. platform-api: @ActorDIDSource qualifier

```java
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({TYPE, METHOD, FIELD, PARAMETER})
public @interface ActorDIDSource {}
```

Mirrors `@DIDMethod`. Individual `ActorDIDProvider` implementations annotate with this; the composite injects `@ActorDIDSource Instance<ActorDIDProvider>`.

### 3. platform-api: ActorDIDProvider.invalidate()

```java
public interface ActorDIDProvider {
    Optional<String> didFor(String actorId);
    default void invalidate(String actorId) {}
}
```

No-op default. Composite propagates to all children. Eliminates `instanceof` checks in downstream consumers.

### 4. identity: KeyDIDResolver multicodec dispatch

#### Varint decoder

Private static method in `KeyDIDResolver`. Decodes unsigned varint, returns `int[]{code, bytesConsumed}` or `null` if malformed. 4-byte limit (all public key multicodec codes fit in 21 bits).

#### MulticodecKeyType enum (package-private)

```java
enum MulticodecKeyType {
    ED25519(0xed, VerificationMethodType.ED25519, 32),
    P256(0x1200, VerificationMethodType.P256, 33);

    final int code;
    final String vmType;
    final int rawKeyLength;

    static Optional<MulticodecKeyType> fromCode(int code) { ... }
    byte[] toSpki(byte[] rawKey) { ... }
}
```

- `rawKeyLength` — expected raw key byte count after stripping varint. Mismatch → empty.
- `toSpki()` per algorithm:
  - **Ed25519:** manual 12-byte ASN.1 prefix + 32 raw bytes = 44-byte SPKI. Only one encoding exists — no normalization needed.
  - **P-256:** decompress compressed SEC1 point (33 bytes: prefix `02`/`03` + 32-byte X) to `ECPoint(x, y)`:
    1. Compute `Y² = X³ + aX + b (mod p)` using P-256 curve constants.
    2. Compute candidate `Y = (Y²)^((p+1)/4) mod p` (P-256 has p ≡ 3 mod 4).
    3. **Sign selection:** if prefix is `0x02` and Y is odd, or prefix is `0x03` and Y is even, use `Y = p - Y` (the conjugate root). This selects the correct Y based on the SEC1 compression prefix — without it, ~50% of keys decompress to the conjugate point, producing valid but wrong SPKI.
    4. Construct `ECPublicKeySpec(new ECPoint(X, Y), p256Params)`, generate `PublicKey` via `KeyFactory.getInstance("EC")`, return `getEncoded()` for canonical uncompressed SPKI (91 bytes).
  - **secp256k1:** deferred — JDK 15+ removed secp256k1 from SunEC (JEP 339 aftermath), and the platform has no BouncyCastle dependency. Manual curve parameter hardcoding is possible but adds complexity for a curve with no current deployment use case. Tracked as a separate issue for when a concrete need arises.

#### Revised resolve()

```java
public Optional<DIDDocument> resolve(String actorId, String did) {
    if (did == null || !did.startsWith(DID_KEY_PREFIX)) return Optional.empty();
    try {
        String keyPart = did.substring(DID_KEY_PREFIX.length());
        if (!keyPart.startsWith("z")) return Optional.empty();
        byte[] multicodec = Base64.getUrlDecoder().decode(keyPart.substring(1));

        int[] varint = decodeVarint(multicodec);
        if (varint == null) return Optional.empty();

        Optional<MulticodecKeyType> keyType = MulticodecKeyType.fromCode(varint[0]);
        if (keyType.isEmpty()) return Optional.empty();

        byte[] rawKey = Arrays.copyOfRange(multicodec, varint[1], multicodec.length);
        if (rawKey.length != keyType.get().rawKeyLength) return Optional.empty();

        byte[] spki = keyType.get().toSpki(rawKey);
        String vmId = did + "#" + keyPart;
        var vm = new VerificationMethod(vmId, keyType.get().vmType, spki);
        var aka = actorId != null ? List.of(actorId) : List.<String>of();
        return Optional.of(new DIDDocument(did, List.of(vm), aka));
    } catch (Exception e) {
        LOG.debugf("KeyDIDResolver: failed to decode %s: %s", did, e.getMessage());
        return Optional.empty();
    }
}
```

### 5. identity: CompositeActorDIDProvider

```java
@ApplicationScoped
public class CompositeActorDIDProvider implements ActorDIDProvider {

    private final List<ActorDIDProvider> providers;

    @Inject
    public CompositeActorDIDProvider(@ActorDIDSource Instance<ActorDIDProvider> sources) {
        this.providers = toSortedList(sources);
    }

    CompositeActorDIDProvider(List<ActorDIDProvider> providers) {
        this.providers = providers;
    }

    @Override
    public Optional<String> didFor(String actorId) {
        for (ActorDIDProvider p : providers) {
            try {
                Optional<String> result = p.didFor(actorId);
                if (result.isPresent()) return result;
            } catch (Exception e) {
                LOG.warnf("Provider %s failed for actorId %s: %s",
                    p.getClass().getSimpleName(), actorId, e.getMessage());
            }
        }
        return Optional.empty();
    }

    @Override
    public void invalidate(String actorId) {
        for (ActorDIDProvider p : providers) {
            try {
                p.invalidate(actorId);
            } catch (Exception e) {
                LOG.warnf("Provider %s invalidate failed for actorId %s: %s",
                    p.getClass().getSimpleName(), actorId, e.getMessage());
            }
        }
    }
}
```

Priority sorting logic (currently duplicated in `CompositeDIDResolver`) extracted to `io.casehub.platform.identity.CdiPriorityUtils.toSortedList(Instance<T>)` — package-private utility in the identity module. Both composites delegate to it.

**CdiPriorityUtils details:**
- Identical sorting logic to `CompositeDIDResolver.toSortedList()` / `priorityOf()` — reads `@Priority` via `InjectableBean.getPriority()`, falls back to `Integer.MAX_VALUE` for non-`InjectableBean` instances (e.g., test doubles injected via the `List<>` constructor).
- Package-private visibility is correct — both composites (`CompositeDIDResolver`, `CompositeActorDIDProvider`) are in the same `io.casehub.platform.identity` package. If a third composite is needed in a different package, promote to `public` then — not preemptively.

### 6. identity: Provider annotation changes

| Provider | Before | After |
|----------|--------|-------|
| `ConfiguredActorDIDProvider` | `@ApplicationScoped` | `@ApplicationScoped @ActorDIDSource @Priority(100)` |
| `ScimActorDIDProvider` | `@ApplicationScoped @Alternative` | `@ApplicationScoped @ActorDIDSource @Priority(200)` |
| `NoOpActorDIDProvider` | `@ApplicationScoped @DefaultBean` (identity/) | `@ApplicationScoped @DefaultBean` (platform/) — mirrors NoOpDIDResolver convention |

**Priority rationale:** lower value = tried first. Config (100) is cheap local lookup, tried first. SCIM (200) is network, tried second. Config wins when both have a DID for the same actor.

### 7. identity: ScimActorDIDProvider activation fix

Remove `@Alternative` — bean is always instantiated by the composite.

Remove `@PostConstruct validateEndpoint()` — ScimAgentLookup already handles unconfigured state gracefully (`isConfigured()` returns false → `get()` returns empty → `didFor()` returns empty). No crash.

`ScimActorDIDProvider.invalidate()` overrides to delegate to `lookup.invalidate(actorId)`.

### 8. identity: ScimDIDResolver update

Replace hardcoded type strings with `VerificationMethodType.ED25519` and `VerificationMethodType.P256`. Behavioral no-op — same strings.

---

## Test Plan

### KeyDIDResolver

| Test | Assertion |
|------|-----------|
| Ed25519 did:key → SPKI roundtrip | 44-byte SPKI, loadable via `KeyFactory.getInstance("Ed25519")` (existing test, keep) |
| P-256 did:key → canonical SPKI (0x02 prefix) | `assertArrayEquals(originalKey.getEncoded(), resolvedSpki)` — roundtrip byte equality, not just loadability |
| P-256 did:key → canonical SPKI (0x03 prefix) | known test vector with 0x03-prefix compressed key; asserts sign selection produces correct Y |
| Unknown multicodec code → empty | |
| Raw key length mismatch → empty | |
| Malformed varint → empty | |
| Null/non-key DID → empty | existing tests, keep |
| did:key resolve includes actorId in alsoKnownAs | `DIDDocument.alsoKnownAs().contains(actorId)` |
| did:key resolve with null actorId → empty alsoKnownAs | VC validation path (JwtVCValidator passes null) |

### Identity verification with did:key

| Test | Assertion |
|------|-----------|
| did:key actor → `verifyIdentityBinding()` → VALID | actorId in alsoKnownAs + key match → VALID |
| did:key actor → wrong key → KEY_MISMATCH | actorId matches but key doesn't |

### CompositeActorDIDProvider

| Test | Assertion |
|------|-----------|
| Empty providers → empty | |
| Single provider → delegates | |
| Multiple → first non-empty | |
| Provider throws → catches, continues | |
| All throw → empty | |
| invalidate() propagates to all children | |
| Priority ordering — Config(100) before SCIM(200) | Config result returned when both have DID for same actorId |

### ScimActorDIDProvider

| Test | Assertion |
|------|-----------|
| Unconfigured SCIM → didFor() returns empty | no crash, no exception |

---

## Downstream Issues to File

1. **casehubio/ledger** — `IdentityCacheInvalidator`: replace `instanceof ScimActorDIDProvider` with `actorDIDProvider.invalidate(actorId)`. Scale: XS. Complexity: Low. **Note:** this supersedes the SCIM DID resolver spec's §6 approach (`Instance<ScimAgentLookup>` direct injection). The SPI-level `invalidate()` is architecturally cleaner — the ledger calls the SPI method; the composite propagates to all children. If the SCIM spec's ledger changes were already implemented, they need revision to use this approach instead.

## Out of Scope

- did:key base58btc vs base64url encoding (pre-existing spec deviation). Tracked: #131
- ScimAgentLookup HTTPS validation consolidation (with PostConstruct removed from ScimActorDIDProvider, HTTPS enforcement should move to ScimAgentLookup). Tracked: #132
- secp256k1 did:key support (JDK 15+ removed secp256k1 from SunEC; no BouncyCastle dependency). Tracked: #133
