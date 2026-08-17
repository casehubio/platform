package io.casehub.platform.api.callback;

import java.util.List;
import java.util.Optional;

public interface CallbackRegistry {

    CallbackRegistration register(CallbackRegistrationRequest request);

    void deregister(String registrationId);

    void heartbeat(String registrationId);

    List<CallbackRegistration> findBySpi(String spiName, String tenancyId);

    Optional<CallbackRegistration> findById(String registrationId);
}
