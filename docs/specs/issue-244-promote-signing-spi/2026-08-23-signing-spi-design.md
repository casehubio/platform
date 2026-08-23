# Signing SPI Extraction — Design Spec

**Issue:** casehubio/platform#244
**Branch:** issue-244-promote-signing-spi
**Date:** 2026-08-23

## Problem

Qhorus needs cryptographic signing for signed agent cards (qhorus#E6). The signing
stack (Ed25519, HMAC, AWS KMS, GCP KMS, Azure KeyVault, Vault Transit) lives in
`casehub-ledger-signing`. Qhorus can't depend on ledger — circular dependency.

## Solution

Extract a signing SPI to `casehub-platform-api`. Keep implementations in
`casehub-ledger-signing`. Qhorus depends on platform-api for the SPI;
implementations are provided at deployment time.

**Capability ownership:** This extraction moves the raw signing primitive from
ledger to platform. Ledger retains the trust framework (key rotation, compromise
detection, suspect events, `AgentSignatureVerificationService`). The distinction:
platform owns "sign these bytes" / "verify this signature"; ledger owns "is this
signed entry trustworthy given key lifecycle events?"

Update ARC42STORIES.MD §9 and capability-ownership.md at implementation time to
reflect this split.

## Design decisions vs issue scope

Issue #244 proposed three deliverables. This design diverges on all three with
rationale:

| Issue #244 proposed | This design delivers | Rationale |
|---------------------|---------------------|-----------|
| `SigningService` interface (sign, verify) | `SigningProvider` (SPI, sign only) + `SignatureVerifier` (static utility) | Signing needs backends (SPI pattern); verification is stateless (utility pattern). Platform naming uses agent nouns, not "Service." |
| `VerificationMethod` enum (Ed25519, HMAC, etc.) | No enum — algorithm-transparent detection from key bytes | Callers don't need crypto knowledge; key rotation changes algorithms transparently; forward-compatible with ML-DSA. Name collides with identity module's `VerificationMethod` record. |
| HMAC in signing stack | Asymmetric only | HMAC is symmetric — no public key, different result shape. The entire existing stack is asymmetric. |

## Architecture

Two separate concerns, two separate patterns:

| Concern | Pattern | Reason |
|---------|---------|--------|
| Signing | SPI with pluggable backends (`SigningProvider`) | Requires private key access via KMS/HSM/local key |
| Verification | Static utility (`SignatureVerifier`) | Stateless — needs only public key + JCA |

No algorithm enum. The existing algorithm-transparent pattern (detect algorithm
from key bytes via X.509 SubjectPublicKeyInfo OID) is superior:
- Callers don't need crypto knowledge
- Key rotation can change algorithms transparently
- Forward-compatible with new algorithms (ML-DSA)

No HMAC. Symmetric signing is fundamentally different from asymmetric and would
change the result record shape (HMAC has no public key).

### Package

`io.casehub.platform.api.signing` — new package in platform-api, alongside
`.credentials`, `.identity`, `.governance`.

### Types in platform-api (5 types)

#### `SigningProvider` — SPI interface

```java
package io.casehub.platform.api.signing;

import java.util.Optional;

/**
 * SPI: performs cryptographic signing on behalf of a given identity,
 * delegating to whatever backend (KMS, HSM, local key) is deployed.
 *
 * <p>Return {@link Optional#empty()} for identities that do not participate
 * in signing — their artifacts will be produced unsigned.
 *
 * <p><strong>Error handling:</strong> throw {@link RuntimeException} for
 * transient failures (network, auth). Callers decide how to handle transient
 * errors. Do NOT return empty to signal an error — reserve empty for
 * "identity not configured for signing."
 *
 * <p><strong>Null handling:</strong> implementations MUST throw
 * {@link NullPointerException} if {@code actorId} or {@code data} is null.
 *
 * <p><strong>Thread safety:</strong> implementations must be safe for
 * concurrent calls — {@code @ApplicationScoped} beans are shared across
 * request threads.
 */
public interface SigningProvider {

    /**
     * Sign the given data on behalf of the specified identity.
     *
     * @param actorId the signer identity (e.g. {@code "claude:reviewer@v1"},
     *        {@code "qhorus:service"}, {@code "org:root"})
     * @param data    the bytes to sign
     * @return signed result, or empty if this identity does not sign
     * @throws NullPointerException if actorId or data is null
     */
    Optional<SignatureResult> sign(String actorId, byte[] data);

    /**
     * Retrieve key material for a signing identity.
     *
     * <p>Default implementation calls {@link #sign} with a single-byte
     * sentinel and extracts the key material — this triggers a real signing
     * operation. Cloud KMS implementations (AWS KMS, GCP Cloud KMS, Azure
     * Key Vault) <strong>MUST</strong> override this method to extract the
     * public key from their cached context without triggering a paid sign
     * API call. Failure to override will cause KMS validation errors (these
     * services reject minimal payloads).
     *
     * @param actorId the signer identity
     * @return key material, or empty if this identity does not sign
     * @throws NullPointerException if actorId is null
     */
    default Optional<SigningKeyMaterial> keyMaterial(String actorId) {
        return sign(actorId, new byte[]{0})
                .map(r -> new SigningKeyMaterial(r.publicKey()));
    }
}
```

#### `SignatureResult` — signing output record

```java
package io.casehub.platform.api.signing;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/**
 * The result of a signing operation: raw signature bytes and the signer's
 * public key. The content-addressable key reference ({@code keyRef}) is
 * computed from the public key — callers cannot supply an inconsistent value.
 *
 * <p>{@code publicKey} is X.509 SubjectPublicKeyInfo DER-encoded — the format
 * returned by {@code PublicKey.getEncoded()} and consumed by
 * {@code KeyFactory.generatePublic(new X509EncodedKeySpec(bytes))}.
 *
 * <p>Byte arrays are defensively copied on construction and on access.
 */
public record SignatureResult(byte[] signature, byte[] publicKey, String keyRef) {

    /**
     * Construct from signature and public key. {@code keyRef} is computed
     * as {@code Base64URL(SHA-256(publicKey))}.
     */
    public SignatureResult(byte[] signature, byte[] publicKey) {
        this(signature, publicKey, computeKeyRef(publicKey));
    }

    public SignatureResult {
        Objects.requireNonNull(signature, "signature must not be null");
        Objects.requireNonNull(publicKey, "publicKey must not be null");
        Objects.requireNonNull(keyRef, "keyRef must not be null");
        if (signature.length == 0) throw new IllegalArgumentException("signature must not be empty");
        if (publicKey.length == 0) throw new IllegalArgumentException("publicKey must not be empty");
        signature = signature.clone();
        publicKey = publicKey.clone();
    }

    @Override
    public byte[] signature() { return signature.clone(); }

    @Override
    public byte[] publicKey() { return publicKey.clone(); }

    /**
     * Compute a content-addressable key reference for a public key.
     * {@code keyRef = Base64URL(SHA-256(publicKeyEncoded))}, no padding.
     *
     * @param publicKeyEncoded X.509 SubjectPublicKeyInfo DER-encoded public key
     * @return Base64URL-encoded SHA-256 hash
     * @throws NullPointerException if publicKeyEncoded is null
     */
    public static String computeKeyRef(byte[] publicKeyEncoded) {
        Objects.requireNonNull(publicKeyEncoded, "publicKeyEncoded must not be null");
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(publicKeyEncoded);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SignatureResult other)) return false;
        return Objects.equals(keyRef, other.keyRef)
                && Arrays.equals(signature, other.signature)
                && Arrays.equals(publicKey, other.publicKey);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(keyRef);
        result = 31 * result + Arrays.hashCode(signature);
        result = 31 * result + Arrays.hashCode(publicKey);
        return result;
    }

    @Override
    public String toString() {
        return "SignatureResult[keyRef=" + keyRef
                + ", signature=<" + signature.length + " bytes>"
                + ", publicKey=<" + publicKey.length + " bytes>]";
    }
}
```

#### `SigningKeyMaterial` — key-only retrieval record

```java
package io.casehub.platform.api.signing;

import java.util.Arrays;
import java.util.Objects;

/**
 * Public key material for a signing identity — retrieved via
 * {@link SigningProvider#keyMaterial(String)}. The content-addressable
 * key reference ({@code keyRef}) is computed from the public key.
 *
 * @param publicKey X.509 SubjectPublicKeyInfo DER-encoded public key
 * @param keyRef    Base64URL-encoded SHA-256 hash of {@code publicKey} (computed)
 */
public record SigningKeyMaterial(byte[] publicKey, String keyRef) {

    /**
     * Construct from public key. {@code keyRef} is computed
     * as {@code Base64URL(SHA-256(publicKey))}.
     */
    public SigningKeyMaterial(byte[] publicKey) {
        this(publicKey, SignatureResult.computeKeyRef(publicKey));
    }

    public SigningKeyMaterial {
        Objects.requireNonNull(publicKey, "publicKey must not be null");
        Objects.requireNonNull(keyRef, "keyRef must not be null");
        if (publicKey.length == 0) throw new IllegalArgumentException("publicKey must not be empty");
        publicKey = publicKey.clone();
    }

    @Override
    public byte[] publicKey() { return publicKey.clone(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SigningKeyMaterial other)) return false;
        return Objects.equals(keyRef, other.keyRef)
                && Arrays.equals(publicKey, other.publicKey);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(keyRef);
        result = 31 * result + Arrays.hashCode(publicKey);
        return result;
    }

    @Override
    public String toString() {
        return "SigningKeyMaterial[keyRef=" + keyRef
                + ", publicKey=<" + publicKey.length + " bytes>]";
    }
}
```

#### `VerificationOutcome` — verification result enum

```java
package io.casehub.platform.api.signing;

/**
 * Outcome of a signature verification operation.
 *
 * <p>Callers who want binary semantics check {@code outcome == VALID}.
 * Callers debugging verification failures get actionable diagnostic
 * information without catching exceptions.
 */
public enum VerificationOutcome {

    /** Signature verified successfully. */
    VALID,

    /** Public key algorithm is not in the supported list. */
    UNSUPPORTED_ALGORITHM,

    /** Public key bytes do not parse as X.509 SubjectPublicKeyInfo DER. */
    MALFORMED_KEY,

    /** Cryptographic verification returned false — signature does not match data. */
    SIGNATURE_MISMATCH,

    /** One or more inputs (data, signature, publicKey) was null or empty. */
    INVALID_INPUT
}
```

#### `SignatureVerifier` — static verification utility

```java
package io.casehub.platform.api.signing;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.List;

/**
 * Algorithm-transparent signature verification utility.
 *
 * <p>Detects the signing algorithm from the public key's X.509
 * SubjectPublicKeyInfo DER metadata — callers never specify an algorithm.
 * Supports Ed25519, EC (P-256, P-384, P-521 via ECDSA), and ML-DSA
 * (when BouncyCastle >= 1.79 is present).
 *
 * <p>The algorithm list is tried in order — Ed25519 first (current default),
 * then EC, then ML-DSA variants. Placing the most common algorithm first
 * minimizes trial-and-error exception overhead under load.
 *
 * <p>Never throws. Returns a {@link VerificationOutcome} that indicates
 * success or the specific failure mode.
 *
 * <p>Composes naturally with the identity module's {@code VerificationMethod}
 * record, which stores public keys in the same SPKI format:
 * <pre>{@code
 * VerificationMethod vm = didDocument.verificationMethods().get(0);
 * VerificationOutcome outcome = SignatureVerifier.verify(
 *         data, signature, vm.publicKeyBytes());
 * }</pre>
 * The identity module's {@code AgentIdentityVerificationService} handles
 * key-matching (is this key in the DID document?); {@code SignatureVerifier}
 * handles signature verification (is this signature valid for this key?).
 */
public final class SignatureVerifier {

    private static final List<String> SUPPORTED_ALGORITHMS =
            List.of("Ed25519", "EC", "ML-DSA-44", "ML-DSA-65", "ML-DSA-87");

    private SignatureVerifier() {}

    /**
     * Verify a signature against the given data and public key.
     *
     * @param data      the original signed bytes
     * @param signature the signature to verify
     * @param publicKey X.509 SubjectPublicKeyInfo DER-encoded public key
     * @return verification outcome — check {@code == VALID} for binary semantics
     */
    public static VerificationOutcome verify(byte[] data, byte[] signature, byte[] publicKey) {
        if (data == null || signature == null || publicKey == null
                || data.length == 0 || signature.length == 0 || publicKey.length == 0) {
            return VerificationOutcome.INVALID_INPUT;
        }
        final PublicKey pub;
        try {
            pub = loadPublicKey(publicKey);
        } catch (java.security.InvalidKeyException e) {
            return VerificationOutcome.UNSUPPORTED_ALGORITHM;
        } catch (Exception e) {
            return VerificationOutcome.MALFORMED_KEY;
        }
        try {
            Signature sig = Signature.getInstance(signatureAlgorithm(pub));
            sig.initVerify(pub);
            sig.update(data);
            return sig.verify(signature)
                    ? VerificationOutcome.VALID
                    : VerificationOutcome.SIGNATURE_MISMATCH;
        } catch (IllegalArgumentException e) {
            return VerificationOutcome.UNSUPPORTED_ALGORITHM;
        } catch (Exception e) {
            return VerificationOutcome.MALFORMED_KEY;
        }
    }

    private static PublicKey loadPublicKey(byte[] encoded)
            throws java.security.InvalidKeyException {
        X509EncodedKeySpec spec = new X509EncodedKeySpec(encoded);
        for (String algo : SUPPORTED_ALGORITHMS) {
            try {
                return KeyFactory.getInstance(algo).generatePublic(spec);
            } catch (java.security.NoSuchAlgorithmException
                    | java.security.spec.InvalidKeySpecException ignored) {
            }
        }
        throw new java.security.InvalidKeyException(
                "Public key bytes do not match any supported algorithm");
    }

    private static String signatureAlgorithm(java.security.Key key) {
        if (!"EC".equals(key.getAlgorithm())) {
            return key.getAlgorithm();
        }
        ECKey ec = (ECKey) key;
        return switch (ec.getParams().getOrder().bitLength()) {
            case 256 -> "SHA256withECDSA";
            case 384 -> "SHA384withECDSA";
            case 521 -> "SHA512withECDSA";
            default -> throw new IllegalArgumentException(
                    "Unsupported EC curve order: " + ec.getParams().getOrder().bitLength());
        };
    }
}
```

### @DefaultBean in platform/ module

```java
package io.casehub.platform.signing;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.arc.DefaultBean;
import org.jboss.logging.Logger;

import io.casehub.platform.api.signing.SignatureResult;
import io.casehub.platform.api.signing.SigningProvider;

/**
 * No-op signing provider — returns unsigned results with a per-actor WARN.
 *
 * <p>NOTE: The null guard on {@code actorId} is intentional — the spec's
 * version omits it, but {@code ConcurrentHashMap.newKeySet()} rejects null
 * keys with NPE. This implementation guards null actorId to match the SPI
 * contract (throw NPE with a clear message) rather than letting the CHM
 * internals throw an obscure NPE.
 */
@DefaultBean
@ApplicationScoped
public class NoOpSigningProvider implements SigningProvider {

    private static final Logger LOG = Logger.getLogger(NoOpSigningProvider.class);
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    @Override
    public Optional<SignatureResult> sign(final String actorId, final byte[] data) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(data, "data must not be null");
        if (warned.add(actorId)) {
            LOG.warnf("No signing backend configured — returning unsigned for actor %s. "
                    + "Add a signing backend to enable cryptographic signing.", actorId);
        }
        return Optional.empty();
    }
}
```

Displaced when any `@Alternative @Priority` backend is on the classpath.
The `warned` set is intentionally unbounded — this bean is expected to be
displaced in production. In a misconfigured deployment where it stays active,
the per-actorId deduplication prevents log flooding at the cost of proportional
memory to the number of distinct actors.

## Testing

**Unit tests for `SignatureVerifier`:**
- Ed25519 sign-then-verify round trip (JCA `KeyPairGenerator`) → VALID
- EC P-256 sign-then-verify round trip → VALID
- Tampered data → SIGNATURE_MISMATCH
- Malformed public key → MALFORMED_KEY
- Null/empty inputs → INVALID_INPUT
- Unsupported algorithm key bytes → UNSUPPORTED_ALGORITHM

**Unit tests for `SignatureResult`:**
- `computeKeyRef` determinism (same key → same ref)
- `computeKeyRef` known-answer test (hardcoded expected value)
- `computeKeyRef` null guard
- Defensive copy on construction and access (mutating input doesn't affect record)
- equals/hashCode correctness with cloned byte arrays
- Constructor rejects null and empty arrays
- 2-param constructor computes keyRef correctly

**Unit tests for `SigningKeyMaterial`:**
- 1-param constructor computes keyRef correctly
- equals/hashCode correctness with cloned byte arrays
- toString redacts key bytes

**Unit tests for `SigningProvider` default method:**
- `keyMaterial()` positive path with stub that returns a real `SignatureResult`

**CDI test for `NoOpSigningProvider`:**
- Returns `Optional.empty()`
- Throws NPE on null actorId
- Throws NPE on null data
- WARN logged on first invocation per actorId (log capture)
- No WARN on subsequent invocations for same actorId

No integration tests with real KMS backends — that's a ledger concern.

## Out of scope — post-merge follow-up

File these as GitHub issues when this PR lands:

1. **casehubio/ledger** — `AgentSigner extends SigningProvider` migration
   (or adapter). Architectural decision deferred to ledger team.
2. **casehubio/platform** — `JwtVCValidator` delegates to `SignatureVerifier`.
   Security improvement: derives algorithm from key bytes (SPKI OID) rather
   than JWT `alg` header (attacker-influenced field).

## VerificationMethod naming — no conflict

The issue originally proposed a "VerificationMethod enum." This design does not
include one — the algorithm-transparent pattern is superior. The existing
`VerificationMethod` record and `VerificationMethodType` constants in the identity
package are unaffected. They model DID document entries (public key + type), not
signing algorithms.

The signing and identity packages compose cleanly: `VerificationMethod.publicKeyBytes()`
produces SPKI-encoded bytes that `SignatureVerifier.verify()` consumes directly.
The identity module handles key-matching (is this key in the DID document?);
`SignatureVerifier` handles signature verification (is this signature valid for
this key?).

## References

- `AgentSigner.java` (ledger/runtime) — existing signing SPI being extracted
- `AgentSignature.java` (ledger/runtime) — existing result type
- `AgentCryptographicVerifier.java` (ledger/runtime) — verification logic being extracted
- `SignatureAlgorithms.java` (ledger/runtime) — EC curve mapping being extracted
- `CredentialResolver.java` (platform-api) — SPI placement precedent
- `VerificationMethod.java` (platform-api/identity) — naming conflict analysis, SPKI composition
- `IdentityVerificationResult.java` (platform-api/identity) — enum verification result pattern
- `CredentialValidationResult.java` (platform-api/identity) — enum verification result pattern
- `NoOpCaseMemoryStore.java` (platform/) — @DefaultBean no-op pattern
- `JwtVCValidator.java` (identity/) — duplication candidate for post-merge follow-up
- casehubio/platform#244 — parent issue
- Garden: GE-Platform-CredentialResolver — SPI pattern precedent
- Garden: GE-Platform-AgentCredentialValidator — verification SPI pattern
- Garden: GE-Platform-API-Constraints — zero-dependency boundary rules
