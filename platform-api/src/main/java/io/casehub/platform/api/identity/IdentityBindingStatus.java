package io.casehub.platform.api.identity;

/**
 * The result of binding an actor's cryptographic identity to a DID and Verifiable Credential.
 *
 * <p>{@link #VALID} is the only positive signal; all other values indicate
 * a binding that could not be verified or that revealed a mismatch.
 */
public enum IdentityBindingStatus {
    /** The DID resolved, the VC is current, and the signing key matches the actor's key. */
    VALID,
    /** No agent signature was present — binding check was not attempted. */
    UNSIGNED,
    /** The DID document could not be resolved from the registry or network. */
    DID_UNRESOLVABLE,
    /** The actor identity claimed in the VC does not match the {@code actorId}. */
    IDENTITY_MISMATCH,
    /** The public key in the DID document does not match the key used to sign. */
    KEY_MISMATCH,
    /** The Verifiable Credential has passed its {@code expirationDate}. */
    CREDENTIAL_EXPIRED,
    /** The Verifiable Credential signature or structure is invalid. */
    CREDENTIAL_INVALID
}
