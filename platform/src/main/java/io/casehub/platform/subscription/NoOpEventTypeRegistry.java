package io.casehub.platform.subscription;

import io.casehub.platform.api.subscription.EventTypeDescriptor;
import io.casehub.platform.api.subscription.EventTypeRegistry;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.Set;

/**
 * No-op {@link EventTypeRegistry} — active when no domain bridges are deployed.
 *
 * <p>All queries return empty. {@link #register(EventTypeDescriptor)} is a silent no-op.
 *
 * <p>Displaced by any {@code @ApplicationScoped} implementation on the classpath,
 * per the {@code @DefaultBean} CDI displacement contract.
 */
@DefaultBean
@ApplicationScoped
public class NoOpEventTypeRegistry implements EventTypeRegistry {

    @Override
    public void register(final EventTypeDescriptor descriptor) {
        // Silent no-op
    }

    @Override
    public Optional<EventTypeDescriptor> resolve(final String eventType) {
        return Optional.empty();
    }

    @Override
    public Set<EventTypeDescriptor> discover() {
        return Set.of();
    }
}
