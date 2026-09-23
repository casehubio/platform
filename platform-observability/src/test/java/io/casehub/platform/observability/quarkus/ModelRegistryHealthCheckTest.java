package io.casehub.platform.observability.quarkus;

import io.casehub.platform.api.model.ModelDescriptor;
import io.casehub.platform.api.model.ModelLocality;
import io.casehub.platform.api.model.ModelQuery;
import io.casehub.platform.api.model.ModelTier;
import io.casehub.platform.api.model.MutableModelRegistry;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ModelRegistryHealthCheckTest {

    @Test
    void upWhenModelsPresent() {
        var check = new ModelRegistryHealthCheck();
        check.registry = SingletonInstance.of(stubRegistry(List.of(
                new ModelDescriptor("m1", "m1", "claude", null, "anthropic", "claude",
                        "Claude", ModelTier.FLAGSHIP, Set.of(), 200000, 8192,
                        ModelLocality.CLOUD, null, null, null))));

        var response = check.call();
        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
    }

    @Test
    void downWhenEmpty() {
        var check = new ModelRegistryHealthCheck();
        check.registry = SingletonInstance.of(stubRegistry(List.of()));

        var response = check.call();
        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.DOWN);
    }

    @Test
    void upWhenUnsatisfied() {
        var check = new ModelRegistryHealthCheck();
        check.registry = SingletonInstance.empty();

        var response = check.call();
        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
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
