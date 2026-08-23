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
