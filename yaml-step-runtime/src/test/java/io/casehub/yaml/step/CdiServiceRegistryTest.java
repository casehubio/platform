package io.casehub.yaml.step;

import io.casehub.yaml.plugin.api.MapServiceRegistry;
import io.casehub.yaml.plugin.api.ServiceRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CdiServiceRegistryTest {

    @Test
    void lookupReturnsRegisteredService() {
        ServiceRegistry registry = new MapServiceRegistry()
                .register(Runnable.class, () -> {});

        Runnable result = registry.lookup(Runnable.class);
        assertThat(result).isNotNull();
    }

    @Test
    void lookupThrowsForMissingService() {
        ServiceRegistry registry = new MapServiceRegistry();

        assertThatThrownBy(() -> registry.lookup(Runnable.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Runnable");
    }

    @Test
    void cdiServiceRegistryExists() throws Exception {
        Class<?> clazz = Class.forName("io.casehub.yaml.step.CdiServiceRegistry");
        assertThat(ServiceRegistry.class.isAssignableFrom(clazz)).isTrue();
    }
}
