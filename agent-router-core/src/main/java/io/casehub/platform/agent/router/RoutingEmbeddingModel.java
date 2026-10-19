package io.casehub.platform.agent.router;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import io.casehub.platform.api.model.ModelDescriptor;
import io.casehub.platform.api.model.ModelRegistry;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RoutingEmbeddingModel implements EmbeddingModel {

    private static final Logger LOG = Logger.getLogger(RoutingEmbeddingModel.class);

    private final Map<String, EmbeddingModelBackend> backends;
    private final ModelRegistry modelRegistry;
    private final String defaultBackendKey;

    public RoutingEmbeddingModel(List<EmbeddingModelBackend> backends, ModelRegistry modelRegistry,
                                  String defaultBackendKey) {
        this.backends = backends.stream()
                .collect(Collectors.toMap(EmbeddingModelBackend::key, Function.identity()));
        this.modelRegistry = Objects.requireNonNull(modelRegistry, "modelRegistry");
        this.defaultBackendKey = defaultBackendKey;
        LOG.infof("EmbeddingModel router initialized with %d backends, default=%s",
                this.backends.size(), defaultBackendKey);
    }

    @Override
    public EmbeddingResponse doEmbed(EmbeddingRequest request) {
        String modelName = request.modelName();
        EmbeddingModelBackend backend = resolveBackend(modelName);
        return backend.embeddingModel().doEmbed(request);
    }

    private EmbeddingModelBackend resolveBackend(String modelName) {
        if (modelName != null) {
            Optional<ModelDescriptor> descriptor = modelRegistry.resolveById(modelName);
            if (descriptor.isPresent()) {
                EmbeddingModelBackend backend = backends.get(descriptor.get().backendKey());
                if (backend != null) {
                    return backend;
                }
                LOG.warnf("No EmbeddingModel backend for key '%s' (model '%s'), falling back to default",
                        descriptor.get().backendKey(), modelName);
            }
        }

        EmbeddingModelBackend defaultBackend = backends.get(defaultBackendKey);
        if (defaultBackend == null) {
            throw new IllegalStateException(
                    "No EmbeddingModel backend available. Requested model: " + modelName
                    + ", default backend: " + defaultBackendKey
                    + ", available: " + backends.keySet());
        }
        return defaultBackend;
    }
}
