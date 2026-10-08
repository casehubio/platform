package io.casehub.platform.authn.social;

import io.casehub.platform.api.authn.AuthenticationContext;
import io.casehub.platform.api.authn.ChallengeRecord;
import io.casehub.platform.api.identity.PrincipalId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleAuthenticationProviderTest {

    private static final String TENANT = "tenant-1";
    private static final String ORIGIN = "https://example.com";

    private OAuthTestFixtures.InMemOAuthTokenStore tokenStore;
    private OAuthTestFixtures.InMemIdentityBindingStore bindingStore;
    private OAuthTestFixtures.StubUserResolver userResolver;
    private OAuthTestFixtures.StubOAuthHttpClient httpClient;
    private GoogleAuthenticationProvider provider;

    @BeforeEach
    void setUp() {
        tokenStore = new OAuthTestFixtures.InMemOAuthTokenStore();
        bindingStore = new OAuthTestFixtures.InMemIdentityBindingStore();
        userResolver = new OAuthTestFixtures.StubUserResolver();
        httpClient = new OAuthTestFixtures.StubOAuthHttpClient();

        var config = new GoogleAuthenticationProvider.GoogleConfig(
                "google-client-id", "google-client-secret",
                "https://example.com/callback");
        provider = new GoogleAuthenticationProvider(config, httpClient, tokenStore, bindingStore, userResolver, OAuthTestFixtures.NO_OP_LISTENER);
    }

    @Test
    void methodReturnsGoogle() {
        assertThat(provider.method()).isEqualTo("google");
    }

    @Test
    void initiateUrlPointsToGoogleAuth() {
        var context = new AuthenticationContext(
                "google", TENANT, ORIGIN, Optional.empty(), Map.of());
        var response = (OAuthChallengeResponse) provider.initiate(context);

        assertThat(response.authorizationUrl()).contains("accounts.google.com/o/oauth2/v2/auth");
        assertThat(response.authorizationUrl()).contains("client_id=google-client-id");
        assertThat(response.authorizationUrl()).contains("openid");
        assertThat(response.authorizationUrl()).contains("email");
        assertThat(response.authorizationUrl()).contains("profile");
    }

    @Test
    void initiateIncludesAccessTypeOfflineWhenAdditionalScopesPresent() {
        var context = new AuthenticationContext(
                "google", TENANT, ORIGIN, Optional.empty(),
                Map.of("additionalScopes", "https://www.googleapis.com/auth/calendar"));
        var response = (OAuthChallengeResponse) provider.initiate(context);

        assertThat(response.authorizationUrl()).contains("access_type=offline");
    }

    @Test
    void initiateIncludesPromptConsentWhenAdditionalScopesPresent() {
        var context = new AuthenticationContext(
                "google", TENANT, ORIGIN, Optional.empty(),
                Map.of("additionalScopes", "https://www.googleapis.com/auth/calendar"));
        var response = (OAuthChallengeResponse) provider.initiate(context);

        assertThat(response.authorizationUrl()).contains("prompt=consent");
    }

    @Test
    void initiateOmitsPromptConsentWithoutAdditionalScopes() {
        var context = new AuthenticationContext(
                "google", TENANT, ORIGIN, Optional.empty(), Map.of());
        var response = (OAuthChallengeResponse) provider.initiate(context);

        assertThat(response.authorizationUrl()).doesNotContain("prompt");
    }


    @Test
    void initiateOmitsAccessTypeOfflineWithoutAdditionalScopes() {
        var context = new AuthenticationContext(
                "google", TENANT, ORIGIN, Optional.empty(), Map.of());
        var response = (OAuthChallengeResponse) provider.initiate(context);

        assertThat(response.authorizationUrl()).doesNotContain("access_type");
    }

    @Test
    void initiateIncludesAccessTypeOfflineWithSetAdditionalScopes() {
        var context = new AuthenticationContext(
                "google", TENANT, ORIGIN, Optional.empty(),
                Map.of("additionalScopes", Set.of("https://www.googleapis.com/auth/calendar")));
        var response = (OAuthChallengeResponse) provider.initiate(context);

        assertThat(response.authorizationUrl()).contains("access_type=offline");
    }

    @Test
    void verifyExtractsIdentityFromIdToken() {
        String idToken = OAuthTestFixtures.buildIdToken("google-user-123", "user@gmail.com", "Jane Doe", true);
        httpClient.nextTokenResponse = new OAuthTokenResponse(
                "access-token", "refresh-token", idToken, 3600, "Bearer", "openid email profile");

        var context = new AuthenticationContext(
                "google", TENANT, ORIGIN, Optional.empty(), Map.of());
        var response = (OAuthChallengeResponse) provider.initiate(context);
        var challenge = new ChallengeRecord(
                response.challengeId(), "google", TENANT,
                null, Instant.now(), response.expiresAt());

        var result = provider.verify(challenge, Map.of(
                "code", "google-auth-code",
                "state", response.challengeId()));

        assertThat(result.principal()).isEqualTo(PrincipalId.human("google-user-123"));
        assertThat(result.method()).isEqualTo("google");
        assertThat(result.metadata()).containsEntry("externalId", "google-user-123");
    }

    @Test
    void verifyRejectsUnverifiedEmail() {
        String idToken = OAuthTestFixtures.buildIdToken("user-1", "user@gmail.com", "Jane", false);
        httpClient.nextTokenResponse = new OAuthTokenResponse(
                "access-token", null, idToken, 3600, "Bearer", "openid email");

        var context = new AuthenticationContext(
                "google", TENANT, ORIGIN, Optional.empty(), Map.of());
        var response = (OAuthChallengeResponse) provider.initiate(context);
        var challenge = new ChallengeRecord(
                response.challengeId(), "google", TENANT,
                null, Instant.now(), response.expiresAt());

        assertThatThrownBy(() -> provider.verify(challenge, Map.of(
                "code", "auth-code", "state", response.challengeId())))
                .isInstanceOf(OAuthException.class)
                .hasMessageContaining("email not verified");
    }

    @Test
    void verifyFallsBackToUserInfoWhenNoIdToken() {
        httpClient.nextTokenResponse = new OAuthTokenResponse(
                "access-token", null, null, 3600, "Bearer", "openid email");
        httpClient.nextUserInfo = Map.of(
                "sub", "google-user-456",
                "email", "user@gmail.com",
                "email_verified", true,
                "name", "John Doe");

        var context = new AuthenticationContext(
                "google", TENANT, ORIGIN, Optional.empty(), Map.of());
        var response = (OAuthChallengeResponse) provider.initiate(context);
        var challenge = new ChallengeRecord(
                response.challengeId(), "google", TENANT,
                null, Instant.now(), response.expiresAt());

        var result = provider.verify(challenge, Map.of(
                "code", "auth-code", "state", response.challengeId()));

        assertThat(result.principal()).isEqualTo(PrincipalId.human("google-user-456"));
    }

    @Test
    void verifyStoresTokenAndBinding() {
        String idToken = OAuthTestFixtures.buildIdToken("google-user-789", "user@gmail.com", "Jane", true);
        httpClient.nextTokenResponse = new OAuthTokenResponse(
                "access-token", "refresh-token", idToken, 3600, "Bearer", "openid email");

        var context = new AuthenticationContext(
                "google", TENANT, ORIGIN, Optional.empty(), Map.of());
        var response = (OAuthChallengeResponse) provider.initiate(context);
        var challenge = new ChallengeRecord(
                response.challengeId(), "google", TENANT,
                null, Instant.now(), response.expiresAt());

        provider.verify(challenge, Map.of("code", "auth-code", "state", response.challengeId()));

        assertThat(tokenStore.stored).hasSize(1);
        assertThat(bindingStore.stored).hasSize(1);
        var binding = bindingStore.stored.values().iterator().next();
        assertThat(binding.provider()).isEqualTo("google");
        assertThat(binding.externalId()).isEqualTo("google-user-789");
        assertThat(binding.email()).isEqualTo("user@gmail.com");
    }

}
