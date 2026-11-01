package io.casehub.platform.authn.spring;

import io.casehub.platform.api.authn.AuthenticationFailure;
import io.casehub.platform.api.authn.AuthenticationSuccess;
import io.casehub.platform.api.authn.OAuthTokenRefreshFailed;
import io.casehub.platform.api.authn.SocialLoginCompleted;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAuthenticationEventBridgeTest {

    private final List<Object> publishedEvents = new ArrayList<>();
    private final SpringAuthenticationEventBridge bridge =
            new SpringAuthenticationEventBridge(publishedEvents::add);

    @Test
    void firesAuthenticationSuccess() {
        var event = new AuthenticationSuccess("user-1", "tenant-1", "password");
        bridge.onAuthenticationSuccess(event);
        assertThat(publishedEvents).containsExactly(event);
    }

    @Test
    void firesAuthenticationFailure() {
        var event = new AuthenticationFailure("password", "bad credentials");
        bridge.onAuthenticationFailure(event);
        assertThat(publishedEvents).containsExactly(event);
    }

    @Test
    void firesSocialLoginCompleted() {
        var event = new SocialLoginCompleted("google", "user-1", "tenant-1",
                Set.of("email", "profile"), false, true);
        bridge.onSocialLoginCompleted(event);
        assertThat(publishedEvents).containsExactly(event);
    }

    @Test
    void firesOAuthTokenRefreshFailed() {
        var event = new OAuthTokenRefreshFailed("user-1", "tenant-1", "google", "token expired");
        bridge.onOAuthTokenRefreshFailed(event);
        assertThat(publishedEvents).containsExactly(event);
    }
}
