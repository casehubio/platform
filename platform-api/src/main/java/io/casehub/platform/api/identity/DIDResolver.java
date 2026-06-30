package io.casehub.platform.api.identity;

import java.util.Optional;

/**
 * Resolves a DID URI to a DID document.
 *
 * <p>Return empty when the DID is unresolvable — for example when the method is
 * unsupported, the network is unreachable, or the document does not exist.
 * Implementations MUST NOT throw — return empty for any failure case.
 *
 * <p>Implementations are CDI beans annotated with {@code @DIDMethod}.
 * The composite resolver iterates all {@code @DIDMethod} resolvers
 * in {@code @Priority} order, returning the first non-empty result.
 */
public interface DIDResolver {

    /**
     * Resolves the given DID URI to its document.
     *
     * @param actorId the actor that claims this DID, or {@code null} when the actor is
     *                unknown (e.g., resolving a credential issuer's DID for VC validation)
     * @param did     the DID URI to resolve (e.g. {@code "did:web:example.com:agents:tarkus"})
     * @return the resolved document, or empty when unresolvable
     */
    Optional<DIDDocument> resolve(String actorId, String did);
}
