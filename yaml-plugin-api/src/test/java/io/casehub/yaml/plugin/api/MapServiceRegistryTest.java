package io.casehub.yaml.plugin.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MapServiceRegistryTest {

    @Test
    void lookupReturnsLocallyRegisteredService() {
        var registry = new MapServiceRegistry();
        registry.register(String.class, "local");

        assertThat(registry.lookup(String.class)).isEqualTo("local");
    }

    @Test
    void lookupThrowsWhenNotRegistered() {
        var registry = new MapServiceRegistry();

        assertThatThrownBy(() -> registry.lookup(String.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("java.lang.String");
    }

    @Test
    void delegatesToParentWhenNotFoundLocally() {
        var parent = new MapServiceRegistry();
        parent.register(String.class, "from-parent");

        var child = new MapServiceRegistry(parent);

        assertThat(child.lookup(String.class)).isEqualTo("from-parent");
    }

    @Test
    void localRegistrationOverridesParent() {
        var parent = new MapServiceRegistry();
        parent.register(String.class, "from-parent");

        var child = new MapServiceRegistry(parent);
        child.register(String.class, "from-child");

        assertThat(child.lookup(String.class)).isEqualTo("from-child");
    }

    @Test
    void throwsWhenNeitherLocalNorParentHasService() {
        var parent = new MapServiceRegistry();
        var child = new MapServiceRegistry(parent);

        assertThatThrownBy(() -> child.lookup(Integer.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("java.lang.Integer");
    }

    @Test
    void parentServicesDoNotLeakIntoChildLocalMap() {
        var parent = new MapServiceRegistry();
        parent.register(String.class, "from-parent");

        var child = new MapServiceRegistry(parent);
        child.register(Integer.class, 42);

        assertThat(child.lookup(String.class)).isEqualTo("from-parent");
        assertThat(child.lookup(Integer.class)).isEqualTo(42);
    }

    @Test
    void acceptsAnyServiceRegistryAsParent() {
        ServiceRegistry parent = new ServiceRegistry() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T lookup(Class<T> serviceType) {
                if (serviceType == String.class) return (T) "custom-parent";
                throw new IllegalArgumentException("No service for " + serviceType.getName());
            }
        };

        var child = new MapServiceRegistry(parent);

        assertThat(child.lookup(String.class)).isEqualTo("custom-parent");
    }
}
