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
    default Optional<SigningKeyMaterial> keyMaterial(final String actorId) {
        return sign(actorId, new byte[]{0})
                .map(r -> new SigningKeyMaterial(r.publicKey()));
    }
}
