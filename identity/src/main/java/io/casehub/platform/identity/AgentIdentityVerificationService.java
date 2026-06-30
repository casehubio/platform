package io.casehub.platform.identity;

import io.casehub.platform.api.identity.DIDResolver;
import io.casehub.platform.api.identity.IdentityVerificationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Arrays;

/**
 * Read-path DID identity verification service.
 *
 * <p>Verifies that a stored agent public key matches a verification method in the current
 * DID document for the actor's DID, and that the DID document's {@code alsoKnownAs} claim
 * includes the actorId.
 *
 * <p><strong>VC validation is not re-run on the read path.</strong> VC results are stored
 * at write time. Consumers needing write-time VC results should query the binding repository.
 */
@ApplicationScoped
public class AgentIdentityVerificationService {

    private final DIDResolver resolver;

    @Inject
    public AgentIdentityVerificationService(final DIDResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * Verifies the DID/key binding for an agent entry.
     *
     * @param actorId        the actor identifier (e.g. {@code "claude:reviewer@v1"})
     * @param actorDid       the DID URI stored on the entry, or {@code null} if absent
     * @param agentPublicKey the public key bytes stored on the entry, or {@code null} if absent
     * @return the verification result
     */
    public IdentityVerificationResult verifyIdentityBinding(
            final String actorId,
            final String actorDid,
            final byte[] agentPublicKey) {

        if (actorDid == null) return IdentityVerificationResult.UNVERIFIABLE;
        if (agentPublicKey == null) return IdentityVerificationResult.UNSIGNED;

        final var docOpt = resolver.resolve(actorId, actorDid);
        if (docOpt.isEmpty()) return IdentityVerificationResult.DID_UNRESOLVABLE;

        final var doc = docOpt.get();
        if (!doc.alsoKnownAs().contains(actorId)) {
            return IdentityVerificationResult.IDENTITY_MISMATCH;
        }

        final boolean keyMatch = doc.verificationMethods().stream()
                .anyMatch(vm -> Arrays.equals(vm.publicKeyBytes(), agentPublicKey));
        return keyMatch ? IdentityVerificationResult.VALID : IdentityVerificationResult.KEY_MISMATCH;
    }
}
