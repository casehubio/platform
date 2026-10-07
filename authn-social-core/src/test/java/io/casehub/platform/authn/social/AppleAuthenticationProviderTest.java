package io.casehub.platform.authn.social;

import io.casehub.platform.api.authn.AuthenticationContext;
import io.casehub.platform.api.authn.ChallengeRecord;
import io.casehub.platform.api.identity.PrincipalId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppleAuthenticationProviderTest {

    private static final String TENANT = "tenant-1";
    private static final String ORIGIN = "https://example.com";

    private OAuthTestFixtures.InMemOAuthTokenStore tokenStore;
    private OAuthTestFixtures.InMemIdentityBindingStore bindingStore;
    private OAuthTestFixtures.StubOAuthHttpClient httpClient;
    private AppleAuthenticationProvider provider;

    @BeforeEach
    void setUp() {
        tokenStore = new OAuthTestFixtures.InMemOAuthTokenStore();
        bindingStore = new OAuthTestFixtures.InMemIdentityBindingStore();
        httpClient = new OAuthTestFixtures.StubOAuthHttpClient();
        var userResolver = new OAuthTestFixtures.StubUserResolver();

        var config = new AppleAuthenticationProvider.AppleConfig(
                "com.example.service",
                "team-id-123",
                "key-id-abc",
                "fake-private-key",
                "https://example.com/callback");
        provider = new AppleAuthenticationProvider(config, httpClient, tokenStore, bindingStore, userResolver, OAuthTestFixtures.NO_OP_LISTENER);
    }

    @Test
    void methodReturnsApple() {
        assertThat(provider.method()).isEqualTo("apple");
    }

    @Test
    void initiateUrlPointsToApple() {
        var context = new AuthenticationContext(
                "apple", TENANT, ORIGIN, Optional.empty(), Map.of());
        var response = (OAuthChallengeResponse) provider.initiate(context);

        assertThat(response.authorizationUrl()).contains("appleid.apple.com/auth/authorize");
        assertThat(response.authorizationUrl()).contains("response_mode=form_post");
    }

    @Test
    void verifyExtractsIdentityFromIdToken() {
        String idToken = OAuthTestFixtures.buildIdToken("apple-user-001", "user@privaterelay.appleid.com", null, true);
        httpClient.nextTokenResponse = new OAuthTokenResponse(
                "apple-access-token", "apple-refresh-token", idToken, 3600, "Bearer", "openid email");

        var context = new AuthenticationContext(
                "apple", TENANT, ORIGIN, Optional.empty(), Map.of());
        var response = (OAuthChallengeResponse) provider.initiate(context);
        var challenge = new ChallengeRecord(
                response.challengeId(), "apple", TENANT,
                null, Instant.now(), response.expiresAt());

        var result = provider.verify(challenge, Map.of(
                "code", "apple-auth-code", "state", response.challengeId()));

        assertThat(result.principal()).isEqualTo(PrincipalId.human("apple-user-001"));
        assertThat(result.method()).isEqualTo("apple");
    }

    @Test
    void verifyUsesFirstLoginUserData() {
        String idToken = OAuthTestFixtures.buildIdToken("apple-user-002", null, null, true);
        httpClient.nextTokenResponse = new OAuthTokenResponse(
                "token", null, idToken, 3600, "Bearer", "openid");

        var context = new AuthenticationContext(
                "apple", TENANT, ORIGIN, Optional.empty(), Map.of());
        var response = (OAuthChallengeResponse) provider.initiate(context);
        var challenge = new ChallengeRecord(
                response.challengeId(), "apple", TENANT,
                null, Instant.now(), response.expiresAt());

        var result = provider.verify(challenge, Map.of(
                "code", "code",
                "state", response.challengeId(),
                "user", "{\"name\":{\"firstName\":\"Jane\",\"lastName\":\"Doe\"},\"email\":\"jane@example.com\"}"));

        assertThat(result.principal()).isEqualTo(PrincipalId.human("apple-user-002"));
    }

    @Test
    void verifyRejectsNoIdToken() {
        httpClient.nextTokenResponse = new OAuthTokenResponse(
                "token", null, null, 3600, "Bearer", "openid");

        var context = new AuthenticationContext(
                "apple", TENANT, ORIGIN, Optional.empty(), Map.of());
        var response = (OAuthChallengeResponse) provider.initiate(context);
        var challenge = new ChallengeRecord(
                response.challengeId(), "apple", TENANT,
                null, Instant.now(), response.expiresAt());

        assertThatThrownBy(() -> provider.verify(challenge, Map.of(
                "code", "code", "state", response.challengeId())))
                .isInstanceOf(OAuthException.class)
                .hasMessageContaining("ID token");
    }


}
