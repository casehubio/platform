package io.casehub.platform.confirmation.quarkus;

import io.casehub.platform.api.confirmation.OperationConfirmationProvider;
import io.casehub.platform.confirmation.NoOpOperationConfirmationProvider;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class NoOpConfirmationProvider {

    @Produces
    @DefaultBean
    @ApplicationScoped
    OperationConfirmationProvider noOpProvider() {
        return new NoOpOperationConfirmationProvider();
    }
}
