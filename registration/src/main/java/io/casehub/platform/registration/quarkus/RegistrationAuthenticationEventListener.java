package io.casehub.platform.registration.quarkus;

import io.casehub.platform.api.authn.AuthenticationEventListener;
import io.casehub.platform.api.authn.SocialLoginCompleted;
import io.casehub.platform.registration.core.SocialLoginReconciliationHandler;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import java.util.logging.Level;
import java.util.logging.Logger;

@Alternative
@Priority(100)
@ApplicationScoped
public class RegistrationAuthenticationEventListener implements AuthenticationEventListener {

    private static final Logger LOG = Logger.getLogger(RegistrationAuthenticationEventListener.class.getName());

    @Inject
    SocialLoginReconciliationHandler reconciliationHandler;

    @Override
    public void onSocialLoginCompleted(SocialLoginCompleted event) {
        try {
            reconciliationHandler.onSocialLoginCompleted(
                    event.actorId(), event.tenancyId(), event.emailVerified());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Social login reconciliation failed", e);
        }
    }
}
