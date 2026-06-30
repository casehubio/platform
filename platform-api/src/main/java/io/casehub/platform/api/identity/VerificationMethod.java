package io.casehub.platform.api.identity;

import java.util.Arrays;

/**
 * A verification method entry from a DID document.
 *
 * <p>Represents a single public key associated with a DID, identified by its
 * {@code id} fragment and key {@code type} (e.g. {@code Ed25519VerificationKey2020}).
 *
 * <p>{@code publicKeyBytes} stores SPKI (X.509 SubjectPublicKeyInfo) DER-encoded bytes.
 * For Ed25519 this is 44 bytes (12-byte ASN.1 header + 32-byte key); for EC P-256 this
 * is 91 bytes. SPKI is the format returned by {@code PublicKey.getEncoded()} and consumed
 * by {@code KeyFactory.generatePublic(new X509EncodedKeySpec(bytes))}.
 *
 * <p>{@code publicKeyBytes} is defensively copied on construction and on access —
 * callers cannot mutate the stored key material.
 */
public record VerificationMethod(String id, String type, byte[] publicKeyBytes) {

    /** Defensively copies the supplied key bytes on the way in. */
    public VerificationMethod {
        publicKeyBytes = publicKeyBytes == null ? new byte[0] : publicKeyBytes.clone();
    }

    /** Returns a defensive copy so callers cannot mutate internal key material. */
    @Override
    public byte[] publicKeyBytes() {
        return publicKeyBytes.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VerificationMethod other)) return false;
        return java.util.Objects.equals(id, other.id)
                && java.util.Objects.equals(type, other.type)
                && Arrays.equals(publicKeyBytes, other.publicKeyBytes);
    }

    @Override
    public int hashCode() {
        int result = java.util.Objects.hash(id, type);
        result = 31 * result + Arrays.hashCode(publicKeyBytes);
        return result;
    }

    @Override
    public String toString() {
        return "VerificationMethod[id=" + id + ", type=" + type
                + ", publicKeyBytes=<" + publicKeyBytes.length + " bytes>]";
    }
}
