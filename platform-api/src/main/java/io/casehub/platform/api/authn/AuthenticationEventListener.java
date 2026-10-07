package io.casehub.platform.api.authn;

public interface AuthenticationEventListener {
    default void onAuthenticationSuccess(AuthenticationSuccess event)     {}

    default void onAuthenticationFailure(AuthenticationFailure event)     {}

    default void onSocialLoginCompleted(SocialLoginCompleted event)       {}

    default void onOAuthTokenRefreshFailed(OAuthTokenRefreshFailed event) {}
}
