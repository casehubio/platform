package io.casehub.platform.api.model;

import java.util.List;
import java.util.Optional;

public interface ModelRegistry {
    Optional<ModelDescriptor> resolveById(String modelId);
    List<ModelDescriptor> query(ModelQuery query);
    List<ModelDescriptor> all();
}
