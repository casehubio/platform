package io.casehub.platform.agent.router;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.casehub.platform.api.model.ModelDescriptor;
import io.casehub.platform.api.model.ModelRegistry;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RoutingChatModel implements ChatModel {

    private static final Logger LOG = Logger.getLogger(RoutingChatModel.class);

    private final Map<String, ChatModelBackend> backends;
    private final ModelRegistry modelRegistry;
    private final String defaultBackendKey;
    private final String defaultModel;

    public RoutingChatModel(List<ChatModelBackend> backends, ModelRegistry modelRegistry,
                             String defaultBackendKey, String defaultModel) {
        this.backends = backends.stream()
                .collect(Collectors.toMap(ChatModelBackend::key, Function.identity()));
        this.modelRegistry = Objects.requireNonNull(modelRegistry, "modelRegistry");
        this.defaultBackendKey = defaultBackendKey;
        this.defaultModel = defaultModel;
        LOG.infof("ChatModel router initialized with %d backends, default=%s", this.backends.size(), defaultBackendKey);
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        String modelName = request.parameters().modelName() != null
                ? request.parameters().modelName()
                : defaultModel;

        ChatModelBackend backend = resolveBackend(modelName);
        return backend.chatModel().chat(request);
    }

    private ChatModelBackend resolveBackend(String modelName) {
        if (modelName != null) {
            Optional<ModelDescriptor> descriptor = modelRegistry.resolveById(modelName);
            if (descriptor.isPresent()) {
                ChatModelBackend backend = backends.get(descriptor.get().backendKey());
                if (backend != null) {
                    return backend;
                }
                LOG.warnf("No ChatModel backend for key '%s' (model '%s'), falling back to default",
                        descriptor.get().backendKey(), modelName);
            }
        }

        ChatModelBackend defaultBackend = backends.get(defaultBackendKey);
        if (defaultBackend == null) {
            throw new IllegalStateException(
                    "No ChatModel backend available. Requested model: " + modelName
                    + ", default backend: " + defaultBackendKey
                    + ", available: " + backends.keySet());
        }
        return defaultBackend;
    }
}
