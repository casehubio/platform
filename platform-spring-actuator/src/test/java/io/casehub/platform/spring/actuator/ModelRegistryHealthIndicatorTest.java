package io.casehub.platform.spring.actuator;

import io.casehub.platform.api.model.ModelDescriptor;
import io.casehub.platform.api.model.ModelLocality;
import io.casehub.platform.api.model.ModelQuery;
import io.casehub.platform.api.model.ModelTier;
import io.casehub.platform.api.model.MutableModelRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ModelRegistryHealthIndicatorTest {

    @Test
    void upWhenModelsPresent() {
        var registry = stubRegistry(List.of(
                new ModelDescriptor("m1", "m1", "claude", null, "anthropic", "claude",
                        "Claude Sonnet", ModelTier.FLAGSHIP, Set.of(), 200000, 8192,
                        ModelLocality.CLOUD, null, null, null)));
        var indicator = new ModelRegistryHealthIndicator(registry);

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("modelCount", 1);
    }

    @Test
    void downWhenEmpty() {
        var registry = stubRegistry(List.of());
        var indicator = new ModelRegistryHealthIndicator(registry);

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("modelCount", 0);
    }

    private MutableModelRegistry stubRegistry(List<ModelDescriptor> models) {
        return new MutableModelRegistry() {
            @Override public CatalogDelta replaceSource(String s, int p, List<ModelDescriptor> m) {
                return new CatalogDelta(Set.of(), Set.of(), Set.of());
            }
            @Override public Optional<ModelDescriptor> resolveById(String id) { return Optional.empty(); }
            @Override public List<ModelDescriptor> query(ModelQuery q) { return models; }
            @Override public List<ModelDescriptor> all() { return models; }
        };
    }
}
