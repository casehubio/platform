package io.casehub.platform.confirmation.quarkus;

import io.casehub.platform.api.confirmation.OperationConfirmationProvider;
import io.casehub.platform.confirmation.ConfirmationInterceptorCore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class ConfirmationBeans {

    @Produces
    @ApplicationScoped
    ConfirmationInterceptorCore confirmationInterceptorCore(
            OperationConfirmationProvider provider) {
        return new ConfirmationInterceptorCore(provider);
    }
}
