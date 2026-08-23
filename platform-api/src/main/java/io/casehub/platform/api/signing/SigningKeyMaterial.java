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
    public SigningKeyMaterial(final byte[] publicKey) {
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
    public boolean equals(final Object o) {
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
