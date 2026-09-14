package io.casehub.platform.callback;

import io.casehub.platform.api.callback.CallbackApi;
import io.casehub.platform.api.callback.CallbackRegistration;
import io.casehub.platform.api.callback.CallbackRegistrationRequest;
import io.casehub.platform.api.callback.CallbackRegistry;
import io.casehub.platform.api.identity.PlatformRoles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
@RolesAllowed(PlatformRoles.ADMIN)
public class CallbackService implements CallbackApi {

    private final CallbackRegistry callbackRegistry;

    @Inject
    public CallbackService(CallbackRegistry callbackRegistry) {
        this.callbackRegistry = callbackRegistry;
    }

    @Override
    public CallbackRegistration register(CallbackRegistrationRequest request) {
        return callbackRegistry.register(request);
    }

    @Override
    public void heartbeat(String id) {
        if (callbackRegistry.findById(id).isEmpty()) {
            throw new NotFoundException("Callback registration not found: " + id);
        }
        callbackRegistry.heartbeat(id);
    }

    @Override
    public void deregister(String id) {
        callbackRegistry.deregister(id);
    }
}
