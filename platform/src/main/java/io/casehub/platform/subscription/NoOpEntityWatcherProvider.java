package io.casehub.platform.subscription;

import io.casehub.platform.api.subscription.EntityWatcherProvider;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.Set;

/**
 * No-op {@link EntityWatcherProvider} that always returns empty results.
 * Warns on each invocation to surface missing implementation.
 */
@DefaultBean
@ApplicationScoped
public class NoOpEntityWatcherProvider implements EntityWatcherProvider {
    private static final Logger LOG = Logger.getLogger(NoOpEntityWatcherProvider.class);

    @Override
    public Set<String> watchersOf(final String entityType, final String entityId, final String tenancyId) {
        LOG.warnf("ENTITY_WATCHERS target used but no EntityWatcherProvider is registered"
                + " — notifications to %s/%s will not be delivered", entityType, entityId);
        return Set.of();
    }
}
