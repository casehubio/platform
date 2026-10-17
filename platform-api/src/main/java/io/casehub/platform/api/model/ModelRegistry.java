package io.casehub.platform.api.model;

import java.util.List;
import java.util.Optional;

public interface ModelRegistry {
    Optional<ModelDescriptor> resolveById(String modelId);
    List<ModelDescriptor> query(ModelQuery query);
    List<ModelDescriptor> all();

    static ModelRegistry empty() {
        return new ModelRegistry() {
            @Override
            public Optional<ModelDescriptor> resolveById(String modelId) {
                return Optional.empty();
            }

            @Override
            public List<ModelDescriptor> query(ModelQuery query) {
                return List.of();
            }

            @Override
            public List<ModelDescriptor> all() {
                return List.of();
            }
        };
    }

}
