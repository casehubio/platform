package io.casehub.yaml.step;

import io.casehub.yaml.plugin.api.ServiceRegistry;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CdiServiceRegistry implements ServiceRegistry {

    @Override
    public <T> T lookup(Class<T> serviceType) {
        InstanceHandle<T> handle = Arc.container().instance(serviceType);
        if (!handle.isAvailable()) {
            throw new IllegalArgumentException(
                    "No CDI bean registered for " + serviceType.getName()
                    + ". Ensure the implementation is on the classpath.");
        }
        return handle.get();
    }
}
