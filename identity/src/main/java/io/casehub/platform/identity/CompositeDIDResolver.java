package io.casehub.platform.identity;

import io.casehub.platform.api.identity.DIDDocument;
import io.casehub.platform.api.identity.DIDMethod;
import io.casehub.platform.api.identity.DIDResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Optional;

/**
 * Composite DID resolver — iterates {@code @DIDMethod}-qualified resolvers
 * in {@code @Priority} order, returning the first non-empty result.
 *
 * <p>Consumers inject the unqualified {@code DIDResolver} and get this bean
 * (beats {@code NoOpDIDResolver @DefaultBean}).
 */
@ApplicationScoped
public class CompositeDIDResolver implements DIDResolver {

    private static final Logger LOG = Logger.getLogger(CompositeDIDResolver.class);

    private final List<DIDResolver> resolvers;

    @Inject
    public CompositeDIDResolver(@DIDMethod Instance<DIDResolver> methodResolvers) {
        this(CdiPriorityUtils.toSortedList(methodResolvers));
    }

    CompositeDIDResolver(List<DIDResolver> resolvers) {
        this.resolvers = resolvers;
    }

    @Override
    public Optional<DIDDocument> resolve(final String actorId, final String did) {
        for (final DIDResolver r : resolvers) {
            try {
                final Optional<DIDDocument> result = r.resolve(actorId, did);
                if (result.isPresent()) return result;
            } catch (final Exception e) {
                LOG.warnf("Resolver %s failed for DID %s: %s",
                        r.getClass().getSimpleName(), did, e.getMessage());
            }
        }
        return Optional.empty();
    }
}
