package io.casehub.platform.registration.quarkus;

import io.casehub.platform.api.authn.SocialLoginCompleted;
import io.casehub.platform.registration.core.SocialLoginReconciliationHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class RegistrationAuthenticationEventListener {

    private static final Logger LOG = Logger.getLogger(RegistrationAuthenticationEventListener.class.getName());

    @Inject
    SocialLoginReconciliationHandler reconciliationHandler;

    public void onSocialLoginCompleted(@Observes SocialLoginCompleted event) {
        try {
            reconciliationHandler.onSocialLoginCompleted(
                    event.actorId(), event.tenancyId(), event.emailVerified());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Social login reconciliation failed", e);
        }
    }
}
