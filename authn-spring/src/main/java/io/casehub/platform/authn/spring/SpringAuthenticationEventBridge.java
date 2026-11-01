package io.casehub.platform.authn.spring;

import io.casehub.platform.api.authn.AuthenticationEventListener;
import io.casehub.platform.api.authn.AuthenticationFailure;
import io.casehub.platform.api.authn.AuthenticationSuccess;
import io.casehub.platform.api.authn.OAuthTokenRefreshFailed;
import io.casehub.platform.api.authn.SocialLoginCompleted;
import org.springframework.context.ApplicationEventPublisher;

public class SpringAuthenticationEventBridge implements AuthenticationEventListener {

    private final ApplicationEventPublisher publisher;

    public SpringAuthenticationEventBridge(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void onAuthenticationSuccess(AuthenticationSuccess event) {
        publisher.publishEvent(event);
    }

    @Override
    public void onAuthenticationFailure(AuthenticationFailure event) {
        publisher.publishEvent(event);
    }

    @Override
    public void onSocialLoginCompleted(SocialLoginCompleted event) {
        publisher.publishEvent(event);
    }

    @Override
    public void onOAuthTokenRefreshFailed(OAuthTokenRefreshFailed event) {
        publisher.publishEvent(event);
    }
}
