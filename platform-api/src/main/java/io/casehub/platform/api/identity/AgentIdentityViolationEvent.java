package io.casehub.platform.api.identity;

/** Fired async when actorId→DID binding validation returns a non-VALID result. */
public record AgentIdentityViolationEvent(
        String actorId,
        String tenancyId,
        String actorDid,
        IdentityBindingStatus status) {}
