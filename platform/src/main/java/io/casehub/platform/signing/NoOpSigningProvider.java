package io.casehub.platform.signing;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.arc.DefaultBean;
import org.jboss.logging.Logger;

import io.casehub.platform.api.signing.SignatureResult;
import io.casehub.platform.api.signing.SigningProvider;

@DefaultBean
@ApplicationScoped
public class NoOpSigningProvider implements SigningProvider {

    private static final Logger LOG = Logger.getLogger(NoOpSigningProvider.class);
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    @Override
    public Optional<SignatureResult> sign(final String actorId, final byte[] data) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(data, "data must not be null");
        if (warned.add(actorId)) {
            LOG.warnf("No signing backend configured — returning unsigned for actor %s. "
                    + "Add a signing backend to enable cryptographic signing.", actorId);
        }
        return Optional.empty();
    }
}
