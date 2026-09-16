package io.casehub.platform.agent.router;

import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.BackendInstanceRegistry;
import io.casehub.platform.api.model.ModelDescriptor;
import io.casehub.platform.api.model.ModelQuery;
import io.casehub.platform.api.model.ModelRef;
import io.casehub.platform.api.model.ModelRegistry;
import io.casehub.platform.api.model.ModelTier;
import io.smallrye.mutiny.Multi;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.Optional;

public class RoutingAgentProvider implements AgentProvider {

    private static final Logger LOG = Logger.getLogger(RoutingAgentProvider.class);

    private final BackendInstanceRegistry registry;
    private final String                  defaultBackendKey;
    private final ModelRegistry           modelRegistry;
    private final Map<String, ModelQuery> aliases;

    private record ResolvedRoute(AgentBackend backend, String apiModelId) {}

    public RoutingAgentProvider(BackendInstanceRegistry registry,
                                String defaultBackendKey,
                                ModelRegistry modelRegistry) {
        this(registry, defaultBackendKey, modelRegistry, Map.of());
    }

    public RoutingAgentProvider(BackendInstanceRegistry registry,
                                String defaultBackendKey,
                                ModelRegistry modelRegistry,
                                Map<String, ModelQuery> aliases) {
        this.registry          = registry;
        this.defaultBackendKey = defaultBackendKey;
        this.modelRegistry     = modelRegistry;
        this.aliases           = aliases != null ? Map.copyOf(aliases) : Map.of();
        LOG.infof("Agent router initialized with registry, default=%s, aliases=%d",
                  defaultBackendKey, this.aliases.size());
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

    private ResolvedRoute resolveQuery(ModelQuery query) {
        var candidates = modelRegistry.query(query);

        if (candidates.isEmpty()) {
            if (modelRegistry.all().isEmpty()) {
                throw new IllegalArgumentException("No model sources configured — query resolution requires at least one ModelSource.");
            }
            if (query.tier() != null) {
                var availableTiers = modelRegistry.all().stream()
                                                  .map(ModelDescriptor::tier)
                                                  .distinct().sorted().toList();
                throw new IllegalArgumentException(
                        "No model matching tier " + query.tier()
                        + " (default backend: " + defaultBackendKey
                        + "). Available tiers: " + availableTiers);
            }
            throw new IllegalArgumentException("No model matching query " + query);
        }

        var preferred = candidates.stream()
                                  .filter(d -> d.backendKey().equals(defaultBackendKey))
                                  .toList();

        ModelDescriptor selected;
        if (!preferred.isEmpty()) {
            selected = preferred.get(0);
        } else if (query.preferVendor() != null) {
            selected = candidates.stream()
                                 .filter(d -> d.vendor().equals(query.preferVendor()))
                                 .findFirst()
                                 .orElse(candidates.get(0));
        } else {
            selected = candidates.get(0);
        }

        String instanceId = selected.backendInstanceId() != null
                            ? selected.backendInstanceId() : "default";
        var backend = registry.resolve(selected.backendKey(), instanceId);
        if (backend.isEmpty()) {
            throw new IllegalStateException(
                    "Model '" + selected.id() + "' resolved to backend "
                    + selected.backendKey() + "/" + instanceId
                    + ", but no backend with that key/instance is registered");
        }

        LOG.debugf("Query resolved to model %s (backend: %s/%s)",
                   selected.id(), selected.backendKey(), instanceId);
        return new ResolvedRoute(backend.get(), selected.apiModelId());
    }

    private ResolvedRoute resolveTier(ModelTier tier) {
        var query = ModelQuery.builder().tier(tier).build();
        return resolveQuery(query);
    }

    private ResolvedRoute resolve(String model) {
        if (model == null) {
            var backend = registry.resolve(defaultBackendKey, "default");
            if (backend.isEmpty()) {
                throw new IllegalStateException(
                        "No default backend configured: " + defaultBackendKey);
            }
            return new ResolvedRoute(backend.get(), null);
        }

        // Step 0: Alias?
        if (aliases.containsKey(model)) {
            LOG.debugf("Resolving alias: %s", model);
            return resolveQuery(aliases.get(model));
        }

        // Step 1: Tier reference (unambiguous prefix — check first)
        if (ModelRef.isTierRef(model)) {
            return resolveTier(ModelRef.parseTier(model));
        }

        // Step 2: Registry ID
        Optional<ModelDescriptor> descriptor = modelRegistry.resolveById(model);
        if (descriptor.isPresent()) {
            var    d          = descriptor.get();
            String instanceId = d.backendInstanceId() != null ? d.backendInstanceId() : "default";
            var    backend    = registry.resolve(d.backendKey(), instanceId);
            if (backend.isEmpty()) {
                throw new IllegalStateException(
                        "Model '" + model + "' resolved to backend " + d.backendKey()
                        + "/" + instanceId + ", but no backend with that key/instance is registered");
            }
            return new ResolvedRoute(backend.get(), d.apiModelId());
        }

        // Step 3: Backend key
        var backend = registry.resolve(model, "default");
        if (backend.isPresent()) {
            return new ResolvedRoute(backend.get(), null);
        }

        // Step 4: Fail-fast
        throw new IllegalArgumentException("No model or backend for: " + model);
    }
}
