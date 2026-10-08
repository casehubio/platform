package io.casehub.platform.authn.social;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpTokenRefreshClientTest {

    private OAuthTestFixtures.StubOAuthHttpClient httpClient;
    private HttpTokenRefreshClient refreshClient;

    @BeforeEach
    void setUp() {
        httpClient = new OAuthTestFixtures.StubOAuthHttpClient();
        refreshClient = new HttpTokenRefreshClient(httpClient, Map.of(
                "google", new HttpTokenRefreshClient.ProviderRefreshConfig(
                        "https://oauth2.googleapis.com/token", "google-id", "google-secret"),
                "github", new HttpTokenRefreshClient.ProviderRefreshConfig(
                        "https://github.com/login/oauth/access_token", "github-id", "github-secret")
        ));
    }

    @Test
    void refreshCallsTokenEndpointWithCorrectParams() {
        httpClient.nextTokenResponse = new OAuthTokenResponse(
                "new-access", "new-refresh", null, 3600, "Bearer", "openid");

        var result = refreshClient.refresh("google", "old-refresh-token");

        assertThat(result.accessToken()).isEqualTo("new-access");
        assertThat(result.refreshToken()).isEqualTo("new-refresh");
        assertThat(httpClient.lastTokenEndpoint).isEqualTo("https://oauth2.googleapis.com/token");
        assertThat(httpClient.lastTokenParams)
                .containsEntry("grant_type", "refresh_token")
                .containsEntry("refresh_token", "old-refresh-token")
                .containsEntry("client_id", "google-id")
                .containsEntry("client_secret", "google-secret");
    }

    @Test
    void refreshUsesCorrectProviderConfig() {
        httpClient.nextTokenResponse = new OAuthTokenResponse(
                "gh-token", null, null, 0, "Bearer", "repo");

        refreshClient.refresh("github", "gh-refresh");

        assertThat(httpClient.lastTokenEndpoint).isEqualTo("https://github.com/login/oauth/access_token");
        assertThat(httpClient.lastTokenParams)
                .containsEntry("client_id", "github-id")
                .containsEntry("client_secret", "github-secret");
    }

    @Test
    void refreshThrowsForUnknownProvider() {
        assertThatThrownBy(() -> refreshClient.refresh("unknown", "token"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("unknown");
    }
}
