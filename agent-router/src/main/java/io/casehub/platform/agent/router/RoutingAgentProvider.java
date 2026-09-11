package io.casehub.platform.agent.router;

import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.api.model.ModelDescriptor;
import io.casehub.platform.api.model.ModelRegistry;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class RoutingAgentProvider implements AgentProvider {

    private static final Logger LOG = Logger.getLogger(RoutingAgentProvider.class);

    private final Map<String, AgentBackend> backends;
    private final AgentBackend defaultBackend;
    private final ModelRegistry modelRegistry;

    private record ResolvedRoute(AgentBackend backend, String apiModelId) {}


    @Inject
    public RoutingAgentProvider(@Any Instance<AgentBackend> backends,
                                RoutingAgentProperties properties,
                                ModelRegistry modelRegistry) {
        this.backends      = new HashMap<>();
        this.modelRegistry = modelRegistry;
        AgentBackend fallback = null;
        for (AgentBackend backend : backends) {
            this.backends.put(backend.key(), backend);
            if (backend.key().equals(properties.defaultBackend())) {
                fallback = backend;
            }
        }
        this.defaultBackend = fallback;
        LOG.infof("Agent router initialized: %d backend(s) [%s], default=%s",
                  this.backends.size(), String.join(", ", this.backends.keySet()),
                  properties.defaultBackend());
    }

    RoutingAgentProvider(Iterable<AgentBackend> backends, String defaultKey,
                         ModelRegistry modelRegistry) {
        this.backends      = new HashMap<>();
        this.modelRegistry = modelRegistry;
        AgentBackend fallback = null;
        for (AgentBackend backend : backends) {
            this.backends.put(backend.key(), backend);
            if (backend.key().equals(defaultKey)) {
                fallback = backend;
            }
        }
        this.defaultBackend = fallback;
    }

    @Override
    public Multi<AgentEvent> invoke(AgentSessionConfig config) {
        var route = resolve(config.model());
        var rewritten = new AgentSessionConfig(
                config.systemPrompt(), config.userPrompt(), config.mcpServers(),
                config.timeout(), config.correlationId(), route.apiModelId());
        return route.backend().invoke(rewritten);
    }

    @Override
    public AgentSession openSession(AgentSessionInit init) {
        var route = resolve(init.model());
        var rewritten = new AgentSessionInit(
                init.systemPrompt(), init.mcpServers(),
                init.timeout(), init.correlationId(), route.apiModelId());
        return route.backend().openSession(rewritten);
    }

    private ResolvedRoute resolve(String model) {
        if (model == null) {
            if (defaultBackend == null) {
                throw new IllegalStateException(
                        "No default backend configured — set casehub.platform.agent.default-backend");
            }
            return new ResolvedRoute(defaultBackend, null);
        }

        Optional<ModelDescriptor> descriptor = modelRegistry.resolveById(model);
        if (descriptor.isPresent()) {
            AgentBackend backend = backends.get(descriptor.get().backendKey());
            if (backend == null) {
                throw new IllegalStateException(
                        "ModelRegistry resolved '" + model + "' to backend '" +
                        descriptor.get().backendKey() +
                        "', but no backend with that key is available");
            }
            return new ResolvedRoute(backend, descriptor.get().id());
        }

        AgentBackend backend = backends.get(model);
        if (backend != null) return new ResolvedRoute(backend, null);

        throw new IllegalArgumentException("No model or backend for: " + model +
                                           ". Available backends: " + backends.keySet());
    }
}
