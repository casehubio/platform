package io.casehub.platform.endpoints;

import io.casehub.platform.api.endpoints.EndpointDescriptor;
import io.casehub.platform.api.endpoints.EndpointQuery;
import io.casehub.platform.api.endpoints.EndpointRegistry;
import io.casehub.platform.api.path.Path;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

/**
 * No-op {@link EndpointRegistry} — active when no backend module is on the classpath.
 *
 * <p>{@link #register(EndpointDescriptor)} and {@link #deregister(Path, String)} are
 * silent no-ops. {@link #resolve(Path, String)} always returns empty.
 * {@link #discover(EndpointQuery)} always returns an empty list.
 *
 * <p>Displaced by any {@code @Alternative} or bare {@code @ApplicationScoped}
 * {@link io.casehub.platform.api.endpoints.EndpointRegistry} implementation on the
 * classpath, per the {@code @DefaultBean} CDI displacement contract.
 */
@DefaultBean
@ApplicationScoped
public class NoOpEndpointRegistry implements EndpointRegistry {

    @Override public void register(final EndpointDescriptor endpoint) {}

    @Override
    public Optional<EndpointDescriptor> resolve(final Path path, final String tenancyId) {
        return Optional.empty();
    }

    @Override
    public List<EndpointDescriptor> discover(final EndpointQuery query) {
        return List.of();
    }

    @Override public void deregister(final Path path, final String tenancyId) {}
}
