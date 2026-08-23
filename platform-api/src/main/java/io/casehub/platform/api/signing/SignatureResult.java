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
    public SignatureResult(final byte[] signature, final byte[] publicKey) {
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
    public static String computeKeyRef(final byte[] publicKeyEncoded) {
        Objects.requireNonNull(publicKeyEncoded, "publicKeyEncoded must not be null");
        try {
            final byte[] hash = MessageDigest.getInstance("SHA-256").digest(publicKeyEncoded);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (final java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    @Override
    public boolean equals(final Object o) {
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
