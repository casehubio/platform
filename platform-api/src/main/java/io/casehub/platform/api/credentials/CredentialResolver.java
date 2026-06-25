package io.casehub.platform.api.credentials;

import java.util.Map;

/**
 * Resolves outbound endpoint credentials by logical reference name.
 *
 * <p>This is for outbound credential resolution (endpoint secrets, API tokens,
 * service passwords) — not to be confused with inbound Verifiable Credential
 * validation in {@code io.casehub.platform.api.identity}.
 *
 * <p>Implementations must be thread-safe — {@code @ApplicationScoped} beans
 * are shared across request threads.
 *
 * @see CredentialPropertyKeys
 */
public interface CredentialResolver {

    /**
     * Resolve the credential properties for the given logical reference.
     *
     * <p>Returns an empty map for null, blank, or unresolvable refs.
     * Never returns null. Never throws for missing credentials — consumers
     * decide whether absence is an error.
     *
     * @param credentialRef the logical credential name
     *        (from {@link io.casehub.platform.api.endpoints.EndpointDescriptor#credentialRef()})
     * @return credential properties keyed by {@link CredentialPropertyKeys} constants,
     *         or empty map if unresolvable
     */
    Map<String, String> resolve(String credentialRef);
}
