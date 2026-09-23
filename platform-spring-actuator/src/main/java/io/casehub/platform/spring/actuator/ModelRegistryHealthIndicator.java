package io.casehub.platform.spring.actuator;

import io.casehub.platform.api.model.ModelDescriptor;
import io.casehub.platform.api.model.MutableModelRegistry;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import java.util.stream.Collectors;

class ModelRegistryHealthIndicator implements HealthIndicator {

    private final MutableModelRegistry registry;

    ModelRegistryHealthIndicator(MutableModelRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Health health() {
        var models = registry.all();
        if (models.isEmpty()) {
            return Health.down().withDetail("modelCount", 0).build();
        }
        var vendorCounts = models.stream()
                .collect(Collectors.groupingBy(ModelDescriptor::vendor, Collectors.counting()));
        return Health.up()
                .withDetail("modelCount", models.size())
                .withDetail("vendors", vendorCounts)
                .build();
    }
}
