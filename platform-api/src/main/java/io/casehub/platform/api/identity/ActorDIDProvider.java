package io.casehub.platform.api.identity;

import java.util.Optional;

/**
 * Maps an actorId string to its DID URI.
 *
 * <p>Return empty for actors without a DID binding.
 *
 * <p>Implementations are CDI beans annotated with {@code @ActorDIDSource} and
 * {@code @Priority}. The composite {@code CompositeActorDIDProvider} iterates
 * all registered providers in ascending priority order (lowest value first)
 * and returns the first non-empty result. The default no-op implementation returns
 * {@link Optional#empty()} for every actor.
 */
public interface ActorDIDProvider {

    /**
     * Returns the DID URI for the given actor, or empty if the actor has no DID binding.
     *
     * @param actorId the actor identifier (e.g. {@code "claude:tarkus-reviewer@v1"})
     * @return the DID URI (e.g. {@code "did:web:example.com:agents:tarkus"}), or empty
     */
    Optional<String> didFor(String actorId);

    /**
     * Invalidates any cached state for the given actor.
     * Called by the composite to propagate invalidation to all children.
     * No-op by default — override in caching implementations.
     *
     * @param actorId the actor whose cached state should be cleared
     */
    default void invalidate(String actorId) {}
}
