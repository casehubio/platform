package io.casehub.platform.api.identity;

/** Fired async when actorId→DID binding validation succeeds (VALID result). */
public record AgentIdentityValidatedEvent(
        String actorId,
        String actorDid,
        IdentityBindingStatus status,
        boolean alsoKnownAsVerified,
        boolean keyMatchVerified,
        String verifiedKeyRef,
        CredentialValidationResult credentialResult,
        String didMethod) {}
