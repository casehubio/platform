package io.casehub.platform.registration;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

@DefaultBean
@ApplicationScoped
public class PreferenceRegistrationPolicy implements RegistrationPolicy {

    @Override
    public RegistrationMode getMode(String tenancyId) {
        return RegistrationMode.OPEN;
    }
}
