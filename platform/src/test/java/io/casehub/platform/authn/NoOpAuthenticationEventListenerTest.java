package io.casehub.platform.authn;

import io.casehub.platform.api.authn.AuthenticationEventListener;
import io.casehub.platform.api.authn.OAuthTokenRefreshFailed;
import io.casehub.platform.api.authn.SocialLoginCompleted;
import org.junit.jupiter.api.Test;

import java.util.Set;

class NoOpAuthenticationEventListenerTest {

    private final AuthenticationEventListener listener = new NoOpAuthenticationEventListener();

    @Test
    void onSocialLoginCompletedIsNoOp() {
        listener.onSocialLoginCompleted(new SocialLoginCompleted("google", "actor-1", "tenant-1", Set.of("openid"), true));
    }

    @Test
    void onOAuthTokenRefreshFailedIsNoOp() {
        listener.onOAuthTokenRefreshFailed(new OAuthTokenRefreshFailed("actor-1", "tenant-1", "google", "token_expired"));
    }
}
