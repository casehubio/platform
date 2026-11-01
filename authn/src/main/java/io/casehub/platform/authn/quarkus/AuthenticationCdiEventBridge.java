package io.casehub.platform.authn.quarkus;

import io.casehub.platform.api.authn.AuthenticationEventListener;
import io.casehub.platform.api.authn.AuthenticationFailure;
import io.casehub.platform.api.authn.AuthenticationSuccess;
import io.casehub.platform.api.authn.OAuthTokenRefreshFailed;
import io.casehub.platform.api.authn.SocialLoginCompleted;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

@ApplicationScoped
public class AuthenticationCdiEventBridge implements AuthenticationEventListener {

    @Inject
    Event<AuthenticationSuccess> authenticationSuccessEvent;

    @Inject
    Event<AuthenticationFailure> authenticationFailureEvent;

    @Inject
    Event<SocialLoginCompleted> socialLoginCompletedEvent;

    @Inject
    Event<OAuthTokenRefreshFailed> oAuthTokenRefreshFailedEvent;

    @Override
    public void onAuthenticationSuccess(AuthenticationSuccess event) {
        authenticationSuccessEvent.fire(event);
    }

    @Override
    public void onAuthenticationFailure(AuthenticationFailure event) {
        authenticationFailureEvent.fire(event);
    }

    @Override
    public void onSocialLoginCompleted(SocialLoginCompleted event) {
        socialLoginCompletedEvent.fire(event);
    }

    @Override
    public void onOAuthTokenRefreshFailed(OAuthTokenRefreshFailed event) {
        oAuthTokenRefreshFailedEvent.fire(event);
    }
}
