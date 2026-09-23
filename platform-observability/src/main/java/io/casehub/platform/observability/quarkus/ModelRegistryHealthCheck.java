package io.casehub.platform.observability.quarkus;

import io.casehub.platform.api.model.MutableModelRegistry;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

@Liveness
@ApplicationScoped
public class ModelRegistryHealthCheck implements HealthCheck {

    @Inject
    Instance<MutableModelRegistry> registry;

    @Override
    public HealthCheckResponse call() {
        if (registry.isUnsatisfied()) {
            return HealthCheckResponse.up("modelRegistry");
        }
        var models = registry.get().all();
        return HealthCheckResponse.named("modelRegistry")
                .status(!models.isEmpty())
                .withData("modelCount", models.size())
                .build();
    }
}
