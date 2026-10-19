package io.casehub.platform.agent.router.quarkus;

import dev.langchain4j.model.chat.ChatModel;
import io.casehub.platform.agent.BackendInstanceRegistry;
import io.casehub.platform.agent.config.ManifestResult;
import io.casehub.platform.agent.router.ChatModelBackend;
import io.casehub.platform.agent.router.RoutingAgentProvider;
import io.casehub.platform.agent.router.RoutingChatModel;
import io.casehub.platform.agent.router.config.RoutingAgentConfig;
import io.casehub.platform.api.model.ModelRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class RouterBeans {

    @Inject
    BackendInstanceRegistry                                         registry;
    @Inject
    ModelRegistry                                                   modelRegistry;
    @Inject
    @Any
    Instance<ManifestResult>                                        manifestResult;
    @Inject
    @Any
    Instance<io.casehub.platform.agent.session.SessionPoolRegistry> poolRegistryInstance;
    @Inject
    @Any
    Instance<io.casehub.platform.api.model.ModelAvailabilityFilter> availabilityFilterInstance;
    @Inject
    @Any
    Instance<ChatModelBackend>                                      chatModelBackends;
    @Inject
    @Any
    Instance<io.casehub.platform.agent.router.EmbeddingModelBackend> embeddingModelBackends;


    @Produces
    @ApplicationScoped
    @io.quarkus.arc.DefaultBean
    public io.casehub.platform.agent.router.InMemoryBackendInstanceRegistry backendInstanceRegistry() {
        return new io.casehub.platform.agent.router.InMemoryBackendInstanceRegistry();
    }

    @Produces
    @ApplicationScoped
    public RoutingAgentProvider routingAgentProvider(RoutingAgentConfig config) {
        var manifest = manifestResult.isResolvable()
                       ? java.util.Optional.of(manifestResult.get())
                       : java.util.Optional.<io.casehub.platform.agent.config.ManifestResult>empty();
        var availabilityFilter = availabilityFilterInstance.isResolvable()
                                 ? availabilityFilterInstance.get() : null;
        var poolRegistry = poolRegistryInstance.isResolvable()
                           ? poolRegistryInstance.get() : null;
        return RoutingAgentProvider.create(registry, config.defaultBackend(),
                                           modelRegistry, manifest, availabilityFilter, poolRegistry);
    }

    @Produces
    @ApplicationScoped
    @io.quarkus.arc.DefaultBean
    public ChatModel routingChatModel(RoutingAgentConfig config) {
        List<ChatModelBackend> backendList = new ArrayList<>();
        chatModelBackends.forEach(backendList::add);
        return new RoutingChatModel(backendList, modelRegistry, config.defaultBackend(), null);
    }

    @Produces
    @ApplicationScoped
    @io.quarkus.arc.DefaultBean
    public dev.langchain4j.model.embedding.EmbeddingModel routingEmbeddingModel(
            io.casehub.platform.agent.router.config.RoutingAgentConfig config) {
        List<io.casehub.platform.agent.router.EmbeddingModelBackend> backendList = new ArrayList<>();
        embeddingModelBackends.forEach(backendList::add);
        return new io.casehub.platform.agent.router.RoutingEmbeddingModel(
                backendList, modelRegistry, config.defaultBackend());
    }


}
