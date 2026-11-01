package io.casehub.platform.registration.spring;

import io.casehub.platform.api.authn.SocialLoginCompleted;
import io.casehub.platform.registration.core.SocialLoginReconciliationHandler;
import org.springframework.context.event.EventListener;

import java.util.logging.Level;
import java.util.logging.Logger;

public class RegistrationAuthenticationEventObserver {

    private static final Logger LOG = Logger.getLogger(RegistrationAuthenticationEventObserver.class.getName());

    private final SocialLoginReconciliationHandler reconciliationHandler;

    public RegistrationAuthenticationEventObserver(SocialLoginReconciliationHandler reconciliationHandler) {
        this.reconciliationHandler = reconciliationHandler;
    }

    @EventListener
    public void onSocialLoginCompleted(SocialLoginCompleted event) {
        try {
            reconciliationHandler.onSocialLoginCompleted(
                    event.actorId(), event.tenancyId(), event.emailVerified());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Social login reconciliation failed", e);
        }
    }
}
