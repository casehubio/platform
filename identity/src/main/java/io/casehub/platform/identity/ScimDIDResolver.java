package io.casehub.platform.identity;

import io.casehub.platform.api.identity.DIDDocument;
import io.casehub.platform.api.identity.DIDMethod;
import io.casehub.platform.api.identity.DIDResolver;
import io.casehub.platform.api.identity.VerificationMethod;
import io.casehub.platform.api.identity.VerificationMethodType;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Constructs synthetic {@link DIDDocument}s from SCIM {@code x509Certificates}.
 *
 * <p>Retrieves agent resources from {@link ScimAgentLookup}, verifies DID match,
 * and extracts SPKI key bytes from X.509 certificates. Runs at {@code @Priority(1000)}
 * — after Web and Key resolvers in the composite.
 *
 * <p>Returns empty when:
 * <ul>
 *   <li>SCIM is unconfigured ({@code ScimAgentLookup.isConfigured() == false})</li>
 *   <li>Actor not found in SCIM</li>
 *   <li>DID mismatch between request and SCIM response</li>
 *   <li>Any exception during lookup or certificate parsing</li>
 * </ul>
 */
@DIDMethod
@ApplicationScoped
@Priority(1000)
public class ScimDIDResolver implements DIDResolver {

    private static final Logger LOG = Logger.getLogger(ScimDIDResolver.class);

    private final ScimAgentLookup lookup;

    @Inject
    public ScimDIDResolver(final ScimAgentLookup lookup) {
        this.lookup = lookup;
    }

    /** Required by CDI for proxy generation. Must not be called directly. */
    protected ScimDIDResolver() {
        this.lookup = null;
    }

    @Override
    public Optional<DIDDocument> resolve(final String actorId, final String did) {
        if (actorId == null) return Optional.empty();
        if (did == null) return Optional.empty();
        if (lookup == null || !lookup.isConfigured()) return Optional.empty();
        try {
            final Optional<ScimAgentResource> agent = lookup.get(actorId);
            if (agent.isEmpty()) return Optional.empty();

            final ScimAgentResource resource = agent.get();
            if (!did.equals(resource.did())) {
                LOG.warnf("ScimDIDResolver: DID mismatch — SCIM has %s, "
                        + "request has %s for actorId %s", resource.did(), did, actorId);
                return Optional.empty();
            }

            final List<VerificationMethod> vms = extractVerificationMethods(resource);
            return Optional.of(new DIDDocument(resource.did(), vms, List.of(actorId)));
        } catch (final Exception e) {
            LOG.warnf("ScimDIDResolver: lookup failed for actorId %s: %s",
                    actorId, e.getMessage());
            return Optional.empty();
        }
    }

    private List<VerificationMethod> extractVerificationMethods(
            final ScimAgentResource resource) {
        final List<VerificationMethod> vms = new ArrayList<>();
        final List<byte[]> certs = resource.derCertificates();
        for (int i = 0; i < certs.size(); i++) {
            extractVerificationMethod(resource.did(), certs.get(i), i)
                    .ifPresent(vms::add);
        }
        return List.copyOf(vms);
    }

    private Optional<VerificationMethod> extractVerificationMethod(
            final String did, final byte[] derBytes, final int index) {
        try {
            final X509Certificate cert = (X509Certificate) CertificateFactory
                    .getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(derBytes));

            final String vmType = switch (cert.getPublicKey().getAlgorithm()) {
                case "Ed25519", "EdDSA" -> VerificationMethodType.ED25519;
                case "EC" -> VerificationMethodType.P256;
                default -> null;
            };
            if (vmType == null) return Optional.empty();

            return Optional.of(new VerificationMethod(
                    did + "#scim-key-" + index, vmType, cert.getPublicKey().getEncoded()));
        } catch (final Exception e) {
            LOG.warnf("ScimDIDResolver: failed to extract key from certificate %d: %s",
                    index, e.getMessage());
            return Optional.empty();
        }
    }
}
