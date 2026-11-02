package io.casehub.platform.label;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.label.LabelRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class LabelBeans {

    @Produces
    @ApplicationScoped
    public LabelServiceCore labelServiceCore(LabelRegistry registry, CurrentPrincipal principal) {
        return new LabelServiceCore(registry, principal);
    }
}
