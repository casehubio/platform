package io.casehub.platform.identity;

import java.util.List;

/**
 * Cached result of a SCIM2 agent lookup.
 *
 * <p>Contains the DID string and DER-encoded X.509 certificates from SCIM
 * {@code x509Certificates[].value}. Certificates carry the agent's public
 * key material — extract via {@code CertificateFactory.getInstance("X.509")}.
 */
public record ScimAgentResource(String did, List<byte[]> derCertificates) {
    public ScimAgentResource {
        derCertificates = derCertificates == null ? List.of() : List.copyOf(derCertificates);
    }
}
