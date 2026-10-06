package io.casehub.platform.api.authn;

public interface AuthenticationEventListener {
    default void onSocialLoginCompleted(SocialLoginCompleted event) {}
    default void onOAuthTokenRefreshFailed(OAuthTokenRefreshFailed event) {}
}
