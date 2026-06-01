package io.casehub.platform.api.identity;

/**
 * Read-path result from identity verification.
 * Verifies that a stored agent public key matches a verification method
 * in the current DID document for the actor's DID.
 */
public enum IdentityVerificationResult {
    VALID,              // public key matches DID document; alsoKnownAs confirmed
    UNVERIFIABLE,       // no actorDid — actor has no DID binding
    UNSIGNED,           // no agentPublicKey — nothing to cross-check
    DID_UNRESOLVABLE,   // resolver returned empty (network failure or DID not found)
    IDENTITY_MISMATCH,  // DID document alsoKnownAs does not include actorId
    KEY_MISMATCH        // entry key no longer in DID document (key rotated since entry written)
}
