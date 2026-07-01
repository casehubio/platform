package io.casehub.platform.identity;

import io.casehub.platform.api.identity.ActorDIDProvider;
import io.casehub.platform.api.identity.ActorDIDSource;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class CompositeActorDIDProvider implements ActorDIDProvider {

    private static final Logger LOG = Logger.getLogger(CompositeActorDIDProvider.class);

    private final List<ActorDIDProvider> providers;

    @Inject
    public CompositeActorDIDProvider(@ActorDIDSource Instance<ActorDIDProvider> sources) {
        this(CdiPriorityUtils.toSortedList(sources));
    }

    CompositeActorDIDProvider(List<ActorDIDProvider> providers) {
        this.providers = providers;
    }

    @Override
    public Optional<String> didFor(final String actorId) {
        for (final ActorDIDProvider p : providers) {
            try {
                final Optional<String> result = p.didFor(actorId);
                if (result.isPresent()) return result;
            } catch (final Exception e) {
                LOG.warnf("Provider %s failed for actorId %s: %s",
                        p.getClass().getSimpleName(), actorId, e.getMessage());
            }
        }
        return Optional.empty();
    }

    @Override
    public void invalidate(final String actorId) {
        for (final ActorDIDProvider p : providers) {
            try {
                p.invalidate(actorId);
            } catch (final Exception e) {
                LOG.warnf("Provider %s invalidate failed for actorId %s: %s",
                        p.getClass().getSimpleName(), actorId, e.getMessage());
            }
        }
    }
}
