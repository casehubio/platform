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

class GitHubAuthenticationProviderTest {

    private static final String TENANT = "tenant-1";
    private static final String ORIGIN = "https://example.com";

    private OAuthTestFixtures.InMemOAuthTokenStore tokenStore;
    private OAuthTestFixtures.InMemIdentityBindingStore bindingStore;
    private OAuthTestFixtures.StubOAuthHttpClient httpClient;
    private GitHubAuthenticationProvider provider;

    @BeforeEach
    void setUp() {
        tokenStore = new OAuthTestFixtures.InMemOAuthTokenStore();
        bindingStore = new OAuthTestFixtures.InMemIdentityBindingStore();
        httpClient = new OAuthTestFixtures.StubOAuthHttpClient();
        var userResolver = new OAuthTestFixtures.StubUserResolver();

        var config = new GitHubAuthenticationProvider.GitHubConfig(
                "github-client-id", "github-client-secret",
                "https://example.com/callback");
        provider = new GitHubAuthenticationProvider(config, httpClient, tokenStore, bindingStore, userResolver, OAuthTestFixtures.NO_OP_LISTENER);
    }

    @Test
    void methodReturnsGithub() {
        assertThat(provider.method()).isEqualTo("github");
    }

    @Test
    void initiateUrlPointsToGitHub() {
        var context = new AuthenticationContext(
                "github", TENANT, ORIGIN, Optional.empty(), Map.of());
        var response = (OAuthChallengeResponse) provider.initiate(context);

        assertThat(response.authorizationUrl()).contains("github.com/login/oauth/authorize");
        assertThat(response.authorizationUrl()).contains("client_id=github-client-id");
    }

    @Test
    void verifyExtractsIdentityFromUserApi() {
        httpClient.nextTokenResponse = new OAuthTokenResponse(
                "gho_access_token", null, null, 0, "bearer", "user:email");
        httpClient.nextUserInfo = Map.of(
                "id", 12345,
                "login", "octocat",
                "name", "The Octocat",
                "email", "octocat@github.com");

        var context = new AuthenticationContext(
                "github", TENANT, ORIGIN, Optional.empty(), Map.of());
        var response = (OAuthChallengeResponse) provider.initiate(context);
        var challenge = new ChallengeRecord(
                response.challengeId(), "github", TENANT,
                null, Instant.now(), response.expiresAt());

        var result = provider.verify(challenge, Map.of(
                "code", "github-code", "state", response.challengeId()));

        assertThat(result.principal()).isEqualTo(PrincipalId.human("12345"));
        assertThat(result.method()).isEqualTo("github");
    }

    @Test
    void verifyHandlesNumericId() {
        httpClient.nextTokenResponse = new OAuthTokenResponse(
                "gho_token", null, null, 0, "bearer", "user");
        httpClient.nextUserInfo = Map.of(
                "id", 99999,
                "login", "devuser");

        var context = new AuthenticationContext(
                "github", TENANT, ORIGIN, Optional.empty(), Map.of());
        var response = (OAuthChallengeResponse) provider.initiate(context);
        var challenge = new ChallengeRecord(
                response.challengeId(), "github", TENANT,
                null, Instant.now(), response.expiresAt());

        var result = provider.verify(challenge, Map.of(
                "code", "code", "state", response.challengeId()));

        assertThat(result.principal().id()).isEqualTo("99999");
        assertThat(bindingStore.stored).hasSize(1);
        assertThat(bindingStore.stored.values().iterator().next().externalId()).isEqualTo("99999");
    }


}
