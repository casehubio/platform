package io.casehub.platform.authn.social;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class HttpTokenRefreshClient implements OAuthTokenManagerCore.TokenRefreshClient {

    private final OAuthHttpClient httpClient;
    private final Map<String, ProviderRefreshConfig> providers;

    public HttpTokenRefreshClient(OAuthHttpClient httpClient,
                                   Map<String, ProviderRefreshConfig> providers) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.providers = Map.copyOf(providers);
    }

    @Override
    public OAuthTokenResponse refresh(String provider, String refreshToken) {
        var config = providers.get(provider);
        if (config == null) {
            throw new UnsupportedOperationException(
                    "No token refresh configuration for provider: " + provider);
        }

        var params = new LinkedHashMap<String, String>();
        params.put("grant_type", "refresh_token");
        params.put("refresh_token", refreshToken);
        params.put("client_id", config.clientId());
        params.put("client_secret", config.clientSecret());

        return httpClient.exchangeCode(config.tokenEndpoint(), params);
    }

    public record ProviderRefreshConfig(String tokenEndpoint, String clientId, String clientSecret) {
        public ProviderRefreshConfig {
            Objects.requireNonNull(tokenEndpoint, "tokenEndpoint");
            Objects.requireNonNull(clientId, "clientId");
            Objects.requireNonNull(clientSecret, "clientSecret");
        }
    }
}
