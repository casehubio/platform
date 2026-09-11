package io.casehub.platform.agent.router;

import io.casehub.platform.api.model.ModelDescriptor;
import io.casehub.platform.api.model.ModelQuery;
import io.casehub.platform.api.model.ModelRegistry;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@DefaultBean
@ApplicationScoped
public class NoOpModelRegistry implements ModelRegistry {

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
}
