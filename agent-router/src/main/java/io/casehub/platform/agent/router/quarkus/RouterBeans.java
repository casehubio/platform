package io.casehub.platform.agent.router.quarkus;

import io.casehub.platform.agent.BackendInstanceRegistry;
import io.casehub.platform.agent.router.RoutingAgentProvider;
import io.casehub.platform.agent.router.config.RoutingAgentConfig;
import io.casehub.platform.agent.config.ManifestResult;
import io.casehub.platform.api.model.ModelRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@ApplicationScoped
public class RouterBeans {

    @Inject
    BackendInstanceRegistry  registry;
    @Inject
    ModelRegistry            modelRegistry;
    @Inject
    @Any
    Instance<ManifestResult> manifestResult;

    @Produces
    @ApplicationScoped
    public RoutingAgentProvider routingAgentProvider(RoutingAgentConfig config) {
        var manifest = manifestResult.isResolvable()
                ? java.util.Optional.of(manifestResult.get())
                : java.util.Optional.<io.casehub.platform.agent.config.ManifestResult>empty();
        return RoutingAgentProvider.create(registry, config.defaultBackend(), modelRegistry, manifest);
    }
}
