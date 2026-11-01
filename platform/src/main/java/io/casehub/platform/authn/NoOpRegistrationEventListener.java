package io.casehub.platform.authn;

import io.casehub.platform.registration.RegistrationEventListener;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

@DefaultBean
@ApplicationScoped
public class NoOpRegistrationEventListener implements RegistrationEventListener {
}
