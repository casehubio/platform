package io.casehub.platform.callback;

import io.casehub.platform.api.callback.CallbackRegistration;
import io.casehub.platform.api.callback.CallbackRegistry;
import io.casehub.platform.api.callback.CallbackRegistrationRequest;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@DefaultBean
@ApplicationScoped
public class NoOpCallbackRegistry implements CallbackRegistry {

    @Override
    public CallbackRegistration register(CallbackRegistrationRequest request) {
        return null;
    }

    @Override
    public void deregister(String registrationId) {}

    @Override
    public void heartbeat(String registrationId) {}

    @Override
    public List<CallbackRegistration> findBySpi(String spiName, String tenancyId) {
        return List.of();
    }

    @Override
    public Optional<CallbackRegistration> findById(String registrationId) {
        return Optional.empty();
    }
}
