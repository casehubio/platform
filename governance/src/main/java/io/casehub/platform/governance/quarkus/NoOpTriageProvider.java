package io.casehub.platform.governance.quarkus;

import io.casehub.platform.api.governance.TriageHandler;
import io.casehub.platform.governance.NoOpTriageHandler;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class NoOpTriageProvider {

    @Produces
    @DefaultBean
    @ApplicationScoped
    TriageHandler noOpTriageHandler() {
        return new NoOpTriageHandler();
    }
}
